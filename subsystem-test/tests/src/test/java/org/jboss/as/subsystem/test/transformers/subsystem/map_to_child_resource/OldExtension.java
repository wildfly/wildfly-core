/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
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

    static final SimpleAttributeDefinition TEST = new SimpleAttributeDefinition("test", ModelType.STRING, false);

    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinition("value", ModelType.STRING, false);

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(2, 0, 0));
        registration.registerXMLElementWriter(new EmptyParser());
        final ManagementResourceRegistration reg = registration.registerSubsystemModel(new TestResourceDefinition());

        registerTransformers(registration);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, EXTENSION_NAME, EmptyParser::new);
    }

    private void registerTransformers(SubsystemRegistration subsystem) {
        // Register the transformers
        ResourceTransformationDescriptionBuilder builder = ResourceTransformationDescriptionBuilder.Factory.createSubsystemInstance();

        TransformationDescription.Tools.register(builder.build(), subsystem, ModelVersion.create(1, 0, 0));
    }

    private class EmptyParser implements XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter streamWriter, SubsystemMarshallingContext context) throws XMLStreamException {
        }
    }

    protected static class TestResourceDefinition extends SimpleResourceDefinition {
        protected TestResourceDefinition() {
            super(SUBSYSTEM_PATH,
                    new NonResolvingResourceDescriptionResolver(),
                    new ModelOnlyAddStepHandler(TEST),
                    ModelOnlyRemoveStepHandler.INSTANCE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(TEST, null, new ModelOnlyWriteAttributeHandler());
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new PropertyResourceDefinition());
        }
    }

    protected static class PropertyResourceDefinition extends SimpleResourceDefinition {
        protected PropertyResourceDefinition() {
            super(PathElement.pathElement("property"),
                    new NonResolvingResourceDescriptionResolver(),
                    new ModelOnlyAddStepHandler(VALUE),
                    ModelOnlyRemoveStepHandler.INSTANCE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(VALUE, null, new ModelOnlyWriteAttributeHandler());
        }
    }
}
