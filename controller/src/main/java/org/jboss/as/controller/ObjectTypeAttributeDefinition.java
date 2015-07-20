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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.MinMaxValidator;
import org.jboss.as.controller.operations.validation.ObjectTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link AttributeDefinition} for attributes of type {@link ModelType#OBJECT} that aren't simple maps, but
 * rather a set fixed keys where each key may be associated with a value of a different type.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Richard Achmatowicz (c) 2012 RedHat Inc.
 *
 * @see MapAttributeDefinition
 */
public class ObjectTypeAttributeDefinition extends SimpleAttributeDefinition {
    private final AttributeDefinition[] valueTypes;
    private final String suffix;

    protected ObjectTypeAttributeDefinition(Builder builder) {
        this(builder, builder.suffix, builder.valueTypes);
    }

    protected ObjectTypeAttributeDefinition(AbstractAttributeDefinitionBuilder<?, ? extends  ObjectTypeAttributeDefinition> builder,
            final String suffix, final AttributeDefinition[] valueTypes) {
        super(builder);
        this.valueTypes = valueTypes;
        this.suffix = suffix == null ? "" : suffix;
    }

    @Override
    protected ModelNode convertParameterExpressions(ModelNode parameter) {
        ModelNode result = parameter;
        if (parameter.isDefined()) {
            boolean changeMade = false;
            ModelNode updated = new ModelNode().setEmptyObject();
            for (AttributeDefinition ad : valueTypes) {
                String fieldName = ad.getName();
                if (parameter.has(fieldName)) {
                    ModelNode orig = parameter.get(fieldName);
                    if (!orig.isDefined()) {
                        updated.get(fieldName); // establish undefined
                    } else {
                        ModelNode converted = ad.convertParameterExpressions(orig);
                        changeMade |= !orig.equals(converted);
                        updated.get(fieldName).set(converted);
                    }
                }
            }

            if (changeMade) {
                result = updated;
            }
        }
        return result;
    }

    AttributeDefinition[] getValueTypes() {
        return valueTypes;
    }

    @Override
    public ModelNode parse(final String value, final XMLStreamReader reader) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addCapabilityRequirements(OperationContext context, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (AttributeDefinition fieldType : valueTypes) {
                if (attributeValue.hasDefined(fieldType.getName())) {
                    fieldType.addCapabilityRequirements(context, attributeValue.get(fieldType.getName()));
                }
            }
        }
    }

    @Override
    public void removeCapabilityRequirements(OperationContext context, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (AttributeDefinition fieldType : valueTypes) {
                if (attributeValue.hasDefined(fieldType.getName())) {
                    fieldType.removeCapabilityRequirements(context, attributeValue.get(fieldType.getName()));
                }
            }
        }
    }


    @Override
    public ModelNode addResourceAttributeDescription(ResourceBundle bundle, String prefix, ModelNode resourceDescription) {
        final ModelNode result = super.addResourceAttributeDescription(bundle, prefix, resourceDescription);
        addValueTypeDescription(result, prefix, bundle, null, null);
        return result;
    }

    public ModelNode addOperationParameterDescription(final ModelNode resourceDescription, final String operationName,
                                                      final ResourceDescriptionResolver resolver,
                                                      final Locale locale, final ResourceBundle bundle) {
        final ModelNode result = super.addOperationParameterDescription(resourceDescription, operationName, resolver, locale, bundle);
        addValueTypeDescription(result, getName(), bundle, resolver, locale);
        return result;
    }

    public ModelNode addResourceAttributeDescription(final ModelNode resourceDescription, final ResourceDescriptionResolver resolver,
                                                     final Locale locale, final ResourceBundle bundle) {
        final ModelNode result = super.addResourceAttributeDescription(resourceDescription, resolver, locale, bundle);
        addValueTypeDescription(result, getName(), bundle, resolver, locale);
        return result;
    }


    @Override
    public ModelNode addOperationParameterDescription(ResourceBundle bundle, String prefix, ModelNode operationDescription) {
        final ModelNode result = super.addOperationParameterDescription(bundle, prefix, operationDescription);
        addValueTypeDescription(result, prefix, bundle, null, null);
        return result;
    }

    /**
     * Overrides the superclass implementation to allow the AttributeDefinition for each field in the
     * object to in turn resolve that field.
     *
     * {@inheritDoc}
     */
    @Override
    public ModelNode resolveValue(ExpressionResolver resolver, ModelNode value) throws OperationFailedException {

        // Pass non-OBJECT values through the superclass so it can reject weird values and, in the odd chance
        // that's how this object is set up, turn undefined into a default list value.
        ModelNode superResult = value.getType() == ModelType.OBJECT ? value : super.resolveValue(resolver, value);

        // If it's not an OBJECT (almost certainly UNDEFINED), then nothing more we can do
        if (superResult.getType() != ModelType.OBJECT) {
            return superResult;
        }

        // Resolve each field.
        // Don't mess with the original value
        ModelNode clone = superResult == value ? value.clone() : superResult;
        ModelNode result = new ModelNode();
        for (AttributeDefinition field : valueTypes) {
            String fieldName = field.getName();
            if (clone.has(fieldName)) {
                result.get(fieldName).set(field.resolveValue(resolver, clone.get(fieldName)));
            } else {
                // Input doesn't have a child for this field.
                // Don't create one in the output unless the AD produces a default value.
                // TBH this doesn't make a ton of sense, since any object should have
                // all of its fields, just some may be undefined. But doing it this
                // way may avoid breaking some code that is incorrectly checking node.has("xxx")
                // instead of node.hasDefined("xxx")
                ModelNode val = field.resolveValue(resolver, new ModelNode());
                if (val.isDefined()) {
                    result.get(fieldName).set(val);
                }
            }
        }
        // Validate the entire object
        getValidator().validateResolvedParameter(getName(), result);
        return result;
    }

    protected void addValueTypeDescription(final ModelNode node, final String prefix, final ResourceBundle bundle,
                                           final ResourceDescriptionResolver resolver,
                                           final Locale locale) {
        for (AttributeDefinition valueType : valueTypes) {
            // get the value type description of the attribute
            final ModelNode valueTypeDesc = getValueTypeDescription(valueType, false);
            final String p;
            boolean prefixUnusable = prefix == null || prefix.isEmpty() ;
            boolean suffixUnusable = suffix == null || suffix.isEmpty() ;

            if (prefixUnusable && !suffixUnusable) {
                p = suffix;
            } else if (!prefixUnusable && suffixUnusable) {
                p = prefix;
            } else {
                p = String.format("%s.%s", prefix, suffix);
            }

            // get the text description of the attribute
            if (resolver != null) {
                final String key = String.format("%s.%s", p, valueType.getName());
                valueTypeDesc.get(ModelDescriptionConstants.DESCRIPTION).set(resolver.getResourceAttributeDescription(key, locale, bundle));
            } else {
                valueTypeDesc.get(ModelDescriptionConstants.DESCRIPTION).set(valueType.getAttributeTextDescription(bundle, p));
            }
            // set it as one of our value types, and return the value
            final ModelNode childType = node.get(ModelDescriptionConstants.VALUE_TYPE, valueType.getName()).set(valueTypeDesc);
            // if it is of type OBJECT itself (add its nested descriptions)
            // seeing that OBJECT represents a grouping, use prefix+"."+suffix for naming the entries
            if (valueType instanceof ObjectTypeAttributeDefinition) {
                ObjectTypeAttributeDefinition.class.cast(valueType).addValueTypeDescription(childType, p, bundle, resolver, locale);
            }
            // if it is of type LIST, and its value type
            // seeing that LIST represents a grouping, use prefix+"."+suffix for naming the entries
            if (valueType instanceof SimpleListAttributeDefinition) {
                SimpleListAttributeDefinition.class.cast(valueType).addValueTypeDescription(childType, p, bundle);
            } else if (valueType instanceof MapAttributeDefinition) {
                MapAttributeDefinition.class.cast(valueType).addValueTypeDescription(childType, bundle);
            } else if (valueType instanceof PrimitiveListAttributeDefinition) {
                PrimitiveListAttributeDefinition.class.cast(valueType).addValueTypeDescription(childType, bundle);
            } else if (valueType instanceof ObjectListAttributeDefinition) {
                ObjectListAttributeDefinition.class.cast(valueType).addValueTypeDescription(childType, p, bundle, false, resolver, locale);
            }
        }
    }

    private ModelNode getValueTypeDescription(final AttributeDefinition valueType, final boolean forOperation) {
        final ModelNode result = new ModelNode();
        result.get(ModelDescriptionConstants.TYPE).set(valueType.getType());
        result.get(ModelDescriptionConstants.DESCRIPTION); // placeholder
        result.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(valueType.isAllowExpression());
        if (forOperation) {
            result.get(ModelDescriptionConstants.REQUIRED).set(!valueType.isAllowNull());
        }
        result.get(ModelDescriptionConstants.NILLABLE).set(valueType.isAllowNull());
        final ModelNode defaultValue = valueType.getDefaultValue();
        if (!forOperation && defaultValue != null && defaultValue.isDefined()) {
            result.get(ModelDescriptionConstants.DEFAULT).set(defaultValue);
        }
        MeasurementUnit measurementUnit = valueType.getMeasurementUnit();
        if (measurementUnit != null && measurementUnit != MeasurementUnit.NONE) {
            result.get(ModelDescriptionConstants.UNIT).set(measurementUnit.getName());
        }
        final String[] alternatives = valueType.getAlternatives();
        if (alternatives != null) {
            for (final String alternative : alternatives) {
                result.get(ModelDescriptionConstants.ALTERNATIVES).add(alternative);
            }
        }
        final String[] requires = valueType.getRequires();
        if (requires != null) {
            for (final String required : requires) {
                result.get(ModelDescriptionConstants.REQUIRES).add(required);
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
                        result.get(ModelDescriptionConstants.MIN_LENGTH).set(min);
                        break;
                    default:
                        result.get(ModelDescriptionConstants.MIN).set(min);
                }
            }
            Long max = minMax.getMax();
            if (max != null) {
                switch (valueType.getType()) {
                    case STRING:
                    case LIST:
                    case OBJECT:
                    case BYTES:
                        result.get(ModelDescriptionConstants.MAX_LENGTH).set(max);
                        break;
                    default:
                        result.get(ModelDescriptionConstants.MAX).set(max);
                }
            }
        }
        return result;
    }

    @Override
    protected void addAllowedValuesToDescription(ModelNode result, ParameterValidator validator) {
        //Don't add allowed values for object types, since they simply enumerate the fields given in the value type
    }

    public static final class Builder extends AbstractAttributeDefinitionBuilder<Builder, ObjectTypeAttributeDefinition> {
        private String suffix;
        private final AttributeDefinition[] valueTypes;

        public Builder(final String name, final AttributeDefinition... valueTypes) {
            super(name, ModelType.OBJECT, true);
            this.valueTypes = valueTypes;
            setAttributeParser(AttributeParser.OBJECT_PARSER);
        }

        public static Builder of(final String name, final AttributeDefinition... valueTypes) {
            return new Builder(name, valueTypes);
        }

        public static Builder of(final String name, final AttributeDefinition[] valueTypes, final AttributeDefinition[] moreValueTypes) {
            ArrayList<AttributeDefinition> list = new ArrayList<AttributeDefinition>(Arrays.asList(valueTypes));
            list.addAll(Arrays.asList(moreValueTypes));
            AttributeDefinition[] allValueTypes = new AttributeDefinition[list.size()];
            list.toArray(allValueTypes);

            return new Builder(name, allValueTypes);
        }

        public ObjectTypeAttributeDefinition build() {
            if (validator == null) { validator = new ObjectTypeValidator(allowNull, valueTypes); }
            return new ObjectTypeAttributeDefinition(this);
        }

        public Builder setSuffix(final String suffix) {
            this.suffix = suffix;
            return this;
        }

        /*
       --------------------------
       added for binary compatibility for running compatibilty tests
        */
        @Override
        public Builder setAllowNull(boolean allowNull) {
            return super.setAllowNull(allowNull);
        }
    }

}
