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

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class EnhancedSyntaxTestCase extends AbstractControllerTestBase {

    private static final StringListAttributeDefinition LIST_ATTRIBUTE = new StringListAttributeDefinition.Builder("my-list-attribute")
            .setAllowDuplicates(false)
            .setRequired(false)
            .build();

    private static final StringListAttributeDefinition RUNTIME_LIST_ATTRIBUTE = new StringListAttributeDefinition.Builder("runtime-list-attribute")
            .setRequired(false)
            .setStorageRuntime()
            .build();

    private static final PropertiesAttributeDefinition MAP_ATTRIBUTE = new PropertiesAttributeDefinition.Builder("map-attribute", true)
            .setRequired(false)
            .build();

    private static final PropertiesAttributeDefinition RUNTIME_MAP_ATTRIBUTE = new PropertiesAttributeDefinition.Builder("runtime-map-attribute", true)
            .setRequired(false)
            .setStorageRuntime()
            .build();

    private static final AttributeDefinition ATTR_1 = create("attr1", ModelType.STRING)
            .setRequired(false)
            .build();
    private static final AttributeDefinition ATTR_2 = create("attr2", ModelType.BOOLEAN)
            .setRequired(false)
            .build();
    private static final ObjectTypeAttributeDefinition COMPLEX_ATTRIBUTE = ObjectTypeAttributeDefinition.Builder.of("complex-attribute", ATTR_1, ATTR_2, MAP_ATTRIBUTE, RUNTIME_MAP_ATTRIBUTE).build();
    private static final ObjectListAttributeDefinition OBJECT_LIST = ObjectListAttributeDefinition.Builder.of("object-list", COMPLEX_ATTRIBUTE).setRequired(false).build();
    private static final ObjectTypeAttributeDefinition COMPLEX_ATTRIBUTE2 = ObjectTypeAttributeDefinition.Builder.of("complex-attribute2", OBJECT_LIST).build();
    private static final AttributeDefinition NORMAL_LOOKING_EXTENDED = new SimpleAttributeDefinitionBuilder("normal.looking.extended", ModelType.STRING, true).build();


    private static PathAddress TEST_ADDRESS = PathAddress.pathAddress("subsystem", "test");

    private static ModelNode runtimeListAttributeValue;
    private static ModelNode runtimeMapAttributeValue;

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);

        ResourceDefinition profileDefinition = createDummyProfileResourceDefinition();
        rootRegistration.registerSubModel(profileDefinition);
    }

    private static ResourceDefinition createDummyProfileResourceDefinition() {
        return ResourceBuilder.Factory.create(TEST_ADDRESS.getElement(0),
                NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddOperation(new AbstractAddStepHandler() {

                    @Override
                    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
                        LIST_ATTRIBUTE.validateAndSet(operation, model);
                        RUNTIME_LIST_ATTRIBUTE.validateAndSet(operation, model);
                        MAP_ATTRIBUTE.validateAndSet(operation, model);
                        RUNTIME_MAP_ATTRIBUTE.validateAndSet(operation, model);
                        COMPLEX_ATTRIBUTE.validateAndSet(operation, model);
                        OBJECT_LIST.validateAndSet(operation, model);
                        COMPLEX_ATTRIBUTE2.validateAndSet(operation, model);
                        NORMAL_LOOKING_EXTENDED.validateAndSet(operation, model);
                    }

                })
                .setRemoveOperation(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addReadWriteAttribute(LIST_ATTRIBUTE, null, new ModelOnlyWriteAttributeHandler(LIST_ATTRIBUTE))
                .addReadWriteAttribute(RUNTIME_LIST_ATTRIBUTE, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.addStep((context1, operation1) -> context.getResult().set(runtimeListAttributeValue),
                                OperationContext.Stage.RUNTIME);
                    }
                }, new AbstractWriteAttributeHandler(RUNTIME_LIST_ATTRIBUTE) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        runtimeListAttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
                    }
                })
                .addReadWriteAttribute(MAP_ATTRIBUTE, null, new ModelOnlyWriteAttributeHandler(MAP_ATTRIBUTE))
                .addReadWriteAttribute(RUNTIME_MAP_ATTRIBUTE, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        context.addStep((context1, operation1) -> context.getResult().set(runtimeMapAttributeValue),
                                OperationContext.Stage.RUNTIME);
                    }
                }, new AbstractWriteAttributeHandler(RUNTIME_MAP_ATTRIBUTE) {
                    @Override
                    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
                        runtimeMapAttributeValue = operation.get(VALUE);
                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
                    }
                }).addReadWriteAttribute(COMPLEX_ATTRIBUTE, null, new ModelOnlyWriteAttributeHandler(COMPLEX_ATTRIBUTE))
                .addReadWriteAttribute(OBJECT_LIST, null, new ModelOnlyWriteAttributeHandler(OBJECT_LIST))
                .addReadWriteAttribute(COMPLEX_ATTRIBUTE2, null, new ModelOnlyWriteAttributeHandler(COMPLEX_ATTRIBUTE2))
                .addReadWriteAttribute(NORMAL_LOOKING_EXTENDED, null, new ModelOnlyWriteAttributeHandler(NORMAL_LOOKING_EXTENDED))
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

    @Before
    public void setup() throws OperationFailedException {
        executeCheckNoFailure(createOperation("add", TEST_ADDRESS));
        runtimeListAttributeValue = new ModelNode();
        runtimeMapAttributeValue = new ModelNode();
    }

    @After
    public void cleanup() throws OperationFailedException {
        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));
    }


    @Test
    public void testReadAttribute() throws OperationFailedException {

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

    }

    @Test
    public void testComplexAttributes() throws OperationFailedException {
        //test set
        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE.getName());
        ModelNode value = new ModelNode();
        value.get(ATTR_1.getName()).set("attr1-string");
        value.get(ATTR_2.getName()).set(true);
        op.get("value").set(value);
        executeCheckNoFailure(op);

        //test read-attribute(name=complex-attribute.attr1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE.getName() + "." + ATTR_1.getName());
        Assert.assertEquals("attr1-string", executeForResult(op).asString());
        //test read-attribute(name=complex-attribute.attr2)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE.getName() + "." + ATTR_2.getName());
        Assert.assertTrue(executeForResult(op).asBoolean());

        //test List<Object>
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName());
        value = new ModelNode();
        for (int i = 0; i <= 5; i++) {
            ModelNode item = value.add();
            item.get(ATTR_1.getName()).set("value" + i);
            item.get(ATTR_2.getName()).set(true);
        }
        op.get("value").set(value);
        executeCheckNoFailure(op);


        //test read-attribute(name=object-list[1])
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[1]");
        Assert.assertTrue(executeForResult(op).isDefined());

        //test read-attribute(name=object-list[1].attr1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[3].attr1");
        Assert.assertEquals("value3", executeForResult(op).asString());


        //test read-attribute(name=object-list[-3].attr1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[-3].attr1");
        executeCheckForFailure(op);

        // test read non-existent element -- read-attribute(name=object-list[6])
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[6]");
        Assert.assertFalse(executeForResult(op).isDefined());

        // test write to non-existing element
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[6].attr1");
        op.get(VALUE).set("newvalue");
        executeCheckNoFailure(op);
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[6].attr1");
        Assert.assertEquals("newvalue", executeForResult(op).asString());


        //complex attribute with list with complex attributes

        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName());
        value = new ModelNode();

        ModelNode listValue = value.get(OBJECT_LIST.getName());

        for (int i = 0; i <= 5; i++) {
            ModelNode item = listValue.add();
            item.get(ATTR_1.getName()).set("value" + i);
            item.get(ATTR_2.getName()).set(true);
            ModelNode mapValue = item.get(MAP_ATTRIBUTE.getName());
            mapValue.get("key1").set("value1");
            mapValue.get("key2").set("value2");
        }

        op.get("value").set(value);
        executeCheckNoFailure(op);

        //test read-attribute(name=complex-attribute2.object-list[1].attr1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[3].attr1");
        Assert.assertEquals("value3", executeForResult(op).asString());

        //test :read-attribute(name=complex-attribute2.object-list[1].map-attribute.key1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[1].map-attribute.key1");
        Assert.assertEquals("value1", executeForResult(op).asString());


        //test :map-get(name=complex-attribute2.object-list[1].map-attribute key=key1)
        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[1].map-attribute");
        op.get("key").set("key1");
        Assert.assertEquals("value1", executeForResult(op).asString());


        //test :list-get(name=complex-attribute2.object-list index=1)
        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName());
        op.get("index").set(1);
        Assert.assertEquals(3, executeForResult(op).asList().size()); //there should be 3 attributes on this list element

        // test read non-existent element -- read-attribute(name=complex-attribute2.object-list[6])
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[6]");
        Assert.assertFalse(executeForResult(op).isDefined());

        // test write to non-existing element
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[6].attr1");
        op.get(VALUE).set("newvalue");
        executeCheckNoFailure(op);
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[6].attr1");
        Assert.assertEquals("newvalue", executeForResult(op).asString());

        // test add to undefined list
        op = createOperation("undefine-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName());
        executeCheckNoFailure(op);
        ModelNode item = new ModelNode();
        item.get(ATTR_1.getName()).set("newvalue");
        item.get(ATTR_2.getName()).set(true);
        ModelNode mapValue = item.get(MAP_ATTRIBUTE.getName());
        mapValue.get("key1").set("value1");
        mapValue.get("key2").set("value2");
        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName());
        op.get(VALUE).set(item);
        executeCheckNoFailure(op);
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[0].attr1");
        Assert.assertEquals("newvalue", executeForResult(op).asString());

        // test write to non-existing attribute
        op = createOperation("undefine-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName());
        executeCheckNoFailure(op);
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName());
        ModelNode list = new ModelNode();
        list.add(item);
        op.get("value").set(list);
        executeCheckNoFailure(op);

        // test add to undefined parent
        op = createOperation("undefine-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName());
        executeCheckNoFailure(op);
        item = new ModelNode();
        item.get(ATTR_1.getName()).set("newvalue");
        item.get(ATTR_2.getName()).set(true);
        mapValue = item.get(MAP_ATTRIBUTE.getName());
        mapValue.get("key1").set("value1");
        mapValue.get("key2").set("value2");
        op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName());
        op.get(VALUE).set(item);
        executeCheckForFailure(op);  // TODO perhaps this could be made to work and this line would be replaced by the following
