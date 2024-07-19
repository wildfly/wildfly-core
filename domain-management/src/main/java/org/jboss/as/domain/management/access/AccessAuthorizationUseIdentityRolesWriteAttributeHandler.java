/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * An {@link org.jboss.as.controller.OperationStepHandler} handling write updates to the 'use-identity-roles' attribute.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AccessAuthorizationUseIdentityRolesWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    private final WritableAuthorizerConfiguration writableConfiguration;

    AccessAuthorizationUseIdentityRolesWriteAttributeHandler(WritableAuthorizerConfiguration writableConfiguration) {
        this.writableConfiguration = writableConfiguration;
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
            ModelNode oldValue, Resource model) throws OperationFailedException {
        boolean useIdentityRoles = newValue.isDefined() ? newValue.asBoolean() : AccessAuthorizationResourceDefinition.USE_IDENTITY_ROLES.getDefaultValue().asBoolean();
        if (useIdentityRoles == false) {
            /*
              * As we are no longer using identity roles we may need another role mapping strategy.
             */
            RbacSanityCheckOperation.addOperation(context);
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode resolvedValue, ModelNode currentValue,
            org.jboss.as.controller.AbstractWriteAttributeHandler.HandbackHolder<Void> handbackHolder)
            throws OperationFailedException {
        if (!resolvedValue.equals(currentValue)) {
            if (!context.isBooting()) {
                return true;
            }
            updateAuthorizer(resolvedValue, writableConfiguration);
        }

        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        updateAuthorizer(valueToRestore, writableConfiguration);
    }

    static void updateAuthorizer(final ModelNode value, final WritableAuthorizerConfiguration writableConfiguration) {
        ModelNode resolvedValue = value.isDefined() ? value : AccessAuthorizationResourceDefinition.USE_IDENTITY_ROLES.getDefaultValue();
        boolean useIdentityRoles = resolvedValue.asBoolean();

        writableConfiguration.setUseIdentityRoles(useIdentityRoles);
    }

}
