/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
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
 *
 */

package org.jboss.as.controller.operation.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;

/**
 * Testing collection operations for Storage.RUNTIME attributes.
 *
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 * @author Tomas Hofman
 */
public class StorageRuntimeCollectionOperationsTestCase extends AbstractCollectionOperationsTestCase {


    private static final StringListAttributeDefinition LIST_ATTRIBUTE = new StringListAttributeDefinition.Builder(LIST_ATTRIBUTE_NAME)
            .setRequired(false)
            .setAllowDuplicates(false)
            .setStorageRuntime()
            .build();
    private static final PropertiesAttributeDefinition MAP_ATTRIBUTE = new PropertiesAttributeDefinition.Builder(MAP_ATTRIBUTE_NAME, true)
            .setRequired(false)
            .setStorageRuntime()
            .build();

    protected ResourceDefinition createProfileResourceDefinition() {
        return ResourceBuilder.Factory.create(TEST_ADDRESS.getElement(0),
                NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddOperation(new AbstractAddStepHandler() {

                    @Override
                    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
                        LIST_ATTRIBUTE.validateAndSet(operation, model);
                        MAP_ATTRIBUTE.validateAndSet(operation, model);
                    }

                })
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(LIST_ATTRIBUTE, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.addStep((context1, operation1) -> context.getResult().set(runtimeListAttributeValue),
                                OperationContext.Stage.RUNTIME);
                    }
                }, new AbstractWriteAttributeHandler(LIST_ATTRIBUTE) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        // AbstractWriteAttributeHandler performs validation in MODEL stage, when final
                        // values are not yet known for Storage.RUNTIME attributes. Therefore is some
                        // validations are desired, they must be implemented in applyUpdateToRuntime().
                        final ModelNode syntheticOp = new ModelNode();
                        syntheticOp.get(attributeName).set(operation.get(VALUE));
                        LIST_ATTRIBUTE.validateOperation(syntheticOp);

                        runtimeListAttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
                    }
                })
                .addReadWriteAttribute(MAP_ATTRIBUTE, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.addStep((context1, operation1) -> context.getResult().set(runtimeMapAttributeValue),
                                OperationContext.Stage.RUNTIME);
                    }
                }, new AbstractWriteAttributeHandler(MAP_ATTRIBUTE) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        runtimeMapAttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
                    }
                })
                .build();
    }

}
