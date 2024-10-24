/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.jboss.as.subsystem.test.transformationutils.subsystem;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.subsystem.test.simple.subsystem.SimpleSubsystemExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

public class TransformationUtilsExtension implements Extension {
        /** The name space used for the {@code substystem} element */
    public static final String NAMESPACE = "urn:mycompany:test-subsystem:1.0";

    /** The name of our subsystem within the model. */
    public static final String SUBSYSTEM_NAME = "test-subsystem";

    /** The parser used for parsing our subsystem */
    private final SubsystemParser parser = new SubsystemParser();

    private final ResourceDefinition subsystemResourceDefinition;

    private TransformationUtilsExtension(ResourceDefinition subsystemResourceDefinition) {
        this.subsystemResourceDefinition = subsystemResourceDefinition;
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, parser);
    }


    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(subsystemResourceDefinition);
        //We always need to add a 'describe' operation
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        subsystem.registerXMLElementWriter(parser);
    }

    public static Builder createBuilder() {
        return new Builder(true, PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
    }

    /**
    * The subsystem parser, which uses stax to read and write to and from xml
    */
    private static class SubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

        /** {@inheritDoc} */
        @Override
        public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
            context.startSubsystemElement(SimpleSubsystemExtension.NAMESPACE, false);
            writer.writeEndElement();
        }

        /** {@inheritDoc} */
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            // Require no content
            ParseUtils.requireNoContent(reader);
            list.add(Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME))));
        }
    }

    public static class Builder {
        private final PathElement pathElement;
        private Set<AttributeDefinition> attributes = new HashSet<>();
        private Set<Builder> children = new HashSet<>();

        private final boolean top;


        private Builder(boolean top, PathElement pathElement) {
            this.top = top;
            this.pathElement = pathElement;
        }

        public Builder addAttribute(String name) {
            AttributeDefinition attr = SimpleAttributeDefinitionBuilder.create(name, ModelType.STRING)
                    .setRequired(false)
                    .build();
            attributes.add(attr);
            return this;
        }

        public Builder createChildBuilder(PathElement pathElement) {
            Builder childBuilder = new Builder(false, pathElement);
            children.add(childBuilder);
            return childBuilder;
        }

        public TransformationUtilsExtension build() {
            if (!top) {
                throw new IllegalArgumentException();
            }
            return new TransformationUtilsExtension(buildResourceDefinition());
        }

        ResourceDefinition buildResourceDefinition() {
            Set<ResourceDefinition> children = new HashSet<>();
            for (Builder child : this.children) {
                children.add(child.buildResourceDefinition());
            }
            return new TestResourceDefinition(pathElement, attributes, children);
        }

    }

    private static class TestResourceDefinition extends SimpleResourceDefinition {
        private final Set<AttributeDefinition> attributes;
        private final Set<ResourceDefinition> children;

        public TestResourceDefinition(PathElement pathElement, Set<AttributeDefinition> attributes, Set<ResourceDefinition> children) {
            super(pathElement,
                NonResolvingResourceDescriptionResolver.INSTANCE,
                ModelOnlyAddStepHandler.INSTANCE,
                ModelOnlyRemoveStepHandler.INSTANCE);
            this.attributes = attributes;
            this.children = children;
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            for (AttributeDefinition attr : attributes) {
                resourceRegistration.registerReadWriteAttribute(attr, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            for (ResourceDefinition child : children) {
                resourceRegistration.registerSubModel(child);
            }
        }
    }
}
