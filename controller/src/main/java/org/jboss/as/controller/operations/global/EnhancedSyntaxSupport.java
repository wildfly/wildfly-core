/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
class EnhancedSyntaxSupport {

    static final Pattern ENHANCED_SYNTAX_PATTERN = Pattern.compile("(.*[\\.\\[\\]].*)");
    static final Pattern BRACKETS_PATTERN = Pattern.compile("(.*)\\[(-?\\d+)\\]");
    static final Pattern EXTRACT_NAME_PATTERN = Pattern.compile("^(.*?)[\\.\\[].*");

    static void resolveAttribute(OperationContext context, ModelNode operation, String attributeName, boolean defaults, ImmutableManagementResourceRegistration registry, boolean enhancedSyntax) throws OperationFailedException {
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        final ModelNode subModel = resource.getModel();
        if (enhancedSyntax){
            context.getResult().set(resolveEnhancedSyntax(attributeName,subModel));
        }else if (subModel.hasDefined(attributeName)) {
            final ModelNode result = subModel.get(attributeName);
            context.getResult().set(result);
        } else {
            // No defined value in the model. See if we should reply with a default from the metadata,
            // reply with undefined, or fail because it's a non-existent attribute name
            final ModelNode nodeDescription = getNodeDescription(registry, context, operation);
            if (defaults && nodeDescription.get(ATTRIBUTES).hasDefined(attributeName) &&
                    nodeDescription.get(ATTRIBUTES, attributeName).hasDefined(DEFAULT)) {
                final ModelNode result = nodeDescription.get(ATTRIBUTES, attributeName, DEFAULT);
                context.getResult().set(result);
            } else if (subModel.has(attributeName) || nodeDescription.get(ATTRIBUTES).has(attributeName)) {
                // model had no defined value, but we treat its existence in the model or the metadata
                // as proof that it's a legit attribute name
                context.getResult(); // this initializes the "result" to ModelType.UNDEFINED
            } else {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unknownAttribute(attributeName));
            }
        }
    }

    static boolean containsEnhancedSyntax(String attributeName){
        return ENHANCED_SYNTAX_PATTERN.matcher(attributeName).matches();
    }

    static String extractAttributeName(String expression){
        Matcher matcher = EXTRACT_NAME_PATTERN.matcher(expression);
        if (matcher.matches()){
            return matcher.group(1);
        }else {
            return expression;
        }
    }

    static ModelNode resolveEnhancedSyntax(final String attributeExpression, final ModelNode model) throws OperationFailedException {
        ModelNode result = model;
        for (String part : attributeExpression.split("\\.")){
            Matcher matcher = BRACKETS_PATTERN.matcher(part);
            if (matcher.matches()){
                String attribute = matcher.group(1);
                int index = Integer.parseInt(matcher.group(2));
                ModelNode list = attribute.isEmpty()?result:result.get(attribute); // in case we want to additionally resolve already loaded attribute, this usually applies to attributes that have custom read handlers
                if (list.isDefined() && list.getType() == ModelType.LIST){
                    result = list.get(index);
                }else{
                    throw new OperationFailedException(String.format("Could not resolve attribute expression: '%s', type is not a list",attributeExpression));
                }
            }else{
                if (result.has(part)) {
                    result = result.get(part);
                }else{
                    throw new OperationFailedException(String.format("Could not resolve attribute expression: '%s'",attributeExpression));
                }
            }
        }
        return result;
    }

   static ModelNode updateWithEnhancedSyntax(final String attributeExpression, final ModelNode oldValue, final ModelNode newValue) throws OperationFailedException {
            ModelNode result = oldValue;
            for (String part : attributeExpression.split("\\.")) {
                Matcher matcher = BRACKETS_PATTERN.matcher(part);
                if (matcher.matches()) {
                    String attribute = matcher.group(1);
                    int index = Integer.parseInt(matcher.group(2));
                    ModelNode list = attribute.isEmpty() ? result : result.get(attribute); // in case we want to additionally resolve already loaded attribute, this usually applies to attributes that have custom read handlers
                    if (index < 0){
                        result = list.add();
                    }else if (list.isDefined() && list.getType() == ModelType.LIST) {
                        result = list.get(index);
                    }else {
                        throw new OperationFailedException(String.format("Could not resolve attribute expression: '%s', type is not a list", attributeExpression));
                    }
                } else {
                    if (!result.isDefined() || result.getType() == ModelType.OBJECT){
                        result = result.get(part);
                    }else{
                        throw new OperationFailedException(String.format("Could not resolve attribute expression: '%s'",attributeExpression));
                    }
                }
            }
            result.set(newValue);
            return oldValue;
        }

    static ModelNode getNodeDescription(ImmutableManagementResourceRegistration registry, OperationContext context, ModelNode operation) throws OperationFailedException {
        final DescriptionProvider descriptionProvider = registry.getModelDescription(PathAddress.EMPTY_ADDRESS);
        final Locale locale = GlobalOperationHandlers.getLocale(context, operation);
        return descriptionProvider.getModelDescription(locale);
    }
}
