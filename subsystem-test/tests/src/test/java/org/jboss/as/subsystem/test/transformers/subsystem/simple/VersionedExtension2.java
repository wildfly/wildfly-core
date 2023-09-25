/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test.transformers.subsystem.simple;

import java.util.List;
import java.util.Map;

import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public class VersionedExtension2 extends VersionedExtensionCommon {

    // New element which does not exist in v1
    private static final PathElement NEW_ELEMENT = PathElement.pathElement("new-element");
    // Element which is element>renamed in v2
    private static final PathElement RENAMED = PathElement.pathElement("renamed", "element");

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(2));
        // Initialize the subsystem
        final ManagementResourceRegistration registration = initializeSubsystem(subsystem);

        // Add a new model, which does not exist in the old model
        registration.registerSubModel(new TestResourceDefinition(NEW_ELEMENT));
        // Add the renamed model
        registration.registerSubModel(new TestResourceDefinition(RENAMED));
    }


    @Override
    protected void addChildElements(List<ModelNode> list) {
        list.add(createAddOperation(PathAddress.pathAddress(SUBSYSTEM_PATH, RENAMED)));
        list.add(createAddOperation(PathAddress.pathAddress(SUBSYSTEM_PATH, PathElement.pathElement(NEW_ELEMENT.getKey(), "test"))));
    }

    public static final class TransformerRegistration implements ExtensionTransformerRegistration {

        @Override
        public String getSubsystemName() {
            return SUBSYSTEM_NAME;
        }

        @Override
        public void registerTransformers(SubsystemTransformerRegistration subsystem) {

            // Register the transformers
            ResourceTransformationDescriptionBuilder builder = ResourceTransformationDescriptionBuilder.Factory.createSubsystemInstance();
            builder.addChildRedirection(RENAMED, VersionedExtension1.ORIGINAL);
            builder.discardChildResource(NEW_ELEMENT);
            builder.getAttributeBuilder().addRejectCheck(new RejectAttributeChecker() {
                @Override
                public boolean rejectOperationParameter(PathAddress address, String attributeName, ModelNode attributeValue, ModelNode operation, TransformationContext context) {
                    TestAttachment testAttachment = context.getAttachment(TestAttachment.KEY);
                    if (testAttachment != null) {
                        if (testAttachment.s.equals("do reject")) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public boolean rejectResourceAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
                    return false;
                }

                @Override
                public String getRejectionLogMessageId() {
                    return "Rejected";
                }

                @Override
                public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
                    return "Rejected";
                }
            }, TEST_ATTRIBUTE);
            TransformationDescription.Tools.register(builder.build(), subsystem, ModelVersion.create(1, 0, 0));
        }
    }
}
