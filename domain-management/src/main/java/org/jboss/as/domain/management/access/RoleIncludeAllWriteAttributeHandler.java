/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import java.util.Locale;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.dmr.ModelNode;

/**
 * An {@link org.jboss.as.controller.OperationStepHandler} for writing the include-all attribute.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RoleIncludeAllWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    private final WritableAuthorizerConfiguration authorizerConfiguration;

    RoleIncludeAllWriteAttributeHandler(final WritableAuthorizerConfiguration authorizerConfiguration) {
        this.authorizerConfiguration = authorizerConfiguration;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        // During boot add would have taken care of this, otherwise
        // all process types need a runtime update.
        return context.isBooting() == false;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode resolvedValue, ModelNode currentValue,
            org.jboss.as.controller.AbstractWriteAttributeHandler.HandbackHolder<Void> handbackHolder)
            throws OperationFailedException {

        String roleName = RoleMappingResourceDefinition.getRoleName(operation);
        authorizerConfiguration.setRoleMappingIncludeAll(roleName.toUpperCase(Locale.ENGLISH), resolvedValue.asBoolean());
        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        String roleName = RoleMappingResourceDefinition.getRoleName(operation);
        authorizerConfiguration.setRoleMappingIncludeAll(roleName, valueToRestore.asBoolean());
    }




}
