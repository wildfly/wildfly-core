/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests a single level of ordered children in the scenario where the secondary model supports indexed adds.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OrderedChildResourceSyncModelTestCase extends AbstractOrderedChildResourceSyncModelTestCase {

    public OrderedChildResourceSyncModelTestCase() {
        super(false, true);
    }

    protected OrderedChildResourceSyncModelTestCase(boolean localIndexedAdd) {
        super(false, localIndexedAdd);
    }

    @Test
    public void testSameModelSync() throws Exception {
        ModelNode originalModel = readResourceRecursive();
        executeTriggerSyncOperation(createPrimaryDcResources());
        ModelNode currentModel = readResourceRecursive();

        Assert.assertEquals(originalModel, currentModel);
    }

    @Test
    public void testRemovedNonOrderedChildrenModelSync() throws Exception {
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(NON_ORDERED_CHILD.getKey(), "apple"));
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(NON_ORDERED_CHILD.getKey()).keys(), "orange");
        compareSubsystemModels(primary, currentModel);
    }

    @Test
    public void testAddedNonOrderedChildrenModelSync() throws Exception {
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, NON_ORDERED_CHILD.getKey(), "pear");
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(NON_ORDERED_CHILD.getKey()).keys(), "apple", "orange", "pear");
        compareSubsystemModels(primary, currentModel);
    }

    @Test
    public void testRemovedOrderedChildrenModelSync() throws Exception {
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "apple"));
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "orange");
        compareSubsystemModels(primary, currentModel);
    }

    @Test
    public void testAddedOrderedChildrenModelSync() throws Exception {
        //Adds a child to the end
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear");
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "apple", "orange", "pear");
        compareSubsystemModels(primary, currentModel);
    }

    @Test
    public void testInsertedOrderedChildrenModelSync() throws Exception {
        //Inserts a child to the beginning
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear", 0);
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "apple", "orange");
        compareSubsystemModels(primary, currentModel);
    }

    @Test
    public void testChangeOrder() throws Exception {
        //Keeps the same elements but re-orders them

        Resource rootResource = createPrimaryDcResources();
        //[apple, orange] -> [orange, apple]
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(),  "orange"));
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(),  "apple"));
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "orange");
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "apple");
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "orange", "apple");
        compareSubsystemModels(primary, currentModel);
    }


    @Test
    public void testReplaceOrderedElements() throws Exception {
        //Inserts a child to the beginning
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(),  "orange"));
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(),  "apple"));
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear");
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "lemon");
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "lemon");
        compareSubsystemModels(primary, currentModel);
    }


    @Test
    public void testComplexInsertOrderedChildrenModelSync() throws Exception {
        //Complex test
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "grape", 1);
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "lemon", 1);
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear", 0);
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "cherry");
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "apple", "lemon", "grape", "orange", "cherry");
        compareSubsystemModels(primary, currentModel);

        Resource subsystemResource = findSubsystemResource(rootResource);
        subsystemResource.removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "pear"));
        subsystemResource.removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "apple"));
        subsystemResource.removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "lemon"));
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "kiwi", 1);
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "melon", 100);
        for (Resource.ResourceEntry child : subsystemResource.getChildren(ORDERED_CHILD.getKey())) {
            child.getModel().get(ATTR.getName()).set(child.getModel().get(ATTR.getName()).asString() + "$" + child.getName());
        }
        primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "grape", "kiwi", "orange", "cherry", "melon");
        compareSubsystemModels(primary, currentModel);
    }
}