/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.operations.global.EnhancedSyntaxSupport.containsEnhancedSyntax;
import static org.jboss.as.controller.operations.global.EnhancedSyntaxSupport.extractAttributeName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.UnauthorizedException;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} reading a single attribute at the given operation address.
 * The required request parameter "name" represents the attribute name.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReadAttributeHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_ATTRIBUTE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(GlobalOperationAttributes.NAME, GlobalOperationAttributes.INCLUDE_DEFAULTS, GlobalOperationAttributes.INCLUDE_UNDEFINED_METRIC_VALUES)
            .setReadOnly()
            .setReplyType(ModelType.OBJECT)
            .build();

    public static final OperationStepHandler INSTANCE = new ReadAttributeHandler();

    private static final SimpleAttributeDefinition RESOLVE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RESOLVE_EXPRESSIONS, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final OperationDefinition RESOLVE_DEFINITION = new SimpleOperationDefinitionBuilder(READ_ATTRIBUTE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(RESOLVE, GlobalOperationAttributes.NAME, GlobalOperationAttributes.INCLUDE_DEFAULTS)
            .setReadOnly()
            .setReplyType(ModelType.OBJECT)
            .build();

    public static final OperationStepHandler RESOLVE_INSTANCE = new ReadAttributeHandler(true);

    private final ParametersValidator validator = new ParametersValidator() {

        @Override
        public void validate(ModelNode operation) throws OperationFailedException {
            super.validate(operation);
            if( operation.hasDefined(ModelDescriptionConstants.RESOLVE_EXPRESSIONS)){
                if(operation.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).asBoolean(false) && !resolvable){
                    throw ControllerLogger.ROOT_LOGGER.unableToResolveExpressions();
                }
            }
        }
    };
    private final OperationStepHandler overrideHandler;
    private final boolean resolvable;

    public ReadAttributeHandler() {
        this(null, null, false);
    }

    public ReadAttributeHandler(boolean resolve){
        this(null, null, resolve);
    }
    ReadAttributeHandler(FilteredData filteredData, OperationStepHandler overrideHandler, boolean resolvable) {
        super(filteredData);
        if( resolvable){
            validator.registerValidator(RESOLVE.getName(), new ModelTypeValidator(ModelType.BOOLEAN, true));
        }
        validator.registerValidator(GlobalOperationAttributes.NAME.getName(), new StringLengthValidator(1));
        validator.registerValidator(GlobalOperationAttributes.INCLUDE_DEFAULTS.getName(), new ModelTypeValidator(ModelType.BOOLEAN, true));
        validator.registerValidator(GlobalOperationAttributes.INCLUDE_UNDEFINED_METRIC_VALUES.getName(), new ModelTypeValidator(ModelType.BOOLEAN, true));
        assert overrideHandler == null || filteredData != null : "overrideHandler only supported with filteredData";
        this.overrideHandler = overrideHandler;
        this.resolvable = resolvable;
    }

    @Override
    void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {

        // Add a step to authorize the attribute read once we determine the value below
        context.addStep(operation, new AuthorizeAttributeReadHandler(filteredData), OperationContext.Stage.MODEL, true);

        final boolean resolve = RESOLVE.resolveModelAttribute(context, operation).asBoolean();
        if( resolve && resolvable ){
            context.addStep(operation, ResolveAttributeHandler.getInstance(), OperationContext.Stage.MODEL, true);
        }

        if (filteredData == null) {
            doExecuteInternal(context, operation);
        } else {
            try {
                if (overrideHandler == null) {
                    doExecuteInternal(context, operation);
                } else {
                    overrideHandler.execute(context, operation);
                }
            } catch (UnauthorizedException ue) {
                // Just report the failure to the filter and complete normally
                PathAddress pa = context.getCurrentAddress();
                filteredData.addReadRestrictedAttribute(pa, operation.get(NAME).asString());
                context.getResult().set(new ModelNode());
            }
        }
    }

    private void doExecuteInternal(OperationContext context, ModelNode operation) throws OperationFailedException {
        validator.validate(operation);
        String attributeName = GlobalOperationAttributes.NAME.resolveModelAttribute(context, operation).asString();
        final boolean defaults = GlobalOperationAttributes.INCLUDE_DEFAULTS.resolveModelAttribute(context,operation).asBoolean();
        final boolean includeUndefinedMetricValue = GlobalOperationAttributes.INCLUDE_UNDEFINED_METRIC_VALUES.resolveModelAttribute(context, operation).asBoolean();
        final ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        final boolean useEnhancedSyntax = containsEnhancedSyntax(attributeName, registry);
        String attributeExpression = attributeName;
        if (useEnhancedSyntax){
            attributeName = extractAttributeName(attributeName);
        }

        final AttributeAccess attributeAccess = registry.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);

        if (attributeAccess == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unknownAttribute(attributeName));
        }
        assert attributeAccess.getAttributeDefinition() != null;

        if (attributeAccess.getReadHandler() == null) {
            resolveAttribute(context, attributeAccess.getAttributeDefinition(), attributeExpression, defaults, useEnhancedSyntax);
        } else {
            OperationStepHandler handler = attributeAccess.getReadHandler();
            ClassLoader oldTccl = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(handler.getClass());
            try {
                handler.execute(context, operation);
            } finally {
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
            }
            if (attributeAccess.getAccessType() == AttributeAccess.AccessType.METRIC) {
                if (!context.getResult().isDefined() && !includeUndefinedMetricValue) {
                    // Use the undefined metric value for the attribute definition instead.
                    ModelNode undefinedMetricValue = attributeAccess.getAttributeDefinition().getUndefinedMetricValue();
                    if (undefinedMetricValue != null) {
                        context.getResult().set(undefinedMetricValue);
                    }
                }
            }
            if (useEnhancedSyntax) {
                // remove attribute name from expression string ("attribute-name.rest" => "rest")
                int prefixLength = attributeName.length();
                if (attributeExpression.charAt(prefixLength) == '.') {
                    prefixLength++; // remove also '.' character if present
                }
                String remainingExpression = attributeExpression.substring(prefixLength);

                if (AttributeAccess.Storage.CONFIGURATION == attributeAccess.getStorageType()) {
                    ModelNode resolved = EnhancedSyntaxSupport.resolveEnhancedSyntax(remainingExpression, context.getResult(),
                            attributeAccess.getAttributeDefinition());
                    context.getResult().set(resolved);
                } else {
                    assert AttributeAccess.Storage.RUNTIME == attributeAccess.getStorageType();

                    // Resolution must be postponed to RUNTIME stage for Storage.RUNTIME attributes.

                    context.addStep((context1, operation1) -> {
                        ModelNode resolved = EnhancedSyntaxSupport.resolveEnhancedSyntax(remainingExpression, context.getResult(),
                                attributeAccess.getAttributeDefinition());
                        context.getResult().set(resolved);
                    }, OperationContext.Stage.RUNTIME);
                }
            }
        }
    }


    private static class AuthorizeAttributeReadHandler implements OperationStepHandler {

        private final FilteredData filteredData;

        private AuthorizeAttributeReadHandler(FilteredData filteredData) {
            this.filteredData = filteredData;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (filteredData == null) {
                doExecuteInternal(context, operation);
            } else {
                try {
                    doExecuteInternal(context, operation);
                } catch (UnauthorizedException ue) {
                    if (context.hasResult()) {
                        context.getResult().set(new ModelNode());
                    }
                    // Report the failure to the filter and complete normally
                    PathAddress pa = context.getCurrentAddress();
                    filteredData.addReadRestrictedAttribute(pa, operation.get(NAME).asString());
                    context.getResult().set(new ModelNode());
                }
            }
        }

        private void doExecuteInternal(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode value = context.hasResult() ? context.getResult().clone() : new ModelNode();
            AuthorizationResult authorizationResult = context.authorize(operation, operation.require(NAME).asString(), value);
            if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
                context.getResult().clear();
                throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.require(OP).asString(), context.getCurrentAddress(), authorizationResult.getExplanation());
            }
        }
    }

    private static class ResolveAttributeHandler implements OperationStepHandler {

        private ResolveAttributeHandler(){}

        private static class ResolveAttributeHandlerHolder {
            private static final ResolveAttributeHandler INSTANCE = new ResolveAttributeHandler();
        }

        public static ResolveAttributeHandler getInstance(){
            return ResolveAttributeHandlerHolder.INSTANCE;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode result = context.hasResult() ? context.getResult().clone() : new ModelNode();
            // For now, don't use the context to resolve, as we don't want to support vault resolution
            // from a remote management client. The purpose of the vault is to require someone to have
            // access to both the config (i.e. the expression) and to the vault itself in order to read, and
            // allowing a remote user to use the management API to read defeats the purpose.
            //ModelNode resolved = context.resolveExpressions(result);
            // Instead we use a resolver that will not complain about unresolvable stuff (i.e. vault expressions),
            // simply returning them unresolved.
            ModelNode resolved = ExpressionResolver.SIMPLE_LENIENT.resolveExpressions(result);
            context.getResult().set(resolved);
        }
    }

    static void resolveAttribute(OperationContext context, AttributeDefinition attribute, String attributeSyntax, boolean defaults, boolean enhancedSyntax) throws OperationFailedException {
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        final ModelNode subModel = resource.getModel();
        if (enhancedSyntax) {
            context.getResult().set(EnhancedSyntaxSupport.resolveEnhancedSyntax(attributeSyntax, subModel, attribute));
        } else if (subModel.hasDefined(attribute.getName())) {
            final ModelNode result = subModel.get(attribute.getName());
            context.getResult().set(result);
        } else if (defaults && attribute.getDefaultValue() != null) {
            // No defined value in the model. See if we should reply with a default from the metadata,
            // reply with undefined, or fail because it's a non-existent attribute name
            context.getResult().set(attribute.getDefaultValue());
        } else {
            // model had no defined value, but we treat its existence in the model or the metadata
            // as proof that it's a legit attribute name
            context.getResult(); // this initializes the "result" to ModelType.UNDEFINED
        }
    }
}
