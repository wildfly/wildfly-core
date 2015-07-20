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

import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.MinMaxValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * AttributeDefinition suitable for managing LISTs of OBJECTs, which takes into account
 * recursive processing of allowed values and their value types.
 * <p/>
 * Date: 13.10.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Richard Achmatowicz (c) 2012 RedHat Inc.
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class ObjectListAttributeDefinition extends ListAttributeDefinition {
    private final ObjectTypeAttributeDefinition valueType;

    private ObjectListAttributeDefinition(Builder builder) {
        super(builder);
        this.valueType = builder.valueType;
    }

    @Override
    public ModelNode addResourceAttributeDescription(ResourceBundle bundle, String prefix, ModelNode resourceDescription) {
        final ModelNode attr = getNoTextDescription(false);
        attr.get(ModelDescriptionConstants.DESCRIPTION).set(getAttributeTextDescription(bundle, prefix));
        final ModelNode result = resourceDescription.get(ModelDescriptionConstants.ATTRIBUTES, getName()).set(attr);
        addValueTypeDescription(result, prefix, bundle, false,null,null);
        addAccessConstraints(result, bundle.getLocale());
        return result;
    }

    @Override
    public ModelNode addOperationParameterDescription(ResourceBundle bundle, String prefix, ModelNode operationDescription) {
        final ModelNode param = getNoTextDescription(true);
        param.get(ModelDescriptionConstants.DESCRIPTION).set(getAttributeTextDescription(bundle, prefix));
        final ModelNode result = operationDescription.get(ModelDescriptionConstants.REQUEST_PROPERTIES, getName()).set(param);
        addValueTypeDescription(result, prefix, bundle, true,null,null);
        return result;
    }

    @Override
    public void addCapabilityRequirements(OperationContext context, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (ModelNode element : attributeValue.asList()) {
                valueType.addCapabilityRequirements(context, element);
            }
        }
    }

    @Override
    public void removeCapabilityRequirements(OperationContext context, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (ModelNode element : attributeValue.asList()) {
                valueType.removeCapabilityRequirements(context, element);
            }
        }
    }


    @Override
    protected void addValueTypeDescription(final ModelNode node, final ResourceBundle bundle) {
        addValueTypeDescription(node, valueType.getName(), bundle, false,null,null);
    }

    @Override
    protected void addAttributeValueTypeDescription(final ModelNode node, final ResourceDescriptionResolver resolver, final Locale locale, final ResourceBundle bundle) {
        addValueTypeDescription(node, getName(), bundle, false, resolver, locale);
    }

    @Override
    protected void addOperationParameterValueTypeDescription(final ModelNode node, final String operationName, final ResourceDescriptionResolver resolver, final Locale locale, final ResourceBundle bundle) {
        addValueTypeDescription(node, getName(), bundle, true, resolver, locale);
    }

    @Override
    public void marshallAsElement(final ModelNode resourceModel, final boolean marshalDefault, final XMLStreamWriter writer) throws XMLStreamException {
        if (resourceModel.hasDefined(getName())) {
            writer.writeStartElement(getXmlName());
            for (ModelNode handler : resourceModel.get(getName()).asList()) {
                valueType.marshallAsElement(handler, writer);
            }
            writer.writeEndElement();
        }
    }

    /**
     * Overrides the superclass implementation to allow the value type's AttributeDefinition to in turn
     * resolve each element.
     *
     * {@inheritDoc}
     */
    @Override
    public ModelNode resolveValue(ExpressionResolver resolver, ModelNode value) throws OperationFailedException {

        // Pass non-LIST values through the superclass so it can reject weird values and, in the odd chance
        // that's how this object is set up, turn undefined into a default list value.
        ModelNode superResult = value.getType() == ModelType.LIST ? value : super.resolveValue(resolver, value);

        // If it's not a LIST (almost certainly UNDEFINED), then nothing more we can do
        if (superResult.getType() != ModelType.LIST) {
            return superResult;
        }

        // Resolve each element.
        // Don't mess with the original value
        ModelNode clone = superResult == value ? value.clone() : superResult;
        ModelNode result = new ModelNode();
        for (ModelNode element : clone.asList()) {
            result.add(valueType.resolveValue(resolver, element));
        }
        // Validate the entire list
        getValidator().validateResolvedParameter(getName(), result);
        return result;
    }

    /**
     * Uses the {@link ObjectTypeAttributeDefinition} passed to the constructor to
     * {@link ObjectTypeAttributeDefinition#convertParameterExpressions(ModelNode) convert the element's expressions}.
     *
     * {@inheritDoc}
     */
    @Override
    protected ModelNode convertParameterElementExpressions(ModelNode parameterElement) {
        return valueType.convertParameterExpressions(parameterElement);
    }

    protected void addValueTypeDescription(final ModelNode node, final String prefix, final ResourceBundle bundle,
                                           boolean forOperation, final ResourceDescriptionResolver resolver, Locale locale) {
        node.get(ModelDescriptionConstants.DESCRIPTION); // placeholder
        node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(valueType.isAllowExpression());
        if (forOperation) {
            node.get(ModelDescriptionConstants.REQUIRED).set(!valueType.isAllowNull());
        }
        node.get(ModelDescriptionConstants.NILLABLE).set(isAllowNull());
        final ModelNode defaultValue = valueType.getDefaultValue();
        if (!forOperation && defaultValue != null && defaultValue.isDefined()) {
            node.get(ModelDescriptionConstants.DEFAULT).set(defaultValue);
        }
        MeasurementUnit measurementUnit = valueType.getMeasurementUnit();
        if (measurementUnit != null && measurementUnit != MeasurementUnit.NONE) {
            node.get(ModelDescriptionConstants.UNIT).set(measurementUnit.getName());
        }
        final String[] alternatives = valueType.getAlternatives();
        if (alternatives != null) {
            for (final String alternative : alternatives) {
                node.get(ModelDescriptionConstants.ALTERNATIVES).add(alternative);
            }
        }
        final String[] requires = valueType.getRequires();
        if (requires != null) {
            for (final String required : requires) {
                node.get(ModelDescriptionConstants.REQUIRES).add(required);
            }
        }
        final ParameterValidator validator = valueType.getValidator();
        if (validator instanceof MinMaxValidator) {
            MinMaxValidator minMax = (MinMaxValidator) validator;
            Long min = minMax.getMin();
            if (min != null) {
                switch (valueType.getType()) {
                    case STRING:
                    case LIST:
                    case OBJECT:
                    case BYTES:
                        node.get(ModelDescriptionConstants.MIN_LENGTH).set(min);
                        break;
                    default:
                        node.get(ModelDescriptionConstants.MIN).set(min);
                }
            }
            Long max = minMax.getMax();
            if (max != null) {
                switch (valueType.getType()) {
                    case STRING:
                    case LIST:
                    case OBJECT:
                    case BYTES:
                        node.get(ModelDescriptionConstants.MAX_LENGTH).set(max);
                        break;
                    default:
                        node.get(ModelDescriptionConstants.MAX).set(max);
                }
            }
        }
        addAllowedValuesToDescription(node, validator);
        valueType.addValueTypeDescription(node, prefix, bundle,resolver,locale);
    }

    ObjectTypeAttributeDefinition getValueType() {
        return valueType;
    }

    public static final class Builder extends ListAttributeDefinition.Builder<Builder, ObjectListAttributeDefinition> {
        private final ObjectTypeAttributeDefinition valueType;

        public Builder(final String name, final ObjectTypeAttributeDefinition valueType) {
            super(name);
            this.valueType = valueType;
            setElementValidator(valueType.getValidator());
            setAttributeParser(AttributeParser.OBJECT_LIST_PARSER);
            setAttributeMarshaller(AttributeMarshaller.OBJECT_LIST_MARSHALLER);
        }

        public static Builder of(final String name, final ObjectTypeAttributeDefinition valueType) {
            return new Builder(name, valueType);
        }

        public ObjectListAttributeDefinition build() {
            return new ObjectListAttributeDefinition(this);
        }

        /*
       --------------------------
       added for binary compatibility for running compatibility tests
        */
        @Override
        public Builder setAllowNull(boolean allowNull) {
            return super.setAllowNull(allowNull);
        }
    }
}
