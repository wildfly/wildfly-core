/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.Resource;
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
        addValueTypeDescription(result, prefix, bundle, false, null, null);
        addAccessConstraints(result, bundle.getLocale());
        return result;
    }

    @Override
    public ModelNode addOperationParameterDescription(ResourceBundle bundle, String prefix, ModelNode operationDescription) {
        final ModelNode param = getNoTextDescription(true);
        param.get(ModelDescriptionConstants.DESCRIPTION).set(getAttributeTextDescription(bundle, prefix));
        final ModelNode result = operationDescription.get(ModelDescriptionConstants.REQUEST_PROPERTIES, getName()).set(param);
        addValueTypeDescription(result, prefix, bundle, true, null, null);
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
                valueType.getMarshaller().marshallAsElement(this, handler, true, writer);
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
        result.setEmptyList();
        for (ModelNode element : clone.asList()) {
            result.add(valueType.resolveValue(resolver, element));
        }
        // Validate the entire list
        getValidator().validateParameter(getName(), result);
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
        valueType.addValueTypeDescription(node, prefix, bundle, forOperation, resolver,locale);
    }

    @Override
    protected void addAllowedValuesToDescription(ModelNode result, ParameterValidator validator) {
        //Don't add allowed values for object types, since they simply enumerate the fields given in the value type
    }

    public final ObjectTypeAttributeDefinition getValueType() {
        return (ObjectTypeAttributeDefinition) getValueAttributeDefinition();
    }

    @Override
    public final AttributeDefinition getValueAttributeDefinition() {
        return valueType;
    }


    public static final class Builder extends ListAttributeDefinition.Builder<Builder, ObjectListAttributeDefinition> {
        private ObjectTypeAttributeDefinition valueType;

        public Builder(final String name, final ObjectTypeAttributeDefinition valueType) {
            super(name);
            this.valueType = valueType;
            setElementValidator(valueType.getValidator());
            setAttributeParser(AttributeParser.OBJECT_LIST_PARSER);
            setAttributeMarshaller(AttributeMarshaller.OBJECT_LIST_MARSHALLER);
        }

        public Builder(ObjectListAttributeDefinition basis) {
            super(basis);
            this.valueType = basis.valueType;
        }

        public Builder setValueType(ObjectTypeAttributeDefinition valueType) {
            this.valueType = valueType;
            return this;
        }

        public static Builder of(final String name, final ObjectTypeAttributeDefinition valueType) {
            return new Builder(name, valueType);
        }

        @Override
        public ObjectListAttributeDefinition build() {
            List<AccessConstraintDefinition> valueConstraints = valueType.getAccessConstraints();
            if (!valueConstraints.isEmpty()) {
                Set<AccessConstraintDefinition> acdSet = new LinkedHashSet<>();
                AccessConstraintDefinition[] curAcds = getAccessConstraints();
                if (curAcds != null && curAcds.length > 0) {
                    Collections.addAll(acdSet, curAcds);
                }
                acdSet.addAll(valueConstraints);
                setAccessConstraints(acdSet.toArray(new AccessConstraintDefinition[acdSet.size()]));
            }
            if (valueType.getCorrector() != null) {
                // Need to be sure we call this corrector
                setCorrector(new ListParameterCorrector(getCorrector(), valueType.getCorrector()));
            }
            return new ObjectListAttributeDefinition(this);
        }
    }

    private static class ListParameterCorrector implements ParameterCorrector {
        private final ParameterCorrector listCorrector;
        private final ParameterCorrector elementCorrector;

        private ListParameterCorrector(ParameterCorrector listCorrector, ParameterCorrector elementCorrector) {
            this.listCorrector = listCorrector;
            this.elementCorrector = elementCorrector;
        }

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            ModelNode result = newValue;
            if (newValue.isDefined()) {
                int curSize = currentValue.isDefined() ? currentValue.asInt() : 0;
                for (int i = 0; i < newValue.asInt(); i++) {
                    ModelNode toCorrect = newValue.get(i);
                    ModelNode corrected = elementCorrector.correct(toCorrect, i < curSize ? currentValue.get(i) : new ModelNode());
                    if (!corrected.equals(toCorrect)) {
                        toCorrect.set(corrected);
                    }
                }
            }
            if (listCorrector != null) {
                result = listCorrector.correct(result, currentValue);
            }
            return result;
        }
    }
}
