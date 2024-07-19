/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test.transformers.subsystem.similarity;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar
 * @created 19.12.11 21:04
 */
public class SessionDefinition extends SimpleResourceDefinition {
    public static SessionDefinition INSTANCE = new SessionDefinition();

    private SessionDefinition() {
        super(PathElement.pathElement("session"), NonResolvingResourceDescriptionResolver.INSTANCE);
    }

    protected static final SimpleAttributeDefinition JNDI_NAME =
            new SimpleAttributeDefinitionBuilder("jndi-name", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setRestartAllServices()
                    .build();
    protected static final SimpleAttributeDefinition FROM =
            new SimpleAttributeDefinitionBuilder("from", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setDefaultValue(null)
                    .setRestartAllServices()
                    .setRequired(false)
                    .build();
    protected static final SimpleAttributeDefinition DEBUG =
            new SimpleAttributeDefinitionBuilder("debug", ModelType.BOOLEAN, true)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .setRestartAllServices()
                    .build();
    private static final AttributeDefinition[] ATTRIBUTES = {DEBUG, JNDI_NAME, FROM};

    @Override
    public void registerAttributes(final ManagementResourceRegistration registry) {
        for (AttributeDefinition attr : ATTRIBUTES) {
            registry.registerReadWriteAttribute(attr, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registry) {
        super.registerOperations(registry);
        registry.registerOperationHandler(createOperationDefinition("dump-session-info", DEBUG), NoopOperationStepHandler.WITHOUT_RESULT);
    }

    private static OperationDefinition createOperationDefinition(String name, AttributeDefinition... parameters) {
        return new SimpleOperationDefinitionBuilder(name, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setParameters(parameters)
                .build();
    }
}
