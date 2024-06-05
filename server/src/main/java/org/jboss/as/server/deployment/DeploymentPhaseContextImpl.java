/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.List;
import java.util.function.Consumer;

import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.service.ServiceDependency;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class DeploymentPhaseContextImpl extends SimpleAttachable implements DeploymentPhaseContext {
    private final RequirementServiceTarget serviceTarget;
    private final ServiceRegistry serviceRegistry;
    private final List<Consumer<ServiceBuilder<?>>> dependencies;
    private final DeploymentUnit deploymentUnitContext;
    private final Phase phase;

    DeploymentPhaseContextImpl(final RequirementServiceTarget serviceTarget, final ServiceRegistry serviceRegistry, final List<Consumer<ServiceBuilder<?>>> dependencies, final DeploymentUnit deploymentUnitContext, final Phase phase) {
        this.serviceTarget = serviceTarget;
        this.serviceRegistry = serviceRegistry;
        this.dependencies = dependencies;
        this.deploymentUnitContext = deploymentUnitContext;
        this.phase = phase;
    }

    @Override
    public ServiceName getPhaseServiceName() {
        return deploymentUnitContext.getServiceName().append(phase.name());
    }

    @Override
    public RequirementServiceTarget getRequirementServiceTarget() {
        return serviceTarget;
    }

    @Override
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    @Override
    public DeploymentUnit getDeploymentUnit() {
        return deploymentUnitContext;
    }

    @Override
    public Phase getPhase() {
        return phase;
    }

    @Override
    public <T> void addDependency(ServiceName serviceName, AttachmentKey<T> attachmentKey) {
        addToAttachmentList(Attachments.NEXT_PHASE_ATTACHABLE_DEPS, new AttachableDependency(attachmentKey, serviceName, false));
    }

    @Override
    public <T> void requires(ServiceDependency<T> dependency) {
        this.dependencies.add(dependency);
    }

    @Override
    public <T> void addDeploymentDependency(ServiceName serviceName, AttachmentKey<T> attachmentKey) {
        addToAttachmentList(Attachments.NEXT_PHASE_ATTACHABLE_DEPS, new AttachableDependency(attachmentKey, serviceName, true));
    }
}
