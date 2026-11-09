/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.POSSIBLE_CAUSES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICES_MISSING_TRANSITIVE_DEPENDENCIES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartException;

/**
 * Tracks the status of a service installed by an {@link OperationStepHandler}, recording a failure description
 * if the service has a problems starting.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 * @author Radoslav Husar
 */
class ServiceVerificationHelper implements OperationStepHandler {

    static final OperationContext.AttachmentKey<Boolean> DEFERRED_ROLLBACK_ATTACHMENT = OperationContext.AttachmentKey.create(Boolean.class);

    private final StabilityMonitor monitor = new StabilityMonitor();

    StabilityMonitor getMonitor() {
        return monitor;
    }

    public synchronized void execute(final OperationContext context, final ModelNode operation) {
        final Set<ServiceController<?>> failed = new HashSet<ServiceController<?>>();
        final Set<ServiceController<?>> problems = new HashSet<ServiceController<?>>();

        try {
            monitor.awaitStability(failed, problems);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.operationCancelled());
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            return;
        } finally {
            monitor.clear();
        }

        if (!failed.isEmpty() || !problems.isEmpty()) {
            final ModelNode failureDescription = context.getFailureDescription();
            Set<ServiceName> unavailableServices = new HashSet<>();

            // generate a list of failedServices
            ModelNode failedList = null;
            for (ServiceController<?> controller : failed) {
                if (failedList == null) {
                    failedList = failureDescription.get(ControllerLogger.ROOT_LOGGER.failedServices());
                }
                for (ServiceName serviceName : controller.provides()) {
                    failedList.get(serviceName.getCanonicalName()).set(getServiceFailureDescription(controller.getStartException()));
                    break; // it is sufficient to report just first provided value
                }
            }
            ServiceRegistry registry = context.getServiceRegistry(false);
            // generate lists of problems and missing services
            List<String> problemList = new ArrayList<>();
            for (ServiceController<?> controller : problems) {
                Collection<ServiceName> immediatelyUnavailable = controller.missing();
                StringBuilder missing = new StringBuilder();
                boolean direct = false;
                for (ServiceName missingSvc : immediatelyUnavailable) {
                    ServiceController<?> depController = registry.getService(missingSvc);
                    if (depController == null || depController.getMode() == ServiceController.Mode.NEVER) {
                        unavailableServices.add(missingSvc);
                        direct = true;
                        if (!missing.isEmpty()) {
                            missing.append(", ");
                        }
                        missing.append(missingSvc.getCanonicalName());
                    }
                }
                if (direct) {
                    final StringBuilder problem = new StringBuilder();
                    for (ServiceName serviceName : controller.provides()) {
                        problem.append(serviceName);
                        break; // it is sufficient to report just first provided value
                    }
                    problem.append(" ").append(ControllerLogger.ROOT_LOGGER.servicesMissing(missing));
                    problemList.add(problem.toString());
                }
            }

            // print out missing services
            reportUnavailableRequiredServices(unavailableServices, failureDescription);

            // print out list of services depending on missing services
            reportImmediateDependants(problemList, failureDescription);

            // None of the above applied and the failure description is still empty
            // e.g. the unavailable dependencies are installed but not tracked by this operation, or became available after stability was reached
            // Thus report the problem services otherwise rollback is left unexplained!
            if (!failureDescription.isDefined()) {
                reportUnexplainedProblems(problems, registry, failureDescription);
            }

            if (context.isRollbackOnRuntimeFailure()) {
                // Registering new VERIFY step to be executed after all ServiceVerificationHandlers.
                // This way other failing services have a chance to report missing dependencies before rollback.
                // DEFERRED_ROLLBACK_ATTACHMENT notifies AbstractOperationContext that the operation will be
                // rolled back at later point so that it doesn't force rollback due to failure description
                // being present.
                context.attach(DEFERRED_ROLLBACK_ATTACHMENT, Boolean.TRUE);
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.setRollbackOnly();
                    }
                }, OperationContext.Stage.VERIFY);
            }

            // Notify ContainerStateVerificationHandler that we've reported an issue so it
            // doesn't need to
            context.attach(ContainerStateVerificationHandler.FAILURE_REPORTED_ATTACHMENT, Boolean.TRUE);
        }
    }

    private static void reportUnavailableRequiredServices(Set<ServiceName> unavailableServices, ModelNode failureDescription) {
        if (!unavailableServices.isEmpty()) {
            ModelNode requiredServicesNode = failureDescription.get(ControllerLogger.ROOT_LOGGER.missingRequiredServices());
            for (ServiceName serviceName : unavailableServices) {
                requiredServicesNode.add(serviceName.getCanonicalName());
            }
        }
    }

    private static void reportImmediateDependants(List<String> problemList, ModelNode failureDescription) {
        if (!problemList.isEmpty()) {
            ModelNode problemListNode = failureDescription.get(ControllerLogger.ROOT_LOGGER.servicesMissingDependencies());
            for (String problem : problemList) {
                problemListNode.add(problem);
            }
        }
    }

    private static void reportUnexplainedProblems(Set<ServiceController<?>> problems, ServiceRegistry registry, ModelNode failureDescription) {
        ModelNode problemsNode = failureDescription.get(ControllerLogger.ROOT_LOGGER.servicesWithUnresolvedProblems());
        for (ServiceController<?> controller : problems) {
            StringBuilder missing = new StringBuilder();
            for (ServiceName missingSvc : controller.missing()) {
                if (!missing.isEmpty()) {
                    missing.append(", ");
                }
                missing.append(missingSvc.getCanonicalName());
                ServiceController<?> depController = registry.getService(missingSvc);
                if (depController != null) {
                    missing.append(" (").append(depController.getState()).append(", ").append(depController.getMode()).append(')');
                }
            }
            // Report all provided values, or a placeholder for a service that provides none, so that no problem service is omitted
            String names = controller.provides().isEmpty() ? "<anonymous>" : controller.provides().stream().map(ServiceName::getCanonicalName).collect(Collectors.joining(", "));
            problemsNode.add(names + " (" + controller.getState() + ") " + ControllerLogger.ROOT_LOGGER.servicesMissing(missing));
        }
    }

    private static ModelNode getServiceFailureDescription(final StartException exception) {
        final ModelNode result = new ModelNode();
        if (exception != null) {
            // This is a bit of inside knowledge of StartException. Its toString() prefixes
            // the main data with 'org...StartException in org.foo.bar: " stuff that
            // will be redundant in the overall output we are creating so we omit that if we can.
            String msg = exception.getLocalizedMessage();
            if (msg == null || msg.isEmpty()) {
                msg = exception.toString();
            }
            StringBuilder sb = new StringBuilder(msg);
            Throwable cause = exception.getCause();
            while (cause != null) {
                sb.append("\n    Caused by: ");
                sb.append(cause);
                cause = cause.getCause();
            }
            result.set(sb.toString());
        }
        return result;
    }

    static ModelNode extractFailedServicesDescription(ModelNode failureDescription) {
        return extractIfPresent(ControllerLogger.ROOT_LOGGER.failedServices(), failureDescription);
    }

    static ModelNode extractMissingServicesDescription(ModelNode failureDescription) {
        return extractIfPresent(ControllerLogger.ROOT_LOGGER.servicesMissingDependencies(), failureDescription);
    }

    static ModelNode extractTransitiveDependencyProblemDescription(ModelNode failureDescription) {
        ModelNode transitiveDependencyProblemDescription = null;
        ModelNode missingTransitiveDesc = extractIfPresent(ControllerLogger.ROOT_LOGGER.missingTransitiveDependencyProblem(), failureDescription);
        if (missingTransitiveDesc != null) {
            ModelNode missingTransitiveDeps = extractIfPresent(ControllerLogger.ROOT_LOGGER.missingTransitiveDependents(), missingTransitiveDesc);
            ModelNode allMissingList = extractIfPresent(ControllerLogger.ROOT_LOGGER.missingTransitiveDependencies(), missingTransitiveDesc);
            if (allMissingList != null || missingTransitiveDeps != null) {
                transitiveDependencyProblemDescription = new ModelNode();
                if (missingTransitiveDeps != null) {
                    transitiveDependencyProblemDescription.get(SERVICES_MISSING_TRANSITIVE_DEPENDENCIES).set(missingTransitiveDeps);
                }
                if (allMissingList != null) {
                    transitiveDependencyProblemDescription.get(POSSIBLE_CAUSES).set(allMissingList);
                }
            }
        }
        return transitiveDependencyProblemDescription;
    }

    private static ModelNode extractIfPresent(String key, ModelNode modelNode) {
        ModelNode result = null;
        if (modelNode.hasDefined(key)) {
            result = modelNode.get(key);
        }
        return result;

    }
}

