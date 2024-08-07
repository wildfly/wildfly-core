/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.ProcessEnvironmentSystemPropertyUpdater;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Handles changes to the value of a system property.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SystemPropertyValueWriteAttributeHandler extends AbstractWriteAttributeHandler<SystemPropertyValueWriteAttributeHandler.SysPropValue> {

    private final ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater;

    public SystemPropertyValueWriteAttributeHandler(ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater) {
        this.systemPropertyUpdater = systemPropertyUpdater;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return systemPropertyUpdater != null;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                           ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<SysPropValue> handbackHolder) throws OperationFailedException {

        final String name = PathAddress.pathAddress(operation.get(OP_ADDR)).getLastElement().getValue();
        String setValue = resolvedValue.isDefined() ? resolvedValue.asString() : null;
        // This method will only be called if systemPropertyUpdater != null (see requiresRuntime())
        final boolean applyToRuntime = systemPropertyUpdater.isRuntimeSystemPropertyUpdateAllowed(name, setValue, context.isBooting());

        if (applyToRuntime) {
            final String oldValue = WildFlySecurityManager.getPropertyPrivileged(name, null);
            if (setValue != null) {
                WildFlySecurityManager.setPropertyPrivileged(name, setValue);
            } else {
                WildFlySecurityManager.clearPropertyPrivileged(name);
            }
            systemPropertyUpdater.systemPropertyUpdated(name, setValue);

            handbackHolder.setHandback(new SysPropValue(name, oldValue));
        }

        return !applyToRuntime;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                         ModelNode valueToRestore, ModelNode valueToRevert, SysPropValue handback) throws OperationFailedException {
        if (handback != null) {
            if (handback.value != null) {
                WildFlySecurityManager.setPropertyPrivileged(handback.name, handback.value);
            } else {
                WildFlySecurityManager.clearPropertyPrivileged(handback.name);
            }

            systemPropertyUpdater.systemPropertyUpdated(handback.name, handback.value);

        }
    }

    public static class SysPropValue {
        private final String name;
        private final String value;

        private SysPropValue(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
