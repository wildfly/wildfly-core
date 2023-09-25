/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import java.util.Set;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests multiple levels in the scenario where the secondary model supports indexed adds
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OrderedChildMoreLevelsResourceSyncModelTestCase extends AbstractOrderedChildResourceSyncModelTestCase {

    public OrderedChildMoreLevelsResourceSyncModelTestCase() {
        this(true);
    }

    protected OrderedChildMoreLevelsResourceSyncModelTestCase(boolean localIndexedAdd) {
        super(true, localIndexedAdd);
    }

    @Test
    public void testSameModelSync() throws Exception {
        ModelNode originalModel = readResourceRecursive();
        executeTriggerSyncOperation(createPrimaryDcResources());
        ModelNode currentModel = readResourceRecursive();

        Assert.assertEquals(originalModel, currentModel);
    }

    @Test
    public void testRemovedOrderedChildrenModelSync() throws Exception {
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "apple"));
        findSubsystemResource(rootResource)
            .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"))
            .removeChild(PathElement.pathElement(EXTRA_CHILD.getKey(), "jam"));
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "orange");
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "juice");
        compareSubsystemModels(primary, currentModel);
    }

    @Test
    public void testAddedOrderedChildrenModelSync() throws Exception {
        //Adds a child to the end
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear");
        Resource orangeResource = findSubsystemResource(rootResource)
                .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"));
        createChildResource(orangeResource, EXTRA_CHILD.getKey(), "pancake", -1);
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "apple", "orange", "pear");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "jam", "juice", "pancake");
        compareSubsystemModels(primary, currentModel);
    }

    @Test
    public void testInsertedOrderedChildrenModelSync() throws Exception {
        //Inserts a child to the beginning
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createPrimaryDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear", 0);
        Resource orangeResource = findSubsystemResource(rootResource)
                .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"));
        createChildResource(orangeResource, EXTRA_CHILD.getKey(), "pancake", 0);
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "apple", "orange");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "pancake", "jam", "juice");
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
        //[jam, juice] -> [juice, jam]
        Resource orangeResource = findSubsystemResource(rootResource)
                .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"));
        orangeResource.removeChild(PathElement.pathElement(EXTRA_CHILD.getKey(), "jam"));
        orangeResource.removeChild(PathElement.pathElement(EXTRA_CHILD.getKey(), "juice"));
        createChildResource(orangeResource, EXTRA_CHILD.getKey(), "juice", -1);
        createChildResource(orangeResource, EXTRA_CHILD.getKey(), "jam", -1);
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "orange", "apple");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "juice", "jam");
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
        Resource pearResource = findSubsystemResource(rootResource)
                .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "pear"));
        pearResource.removeChild(PathElement.pathElement(EXTRA_CHILD.getKey(), "jam"));
        pearResource.removeChild(PathElement.pathElement(EXTRA_CHILD.getKey(), "juice"));
        createChildResource(pearResource, EXTRA_CHILD.getKey(), "marmelade", -1);
        createChildResource(pearResource, EXTRA_CHILD.getKey(), "compot", -1);
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "lemon");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "pear", EXTRA_CHILD.getKey()).keys(), "marmelade", "compot");
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
        Resource cherry = createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "cherry");
        createChildResource(cherry, EXTRA_CHILD.getKey(), "compot", 1);
        createChildResource(cherry, EXTRA_CHILD.getKey(), "cake", 1);
        createChildResource(cherry, EXTRA_CHILD.getKey(), "pancake", 0);
        createChildResource(cherry, EXTRA_CHILD.getKey(), "cider", -1);
        Resource apple = findSubsystemResource(rootResource).getChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "apple"));
        createChildResource(apple, EXTRA_CHILD.getKey(), "compot", 1);
        createChildResource(apple, EXTRA_CHILD.getKey(), "cake", 1);
        createChildResource(apple, EXTRA_CHILD.getKey(), "pancake", 0);
        createChildResource(apple, EXTRA_CHILD.getKey(), "cider", -1);
        ModelNode primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "apple", "lemon", "grape", "orange", "cherry");
        Set<String> appleKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "apple", EXTRA_CHILD.getKey()).keys();
        compare(appleKeys, "pancake", "jam", "cake", "compot", "juice", "cider");
        Set<String> cherryKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "cherry", EXTRA_CHILD.getKey()).keys();
        compare(cherryKeys, "pancake", "jam", "cake", "compot", "juice", "cider");
        compareSubsystemModels(primary, currentModel);

        Resource subsystemResource = findSubsystemResource(rootResource);
        subsystemResource.removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "pear"));
        subsystemResource.removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "apple"));
        subsystemResource.removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "lemon"));
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "kiwi", 1);
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "melon", 100);
        Resource orange = findSubsystemResource(rootResource).getChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"));
        orange.removeChild(PathElement.pathElement(EXTRA_CHILD.getKey(), "juice"));
        createChildResource(orange, EXTRA_CHILD.getKey(), "marmelade", 0);

        for (Resource.ResourceEntry child : subsystemResource.getChildren(ORDERED_CHILD.getKey())) {
            child.getModel().get(ATTR.getName()).set(child.getModel().get(ATTR.getName()).asString() + "$" + child.getName());
            for (Resource.ResourceEntry extraChild : child.getChildren(EXTRA_CHILD.getKey())) {
                extraChild.getModel().get(ATTR.getName()).set(extraChild.getModel().get(ATTR.getName()).asString() + "$" + extraChild.getName());
            }
        }
        primary = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "grape", "kiwi", "orange", "cherry", "melon");
        cherryKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "cherry", EXTRA_CHILD.getKey()).keys();
        compare(cherryKeys, "pancake", "jam", "cake", "compot", "juice", "cider");
        Set<String> orangeKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys();
        compare(orangeKeys, "marmelade", "jam"); // <-- The order in current model is wrong
        compareSubsystemModels(primary, currentModel);
    }

}