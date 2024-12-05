/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;

import java.util.List;
import java.util.Map;

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public abstract class AbstractCollectionOperationsTestCase extends AbstractControllerTestBase {

    protected static final String LIST_ATTRIBUTE_NAME = "my-list-attribute";
    protected static final String MAP_ATTRIBUTE_NAME = "my-map-attribute";

    protected static PathAddress TEST_ADDRESS = PathAddress.pathAddress("subsystem", "test");

    protected ModelNode runtimeListAttributeValue;
    protected ModelNode runtimeMapAttributeValue;

    @Override
    protected void initModel(ManagementModel managementModel) {
        runtimeListAttributeValue = new ModelNode();
        runtimeMapAttributeValue = new ModelNode();

        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);

        ResourceDefinition profileDefinition = createProfileResourceDefinition();
        rootRegistration.registerSubModel(profileDefinition);
    }

    protected abstract ResourceDefinition createProfileResourceDefinition();


    @Test
    public void testMapOperations() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key");
        op.get("value").set("map-value");
        executeCheckNoFailure(op);

        Map<String, String> map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value", map.get("map-key"));

        executeCheckNoFailure(op);

        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key");
        Assert.assertEquals("map-value", executeForResult(op).asString());

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value", map.get("map-key"));


        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key");
        op.get("value").set("map-value2");
        executeCheckNoFailure(op);

        //check for properly updated existing key
        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key");
        Assert.assertEquals("map-value2", executeForResult(op).asString());

        //add second entry
        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key2");
        op.get("value").set("map-value2");
        executeCheckNoFailure(op);

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(2, map.size());
        Assert.assertEquals("map-value2", map.get("map-key"));
        Assert.assertEquals("map-value2", map.get("map-key2"));

        //remove second entry
        op = createOperation("map-remove", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key2");
        executeCheckNoFailure(op);

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value2", map.get("map-key"));
        Assert.assertFalse(map.containsKey("map-key2"));

        //clear map
        op = createOperation("map-clear", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        executeCheckNoFailure(op);
        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void testMapOperationsInComposite() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode composite = createOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key1");
        op.get("value").set("map-value1");
        composite.get(STEPS).add(op);

        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key2");
        op.get("value").set("map-value2");
        composite.get(STEPS).add(op);

        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key3");
        op.get("value").set("map-value3");
        composite.get(STEPS).add(op);

        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key4");
        op.get("value").set("map-value4");
        composite.get(STEPS).add(op);

        executeCheckNoFailure(composite);

        Map<String, String> map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(4, map.size());
        Assert.assertEquals("map-value1", map.get("map-key1"));
        Assert.assertEquals("map-value2", map.get("map-key2"));
        Assert.assertEquals("map-value3", map.get("map-key3"));
        Assert.assertEquals("map-value4", map.get("map-key4"));

        composite = createOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        op = createOperation("map-remove", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key1");
        composite.get(STEPS).add(op);

        op = createOperation("map-remove", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key2");
        composite.get(STEPS).add(op);

        op = createOperation("map-remove", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE_NAME);
        op.get("key").set("map-key4");
        composite.get(STEPS).add(op);

        executeCheckNoFailure(composite);

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value3", map.get("map-key3"));
    }

    @Test
    public void testListOperations() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        List<String> list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("value1", list.get(0));

        //add second value
        op.get("value").set("value2");
        executeCheckNoFailure(op);

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("index").set(0);
        Assert.assertEquals("value1", executeForResult(op).asString());

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("index").set(1);
        Assert.assertEquals("value2", executeForResult(op).asString());

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("value1", list.get(0));
        Assert.assertEquals("value2", list.get(1));

        //insert on index 0
        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("inserted");
        op.get("index").set(0);
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(3, list.size());
        Assert.assertEquals("inserted", list.get(0));
        Assert.assertEquals("value1", list.get(1));


        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("index").set(0);
        Assert.assertEquals("inserted", executeForResult(op).asString());


        //remove by value
        op = createOperation("list-remove", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("inserted", list.get(0));
        Assert.assertEquals("value2", list.get(1));

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("index").set(1);
        Assert.assertEquals("value2", executeForResult(op).asString());


        //remove by index
        op = createOperation("list-remove", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("index").set(0);
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("value2", list.get(0));

        //clear
        op = createOperation("list-clear", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertTrue(list.isEmpty());

        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
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
    public void testListOperationsInComposite() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));

        ModelNode composite = createOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);

        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value1");
        composite.get(STEPS).add(op);

        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value2");
        composite.get(STEPS).add(op);

        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value3");
        composite.get(STEPS).add(op);

        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value4");
        composite.get(STEPS).add(op);

        executeCheckNoFailure(composite);

        List<String> list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(4, list.size());
        Assert.assertEquals("value1", list.get(0));
        Assert.assertEquals("value2", list.get(1));
        Assert.assertEquals("value3", list.get(2));
        Assert.assertEquals("value4", list.get(3));

        composite = createOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);

        op = createOperation("list-remove", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value4");
        composite.get(STEPS).add(op);

        op = createOperation("list-remove", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value3");
        composite.get(STEPS).add(op);

        op = createOperation("list-remove", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE_NAME);
        op.get("value").set("value1");
        composite.get(STEPS).add(op);

        executeCheckNoFailure(composite);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("value2", list.get(0));
    }
}
