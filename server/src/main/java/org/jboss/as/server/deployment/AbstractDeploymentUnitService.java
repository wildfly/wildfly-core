/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.ExceptionConsumer;

/**
 * Abstract service responsible for managing the life-cycle of a {@link DeploymentUnit}.
 *
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractDeploymentUnitService implements Service<DeploymentUnit> {

    private static final String FIRST_PHASE_NAME = Phase.values()[0].name();
    final ImmutableManagementResourceRegistration registration;
    final ManagementResourceRegistration mutableRegistration;
    final Resource resource;
    final CapabilityServiceSupport capabilityServiceSupport;
    private final Consumer<DeploymentUnit> deploymentUnitConsumer;
    protected final String name;
    private volatile DeploymentUnitPhaseBuilder phaseBuilder = null;
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

        ExceptionConsumer<StartContext, StartException> installer = startContext -> {
            ServiceName serviceName = this.deploymentUnit.getServiceName().append(FIRST_PHASE_NAME);
            DeploymentUnitPhaseService<?> phaseService = DeploymentUnitPhaseService.create(this.deploymentUnit, Phase.values()[0]);
            startContext.getChildTarget().addService(serviceName, phaseService)
                    .addDependency(Services.JBOSS_DEPLOYMENT_CHAINS, DeployerChains.class, phaseService.getDeployerChainsInjector())
                    .install();
        };

        // If a builder was previously attached, reattach to the new deployment unit instance and build the initial phase using that builder
        if (this.phaseBuilder != null) {
            this.deploymentUnit.putAttachment(Attachments.DEPLOYMENT_UNIT_PHASE_BUILDER, this.phaseBuilder);
            Set<AttachmentKey<?>> initialAttachmentKeys = this.getDeploymentUnitAttachmentKeys();
            Consumer<StopContext> uninstaller = stopContext -> {
                // Cleanup any deployment unit attachments that were not properly removed during DUP undeploy
                for (AttachmentKey<?> key : this.getDeploymentUnitAttachmentKeys()) {
                    if (! initialAttachmentKeys.contains(key)) {
                        this.deploymentUnit.removeAttachment(key);
                    }
                }
            };
            ServiceName serviceName = this.deploymentUnit.getServiceName().append("installer");
            this.phaseBuilder.build(target, serviceName, new FunctionalVoidService(installer, uninstaller)).install();
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
        this.phaseBuilder = this.deploymentUnit.getAttachment(Attachments.DEPLOYMENT_UNIT_PHASE_BUILDER);
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
    public synchronized DeploymentUnit getValue() throws IllegalStateException, IllegalArgumentException {
        return deploymentUnit;
    }

    public DeploymentStatus getStatus() {
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

    public enum DeploymentStatus {
        NEW,
        OK,
        FAILED,
        STOPPED
    }
}
