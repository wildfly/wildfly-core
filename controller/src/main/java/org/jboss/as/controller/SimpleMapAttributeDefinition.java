/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link MapAttributeDefinition} for maps with keys of {@link ModelType#STRING} and values of type ModelType
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @since 7.2
 */
public class SimpleMapAttributeDefinition extends MapAttributeDefinition {
    private final ModelType valueType;

    private SimpleMapAttributeDefinition(final Builder builder) {
        super(builder);
        this.valueType = builder.valueType;
    }

    @Override
    protected void addValueTypeDescription(ModelNode node, ResourceBundle bundle) {
        node.get(ModelDescriptionConstants.VALUE_TYPE).set(valueType);
        node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(new ModelNode(isAllowExpression()));
    }

    @Override
    protected void addAttributeValueTypeDescription(ModelNode node, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        node.get(ModelDescriptionConstants.VALUE_TYPE).set(valueType);
        node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(new ModelNode(isAllowExpression()));
    }

    @Override
    protected void addOperationParameterValueTypeDescription(ModelNode node, String operationName, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        node.get(ModelDescriptionConstants.VALUE_TYPE).set(valueType);
        node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(new ModelNode(isAllowExpression()));
    }

    @Override
    public void addCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        handleCapabilityRequirements(context, resource, attributeValue, false);
    }

    @Override
    public void removeCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        handleCapabilityRequirements(context, resource, attributeValue, true);
    }

    private void handleCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue, boolean remove) {
        CapabilityReferenceRecorder refRecorder = getReferenceRecorder();
        if (refRecorder != null && attributeValue.isDefined()) {
            Set<String> KeyList = attributeValue.keys();
            String[] attributeValues = new String[KeyList.size()];
            int position = 0;
            for (String key : KeyList) {
                ModelNode current = attributeValue.get(key);
                if (current.isDefined() == false || current.getType().equals(ModelType.EXPRESSION)) {
                    return;
                }
                attributeValues[position++] = current.asString();
            }
            if (remove) {
                refRecorder.removeCapabilityRequirements(context, resource, getName(), attributeValues);
            } else {
                refRecorder.addCapabilityRequirements(context, resource, getName(), attributeValues);
            }
        }
    }

    public static final class Builder extends MapAttributeDefinition.Builder<Builder, SimpleMapAttributeDefinition> {
        private ModelType valueType = ModelType.STRING;

        public Builder(final String name, boolean optional) {
            super(name, optional);
            setDefaults();
        }

        public Builder(final String name, final ModelType valueType, final boolean optional) {
            super(name, optional);
            this.valueType = valueType;
            setDefaults();
        }

        private void setDefaults(){
            setAttributeParser(AttributeParser.PROPERTIES_PARSER);
            setAttributeMarshaller(AttributeMarshaller.PROPERTIES_MARSHALLER);
        }

        public Builder(final SimpleMapAttributeDefinition basis) {
            super(basis);
        }

        public Builder(final PropertiesAttributeDefinition basis) {
            super(basis);
        }

        public Builder setValueType(ModelType valueType) {
            this.valueType = valueType;
            return this;
        }

        @Override
        public SimpleMapAttributeDefinition build() {
            if (elementValidator == null) {
                elementValidator = new ModelTypeValidator(valueType);
            }
            return new SimpleMapAttributeDefinition(this);
        }
    }
}
