/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.PrimitiveListAttributeDefinition.parseSingleElementToList;

import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.AttributeDefinition} for attributes that represent lists with
 * simple element types (i.e. not {@link org.jboss.dmr.ModelType#LIST} or {@link org.jboss.dmr.ModelType#OBJECT}.
 *
 * Date: 13.10.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Richard Achmatowicz (c) 2012 RedHat Inc.
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class SimpleListAttributeDefinition extends ListAttributeDefinition {
    private final AttributeDefinition valueType;

    protected SimpleListAttributeDefinition(final ListAttributeDefinition.Builder builder, AttributeDefinition valueType) {
        super(builder);
        this.valueType = valueType;
        // This class is not appropriate for lists with complex elements. Use ObjectListAttributeDefinition
        assert valueType.getType() != ModelType.OBJECT && valueType.getType() != ModelType.LIST;
    }

    public AttributeDefinition getValueType() {
        return getValueAttributeDefinition();
    }

    @Override
    public AttributeDefinition getValueAttributeDefinition() {
        return valueType;
    }

    @Override
    public ModelNode addResourceAttributeDescription(ResourceBundle bundle, String prefix, ModelNode resourceDescription) {
        final ModelNode result = super.addResourceAttributeDescription(bundle, prefix, resourceDescription);
        addValueTypeDescription(result, prefix, bundle);
        return result;
    }

    @Override
    public ModelNode addOperationParameterDescription(ResourceBundle bundle, String prefix, ModelNode operationDescription) {
        final ModelNode result = super.addOperationParameterDescription(bundle, prefix, operationDescription);
        addValueTypeDescription(result, prefix, bundle);
        return result;
    }

    @Override
    public void addCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (ModelNode element : attributeValue.asList()) {
                valueType.addCapabilityRequirements(context, resource, element);
            }
        }
    }

    @Override
    public void removeCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (ModelNode element : attributeValue.asList()) {
                valueType.removeCapabilityRequirements(context, resource, element);
            }
        }
    }

    @Override
    protected CapabilityReferenceRecorder getReferenceRecorder() {
        return valueType.getReferenceRecorder();
    }

    @Override
    public boolean hasCapabilityRequirements() {
        return valueType.hasCapabilityRequirements();
    }

    @Override
    protected void addValueTypeDescription(final ModelNode node, final ResourceBundle bundle) {
        addValueTypeDescription(node);
    }

    protected void addValueTypeDescription(final ModelNode node, final String prefix, final ResourceBundle bundle) {
        addValueTypeDescription(node);
    }

    @Override
    protected void addAttributeValueTypeDescription(final ModelNode node, final ResourceDescriptionResolver resolver, final Locale locale, final ResourceBundle bundle) {
        addValueTypeDescription(node);
    }

    @Override
    protected void addOperationParameterValueTypeDescription(final ModelNode node, final String operationName, final ResourceDescriptionResolver resolver, final Locale locale, final ResourceBundle bundle) {
        addValueTypeDescription(node);
    }

    /**
     * Overrides {@link ListAttributeDefinition#convertParameterElementExpressions(ModelNode) the superclass}
     * to check that expressions are supported yet the {@code valueType} passed to the constructor is one of
     * the {@link #COMPLEX_TYPES complex DMR types}. If it is, an {@link IllegalStateException} is thrown, as this
     * implementation cannot properly handle such a combination.
     *
     * {@inheritDoc}
     *
     * @throws IllegalStateException if expressions are supported, but the {@code valueType} is {@link #COMPLEX_TYPES complex}
     */
    @Override
    protected ModelNode convertParameterElementExpressions(ModelNode parameterElement) {
        boolean allowExp = isAllowExpression() || valueType.isAllowExpression();
        if (allowExp && COMPLEX_TYPES.contains(valueType.getType())) {
            // They need to subclass and override
            throw new IllegalStateException();
        }
        return allowExp ? convertStringExpression(parameterElement) : parameterElement;
    }



    @Override
    ModelNode parseResolvedValue(ModelNode original, ModelNode resolved) {
        return parseSingleElementToList(this, original, resolved);
    }

    private void addValueTypeDescription(final ModelNode node) {
        node.get(ModelDescriptionConstants.VALUE_TYPE).set(valueType.getType());
    }

    public static class Builder extends ListAttributeDefinition.Builder<Builder,SimpleListAttributeDefinition>{
        private final AttributeDefinition valueType;
        private boolean wrapXmlList = true;

        public Builder(final String name, final AttributeDefinition valueType) {
            super(name);
            this.valueType = valueType;
            setElementValidator(valueType.getValidator());
        }

        public Builder(final SimpleListAttributeDefinition basis) {
            super(basis);
            valueType = basis.getValueAttributeDefinition();
        }

        public static Builder of(final String name, final AttributeDefinition valueType) {
            return new Builder(name, valueType);
        }

        public Builder setWrapXmlList(boolean wrap) {
            this.wrapXmlList = wrap;
            return this;
        }

        public SimpleListAttributeDefinition build() {
            if (getAttributeMarshaller() == null) {
                setAttributeMarshaller(AttributeMarshallers.getSimpleListMarshaller(wrapXmlList));
            }
            //todo add parser for SimpleListAttributeDefinition, for now no one is using it yet.
            /*if (getParser() == null) {
                setAttributeParser(AttributeParser..);
            }*/
            return new SimpleListAttributeDefinition(this, valueType);
        }

        @Override
        public Builder setMaxSize(final int maxSize) {
            return super.setMaxSize(maxSize);
        }

        @Override
        public Builder setMinSize(final int minSize) {
            return super.setMinSize(minSize);
        }
    }
}
