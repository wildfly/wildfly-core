/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MIN_LENGTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NILLABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE_ADDED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE_REMOVED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.AttributeAccess.AccessType;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.dmr.ValueExpression;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractGlobalOperationsTestCase extends AbstractControllerTestBase {

    private final AccessType expectedRwAttributeAccess;

    protected AbstractGlobalOperationsTestCase() {
        super();
        this.expectedRwAttributeAccess = AccessType.READ_WRITE;
    }

    protected AbstractGlobalOperationsTestCase(ProcessType processType, AccessType expectedRwAttributeAccess) {
        super(processType);
        this.expectedRwAttributeAccess = expectedRwAttributeAccess;
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        rootRegistration.registerOperationHandler(TestUtils.SETUP_OPERATION_DEF, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        final ModelNode model = new ModelNode();
                        //Atttributes
                        model.get("profile", "profileA", "name").set("profileA");
                        model.get("profile", "profileA", "subsystem", "subsystem1", "attr1").add(1);
                        model.get("profile", "profileA", "subsystem", "subsystem1", "attr1").add(2);
                        //Children
                        model.get("profile", "profileA", "subsystem", "subsystem1", "type1", "thing1", "name").set("Name11");
                        model.get("profile", "profileA", "subsystem", "subsystem1", "type1", "thing1", "value").set("201");
                        model.get("profile", "profileA", "subsystem", "subsystem1", "type1", "thing2", "name").set("Name12");
                        model.get("profile", "profileA", "subsystem", "subsystem1", "type1", "thing2", "value").set("202");
                        model.get("profile", "profileA", "subsystem", "subsystem1", "type2", "other", "name").set("Name2");


                        model.get("profile", "profileA", "subsystem", "subsystem2", "bigdecimal").set(new BigDecimal(100));
                        model.get("profile", "profileA", "subsystem", "subsystem2", "biginteger").set(new BigInteger("101"));
                        model.get("profile", "profileA", "subsystem", "subsystem2", "boolean").set(true);
                        model.get("profile", "profileA", "subsystem", "subsystem2", "bytes").set(new byte[]{1, 2, 3});
                        model.get("profile", "profileA", "subsystem", "subsystem2", "double").set(Double.MAX_VALUE);
                        model.get("profile", "profileA", "subsystem", "subsystem2", "expression").set(new ValueExpression("${expr}"));
                        model.get("profile", "profileA", "subsystem", "subsystem2", "int").set(102);
                        model.get("profile", "profileA", "subsystem", "subsystem2", "list").add("l1A");
                        model.get("profile", "profileA", "subsystem", "subsystem2", "list").add("l1B");
                        model.get("profile", "profileA", "subsystem", "subsystem2", "long").set(Long.MAX_VALUE);
                        model.get("profile", "profileA", "subsystem", "subsystem2", "object", "value").set("objVal");
                        model.get("profile", "profileA", "subsystem", "subsystem2", "property").set(new Property("prop1", new ModelNode().set("value1")));
                        model.get("profile", "profileA", "subsystem", "subsystem2", "string1").set("s1");
                        model.get("profile", "profileA", "subsystem", "subsystem2", "string2").set("s2");
                        model.get("profile", "profileA", "subsystem", "subsystem2", "type").set(ModelType.TYPE);


                        model.get("profile", "profileB", "name").set("Profile B");

                        model.get("profile", "profileC", "name").set("profileC");
                        model.get("profile", "profileC", "subsystem", "subsystem4");
                        model.get("profile", "profileC", "subsystem", "subsystem5", "name").set("Test");

                        createModel(context, model);
                    }
                }
        );

        ResourceDefinition profileDef = ResourceBuilder.Factory.create(PathElement.pathElement("profile", "*"),
                NonResolvingResourceDescriptionResolver.INSTANCE)
                .addReadOnlyAttribute(SimpleAttributeDefinitionBuilder.create("name", ModelType.STRING, false).setMinSize(1).build())
                .build();


        ManagementResourceRegistration profileReg = rootRegistration.registerSubModel(profileDef);

        ManagementResourceRegistration profileSub1Reg = profileReg.registerSubModel(new Subsystem1RootResource());

        ManagementResourceRegistration profileASub2Reg = profileReg.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("subsystem", "subsystem2"), NonResolvingResourceDescriptionResolver.INSTANCE));
        AttributeDefinition longAttr = TestUtils.createAttribute("long", ModelType.LONG, "number");
        profileASub2Reg.registerReadWriteAttribute(longAttr, null, new ModelOnlyWriteAttributeHandler(longAttr));
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("type", ModelType.TYPE), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("string1", ModelType.STRING), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("string2", ModelType.STRING), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("property", ModelType.STRING), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("object", ModelType.OBJECT), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("list", ModelType.LIST), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("int", ModelType.INT), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("expression", ModelType.STRING), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("double", ModelType.DOUBLE), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("bytes", ModelType.BYTES), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("bigdecimal", ModelType.BIG_DECIMAL), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("biginteger", ModelType.BIG_INTEGER), null);
        profileASub2Reg.registerReadOnlyAttribute(TestUtils.createAttribute("boolean", ModelType.BOOLEAN), null);


        AttributeDefinition att1 = TestUtils.createAttribute("param1", ModelType.STRING);
        AttributeDefinition att2 = TestUtils.createAttribute("param2", ModelType.STRING);
        AttributeDefinition ref = SimpleAttributeDefinitionBuilder.create("test_capability", ModelType.STRING)
                .setCapabilityReference("org.wildfly.test.capability", att1, att2)
                .build();
        ManagementResourceRegistration profileBSub3Reg = profileReg.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("subsystem", "subsystem3"), NonResolvingResourceDescriptionResolver.INSTANCE));
        profileBSub3Reg.registerReadOnlyAttribute(att1, null);
        profileBSub3Reg.registerReadOnlyAttribute(att2, null);
        profileBSub3Reg.registerReadOnlyAttribute(ref, null);
        profileSub1Reg.registerOperationHandler(TestUtils.createOperationDefinition("testA1-1", TestUtils.createAttribute("paramA1", ModelType.INT)),
                new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) {
                    }
                }
        );

        profileSub1Reg.registerOperationHandler(TestUtils.createOperationDefinition("testA1-2", TestUtils.createAttribute("paramA2", ModelType.STRING)),
                new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) {
                    }
                }
        );


        profileASub2Reg.registerOperationHandler(TestUtils.createOperationDefinition("testA2", TestUtils.createAttribute("paramB", ModelType.LONG)),
                new OperationStepHandler() {

                    @Override
                    public void execute(OperationContext context, ModelNode operation) {
                    }
                }
        );

        AttributeDefinition simpleRef = SimpleAttributeDefinitionBuilder.create("simple_ref", ModelType.STRING)
                .setCapabilityReference("org.wildfly.test.capability")
                .build();
        ManagementResourceRegistration profileCSub4Reg = profileReg.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("subsystem", "subsystem4"), NonResolvingResourceDescriptionResolver.INSTANCE));
        profileCSub4Reg.registerReadOnlyAttribute(simpleRef, null);

        ManagementResourceRegistration profileCSub5Reg = profileReg.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("subsystem", "subsystem5"), NonResolvingResourceDescriptionResolver.INSTANCE));
        profileCSub5Reg.registerReadOnlyAttribute(TestUtils.createAttribute("name", ModelType.STRING, "varchar"), new OperationStepHandler() {

            @Override
            public void execute(OperationContext context, ModelNode operation) {
                context.getResult().set("Overridden by special read handler");
            }
        });
        profileCSub5Reg.registerRequirements(Collections.singleton(
                        new CapabilityReferenceRecorder.ResourceCapabilityReferenceRecorder(
                                address-> new String[]{address.getLastElement().getKey()},
                                "org.wildfly.test.capability.dep",
                                address-> new String[]{address.getParent().getLastElement().getValue(), address.getLastElement().getValue()},
                                "org.wildfly.test.capability.req")));

        ResourceDefinition profileCSub5Type1RegDef = ResourceBuilder.Factory.create(PathElement.pathElement("type1"),
                NonResolvingResourceDescriptionResolver.INSTANCE)
                .build();

        ManagementResourceRegistration profileCSub5Type1Reg = profileCSub5Reg.registerSubModel(profileCSub5Type1RegDef);

        ManagementResourceRegistration profileCSub6Reg = profileReg.registerSubModel(
                new SimpleResourceDefinition(PathElement.pathElement("subsystem", "subsystem6"), NonResolvingResourceDescriptionResolver.INSTANCE));

        profileCSub6Reg.registerOperationHandler(TestUtils.createOperationDefinition("testA", true),
                new OperationStepHandler() {

                    @Override
                    public void execute(OperationContext context, ModelNode operation) {
                    }
                }
        );
    }

    /**
     * Override to get the actual result from the response.
     * @param operation
     * @return
     * @throws OperationFailedException
     */
    @Override
    public ModelNode executeForResult(ModelNode operation) throws OperationFailedException {
        ModelNode rsp = getController().execute(operation, null, null, null);
        assertNoUndefinedRolledBackNode(rsp);
        if (FAILED.equals(rsp.get(OUTCOME).asString())) {
            ModelNode fd = rsp.get(FAILURE_DESCRIPTION);
            throw new OperationFailedException(fd.toString(), fd);
        }
        return rsp.get(RESULT);
    }

    static class TestMetricHandler implements OperationStepHandler {
        static final TestMetricHandler INSTANCE = new TestMetricHandler();
        private static final Random random = new Random();

        @Override
        public void execute(final OperationContext context, final ModelNode operation) {
            context.getResult().set(random.nextInt());
        }

    }

    protected void checkRootNodeDescription(ModelNode result, boolean recursive, boolean operations, boolean notifications) {
        assertEquals("description", result.require(DESCRIPTION).asString());
        assertEquals("profile", result.require(CHILDREN).require(PROFILE).require(DESCRIPTION).asString());

        if (operations) {
            assertTrue(result.require(OPERATIONS).isDefined());
            Set<String> ops = result.require(OPERATIONS).keys();
            assertTrue(ops.contains(READ_ATTRIBUTE_OPERATION));
            assertTrue(ops.contains(READ_CHILDREN_NAMES_OPERATION));
            assertTrue(ops.contains(READ_CHILDREN_TYPES_OPERATION));
            assertTrue(ops.contains(READ_OPERATION_DESCRIPTION_OPERATION));
            assertTrue(ops.contains(READ_OPERATION_NAMES_OPERATION));
            assertTrue(ops.contains(READ_RESOURCE_DESCRIPTION_OPERATION));
            assertTrue(ops.contains(READ_RESOURCE_OPERATION));
            assertEquals(processType != ProcessType.DOMAIN_SERVER, ops.contains(WRITE_ATTRIBUTE_OPERATION));
            for (String op : ops) {
                assertEquals(op, result.require(OPERATIONS).require(op).require(OPERATION_NAME).asString());
            }
        } else {
            assertFalse(result.get(OPERATIONS).isDefined());
        }

        if (!recursive) {
            assertFalse(result.require(CHILDREN).require(PROFILE).require(MODEL_DESCRIPTION).isDefined());
            return;
        }
        assertTrue(result.require(CHILDREN).require(PROFILE).require(MODEL_DESCRIPTION).isDefined());
        assertEquals(1, result.require(CHILDREN).require(PROFILE).require(MODEL_DESCRIPTION).keys().size());
        checkProfileNodeDescription(result.require(CHILDREN).require(PROFILE).require(MODEL_DESCRIPTION).require("*"), true, operations, notifications);

    }

    protected void checkProfileNodeDescription(ModelNode result, boolean recursive, boolean operations, boolean notifications) {
        assertEquals(ModelType.STRING, result.require(ATTRIBUTES).require(NAME).require(TYPE).asType());
        assertEquals(false, result.require(ATTRIBUTES).require(NAME).require(NILLABLE).asBoolean());
        assertEquals(1, result.require(ATTRIBUTES).require(NAME).require(MIN_LENGTH).asInt());
        assertEquals("subsystem", result.require(CHILDREN).require(SUBSYSTEM).require(DESCRIPTION).asString());
        if (!recursive) {
            assertFalse(result.require(CHILDREN).require(SUBSYSTEM).require(MODEL_DESCRIPTION).isDefined());
            return;
        }
        assertTrue(result.require(CHILDREN).require(SUBSYSTEM).require(MODEL_DESCRIPTION).isDefined());
        assertEquals(getExpectedNumberProfiles(), result.require(CHILDREN).require(SUBSYSTEM).require(MODEL_DESCRIPTION).keys().size());
        checkSubsystem1Description(result.require(CHILDREN).require(SUBSYSTEM).require(MODEL_DESCRIPTION).require("subsystem1"), recursive, operations, notifications);
    }

    protected int getExpectedNumberProfiles() {
        //Some tests might add more, if so they should override this method
        return 6;
    }

    protected void checkSubsystem1Description(ModelNode result, boolean recursive, boolean operations, boolean notifications) {
        assertNotNull(result);

        assertEquals(ModelType.LIST, result.require(ATTRIBUTES).require("attr1").require(TYPE).asType());
        assertEquals(ModelType.INT, result.require(ATTRIBUTES).require("attr1").require(VALUE_TYPE).asType());
        assertFalse(result.require(ATTRIBUTES).require("attr1").require(NILLABLE).asBoolean());
        assertEquals(AccessType.READ_ONLY.toString(), result.require(ATTRIBUTES).require("attr1").get(ACCESS_TYPE).asString());
        assertEquals(ModelType.INT, result.require(ATTRIBUTES).require("read-only").require(TYPE).asType());
        assertTrue(result.require(ATTRIBUTES).require("read-only").require(NILLABLE).asBoolean());
        assertEquals(AccessType.READ_ONLY.toString(), result.require(ATTRIBUTES).require("read-only").get(ACCESS_TYPE).asString());
        assertEquals(ModelType.INT, result.require(ATTRIBUTES).require("metric1").require(TYPE).asType());
        assertEquals(AccessType.METRIC.toString(), result.require(ATTRIBUTES).require("metric1").get(ACCESS_TYPE).asString());
        assertEquals(AccessType.METRIC.toString(), result.require(ATTRIBUTES).require("metric2").get(ACCESS_TYPE).asString());
        assertEquals(ModelType.INT, result.require(ATTRIBUTES).require("read-write").require(TYPE).asType());
        assertTrue(result.require(ATTRIBUTES).require("read-write").require(NILLABLE).asBoolean());
        assertEquals(expectedRwAttributeAccess.toString(), result.require(ATTRIBUTES).require("read-write").get(ACCESS_TYPE).asString());

        //we don't have proper support for this!
        /*assertEquals(1, result.require(CHILDREN).require("type1").require(MIN_OCCURS).asInt());
        assertEquals(1, result.require(CHILDREN).require("type2").require(MIN_OCCURS).asInt());
        assertEquals(1, result.require(CHILDREN).require("type2").require(MIN_OCCURS).asInt());*/

        assertEquals("type1", result.require(CHILDREN).require("type1").require(DESCRIPTION).asString());
        assertEquals("type2", result.require(CHILDREN).require("type2").require(DESCRIPTION).asString());


        if (operations) {
            assertTrue(result.require(OPERATIONS).isDefined());
            Set<String> ops = result.require(OPERATIONS).keys();
            assertEquals(processType == ProcessType.DOMAIN_SERVER ? 13 : 23, ops.size());
            boolean runtimeOnly = processType != ProcessType.DOMAIN_SERVER;
            assertEquals(runtimeOnly, ops.contains("testA1-1"));
            assertEquals(runtimeOnly, ops.contains("testA1-2"));
            assertGlobalOperations(ops);

        } else {
            assertFalse(result.get(OPERATIONS).isDefined());
        }

        if (notifications) {
            assertTrue(result.require(NOTIFICATIONS).isDefined());
            Set<String> notifs = result.require(NOTIFICATIONS).keys();
            assertEquals(processType == ProcessType.DOMAIN_SERVER ? 2 : 3, notifs.size());
            boolean runtimeOnly = processType != ProcessType.DOMAIN_SERVER;
            assertTrue(notifs.contains(RESOURCE_ADDED_NOTIFICATION));
            assertTrue(notifs.contains(RESOURCE_REMOVED_NOTIFICATION));
            assertEquals(runtimeOnly, notifs.contains(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION));
        } else {
            assertFalse(result.get(NOTIFICATIONS).isDefined());
        }

        if (!recursive) {
            assertFalse(result.require(CHILDREN).require("type1").require(MODEL_DESCRIPTION).isDefined());
            assertFalse(result.require(CHILDREN).require("type2").require(MODEL_DESCRIPTION).isDefined());
            return;
        }

        checkType1Description(result.require(CHILDREN).require("type1").require(MODEL_DESCRIPTION).require("*"));
        checkType2Description(result.require(CHILDREN).require("type2").require(MODEL_DESCRIPTION).require("other"));
    }

    private void assertGlobalOperations(Set<String> ops) {
        assertTrue(ops.contains(READ_RESOURCE_OPERATION));
        assertTrue(ops.contains(READ_ATTRIBUTE_OPERATION));
        assertTrue(ops.contains(READ_ATTRIBUTE_GROUP_OPERATION));
        assertTrue(ops.contains(READ_ATTRIBUTE_GROUP_NAMES_OPERATION));
        assertTrue(ops.contains(READ_RESOURCE_DESCRIPTION_OPERATION));
        assertTrue(ops.contains(READ_CHILDREN_NAMES_OPERATION));
        assertTrue(ops.contains(READ_CHILDREN_TYPES_OPERATION));
        assertTrue(ops.contains(READ_CHILDREN_RESOURCES_OPERATION));
        assertTrue(ops.contains(READ_OPERATION_NAMES_OPERATION));
        assertTrue(ops.contains(READ_OPERATION_DESCRIPTION_OPERATION));
        assertTrue(ops.contains("list-get"));
        assertTrue(ops.contains("map-get"));
        if (processType == ProcessType.DOMAIN_SERVER) {
            assertFalse(ops.contains(WRITE_ATTRIBUTE_OPERATION));
            assertFalse(ops.contains("list-add"));
            assertFalse(ops.contains("list-remove"));
            assertFalse(ops.contains("list-clear"));
            assertFalse(ops.contains("map-put"));
            assertFalse(ops.contains("map-remove"));
            assertFalse(ops.contains("map-clear"));
        } else {
            assertTrue(ops.contains(WRITE_ATTRIBUTE_OPERATION));
            assertTrue(ops.contains("list-add"));
            assertTrue(ops.contains("list-remove"));
            assertTrue(ops.contains("list-clear"));
            assertTrue(ops.contains("map-put"));
            assertTrue(ops.contains("map-remove"));
            assertTrue(ops.contains("map-clear"));
        }
    }

    protected void checkType1Description(ModelNode result) {
        assertNotNull(result);
        assertEquals(ModelType.STRING, result.require(ATTRIBUTES).require("name").require(TYPE).asType());
        assertEquals("name", result.require(ATTRIBUTES).require("name").require(DESCRIPTION).asString());
        assertFalse(result.require(ATTRIBUTES).require("name").require(NILLABLE).asBoolean());
        assertEquals(ModelType.INT, result.require(ATTRIBUTES).require("value").require(TYPE).asType());
        assertEquals("value", result.require(ATTRIBUTES).require("value").require(DESCRIPTION).asString());
        assertFalse(result.require(ATTRIBUTES).require("value").require(NILLABLE).asBoolean());
        //TODO should the inherited ops be picked up?
        if (result.hasDefined(OPERATIONS)) {
            assertTrue(result.require(OPERATIONS).isDefined());
            Set<String> ops = result.require(OPERATIONS).keys();
            assertEquals(processType == ProcessType.DOMAIN_SERVER ? 13 : 21, ops.size());
            assertGlobalOperations(ops);
        }

        if (result.hasDefined(NOTIFICATIONS)) {
            assertTrue(result.require(NOTIFICATIONS).isDefined());
            Set<String> notifs = result.require(NOTIFICATIONS).keys();
            assertEquals(processType == ProcessType.DOMAIN_SERVER ? 2 : 3, notifs.size());
            assertTrue(notifs.contains(RESOURCE_ADDED_NOTIFICATION));
            assertTrue(notifs.contains(RESOURCE_REMOVED_NOTIFICATION));
            assertEquals(processType != ProcessType.DOMAIN_SERVER, notifs.contains(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION));
            for (String notif : notifs) {
                assertEquals(notif, result.require(NOTIFICATIONS).require(notif).require(NOTIFICATION_TYPE).asString());
            }
        }
    }

    protected void checkType2Description(ModelNode result) {
        assertNotNull(result);
        assertEquals("description", result.require(DESCRIPTION).asString());
        assertEquals(ModelType.STRING, result.require(ATTRIBUTES).require("name").require(TYPE).asType());
        assertEquals("name", result.require(ATTRIBUTES).require("name").require(DESCRIPTION).asString());
        assertFalse(result.require(ATTRIBUTES).require("name").require(NILLABLE).asBoolean());
        if (result.hasDefined(OPERATIONS)) {
            assertTrue(result.require(OPERATIONS).isDefined());
            Set<String> ops = result.require(OPERATIONS).keys();
            assertEquals(processType == ProcessType.DOMAIN_SERVER ? 13 : 21, ops.size());
            assertGlobalOperations(ops);
        }

        if (result.hasDefined(NOTIFICATIONS)) {
            assertTrue(result.require(NOTIFICATIONS).isDefined());
            Set<String> notifs = result.require(NOTIFICATIONS).keys();
            assertEquals(processType == ProcessType.DOMAIN_SERVER ? 2 : 3, notifs.size());
            assertTrue(notifs.contains(RESOURCE_ADDED_NOTIFICATION));
            assertTrue(notifs.contains(RESOURCE_REMOVED_NOTIFICATION));
            assertEquals(processType != ProcessType.DOMAIN_SERVER, notifs.contains(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION));
        }
    }

    protected ModelNode createOperation(String operationName, String... address) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(operationName);
        if (address.length > 0) {
            for (String addr : address) {
                operation.get(OP_ADDR).add(addr);
            }
        } else {
            operation.get(OP_ADDR).setEmptyList();
        }

        return operation;
    }

    protected List<String> modelNodeListToStringList(List<ModelNode> nodes) {
        List<String> result = new ArrayList<String>();
        for (ModelNode node : nodes) {
            result.add(node.asString());
        }
        return result;
    }

}
