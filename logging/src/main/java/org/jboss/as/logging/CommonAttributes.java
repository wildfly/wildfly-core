/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CaseParameterCorrector;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.operations.validation.ObjectTypeValidator;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.correctors.FileCorrector;
import org.jboss.as.logging.resolvers.FileResolver;
import org.jboss.as.logging.resolvers.LevelResolver;
import org.jboss.as.logging.validators.FileValidator;
import org.jboss.as.logging.validators.LogLevelValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.Level;


/**
 * @author Emanuel Muckenhuber
 */
public interface CommonAttributes {

    // Attributes
    PropertyAttributeDefinition APPEND = PropertyAttributeDefinition.Builder.of("append", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    PropertyAttributeDefinition AUTOFLUSH = PropertyAttributeDefinition.Builder.of("autoflush", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setPropertyName("autoFlush")
            .build();

    SimpleAttributeDefinition CLASS = SimpleAttributeDefinitionBuilder.create("class", ModelType.STRING)
            .setAllowExpression(false)
            .setRestartAllServices()
            .build();

    PropertyAttributeDefinition ENABLED = PropertyAttributeDefinition.Builder.of("enabled", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    PropertyAttributeDefinition ENCODING = PropertyAttributeDefinition.Builder.of("encoding", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .build();

    // Defined out of order as it needs to be used in the FILE
    SimpleAttributeDefinition RELATIVE_TO = SimpleAttributeDefinitionBuilder.create(PathResourceDefinition.RELATIVE_TO)
            .setCapabilityReference(Capabilities.PATH_CAPABILITY)
            .build();

    PropertyObjectTypeAttributeDefinition FILE = PropertyObjectTypeAttributeDefinition.Builder.of("file", RELATIVE_TO, PATH)
            .setAllowExpression(false)
            .setRequired(true)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        final ModelNode file = resourceModel.get(attribute.getName());
                        RELATIVE_TO.marshallAsAttribute(file, marshallDefault, writer);
                        PATH.marshallAsAttribute(file, marshallDefault, writer);
                        writer.writeEndElement();
                    }
                }
            })
            .setCorrector(FileCorrector.INSTANCE)
            .setPropertyName("fileName")
            .setResolver(FileResolver.INSTANCE)
            .setValidator(new FileValidator())
            .build();

    SimpleAttributeDefinition HANDLER_NAME = SimpleAttributeDefinitionBuilder.create("name", ModelType.STRING, true)
            .setCapabilityReference(Capabilities.HANDLER_REFERENCE_RECORDER)
            .build();

