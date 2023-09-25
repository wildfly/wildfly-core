/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;

/**
 * Internal op called.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class AccessAuthorizationDomainSlaveConfigHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "configure-from-domain";
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, DomainManagementResolver.getResolver("core.access-control"))
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setPrivateEntry()
            .build();

    private final DelegatingConfigurableAuthorizer configurableAuthorizer;

    AccessAuthorizationDomainSlaveConfigHandler(DelegatingConfigurableAuthorizer configurableAuthorizer) {
        this.configurableAuthorizer = configurableAuthorizer;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
        for (AttributeDefinition ad : AccessAuthorizationResourceDefinition.CONFIG_ATTRIBUTES) {
            ad.validateAndSet(operation, model);
        }
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                WritableAuthorizerConfiguration authorizerConfiguration = configurableAuthorizer.getWritableAuthorizerConfiguration();

                ModelNode provider = AccessAuthorizationResourceDefinition.PROVIDER.resolveModelAttribute(context, model);
                AccessAuthorizationProviderWriteAttributeHandler.updateAuthorizer(provider, configurableAuthorizer);
                ModelNode combinationPolicy = AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.resolveModelAttribute(context, model);
                AccessAuthorizationCombinationPolicyWriteAttributeHandler.updateAuthorizer(combinationPolicy, authorizerConfiguration);

                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
