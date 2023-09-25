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
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handler to publish configuration.
 * Currently this is used only if git is used for persistence history.
 * This will push the changes to a remote repository.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ConfigurationPublishHandler implements OperationStepHandler {

    private static final String OPERATION_NAME = "publish-configuration";

    private static final SimpleAttributeDefinition LOCATION = new SimpleAttributeDefinitionBuilder("location", ModelType.STRING)
            .setRequired(false)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver("publish"))
            .setParameters(LOCATION)
            .setRuntimeOnly()
            .withFlag(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SNAPSHOTS)
            .build();


    private final ConfigurationPersister persister;

    public ConfigurationPublishHandler(ConfigurationPersister persister) {
        this.persister = persister;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        AuthorizationResult authorizationResult = context.authorize(operation);
        if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
            throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.get(OP).asString(), context.getCurrentAddress(), authorizationResult.getExplanation());
        }

        String name = null;
        if(operation.hasDefined(LOCATION.getName())) {
            name = LOCATION.resolveModelAttribute(context, operation).asString();
        }
        try {
            context.getResult().set(persister.publish(name));
        } catch (ConfigurationPersistenceException e) {
            throw new OperationFailedException(e);
        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

}
