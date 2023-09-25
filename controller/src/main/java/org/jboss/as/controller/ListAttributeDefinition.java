/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;


import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ListValidator;
import org.jboss.as.controller.operations.validation.NillableOrExpressionParameterValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.common.Assert;

/**
 * Defining characteristics of an {@link ModelType#LIST} attribute in a {@link org.jboss.as.controller.registry.Resource}, with utility
 * methods for conversion to and from xml and for validation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class ListAttributeDefinition extends AttributeDefinition {

    private final ParameterValidator elementValidator;

    protected ListAttributeDefinition(ListAttributeDefinition.Builder<?, ?> builder) {
        super(builder);
        this.elementValidator = builder.getElementValidator();
    }

    /**
     * The validator used to validate elements in the list.
     * @return  the element validator
     */
    @SuppressWarnings("WeakerAccess")
    public ParameterValidator getElementValidator() {
        return elementValidator;
    }

    /**
     * Returns an AttributeDefinition describing the content of the list.
     * @return an AttributeDefinition describing the content of the list - null if none is defined.
     */
    public AttributeDefinition getValueAttributeDefinition() {
        return null;
    }

    /**
     * Creates a {@link ModelNode} using the given {@code value} after first validating the node
     * against {@link #getElementValidator() this object's element validator}, and then stores it in the given {@code operation}
     * model node as an element in a {@link ModelType#LIST} value in a key/value pair whose key is this attribute's
     * {@link #getName() name}.
     * <p>
     * If {@code value} is {@code null} an {@link ModelType#UNDEFINED undefined} node will be stored if such a value
     * is acceptable to the validator.
     * </p>
     * <p>
     * The expected usage of this method is in parsers seeking to build up an operation to store their parsed data
     * into the configuration.
     * </p>
     *
     * @param value the value. Will be {@link String#trim() trimmed} before use if not {@code null}.
     * @param operation model node of type {@link ModelType#OBJECT} into which the parsed value should be stored
     * @param reader {@link XMLStreamReader} from which the {@link XMLStreamReader#getLocation() location} from which
     *               the attribute value was read can be obtained and used in any {@code XMLStreamException}, in case
     *               the given value is invalid.
     * @throws XMLStreamException if {@code value} is not valid
     * @deprecated use {@link #getParser()}
     */
    @Deprecated
    public void parseAndAddParameterElement(final String value, final ModelNode operation, final XMLStreamReader reader) throws XMLStreamException {
        ModelNode paramVal = AttributeParser.SIMPLE.parse(this, value, reader);
        operation.get(getName()).add(paramVal);
    }

    @Override
    public ModelNode addResourceAttributeDescription(ResourceBundle bundle, String prefix, ModelNode resourceDescription) {
        final ModelNode result = super.addResourceAttributeDescription(bundle, prefix, resourceDescription);
        addValueTypeDescription(result, bundle);
        return result;
    }

    @Override
    public ModelNode addResourceAttributeDescription(ModelNode resourceDescription, ResourceDescriptionResolver resolver,
                                                     Locale locale, ResourceBundle bundle) {
        final ModelNode result = super.addResourceAttributeDescription(resourceDescription, resolver, locale, bundle);
        addAttributeValueTypeDescription(result, resolver, locale, bundle);
        return result;
    }

    @Override
    public ModelNode addOperationParameterDescription(ModelNode resourceDescription, String operationName,
                                                      ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        final ModelNode result = super.addOperationParameterDescription(resourceDescription, operationName, resolver, locale, bundle);
        addOperationParameterValueTypeDescription(result, operationName, resolver, locale, bundle);
        return result;
    }

    @Override
    public ModelNode addOperationParameterDescription(ResourceBundle bundle, String prefix, ModelNode operationDescription) {
        final ModelNode result = super.addOperationParameterDescription(bundle, prefix, operationDescription);
        addValueTypeDescription(result, bundle);
        return result;
    }

    @Override
    public ModelNode addOperationReplyDescription(final ModelNode resourceDescription, final String operationName,
                                                      final ResourceDescriptionResolver resolver,
                                                      final Locale locale, final ResourceBundle bundle) {
        final ModelNode result = super.addOperationReplyDescription(resourceDescription, operationName, resolver, locale, bundle);
        addOperationReplyValueTypeDescription(result, operationName, resolver, locale, bundle);
        return result;
    }

    protected abstract void addValueTypeDescription(final ModelNode node, final ResourceBundle bundle);

    protected abstract void addAttributeValueTypeDescription(final ModelNode node, final ResourceDescriptionResolver resolver,
                                                             final Locale locale, final ResourceBundle bundle);

    protected abstract void addOperationParameterValueTypeDescription(final ModelNode node, final String operationName,
                                                                      final ResourceDescriptionResolver resolver,
                                                                      final Locale locale, final ResourceBundle bundle);

    protected void addOperationReplyValueTypeDescription(final ModelNode node, final String operationName,
                                                                      final ResourceDescriptionResolver resolver,
                                                                      final Locale locale, final ResourceBundle bundle) {
        //TODO WFCORE-1178: use reply value types description instead of parameter value type
        addOperationParameterValueTypeDescription(node, operationName, resolver, locale, bundle);
    }

    @Override
    public void marshallAsElement(ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
        getMarshaller().marshallAsElement(this, resourceModel, marshallDefault, writer);
    }

    /**
     * Iterates through the elements in the {@code parameter} list, calling {@link #convertParameterElementExpressions(ModelNode)}
     * for each.
     * <p>
     * <strong>Note</strong> that the default implementation of {@link #convertParameterElementExpressions(ModelNode)}
     * will only convert simple {@link ModelType#STRING} elements. If users need to handle complex elements
     * with embedded expressions, they should use a subclass that overrides that method.
     * </p>
     *
     * {@inheritDoc}
     */
    @Override
    protected ModelNode convertParameterExpressions(ModelNode parameter) {
        ModelNode result = parameter;

        List<ModelNode> asList;
        try {
            asList = parameter.asList();
        } catch (IllegalArgumentException iae) {
            // We can't convert; we'll just return parameter
            asList = null;
        }

        if (asList != null) {
            boolean changeMade = false;
            ModelNode newList = new ModelNode().setEmptyList();
            for (ModelNode item : asList) {
                ModelNode converted = convertParameterElementExpressions(item);
                newList.add(converted);
                changeMade |= !converted.equals(item);
            }
            if (changeMade) {
                result = newList;
            }
        }
        return result;
    }

    /**
     * Examine the given element of a parameter list for any expression syntax, converting the relevant node to
     * {@link ModelType#EXPRESSION} if such is supported. This implementation will only convert elements of
     * {@link ModelType#STRING}. Subclasses that need to handle complex elements should override this method.
     *
     * @param parameterElement the node to examine. Will not be {@code null}
     * @return the parameter element with expressions converted, or the original parameter if no conversion was performed
     *         Cannot return {@code null}
     */
    protected ModelNode convertParameterElementExpressions(ModelNode parameterElement) {
        return isAllowExpression() ? convertStringExpression(parameterElement) : parameterElement;
    }

    public abstract static class Builder<BUILDER extends Builder, ATTRIBUTE extends ListAttributeDefinition>
            extends AbstractAttributeDefinitionBuilder<BUILDER, ATTRIBUTE> {

        private ParameterValidator elementValidator;
        private Boolean allowNullElement;
        private boolean allowDuplicates = true;

        protected Builder(String attributeName) {
            super(attributeName, ModelType.LIST);
            this.setAttributeParser(AttributeParser.STRING_LIST);
        }

        protected Builder(String attributeName, boolean optional) {
            super(attributeName, ModelType.LIST, optional);
            this.setAttributeParser(AttributeParser.STRING_LIST);
        }

        public Builder(ListAttributeDefinition basis) {
            super(basis);
            this.elementValidator = basis.getElementValidator();
        }

        /**
         * Gets the validator to use for validating list elements. En
         * @return the validator, or {@code null} if no validator has been set
         */
        @SuppressWarnings("WeakerAccess")
        public ParameterValidator getElementValidator() {
            if (elementValidator == null) {
                return null;
            }

            ParameterValidator toWrap = elementValidator;
            ParameterValidator wrappedElementValidator = null;
            if (elementValidator instanceof  NillableOrExpressionParameterValidator) {
                // See if it's configured correctly already; if so don't re-wrap
                NillableOrExpressionParameterValidator wrapped = (NillableOrExpressionParameterValidator) elementValidator;
                Boolean allow = wrapped.getAllowNull();
                if ((allow == null || allow) == getAllowNullElement()
                        && wrapped.isAllowExpression() == isAllowExpression()) {
                    wrappedElementValidator = wrapped;
                } else {
                    // re-wrap
                    toWrap = wrapped.getDelegate();
                }
            }
            if (wrappedElementValidator == null) {
                elementValidator = new NillableOrExpressionParameterValidator(toWrap, getAllowNullElement(), isAllowExpression());
            }
            return elementValidator;
        }

        /**
         * Sets the validator to use for validating list elements.
         *
         * @param elementValidator the validator
         * @return a builder that can be used to continue building the attribute definition
         *
         * @throws java.lang.IllegalArgumentException if {@code elementValidator} is {@code null}
         */
        @SuppressWarnings("unchecked")
        public final BUILDER setElementValidator(ParameterValidator elementValidator) {
            Assert.checkNotNullParam("elementValidator", elementValidator);
            this.elementValidator = elementValidator;
            // Setting an element validator invalidates any existing overall attribute validator
            super.setValidator(null);
            return (BUILDER) this;
        }

        /**
         * Overrides the superclass to simply delegate to
         * {@link #setElementValidator(org.jboss.as.controller.operations.validation.ParameterValidator)}.
         * Use {@link #setListValidator(org.jboss.as.controller.operations.validation.ParameterValidator)} to
         * set an overall validator for the list.
         *
         * @param validator the validator. Cannot be {@code null}
         * @return a builder that can be used to continue building the attribute definition
         *
         * @throws java.lang.IllegalArgumentException if {@code elementValidator} is {@code null}
         */
        @Override
        public BUILDER setValidator(ParameterValidator validator) {
            return setElementValidator(validator);
        }

        /**
         * Sets an overall validator for the list.
         *
         * @param validator the validator. {@code null} is allowed
         * @return a builder that can be used to continue building the attribute definition
         */
        @SuppressWarnings("WeakerAccess")
        public BUILDER setListValidator(ParameterValidator validator) {
            return super.setValidator(validator);
        }

        @Override
        public int getMinSize() {
            int min = super.getMinSize();
            if (min < 0) {
                min = 0;
                setMinSize(min);
            }
            return min;
        }

        @Override
        public int getMaxSize() {
            int max = super.getMaxSize();
            if (max < 1) {
                max = Integer.MAX_VALUE;
                setMaxSize(max);
            }
            return max;
        }

        /**
         * Gets whether undefined list elements are valid. In the unlikely case {@link #setAllowNullElement(boolean)}
         * has been called, that value is returned; otherwise the value of {@link #isNillable()} is used.
         *
         * @return {@code true} if undefined list elements are valid
         */
        @SuppressWarnings("WeakerAccess")
        public boolean getAllowNullElement() {
            return allowNullElement == null ? isNillable() : allowNullElement;
        }

        /**
         * Sets whether undefined list elements are valid.
         * @param allowNullElement whether undefined elements are valid
         * @return a builder that can be used to continue building the attribute definition
         */
        @SuppressWarnings({"unchecked", "WeakerAccess"})
        public BUILDER setAllowNullElement(boolean allowNullElement) {
            this.allowNullElement = allowNullElement;
            return (BUILDER) this;
        }

        /**
         * toggles default validator strategy to allow / not allow duplicate elements in list
         * @param allowDuplicates false if duplicates are not allowed
         * @return builder
         */
        @SuppressWarnings("unchecked")
        public BUILDER setAllowDuplicates(boolean allowDuplicates) {
            this.allowDuplicates = allowDuplicates;
            return (BUILDER) this;
        }

        @Override
        public ParameterValidator getValidator() {
            ParameterValidator result = super.getValidator();
            if (result == null) {
                ParameterValidator listElementValidator = getElementValidator();
                // Subclasses must call setElementValidator before calling this
                assert listElementValidator != null;
                result = new ListValidator(getElementValidator(), isNillable(), getMinSize(), getMaxSize(), allowDuplicates);
            }
            return result;
        }
    }
}
