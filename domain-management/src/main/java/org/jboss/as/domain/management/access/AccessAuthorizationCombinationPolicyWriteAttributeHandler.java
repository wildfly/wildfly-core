/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.Locale;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.access.CombinationPolicy;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.dmr.ModelNode;

/**
 * An {@link org.jboss.as.controller.OperationStepHandler} handling write updates to the 'permission-combination-policy'
 * attribute.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class AccessAuthorizationCombinationPolicyWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    private final WritableAuthorizerConfiguration authorizerConfiguration;

    AccessAuthorizationCombinationPolicyWriteAttributeHandler(WritableAuthorizerConfiguration authorizerConfiguration) {
        this.authorizerConfiguration = authorizerConfiguration;
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

        updateAuthorizer(resolvedValue, authorizerConfiguration);
        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                         ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        updateAuthorizer(valueToRestore, authorizerConfiguration);
    }

    static void updateAuthorizer(final ModelNode value, final WritableAuthorizerConfiguration authorizerConfiguration) {
        ModelNode resolvedValue = value.isDefined() ? value : AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.getDefaultValue();
        String policyName = resolvedValue.asString().toUpperCase(Locale.ENGLISH);
        authorizerConfiguration.setPermissionCombinationPolicy(CombinationPolicy.valueOf(policyName));
    }

}

