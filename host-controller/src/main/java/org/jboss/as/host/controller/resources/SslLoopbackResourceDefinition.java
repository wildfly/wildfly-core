/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.resources;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A {@link ResourceDefinition} to hold the configuration attributes for how SSL is handled when the application server instance
 * connects back to it's host controller.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SslLoopbackResourceDefinition extends SimpleResourceDefinition {

    private static final String DESCRIPTION_PREFIX = SERVER_CONFIG + "." + ModelDescriptionConstants.SSL + "." + ModelDescriptionConstants.LOOPBACK;

    public static final SimpleAttributeDefinition SSL_PROTOCOCOL = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SSL_PROTOCOL, ModelType.STRING, true)
            .setDefaultValue(new ModelNode("TLS"))
            .setAllowExpression(true)
            .build();

    /*
     * Note: The algorithm and type don't have a default specified, however if not set they will default to the default of the JVM where the SSLContext is initialised - this is
     * however a different JVM to the JMV where the model exists.
     */

    public static final SimpleAttributeDefinition TRUST_MANAGER_ALGORITHM = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TRUST_MANAGER_ALGORITHM, ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition TRUSTSTORE_TYPE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TRUSTSTORE_TYPE, ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition TRUSTSTORE_PATH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TRUSTSTORE_PATH, ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition TRUSTSTORE_PASSWORD = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TRUSTSTORE_PASSWORD, ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { SSL_PROTOCOCOL, TRUST_MANAGER_ALGORITHM, TRUSTSTORE_TYPE, TRUSTSTORE_PATH, TRUSTSTORE_PASSWORD };

    private final List<AccessConstraintDefinition> sensitivity;

    public SslLoopbackResourceDefinition() {
        super(PathElement.pathElement(ModelDescriptionConstants.SSL, ModelDescriptionConstants.LOOPBACK),
                HostResolver.getResolver(DESCRIPTION_PREFIX, false),
                new SslLoopbackAddHandler(),
                new SslLoopbackRemoveHandler());
        sensitivity = SensitiveTargetAccessConstraintDefinition.SERVER_SSL.wrapAsList();
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler handler = new ModelOnlyWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return sensitivity;
    }

    static class SslLoopbackAddHandler extends AbstractAddStepHandler {

        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            for (AttributeDefinition attr : ATTRIBUTES) {
                attr.validateAndSet(operation, model);
            }
        }

        protected boolean requiresRuntime(OperationContext context) {
            return false;
        }
    }

    static class SslLoopbackRemoveHandler extends AbstractRemoveStepHandler {

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return false;
        }
    }

}
