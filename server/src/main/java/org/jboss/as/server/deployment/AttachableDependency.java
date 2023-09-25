/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.msc.service.ServiceName;

/**
 * Represents a phase dependency that gets attached to the phase context or the deployment unit.
 *
 * @see DeploymentPhaseContext#addDependency(org.jboss.msc.service.ServiceName, AttachmentKey)
 * @see DeploymentPhaseContext#addDeploymentDependency(org.jboss.msc.service.ServiceName, AttachmentKey)
 * @author Stuart Douglas
 *
 */
public class AttachableDependency {

    private final AttachmentKey<?> attachmentKey;
    private final ServiceName serviceName;

    /**
     * True if this should be attached to the {@link DeploymentUnit}. Otherwise it is attached to the next
     * {@link DeploymentPhaseContext}.
     */
    private final boolean deploymentUnit;

    public AttachableDependency(AttachmentKey<?> attachmentKey, ServiceName serviceName, boolean deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
        this.attachmentKey = attachmentKey;
        this.serviceName = serviceName;
    }

    public AttachmentKey<?> getAttachmentKey() {
        return attachmentKey;
    }

    public ServiceName getServiceName() {
        return serviceName;
    }

    public boolean isDeploymentUnit() {
        return deploymentUnit;
    }

}
