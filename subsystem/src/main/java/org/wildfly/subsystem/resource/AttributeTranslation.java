/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.function.UnaryOperator;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * Describes an attribute translation.
 * @author Paul Ferraro
 */
public interface AttributeTranslation {

    interface AttributeValueTranslator {
        static AttributeValueTranslator IDENTITY = new AttributeValueTranslator() {
            @Override
            public ModelNode translate(OperationContext context, ModelNode value) throws OperationFailedException {
                return value;
            }
        };

        ModelNode translate(OperationContext context, ModelNode value) throws OperationFailedException;
    }

    AttributeDefinition getTargetAttribute();

    /**
     * Returns the translator of an attribute value for use by {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#READ_ATTRIBUTE_OPERATION} operations.
     * @return an attribute translator
     */
    AttributeValueTranslator getReadAttributeOperationTranslator();

    /**
     * Returns the translator of an attribute value for use by {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#WRITE_ATTRIBUTE_OPERATION} operations.
     * @return an attribute translator
     */
    AttributeValueTranslator getWriteAttributeOperationTranslator();

    /**
     * Returns a function that return the target {@link PathAddress} of the resource from which the translated attribute value should be read.
     * @return a path address transformation function
     */
    default UnaryOperator<PathAddress> getPathAddressTranslator() {
        return UnaryOperator.identity();
    }

    static AttributeTranslation alias(AttributeDefinition targetAttribute) {
        return relocate(targetAttribute, UnaryOperator.identity());
    }

    static AttributeTranslation relocate(AttributeDefinition targetAttribute, UnaryOperator<PathAddress> addressTranslator) {
        return new AttributeTranslation() {
            @Override
            public AttributeDefinition getTargetAttribute() {
                return targetAttribute;
            }

            @Override
            public UnaryOperator<PathAddress> getPathAddressTranslator() {
                return addressTranslator;
            }

            @Override
            public AttributeValueTranslator getReadAttributeOperationTranslator() {
                return AttributeValueTranslator.IDENTITY;
            }

            @Override
            public AttributeValueTranslator getWriteAttributeOperationTranslator() {
                return AttributeValueTranslator.IDENTITY;
            }
        };
    }

    static AttributeTranslation singletonList(AttributeDefinition targetAttribute) {
        return new AttributeTranslation() {
            @Override
            public AttributeDefinition getTargetAttribute() {
                return targetAttribute;
            }

            @Override
            public AttributeValueTranslator getReadAttributeOperationTranslator() {
                return new AttributeValueTranslator() {
                    @Override
                    public ModelNode translate(OperationContext context, ModelNode value) throws OperationFailedException {
                        return value.isDefined() ? value.asList().get(0) : value;
                    }
                };
            }

            @Override
            public AttributeValueTranslator getWriteAttributeOperationTranslator() {
                return new AttributeValueTranslator() {
                    @Override
                    public ModelNode translate(OperationContext context, ModelNode value) throws OperationFailedException {
                        return new ModelNode().add(value);
                    }
                };
            }
        };
    }
}
