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
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
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
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
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
public class AliasTransformerTestCase {

    private static PathElement PATH = PathElement.pathElement("toto", "testSubsystem");
    private static PathElement CHILD = PathElement.pathElement("child");
    private static PathElement CHILD_ALIAS = PathElement.pathElement("child-alias");
    private static PathElement LEGACY_CHILD = PathElement.pathElement("legacy-child");

    private Resource resourceRoot = Resource.Factory.create();
    private TransformerRegistry registry = TransformerRegistry.Factory.create();
    private ManagementResourceRegistration resourceRegistration;
    private TransformersSubRegistration transformersSubRegistration;

    @Before
    public void setUp() {
        // Cleanup
        resourceRoot = Resource.Factory.create();
        registry = TransformerRegistry.Factory.create();
        resourceRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(ROOT);
        ManagementResourceRegistration ss = resourceRegistration.registerSubModel(new AbstractChildResourceDefinition(PATH));
        ManagementResourceRegistration target = ss.registerSubModel(new AbstractChildResourceDefinition(CHILD));
        ss.registerAlias(CHILD_ALIAS, new AliasEntry(target) {
            @Override
            public PathAddress convertToTargetAddress(PathAddress aliasAddress, AliasContext aliasContext) {
                Resource resource = aliasContext.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true);
                Assert.assertNotNull(resource.navigate(PathAddress.pathAddress(PATH).append(CHILD.getKey(), "one")));
                try {
                    resource.navigate(PathAddress.pathAddress(PATH).append(CHILD_ALIAS.getKey(), "one"));
                    Assert.fail("Should not have found alias child in the model");
                } catch (Exception expected) {
                                    }
                return PathAddress.pathAddress(PATH).append(CHILD.getKey(), aliasAddress.getLastElement().getValue());
            }
        });
        // test
        final Resource toto = Resource.Factory.create();
        resourceRoot.registerChild(PATH, toto);
        //resourceModel = toto.getModel();

        final Resource childOne = Resource.Factory.create();
        toto.registerChild(PathElement.pathElement(CHILD.getKey(), "one"), childOne);
        toto.getModel().setEmptyObject();

        // Register the description
        transformersSubRegistration = registry.getServerRegistration(ModelVersion.create(1));

        final ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(PATH);
        builder.addChildRedirection(CHILD, LEGACY_CHILD);
        TransformationDescription.Tools.register(builder.build(), transformersSubRegistration);
    }

    @Test
    public void testResourceTransformation() throws Exception {
        //We probably test this elsewhere, but make sure that only real (i.e. non-alias resources get transformed)
        final Resource resource = transformResource();
        Assert.assertNotNull(resource);
        final Resource toto = resource.getChild(PATH);
        Assert.assertNotNull(toto);

        Set<String> types = toto.getChildTypes();
        Assert.assertEquals(1, types.size());
        Assert.assertTrue(types.contains(LEGACY_CHILD.getKey()));
        Set<Resource.ResourceEntry> entries = toto.getChildren(LEGACY_CHILD.getKey());
        Assert.assertEquals(1, entries.size());
        Assert.assertNotNull(toto.getChild(PathElement.pathElement(LEGACY_CHILD.getKey(), "one")));
    }

    @Test
    public void testOperationTransformation() throws Exception {
        //This is a test against the real, non-alias entry, which we do elsewhere but included here for clarity
        testOperationTransformation(PathElement.pathElement(CHILD.getKey(), "one"));
    }

    @Test
    public void testAliasOperationTransformation() throws Exception {
        //Test that aliases get transformed the same as the main resource
        testOperationTransformation(PathElement.pathElement(CHILD_ALIAS.getKey(), "one"));
    }

    public void testOperationTransformation(PathElement childElement) throws Exception {
        //This is a test against the real, non-alias entry, which we do elsewhere but included here for clarity
        final ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PATH, childElement));
        OperationTransformer.TransformedOperation txOp = transformOperation(ModelVersion.create(1), addOp.clone());
        Assert.assertFalse(txOp.rejectOperation(success()));
        final ModelNode expectedTx = Util.createAddOperation(PathAddress.pathAddress(PATH, PathElement.pathElement(LEGACY_CHILD.getKey(), "one")));
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

    static class AbstractChildResourceDefinition extends SimpleResourceDefinition {
        public AbstractChildResourceDefinition(PathElement element) {
            super(new Parameters(element, NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(ModelOnlyAddStepHandler.INSTANCE)
                    .setRemoveHandler(new ModelOnlyRemoveStepHandler())
                    .setOrderedChild());
        }
    }

}
