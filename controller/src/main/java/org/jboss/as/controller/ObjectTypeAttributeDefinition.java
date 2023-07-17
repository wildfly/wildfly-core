/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ObjectTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.Resource;
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

    protected ObjectTypeAttributeDefinition(AbstractAttributeDefinitionBuilder<?, ? extends ObjectTypeAttributeDefinition> builder,
            final String suffix, final AttributeDefinition[] valueTypes) {
        super(addValueTypeConstraints(builder, valueTypes));
        this.valueTypes = valueTypes;
        this.suffix = suffix == null ? "" : suffix;
    }

    private static AbstractAttributeDefinitionBuilder<?, ? extends ObjectTypeAttributeDefinition> addValueTypeConstraints(AbstractAttributeDefinitionBuilder<?, ? extends  ObjectTypeAttributeDefinition> builder,
                                                                                                                          AttributeDefinition[] valueTypes) {
        if (valueTypes != null && valueTypes.length > 0) {
            Set<AccessConstraintDefinition> fromValues = null;
            for (AttributeDefinition valueType : valueTypes) {
                List<AccessConstraintDefinition> valueTypeConstraints = valueType.getAccessConstraints();
                if (valueTypeConstraints != null && !valueTypeConstraints.isEmpty()) {
                    if (fromValues == null) {
                        fromValues = new LinkedHashSet<>();
                    }
                    fromValues.addAll(valueTypeConstraints);
                }
            }
            if (fromValues != null) {
                AccessConstraintDefinition[] existing = builder.getAccessConstraints();
                Set<AccessConstraintDefinition> newValues = new LinkedHashSet<>();
                if (existing != null && existing.length > 0) {
                    Collections.addAll(newValues, existing);
                }
                newValues.addAll(fromValues);
                builder.setAccessConstraints(newValues.toArray(new AccessConstraintDefinition[newValues.size()]));
            }
        }
        return builder;
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

    public final AttributeDefinition[] getValueTypes() {
        return valueTypes;
    }

    @Override
    public void addCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (AttributeDefinition fieldType : valueTypes) {
                if (attributeValue.hasDefined(fieldType.getName())) {
                    fieldType.addCapabilityRequirements(context, resource, attributeValue.get(fieldType.getName()));
                }
            }
        }
    }

    @Override
    public void removeCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        if (attributeValue.isDefined()) {
            for (AttributeDefinition fieldType : valueTypes) {
                if (attributeValue.hasDefined(fieldType.getName())) {
                    fieldType.removeCapabilityRequirements(context, resource, attributeValue.get(fieldType.getName()));
                }
            }
        }
    }


    @Override
    public ModelNode addResourceAttributeDescription(ResourceBundle bundle, String prefix, ModelNode resourceDescription) {
        final ModelNode result = super.addResourceAttributeDescription(bundle, prefix, resourceDescription);
        addValueTypeDescription(result, prefix, bundle, false, null, null);
        return result;
    }

    @Override
    public ModelNode addOperationParameterDescription(final ModelNode resourceDescription, final String operationName,
                                                      final ResourceDescriptionResolver resolver,
                                                      final Locale locale, final ResourceBundle bundle) {
        final ModelNode result = super.addOperationParameterDescription(resourceDescription, operationName, resolver, locale, bundle);
        addValueTypeDescription(result, getName(), bundle, true, resolver, locale);
        return result;
    }

    @Override
    public ModelNode addOperationReplyDescription(final ModelNode resourceDescription, final String operationName,
                                                      final ResourceDescriptionResolver resolver,
                                                      final Locale locale, final ResourceBundle bundle) {
        final ModelNode result = super.addOperationReplyDescription(resourceDescription, operationName, resolver, locale, bundle);
        addValueTypeDescription(result, getName(), bundle, true, resolver, locale);
        return result;
    }

    @Override
    public ModelNode addResourceAttributeDescription(final ModelNode resourceDescription, final ResourceDescriptionResolver resolver,
                                                     final Locale locale, final ResourceBundle bundle) {
        final ModelNode result = super.addResourceAttributeDescription(resourceDescription, resolver, locale, bundle);
        addValueTypeDescription(result, getName(), bundle, false, resolver, locale);
        return result;
    }


    @Override
    public ModelNode addOperationParameterDescription(ResourceBundle bundle, String prefix, ModelNode operationDescription) {
        final ModelNode result = super.addOperationParameterDescription(bundle, prefix, operationDescription);
        addValueTypeDescription(result, prefix, bundle, true, null, null);
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
        getValidator().validateParameter(getName(), result);
        return result;
    }

    protected void addValueTypeDescription(final ModelNode node, final String prefix, final ResourceBundle bundle,
                                           boolean forOperation,
                                           final ResourceDescriptionResolver resolver,
                                           final Locale locale) {
        for (AttributeDefinition valueType : valueTypes) {
            if (forOperation && valueType.isResourceOnly()) {
                continue; //WFCORE-597
            }
            // get the value type description of the attribute
            final ModelNode valueTypeDesc = valueType.getNoTextDescription(false);
            if(valueTypeDesc.has(ModelDescriptionConstants.ATTRIBUTE_GROUP)) {
                valueTypeDesc.remove(ModelDescriptionConstants.ATTRIBUTE_GROUP);
            }
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

            // get deprecation description of the attribute
            ModelNode deprecated = valueType.addDeprecatedInfo(valueTypeDesc);
            if (deprecated != null) {
                final String key = String.format("%s.%s", p, valueType.getName());
                final String reason = resolver != null
                        ? resolver.getResourceAttributeDeprecatedDescription(key, locale, bundle)
                        : valueType.getAttributeDeprecatedDescription(bundle, p);
                deprecated.get(ModelDescriptionConstants.REASON).set(reason);
            }

            // set it as one of our value types, and return the value
            final ModelNode childType = node.get(ModelDescriptionConstants.VALUE_TYPE, valueType.getName()).set(valueTypeDesc);
            // if it is of type OBJECT itself (add its nested descriptions)
            // seeing that OBJECT represents a grouping, use prefix+"."+suffix for naming the entries
            if (valueType instanceof ObjectTypeAttributeDefinition) {
                ObjectTypeAttributeDefinition.class.cast(valueType).addValueTypeDescription(childType, p, bundle, forOperation, resolver, locale);
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
    public static Builder create(final String name, final AttributeDefinition... valueTypes){
        return new Builder(name, valueTypes);
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
            setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT);
        }

        public static Builder of(final String name, final AttributeDefinition... valueTypes) {
            return new Builder(name, valueTypes);
        }

        public static Builder of(final String name, final AttributeDefinition[] valueTypes, final AttributeDefinition[] moreValueTypes) {
            ArrayList<AttributeDefinition> list = new ArrayList<>(Arrays.asList(valueTypes));
            list.addAll(Arrays.asList(moreValueTypes));
            AttributeDefinition[] allValueTypes = new AttributeDefinition[list.size()];
            list.toArray(allValueTypes);

            return new Builder(name, allValueTypes);
        }

        @Override
        public ObjectTypeAttributeDefinition build() {
            ParameterValidator validator = getValidator();
            if (validator == null) {
                setValidator(new ObjectTypeValidator(isNillable(), valueTypes));
            }

            for (AttributeDefinition valueType : valueTypes) {
                if (valueType.getCorrector() != null) {
                    // Need to be sure we call this corrector
                    setCorrector(new ObjectParameterCorrector(getCorrector(), valueTypes));
                    break;
                }
            }
            return new ObjectTypeAttributeDefinition(this);
        }

        public Builder setSuffix(final String suffix) {
            this.suffix = suffix;
            return this;
        }
    }

    private static class ObjectParameterCorrector implements ParameterCorrector {
        private final ParameterCorrector topCorrector;
        private final AttributeDefinition[] valueTypes;

        private ObjectParameterCorrector(ParameterCorrector topCorrector, AttributeDefinition[] valueTypes) {
            this.topCorrector = topCorrector;
            this.valueTypes = valueTypes;
        }


        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            ModelNode result = newValue;
            if (newValue.isDefined()) {
                for (AttributeDefinition ad : valueTypes) {
                    ParameterCorrector fieldCorrector = ad.getCorrector();
                    if (fieldCorrector != null) {
                        String name = ad.getName();
                        boolean has = newValue.has(name);
                        ModelNode toCorrect = has ? newValue.get(name) : new ModelNode();
                        ModelNode curField = currentValue.has(name) ? currentValue.get(name) : new ModelNode();
                        ModelNode corrected = fieldCorrector.correct(toCorrect, curField);
                        if (!corrected.equals(toCorrect) || (!has && corrected.isDefined())) {
                            if (has) {
                                toCorrect.set(corrected);
                            } else {
                                // the corrector defined it, so add it
                                newValue.get(name).set(corrected);
                            }
                        }
                    }
                }
            }
            if (topCorrector != null) {
                result = topCorrector.correct(result, currentValue);
            }
            return result;
        }
    }

}
