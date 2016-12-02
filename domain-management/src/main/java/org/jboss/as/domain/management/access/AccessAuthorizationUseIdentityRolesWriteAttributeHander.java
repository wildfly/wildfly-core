/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
class AccessAuthorizationUseIdentityRolesWriteAttributeHander extends AbstractWriteAttributeHandler<Void> {

    private final WritableAuthorizerConfiguration writableConfiguration;

    AccessAuthorizationUseIdentityRolesWriteAttributeHander(WritableAuthorizerConfiguration writableConfiguration) {
        super(AccessAuthorizationResourceDefinition.USE_IDENTITY_ROLES);
        this.writableConfiguration = writableConfiguration;
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
            ModelNode oldValue, Resource model) throws OperationFailedException {
        boolean useIdentityRoles = newValue.asBoolean();
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