//        executeCheckNoFailure(op);
//        op = createOperation("read-attribute", TEST_ADDRESS);
//        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[0].attr1");
//        Assert.assertEquals("newvalue", executeForResult(op).asString());
    }


    @Test
    public void testWriteAttribute() throws OperationFailedException {

        //test :write-attribute(name="map-attribute.map-key" value="map-value")
        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName() + ".map-key");
        op.get("value").set("map-value");
        executeCheckNoFailure(op);

        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        Assert.assertEquals("map-value", executeForResult(op).asString());


        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(MAP_ATTRIBUTE.getName() + ".map-key.sub-key"); //invalid and need to fail
        op.get("value").set("map-value");
        executeForFailure(op);

        //test list elements

        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName() + "[-1]");
        op.get("value").set("value1");
        executeCheckNoFailure(op);

        //add second value
        op.get("value").set("value2");
        executeCheckNoFailure(op);


        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName());
        ModelNode res = executeForResult(op);
        Assert.assertTrue(res.isDefined());
        // return value of list-attribute on index 0
        // :read-attribute(name=map-attribute.myKey)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(LIST_ATTRIBUTE.getName() + "[0]");
        Assert.assertEquals("value1", executeForResult(op).asString());

        op.get("name").set(LIST_ATTRIBUTE.getName() + "[1]");
        Assert.assertEquals("value2", executeForResult(op).asString());
    }

    @Test
    public void testComplexWrite() throws OperationFailedException {
         //test write-attribute(name="complex-attribute.attr1, value="attr1-string")
        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE.getName() + "." + ATTR_1.getName());
        op.get("value").set("attr1-string");
        executeCheckNoFailure(op);

        //test write-attribute(name="complex-attribute.attr2, value=true)
        op.get("name").set(COMPLEX_ATTRIBUTE.getName() + "." + ATTR_2.getName());
        op.get("value").set(true);
        executeCheckNoFailure(op);

        //test read-attribute(name=complex-attribute.attr1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE.getName() + "." + ATTR_1.getName());
        Assert.assertEquals("attr1-string", executeForResult(op).asString());
        //test read-attribute(name=complex-attribute.attr2)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE.getName() + "." + ATTR_2.getName());
        Assert.assertTrue(executeForResult(op).asBoolean());

        //test List<Object>
        op = createOperation("write-attribute", TEST_ADDRESS);
        for (int i = 0; i <= 5; i++) {
            op.get("name").set(OBJECT_LIST.getName() + "[-1].attr1"); // add new element
            op.get("value").set("value"+i);
            executeCheckNoFailure(op);
        }
        //test read-attribute(name=object-list[1])
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[1]");
        Assert.assertTrue(executeForResult(op).isDefined());

        //test read-attribute(name=object-list[1].attr1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[3].attr1");
        Assert.assertEquals("value3", executeForResult(op).asString());

        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[3].attr1"); // update element on index 3
        op.get("value").set("updated");
        executeCheckNoFailure(op);

        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[3].attr1");
        Assert.assertEquals("updated", executeForResult(op).asString());

        //complex attribute with list with complex attributes
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName());
        ModelNode value = new ModelNode();

        ModelNode listValue = value.get(OBJECT_LIST.getName());

        for (int i = 0; i <= 5; i++) {
            ModelNode item = listValue.add();
            item.get(ATTR_1.getName()).set("value" + i);
            item.get(ATTR_2.getName()).set(true);
            ModelNode mapValue = item.get(MAP_ATTRIBUTE.getName());
            mapValue.get("key1").set("value1");
            mapValue.get("key2").set("value2");
        }

        op.get("value").set(value);
        executeCheckNoFailure(op);

        //test read-attribute(name=complex-attribute2.object-list[1].attr1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[3].attr1");
        Assert.assertEquals("value3", executeForResult(op).asString());

        //test :read-attribute(name=complex-attribute2.object-list[1].map-attribute.key1)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[1].map-attribute.key1");
        Assert.assertEquals("value1", executeForResult(op).asString());

        //test :write-attribute(name=complex-attribute2.object-list[1].map-attribute.key1)
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[3].map-attribute.key1");
        op.get("value").set("updated-value");
        executeCheckNoFailure(op);

        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[3].map-attribute.key1");
        Assert.assertEquals("updated-value", executeForResult(op).asString());

        //test :write-attribute(name=complex-attribute2.object-list[-1].map-attribute.key1)
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[-1].map-attribute.key1");
        op.get("value").set("added-value");
        executeCheckNoFailure(op);


        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[6].map-attribute.key1");
        Assert.assertEquals("added-value", executeForResult(op).asString());

        //test :map-get(name=complex-attribute2.object-list[t].map-attribute key=key1)
        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[6].map-attribute");
        op.get("key").set("key1");
        Assert.assertEquals("added-value", executeForResult(op).asString());

        //test :list-get(name=complex-attribute2.object-list index=5)
        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName());
        op.get("index").set(1);
        Assert.assertEquals(3, executeForResult(op).asList().size()); //there should be 3 attributes on this list element


        //test :map-put(name=complex-attribute2.object-list[t].map-attribute key=map-put-key value="map-put-value")
        op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[6].map-attribute");
        op.get("key").set("map-put-key");
        op.get("value").set("map-put-value");
        executeCheckNoFailure(op);


        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(COMPLEX_ATTRIBUTE2.getName() + "." + OBJECT_LIST.getName() + "[6].map-attribute");
        op.get("key").set("map-put-key");
        Assert.assertEquals("map-put-value", executeForResult(op).asString());

    }

    @Test
    public void testNormalAttributeLookingExtended() throws Exception {
        final ModelNode wa = createOperation("write-attribute", TEST_ADDRESS);
        wa.get("name").set(NORMAL_LOOKING_EXTENDED.getName());
        wa.get("value").set("test123");
        executeCheckNoFailure(wa);

        final ModelNode ra = createOperation("read-attribute", TEST_ADDRESS);
        ra.get("name").set(NORMAL_LOOKING_EXTENDED.getName());
        Assert.assertEquals("test123", executeForResult(ra).asString());

        executeCheckNoFailure(createOperation("remove", TEST_ADDRESS));
        final ModelNode add = createOperation("add", TEST_ADDRESS);
        add.get(NORMAL_LOOKING_EXTENDED.getName()).set("test456");
        executeCheckNoFailure(add);

        Assert.assertEquals("test456", executeForResult(ra).asString());
    }

    @Test
    public void testComplexListValue()throws Exception{

        //test List<Object>
        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName());
        ModelNode value = new ModelNode();
        value.get(ATTR_1.getName()).set("complex value");
        value.get(ATTR_2.getName()).set(true);
        op.get("value").set(value);
        executeCheckNoFailure(op);

        ModelNode readOp = createOperation("read-attribute", TEST_ADDRESS);
        readOp.get("name").set(OBJECT_LIST.getName());
        ModelNode result = executeForResult(readOp);
        Assert.assertEquals(1, result.asList().size());

        executeCheckNoFailure(op);

        result = executeForResult(readOp);
        Assert.assertEquals(2, result.asList().size());
    }

    @Test
    public void testRuntimeMapAttributeRead() throws Exception {
        ModelNode op = createOperation("map-put", TEST_ADDRESS);
        op.get("name").set(RUNTIME_MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        op.get("value").set("map-value");
        executeCheckNoFailure(op);

        // :map-get(name=runtime-map-attribute, key=map-key)
        op = createOperation("map-get", TEST_ADDRESS);
        op.get("name").set(RUNTIME_MAP_ATTRIBUTE.getName());
        op.get("key").set("map-key");
        Assert.assertEquals("map-value", executeForResult(op).asString());

        // :read-attribute(name=runtime-map-attribute)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_MAP_ATTRIBUTE.getName());
        Assert.assertEquals("{\"map-key\" => \"map-value\"}", executeForResult(op).asString());

        // :read-attribute(name=runtime-map-attribute.map-key)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_MAP_ATTRIBUTE.getName() + ".map-key");
        Assert.assertEquals("map-value", executeForResult(op).asString());

        op.get("name").set(RUNTIME_MAP_ATTRIBUTE.getName() + ".wrong-key");
        executeForFailure(op);
    }

    @Test
    public void testRuntimeMapAttributeWrite() throws Exception {
        // :write-attribute(name=runtime-map-attribute.map-key)
        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_MAP_ATTRIBUTE.getName() + ".map-key");
        op.get("value").set("map-value");
        executeCheckNoFailure(op);

        Map<String, String> map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("map-value", map.get("map-key"));

        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_MAP_ATTRIBUTE.getName() + ".map-key2");
        op.get("value").set("map-value2");
        executeCheckNoFailure(op);

        map = PropertiesAttributeDefinition.unwrapModel(ExpressionResolver.TEST_RESOLVER, runtimeMapAttributeValue);
        Assert.assertEquals(2, map.size());
        Assert.assertEquals("map-value", map.get("map-key"));
        Assert.assertEquals("map-value2", map.get("map-key2"));
    }

    @Test
    public void testRuntimeListAttributeRead() throws Exception {
        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName());
        op.get("value").set("list-value");
        executeCheckNoFailure(op);

        op = createOperation("list-get", TEST_ADDRESS);
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName());
        op.get("index").set(0);
        Assert.assertEquals("list-value", executeForResult(op).asString());

        // :read-attribute(name=runtime-list-attribute)
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName());
        Assert.assertEquals("[\"list-value\"]", executeForResult(op).asString());

        // :read-attribute(name=runtime-list-attribute[0])
        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName() + "[0]");
        Assert.assertEquals("list-value", executeForResult(op).asString());

        // :read-attribute(name=runtime-list-attribute[1])
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName() + "[1]"); // index out of range
        Assert.assertEquals(ModelType.UNDEFINED, executeForResult(op).getType());
    }

    @Test
    public void testRuntimeListAttributeWrite() throws Exception {
        ModelNode op = createOperation("list-add", TEST_ADDRESS);
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName());
        op.get("value").set("list-value");
        executeCheckNoFailure(op);

        List<String> list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("list-value", list.get(0));

        // :write-attribute(name=runtime-list-attribute[-1])
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName() + "[-1]");
        op.get("value").set("list-value2");
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("list-value", list.get(0));
        Assert.assertEquals("list-value2", list.get(1));

        // :write-attribute(name=runtime-list-attribute[0])
        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(RUNTIME_LIST_ATTRIBUTE.getName() + "[0]");
        op.get("value").set("list-value3");
        executeCheckNoFailure(op);

        list = StringListAttributeDefinition.unwrapValue(ExpressionResolver.TEST_RESOLVER, runtimeListAttributeValue);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("list-value3", list.get(0));
        Assert.assertEquals("list-value2", list.get(1));
    }

    @Test
    public void testCompositeListWrite() throws Exception {
        performCompositeListWrite(LIST_ATTRIBUTE.getName());
        performCompositeListWrite(RUNTIME_LIST_ATTRIBUTE.getName());
    }

    private void performCompositeListWrite(String attrName) throws Exception {
        ModelNode compositeOp = createOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);

        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(attrName + "[-1]");
        op.get("value").set("list-value");
        compositeOp.get(STEPS).add(op);

        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(attrName + "[-1]");
        op.get("value").set("list-value2");
        compositeOp.get(STEPS).add(op);

        executeCheckNoFailure(compositeOp);

        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(attrName);
        ModelNode result = executeForResult(op);
        Assert.assertEquals(2, result.asList().size());
        Assert.assertEquals("list-value", result.asList().get(0).asString());
        Assert.assertEquals("list-value2", result.asList().get(1).asString());
    }

    @Test
    public void testCompositeMapWrite() throws Exception {
        performCompositeMapWrite(MAP_ATTRIBUTE.getName());
        performCompositeMapWrite(RUNTIME_MAP_ATTRIBUTE.getName());
    }

    private void performCompositeMapWrite(String attrName) throws Exception {
        ModelNode compositeOp = createOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);

        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(attrName + ".map-key");
        op.get("value").set("map-value");
        compositeOp.get(STEPS).add(op);

        op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(attrName + ".map-key2");
        op.get("value").set("map-value2");
        compositeOp.get(STEPS).add(op);

        executeCheckNoFailure(compositeOp);

        op = createOperation("read-attribute", TEST_ADDRESS);
        op.get("name").set(attrName);
        ModelNode result = executeForResult(op);
        Assert.assertEquals(2, result.keys().size());
        Assert.assertEquals("map-value", result.get("map-key").asString());
        Assert.assertEquals("map-value2", result.get("map-key2").asString());
    }

    @Test
    public void testBadIndex() throws Exception {
        //test set
        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[zero]");
        op.get("value").set(ModelNode.ZERO);
        ModelNode resp = executeCheckForFailure(op);
        Assert.assertTrue(resp.toString(), resp.toString().contains("WFLYCTL0393"));
    }

    @Test
    public void testInvalidDot() throws Exception {
        //test set
        ModelNode op = createOperation("write-attribute", TEST_ADDRESS);
        op.get("name").set(OBJECT_LIST.getName() + "[1.1]");
        op.get("value").set(ModelNode.ZERO);
        ModelNode resp = executeCheckForFailure(op);
        Assert.assertTrue(resp.toString(), resp.toString().contains("WFLYCTL0393"));
    }
}
