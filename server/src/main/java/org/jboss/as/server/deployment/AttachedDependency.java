/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.msc.value.InjectedValue;

/**
 * Represents an injected dependency from a previous phase. This dependency value is attached under the attachment key when the
 * phase starts.
 *
 * @see DeploymentPhaseContext#addDependency(org.jboss.msc.service.ServiceName, AttachmentKey)
 * @see DeploymentPhaseContext#addDeploymentDependency(org.jboss.msc.service.ServiceName, AttachmentKey)
 * @author Stuart Douglas
 *
 */
class AttachedDependency {

    private final AttachmentKey<?> attachmentKey;
    private final InjectedValue<Object> value;

    /**
     * True if this should be attached to the {@link DeploymentUnit}. Otherwise it is attached to the next
     * {@link DeploymentPhaseContext}.
     */
    private final boolean deploymentUnit;

    public AttachedDependency(AttachmentKey<?> attachmentKey, boolean deploymentUnit) {
        this.attachmentKey = attachmentKey;
        this.value = new InjectedValue<Object>();
        this.deploymentUnit = deploymentUnit;
    }

    public AttachmentKey<?> getAttachmentKey() {
        return attachmentKey;
    }

    public InjectedValue<Object> getValue() {
        return value;
    }

    public boolean isDeploymentUnit() {
        return deploymentUnit;
    }

}
