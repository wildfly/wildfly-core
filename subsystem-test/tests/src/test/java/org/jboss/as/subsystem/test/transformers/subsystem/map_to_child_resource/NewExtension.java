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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.MapOperations;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformerOperationAttachment;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Kabir Khan
 */
public class NewExtension implements Extension {
    public static final String SUBSYSTEM_NAME = "test-subsystem";
    public static final String EXTENSION_NAME = "org.jboss.as.test.transformers";
    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);

    private SubsystemParser parser = new SubsystemParser(EXTENSION_NAME);

    static final SimpleAttributeDefinition TEST = new SimpleAttributeDefinition("test", ModelType.STRING, false);
    static final AttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder("properties", true).build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {TEST, PROPERTIES};

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(2, 0, 0));
        registration.registerXMLElementWriter(new SubsystemParser(EXTENSION_NAME));
        final ManagementResourceRegistration reg = registration.registerSubsystemModel(new TestResourceDefinition());

        registerTransformers(registration);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, EXTENSION_NAME, parser);
    }

    private void registerTransformers(SubsystemRegistration subsystem) {
        // Register the transformers
        ResourceTransformationDescriptionBuilder builder = ResourceTransformationDescriptionBuilder.Factory.createSubsystemInstance();
        builder.getAttributeBuilder()
                    .setValueConverter(new AttributeConverter.DefaultAttributeConverter() {
                        @Override
                        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
                            if (attributeValue.isDefined()) {
                                attributeValue.set(attributeValue.asString().toUpperCase());
                            }
                        }
                    }, TEST)
                .end();
        builder.addOperationTransformationOverride(ADD)
                .inheritResourceAttributeDefinitions()
                .setCustomOperationTransformer(PROPERTIES_ADD_OPERATION_TRANSFORMER);
        builder.setCustomResourceTransformer(PROPERTIES_RESOURCE_TRANSFORMER);

        Set<String> writeAttributeOperations = new HashSet<>(MapOperations.MAP_OPERATION_NAMES);
        writeAttributeOperations.add(WRITE_ATTRIBUTE_OPERATION);
        writeAttributeOperations.add(UNDEFINE_ATTRIBUTE_OPERATION);
        for (String opName : writeAttributeOperations) {
            builder.addOperationTransformationOverride(opName)
                    .inheritResourceAttributeDefinitions()
                    .setCustomOperationTransformer(PROPERTIES_WRITE_ATTRIBUTE_TRANSFORMER);
        }
        TransformationDescription.Tools.register(builder.build(), subsystem, ModelVersion.create(1, 0, 0));
    }

    private class SubsystemParser implements XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

        private final String namespace;

        private SubsystemParser(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            ParseUtils.requireNoAttributes(reader);
            ParseUtils.requireNoContent(reader);

            ModelNode subsystemAdd = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME)));
            subsystemAdd.get("test").set("Hello");
            subsystemAdd.get("properties", "one").set("A");
            subsystemAdd.get("properties", "two").set("B");

            list.add(subsystemAdd);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter streamWriter, SubsystemMarshallingContext context) throws XMLStreamException {
            context.startSubsystemElement(namespace, false);
            streamWriter.writeEndElement();
        }
    }

    protected static class TestResourceDefinition extends SimpleResourceDefinition {
        protected TestResourceDefinition() {
            super(SUBSYSTEM_PATH,
                    new NonResolvingResourceDescriptionResolver(),
                    new ModelOnlyAddStepHandler(ATTRIBUTES),
                    ModelOnlyRemoveStepHandler.INSTANCE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(TEST, null, new ModelOnlyWriteAttributeHandler());
            //Custom write handler to attach the old value
            resourceRegistration.registerReadWriteAttribute(PROPERTIES, null, new ModelOnlyWriteAttributeHandler(){
                @Override
                protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue, ModelNode oldValue, Resource model) throws OperationFailedException {
                    super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
                    if (!context.isBooting()) {
                        TransformerOperationAttachment attachment = TransformerOperationAttachment.getOrCreate(context);
                        attachment.attachIfAbsent(InitialValueAttachement.KEY, new InitialValueAttachement(oldValue));
                    }
                }
            });
        }
    }


    private static class InitialValueAttachement {
        static final OperationContext.AttachmentKey<InitialValueAttachement> KEY = OperationContext.AttachmentKey.create(InitialValueAttachement.class);

        private final ModelNode value;
        volatile boolean done;

        private InitialValueAttachement(ModelNode value) {
            this.value = value;
        }

        public ModelNode getValue() {
            return value;
        }
    }

    private static ResourceTransformer PROPERTIES_RESOURCE_TRANSFORMER = new ResourceTransformer() {
        @Override
        public void transformResource(ResourceTransformationContext context, PathAddress address, Resource resource) throws OperationFailedException {
            ModelNode properties = resource.getModel().remove("properties");
            ResourceTransformationContext childCtx = context.addTransformedResourceFromRoot(address, resource);

            for (ModelNode property : properties.asList()) {
                Property prop = property.asProperty();
                Resource child = Resource.Factory.create();
                child.getModel().get("value").set(prop.getValue());
                childCtx.addTransformedResource(PathAddress.pathAddress("property", prop.getName()), child);
            }
        }
    };

    private static OperationTransformer PROPERTIES_ADD_OPERATION_TRANSFORMER = new OperationTransformer() {
        @Override
        public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation) throws OperationFailedException {
            ModelNode properties = operation.remove("properties");

            ModelNode composite = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
            ModelNode steps = composite.get("steps");
            steps.add(operation);

            for (ModelNode property : properties.asList()) {
                Property prop = property.asProperty();
                ModelNode addProp = Util.createAddOperation(address.append("property", prop.getName()));
                addProp.get("value").set(prop.getValue());
                steps.add(addProp);
            }

            return new TransformedOperation(composite, TransformedOperation.ORIGINAL_RESULT);
        }
    };

    private static OperationTransformer PROPERTIES_WRITE_ATTRIBUTE_TRANSFORMER = new OperationTransformer() {
        @Override
        public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation) throws OperationFailedException {
            String attributeName = operation.get(NAME).asString();
            if (!attributeName.equals("properties")) {
                return new TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
            }
            InitialValueAttachement initialValueAttachement = context.getAttachment(InitialValueAttachement.KEY);
            try {
                ModelNode initialValue = initialValueAttachement.getValue();
                ModelNode currentValue = context.readResourceFromRoot(address).getModel().get("properties");

                if (initialValue.equals(currentValue) || initialValueAttachement.done) {
                    //No change
                    return OperationTransformer.DISCARD.transformOperation(context, address, operation);
                }

                ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
                ModelNode steps = composite.get(STEPS);
                for (String key : getAllKeys(initialValue, currentValue)) {
                    ModelNode initial = initialValue.get(key);
                    ModelNode current = currentValue.get(key);

                    if (initial.isDefined() && current.isDefined() && !current.equals(initial)) {
                        //changed
                        steps.add(Util.getWriteAttributeOperation(address.append("property", key), "value", current));
                    } else if (initial.isDefined() && !current.isDefined()) {
                        //removed
                        steps.add(Util.createRemoveOperation(address.append("property", key)));
                    } else if (!initial.isDefined() && current.isDefined()) {
                        //added
                        ModelNode add = Util.createAddOperation(address.append("property", key));
                        add.get("value").set(current);
                        steps.add(add);
                    }
                }

                if (!steps.isDefined() || steps.asList().isEmpty()) {
                    return OperationTransformer.DISCARD.transformOperation(context, address, operation);
                } else if (steps.asList().size() == 1) {
                    return new TransformedOperation(steps.asList().get(0), OperationResultTransformer.ORIGINAL_RESULT);
                } else {
                    return new TransformedOperation(composite, OperationResultTransformer.ORIGINAL_RESULT);
                }
            } finally {
                initialValueAttachement.done = true;
            }
        }

        private Set<String> getAllKeys(ModelNode initialValue, ModelNode currentValue) {
            Set<String> allKeys = new HashSet<>();
            if (initialValue.isDefined()) {
                allKeys.addAll(initialValue.keys());
            }
            if (currentValue.isDefined()) {
                allKeys.addAll(currentValue.keys());
            }
            return allKeys;
        }
    };


}
