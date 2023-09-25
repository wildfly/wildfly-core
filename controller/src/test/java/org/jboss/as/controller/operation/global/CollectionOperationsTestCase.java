/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operation.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class CollectionOperationsTestCase extends AbstractCollectionOperationsTestCase {

    private static final String WRONG_ATTRIBUTE_NAME = "attribute.with.wrong.name";

    private static final StringListAttributeDefinition LIST_ATTRIBUTE = new StringListAttributeDefinition.Builder(LIST_ATTRIBUTE_NAME)
            .setRequired(false)
            .setAllowDuplicates(false)
            .build();
    private static final PropertiesAttributeDefinition MAP_ATTRIBUTE = new PropertiesAttributeDefinition.Builder(MAP_ATTRIBUTE_NAME, true)
            .setRequired(false)
            .build();

    private static final StringListAttributeDefinition WRONG_ATTRIBUTE = new StringListAttributeDefinition.Builder(WRONG_ATTRIBUTE_NAME)
                .setRequired(false)
                .build();

    private static PathAddress TEST_ADDRESS = PathAddress.pathAddress("subsystem", "test");

    @Override
    protected ResourceDefinition createProfileResourceDefinition() {
        return ResourceBuilder.Factory.create(TEST_ADDRESS.getElement(0),
                NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddOperation(new AbstractAddStepHandler() {
                    @Override
                    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
                        LIST_ATTRIBUTE.validateAndSet(operation, model);
                        MAP_ATTRIBUTE.validateAndSet(operation, model);
                        WRONG_ATTRIBUTE.validateAndSet(operation, model);
                    }
                })
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(WRONG_ATTRIBUTE, null, new ModelOnlyWriteAttributeHandler(WRONG_ATTRIBUTE))
                .addReadWriteAttribute(LIST_ATTRIBUTE, null, new AbstractWriteAttributeHandler(LIST_ATTRIBUTE) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        runtimeListAttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
                    }
                })
                .addReadWriteAttribute(MAP_ATTRIBUTE, null, new AbstractWriteAttributeHandler(MAP_ATTRIBUTE) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        runtimeMapAttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
                    }
                }).build();
    }

    @Test
    public void testListForWrongAttributeNameOperations() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(WRONG_ATTRIBUTE_NAME);
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        //add second value
        op.get("value").set("value2");
        executeCheckNoFailure(op);

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(WRONG_ATTRIBUTE_NAME);
        op.get("index").set(0);
        Assert.assertEquals("value1", executeForResult(op).asString());

    }

}
