/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.Locale;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.access.Authorizer;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.rbac.RoleMapper;
import org.jboss.as.controller.access.rbac.StandardRBACAuthorizer;
import org.jboss.as.controller.access.rbac.StandardRoleMapper;
import org.jboss.as.controller.access.rbac.SuperUserRoleMapper;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition.Provider;
import org.jboss.dmr.ModelNode;

/**
 * An {@link org.jboss.as.controller.OperationStepHandler} handling write updates to the 'provider' attribute allowing
 * for the authorization provider to be switched.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AccessAuthorizationProviderWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    private final DelegatingConfigurableAuthorizer configurableAuthorizer;

    AccessAuthorizationProviderWriteAttributeHandler(DelegatingConfigurableAuthorizer configurableAuthorizer) {
        super(AccessAuthorizationResourceDefinition.PROVIDER);
        this.configurableAuthorizer = configurableAuthorizer;
    }


    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
            ModelNode oldValue, Resource model) throws OperationFailedException {
         Provider provider = Provider.valueOf(newValue.asString().toUpperCase(Locale.ENGLISH));
         if (provider == Provider.RBAC) {
             /*
              * As the provider is being set to RBAC we need to be sure roles can be assigned.
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
            updateAuthorizer(resolvedValue, configurableAuthorizer);
        }

        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        updateAuthorizer(valueToRestore, configurableAuthorizer);
    }

    static void updateAuthorizer(final ModelNode value, final DelegatingConfigurableAuthorizer configurableAuthorizer) {
        ModelNode resolvedValue = value.isDefined() ? value : AccessAuthorizationResourceDefinition.PROVIDER.getDefaultValue();
        String providerName = resolvedValue.asString().toUpperCase(Locale.ENGLISH);
        Provider provider = Provider.valueOf(providerName);
        AuthorizerConfiguration authorizerConfiguration = configurableAuthorizer.getWritableAuthorizerConfiguration();
        RoleMapper roleMapper;
        if (provider == Provider.SIMPLE) {
            roleMapper = new SuperUserRoleMapper(authorizerConfiguration);
        } else {
            roleMapper = new StandardRoleMapper(configurableAuthorizer.getWritableAuthorizerConfiguration());
        }
        Authorizer delegate = StandardRBACAuthorizer.create(configurableAuthorizer.getWritableAuthorizerConfiguration(),
                roleMapper);
        configurableAuthorizer.setDelegate(delegate);
    }

}
