/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
class EnhancedSyntaxSupport {

    static final Pattern ENHANCED_SYNTAX_PATTERN = Pattern.compile("(.*[\\.\\[\\]].*)");
    static final Pattern BRACKETS_PATTERN = Pattern.compile("(.*)\\[(-?\\d+)\\]");
    static final Pattern EXTRACT_NAME_PATTERN = Pattern.compile("^(.*?)[\\.\\[].*");

    static boolean containsEnhancedSyntax(String attributeName, ImmutableManagementResourceRegistration registry) {
        return registry.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName) == null
                && ENHANCED_SYNTAX_PATTERN.matcher(attributeName).matches();
    }

    static String extractAttributeName(String expression) {
        Matcher matcher = EXTRACT_NAME_PATTERN.matcher(expression);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return expression;
        }
    }

    static ModelNode resolveEnhancedSyntax(final String attributeExpression, final ModelNode model, AttributeDefinition attributeDefinition) throws OperationFailedException {
        assert attributeExpression != null;
        ModelNode result = model;
        String[] parts = attributeExpression.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            Matcher matcher = BRACKETS_PATTERN.matcher(part);
            if (matcher.matches()) {
                String attribute = matcher.group(1);
                int index = Integer.parseInt(matcher.group(2));
                ModelNode list = attribute.isEmpty() ? result : result.get(attribute); // in case we want to additionally resolve already loaded attribute, this usually applies to attributes that have custom read handlers
                if (list.isDefined() && list.getType() == ModelType.LIST && index >= 0) {
                    result = list.get(index);
                    if (i < parts.length - 1) {
                        // If this is a list of objects, track the AD in case we need it to validate later parts
                        attributeDefinition = attributeDefinition instanceof ObjectListAttributeDefinition
                                ? ((ObjectListAttributeDefinition) attributeDefinition).getValueType()
                                : null;
                    }
                } else {
                    if (index < 0) {
                        throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpressionIndex(attributeExpression, index);
                    } else {
                        throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpressionList(attributeExpression);
                    }
                }
            } else {
                if (i < parts.length - 1 && !result.hasDefined(part)) {
                    // We can't read later parts when this one is undefined
                    throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
                }
                // Now we are either on the last part or the desired part is defined
                if (result.has(part)) {
                    result = result.get(part);
                } else {
                    // We are on the last part but it's not in the model. So we don't know if it's a valid part.
                    // See if the current AD can tell us.
                    if (attributeDefinition instanceof ObjectTypeAttributeDefinition) {
                        // See if the part matches a field
                        AttributeDefinition[] fields = ((ObjectTypeAttributeDefinition) attributeDefinition).getValueTypes();
                        attributeDefinition = null;
                        for (AttributeDefinition field : fields) {
                            if (field.getName().equals(part)) {
                                attributeDefinition = field;
                                break;
                            }
                        }
                        if (attributeDefinition == null) { // No match
                            throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
                        }
                        result = result.get(part);
                    } else {
                        // AD can't tell us so we have to assume invalid
                        throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
                    }
                }
            }
        }
        return result;
    }

    static ModelNode updateWithEnhancedSyntax(final String attributeExpression, final ModelNode oldValue, final ModelNode newValue,
                                              AttributeDefinition attributeDefinition) throws OperationFailedException {
        assert attributeExpression != null;
        ModelNode result = oldValue;
        String[] parts = attributeExpression.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            Matcher matcher =  BRACKETS_PATTERN.matcher(part);
            if (matcher.matches()) {
                if (attributeDefinition != null && attributeDefinition.getType() != ModelType.LIST) {
                    throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpressionList(attributeExpression);
                }
                String attribute = matcher.group(1);
                int index;
                try {
                    index = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException e) {
                    throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
                }
                ModelNode list = attribute.isEmpty() ? result : result.get(attribute); // in case we want to additionally resolve already loaded attribute, this usually applies to attributes that have custom read handlers
                if (index < 0) {
                    result = list.add();
                } else if (list.isDefined() && list.getType() == ModelType.LIST) {
                    result = list.get(index);
                } else {
                    throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpressionList(attributeExpression);
                }
                if (i < parts.length - 1) {
                    // More parts implies this is a list of objects, so if the AD type allows it
                    // track the AD of the value type in order to validate later parts
                    attributeDefinition = attributeDefinition instanceof ObjectListAttributeDefinition
                            ? ((ObjectListAttributeDefinition) attributeDefinition).getValueType()
                            : null; // oh well, AD impl won't let us validate further
                }
            } else if (attributeDefinition != null && !part.equals(attributeDefinition.getName())) {
                // Unknown part
                throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
            } else if (attributeDefinition == null && part.contains("[")) {
                // A valid attribute or field name wouldn't include an unmatched '['.
                throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
            } else {
                if (!result.isDefined() || result.getType() == ModelType.OBJECT) {
                    result = result.get(part);
                } else {
                    throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
                }
            }

            if (i < parts.length - 1) {
                // Try to find the AD for the next part to use in validating it
                if (attributeDefinition instanceof ObjectTypeAttributeDefinition) {
                    String nextPart = parts[i + 1];
                    int bracket = nextPart.indexOf('[');
                    nextPart = bracket < 0 ? nextPart : nextPart.substring(0, bracket);
                    // See if the next part matches a field
                    AttributeDefinition[] fields = ((ObjectTypeAttributeDefinition) attributeDefinition).getValueTypes();
                    attributeDefinition = null;
                    for (AttributeDefinition field : fields) {
                        if (field.getName().equals(nextPart)) {
                            attributeDefinition = field;
                            break;
                        }
                    }
                    if (attributeDefinition == null) {
                        throw ControllerLogger.MGMT_OP_LOGGER.couldNotResolveExpression(attributeExpression);
                    }
                } else {
                    // oh well, AD impl won't let us validate further
                    attributeDefinition = null;
                }
            }
        }
        result.set(newValue);
        return oldValue;
    }

}
