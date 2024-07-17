/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test.transformers.subsystem.simple;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.TransformerOperationAttachment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public abstract class VersionedExtensionCommon implements Extension {

    public static final String SUBSYSTEM_NAME = "versioned-subsystem";
    public static final String EXTENSION_NAME = "org.jboss.as.test.transformers";
    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);
    static final AttributeDefinition TEST_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create("test-attribute", ModelType.STRING).build();

    private final SubsystemParser parser = new SubsystemParser(EXTENSION_NAME);

    public SubsystemParser getParser() {
        return parser;
    }

    protected ManagementResourceRegistration initializeSubsystem(final SubsystemRegistration registration) {
        // Common subsystem tasks
        registration.registerXMLElementWriter(getParser());

        final ManagementResourceRegistration reg = registration.registerSubsystemModel(new TestResourceDefinition(SUBSYSTEM_PATH, new TestModelOnlyAddHandler(TEST_ATTRIBUTE)) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                resourceRegistration.registerReadWriteAttribute(TEST_ATTRIBUTE, null, new BasicAttributeWriteHandler());
            }
        });
        reg.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        return reg;
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, EXTENSION_NAME, parser);
    }

    protected ModelNode createAddOperation(PathAddress addr) {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).set(addr.toModelNode());
        return op;
    }

    protected abstract void addChildElements(List<ModelNode> list);

    protected static class TestResourceDefinition extends SimpleResourceDefinition {
        protected TestResourceDefinition(PathElement element) {
            this(element, new TestModelOnlyAddHandler());
        }

        @SuppressWarnings("deprecation")
        protected TestResourceDefinition(PathElement element, AbstractAddStepHandler addHandler) {
            super(new Parameters(element, TEST_RESOURCE_DESCRIPTION_RESOLVER)
                    .setAddHandler(addHandler)
                    .setRemoveHandler(NOOP_REMOVE_HANDLER)
                    .setRemoveRestartLevel(OperationEntry.Flag.RESTART_NONE));
        }
    }

    private static class TestModelOnlyAddHandler extends AbstractAddStepHandler {
        AttributeDefinition[] attributes;

        public TestModelOnlyAddHandler(AttributeDefinition... attributes) {
            this.attributes = attributes;
        }

        @Override
        protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
            for (AttributeDefinition def : attributes) {
                def.validateAndSet(operation, model);
            }
        }
    }

    private static final AbstractRemoveStepHandler NOOP_REMOVE_HANDLER = new AbstractRemoveStepHandler() {
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
        }
    };

    private static class BasicAttributeWriteHandler extends AbstractWriteAttributeHandler<Void> {

        protected BasicAttributeWriteHandler() {
            super(List.of());
        }

        protected BasicAttributeWriteHandler(AttributeDefinition def) {
            super(def);
        }

        @Override
        protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue, ModelNode oldValue, Resource model) throws OperationFailedException {
            super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
            if (!context.isBooting()) {
                TransformerOperationAttachment attachment = TransformerOperationAttachment.getOrCreate(context);
                attachment.attachIfAbsent(TestAttachment.KEY, new TestAttachment(oldValue.asString()));
            }
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> voidHandbackHolder) throws OperationFailedException {
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {

        }
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
            subsystemAdd.get("test-attribute").set("This is only a test");
            list.add(subsystemAdd);

            addChildElements(list);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter streamWriter, SubsystemMarshallingContext context) throws XMLStreamException {
            context.startSubsystemElement(namespace, false);
            streamWriter.writeEndElement();
        }
    }

    static final ResourceDescriptionResolver TEST_RESOURCE_DESCRIPTION_RESOLVER = new NonResolvingResourceDescriptionResolver();

    public static final class TestAttachment {
        public static final OperationContext.AttachmentKey<TestAttachment> KEY = OperationContext.AttachmentKey.create(TestAttachment.class);

        public volatile String s;

        public TestAttachment(String s) {
            this.s = s;
        }
    }
}