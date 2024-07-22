/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.extension.remove;

import java.util.Collections;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ChildResourceDefinition extends SimpleResourceDefinition {
    private static final SimpleAttributeDefinition CHILD_ATTRIBUTE = new SimpleAttributeDefinitionBuilder("child-attr", ModelType.STRING, true).build();

    ChildResourceDefinition() {
        super(new Parameters(PathElement.pathElement("child"), NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(new AddChildHandler())
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .setIncorporatingCapabilities(Collections.singleton(RootResourceDefinition.CAPABILITY))
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(CHILD_ATTRIBUTE, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
    }

    private static class AddChildHandler extends AbstractAddStepHandler {

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            CHILD_ATTRIBUTE.validateAndSet(operation, model);
            //model.get("child-attr").set("initialized");
        }

    }
}
