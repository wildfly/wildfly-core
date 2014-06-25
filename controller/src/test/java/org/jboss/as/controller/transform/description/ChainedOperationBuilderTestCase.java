/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.transform.description;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.Collections;

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
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformationTargetImpl;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ChainedOperationBuilderTestCase {

    private static PathElement PATH = PathElement.pathElement("toto", "testSubsystem");

    private static final ResourceDefinition ROOT = new SimpleResourceDefinition(null, new NonResolvingResourceDescriptionResolver());
    private static final ModelVersion V1_0_0 = ModelVersion.create(1, 0, 0);
    private static final ModelVersion V2_0_0 = ModelVersion.create(2, 0, 0);
    private static final ModelVersion V3_0_0 = ModelVersion.create(3, 0, 0);
    private static final ModelVersion V4_0_0 = ModelVersion.create(4, 0, 0);

    private Resource resourceRoot = Resource.Factory.create();
    private Resource toto;
    private TransformerRegistry registry = TransformerRegistry.Factory.create(null);
    private ManagementResourceRegistration resourceRegistration = ManagementResourceRegistration.Factory.create(ROOT);
    private TransformersSubRegistration transformersSubRegistration;
    private ModelNode resourceModel;

    @Before
    public void setUp() {
        // Cleanup
        resourceRoot = Resource.Factory.create();
        registry = TransformerRegistry.Factory.create(null);
        resourceRegistration = ManagementResourceRegistration.Factory.create(ROOT);
        // test
        toto = Resource.Factory.create();
        resourceRoot.registerChild(PATH, toto);
        resourceModel = toto.getModel();

        // Register the description
        transformersSubRegistration = registry.getServerRegistration(ModelVersion.create(1));
    }

    @Test
    public void testOperationNoTransformation() throws Exception {

        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V2_0_0);
        chainedBuilder.createBuilder(V2_0_0, V1_0_0);

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0), transformersSubRegistration);

        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(address.toModelNode());
        node.get(OP).set(ADD);

        OperationTransformer.TransformedOperation op = transformOperation(node);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(node, op.getTransformedOperation());

        node.get(OP_ADDR).set(address.append("one", "two").toModelNode());
        op = transformOperation(node);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(node, op.getTransformedOperation());
    }

    @Test
    public void testOperationChainedNoTransformation() throws Exception {

        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V3_0_0);
        chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        chainedBuilder.createBuilder(V2_0_0, V1_0_0);

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0), transformersSubRegistration);

        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(address.toModelNode());
        node.get(OP).set(ADD);

        OperationTransformer.TransformedOperation op = transformOperation(node);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(node, op.getTransformedOperation());

        node.get(OP_ADDR).set(address.append("one", "two").toModelNode());
        op = transformOperation(node);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(node, op.getTransformedOperation());
    }

    @Test
    public void testDefaultOperationOneInChain() throws Exception {

        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V2_0_0);
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
            .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "attr1")
                .setValueConverter(new SimpleAttributeConverter("old", "new"), "attr2");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0), transformersSubRegistration);

        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = new ModelNode();
        original.get(OP_ADDR).set(address.toModelNode());
        original.get(OP).set(ADD);
        original.get("attr1").set(new ValueExpression("${no.no}"));
        original.get("attr2").set("old");
        original.get("attr3").set("whatever");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertTrue(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr2").set("new");
        Assert.assertEquals(expected, op.getTransformedOperation());

        ModelNode originalNotRejected = original.clone();
        originalNotRejected.get("attr1").set("accepted");
        op = transformOperation(originalNotRejected);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = originalNotRejected.clone();
        expected.get("attr2").set("new");
        Assert.assertEquals(expected, op.getTransformedOperation());
    }

    @Test
    public void testDefaultOperationChain() throws Exception {

        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        chainedBuilder.createBuilder(V4_0_0, V3_0_0)
            .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "attr1")
                .setValueConverter(new SimpleAttributeConverter("old", "one"), "attr");
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
            .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "attr2")
                .setValueConverter(new SimpleAttributeConverter("one", "two"), "attr");
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
            .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "attr3")
                .setValueConverter(new SimpleAttributeConverter("two", "three"), "attr");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = new ModelNode();
        original.get(OP_ADDR).set(address.toModelNode());
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr2").set("b");
        original.get("attr3").set("c");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());

        ModelNode reject = original.clone();
        reject.get("attr1").set(new ValueExpression("${reject}"));
        op = transformOperation(reject);
        Assert.assertTrue(op.rejectOperation(success()));
        expected = reject.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());

        reject = original.clone();
        reject.get("attr2").set(new ValueExpression("${reject}"));
        op = transformOperation(reject);
        Assert.assertTrue(op.rejectOperation(success()));
        expected = reject.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());

        reject = original.clone();
        reject.get("attr3").set(new ValueExpression("${reject}"));
        op = transformOperation(reject);
        Assert.assertTrue(op.rejectOperation(success()));
        expected = reject.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());
    }


    @Test
    public void testDefaultOperationChainNotAllVersions() throws Exception {

        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        chainedBuilder.createBuilder(V4_0_0, V3_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("old", "one"), "attr");
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("one", "two"), "attr");
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("two", "three"), "attr");

        TransformationDescription.Tools.register(chainedBuilder.build(V2_0_0, V3_0_0), transformersSubRegistration);

        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = new ModelNode();
        original.get(OP_ADDR).set(address.toModelNode());
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr2").set("b");
        original.get("attr3").set("c");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("two");
        Assert.assertEquals(expected, op.getTransformedOperation());
    }

    @Test
    public void testDefaultOperationChildWithNoTransformer() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        chainedBuilder.createBuilder(V4_0_0, V3_0_0)
            .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "attr1")
                .setValueConverter(new SimpleAttributeConverter("old", "new"), "attr2");
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
            .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "attr1")
                .setValueConverter(new SimpleAttributeConverter("old", "new"), "attr2");
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
            .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "attr1")
                .setValueConverter(new SimpleAttributeConverter("old", "new"), "attr2");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH).append("one", "two");
        ModelNode original = new ModelNode();
        original.get(OP_ADDR).set(address.toModelNode());
        original.get(OP).set(ADD);
        original.get("attr1").set(new ValueExpression("${no.no}"));
        original.get("attr2").set("old");
        original.get("attr3").set("whatever");

        //Since this operation is on child resource with no transformers registered we should
        //just get the original back and there should be no rejections
        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());
    }


    @Test
    public void testOperationAgainstDiscardedAndRejectedResources() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("old", "one"), "attr")
                .end();
        builder.discardChildResource(PathElement.pathElement("child", "discard1"));
        builder.rejectChildResource(PathElement.pathElement("child", "reject1"));

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("one", "two"), "attr")
                .end();
        builder.discardChildResource(PathElement.pathElement("child", "discard2"));
        builder.rejectChildResource(PathElement.pathElement("child", "reject2"));

        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("two", "three"), "attr")
                .end();
        builder.discardChildResource(PathElement.pathElement("child", "discard3"));
        builder.rejectChildResource(PathElement.pathElement("child", "reject3"));

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        //Although tested elsewhere test that the root operations get transformed as expected
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = Util.createAddOperation(address);
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Test ops against children with nothing special registered, and its children
        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "normal")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "normal")).append(PathElement.pathElement("one", "more")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        //Test ops against the discarded resources
        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "discard1")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "discard2")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "discard3")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        //Test ops against children of the discarded resources
        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "discard1")).append(PathElement.pathElement("one", "more")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "discard2")).append(PathElement.pathElement("one", "more")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "discard3")).append(PathElement.pathElement("one", "more")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        //Test ops against the rejected resources
        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "reject1")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertTrue(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "reject2")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertTrue(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "reject3")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertTrue(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        //Test ops against children of the rejected resources
        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "reject1")).append(PathElement.pathElement("one", "more")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertTrue(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "reject2")).append(PathElement.pathElement("one", "more")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertTrue(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        original = Util.createAddOperation(address.append(PathElement.pathElement("child", "reject3")).append(PathElement.pathElement("one", "more")));
        original.get("attr1").set("a");
        op = transformOperation(original);
        Assert.assertTrue(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());
    }

    @Test
    public void testRawOperationOverride() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0)
            .addRawOperationTransformationOverride("test", new OperationTransformer() {
                @Override
                public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation)
                        throws OperationFailedException {
                    Assert.assertEquals("test", operation.get(OP).asString());
                    return new TransformedOperation(Util.createOperation("test1", address), TransformedOperation.ORIGINAL_RESULT);
                }
            });
        builder.getAttributeBuilder()
            .setValueConverter(new SimpleAttributeConverter("old", "one"), "attr")
            .end();


        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0)
                .addRawOperationTransformationOverride("test1", new OperationTransformer() {
                    @Override
                    public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation)
                            throws OperationFailedException {
                        Assert.assertEquals("test1", operation.get(OP).asString());
                        return new TransformedOperation(Util.createOperation("test2", address), TransformedOperation.ORIGINAL_RESULT);
                    }
                });
        builder.getAttributeBuilder()
            .setValueConverter(new SimpleAttributeConverter("one", "two"), "attr")
            .end();

        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .addRawOperationTransformationOverride("test2", new OperationTransformer() {
                    @Override
                    public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation)
                            throws OperationFailedException {
                        Assert.assertEquals("test2", operation.get(OP).asString());
                        return new TransformedOperation(Util.createOperation("test3", address), TransformedOperation.ORIGINAL_RESULT);
                    }
                });
        builder.getAttributeBuilder()
            .setValueConverter(new SimpleAttributeConverter("two", "three"), "attr")
            .end();

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        //Although tested elsewhere test that not overridded operations get transformed as expected
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = Util.createAddOperation(address);
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Test that the raw operation transformer kicks in
        original = Util.createEmptyOperation("test", address);
        op = transformOperation(original);
    }

    @Test
    public void testDiscardOperations() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        chainedBuilder.createBuilder(V4_0_0, V3_0_0)
                .discardOperations("discard1A", "discard1B");
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
                .discardOperations("discard2A", "discard2B");
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .discardOperations("discard3A", "discard3B");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        //Although tested elsewhere test that not discarded operations get transformed as expected
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = Util.createAddOperation(address);
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        //Now test that the discarded operations get discarded
        original = Util.createEmptyOperation("discard1A", address);
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createEmptyOperation("discard1B", address);
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createEmptyOperation("discard2A", address);
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createEmptyOperation("discard2B", address);
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createEmptyOperation("discard3A", address);
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());

        original = Util.createEmptyOperation("discard3B", address);
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertNull(op.getTransformedOperation());
    }

    @Test
    public void testOperationTransformationOverride() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("old", "one"), "attr")
                    .end();
        builder.addOperationTransformationOverride("test")
                .inheritResourceAttributeDefinitions()
                .addRename("attr1", "attr-one")
                .rename("testA");

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("one", "two"), "attr")
                .end();
        builder.addOperationTransformationOverride("testA")
            .setValueConverter(new NewAttributeConverter("new-one"), "attr-new")
            .setValueConverter(new SimpleAttributeConverter("a", "No1"), "attr-one");


        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0)
            .getAttributeBuilder()
                .setValueConverter(new NewAttributeConverter("newA"), "new-main")
                .end();
        builder.addOperationTransformationOverride("testA")
            .inheritResourceAttributeDefinitions()
            .setValueConverter(new SimpleAttributeConverter("new-one", "new1"), "attr-new")
            .addRename("attr-new", "new-attr")
            .rename("testB");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        //Although tested elsewhere test that the main operation gets transformed as expected
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = Util.createAddOperation(address);
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("two");
        expected.get("new-main").set("newA");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Test the overridden operation
        original = Util.createAddOperation(address);
        original.get(OP).set("test");
        original.get("attr").set("old");
        original.get("attr1").set("a");

        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = Util.createOperation("testB", address);
        expected.get("attr-one").set("No1");
        expected.get("attr").set("one");
        expected.get("new-main").set("newA");
        expected.get("new-attr").set("new1");
        Assert.assertEquals(expected, op.getTransformedOperation());
    }

    @Test
    public void testCustomOperationTransformationOverride() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("old", "one"), "attr")
                    .end();
        builder.addOperationTransformationOverride("test")
                .inheritResourceAttributeDefinitions()
                .addRename("attr1", "attr-one")
                .rename("testA")
                .setCustomOperationTransformer(new OperationTransformer() {
                    @Override
                    public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation)
                            throws OperationFailedException {
                        Assert.assertEquals("testA", operation.get(OP).asString());
                        Assert.assertEquals("one", operation.get("attr").asString());
                        Assert.assertEquals("a", operation.get("attr-one").asString());

                        ModelNode copy = operation.clone();
                        copy.get("attr").set("one_");
                        copy.get("attr-one").set("a_");
                        return new TransformedOperation(copy, TransformedOperation.ORIGINAL_RESULT);
                    }
                });


        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0)
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("one", "two"), "attr")
                .end();
        builder.addOperationTransformationOverride("testA")
            .setValueConverter(new SimpleAttributeConverter("a_", "No1"), "attr-one")
            .setCustomOperationTransformer(new OperationTransformer() {
                @Override
                public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation)
                        throws OperationFailedException {
                    ModelNode copy = operation.clone();
                    copy.get("attr-new").set("new-one");
                    return new TransformedOperation(copy, TransformedOperation.ORIGINAL_RESULT);
                }
            });


        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0)
            .getAttributeBuilder()
                .setValueConverter(new NewAttributeConverter("newA"), "new-main")
                .end();
        builder.addOperationTransformationOverride("testA")
            .inheritResourceAttributeDefinitions()
            .setValueConverter(new SimpleAttributeConverter("new-one", "new1"), "attr-new")
            .addRename("attr-new", "new-attr")
            .rename("testB");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        //Although tested elsewhere test that the main operation gets transformed as expected
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = Util.createAddOperation(address);
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("two");
        expected.get("new-main").set("newA");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Test the overridden operation
        original = Util.createAddOperation(address);
        original.get(OP).set("test");
        original.get("attr").set("old");
        original.get("attr1").set("a");

        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = Util.createOperation("testB", address);
        expected.get("attr-one").set("No1");
        expected.get("attr").set("one_");
        expected.get("new-main").set("newA");
        expected.get("new-attr").set("new1");
        Assert.assertEquals(expected, op.getTransformedOperation());

    }

    @Test
    public void testOperationsTransformationHierarchy() throws Exception {
        final PathElement common = PathElement.pathElement("child", "common");
        final PathElement one = PathElement.pathElement("child", "one");
        final PathElement two = PathElement.pathElement("child", "two");
        final PathElement three = PathElement.pathElement("child", "three");
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        builder.addChildResource(common).getAttributeBuilder()
            .setValueConverter(new SimpleAttributeConverter("old", "one"), "attr")
            .end();
        builder.addChildResource(one).addOperationTransformationOverride("test")
            .setValueConverter(new NewAttributeConverter("a"), "attr-1");

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        builder.addChildResource(common).getAttributeBuilder()
            .setValueConverter(new SimpleAttributeConverter("one", "two"), "attr");
        builder.addChildResource(two).addOperationTransformationOverride("test")
            .setValueConverter(new NewAttributeConverter("b"), "attr-2");

        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0);
        builder.addChildResource(common).getAttributeBuilder()
            .setValueConverter(new SimpleAttributeConverter("two", "three"), "attr");
        builder.addChildResource(three).addOperationTransformationOverride("test")
            .setValueConverter(new NewAttributeConverter("c"), "attr-3");


        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        //Although tested elsewhere test that the root operation gets (not) transformed as expected
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = Util.createAddOperation(address);
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        //Add ops on the common child should have the 'common' transformation
        original = Util.createAddOperation(address.append(common));
        original.get("attr").set("old");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Add ops on the one, two and three children should not be transformed
        original = Util.createAddOperation(address.append(one));
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        original = Util.createAddOperation(address.append(two));
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        original = Util.createAddOperation(address.append(three));
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());

        //The overridden ops on the children should have basic transformation
        original = Util.createEmptyOperation("test", address.append(one));
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = original.clone();
        expected.get("attr-1").set("a");
        Assert.assertEquals(expected, op.getTransformedOperation());

        original = Util.createEmptyOperation("test", address.append(two));
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = original.clone();
        expected.get("attr-2").set("b");
        Assert.assertEquals(expected, op.getTransformedOperation());

        original = Util.createEmptyOperation("test", address.append(three));
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = original.clone();
        expected.get("attr-3").set("c");
        Assert.assertEquals(expected, op.getTransformedOperation());
    }


    @Test
    public void testOperationsTransformationHierarchyWithPathChange() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);


        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        builder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("old", "one"), "attr");
        ResourceTransformationDescriptionBuilder childBuilder =
                builder.addChildRedirection(PathElement.pathElement("a", "one"), PathElement.pathElement("a1", "one1"));
        childBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("original", "ONE"), "ch");
        ResourceTransformationDescriptionBuilder grandChildBuilder =
                childBuilder.addChildRedirection(PathElement.pathElement("x", "a"), PathElement.pathElement("x1", "a1"));
        grandChildBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("start", "A"), "gc");
        grandChildBuilder = builder.addChildResource(PathElement.pathElement("b", "two")).addChildRedirection(PathElement.pathElement("y", "b"), PathElement.pathElement("y1", "b1"));
        grandChildBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("first", "second"), "gc");

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        builder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("one", "two"), "attr");
        childBuilder =
                builder.addChildRedirection(PathElement.pathElement("a1", "one1"), PathElement.pathElement("a2", "one2"));
        childBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("ONE", "TWO"), "ch");
        grandChildBuilder = builder.addChildResource(PathElement.pathElement("b", "two")).addChildRedirection(PathElement.pathElement("y1", "b1"), PathElement.pathElement("y2", "b2"));
        grandChildBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("second", "third"), "gc");

        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0);
        builder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("two", "three"), "attr");
        childBuilder =
                builder.addChildRedirection(PathElement.pathElement("a2", "one2"), PathElement.pathElement("a3", "one3"));
        childBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("TWO", "THREE"), "ch");
        grandChildBuilder =
                childBuilder.addChildRedirection(PathElement.pathElement("x1", "a1"), PathElement.pathElement("x2", "a2"));
        grandChildBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("A", "B"), "gc");
        grandChildBuilder = builder.addChildResource(PathElement.pathElement("b", "two")).addChildRedirection(PathElement.pathElement("y2", "b2"), PathElement.pathElement("y3", "b3"));
        grandChildBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("third", "fourth"), "gc");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0, V3_0_0), transformersSubRegistration);

        //Although tested elsewhere test that the root operation gets transformed as expected
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(PATH);
        ModelNode original = Util.createAddOperation(address);
        original.get(OP).set(ADD);
        original.get("attr1").set("a");
        original.get("attr").set("old");

        OperationTransformer.TransformedOperation op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        ModelNode expected = original.clone();
        expected.get("attr").set("three");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Test child redirection
        original = Util.createAddOperation(address.append(PathElement.pathElement("a", "one")));
        original.get("ch").set("original");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = Util.createAddOperation(address.append(PathElement.pathElement("a3", "one3")));
        expected.get("ch").set("THREE");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Test grandchild redirection when the intermediate child changes
        original = Util.createAddOperation(address.append(PathElement.pathElement("a", "one"), PathElement.pathElement("x", "a")));
        original.get("gc").set("start");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = Util.createAddOperation(address.append(PathElement.pathElement("a3", "one3"), PathElement.pathElement("x2", "a2")));
        expected.get("gc").set("B");
        Assert.assertEquals(expected, op.getTransformedOperation());

        //Test grandchild redirection when the intermediate child stays the same
        original = Util.createAddOperation(address.append(PathElement.pathElement("b", "two"), PathElement.pathElement("y", "b")));
        original.get("gc").set("first");
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        expected = Util.createAddOperation(address.append(PathElement.pathElement("b", "two"), PathElement.pathElement("y3", "b3")));
        expected.get("gc").set("fourth");
        Assert.assertEquals(expected, op.getTransformedOperation());

        /*
        This does not work, but should not be an issue since in the real world operations must be executed against the current model, so a1=one1 does not exist

        //Test that ops for addresses further down the chain don't get transformed
        original = Util.createAddOperation(address.append(PathElement.pathElement("a1", "one1")));
        op = transformOperation(original);
        Assert.assertFalse(op.rejectOperation(success()));
        Assert.assertEquals(original, op.getTransformedOperation());
         */
    }

    private OperationTransformer.TransformedOperation transformOperation(final ModelNode operation) throws OperationFailedException {
        final TransformationTarget target = create(registry, ModelVersion.create(1));
        final TransformationContext context = createContext(target);
        return getTransfomers(target).transformOperation(context, operation);
    }

    private ResourceTransformationContext createContext(final TransformationTarget target) {
        return Transformers.Factory.create(target, resourceRoot, resourceRegistration, ExpressionResolver.DEFAULT, RunningMode.NORMAL, ProcessType.STANDALONE_SERVER);
    }

    private Transformers getTransfomers(final TransformationTarget target) {
        return Transformers.Factory.create(target);
    }

    protected TransformationTarget create(final TransformerRegistry registry, ModelVersion version) {
        return create(registry, version, TransformationTarget.TransformationTargetType.SERVER);
    }

    protected TransformationTarget create(final TransformerRegistry registry, ModelVersion version, TransformationTarget.TransformationTargetType type) {
        return TransformationTargetImpl.create(registry, version, Collections.<PathAddress, ModelVersion>emptyMap(), null, type, null);
    }

    private static final ModelNode success() {
        final ModelNode result = new ModelNode();
        result.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
        result.get(ModelDescriptionConstants.RESULT);
        return result;
    }

    private static class SimpleAttributeConverter extends AttributeConverter.DefaultAttributeConverter {
        private final ModelNode expectedValue;
        private final ModelNode newValue;

        SimpleAttributeConverter(String expectedValue, String newValue) {
            this.expectedValue = new ModelNode(expectedValue);
            this.newValue = new ModelNode(newValue);
        }

        SimpleAttributeConverter(ModelNode expectedValue, ModelNode newValue) {
            this.expectedValue = expectedValue;
            this.newValue = newValue;
        }

        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            //System.out.println("-----> transform " + address + " " + attributeValue.asString() + " expected:" + expectedValue);
            Assert.assertEquals(expectedValue, attributeValue);
            attributeValue.set(newValue);
        }
    }

    private static class NewAttributeConverter extends SimpleAttributeConverter {
        NewAttributeConverter(String newValue) {
            super(new ModelNode(), new ModelNode(newValue));
        }
    }
}
