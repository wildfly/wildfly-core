/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class RootResourceDefinition extends SimpleResourceDefinition {

    private static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder("name", ModelType.STRING, false).build();

    public RootResourceDefinition(String name) {
        super(PathElement.pathElement(SUBSYSTEM, name), NonResolvingResourceDescriptionResolver.INSTANCE,
                new AddSubsystemHandler(), ModelOnlyRemoveStepHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(NAME, null, new ModelOnlyWriteAttributeHandler(NAME));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
    }

    private static class AddSubsystemHandler extends AbstractAddStepHandler {

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            NAME.validateAndSet(operation, model);

        }
    }


}
