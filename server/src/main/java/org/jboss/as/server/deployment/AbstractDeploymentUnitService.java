/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.DelegatingServiceTarget;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Abstract service responsible for managing the life-cycle of a {@link DeploymentUnit}.
 *
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractDeploymentUnitService implements org.jboss.msc.Service {

    private static final String FIRST_PHASE_NAME = Phase.values()[0].name();
    final ImmutableManagementResourceRegistration registration;
    final ManagementResourceRegistration mutableRegistration;
    final Resource resource;
    final CapabilityServiceSupport capabilityServiceSupport;
    private final Consumer<DeploymentUnit> deploymentUnitConsumer;
    protected final String name;
    private volatile UnaryOperator<ServiceTarget> serviceTargetTransformer = null;
    private volatile DeploymentUnit deploymentUnit;
    private volatile StabilityMonitor monitor;

    AbstractDeploymentUnitService(final Consumer<DeploymentUnit> deploymentUnitConsumer, final ImmutableManagementResourceRegistration registration, final ManagementResourceRegistration mutableRegistration, final Resource resource, final CapabilityServiceSupport capabilityServiceSupport, final String name) {
        this.deploymentUnitConsumer = deploymentUnitConsumer;
        this.mutableRegistration = mutableRegistration;
        this.capabilityServiceSupport = capabilityServiceSupport;
        this.registration = registration;
        this.resource = resource;
        this.name = name;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        ServiceTarget target = context.getChildTarget();
        monitor = new StabilityMonitor();
        monitor.addController(context.getController());
        deploymentUnit = createAndInitializeDeploymentUnit(context.getController().getServiceContainer());

        final String managementName = deploymentUnit.getAttachment(Attachments.MANAGEMENT_NAME);
        if (deploymentUnit.getParent()==null) {
            ServerLogger.DEPLOYMENT_LOGGER.startingDeployment(managementName, name);
        } else {
            ServerLogger.DEPLOYMENT_LOGGER.startingSubDeployment(name);
        }

        Consumer<StartContext> installer = new Consumer<>() {
            @Override
            public void accept(StartContext context) {
                ServiceBuilder<?> phaseServiceBuilder = context.getChildTarget().addService();
                DeploymentUnitPhaseService.createAndSetInstance(phaseServiceBuilder,
                                AbstractDeploymentUnitService.this.deploymentUnit, Phase.values()[0]);
                phaseServiceBuilder.install();
            }
        };

        // If a builder was previously attached, reattach to the new deployment unit instance and build the initial phase using that builder
        if (this.serviceTargetTransformer != null) {
            this.deploymentUnit.putAttachment(Attachments.DEPLOYMENT_UNIT_PHASE_SERVICE_TARGET_TRANSFORMER, this.serviceTargetTransformer);
            // TODO Remove this after WildFly migrates away from DeploymentUnitPhaseBuilder
            this.deploymentUnit.putAttachment(Attachments.DEPLOYMENT_UNIT_PHASE_BUILDER, new DeploymentUnitPhaseBuilder() {
                @Override
                public <T> ServiceBuilder<T> build(ServiceTarget target, ServiceName name, Service<T> service) {
                    return AbstractDeploymentUnitService.this.serviceTargetTransformer.apply(target).addService(name, service);
                }
            });
            Set<AttachmentKey<?>> initialAttachmentKeys = this.getDeploymentUnitAttachmentKeys();
            Runnable uninstaller = new Runnable() {
                @Override
                public void run() {
                    // Cleanup any deployment unit attachments that were not properly removed during DUP undeploy
                    for (AttachmentKey<?> key : AbstractDeploymentUnitService.this.getDeploymentUnitAttachmentKeys()) {
                        if (! initialAttachmentKeys.contains(key)) {
                            AbstractDeploymentUnitService.this.deploymentUnit.removeAttachment(key);
                        }
                    }
                }
            };
            // TODO Use legacy service installation until DeploymentUnitPhaseBuilder migration is complete
            this.serviceTargetTransformer.apply(target).addService(this.deploymentUnit.getServiceName().append("installer"), new org.jboss.msc.service.Service<Void>() {
                    @Override
                    public void start(StartContext context) throws StartException {
                        installer.accept(context);
                    }

                    @Override
                    public void stop(StopContext context) {
                        uninstaller.run();
                    }

                    @Override
                    public Void getValue() {
                        return null;
                    }
                }).install();
        } else {
            installer.accept(context);
        }
        deploymentUnitConsumer.accept(deploymentUnit);
    }

    /**
     * Template method required for implementations to create and fully initialize a deployment unit instance.  This method
     * should be used to attach any initial deployment unit attachments required for the deployment type.
     *
     * @param registry The service registry
     * @return An initialized DeploymentUnit instance
     */
    protected abstract DeploymentUnit createAndInitializeDeploymentUnit(final ServiceRegistry registry);

    @Override
    public synchronized void stop(final StopContext context) {
        deploymentUnitConsumer.accept(null);
        final String managementName = deploymentUnit.getAttachment(Attachments.MANAGEMENT_NAME);
        if (deploymentUnit.getParent()==null) {
            ServerLogger.DEPLOYMENT_LOGGER.stoppedDeployment(managementName, name, (int) (context.getElapsedTime() / 1000000L));
        } else {
            ServerLogger.DEPLOYMENT_LOGGER.stoppedSubDeployment(name, (int) (context.getElapsedTime() / 1000000L));
        }
        // Retain any attached builder across restarts
        this.serviceTargetTransformer = this.deploymentUnit.getAttachment(Attachments.DEPLOYMENT_UNIT_PHASE_SERVICE_TARGET_TRANSFORMER);
        if (this.serviceTargetTransformer == null) {
            // TODO Remove this after WildFly migrates away from DeploymentUnitPhaseBuilder
            DeploymentUnitPhaseBuilder builder = this.deploymentUnit.getAttachment(Attachments.DEPLOYMENT_UNIT_PHASE_BUILDER);
            if (builder != null) {
                this.serviceTargetTransformer = new UnaryOperator<>() {
                    @Override
                    public ServiceTarget apply(ServiceTarget target) {
                        return new DelegatingServiceTarget(target) {
                            @Override
                            public <T> ServiceBuilder<T> addService(ServiceName name, Service<T> service) {
                                return builder.build(target, name, service);
                            }
                        };
                    }
                };
            }
        }
        //clear up all attachments
        for (AttachmentKey<?> key : this.getDeploymentUnitAttachmentKeys()) {
            deploymentUnit.removeAttachment(key);
        }
        deploymentUnit = null;
        monitor.removeController(context.getController());
        monitor = null;
        DeploymentResourceSupport.cleanup(resource);
    }

    /**
     * Returns a new set containing the keys of all current deployment unit attachments.
     */
    private Set<AttachmentKey<?>> getDeploymentUnitAttachmentKeys() {
        return ((SimpleAttachable) this.deploymentUnit).attachmentKeys();
    }

    DeploymentStatus getStatus() {
        StabilityMonitor monitor = this.monitor;
        if (monitor == null) {
            return DeploymentStatus.STOPPED;
        }
        final Set<ServiceController<?>> problems = new HashSet<ServiceController<?>>();
        try {
            monitor.awaitStability(problems, problems);
        } catch (final InterruptedException e) {
            // ignore
        }
        return problems.isEmpty() ? DeploymentStatus.OK : DeploymentStatus.FAILED;
    }

}
