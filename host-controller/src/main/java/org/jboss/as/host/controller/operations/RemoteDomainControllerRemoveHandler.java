/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RemoteDomainControllerRemoveHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "remove-remote-domain-controller";
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host"))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.DOMAIN_CONTROLLER)
            .build();
    public static final RemoteDomainControllerRemoveHandler INSTANCE = new RemoteDomainControllerRemoveHandler();

    /**
     * Create the RemoteDomainControllerRemoveHandler
     */
    private RemoteDomainControllerRemoveHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();
        model.get(DOMAIN_CONTROLLER).setEmptyObject();
        context.reloadRequired();
        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                context.revertReloadRequired();
            }
        });
    }
}
