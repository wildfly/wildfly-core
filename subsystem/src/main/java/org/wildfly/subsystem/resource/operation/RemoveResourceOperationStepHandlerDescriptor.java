/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import org.jboss.as.controller.registry.OperationEntry;

/**
 * Describes the properties of a resource {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#REMOVE} operation handler.
 * @author Paul Ferraro
 */
public interface RemoveResourceOperationStepHandlerDescriptor extends ResourceOperationStepHandlerDescriptor {

    /**
     * Returns the restart flag for the {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#REMOVE} operation of this resource.
     * @return an operation flag
     */
    default OperationEntry.Flag getRemoveOperationRestartFlag() {
        return OperationEntry.Flag.RESTART_NONE;
    }
}
