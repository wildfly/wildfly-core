/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.jboss.as.domain.controller.operations;

import java.util.Set;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests multiple levels in the scenario where the slave model supports indexed adds
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
        executeTriggerSyncOperation(createMasterDcResources());
        ModelNode currentModel = readResourceRecursive();

        Assert.assertEquals(originalModel, currentModel);
    }

    @Test
    public void testRemovedOrderedChildrenModelSync() throws Exception {
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createMasterDcResources();
        findSubsystemResource(rootResource).removeChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "apple"));
        findSubsystemResource(rootResource)
            .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"))
            .removeChild(PathElement.pathElement(EXTRA_CHILD.getKey(), "jam"));
        ModelNode master = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "orange");
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "juice");
        compareSubsystemModels(master, currentModel);
    }

    @Test
    public void testAddedOrderedChildrenModelSync() throws Exception {
        //Adds a child to the end
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createMasterDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear");
        Resource orangeResource = findSubsystemResource(rootResource)
                .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"));
        createChildResource(orangeResource, EXTRA_CHILD.getKey(), "pancake", -1);
        ModelNode master = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "apple", "orange", "pear");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "jam", "juice", "pancake");
        compareSubsystemModels(master, currentModel);
    }

    @Test
    public void testInsertedOrderedChildrenModelSync() throws Exception {
        //Inserts a child to the beginning
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createMasterDcResources();
        createAndRegisterSubsystemChildFromRoot(rootResource, ORDERED_CHILD.getKey(), "pear", 0);
        Resource orangeResource = findSubsystemResource(rootResource)
                .requireChild(PathElement.pathElement(ORDERED_CHILD.getKey(), "orange"));
        createChildResource(orangeResource, EXTRA_CHILD.getKey(), "pancake", 0);
        ModelNode master = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "apple", "orange");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "pancake", "jam", "juice");
        compareSubsystemModels(master, currentModel);
    }

    @Test
    public void testChangeOrder() throws Exception {
        //Keeps the same elements but re-orders them

        Resource rootResource = createMasterDcResources();
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
        ModelNode master = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "orange", "apple");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys(), "juice", "jam");
        compareSubsystemModels(master, currentModel);
    }


    @Test
    public void testReplaceOrderedElements() throws Exception {
        //Inserts a child to the beginning
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createMasterDcResources();
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
        ModelNode master = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "lemon");
        compare(findSubsystemResource(
                currentModel).get(ORDERED_CHILD.getKey(), "pear", EXTRA_CHILD.getKey()).keys(), "marmelade", "compot");
        compareSubsystemModels(master, currentModel);
    }

    @Test
    public void testComplexInsertOrderedChildrenModelSync() throws Exception {
        //Complex test
        ModelNode originalModel = readResourceRecursive();

        Resource rootResource = createMasterDcResources();
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
        ModelNode master = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        ModelNode currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "pear", "apple", "lemon", "grape", "orange", "cherry");
        Set<String> appleKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "apple", EXTRA_CHILD.getKey()).keys();
        compare(appleKeys, "pancake", "jam", "cake", "compot", "juice", "cider");
        Set<String> cherryKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "cherry", EXTRA_CHILD.getKey()).keys();
        compare(cherryKeys, "pancake", "jam", "cake", "compot", "juice", "cider");
        compareSubsystemModels(master, currentModel);

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
        master = Resource.Tools.readModel(rootResource);

        executeTriggerSyncOperation(rootResource);
        currentModel = readResourceRecursive();

        Assert.assertNotEquals(originalModel, currentModel);
        compare(findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey()).keys(), "grape", "kiwi", "orange", "cherry", "melon");
        cherryKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "cherry", EXTRA_CHILD.getKey()).keys();
        compare(cherryKeys, "pancake", "jam", "cake", "compot", "juice", "cider");
        Set<String> orangeKeys = findSubsystemResource(currentModel).get(ORDERED_CHILD.getKey(), "orange", EXTRA_CHILD.getKey()).keys();
        compare(orangeKeys, "marmelade", "jam"); // <-- The order in current model is wrong
        compareSubsystemModels(master, currentModel);
    }

}