    // JUL doesn't allow for null levels. Use ALL as the default
    PropertyAttributeDefinition LEVEL = PropertyAttributeDefinition.Builder.of("level", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setCorrector(CaseParameterCorrector.TO_UPPER)
            .setDefaultValue(new ModelNode(Level.ALL.getName()))
            .setResolver(LevelResolver.INSTANCE)
            .setValidator(new LogLevelValidator(true))
            .build();

    String LOGGING_PROFILE = "logging-profile";

    String LOGGING_PROFILES = "logging-profiles";

    SimpleAttributeDefinition MODULE = SimpleAttributeDefinitionBuilder.create("module", ModelType.STRING)
            .setAllowExpression(false)
            .setRestartAllServices()
            .build();

    SimpleAttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create("name", ModelType.STRING, true)
            .setAllowExpression(false)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .build();

    SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder("properties", true)
            .setAllowExpression(true)
            .setAttributeMarshaller(PropertyAttributeMarshaller.INSTANCE)
            .build();

    /**
     * The name of the root logger.
     */
    String ROOT_LOGGER_NAME = "";

    // Legacy Filter attributes
    SimpleAttributeDefinition ACCEPT = SimpleAttributeDefinitionBuilder.create("accept", ModelType.BOOLEAN, true)
            .setAllowExpression(false)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    SimpleAttributeDefinition CHANGE_LEVEL = SimpleAttributeDefinitionBuilder.create("change-level", ModelType.STRING, true)
            .setAllowExpression(false)
            .setCorrector(CaseParameterCorrector.TO_UPPER)
            .setValidator(new LogLevelValidator(true))
            .build();

    SimpleAttributeDefinition DENY = SimpleAttributeDefinitionBuilder.create("deny", ModelType.BOOLEAN, true)
            .setAllowExpression(false)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    SimpleAttributeDefinition FILTER_PATTERN = SimpleAttributeDefinitionBuilder.create("pattern", ModelType.STRING)
            .setAllowExpression(false)
            .build();

    SimpleAttributeDefinition MATCH = SimpleAttributeDefinitionBuilder.create("match", ModelType.STRING, true)
            .setAllowExpression(false)
            .build();

    SimpleAttributeDefinition MAX_INCLUSIVE = SimpleAttributeDefinitionBuilder.create("max-inclusive", ModelType.BOOLEAN, true)
            .setAllowExpression(false)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    SimpleAttributeDefinition MAX_LEVEL = SimpleAttributeDefinitionBuilder.create("max-level", ModelType.STRING)
            .setAllowExpression(false)
            .setCorrector(CaseParameterCorrector.TO_UPPER)
            .setValidator(new LogLevelValidator(true))
            .build();

    SimpleAttributeDefinition MIN_INCLUSIVE = SimpleAttributeDefinitionBuilder.create("min-inclusive", ModelType.BOOLEAN, true)
            .setAllowExpression(false)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    SimpleAttributeDefinition MIN_LEVEL = SimpleAttributeDefinitionBuilder.create("min-level", ModelType.STRING)
            .setAllowExpression(false)
            .setCorrector(CaseParameterCorrector.TO_UPPER)
            .setValidator(new LogLevelValidator(true))
            .build();

    SimpleAttributeDefinition NEW_LEVEL = SimpleAttributeDefinitionBuilder.create("new-level", ModelType.STRING)
            .setAllowExpression(false)
            .setCorrector(CaseParameterCorrector.TO_UPPER)
            .setValidator(new LogLevelValidator(true))
            .build();

    SimpleAttributeDefinition REPLACEMENT = SimpleAttributeDefinitionBuilder.create("replacement", ModelType.STRING)
            .setAllowExpression(false)
            .build();

    SimpleAttributeDefinition REPLACE_ALL = SimpleAttributeDefinitionBuilder.create("replace-all", ModelType.BOOLEAN, true)
            .setAllowExpression(false)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    ObjectTypeAttributeDefinition LEVEL_RANGE_LEGACY = ObjectTypeAttributeDefinition.Builder.of("level-range", MIN_LEVEL, MIN_INCLUSIVE, MAX_LEVEL, MAX_INCLUSIVE)
            .setAllowExpression(false)
            .setRequired(false)
            .setValidator(new ObjectTypeValidator(false, MIN_LEVEL, MIN_INCLUSIVE, MAX_LEVEL, MAX_INCLUSIVE) {
                @Override
                public void validateParameter(final String parameterName, final ModelNode value) throws OperationFailedException {
                    super.validateParameter(parameterName, value);
                    final ModelNode clonedValue = value.clone();
                    final AttributeDefinition[] allowedValues = {MIN_LEVEL, MIN_INCLUSIVE, MAX_LEVEL, MAX_INCLUSIVE};
                    for (AttributeDefinition valueType : allowedValues) {
                        final ModelNode syntheticValue;
                        // Does the value the type
                        if (clonedValue.has(valueType.getName())) {
                            syntheticValue = clonedValue.get(valueType.getName());
                        } else if (valueType.getDefaultValue() != null) {
                            // Use the default value
                            syntheticValue = valueType.getDefaultValue();
                        } else {
                            // Use an undefined value
                            syntheticValue = new ModelNode();
                        }
                        valueType.getValidator().validateParameter(valueType.getName(), syntheticValue);
                    }
                }
            })
            .build();

    ObjectTypeAttributeDefinition REPLACE = ObjectTypeAttributeDefinition.Builder.of("replace", FILTER_PATTERN, REPLACEMENT, REPLACE_ALL)
            .setAllowExpression(false)
            .setRequired(false)
            .setValidator(new ObjectTypeValidator(false, FILTER_PATTERN, REPLACEMENT, REPLACE_ALL) {
                @Override
                public void validateParameter(final String parameterName, final ModelNode value) throws OperationFailedException {
                    super.validateParameter(parameterName, value);
                    final ModelNode clonedValue = value.clone();
                    final AttributeDefinition[] allowedValues = {FILTER_PATTERN, REPLACEMENT, REPLACE_ALL};
                    for (AttributeDefinition valueType : allowedValues) {
                        final ModelNode syntheticValue;
                        // Does the value the type
                        if (clonedValue.has(valueType.getName())) {
                            syntheticValue = clonedValue.get(valueType.getName());
                        } else if (valueType.getDefaultValue() != null) {
                            // Use the default value
                            syntheticValue = valueType.getDefaultValue();
                        } else {
                            // Use an undefined value
                            syntheticValue = new ModelNode();
                        }
                        valueType.getValidator().validateParameter(valueType.getName(), syntheticValue);
                    }
                }
            })
            .build();

    ObjectTypeAttributeDefinition NOT = ObjectTypeAttributeDefinition.Builder.of("not", ACCEPT, CHANGE_LEVEL, DENY, LEVEL, LEVEL_RANGE_LEGACY, MATCH, REPLACE)
            .setAllowExpression(false)
            .setRequired(false)
            .build();

    ObjectTypeAttributeDefinition ALL = ObjectTypeAttributeDefinition.Builder.of("all", ACCEPT, CHANGE_LEVEL, DENY, LEVEL, LEVEL_RANGE_LEGACY, MATCH, NOT, REPLACE)
            .setAllowExpression(false)
            .setRequired(false)
            .build();

    ObjectTypeAttributeDefinition ANY = ObjectTypeAttributeDefinition.Builder.of("any", ACCEPT, CHANGE_LEVEL, DENY, LEVEL, LEVEL_RANGE_LEGACY, MATCH, NOT, REPLACE)
            .setAllowExpression(false)
            .setRequired(false)
            .build();

    ObjectTypeAttributeDefinition FILTER = ObjectTypeAttributeDefinition.Builder.of("filter", ALL, ANY, ACCEPT, CHANGE_LEVEL, DENY, LEVEL, LEVEL_RANGE_LEGACY, MATCH, NOT, REPLACE)
            .setAllowExpression(false)
            .addAlternatives("filter-spec")
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .setRequired(false)
            .build();

    String ADD_HANDLER_OPERATION_NAME = "add-handler";

    String REMOVE_HANDLER_OPERATION_NAME = "remove-handler";
}
