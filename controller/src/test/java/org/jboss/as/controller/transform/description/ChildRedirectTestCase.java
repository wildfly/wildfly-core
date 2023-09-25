/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformationTargetImpl;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ChildRedirectTestCase {

    private static PathElement PATH = PathElement.pathElement("toto", "testSubsystem");
    private static PathElement CHILD = PathElement.pathElement("child");
    private static PathElement CHILD_ONE = PathElement.pathElement("child", "one");
    private static PathElement CHILD_TWO = PathElement.pathElement("child", "two");

    private static PathElement NEW_CHILD = PathElement.pathElement("new-child");

    private Resource resourceRoot = Resource.Factory.create();
    private TransformerRegistry registry = TransformerRegistry.Factory.create();
    private ManagementResourceRegistration resourceRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(ROOT);
    private TransformersSubRegistration transformersSubRegistration;

    @Before
    public void setUp() {
        // Cleanup
        resourceRoot = Resource.Factory.create();
        registry = TransformerRegistry.Factory.create();
        resourceRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(ROOT);
        // test
        final Resource toto = Resource.Factory.create();
        resourceRoot.registerChild(PATH, toto);
        //resourceModel = toto.getModel();

        final Resource childOne = Resource.Factory.create();
        toto.registerChild(CHILD_ONE, childOne);
        toto.getModel().setEmptyObject();

        final Resource childTwo = Resource.Factory.create();
        toto.registerChild(CHILD_TWO, childTwo);
        toto.getModel().setEmptyObject();

        // Register the description
        transformersSubRegistration = registry.getServerRegistration(ModelVersion.create(1));
    }


    @Test
    public void testWildcardRedirect() throws Exception {
        final ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(PATH);
        builder.addChildRedirection(CHILD, NEW_CHILD);
        TransformationDescription.Tools.register(builder.build(), transformersSubRegistration);

        final Resource resource = transformResource();
        Assert.assertNotNull(resource);
        final Resource toto = resource.getChild(PATH);
        Assert.assertNotNull(toto);

        Set<String> types = toto.getChildTypes();
        Assert.assertEquals(1, types.size());
        Assert.assertTrue(types.contains(NEW_CHILD.getKey()));

        Set<ResourceEntry> entries = toto.getChildren(NEW_CHILD.getKey());
        Assert.assertEquals(2, entries.size());

        PathElement[] expectedChildren = new PathElement[] {PathElement.pathElement(NEW_CHILD.getKey(), CHILD_ONE.getValue()), PathElement.pathElement(NEW_CHILD.getKey(), CHILD_TWO.getValue())};
        for (PathElement expectedChild : expectedChildren) {
            boolean found = false;
            for (ResourceEntry entry : entries) {
                if (entry.getPathElement().equals(expectedChild)) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue(found);
        }

        //Test operations get redirected
        final ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PATH, CHILD));
        OperationTransformer.TransformedOperation txOp = transformOperation(ModelVersion.create(1), addOp.clone());
        Assert.assertFalse(txOp.rejectOperation(success()));
        final ModelNode expectedTx = Util.createAddOperation(PathAddress.pathAddress(PATH, NEW_CHILD));
        Assert.assertEquals(expectedTx, txOp.getTransformedOperation());

        //Test operations in a composite get redirected
        final ModelNode composite = createComposite(addOp, addOp);
        txOp = transformOperation(ModelVersion.create(1), composite);
        Assert.assertFalse(txOp.rejectOperation(success()));
        Assert.assertEquals(createComposite(expectedTx, expectedTx), txOp.getTransformedOperation());
    }

    @Test
    public void testFixedRedirect() throws Exception {
        PathElement newChild = PathElement.pathElement("new-style", "lalala");
        final ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(PATH);
        builder.addChildRedirection(CHILD_ONE, newChild);
        TransformationDescription.Tools.register(builder.build(), transformersSubRegistration);

        final Resource resource = transformResource();
        Assert.assertNotNull(resource);
        final Resource toto = resource.getChild(PATH);
        Assert.assertNotNull(toto);

        Set<String> types = toto.getChildTypes();
        Assert.assertEquals(2, types.size());
        Assert.assertTrue(types.contains(CHILD_TWO.getKey()));
        Assert.assertTrue(types.contains(newChild.getKey()));

        Set<ResourceEntry> childEntries = toto.getChildren(CHILD_TWO.getKey());
        Assert.assertEquals(1, childEntries.size());
        Assert.assertEquals(CHILD_TWO, childEntries.iterator().next().getPathElement());

        childEntries = toto.getChildren(newChild.getKey());
        Assert.assertEquals(1, childEntries.size());
        Assert.assertEquals(newChild, childEntries.iterator().next().getPathElement());

        //Test operations get redirected
        final ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PATH, CHILD_ONE));
        OperationTransformer.TransformedOperation txOp = transformOperation(ModelVersion.create(1), addOp.clone());
        Assert.assertFalse(txOp.rejectOperation(success()));
        final ModelNode expectedTx = Util.createAddOperation(PathAddress.pathAddress(PATH, newChild));
        Assert.assertEquals(expectedTx, txOp.getTransformedOperation());

        //Test operations in a composite get redirected
        final ModelNode composite = createComposite(addOp, addOp);
        txOp = transformOperation(ModelVersion.create(1), composite);
        Assert.assertFalse(txOp.rejectOperation(success()));
        Assert.assertEquals(createComposite(expectedTx, expectedTx), txOp.getTransformedOperation());

    }


    private Resource transformResource() throws OperationFailedException {
        final TransformationTarget target = create(registry, ModelVersion.create(1));
        final ResourceTransformationContext context = createContext(target);
        return getTransfomers(target).transformResource(context, resourceRoot);
    }

    private ResourceTransformationContext createContext(final TransformationTarget target) {
        return Transformers.Factory.create(target, resourceRoot, resourceRegistration, ExpressionResolver.TEST_RESOLVER,
                RunningMode.NORMAL, ProcessType.STANDALONE_SERVER, null);
    }

    private Transformers getTransfomers(final TransformationTarget target) {
        return Transformers.Factory.create(target);
    }

    protected TransformationTarget create(final TransformerRegistry registry, ModelVersion version) {
        return create(registry, version, TransformationTarget.TransformationTargetType.SERVER);
    }

    protected TransformationTarget create(final TransformerRegistry registry, ModelVersion version, TransformationTarget.TransformationTargetType type) {
        return TransformationTargetImpl.create(null, registry, version, Collections.<PathAddress, ModelVersion>emptyMap(), type);
    }

    private OperationTransformer.TransformedOperation transformOperation(final ModelVersion version, final ModelNode operation) throws OperationFailedException {
        final TransformationTarget target = create(registry, version);
        final TransformationContext context = createContext(target);
        return getTransfomers(target).transformOperation(context, operation);
    }

    private static final ResourceDefinition ROOT = new SimpleResourceDefinition(PathElement.pathElement("test"), NonResolvingResourceDescriptionResolver.INSTANCE);

    private static ModelNode success() {
        final ModelNode result = new ModelNode();
        result.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
        result.get(ModelDescriptionConstants.RESULT);
        return result;
    }

    private ModelNode createComposite(ModelNode... steps) {
        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode stepsNode = composite.get(STEPS);
        for (ModelNode step : steps) {
            stepsNode.add(step);
        }
        return composite;
    }
}
