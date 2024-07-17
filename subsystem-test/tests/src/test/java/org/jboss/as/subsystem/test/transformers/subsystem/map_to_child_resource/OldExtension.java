/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.transformers.subsystem.map_to_child_resource;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Kabir Khan
 */
public class OldExtension implements Extension {
    public static final String SUBSYSTEM_NAME = "test-subsystem";
    public static final String EXTENSION_NAME = "org.jboss.as.test.transformers";
    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);

    static final SimpleAttributeDefinition TEST = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING, false).build();

    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder("value", ModelType.STRING, false).build();

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1, 0, 0));
        registration.registerXMLElementWriter(new EmptyParser());
        registration.registerSubsystemModel(new TestResourceDefinition());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, EXTENSION_NAME, EmptyParser::new);
    }

    private static final class EmptyParser implements XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter streamWriter, SubsystemMarshallingContext context) throws XMLStreamException {
        }
    }

    private static final class TestResourceDefinition extends SimpleResourceDefinition {
        private TestResourceDefinition() {
            super(SUBSYSTEM_PATH,
                    NonResolvingResourceDescriptionResolver.INSTANCE,
                    new ModelOnlyAddStepHandler(TEST),
                    ModelOnlyRemoveStepHandler.INSTANCE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(TEST, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new PropertyResourceDefinition());
        }
    }

    private static final class PropertyResourceDefinition extends SimpleResourceDefinition {
        private PropertyResourceDefinition() {
            super(PathElement.pathElement("property"),
                    NonResolvingResourceDescriptionResolver.INSTANCE,
                    new ModelOnlyAddStepHandler(VALUE),
                    ModelOnlyRemoveStepHandler.INSTANCE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(VALUE, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
    }
}
