/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OrderedChildResourceTestCase extends AbstractControllerTestBase {

    private static final PathElement PARENT_MAIN = PathElement.pathElement("parent", "main");
    private static final PathElement CHILD = PathElement.pathElement("child");
    private static final AttributeDefinition ATTR = new SimpleAttributeDefinitionBuilder("attr", ModelType.STRING, true).build();
    private static final AttributeDefinition[] REQUEST_ATTRIBUTES = new AttributeDefinition[]{ATTR};


    @Test
    public void testOrderedChildrenDescription() throws Exception {

        //Check the non-ordered resource does not have the index property in the add operation
        ModelNode readOp = createOperation(READ_OPERATION_DESCRIPTION_OPERATION, PathAddress.pathAddress(PARENT_MAIN));
        readOp.get(NAME).set(ADD);
        ModelNode result = executeForResult(readOp);
        ModelNode reqProps = result.get(REQUEST_PROPERTIES);
        Assert.assertTrue(reqProps.hasDefined("attr"));
        Assert.assertFalse(reqProps.hasDefined("index"));

        //Check the ordered resource does have the index property in the add operation
        readOp.get(OP_ADDR).set(PathAddress.pathAddress(PARENT_MAIN, CHILD).toModelNode());
        result = executeForResult(readOp);
        reqProps = result.get(REQUEST_PROPERTIES);
        Assert.assertTrue(reqProps.hasDefined("attr"));
        Assert.assertTrue(reqProps.hasDefined(ADD_INDEX));

        //Check neither resource has the index as an attribute
        ModelNode rrd = createOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(PARENT_MAIN));
        rrd.get(RECURSIVE).set(true);
        result = executeForResult(rrd);
        ModelNode attributes = result.get(ATTRIBUTES);
        Assert.assertTrue(attributes.hasDefined("attr"));
        Assert.assertFalse(attributes.hasDefined(ADD_INDEX));
        attributes = result.get(CHILDREN, "child", MODEL_DESCRIPTION, "*", ATTRIBUTES);
        Assert.assertTrue(attributes.hasDefined("attr"));
        Assert.assertFalse(attributes.hasDefined("index"));
    }

    @Test
    public void testOrderedChildren() throws Exception {
        ModelNode add = Util.createAddOperation(PathAddress.pathAddress(PARENT_MAIN));
        executeForResult(add);
        assertChildren();

        executeAddChildOperation("tree", 0);
        assertChildren("tree");

        executeAddChildOperation("house", null);
        assertChildren("tree", "house");

        executeAddChildOperation("which", 0);
        assertChildren("which", "tree", "house");

        executeAddChildOperation("likes", 2);
        assertChildren("which", "tree", "likes", "house");

        executeAddChildOperation("mice", 1000000);
        assertChildren("which", "tree", "likes", "house", "mice");
    }

    private void executeAddChildOperation(String childName, Integer index) throws Exception {
        ModelNode add = Util.createAddOperation(PathAddress.pathAddress(PARENT_MAIN, PathElement.pathElement("child", childName)));
        if (index != null) {
            add.get(ADD_INDEX).set(index);
        }
        executeForResult(add);
    }

    private void assertChildren(String...children) throws Exception {
        ModelNode rr = createOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(PARENT_MAIN));
        rr.get(RECURSIVE).set(true);
        ModelNode result = executeForResult(rr);
        if (children.length == 0) {
            Assert.assertFalse(result.hasDefined(CHILD.getKey()));
        } else {
            Assert.assertTrue(result.hasDefined(CHILD.getKey()));
            ModelNode childNode = result.get(CHILD.getKey());
            String[] childNames = childNode.keys().toArray(new String[childNode.keys().size()]);
            Assert.assertArrayEquals(children, childNames);

        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);

        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        GlobalNotifications.registerGlobalNotifications(registration, processType);

        registration.registerSubModel(new ParentResourceDefinition());
    }

    private static class ParentResourceDefinition extends SimpleResourceDefinition {

        public ParentResourceDefinition() {
            super(PARENT_MAIN,
                    NonResolvingResourceDescriptionResolver.INSTANCE,
                    ModelOnlyAddStepHandler.INSTANCE,
                    ModelOnlyRemoveStepHandler.INSTANCE);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(ATTR, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new OrderedChildResourceDefinition());
        }
    }

    private static class OrderedChildResourceDefinition extends SimpleResourceDefinition {

        public OrderedChildResourceDefinition() {
            super(new Parameters(CHILD, NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(ModelOnlyAddStepHandler.INSTANCE)
                    .setRemoveHandler(ModelOnlyRemoveStepHandler.INSTANCE)
                    .setOrderedChild());
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(ATTR, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
    }
}
