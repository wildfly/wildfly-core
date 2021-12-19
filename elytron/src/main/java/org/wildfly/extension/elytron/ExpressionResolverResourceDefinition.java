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

import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.EXPRESSION_RESOLVER_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ExpressionResolverExtension;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.elytron.expression.ElytronExpressionResolver;
import org.wildfly.extension.elytron.expression.ElytronExpressionResolver.ResolverConfiguration;
import org.wildfly.extension.elytron.expression.ExpressionResolverRuntimeHandler;

/**
 * The {@link ResourceDefinition} for the expression resolver resource.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ExpressionResolverResourceDefinition extends SimpleResourceDefinition {

    public static final Pattern INITIAL_PATTERN = Pattern.compile("\\$\\{.+::.*:.+}");

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

    // Operation and Parameters

    static final SimpleAttributeDefinition RESOLVER_PARAM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RESOLVER, ModelType.STRING, true)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition CLEAR_TEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CLEAR_TEXT, ModelType.STRING, false)
            .setMinSize(1)
            .build();

    static final SimpleOperationDefinition CREATE_EXPRESSION = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.CREATE_EXPRESSION, RESOURCE_RESOLVER)
            .setParameters(RESOLVER_PARAM, CLEAR_TEXT)
            .setRuntimeOnly()
            .build();

    ExpressionResolverResourceDefinition(OperationStepHandler add, OperationStepHandler remove,
            RuntimeCapability<ExpressionResolverExtension> expressionResolverRuntimeCapability) {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION),
                RESOURCE_RESOLVER)
                .setAddHandler(add)
                .setRemoveHandler(remove)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setCapabilities(expressionResolverRuntimeCapability));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, write);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration); // Needed for add / remove.
        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerOperationHandler(CREATE_EXPRESSION, new CreateExpressionHandler());
        }
    }

    static void configureExpressionResolver(PathAddress resourceAddress, ElytronExpressionResolver expressionResolver, OperationContext context) throws OperationFailedException {
        // The OperationContext could be for any resource across the management model.
        ModelNode expressionEncryption = context.readResourceFromRoot(resourceAddress).getModel();

        String prefix = PREFIX.resolveModelAttribute(context, expressionEncryption).asString();

        String defaultResolver = DEFAULT_RESOLVER.resolveModelAttribute(context, expressionEncryption).asStringOrNull();

        Map<String, ResolverConfiguration> resolverConfigurations = new HashMap<>();
        for (ModelNode currentResolver : RESOLVERS.resolveModelAttribute(context, expressionEncryption).asList()) {
            String name = NAME.resolveModelAttribute(context, currentResolver).asString();
            String credentialStoreName = CREDENTIAL_STORE.resolveModelAttribute(context, currentResolver).asString();
            String alias = SECRET_KEY.resolveModelAttribute(context, currentResolver).asString();

            resolverConfigurations.put(name, new ResolverConfiguration(credentialStoreName, alias));
        }

        expressionResolver.setPrefix(prefix)
            .setDefaultResolver(defaultResolver)
            .setResolverConfigurations(resolverConfigurations);
    }

    static ResourceDefinition getExpressionResolverDefinition(PathAddress parentAddress, AtomicReference<ExpressionResolverExtension> resolverRef) {
        final PathAddress resourceAddress = parentAddress.append(PathElement.pathElement(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION));

        ElytronExpressionResolver expressionResolver = new ElytronExpressionResolver(
                (e, c) -> configureExpressionResolver(resourceAddress, e, c));
        RuntimeCapability<ExpressionResolverExtension> expressionResolverRuntimeCapability =  RuntimeCapability
                .Builder.<ExpressionResolverExtension>of(EXPRESSION_RESOLVER_CAPABILITY, false, expressionResolver)
                .build();

        AbstractAddStepHandler add = new ExpressionResolverAddHandler(expressionResolverRuntimeCapability, resolverRef);
        OperationStepHandler remove = new ExpressionResolverRemoveHandler(expressionResolverRuntimeCapability, resolverRef);

        return new ExpressionResolverResourceDefinition(add, remove, expressionResolverRuntimeCapability);
    }

    private static class ExpressionResolverAddHandler extends BaseAddHandler {

        private final ElytronExpressionResolver expressionResolver;
        private final AtomicReference<ExpressionResolverExtension> resolverRef;

        ExpressionResolverAddHandler(RuntimeCapability<ExpressionResolverExtension> expressionResolverRuntimeCapability,
                                     AtomicReference<ExpressionResolverExtension> resolverRef) {
            super(expressionResolverRuntimeCapability, ATTRIBUTES);
            this.expressionResolver = (ElytronExpressionResolver) expressionResolverRuntimeCapability.getRuntimeAPI();
            this.resolverRef = resolverRef;
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
            super.populateModel(context, operation, resource);
            resolverRef.set(expressionResolver);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

            ExpressionResolverRuntimeHandler.initializeResolver(context);
        }

    }

    private static class ExpressionResolverRemoveHandler extends AbstractRemoveStepHandler implements ElytronOperationStepHandler {

        private final ElytronExpressionResolver expressionResolver;
        private final AtomicReference<ExpressionResolverExtension> resolverRef;

        ExpressionResolverRemoveHandler(RuntimeCapability<ExpressionResolverExtension> expressionResolverRuntimeCapability,
                                        AtomicReference<ExpressionResolverExtension> resolverRef) {
            super(expressionResolverRuntimeCapability);
            this.expressionResolver = (ElytronExpressionResolver) expressionResolverRuntimeCapability.getRuntimeAPI();
            this.resolverRef = resolverRef;
        }

        @Override
        protected boolean requiresRuntime(final OperationContext context) {
            return isServerOrHostController(context);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
            if (context.isResourceServiceRestartAllowed()) {
                resolverRef.set(null);
            }
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            resolverRef.set(this.expressionResolver);
        }

    }

    private static class CreateExpressionHandler extends ElytronRuntimeOnlyHandler {

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            String resolver = RESOLVER_PARAM.resolveModelAttribute(context, operation).asStringOrNull();
            String clearText = CLEAR_TEXT.resolveModelAttribute(context, operation).asString();

            ElytronExpressionResolver expressionResolver = (ElytronExpressionResolver) context.getCapabilityRuntimeAPI(EXPRESSION_RESOLVER_CAPABILITY, ExpressionResolverExtension.class);
            String expression = expressionResolver.createExpression(resolver, clearText, context);

            context.getResult().get(ElytronDescriptionConstants.EXPRESSION).set(expression);
        }

    }

}
