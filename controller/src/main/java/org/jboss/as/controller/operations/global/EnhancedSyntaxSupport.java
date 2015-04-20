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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
class EnhancedSyntaxSupport {

    static final Pattern ENHANCED_SYNTAX_PATTERN = Pattern.compile("(.*[\\.\\[\\]].*)");
    static final Pattern BRACKETS_PATTERN = Pattern.compile("(.*)\\[(-?\\d+)\\]");
    static final Pattern EXTRACT_NAME_PATTERN = Pattern.compile("^(.*?)[\\.\\[].*");

    static boolean containsEnhancedSyntax(String attributeName) {
        return ENHANCED_SYNTAX_PATTERN.matcher(attributeName).matches();
    }

    static String extractAttributeName(String expression) {
        Matcher matcher = EXTRACT_NAME_PATTERN.matcher(expression);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return expression;
        }
    }

    static ModelNode resolveEnhancedSyntax(final String attributeExpression, final ModelNode model) throws OperationFailedException {
        ModelNode result = model;
        for (String part : attributeExpression.split("\\.")) {
            Matcher matcher = BRACKETS_PATTERN.matcher(part);
            if (matcher.matches()) {
                String attribute = matcher.group(1);
                int index = Integer.parseInt(matcher.group(2));
                ModelNode list = attribute.isEmpty() ? result : result.get(attribute); // in case we want to additionally resolve already loaded attribute, this usually applies to attributes that have custom read handlers
                if (list.isDefined() && list.getType() == ModelType.LIST && index >= 0) {
                    result = list.get(index);
                } else {
                    if (index < 0) {
                        throw ControllerLogger.SERVER_LOGGER.couldNotResolveExpressionIndex(attributeExpression, index);
                    } else {
                        throw ControllerLogger.SERVER_LOGGER.couldNotResolveExpressionList(attributeExpression);
                    }
                }
            } else {
                if (result.has(part)) {
                    result = result.get(part);
                } else {
                    throw ControllerLogger.SERVER_LOGGER.couldNotResolveExpression(attributeExpression);
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
                if (index < 0) {
                    result = list.add();
                } else if (list.isDefined() && list.getType() == ModelType.LIST) {
                    result = list.get(index);
                } else {
                    throw ControllerLogger.SERVER_LOGGER.couldNotResolveExpressionList(attributeExpression);
                }
            } else {
                if (!result.isDefined() || result.getType() == ModelType.OBJECT) {
                    result = result.get(part);
                } else {
                    throw ControllerLogger.SERVER_LOGGER.couldNotResolveExpression(attributeExpression);
                }
            }
        }
        result.set(newValue);
        return oldValue;
    }

}
