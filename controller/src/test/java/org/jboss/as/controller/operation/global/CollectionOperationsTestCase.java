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

package org.jboss.as.controller.operation.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class CollectionOperationsTestCase extends AbstractControllerTestBase {

    private static final StringListAttributeDefinition LIST_ATTRIBUTE = new StringListAttributeDefinition.Builder("my-list-attribute")
            .setAllowNull(true)
            .setAllowDuplicates(false)
            .build();
    private static final PropertiesAttributeDefinition MAP_ATTRIBUTE = new PropertiesAttributeDefinition.Builder("my-map-attribute", true)
            .setAllowNull(true)
            .setStorageRuntime()
            .build();

    private static final StringListAttributeDefinition WRONG_ATTRIBUTE_NAME = new StringListAttributeDefinition.Builder("attribute.with.wrong.name")
                .setAllowNull(true)
                .build();
    private static PathAddress TEST_ADDRESS = PathAddress.pathAddress("subsystem", "test");

    private static ModelNode runtimeListAttributeValue = new ModelNode();
    private static ModelNode runtimeMapAttributeValue = new ModelNode();

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        ResourceDefinition profileDefinition = createDummyProfileResourceDefinition();
        rootRegistration.registerSubModel(profileDefinition);
    }

    private static ResourceDefinition createDummyProfileResourceDefinition() {
        return ResourceBuilder.Factory.create(TEST_ADDRESS.getElement(0),
                new NonResolvingResourceDescriptionResolver())
                .setAddOperation(new AbstractAddStepHandler() {

                    @Override
                    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
                        LIST_ATTRIBUTE.validateAndSet(operation, model);
                        MAP_ATTRIBUTE.validateAndSet(operation, model);
                        WRONG_ATTRIBUTE_NAME.validateAndSet(operation, model);
                    }

                })
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(WRONG_ATTRIBUTE_NAME, null ,new ModelOnlyWriteAttributeHandler(WRONG_ATTRIBUTE_NAME))
                .addReadWriteAttribute(LIST_ATTRIBUTE, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.getResult().set(runtimeMapAttributeValue);
                    }
                }, new AbstractWriteAttributeHandler(LIST_ATTRIBUTE) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
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
                        context.getResult().set(runtimeMapAttributeValue);
                    }
                }, new AbstractWriteAttributeHandler() {
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


    @Test
    public void testMapOperations() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        op.get("value").set("map-value");
        executeCheckNoFailure(op);

        Map<String, String> map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value", map.get("map-key"));

        executeCheckNoFailure(op);

        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        Assert.assertEquals("map-value", executeForResult(op).asString());

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value", map.get("map-key"));


        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        op.get("value").set("map-value2");
        executeCheckNoFailure(op);

        //check for properly updated existing key
        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        Assert.assertEquals("map-value2", executeForResult(op).asString());

        //add second entry
        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key2");
        op.get("value").set("map-value2");
        executeCheckNoFailure(op);

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(2, map.size());
        Assert.assertEquals("map-value2", map.get("map-key"));
        Assert.assertEquals("map-value2", map.get("map-key2"));

        //remove second entry
        op = createOperation("map-remove", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key2");
        executeCheckNoFailure(op);

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value2", map.get("map-key"));
        Assert.assertFalse(map.containsKey("map-key2"));

        //clear map
        op = createOperation("map-clear", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        executeCheckNoFailure(op);
        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void testListOperations() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        List<String> list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("value1", list.get(0));

        //add second value
        op.get("value").set("value2");
        executeCheckNoFailure(op);

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("index").set(0);
        Assert.assertEquals("value1", executeForResult(op).asString());

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("index").set(1);
        Assert.assertEquals("value2", executeForResult(op).asString());

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("value1", list.get(0));
        Assert.assertEquals("value2", list.get(1));

        //insert on index 0
        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("value").set("inserted");
        op.get("index").set(0);
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(3, list.size());
        Assert.assertEquals("inserted", list.get(0));
        Assert.assertEquals("value1", list.get(1));


        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("index").set(0);
        Assert.assertEquals("inserted", executeForResult(op).asString());


        //remove by value
        op = createOperation("list-remove", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("inserted", list.get(0));
        Assert.assertEquals("value2", list.get(1));

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("index").set(1);
        Assert.assertEquals("value2", executeForResult(op).asString());


        //remove by index
        op = createOperation("list-remove", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("index").set(0);
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("value2", list.get(0));

        //clear
        op = createOperation("list-clear", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertTrue(list.isEmpty());

        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("value").set("one");
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(1, list.size());

        /*op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("value").set("one");*/
        executeCheckForFailure(op); //duplicates not allowed
    }


    @Test
    public void testListForWrongAttributeNameOperations() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(WRONG_ATTRIBUTE_NAME.getName());
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        //add second value
        op.get("value").set("value2");
        executeCheckNoFailure(op);

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(WRONG_ATTRIBUTE_NAME.getName());
        op.get("index").set(0);
        Assert.assertEquals("value1", executeForResult(op).asString());

    }
}
