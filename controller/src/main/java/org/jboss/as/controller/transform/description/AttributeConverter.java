/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.dmr.ModelNode;

/**
 * Used to convert an individual attribute/operation parameter value during transformation.
 * Conversion can both mean modifying an existing attribute/parameter, or adding a new one.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface AttributeConverter {

    /**
     * Converts an operation parameter
     *
     * @param address the address of the operation
     * @param attributeName the name of the operation parameter
     * @param attributeValue the value of the operation parameter to be converted
     * @param operation the operation executed. This is unmodifiable.
     * @param context the context of the transformation
     */
    void convertOperationParameter(PathAddress address, String attributeName, ModelNode attributeValue, ModelNode operation, TransformationContext context);

    /**
     * Converts a resource attribute
     *
     * @param address the address of the operation
     * @param attributeName the name of the attribute
     * @param attributeValue the value of the attribute to be converted
     * @param context the context of the transformation
     */
    void convertResourceAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context);

    /**
     * A default implementation of AttributeConverter
     *
     * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
     */
    public abstract class DefaultAttributeConverter implements AttributeConverter {

        /** {@inheritDoc} */
        @Override
        public void convertOperationParameter(PathAddress address, String attributeName, ModelNode attributeValue, ModelNode operation, TransformationContext context) {
            convertAttribute(address, attributeName, attributeValue, context);
        }

        /** {@inheritDoc} */
        @Override
        public void convertResourceAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            convertAttribute(address, attributeName, attributeValue, context);
        }

        /**
         * Gets called by the default implementations of {@link #convertOperationParameter(PathAddress, String, ModelNode, ModelNode, TransformationContext)} and
         * {@link #convertResourceAttribute(PathAddress, String, ModelNode, TransformationContext)}.
         *
         * @param address the address of the operation or resource
         * @param attributeName the name of the attribute
         * @param attributeValue the value of the attribute
         * @param context the context of the transformation
         */
        protected abstract void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context);
    }

    /**
     * Converter for an existing attribute whose default value has changed.
     * @author Paul Ferraro
     */
    AttributeConverter DEFAULT_VALUE = new AttributeConverter() {
        @Override
        public void convertOperationParameter(PathAddress address, String attributeName, ModelNode attributeValue, ModelNode operation, TransformationContext context) {
            if (!attributeValue.isDefined()) {
                String operationName = operation.get(ModelDescriptionConstants.OP).asString();
                if (operationName.equals(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION)) {
                    this.convertResourceAttribute(address, attributeName, attributeValue, context);
                } else {
                    ImmutableManagementResourceRegistration registration = context.getResourceRegistrationFromRoot(PathAddress.EMPTY_ADDRESS);
                    OperationDefinition definition = registration.getOperationEntry(address, operationName).getOperationDefinition();
                    for (AttributeDefinition parameter : definition.getParameters()) {
                        if (parameter.getName().equals(attributeName)) {
                            attributeValue.set(parameter.getDefaultValue());
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void convertResourceAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (!attributeValue.isDefined()) {
                ImmutableManagementResourceRegistration registration = context.getResourceRegistrationFromRoot(PathAddress.EMPTY_ADDRESS);
                AttributeDefinition definition = registration.getAttributeAccess(address, attributeName).getAttributeDefinition();
                attributeValue.set(definition.getDefaultValue());
            }
        }
    };

    /**
     * Factory for common types of AttributeConverters
     */
    public static class Factory {

        /**
         * Creates an AttributeConverter where the conversion is to a hard-coded value
         *
         * @param hardCodedValue the value to set the attribute to
         * @return the created attribute converter
         */
        public static AttributeConverter createHardCoded(final ModelNode hardCodedValue) {
            return createHardCoded(hardCodedValue, false);
        }

        /**
         * Creates an AttributeConverter where the conversion is to a hard-coded value, with
         * the ability to restrict the conversion to cases where the value being converted is
         * {@link org.jboss.dmr.ModelType#UNDEFINED}.
         * <p>
         * The expected use case for setting the {@code undefinedOnly} param to {@code true} is to
         * ensure a legacy slave sees the correct value following a change in a default between releases.
         * If the attribute being converted is undefined, then the default value is relevant, and in order
         * to function consistently with newer slaves, the legacy slave will need to be given the new
         * default in place of "undefined".
         * </p>
         *
         * @param hardCodedValue the value to set the attribute to
         * @param undefinedOnly {@code true} if the conversion should only occur if the {@code attributeValue}
         *                                  param is {@link org.jboss.dmr.ModelType#UNDEFINED}
         * @return the created attribute converter
         */
        public static AttributeConverter createHardCoded(final ModelNode hardCodedValue, final boolean undefinedOnly) {
            return new DefaultAttributeConverter() {
                @Override
                public void convertAttribute(PathAddress address, String name, ModelNode attributeValue, TransformationContext context) {
                    if (!undefinedOnly || !attributeValue.isDefined()) {
                        attributeValue.set(hardCodedValue);
                    }
                }
            };
        }
    }

    /**
     * An attribute converter which converts the attribute value to be the value of the last {@link PathElement} in the {@link PathAddress}
     */
    AttributeConverter NAME_FROM_ADDRESS = new DefaultAttributeConverter() {
        @Override
        public void convertAttribute(PathAddress address, String name, ModelNode attributeValue, TransformationContext context) {
            PathElement element = address.getLastElement();
            attributeValue.set(element.getValue());
        }
    };
}
