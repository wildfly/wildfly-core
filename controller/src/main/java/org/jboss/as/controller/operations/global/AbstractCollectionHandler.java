/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
abstract class AbstractCollectionHandler implements OperationStepHandler {

    static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinition("name", ModelType.STRING, false);
    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinition("value", ModelType.STRING, true);

    private final AttributeDefinition[] attributes;
    private final boolean requiredReadWriteAccess;

    AbstractCollectionHandler(AttributeDefinition... attributes) {
        this.attributes = attributes;
        requiredReadWriteAccess = true;
    }

    AbstractCollectionHandler(boolean requiredReadWrite, AttributeDefinition... attributes) {
        this.attributes = attributes;
        requiredReadWriteAccess = requiredReadWrite;
    }

    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        NAME.validateAndSet(operation, model);
        for (AttributeDefinition attr : attributes) {
            attr.validateAndSet(operation, model);
        }
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode operationModel = new ModelNode();
        populateModel(operation, operationModel);
        final String attributeName = NAME.resolveModelAttribute(context, operationModel).asString();
        final AttributeAccess attributeAccess = context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
        if (attributeAccess == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unknownAttribute(attributeName));
        } else if (requiredReadWriteAccess && attributeAccess.getAccessType() != AttributeAccess.AccessType.READ_WRITE) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.attributeNotWritable(attributeName));
        }
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));

        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode model = resource.getModel().get(attributeName);
        updateModel(context, operationModel, attributeAccess.getAttributeDefinition(),  model);
        if (requiredReadWriteAccess) {
            //debugf("Value '%s' for key '%s' being replaced by value '%s'.", oldValue, key, value);
            //lets validate it first
            attributeAccess.getAttributeDefinition().getValidator().validateParameter(attributeName, model);//todo move this to write-attribute operation!
            ModelNode writeOperation = Util.createOperation(WriteAttributeHandler.DEFINITION, address);
            writeOperation.get(NAME.getName()).set(attributeName);
            writeOperation.get(ModelDescriptionConstants.VALUE).set(model);
            context.addStep(writeOperation, WriteAttributeHandler.INSTANCE, OperationContext.Stage.MODEL);
        }
        context.stepCompleted();
    }

    abstract void updateModel(final OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException;
}
