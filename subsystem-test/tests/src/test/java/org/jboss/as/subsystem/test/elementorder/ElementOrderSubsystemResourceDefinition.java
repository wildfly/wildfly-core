/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.elementorder;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

public class ElementOrderSubsystemResourceDefinition extends SimpleResourceDefinition {
    static final String SUBSYSTEM_NAME = "element-order";
    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);
    static final ResourceRegistration GROUP_REGISTRATION = ResourceRegistration.of(PathElement.pathElement("group"));
    static final ResourceRegistration CHILD_REGISTRATION = ResourceRegistration.of(PathElement.pathElement("child"));
    static final ResourceRegistration CHILD_2_REGISTRATION = ResourceRegistration.of(PathElement.pathElement("child2"));

    static final AttributeDefinition ATTRIBUTE = new SimpleAttributeDefinitionBuilder("attribute", ModelType.STRING)
            .setRequired(false)
            .build();

    static final AttributeDefinition ATTRIBUTE_2 = new SimpleAttributeDefinitionBuilder("attribute2", ModelType.STRING)
            .setRequired(false)
            .build();

    ElementOrderSubsystemResourceDefinition() {
        super(new Parameters(PATH, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }

    @Override
    public void registerChildren(ManagementResourceRegistration registration) {
        registration.registerSubModel(new GroupResourceDefinition());
    }

    public class GroupResourceDefinition extends SimpleResourceDefinition {

        GroupResourceDefinition() {
            super(new Parameters(GROUP_REGISTRATION, NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                    .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration registration) {
            registration.registerReadWriteAttribute(ATTRIBUTE, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
            registration.registerReadWriteAttribute(ATTRIBUTE_2, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration registration) {
            registration.registerSubModel(new ChildResourceDefinition());
            registration.registerSubModel(new Child2ResourceDefinition());
        }
    }

    public class ChildResourceDefinition extends SimpleResourceDefinition {

        ChildResourceDefinition() {
            super(new Parameters(CHILD_REGISTRATION, NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                    .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
        }
    }

    public class Child2ResourceDefinition extends SimpleResourceDefinition {

        Child2ResourceDefinition() {
            super(new Parameters(CHILD_2_REGISTRATION, NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                    .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
        }
    }

}
