/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.DelegatingServiceRegistry;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * A service which executes a particular phase of deployment.
 *
 * @param <T> the public type of this deployment unit phase
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class DeploymentUnitPhaseService<T> implements Service<T> {

    private final InjectedValue<DeployerChains> deployerChainsInjector = new InjectedValue<DeployerChains>();
    private final DeploymentUnit deploymentUnit;
    private final Phase phase;
    private final AttachmentKey<T> valueKey;
    private final List<AttachedDependency> injectedAttachedDependencies = new ArrayList<AttachedDependency>();
    /**
     * boolean value that tracks if this phase has already been run.
     *
     * If anything attempts to restart the phase a complete deployment restart is performed instead.
     */
    private final AtomicBoolean runOnce = new AtomicBoolean();

    private DeploymentUnitPhaseService(final DeploymentUnit deploymentUnit, final Phase phase, final AttachmentKey<T> valueKey) {
        this.deploymentUnit = deploymentUnit;
        this.phase = phase;
        this.valueKey = valueKey;
    }

    private static <T> DeploymentUnitPhaseService<T> create(final DeploymentUnit deploymentUnit, final Phase phase, AttachmentKey<T> valueKey) {
        return new DeploymentUnitPhaseService<T>(deploymentUnit, phase, valueKey);
    }

    static DeploymentUnitPhaseService<?> create(final DeploymentUnit deploymentUnit, final Phase phase) {
        return create(deploymentUnit, phase, phase.getPhaseKey());
    }

    @SuppressWarnings("unchecked")
    public synchronized void start(final StartContext context) throws StartException {
        if(runOnce.get()) {
            ServerLogger.DEPLOYMENT_LOGGER.deploymentRestartDetected(deploymentUnit.getName());
            //this only happens on deployment restart, which we don't support at the moment.
            //instead we are going to restart the complete deployment.

            //we get the deployment unit service name
            //add a listener to perform a restart when the service goes down
            //then stop the deployment unit service
            final ServiceName serviceName;
            if(deploymentUnit.getParent() == null) {
                serviceName = deploymentUnit.getServiceName();
            } else {
                serviceName = deploymentUnit.getParent().getServiceName();
            }
            ServiceController<?> controller = context.getController().getServiceContainer().getRequiredService(serviceName);
            controller.addListener(new LifecycleListener() {
                @Override
                public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                    if (event == LifecycleEvent.DOWN) {
                        controller.setMode(Mode.ACTIVE);
                        controller.removeListener(this);
                    }
                }
            });
            controller.setMode(Mode.NEVER);
            return;
        }
        runOnce.set(true);
        final DeployerChains chains = deployerChainsInjector.getValue();
        final DeploymentUnit deploymentUnit = this.deploymentUnit;
        final List<RegisteredDeploymentUnitProcessor> list = chains.getChain(phase);
        final ListIterator<RegisteredDeploymentUnitProcessor> iterator = list.listIterator();
        final ServiceContainer container = context.getController().getServiceContainer();
        final RequirementServiceTarget serviceTarget = RequirementServiceTarget.forTarget(context.getChildTarget().subTarget(), deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT));
        final DeploymentUnit parent = deploymentUnit.getParent();

        final List<Consumer<ServiceBuilder<?>>> dependencies = new LinkedList<>();
        final DeploymentPhaseContext processorContext = new DeploymentPhaseContextImpl(serviceTarget, new DelegatingServiceRegistry(container), dependencies, deploymentUnit, phase);

        // attach any injected values from the last phase
        for (AttachedDependency attachedDependency : injectedAttachedDependencies) {
            final Attachable target;
            if (attachedDependency.isDeploymentUnit()) {
                target = deploymentUnit;
            } else {
                target = processorContext;
            }
            if (attachedDependency.getAttachmentKey() instanceof ListAttachmentKey) {
                target.addToAttachmentList((AttachmentKey) attachedDependency.getAttachmentKey(), attachedDependency.getValue().getValue());
            } else {
                target.putAttachment((AttachmentKey) attachedDependency.getAttachmentKey(), attachedDependency.getValue().getValue());
            }
        }

        final Set<String> registeredSubSystems;
        if (phase == Phase.STRUCTURE) {
            registeredSubSystems = new HashSet<>();
            deploymentUnit.putAttachment(Attachments.REGISTERED_SUBSYSTEMS, registeredSubSystems);
        } else {
            registeredSubSystems = deploymentUnit.getAttachment(Attachments.REGISTERED_SUBSYSTEMS);
        }

        for (RegisteredDeploymentUnitProcessor dupRegistration : list) {
            registeredSubSystems.add(dupRegistration.getSubsystemName());
        }

        if (phase == Phase.CLEANUP) {
            // WFCORE-4233 check all excluded subsystems via jboss-deployment-structure.xml are valid in last Phase.CLEANUP
            Set<String> excludedSubSystems = deploymentUnit.getAttachment(Attachments.EXCLUDED_SUBSYSTEMS);
            if (excludedSubSystems == null && deploymentUnit.getParent() != null) {
                excludedSubSystems = deploymentUnit.getParent().getAttachment(Attachments.EXCLUDED_SUBSYSTEMS);
            }
            if (excludedSubSystems != null) {
                for (String sub : excludedSubSystems) {
                    if (!registeredSubSystems.contains(sub)) {
                        ServerLogger.DEPLOYMENT_LOGGER.excludedSubSystemsNotExist(sub);
                    }
                }
            }

            final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
            final Set<ModuleIdentifier> nonexsistentExcludedDependencies = moduleSpecification.getNonexistentExcludedDependencies();
            if (!nonexsistentExcludedDependencies.isEmpty()) {
                for (ModuleIdentifier module : nonexsistentExcludedDependencies) {
                    ServerLogger.DEPLOYMENT_LOGGER.excludedDependenciesNotExist(module.getName());
                }
            }
        }

        while (iterator.hasNext()) {
            final RegisteredDeploymentUnitProcessor processor = iterator.next();
            try {
                if (shouldRun(deploymentUnit, processor)) {
                    processor.getProcessor().deploy(processorContext);
                }
            } catch (Throwable e) {
                while (iterator.hasPrevious()) {
                    final RegisteredDeploymentUnitProcessor prev = iterator.previous();
                    safeUndeploy(deploymentUnit, phase, prev);
                }
                throw ServerLogger.ROOT_LOGGER.deploymentPhaseFailed(phase, deploymentUnit, e);
            }
        }

        final Phase nextPhase = phase.next();
        if (nextPhase != null) {
            final ServiceName serviceName = DeploymentUtils.getDeploymentUnitPhaseServiceName(deploymentUnit, nextPhase);
            final DeploymentUnitPhaseService<?> phaseService = DeploymentUnitPhaseService.create(deploymentUnit, nextPhase);
            final ServiceBuilder<?> phaseServiceBuilder = serviceTarget.addService(serviceName, phaseService);

            for (Consumer<ServiceBuilder<?>> dependency: dependencies) {
                dependency.accept(phaseServiceBuilder);
            }

            phaseServiceBuilder.addDependency(Services.JBOSS_DEPLOYMENT_CHAINS, DeployerChains.class, phaseService.getDeployerChainsInjector());
            for (ServiceName providedValue : context.getController().provides()) {
                phaseServiceBuilder.requires(providedValue);
            }

            final List<ServiceName> nextPhaseDeps = processorContext.getAttachment(Attachments.NEXT_PHASE_DEPS);
            if (nextPhaseDeps != null) {
                for (final ServiceName nextPhaseDep : nextPhaseDeps) {
                    phaseServiceBuilder.requires(nextPhaseDep);
                }
            }
            final List<AttachableDependency> nextPhaseAttachableDeps = processorContext.getAttachment(Attachments.NEXT_PHASE_ATTACHABLE_DEPS);
            if (nextPhaseAttachableDeps != null) {
                for (AttachableDependency attachableDep : nextPhaseAttachableDeps) {
                    AttachedDependency result = new AttachedDependency(attachableDep.getAttachmentKey(), attachableDep.isDeploymentUnit());
                    phaseServiceBuilder.addDependency(attachableDep.getServiceName(), Object.class, result.getValue());
                    phaseService.injectedAttachedDependencies.add(result);

                }
            }

            // Add a dependency on the parent's next phase
            if (parent != null) {
                phaseServiceBuilder.requires(Services.deploymentUnitName(parent.getName(), nextPhase));
            }

            // Make sure all sub deployments have finished this phase before moving to the next one
            List<DeploymentUnit> subDeployments = deploymentUnit.getAttachmentList(Attachments.SUB_DEPLOYMENTS);
            for (DeploymentUnit du : subDeployments) {
                phaseServiceBuilder.requires(du.getServiceName().append(phase.name()));
            }

            phaseServiceBuilder.install();
        }
    }

    public synchronized void stop(final StopContext context) {
        final DeploymentUnit deploymentUnitContext = deploymentUnit;
        final DeployerChains chains = deployerChainsInjector.getValue();
        final List<RegisteredDeploymentUnitProcessor> list = chains.getChain(phase);
        final ListIterator<RegisteredDeploymentUnitProcessor> iterator = list.listIterator(list.size());
        while (iterator.hasPrevious()) {
            final RegisteredDeploymentUnitProcessor prev = iterator.previous();
            safeUndeploy(deploymentUnitContext, phase, prev);
        }
    }

    private static void safeUndeploy(final DeploymentUnit deploymentUnit, final Phase phase, final RegisteredDeploymentUnitProcessor prev) {
        try {
            if (shouldRun(deploymentUnit, prev)) {
                prev.getProcessor().undeploy(deploymentUnit);
            }
        } catch (Throwable t) {
            ServerLogger.DEPLOYMENT_LOGGER.caughtExceptionUndeploying(t, prev.getProcessor(), phase, deploymentUnit);
        }
    }

    public synchronized T getValue() throws IllegalStateException, IllegalArgumentException {
        return deploymentUnit.getAttachment(valueKey);
    }

    InjectedValue<DeployerChains> getDeployerChainsInjector() {
        return deployerChainsInjector;
    }

    private static boolean shouldRun(final DeploymentUnit unit, final RegisteredDeploymentUnitProcessor deployer) {
        Set<String> shouldNotRun = unit.getAttachment(Attachments.EXCLUDED_SUBSYSTEMS);
        if (shouldNotRun == null) {
            if (unit.getParent() != null) {
                shouldNotRun = unit.getParent().getAttachment(Attachments.EXCLUDED_SUBSYSTEMS);
            }
            if (shouldNotRun == null) {
                return true;
            }
        }
        return !shouldNotRun.contains(deployer.getSubsystemName());
    }
}
