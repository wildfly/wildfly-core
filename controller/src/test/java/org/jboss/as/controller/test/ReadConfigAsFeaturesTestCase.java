/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NESTED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONFIG_AS_FEATURES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PARAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SPEC;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.junit.Assert.assertEquals;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.global.ReadConfigAsFeaturesOperationHandler;
import org.jboss.as.controller.operations.global.ReadFeatureDescriptionHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ReadConfigAsFeaturesTestCase extends AbstractControllerTestBase {

    private static final String NON_FEATURE = "non-feature";
    private static final String RESOURCE = "resource";
    private static final String RT_RESOURCE = "rt-resource";
    private static final String TEST = "test";

    private static final String ATTR_HOST = "host";
    private static final String ATTR_PROFILE = "profile";
    private static final String ATTR_STR = "attr-str";
    private static final String ATTR_RT_ONLY = "attr-rt-only";
    private static final String ATTR_LIST_STR = "attr-list-str";
    private static final String ATTR_LIST_OBJ = "attr-list-obj";
    private static final String ATTR_OBJ = "attr-obj";
    private static final String ATTR_OBJ_REQUIRED = "attr-obj-required";

    private static final AttributeDefinition ATTR_HOST_DEF = TestUtils.createAttribute(ATTR_HOST, ModelType.STRING);
    private static final AttributeDefinition ATTR_PROFILE_DEF = TestUtils.createAttribute(ATTR_PROFILE, ModelType.STRING);
    private static final AttributeDefinition ATTR_STR_DEF = TestUtils.createAttribute(ATTR_STR, ModelType.STRING);
    private static final AttributeDefinition ATTR_RT_ONLY_DEF = TestUtils.createAttribute(ATTR_RT_ONLY, ModelType.STRING, true);
    private static final PrimitiveListAttributeDefinition ATTR_LIST_STR_DEF = new PrimitiveListAttributeDefinition.Builder(ATTR_LIST_STR, ModelType.STRING).build();
    private static final ObjectTypeAttributeDefinition ATTR_OBJ_DEF = new ObjectTypeAttributeDefinition.Builder(ATTR_OBJ, ATTR_STR_DEF, ATTR_LIST_STR_DEF).build();
    private static final ObjectListAttributeDefinition ATTR_LIST_OBJ_DEF = new ObjectListAttributeDefinition.Builder(ATTR_LIST_OBJ, ATTR_OBJ_DEF).build();
    private static final ObjectTypeAttributeDefinition ATTR_OBJ_REQUIRED_DEF = new ObjectTypeAttributeDefinition.Builder(ATTR_OBJ_REQUIRED, ATTR_STR_DEF, ATTR_LIST_STR_DEF)
            .setRequired(true)
            .build();
    private static final AttributeDefinition ATTR_SUBSYSTEM_DEF = TestUtils.createAttribute(SUBSYSTEM, ModelType.STRING);

    private static final OperationStepHandler WRITE_HANDLER = new ModelOnlyWriteAttributeHandler();

    private ManagementResourceRegistration registration;

    @Override
    public void setupController() throws InterruptedException {
        super.setupController();
        // register read-feature op
        // can't do this in #initModel() because `capabilityRegistry` is not available at that stage
        registration.registerOperationHandler(ReadFeatureDescriptionHandler.DEFINITION,
                ReadFeatureDescriptionHandler.getInstance(capabilityRegistry), true);
    }

    @Test
    public void testNested() throws Exception {
/*
        ModelNode op = new ModelNode();
        op.get("operation").set("read-feature-description");
        op.get("recursive").set(true);
        op.get("address").setEmptyList().add(SUBSYSTEM, TEST);
        System.out.println(executeForResult(op));
*/
        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set(READ_CONFIG_AS_FEATURES_OPERATION);
        final ModelNode subsystemFeature = getSubsystemFeature();
        final ModelNode children = subsystemFeature.get("children");
        children.add(getAttrObjFeature(true));
        children.add(getTestResourceFeature(true));
        final ModelNode rootFeature = getRootFeature();
        rootFeature.get("children").add(subsystemFeature);

        assertEquals(new ModelNode().add(rootFeature), executeForResult(operation));
    }

    @Test
    public void testNestedFalse() throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set(READ_CONFIG_AS_FEATURES_OPERATION);
        operation.get(NESTED).set(false);
        assertEquals(new ModelNode().add(getRootFeature()).add(getSubsystemFeature()).add(getAttrObjFeature(false)).add(getTestResourceFeature(false)), executeForResult(operation));
    }

    private static ModelNode getRootFeature() {
        final ModelNode result = new ModelNode();
        result.get(SPEC).set("server-root");
        result.get("id");
        return result;
    }

    private static ModelNode getSubsystemFeature() {
        final ModelNode result = new ModelNode();
        result.get(SPEC).set(SUBSYSTEM + "." + TEST);
        result.get("id").get(SUBSYSTEM).set(TEST);

        final ModelNode params = result.get(PARAMS);
        params.get(ATTR_STR).set("one");
        params.get("host-feature").set("test-host");
        params.get("profile-feature").set("test-profile");
        params.get(ATTR_LIST_STR).set(new ModelNode().add("a").add("b").add("c"));

        final ModelNode obj = new ModelNode();
        obj.get(ATTR_STR).set("1");
        obj.get(ATTR_LIST_STR).set(new ModelNode().add("d").add("e"));

        params.get(ATTR_LIST_OBJ).add(obj);
        params.get(ATTR_OBJ_REQUIRED).set(obj);

        return result;
    }

    private static ModelNode getAttrObjFeature(boolean nested) {
        final ModelNode result = new ModelNode();
        result.get(SPEC).set(SUBSYSTEM + "." + TEST + "." + ATTR_OBJ);
        if(!nested) {
            result.get("id").get(SUBSYSTEM).set(TEST);
        }
        final ModelNode params = result.get(PARAMS);
        params.get(ATTR_STR).set("1");
        params.get(ATTR_LIST_STR).set(new ModelNode().add("d").add("e"));
        return result;
    }

    private static ModelNode getTestResourceFeature(boolean nested) {
        final ModelNode result = new ModelNode();
        result.get(SPEC).set(SUBSYSTEM + "." + TEST + "." + RESOURCE);
        ModelNode id = result.get("id");
        if(!nested) {
            id.get(SUBSYSTEM).set(TEST);
        }
        id.get(RESOURCE).set(TEST);

        final ModelNode params = result.get(PARAMS);
        params.get(ATTR_STR).set("one");
        params.get(ATTR_LIST_STR).set(new ModelNode().add("d").add("e"));
        params.get(SUBSYSTEM + "-feature").set("one");
        return result;
    }

    @Override
    protected void initModel(ManagementModel managementModel) {

        final ModelNode model = new ModelNode();
        model.get(SUBSYSTEM, TEST, ATTR_HOST).set("test-host");
        model.get(SUBSYSTEM, TEST, ATTR_PROFILE).set("test-profile");
        model.get(SUBSYSTEM, TEST, ATTR_STR).set("one");
        model.get(SUBSYSTEM, TEST, ATTR_RT_ONLY).set("value");

        model.get(SUBSYSTEM, TEST, ATTR_LIST_STR).set(new ModelNode().add("a").add("b").add("c"));

        ModelNode value = new ModelNode();
        value.get(ATTR_STR).set("1");
        value.get(ATTR_LIST_STR).add("d").add("e");

        model.get(SUBSYSTEM, TEST, ATTR_OBJ).set(value);
        model.get(SUBSYSTEM, TEST, ATTR_OBJ_REQUIRED).set(value);

        model.get(SUBSYSTEM, TEST, ATTR_LIST_OBJ).set(new ModelNode().add(value));

        model.get(SUBSYSTEM, TEST, RESOURCE, TEST, ATTR_STR).set("one");
        model.get(SUBSYSTEM, TEST, RESOURCE, TEST, ATTR_RT_ONLY).set("value");
        model.get(SUBSYSTEM, TEST, RESOURCE, TEST, ATTR_LIST_STR).set(new ModelNode().add("d").add("e"));
        model.get(SUBSYSTEM, TEST, RESOURCE, TEST, SUBSYSTEM).set("one");

        model.get(SUBSYSTEM, TEST, RT_RESOURCE, TEST, ATTR_STR).set("one");
        model.get(SUBSYSTEM, TEST, NON_FEATURE, TEST, ATTR_STR).set("one");

        final ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        this.registration = registration;
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder("setup", NonResolvingResourceDescriptionResolver.INSTANCE)
                .setPrivateEntry()
                .build()
                , new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                createModel(context, model);
            }
        });


        GlobalNotifications.registerGlobalNotifications(registration, processType);

        registration.registerOperationHandler(ReadConfigAsFeaturesOperationHandler.DEFINITION, new ReadConfigAsFeaturesOperationHandler(), true);

        final ManagementResourceRegistration subsystem =
                registration.registerSubModel(new SimpleResourceDefinition(PathElement.pathElement(SUBSYSTEM, TEST),
                                NonResolvingResourceDescriptionResolver.INSTANCE));
        subsystem.registerReadWriteAttribute(ATTR_HOST_DEF, null, WRITE_HANDLER);
        subsystem.registerReadWriteAttribute(ATTR_PROFILE_DEF, null, WRITE_HANDLER);
        subsystem.registerReadWriteAttribute(ATTR_STR_DEF, null, WRITE_HANDLER);
        subsystem.registerReadWriteAttribute(ATTR_RT_ONLY_DEF, null, WRITE_HANDLER);
        subsystem.registerReadWriteAttribute(ATTR_LIST_STR_DEF, null, WRITE_HANDLER);
        subsystem.registerReadWriteAttribute(ATTR_OBJ_DEF, null, WRITE_HANDLER);
        subsystem.registerReadWriteAttribute(ATTR_OBJ_REQUIRED_DEF, null, WRITE_HANDLER);
        subsystem.registerReadWriteAttribute(ATTR_LIST_OBJ_DEF, null, WRITE_HANDLER);

        AttributeDefinition[] attrDefs = new AttributeDefinition[] {ATTR_HOST_DEF, ATTR_PROFILE_DEF, ATTR_STR_DEF, ATTR_RT_ONLY_DEF, ATTR_LIST_STR_DEF, ATTR_OBJ_DEF, ATTR_OBJ_REQUIRED_DEF, ATTR_LIST_OBJ_DEF};
        subsystem.registerOperationHandler(TestUtils.createOperationDefinition("add", attrDefs), new ModelOnlyAddStepHandler(attrDefs));

        final ManagementResourceRegistration resource = subsystem.registerSubModel(new SimpleResourceDefinition(PathElement.pathElement(RESOURCE, "*"),
                NonResolvingResourceDescriptionResolver.INSTANCE));
        resource.registerReadWriteAttribute(ATTR_STR_DEF, null, WRITE_HANDLER);
        resource.registerReadWriteAttribute(ATTR_RT_ONLY_DEF, null, WRITE_HANDLER);
        resource.registerReadWriteAttribute(ATTR_LIST_STR_DEF, null, WRITE_HANDLER);
        resource.registerReadWriteAttribute(ATTR_SUBSYSTEM_DEF, null, WRITE_HANDLER);
        attrDefs = new AttributeDefinition[] {ATTR_STR_DEF, ATTR_RT_ONLY_DEF, ATTR_LIST_STR_DEF, ATTR_SUBSYSTEM_DEF};
        resource.registerOperationHandler(TestUtils.createOperationDefinition("add", attrDefs), new ModelOnlyAddStepHandler(attrDefs));

        final ManagementResourceRegistration rt = subsystem.registerSubModel(new SimpleResourceDefinition(
                new SimpleResourceDefinition.Parameters(PathElement.pathElement(RT_RESOURCE, TEST),
                        NonResolvingResourceDescriptionResolver.INSTANCE)
                        .setRuntime()));
        rt.registerReadWriteAttribute(ATTR_STR_DEF, null, WRITE_HANDLER);
        rt.registerOperationHandler(TestUtils.createOperationDefinition("add", new AttributeDefinition[] {ATTR_STR_DEF}), new ModelOnlyAddStepHandler(attrDefs));

        final ManagementResourceRegistration nonFeature = subsystem.registerSubModel(new SimpleResourceDefinition(
                new SimpleResourceDefinition.Parameters(PathElement.pathElement(NON_FEATURE, TEST),
                        NonResolvingResourceDescriptionResolver.INSTANCE)
                        .setFeature(false)));
        nonFeature.registerReadWriteAttribute(ATTR_STR_DEF, null, WRITE_HANDLER);
        nonFeature.registerOperationHandler(TestUtils.createOperationDefinition("add", new AttributeDefinition[] {ATTR_STR_DEF}), new ModelOnlyAddStepHandler(attrDefs));
    }
}
