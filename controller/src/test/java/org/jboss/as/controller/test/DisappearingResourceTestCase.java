/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
 */

package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Before;
import org.junit.Test;

/**
 * WFCORE-573 -- tests of handling a resource that disappears in the middle of executing a read.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class DisappearingResourceTestCase extends AbstractControllerTestBase {

    private static final PathElement SUBSYSTEM_ELEMENT = PathElement.pathElement(SUBSYSTEM, "mysubsystem");
    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(SUBSYSTEM_ELEMENT);

    private static final String PARENT = "parent";
    private static final PathElement PARENT_ELEMENT = PathElement.pathElement(PARENT, "A");
    private static final PathAddress PARENT_ADDRESS = PathAddress.pathAddress(SUBSYSTEM_ELEMENT, PARENT_ELEMENT);

    private static final String CHILD = "child";
    private static final PathElement CHILD_B_ELEMENT = PathElement.pathElement(CHILD, "B");
    private static final PathElement CHILD_C_ELEMENT = PathElement.pathElement(CHILD, "C");
    private static final PathElement CHILD_WILDCARD_ELEMENT = PathElement.pathElement(CHILD);
    private static final PathAddress CHILD_B_ADDRESS = PathAddress.pathAddress(PARENT_ADDRESS, CHILD_B_ELEMENT);
    private static final PathAddress CHILD_C_ADDRESS = PathAddress.pathAddress(PARENT_ADDRESS, CHILD_C_ELEMENT);
    private static final PathAddress CHILD_WILDCARD_ADDRESS = PathAddress.pathAddress(PARENT_ADDRESS, CHILD_WILDCARD_ELEMENT);

    private static final String ATTR = "attr";
    private static final String GROUP = "group";

    private CountDownLatch attributeInLatch;
    private CountDownLatch attributeOutLatch;
    private boolean discardC;
    private boolean discardParent;

    @Before
    public void setup() {
        attributeInLatch = new CountDownLatch(1);
        attributeOutLatch = new CountDownLatch(1);
        discardC = false;
        discardParent = false;
    }

    @Test
    public void testDisappearingTopLevelAttribute() throws Exception {
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, CHILD_B_ADDRESS);
        ModelNode rsp = executeCheckForFailure(op);
        //noinspection ThrowableResultOfMethodCallIgnored
        assertTrue(rsp.toString(), rsp.get(FAILURE_DESCRIPTION).asString().contains(ControllerLogger.MGMT_OP_LOGGER.managementResourceNotFound(CHILD_B_ADDRESS).getMessage()));
    }

    @Test
    public void testDisappearingChildResourceAttribute() throws Exception {
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, PARENT_ADDRESS);
        op.get(RECURSIVE).set(true);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertTrue(result.toString(), result.hasDefined(CHILD, "C", ATTR));
        assertTrue(result.toString(), result.get(CHILD, "C", ATTR).asInt() == 1);
        assertFalse(result.toString(), result.get(CHILD).has("B"));
    }

    @Test
    public void testDisappearingChildrenResourcesAttribute() throws Exception {
        discardC = true;
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, PARENT_ADDRESS);
        op.get(RECURSIVE).set(true);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertFalse(result.toString(), result.get(CHILD).has("B"));
        assertFalse(result.toString(), result.get(CHILD).has("C"));
    }

    @Test
    public void testDisappearingWildcardChildrenResourcesAttribute() throws Exception {
        discardC = true;
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, CHILD_WILDCARD_ADDRESS);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 0, result.asInt());
    }

    @Test
    public void testDisappearingWildcardResourceAttribute() throws Exception {
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, CHILD_WILDCARD_ADDRESS);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());
        ModelNode cResult = result.get(0);
        assertEquals(result.toString(), CHILD_C_ADDRESS, PathAddress.pathAddress(cResult.get(OP_ADDR)));
        assertTrue(result.toString(), cResult.hasDefined(RESULT, ATTR));
        assertEquals(result.toString(), 1, cResult.get(RESULT, ATTR).asInt());
    }

    @Test
    public void testResourceDisappearingInAssembly() throws Exception {

        ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, PARENT_ADDRESS);
        op.get(RECURSIVE).set(true);
        op.get(INCLUDE_RUNTIME).set(true);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    attributeInLatch.await(300, TimeUnit.SECONDS);
                    discardParent = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    attributeOutLatch.countDown();
                }

            }
        };
        Thread t = new Thread(r);
        t.start();

        ModelNode rsp = executeCheckForFailure(op);
        //noinspection ThrowableResultOfMethodCallIgnored
        assertTrue(rsp.toString(), rsp.get(FAILURE_DESCRIPTION).asString().contains(ControllerLogger.MGMT_OP_LOGGER.managementResourceNotFound(PARENT_ADDRESS).getMessage()));
    }

    @Test
    public void testReadChildrenResourcesSingleDisappearing() throws Exception {
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_CHILDREN_RESOURCES_OPERATION, PARENT_ADDRESS);
        op.get(CHILD_TYPE).set(CHILD);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.OBJECT, result.getType());
        assertEquals(result.toString(), 1, result.asInt());
        assertTrue(result.toString(), result.hasDefined(CHILD_C_ELEMENT.getValue(), ATTR));
        assertEquals(result.toString(), 1, result.get(CHILD_C_ELEMENT.getValue(), ATTR).asInt());
    }

    @Test
    public void testReadChildrenResourcesAllDisappearing() throws Exception {
        discardC = true;
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_CHILDREN_RESOURCES_OPERATION, PARENT_ADDRESS);
        op.get(CHILD_TYPE).set(CHILD);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.OBJECT, result.getType());
        assertEquals(result.toString(), 0, result.asInt());
    }

    @Test
    public void testReadChildrenResourcesRecursive() throws Exception {
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_CHILDREN_RESOURCES_OPERATION, SUBSYSTEM_ADDRESS);
        op.get(CHILD_TYPE).set(PARENT);
        op.get(INCLUDE_RUNTIME).set(true);
        op.get(RECURSIVE).set(true);
        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.OBJECT, result.getType());
        assertEquals(result.toString(), 1, result.asInt());
        ModelNode parentResult = result.get(PARENT_ELEMENT.getValue());
        assertTrue(result.toString(), parentResult.hasDefined(CHILD, "C", ATTR));
        assertTrue(result.toString(), parentResult.get(CHILD, "C", ATTR).asInt() == 1);
        assertFalse(result.toString(), parentResult.get(CHILD).has("B"));
    }

    @Test
    public void testReadChildrenResourcesDisappearingInAssembly() throws Exception {

        ModelNode op = Util.createOperation(READ_CHILDREN_RESOURCES_OPERATION, SUBSYSTEM_ADDRESS);
        op.get(CHILD_TYPE).set(PARENT);
        op.get(RECURSIVE).set(true);
        op.get(INCLUDE_RUNTIME).set(true);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    attributeInLatch.await(300, TimeUnit.SECONDS);
                    discardParent = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    attributeOutLatch.countDown();
                }

            }
        };
        Thread t = new Thread(r);
        t.start();


        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.OBJECT, result.getType());
        assertEquals(result.toString(), 0, result.asInt());
    }

    @Test
    public void testReadAttributeGroup() throws Exception {
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_ATTRIBUTE_GROUP_OPERATION, CHILD_B_ADDRESS);
        op.get(NAME).set(GROUP);
        ModelNode rsp = executeCheckForFailure(op);
        //noinspection ThrowableResultOfMethodCallIgnored
        assertTrue(rsp.toString(), rsp.get(FAILURE_DESCRIPTION).asString().contains(ControllerLogger.MGMT_OP_LOGGER.managementResourceNotFound(CHILD_B_ADDRESS).getMessage()));
    }

    @Test
    public void testReadAttributeGroupWildcardsAllDisappearing() throws Exception {
        discardC = true;
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_ATTRIBUTE_GROUP_OPERATION, CHILD_WILDCARD_ADDRESS);
        op.get(NAME).set(GROUP);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 0, result.asInt());
    }

    @Test
    public void testReadAttributeGroupWildcardsOneDisappearing() throws Exception {
        attributeOutLatch.countDown();

        ModelNode op = Util.createOperation(READ_ATTRIBUTE_GROUP_OPERATION, CHILD_WILDCARD_ADDRESS);
        op.get(NAME).set(GROUP);
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = executeForResult(op);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());
        ModelNode cResult = result.get(0);
        assertEquals(result.toString(), CHILD_C_ADDRESS, PathAddress.pathAddress(cResult.get(OP_ADDR)));
        assertTrue(result.toString(), cResult.hasDefined(RESULT, ATTR));
        assertEquals(result.toString(), 1, cResult.get(RESULT, ATTR).asInt());
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        GlobalNotifications.registerGlobalNotifications(registration, processType);

        ManagementResourceRegistration subsystemRegistration = registration.registerSubModel(
                new SimpleResourceDefinition(SUBSYSTEM_ELEMENT, NonResolvingResourceDescriptionResolver.INSTANCE));
        ManagementResourceRegistration parentReg = subsystemRegistration.registerSubModel(
                new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PARENT_ELEMENT, NonResolvingResourceDescriptionResolver.INSTANCE).setRuntime()));
        ManagementResourceRegistration runtimeResource = parentReg.registerSubModel(
                new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(CHILD_WILDCARD_ELEMENT, NonResolvingResourceDescriptionResolver.INSTANCE).setRuntime()));
        AttributeDefinition runtimeAttr = TestUtils.createAttribute(ATTR, ModelType.LONG, GROUP);
        runtimeResource.registerReadOnlyAttribute(runtimeAttr, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                if (!discardC && "C".equals(context.getCurrentAddressValue())) {
                    context.getResult().set(1);
                } else {
                    attributeInLatch.countDown();
                    try {
                        attributeOutLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    throw new Resource.NoSuchResourceException(context.getCurrentAddress().getLastElement());
                }
            }
        });

        registration.registerOperationHandler(TestUtils.SETUP_OPERATION_DEF, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // no-op; just satisfy the test infrastructure's desire for an op
            }
        });

        managementModel.getRootResource().registerChild(SUBSYSTEM_ELEMENT, new SubsystemResource());
    }

    private class SubsystemResource extends PlaceholderResource.PlaceholderResourceEntry {


        private SubsystemResource() {
            super(PARENT_ELEMENT);
        }

        @Override
        public boolean hasChild(PathElement element) {
            return getChild(element) != null;
        }

        @Override
        public Resource getChild(PathElement element) {
            if (!discardParent && PARENT_ELEMENT.equals(element)) {
                return new ParentResource();
            }
            return null;
        }

        @Override
        public Resource requireChild(PathElement element) {
            Resource child = getChild(element);
            if (child == null) {
                throw new NoSuchResourceException(element);
            }
            return child;
        }

        @Override
        public boolean hasChildren(String childType) {
            return getChildren(childType).size() > 0;
        }

        @Override
        public Set<String> getChildTypes() {
            return Collections.singleton(PARENT);
        }

        @Override
        public Set<String> getChildrenNames(String childType) {
            if (!discardParent && PARENT.equals(childType)) {
                return Collections.singleton(PARENT_ELEMENT.getValue());
            }
            return Collections.emptySet();
        }

        @Override
        public Set<ResourceEntry> getChildren(String childType) {
            if (!discardParent && PARENT.equals(childType)) {
                ResourceEntry entry = new ParentResource();
                return Collections.singleton(entry);
            }
            return Collections.emptySet();
        }
    }

    private class ParentResource extends PlaceholderResource.PlaceholderResourceEntry {

        private ParentResource() {
            super(PARENT_ELEMENT);
        }

        @Override
        public boolean hasChild(PathElement element) {
            return getChild(element) != null;
        }

        @Override
        public Resource getChild(PathElement element) {
            if (CHILD_B_ELEMENT.equals(element)) {
                return new PlaceholderResourceEntry(CHILD_B_ELEMENT);
            } else if (CHILD_C_ELEMENT.equals(element)) {
                return new PlaceholderResourceEntry(CHILD_C_ELEMENT);
            }
            return null;
        }

        @Override
        public Resource requireChild(PathElement element) {
            Resource child = getChild(element);
            if (child == null) {
                throw new NoSuchResourceException(element);
            }
            return child;
        }

        @Override
        public boolean hasChildren(String childType) {
            return getChildren(childType).size() > 0;
        }

        @Override
        public Set<String> getChildTypes() {
            return Collections.singleton(CHILD);
        }

        @Override
        public Set<String> getChildrenNames(String childType) {
            if (CHILD.equals(childType)) {
                return new HashSet<>(Arrays.asList("B", "C"));
            }
            return Collections.emptySet();
        }

        @Override
        public Set<ResourceEntry> getChildren(String childType) {
            if (CHILD.equals(childType)) {
                ResourceEntry entryB = new PlaceholderResourceEntry(CHILD_B_ELEMENT);
                ResourceEntry entryC = new PlaceholderResourceEntry(CHILD_C_ELEMENT);
                return new HashSet<>(Arrays.asList(entryB, entryC));
            }
            return Collections.emptySet();
        }
    }
}
