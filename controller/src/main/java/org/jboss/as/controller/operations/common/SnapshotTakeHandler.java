/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * An operation that takes a snapshot of the current configuration
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class SnapshotTakeHandler implements OperationStepHandler {

    private static final String OPERATION_NAME = "take-snapshot";

    private static final AttributeDefinition COMMENT = SimpleAttributeDefinitionBuilder.create("comment", ModelType.STRING, true).setNullSignificant(true).build();
    private static final AttributeDefinition SNAPSHOT_NAME = SimpleAttributeDefinitionBuilder.create(NAME, ModelType.STRING, true).setNullSignificant(true).build();
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver("snapshot"))
            .addParameter(COMMENT)
            .addParameter(SNAPSHOT_NAME)
            .setReplyType(ModelType.STRING)
            .setRuntimeOnly()
            .withFlag(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SNAPSHOTS)
            .build();

    private final ConfigurationPersister persister;

    public SnapshotTakeHandler(ConfigurationPersister persister) {
        this.persister = persister;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        AuthorizationResult authorizationResult = context.authorize(operation);
        if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
            throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.get(OP).asString(), context.getCurrentAddress(), authorizationResult.getExplanation());
        }
        ModelNode commentNode = COMMENT.resolveModelAttribute(context, operation);
        ModelNode snapshotNode = SNAPSHOT_NAME.resolveModelAttribute(context, operation);
        String comment = commentNode.asStringOrNull();
        String snapshot = snapshotNode.asStringOrNull();
        try {
            String name = persister.snapshot(snapshot, comment);
            context.getResult().set(name);
        } catch (ConfigurationPersistenceException e) {
            throw new OperationFailedException(e);
        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }


}
