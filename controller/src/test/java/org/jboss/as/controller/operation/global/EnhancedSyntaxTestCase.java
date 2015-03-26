/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
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

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
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
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class EnhancedSyntaxTestCase extends AbstractControllerTestBase {

    private static final StringListAttributeDefinition LIST_ATTRIBUTE = new StringListAttributeDefinition.Builder("my-list-attribute")
            .setAllowNull(true)
            .setAllowDuplicates(false)
            .build();
    private static final PropertiesAttributeDefinition MAP_ATTRIBUTE = new PropertiesAttributeDefinition.Builder("my-map-attribute", true)
            .setAllowNull(true)
            .setStorageRuntime()
            .build();

    private static final PropertiesAttributeDefinition MAP_ATTRIBUTE2 = new PropertiesAttributeDefinition.Builder("my-map-attribute2", true)
            .setAllowNull(true)
            .setStorageRuntime()
            .build();
    private static PathAddress TEST_ADDRESS = PathAddress.pathAddress("subsystem", "test");

    private static ModelNode runtimeListAttributeValue = new ModelNode();
    private static ModelNode runtimeMap2AttributeValue = new ModelNode();

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
                        MAP_ATTRIBUTE2.validateAndSet(operation, model);
                    }

                })
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(LIST_ATTRIBUTE, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.getResult().set(runtimeListAttributeValue);
                    }
                }, new AbstractWriteAttributeHandler() {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        runtimeListAttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {

                    }
                })
                .addReadWriteAttribute(MAP_ATTRIBUTE, null, new AbstractWriteAttributeHandler() {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {

                    }
                }).addReadWriteAttribute(MAP_ATTRIBUTE2, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.getResult().set(runtimeMap2AttributeValue);
                    }
                }, new AbstractWriteAttributeHandler() {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        runtimeMap2AttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {

                    }
                })
                .build();
    }

    /*
    Map read enhancements

    # return value of map-attribute with key "myKey"
    :read-attribute(name=map-attribute.myKey)

    List read enhancements

    # return element under index 5 of list-attribute
    :read-attribute(name=list-attribute[5])

     */

    @Test
    public void testReadAttribute() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        op.get("value").set("map-value");
        executeCheckNoFailure(op);

        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        Assert.assertEquals("map-value", executeForResult(op).asString());

        // return value of map-attribute with key "myKey"
        // :read-attribute(name=map-attribute.myKey)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName() + ".map-key");
        Assert.assertEquals("map-value", executeForResult(op).asString());

        op.get("name").set(MAP_ATTRIBUTE.getName() + ".wrong-key");
        executeForFailure(op);

        //test list elements

        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        //add second value
        op.get("value").set("value2");
        executeCheckNoFailure(op);


        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        Assert.assertTrue(executeForResult(op).isDefined());

        // return value of list-attribute on index 0
        // :read-attribute(name=map-attribute.myKey)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName() + "[0]");
        Assert.assertEquals("value1", executeForResult(op).asString());

        op.get("name").set(LIST_ATTRIBUTE.getName() + "[1]");
        Assert.assertEquals("value2", executeForResult(op).asString());




      /*  map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
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
        Assert.assertTrue(map.isEmpty());*/
    }

}
