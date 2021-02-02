/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.EXPRESSION_RESOLVER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.EXPRESSION_RESOLVER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_CAPABILITY;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * The {@link ResourceDefinition} for the expression resolver resource.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ExpressionResolverResourceDefinition extends SimpleResourceDefinition {

    // Resource Resolver
    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER =
            ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION);

    /*
     * As this resource is attempting to make available an ExpressionResolver for use at runtime we need all values to be known before
     * first use so the use of expressions is disabled on this resource.
     */

    private static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
            .setAllowExpression(false)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static final SimpleAttributeDefinition CREDENTIAL_STORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CREDENTIAL_STORE, ModelType.STRING, false)
            .setAllowExpression(false)
            .setMinSize(1)
            .setRestartAllServices()
            .setCapabilityReference(CREDENTIAL_STORE_CAPABILITY, EXPRESSION_RESOLVER_CAPABILITY)
            .build();

    private static final SimpleAttributeDefinition SECRET_KEY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECRET_KEY, ModelType.STRING, false)
            .setAllowExpression(false)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static final ObjectTypeAttributeDefinition RESOLVER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.RESOLVER, NAME, CREDENTIAL_STORE, SECRET_KEY)
            .setRequired(true)
            .setRestartAllServices()
            .build();

    static final ObjectListAttributeDefinition RESOLVERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.RESOLVERS, RESOLVER)
            .setRequired(true)
            .setMinSize(1)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();

    static final SimpleAttributeDefinition DEFAULT_RESOLVER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_RESOLVER, ModelType.STRING, true)
            .setAllowExpression(false)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PREFIX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PREFIX, ModelType.STRING, true)
            .setAllowExpression(false)
            .setDefaultValue(new ModelNode("ENC"))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {RESOLVERS, DEFAULT_RESOLVER, PREFIX};

    private static final AbstractAddStepHandler ADD = new ExpressionResolverAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, EXPRESSION_RESOLVER_RUNTIME_CAPABILITY);

    ExpressionResolverResourceDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION),
                RESOURCE_RESOLVER)
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setCapabilities(EXPRESSION_RESOLVER_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, write);
        }
    }

    static ResourceDefinition getExpressionResolverDefinition() {
        return new ExpressionResolverResourceDefinition();
    }

    private static class ExpressionResolverAddHandler extends BaseAddHandler {

        private ExpressionResolverAddHandler() {
            super(EXPRESSION_RESOLVER_RUNTIME_CAPABILITY, ATTRIBUTES);
        }
    }
}
