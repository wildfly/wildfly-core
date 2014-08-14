/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import java.util.Locale;
import java.util.ResourceBundle;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
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
    }


    public AttributeDefinition getValueType() {
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
    protected void addValueTypeDescription(final ModelNode node, final ResourceBundle bundle) {
        addValueTypeDescription(node, null, bundle);
    }


    protected void addValueTypeDescription(final ModelNode node, final String prefix, final ResourceBundle bundle) {
        if (valueType.getType() == ModelType.OBJECT) {
            final ModelNode param = valueType.getNoTextDescription(true);
            final ModelNode childType = node.get(ModelDescriptionConstants.VALUE_TYPE, valueType.getName()).set(param);
            if (valueType instanceof ObjectTypeAttributeDefinition) {
                ObjectTypeAttributeDefinition.class.cast(valueType).addOperationParameterDescription(bundle, prefix, childType);
            }
        } else {
            addSimpleValueTypeDescription(node);
        }
    }

    @Override
    protected void addAttributeValueTypeDescription(final ModelNode node, final ResourceDescriptionResolver resolver, final Locale locale, final ResourceBundle bundle) {
        addValueTypeDescription(node, bundle);
    }

    @Override
    protected void addOperationParameterValueTypeDescription(final ModelNode node, final String operationName, final ResourceDescriptionResolver resolver, final Locale locale, final ResourceBundle bundle) {
        addValueTypeDescription(node, bundle);
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

    private void addSimpleValueTypeDescription(final ModelNode node) {
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
            valueType = basis.getValueType();
            setElementValidator(valueType.getValidator());
        }

        public static Builder of(final String name, final AttributeDefinition valueType) {
            return new Builder(name, valueType);
        }

        public Builder setWrapXmlList(boolean wrap) {
            this.wrapXmlList = wrap;
            return this;
        }

        public SimpleListAttributeDefinition build() {
            if (attributeMarshaller == null) {
                final boolean wrap = wrapXmlList;
                attributeMarshaller = new AttributeMarshaller() {
                    @Override
                    public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
                        if (resourceModel.hasDefined(attribute.getName())) {
                            if (wrap) {
                                writer.writeStartElement(attribute.getXmlName());
                            }
                            for (ModelNode handler : resourceModel.get(attribute.getName()).asList()) {
                                valueType.marshallAsElement(handler, writer);
                            }
                            if (wrap) {
                                writer.writeEndElement();
                            }
                        }
                    }
                };
            }
            return new SimpleListAttributeDefinition(this, valueType);
        }

        /*
        --------------------------
        added for binary compatibility with older versions
         */
        @Override
        public Builder setAllowNull(boolean allowNull) {
            return super.setAllowNull(allowNull);
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
