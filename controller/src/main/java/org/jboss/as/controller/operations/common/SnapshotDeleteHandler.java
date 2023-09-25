/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * An operation that deletes a snapshot of the current configuration
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SnapshotDeleteHandler implements OperationStepHandler {

    private static final String OPERATION_NAME = "delete-snapshot";

    private static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING)
            .setRequired(true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver("snapshot"))
            .setParameters(NAME)
            .setRuntimeOnly()
            .withFlag(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SNAPSHOTS)
            .build();


    private final ConfigurationPersister persister;

    public SnapshotDeleteHandler(ConfigurationPersister persister) {
        this.persister = persister;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        AuthorizationResult authorizationResult = context.authorize(operation);
        if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
            throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.get(OP).asString(), context.getCurrentAddress(), authorizationResult.getExplanation());
        }

        String name = operation.require(ModelDescriptionConstants.NAME).asString();
        try {
            persister.deleteSnapshot(name);
        } catch (Exception e) {
            throw new OperationFailedException(e);
        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

}
