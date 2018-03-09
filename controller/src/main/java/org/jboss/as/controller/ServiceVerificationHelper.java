/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.POSSIBLE_CAUSES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICES_MISSING_TRANSITIVE_DEPENDENCIES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartException;

/**
 * Tracks the status of a service installed by an {@link OperationStepHandler}, recording a failure desription
 * if the service has a problems starting.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a> *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class ServiceVerificationHelper implements OperationStepHandler {

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
            Set<ServiceName> failedSet = new HashSet<>();

            // generate a list of failedServices
            ModelNode failedList = null;
            for (ServiceController<?> controller : failed) {
                if (failedList == null) {
                    failedList = failureDescription.get(ControllerLogger.ROOT_LOGGER.failedServices());
                }
                ServiceName serviceName = controller.getName();
                failedSet.add(serviceName);
                failedList.get(serviceName.getCanonicalName()).set(getServiceFailureDescription(controller.getStartException()));
            }
            ServiceRegistry registry = context.getServiceRegistry(false);
            // generate lists of problems and missing services
            List<String> problemList = new ArrayList<>();
            for (ServiceController<?> controller : problems) {
                Collection<ServiceName> immediatelyUnavailable = controller.getUnavailableDependencies();
                StringBuilder missing = new StringBuilder();
                boolean direct = false;
                for (Iterator<ServiceName> i = immediatelyUnavailable.iterator(); i.hasNext(); ) {
                    ServiceName missingSvc = i.next();
                    ServiceController<?> depController = registry.getService(missingSvc);
                    if (depController == null || depController.getMode() == ServiceController.Mode.NEVER) {
                        unavailableServices.add(missingSvc);
                        direct = true;
                        if (missing.length() != 0) {
                            missing.append(", ");
                        }
                        missing.append(missingSvc.getCanonicalName());
                    }
                }
                if (direct) {
                    final StringBuilder problem = new StringBuilder();
                    problem.append(controller.getName().getCanonicalName());
                    problem.append(" ").append(ControllerLogger.ROOT_LOGGER.servicesMissing(missing));
                    problemList.add(problem.toString());
                }
            }

            // print out missing services
            reportUnavailableRequiredServices(unavailableServices, failureDescription);

            // print out list of services depending on missing services
            reportImmediateDependants(problemList, failureDescription);

            if (context.isRollbackOnRuntimeFailure()) {
                context.setRollbackOnly();
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

    private static ModelNode getServiceFailureDescription(final StartException exception) {
        final ModelNode result = new ModelNode();
        if (exception != null) {
            // This is a bit of inside knowledge of StartException. Its toString() prefixes
            // the main data with 'org...StartException in org.foo.bar: " stuff that
            // will be redundant in the overall output we are creating so we omit that if we can.
            String msg = exception.getLocalizedMessage();
            if (msg == null || msg.length() == 0) {
                msg = exception.toString();
            }
            StringBuilder sb = new StringBuilder(msg);
            Throwable cause = exception.getCause();
            while (cause != null) {
                sb.append("\n    Caused by: ");
                sb.append(cause.toString());
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

