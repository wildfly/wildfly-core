/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A transformer for a single attribute
 *
 * @author Emanuel Muckenhuber
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class AttributeTransformationDescription {

    final String name;
    final List<RejectAttributeChecker> checks;
    final String newName;
    final DiscardAttributeChecker discardChecker;
    final AttributeConverter converter;


    AttributeTransformationDescription(String name, List<RejectAttributeChecker> checks, String newName, DiscardAttributeChecker discardChecker, AttributeConverter converter) {
        this.name = name;
        this.checks = checks != null ? checks : Collections.<RejectAttributeChecker>emptyList();
        this.newName = newName;
        this.discardChecker = discardChecker;
        this.converter = converter;
    }

    boolean shouldDiscard(PathAddress address, ModelNode attributeValue, ModelNode operation, TransformationRule.AbstractChainedContext context) {
        if (discardChecker == null) {
            return false;
        }

        if (discardChecker.isDiscardUndefined() && !attributeValue.isDefined()) {
            return true;
        }

        if (!discardChecker.isDiscardExpressions() && attributeValue.getType() == ModelType.EXPRESSION) {
            return false;
        }

        if (operation != null) {
            if (discardChecker.isOperationParameterDiscardable(address, name, attributeValue, operation, context.getContext())) {
                return true;
            }
        } else {
            if (discardChecker.isResourceAttributeDiscardable(address, name, attributeValue, context.getContext())) {
                return true;
            }
        }
        return false;
    }

    String getNewName() {
        return newName;
    }

    /**
     * Checks attributes for rejection
     *
     * @param rejectedAttributes gathers information about failed attributes
     * @param attributeValue the attribute value
     */
    void rejectAttributes(RejectedAttributesLogContext rejectedAttributes, ModelNode attributeValue) {
        for (RejectAttributeChecker checker : checks) {
            rejectedAttributes.checkAttribute(checker, name, attributeValue);
        }
    }

    void isExistingValue(PathAddress address, ModelNode attributeValue, ModelNode operation, TransformationRule.AbstractChainedContext context) {
        if (converter != null) {
            if (operation != null) {
                converter.convertOperationParameter(address, name, attributeValue, operation, context.getContext());
            } else {
                converter.convertResourceAttribute(address, name, attributeValue, context.getContext());
            }
        }
    }

    void convertValue(PathAddress address, ModelNode attributeValue, ModelNode operation, TransformationRule.AbstractChainedContext context) {
        if (converter != null) {
            if (operation != null) {
                converter.convertOperationParameter(address, name, attributeValue, operation, context.getContext());
            } else {
                converter.convertResourceAttribute(address, name, attributeValue, context.getContext());
            }
        }
    }
}
