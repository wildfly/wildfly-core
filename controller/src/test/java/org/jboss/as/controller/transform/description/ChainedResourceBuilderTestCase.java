/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

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
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
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
public class ChainedResourceBuilderTestCase {

    private static PathElement PATH = PathElement.pathElement("toto", "testSubsystem");

    private static final ResourceDefinition ROOT = new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE);
    private static final ModelVersion V1_0_0 = ModelVersion.create(1, 0, 0);
    private static final ModelVersion V2_0_0 = ModelVersion.create(2, 0, 0);
    private static final ModelVersion V3_0_0 = ModelVersion.create(3, 0, 0);
    private static final ModelVersion V4_0_0 = ModelVersion.create(4, 0, 0);
    private static final ModelVersion UNKNOWN_VERSION = ModelVersion.create(1, 1, 0);
    private static final ModelVersion[] ALL_TESTED_VERSIONS = {V1_0_0, ModelVersion.create(1, 0, 1), ModelVersion.create(1, 0, 5), UNKNOWN_VERSION};
    private static final ModelVersion[] VALID_TESTED_VERSIONS = {V1_0_0, ModelVersion.create(1, 0, 1), ModelVersion.create(1, 0, 5)};

    private Resource resourceRoot = Resource.Factory.create();
    private Resource toto;
    private TransformerRegistry registry = TransformerRegistry.Factory.create();
    private ManagementResourceRegistration resourceRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(ROOT);
    private TransformersSubRegistration transformersSubRegistration;
    private ModelNode resourceModel;

    @Before
    public void setUp() {
        // Cleanup
        resourceRoot = Resource.Factory.create();
        registry = TransformerRegistry.Factory.create();
        resourceRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(ROOT);
        // test
        toto = Resource.Factory.create();
        resourceRoot.registerChild(PATH, toto);
        resourceModel = toto.getModel();

        // Register the description
        transformersSubRegistration = registry.getServerRegistration(ModelVersion.create(1));
    }

    @Test
    public void testResourceChildrenNoTransformation() throws Exception {
        //Set up the model
        resourceModel.get("attr1").set("test1");
        Resource childResource = addChild(toto, "child", "one");
        childResource.getModel().get("attr2").set("test2");
        Resource grandChildResource = addChild(childResource, "grand", "one");
        grandChildResource.getModel().get("attr3").set("test3");

        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V2_0_0);
        chainedBuilder.createBuilder(V2_0_0, V1_0_0);

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0).get(V1_0_0), transformersSubRegistration);

        final Resource resource = transformResource(ModelVersion.create(1));
        Assert.assertNotNull(resource);
        final Resource toto = resource.getChild(PATH);
        Assert.assertNotNull(toto);
        final ModelNode model = toto.getModel();
        Assert.assertEquals("test1", model.get("attr1").asString());
        Resource child = toto.getChild(PathElement.pathElement("child", "one"));
        ModelNode transChildModel = child.getModel();
        Assert.assertEquals("test2", transChildModel.get("attr2").asString());
        Resource grandChild = child.getChild(PathElement.pathElement("grand", "one"));
        ModelNode transGrandChildModel = grandChild.getModel();
        Assert.assertEquals("test3", transGrandChildModel.get("attr3").asString());
    }

    @Test
    public void testResourceChildrenWithOneTransformerInChain() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V2_0_0);
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test1", "test11"), "attr1")
                .end()
            .addChildResource(PathElement.pathElement("child", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test2", "test21"), "attr2")
                .end()
            .addChildResource(PathElement.pathElement("grand", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test3", "test31"), "attr3");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0).get(V1_0_0), transformersSubRegistration);
        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource childResource = addChild(toto, "child", "one");
            childResource.getModel().get("attr2").set("test2");
            Resource grandChildResource = addChild(childResource, "grand", "one");
            grandChildResource.getModel().get("attr3").set("test3");
            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            final ModelNode model = toto.getModel();
            Assert.assertEquals("test11", model.get("attr1").asString());
            Resource child = toto.getChild(PathElement.pathElement("child", "one"));
            ModelNode transChildModel = child.getModel();
            Assert.assertEquals("test21", transChildModel.get("attr2").asString());
            Resource grandChild = child.getChild(PathElement.pathElement("grand", "one"));
            ModelNode transGrandChildModel = grandChild.getModel();
            Assert.assertEquals("test31", transGrandChildModel.get("attr3").asString());
        }
    }

    @Test
    public void testResourceNoChildrenChainedTransformation() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        chainedBuilder.createBuilder(V4_0_0, V3_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test1", "test11"), "attr1")
                .end();
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test11", "test111"), "attr1")
                .end();
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test111", "test1111"), "attr1")
                .end();

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V3_0_0, V2_0_0).get(V1_0_0), transformersSubRegistration);

        for(ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            final ModelNode model = toto.getModel();
            Assert.assertEquals("test1111", model.get("attr1").asString());
        }
    }


    @Test
    public void testResourceChildrenChainedTransformation() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        builder.getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test1", "test11"), "attr1")
                .end()
            .addChildResource(PathElement.pathElement("child", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test2", "test21"), "attr2")
                 .end()
            .addChildResource(PathElement.pathElement("grand", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test3", "test31"), "attr3");
        builder.addChildResource(PathElement.pathElement("child", "two"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test4", "test41"), "attr4")
                .end();

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        builder.getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test11", "test111"), "attr1")
                .end()
            .addChildResource(PathElement.pathElement("child", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test21", "test211"), "attr2")
                .end()
            .addChildResource(PathElement.pathElement("grand", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test31", "test311"), "attr3");
        builder.addChildResource(PathElement.pathElement("child", "two"))
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("test41", "test411"), "attr4")
            .end();

        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0);
        builder.getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test111", "test1111"), "attr1")
                .end()
            .addChildResource(PathElement.pathElement("child", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test211", "test2111"), "attr2")
                .end()
            .addChildResource(PathElement.pathElement("grand", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test311", "test3111"), "attr3");
        builder.addChildResource(PathElement.pathElement("child", "two"))
            .getAttributeBuilder()
                .setValueConverter(new SimpleAttributeConverter("test411", "test4111"), "attr4")
                .end();

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V3_0_0, V2_0_0).get(V1_0_0), transformersSubRegistration);
        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource child1Resource = addChild(toto, "child", "one");
            child1Resource.getModel().get("attr2").set("test2");
            Resource grandChildResource = addChild(child1Resource, "grand", "one");
            grandChildResource.getModel().get("attr3").set("test3");
            toto.removeChild(PathElement.pathElement("child", "two"));
            Resource child2Resource = addChild(toto, "child", "two");
            child2Resource.getModel().get("attr4").set("test4");
            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            final ModelNode model = toto.getModel();
            Assert.assertEquals("test1111", model.get("attr1").asString());
            Resource child1 = toto.getChild(PathElement.pathElement("child", "one"));
            ModelNode transChild1Model = child1.getModel();
            Assert.assertEquals("test2111", transChild1Model.get("attr2").asString());
            Resource grandChild = child1.getChild(PathElement.pathElement("grand", "one"));
            ModelNode transGrandChildModel = grandChild.getModel();
            Assert.assertEquals("test3111", transGrandChildModel.get("attr3").asString());
            Resource child2 = toto.getChild(PathElement.pathElement("child", "two"));
            ModelNode transChild2Model = child2.getModel();
            Assert.assertEquals("test4111", transChild2Model.get("attr4").asString());
        }
    }

    @Test
    public void testResourceChildrenChainedTransformationNonUniform() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test1", "test11"), "attr1")
                .end()
            .addChildResource(PathElement.pathElement("child", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test2", "test21"), "attr2")
                 .end()
            .addChildResource(PathElement.pathElement("grand", "one"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test3", "test31"), "attr3");
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("test11", "test111"), "attr1");

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V3_0_0, V2_0_0).get(V1_0_0), transformersSubRegistration);

        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource childResource = addChild(toto, "child", "one");
            childResource.getModel().get("attr2").set("test2");
            Resource grandChildResource = addChild(childResource, "grand", "one");
            grandChildResource.getModel().get("attr3").set("test3");
            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            final ModelNode model = toto.getModel();
            Assert.assertEquals("test111", model.get("attr1").asString());
            Resource child = toto.getChild(PathElement.pathElement("child", "one"));
            ModelNode transChildModel = child.getModel();
            Assert.assertEquals("test21", transChildModel.get("attr2").asString());
            Resource grandChild = child.getChild(PathElement.pathElement("grand", "one"));
            ModelNode transGrandChildModel = grandChild.getModel();
            Assert.assertEquals("test31", transGrandChildModel.get("attr3").asString());
        }
    }

    @Test
    public void testDiscardResources() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        builder.discardChildResource(PathElement.pathElement("child", "one"));
        builder.addChildResource(PathElement.pathElement("child", "two")).discardChildResource(PathElement.pathElement("grand", "C"));

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        builder.discardChildResource(PathElement.pathElement("child", "three"));


        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0);
        builder.discardChildResource(PathElement.pathElement("child", "four"));
        builder.addChildResource(PathElement.pathElement("child", "five")).discardChildResource(PathElement.pathElement("grand", "J"));


        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V3_0_0, V2_0_0).get(V1_0_0), transformersSubRegistration);

        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource child1Resource = addChild(toto, "child", "one");
            child1Resource.getModel().get("attr2").set("test2");
            toto.removeChild(PathElement.pathElement("child", "two"));
            Resource child2Resource = addChild(toto, "child", "two");
            child2Resource.getModel().get("attr4").set("test4");
            toto.removeChild(PathElement.pathElement("child", "three"));
            Resource child3Resource = addChild(toto, "child", "three");
            child3Resource.getModel().get("attr5").set("test5");
            toto.removeChild(PathElement.pathElement("child", "four"));
            Resource child4Resource = addChild(toto, "child", "four");
            child4Resource.getModel().get("attr5").set("test5");
            toto.removeChild(PathElement.pathElement("child", "five"));
            Resource child5Resource = addChild(toto, "child", "five");
            child5Resource.getModel().get("attr6").set("test6");
            Resource grandChild1AResource = addChild(child1Resource, "grand", "A");
            grandChild1AResource.getModel().get("attrA").set("testA");
            Resource grandChild1BResource = addChild(child1Resource, "grand", "B");
            grandChild1BResource.getModel().get("attrB").set("testB");
            Resource grandChild2CResource = addChild(child2Resource, "grand", "C");
            grandChild2CResource.getModel().get("attrC").set("testC");
            Resource grandChild2DResource = addChild(child2Resource, "grand", "D");
            grandChild2DResource.getModel().get("attrD").set("testD");
            Resource grandChild3EResource = addChild(child3Resource, "grand", "E");
            grandChild3EResource.getModel().get("attrE").set("testE");
            Resource grandChild3FResource = addChild(child3Resource, "grand", "F");
            grandChild3FResource.getModel().get("attrF").set("testF");
            Resource grandChild4GResource = addChild(child4Resource, "grand", "G");
            grandChild4GResource.getModel().get("attrG").set("testG");
            Resource grandChild4HResource = addChild(child4Resource, "grand", "H");
            grandChild4HResource.getModel().get("attrH").set("testH");
            Resource grandChild5IResource = addChild(child5Resource, "grand", "I");
            grandChild5IResource.getModel().get("attrI").set("testI");
            Resource grandChild5JResource = addChild(child5Resource, "grand", "J");
            grandChild5JResource.getModel().get("attrJ").set("testJ");

            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            ModelNode model = toto.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test1", model.get("attr1").asString());

            Set<String> types = toto.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("child"));
            Set<String> names = toto.getChildrenNames("child");
            Assert.assertEquals(2, names.size());
            Assert.assertTrue(names.contains("two"));
            Assert.assertTrue(names.contains("five"));

            Resource childTwo = toto.getChild(PathElement.pathElement("child", "two"));
            Assert.assertNotNull(childTwo);
            model = childTwo.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test4", model.get("attr4").asString());
            types = childTwo.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("grand"));
            names = childTwo.getChildrenNames("grand");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("D"));
            Resource grandTwoD = childTwo.getChild(PathElement.pathElement("grand", "D"));
            Assert.assertNotNull(grandTwoD);
            model = grandTwoD.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testD", model.get("attrD").asString());

            Resource childFive = toto.getChild(PathElement.pathElement("child", "five"));
            Assert.assertNotNull(childFive);
            model = childFive.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test6", model.get("attr6").asString());
            types = childFive.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("grand"));
            names = childFive.getChildrenNames("grand");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("I"));
            Resource grandFiveI = childFive.getChild(PathElement.pathElement("grand", "I"));
            Assert.assertNotNull(grandFiveI);
            model = grandFiveI.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testI", model.get("attrI").asString());
        }
    }

    @Test
    public void testRejectResources() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        builder.rejectChildResource(PathElement.pathElement("child", "one"));
        builder.addChildResource(PathElement.pathElement("child", "two")).rejectChildResource(PathElement.pathElement("grand", "C"));

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        builder.rejectChildResource(PathElement.pathElement("child", "three"));

        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0);
        builder.rejectChildResource(PathElement.pathElement("child", "four"));
        builder.addChildResource(PathElement.pathElement("child", "five")).rejectChildResource(PathElement.pathElement("grand", "J"));

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V3_0_0, V2_0_0).get(V1_0_0), transformersSubRegistration);

        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource child1Resource = addChild(toto, "child", "one");
            child1Resource.getModel().get("attr2").set("test2");
            toto.removeChild(PathElement.pathElement("child", "two"));
            Resource child2Resource = addChild(toto, "child", "two");
            child2Resource.getModel().get("attr4").set("test4");
            toto.removeChild(PathElement.pathElement("child", "three"));
            Resource child3Resource = addChild(toto, "child", "three");
            child3Resource.getModel().get("attr5").set("test5");
            toto.removeChild(PathElement.pathElement("child", "four"));
            Resource child4Resource = addChild(toto, "child", "four");
            child4Resource.getModel().get("attr5").set("test5");
            toto.removeChild(PathElement.pathElement("child", "five"));
            Resource child5Resource = addChild(toto, "child", "five");
            child5Resource.getModel().get("attr6").set("test6");
            Resource grandChild1AResource = addChild(child1Resource, "grand", "A");
            grandChild1AResource.getModel().get("attrA").set("testA");
            Resource grandChild1BResource = addChild(child1Resource, "grand", "B");
            grandChild1BResource.getModel().get("attrB").set("testB");
            Resource grandChild2CResource = addChild(child2Resource, "grand", "C");
            grandChild2CResource.getModel().get("attrC").set("testC");
            Resource grandChild2DResource = addChild(child2Resource, "grand", "D");
            grandChild2DResource.getModel().get("attrD").set("testD");
            Resource grandChild3EResource = addChild(child3Resource, "grand", "E");
            grandChild3EResource.getModel().get("attrE").set("testE");
            Resource grandChild3FResource = addChild(child3Resource, "grand", "F");
            grandChild3FResource.getModel().get("attrF").set("testF");
            Resource grandChild4GResource = addChild(child4Resource, "grand", "G");
            grandChild4GResource.getModel().get("attrG").set("testG");
            Resource grandChild4HResource = addChild(child4Resource, "grand", "H");
            grandChild4HResource.getModel().get("attrH").set("testH");
            Resource grandChild5IResource = addChild(child5Resource, "grand", "I");
            grandChild5IResource.getModel().get("attrI").set("testI");
            Resource grandChild5JResource = addChild(child5Resource, "grand", "J");
            grandChild5JResource.getModel().get("attrJ").set("testJ");
            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            ModelNode model = toto.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test1", model.get("attr1").asString());

            Set<String> types = toto.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("child"));
            Set<String> names = toto.getChildrenNames("child");
            Assert.assertEquals(2, names.size());
            Assert.assertTrue(names.contains("two"));
            Assert.assertTrue(names.contains("five"));

            Resource childTwo = toto.getChild(PathElement.pathElement("child", "two"));
            Assert.assertNotNull(childTwo);
            model = childTwo.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test4", model.get("attr4").asString());
            types = childTwo.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("grand"));
            names = childTwo.getChildrenNames("grand");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("D"));
            Resource grandTwoD = childTwo.getChild(PathElement.pathElement("grand", "D"));
            Assert.assertNotNull(grandTwoD);
            model = grandTwoD.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testD", model.get("attrD").asString());

            Resource childFive = toto.getChild(PathElement.pathElement("child", "five"));
            Assert.assertNotNull(childFive);
            model = childFive.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test6", model.get("attr6").asString());
            types = childFive.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("grand"));
            names = childFive.getChildrenNames("grand");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("I"));
            Resource grandFiveI = childFive.getChild(PathElement.pathElement("grand", "I"));
            Assert.assertNotNull(grandFiveI);
            model = grandFiveI.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testI", model.get("attrI").asString());
        }
    }

    @Test
    public void testResourcePathAddressTransformationOneInChainTransformTopOnly() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V2_0_0);
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .addChildRedirection(PathElement.pathElement("child", "one"), PathElement.pathElement("chico", "uno"));

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0).get(V1_0_0), transformersSubRegistration);

        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource childResource = addChild(toto, "child", "one");
            childResource.getModel().get("attr2").set("test2");
            Resource grandChildResource = addChild(childResource, "grandchild", "one");
            grandChildResource.getModel().get("attr3").set("test3");
            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);

            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            ModelNode model = toto.getModel();
            Assert.assertEquals("test1", model.get("attr1").asString());
            Set<String> types = toto.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("chico"));
            Set<String> names = toto.getChildrenNames("chico");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("uno"));

            Resource child = toto.getChild(PathElement.pathElement("chico", "uno"));
            model = child.getModel();
            Assert.assertEquals("test2", model.get("attr2").asString());
            types = child.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("grandchild"));
            names = child.getChildrenNames("grandchild");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("one"));

            Resource grand = child.getChild(PathElement.pathElement("grandchild", "one"));
            model = grand.getModel();
            Assert.assertEquals("test3", model.get("attr3").asString());
        }
    }

    @Test
    public void testResourcePathAddressTransformationOneInChainTransformBottomOnly() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V2_0_0);
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .addChildResource(PathElement.pathElement("child", "one"))
                .addChildRedirection(PathElement.pathElement("grandchild", "one"), PathElement.pathElement("nieto", "uno"));

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0).get(V1_0_0), transformersSubRegistration);

        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource childResource = addChild(toto, "child", "one");
            childResource.getModel().get("attr2").set("test2");
            Resource grandChildResource = addChild(childResource, "grandchild", "one");
            grandChildResource.getModel().get("attr3").set("test3");

            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            ModelNode model = toto.getModel();
            Assert.assertEquals("test1", model.get("attr1").asString());
            Set<String> types = toto.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("child"));
            Set<String> names = toto.getChildrenNames("child");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("one"));

            Resource child = toto.getChild(PathElement.pathElement("child", "one"));
            model = child.getModel();
            Assert.assertEquals("test2", model.get("attr2").asString());
            types = child.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("nieto"));
            names = child.getChildrenNames("nieto");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("uno"));

            Resource grand = child.getChild(PathElement.pathElement("nieto", "uno"));
            model = grand.getModel();
            Assert.assertEquals("test3", model.get("attr3").asString());
        }
    }

    @Test
    public void testResourcePathAddressTransformationOneInChainTransformAll() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V2_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .addChildRedirection(PathElement.pathElement("child", "one"), PathElement.pathElement("chico", "uno"));
        builder.addChildRedirection(PathElement.pathElement("grandchild", "one"), PathElement.pathElement("nieto", "uno"));

        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0).get(V1_0_0), transformersSubRegistration);

        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource childResource = addChild(toto, "child", "one");
            childResource.getModel().get("attr2").set("test2");
            Resource grandChildResource = addChild(childResource, "grandchild", "one");
            grandChildResource.getModel().get("attr3").set("test3");

            final Resource resource = transformResource(version);
            Assert.assertNotNull(resource);

            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            ModelNode model = toto.getModel();
            Assert.assertEquals("test1", model.get("attr1").asString());
            Set<String> types = toto.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("chico"));
            Set<String> names = toto.getChildrenNames("chico");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("uno"));

            Resource child = toto.getChild(PathElement.pathElement("chico", "uno"));
            model = child.getModel();
            Assert.assertEquals("test2", model.get("attr2").asString());
            types = child.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("nieto"));
            names = child.getChildrenNames("nieto");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("uno"));

            Resource grand = child.getChild(PathElement.pathElement("nieto", "uno"));
            model = grand.getModel();
            Assert.assertEquals("test3", model.get("attr3").asString());
        }
    }

    @Test
    public void testResourcePathAddressTransformationChain() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        ResourceTransformationDescriptionBuilder childBuilder = builder.addChildRedirection(PathElement.pathElement("child", "two"), PathElement.pathElement("kind", "second"));
        childBuilder.addChildRedirection(PathElement.pathElement("grand", "C"), PathElement.pathElement("nieto", "Ceh"));
        childBuilder.addChildRedirection(PathElement.pathElement("grand", "D"), PathElement.pathElement("enkel", "Deh"));
        builder.addChildResource(PathElement.pathElement("child", "three"))
            .addChildRedirection(PathElement.pathElement("grand", "E"), PathElement.pathElement("nieto", "Eh"));

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        childBuilder = builder.addChildRedirection(PathElement.pathElement("kind", "second"), PathElement.pathElement("thing", "2"));
        childBuilder.addChildRedirection(PathElement.pathElement("nieto", "Ceh"), PathElement.pathElement("nieto", "C!"));
        childBuilder.addChildRedirection(PathElement.pathElement("enkel", "Deh"), PathElement.pathElement("enkel", "D!"));
        builder.addChildResource(PathElement.pathElement("child", "three"))
            .addChildRedirection(PathElement.pathElement("nieto", "Eh"), PathElement.pathElement("enkel", "Eh!"));

        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0);
        childBuilder = builder.addChildRedirection(PathElement.pathElement("thing", "2"), PathElement.pathElement("thechild", "TWO"));
        childBuilder.addChildRedirection(PathElement.pathElement("nieto", "C!"), PathElement.pathElement("GRAND", "Cee"));
        childBuilder.addChildRedirection(PathElement.pathElement("enkel", "D!"), PathElement.pathElement("GRAND", "Dee"));
        builder.addChildResource(PathElement.pathElement("child", "three"))
            .addChildRedirection(PathElement.pathElement("enkel", "Eh!"), PathElement.pathElement("GRAND", "Eee"));


        TransformationDescription.Tools.register(chainedBuilder.build(V3_0_0, V2_0_0, V1_0_0).get(V1_0_0), transformersSubRegistration);
        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource child1Resource = addChild(toto, "child", "one");
            child1Resource.getModel().get("attr2").set("test2");
            toto.removeChild(PathElement.pathElement("child", "two"));
            Resource child2Resource = addChild(toto, "child", "two");
            child2Resource.getModel().get("attr4").set("test4");
            toto.removeChild(PathElement.pathElement("child", "three"));
            Resource child3Resource = addChild(toto, "child", "three");
            child3Resource.getModel().get("attr5").set("test5");
            Resource grandChild1AResource = addChild(child1Resource, "grand", "A");
            grandChild1AResource.getModel().get("attrA").set("testA");
            Resource grandChild1BResource = addChild(child1Resource, "grand", "B");
            grandChild1BResource.getModel().get("attrB").set("testB");
            Resource grandChild2CResource = addChild(child2Resource, "grand", "C");
            grandChild2CResource.getModel().get("attrC").set("testC");
            Resource grandChild2DResource = addChild(child2Resource, "grand", "D");
            grandChild2DResource.getModel().get("attrD").set("testD");
            Resource grandChild3EResource = addChild(child3Resource, "grand", "E");
            grandChild3EResource.getModel().get("attrE").set("testE");
            Resource grandChild3FResource = addChild(child3Resource, "grand", "F");
            grandChild3FResource.getModel().get("attrF").set("testF");

            final Resource resource = transformResource(version);
            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            ModelNode model = toto.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test1", model.get("attr1").asString());

            Set<String> types = toto.getChildTypes();
            Assert.assertEquals(2, types.size());
            Assert.assertTrue(types.contains("child"));
            Assert.assertTrue(types.contains("thechild"));

            Set<String> names = toto.getChildrenNames("child");
            Assert.assertEquals(2, names.size());
            Assert.assertTrue(names.contains("one"));
            Assert.assertTrue(names.contains("three"));

            Resource childOne = toto.getChild(PathElement.pathElement("child", "one"));
            Assert.assertNotNull(childOne);
            model = childOne.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test2", model.get("attr2").asString());
            types = childOne.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("grand"));
            names = childOne.getChildrenNames("grand");
            Assert.assertEquals(2, names.size());
            Assert.assertTrue(names.contains("A"));
            Assert.assertTrue(names.contains("B"));
            Resource grandA = childOne.getChild(PathElement.pathElement("grand", "A"));
            Assert.assertNotNull(grandA);
            model = grandA.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testA", model.get("attrA").asString());
            Resource grandB = childOne.getChild(PathElement.pathElement("grand", "B"));
            Assert.assertNotNull(grandB);
            model = grandB.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testB", model.get("attrB").asString());

            Resource childThree = toto.getChild(PathElement.pathElement("child", "three"));
            Assert.assertNotNull(childThree);
            model = childThree.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test5", model.get("attr5").asString());
            types = childThree.getChildTypes();
            Assert.assertEquals(2, types.size());
            Assert.assertTrue(types.contains("grand"));
            Assert.assertTrue(types.contains("GRAND"));
            names = childThree.getChildrenNames("GRAND");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("Eee"));
            Resource grandE = childThree.getChild(PathElement.pathElement("GRAND", "Eee"));
            Assert.assertNotNull(grandE);
            model = grandE.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testE", model.get("attrE").asString());
            names = childThree.getChildrenNames("grand");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("F"));
            Resource grandF = childThree.getChild(PathElement.pathElement("grand", "F"));
            Assert.assertNotNull(grandF);
            model = grandF.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testF", model.get("attrF").asString());

            names = toto.getChildrenNames("thechild");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("TWO"));

            Resource childTwo = toto.getChild(PathElement.pathElement("thechild", "TWO"));
            Assert.assertNotNull(childTwo);
            model = childTwo.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("test4", model.get("attr4").asString());
            types = childTwo.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("GRAND"));
            names = childTwo.getChildrenNames("GRAND");
            Assert.assertEquals(2, names.size());
            Assert.assertTrue(names.contains("Cee"));
            Assert.assertTrue(names.contains("Dee"));
            Resource grandC = childTwo.getChild(PathElement.pathElement("GRAND", "Cee"));
            Assert.assertNotNull(grandC);
            model = grandC.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testC", model.get("attrC").asString());
            Resource grandD = childTwo.getChild(PathElement.pathElement("GRAND", "Dee"));
            Assert.assertNotNull(grandD);
            model = grandD.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("testD", model.get("attrD").asString());
        }
    }

    @Test
    public void testCustomResourceTransformerChain() throws Exception {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedInstance(PATH, V4_0_0);
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(V4_0_0, V3_0_0);
        builder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("test1", "test11"), "attr1");
        ResourceTransformationDescriptionBuilder childBuilder = builder.addChildResource(PathElement.pathElement("child", "one"));
        childBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("test2", "test21"), "attr2");
        childBuilder.addChildResource(PathElement.pathElement("grand", "A")).getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("testA", "testA1"), "attrA");
        childBuilder.addChildResource(PathElement.pathElement("grand", "B")).getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("testB", "testB1"), "attrB");
        builder.setCustomResourceTransformer(new ResourceTransformer() {
            @Override
            public void transformResource(ResourceTransformationContext context, PathAddress address, Resource resource)
                    throws OperationFailedException {

                Assert.assertEquals("test11", resource.getModel().get("attr1").asString());
                //The children have not been processed yet, so the child builder rules have not kicked in yet
                Resource child = resource.getChild(PathElement.pathElement("child", "one"));
                Assert.assertNotNull(child);
                Assert.assertEquals("test2", child.getModel().get("attr2").asString());
                Resource grand = child.getChild(PathElement.pathElement("grand", "A"));
                Assert.assertNotNull(grand);
                Assert.assertEquals("testA", grand.getModel().get("attrA").asString());
                grand = child.getChild(PathElement.pathElement("grand", "B"));
                Assert.assertNotNull(grand);
                Assert.assertEquals("testB", grand.getModel().get("attrB").asString());

                //Now change the value in the current resource
                Resource copy = Resource.Factory.create();
                copy.getModel().get("first-attr").set(resource.getModel().get("attr1"));

                //Process the children, this will cause the rules from the child builders to work
                final ResourceTransformationContext childContext = context.addTransformedResource(PathAddress.EMPTY_ADDRESS, copy);
                childContext.processChildren(resource);
            }
        });

        builder = chainedBuilder.createBuilder(V3_0_0, V2_0_0);
        //This will work, this happens before the resource transformer
        builder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("test11", "testone"), "first-attr");
        //These will get ignored since the resource transformer happens after the parent resource, and does not process the children normally
        //Do one set for the original names
        childBuilder = builder.addChildResource(PathElement.pathElement("child", "one"));
        childBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("not-called", "doesn't matter"), "whatever");
        childBuilder.addChildResource(PathElement.pathElement("grand", "A")).getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("not-called", "doesn't matter"), "whatever");
        childBuilder.addChildResource(PathElement.pathElement("grand", "B")).getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("not-called", "doesn't matter"), "whatever");
        //One set for the new names set by the custom transformer
        childBuilder = builder.addChildResource(PathElement.pathElement("childie", "1"));
        childBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("not-called", "doesn't matter"), "whatever");
        childBuilder.addChildResource(PathElement.pathElement("grandie", "A")).getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("not-called", "doesn't matter"), "whatever");
        childBuilder.addChildResource(PathElement.pathElement("grandie", "B")).getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("not-called", "doesn't matter"), "whatever");
        builder.setCustomResourceTransformer(new ResourceTransformer() {
            @Override
            public void transformResource(ResourceTransformationContext context, PathAddress address, Resource resource)
                    throws OperationFailedException {
                Assert.assertEquals("testone", resource.getModel().get("first-attr").asString());
                //Again the children have not been processed yet, so the child builder rules have not kicked in yet
                Resource child = resource.getChild(PathElement.pathElement("child", "one"));
                Assert.assertNotNull(child);
                Assert.assertEquals("test21", child.getModel().get("attr2").asString());
                Resource grand = child.getChild(PathElement.pathElement("grand", "A"));
                Assert.assertNotNull(grand);
                Assert.assertEquals("testA1", grand.getModel().get("attrA").asString());
                grand = child.getChild(PathElement.pathElement("grand", "B"));
                Assert.assertNotNull(grand);
                Assert.assertEquals("testB1", grand.getModel().get("attrB").asString());

                //Just copy across the resource and add a value
                Resource copy = Resource.Factory.create();
                copy.getModel().set(resource.getModel());
                copy.getModel().get("first-attr").set(resource.getModel().get("first-attr"));

                //Process the children, renaming them
                //NOTE that this will rename the children in the active model, but we don't end up processing the child resources,
                //meaning that the description transformers for the child resources do not get called.
                //ResourceTransformers are a bit confusing, in that they look like they can handle everything underneath them,
                //In reality a PathAddressTransformer should be used for renaming, and there should be a ResourceTranformer at each level
                //where needed. In short what I am doing below is BAD :-)
                ResourceTransformationContext childContext = context.addTransformedResource(PathAddress.EMPTY_ADDRESS, copy);

                copy = Resource.Factory.create();
                copy.getModel().get("attrTwo").set("resource2");
                childContext = childContext.addTransformedResource(PathAddress.pathAddress(PathElement.pathElement("childie", "1")), copy);

                copy = Resource.Factory.create();
                copy.getModel().get("attrAy").set("resourceA");
                childContext.addTransformedResource(PathAddress.pathAddress(PathElement.pathElement("grandie", "A")), copy);

                copy = Resource.Factory.create();
                copy.getModel().get("attrBee").set("resourceB");
                childContext.addTransformedResource(PathAddress.pathAddress(PathElement.pathElement("grandie", "B")), copy);

            }
        });


        builder = chainedBuilder.createBuilder(V2_0_0, V1_0_0);
        builder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("testone", "value-1"), "first-attr");
        childBuilder = builder.addChildResource(PathElement.pathElement("childie", "1"));
        childBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("resource2", "value-2"), "attrTwo");
        ResourceTransformationDescriptionBuilder grandBuilder = childBuilder.addChildRedirection(PathElement.pathElement("grandie", "A"), PathElement.pathElement("grandchild", "A"));
        grandBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("resourceA", "valueA"), "attrAy");
        grandBuilder.setCustomResourceTransformer(new ResourceTransformer() {
            @Override
            public void transformResource(ResourceTransformationContext context, PathAddress address, Resource resource)
                    throws OperationFailedException {
                Assert.assertEquals("valueA", resource.getModel().get("attrAy").asString());
                Resource copy = Resource.Factory.create();
                copy.getModel().get("attributeA").set("value-A");
                ResourceTransformationContext childContext = context.addTransformedResource(PathAddress.EMPTY_ADDRESS, copy);
                childContext.processChildren(resource);
            }
        });
        grandBuilder = childBuilder.addChildResource(PathElement.pathElement("grandie", "B"));
        grandBuilder.getAttributeBuilder().setValueConverter(new SimpleAttributeConverter("resourceB", "valueB"), "attrBee");
        grandBuilder.setCustomResourceTransformer(new ResourceTransformer() {
            @Override
            public void transformResource(ResourceTransformationContext context, PathAddress address, Resource resource)
                    throws OperationFailedException {
                Assert.assertEquals("valueB", resource.getModel().get("attrBee").asString());
                Resource copy = Resource.Factory.create();
                copy.getModel().get("attributeB").set("value-B");
                ResourceTransformationContext childContext = context.addTransformedResource(PathAddress.EMPTY_ADDRESS, copy);
                childContext.processChildren(resource);
            }
        });

        TransformationDescription.Tools.register(chainedBuilder.build(V3_0_0, V2_0_0, V1_0_0).get(V1_0_0), transformersSubRegistration);
        for (ModelVersion version : VALID_TESTED_VERSIONS) {
            //Set up the model
            resourceModel.get("attr1").set("test1");
            toto.removeChild(PathElement.pathElement("child", "one"));
            Resource child1Resource = addChild(toto, "child", "one");
            child1Resource.getModel().get("attr2").set("test2");
            Resource grandChild1AResource = addChild(child1Resource, "grand", "A");
            grandChild1AResource.getModel().get("attrA").set("testA");
            Resource grandChild1BResource = addChild(child1Resource, "grand", "B");
            grandChild1BResource.getModel().get("attrB").set("testB");

            final Resource resource = transformResource(version);

            final Resource toto = resource.getChild(PATH);
            Assert.assertNotNull(toto);
            ModelNode model = toto.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("value-1", model.get("first-attr").asString());

            Set<String> types = toto.getChildTypes();
            Assert.assertEquals(1, types.size());
            Assert.assertTrue(types.contains("childie"));
            Set<String> names = toto.getChildrenNames("childie");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("1"));

            Resource child = toto.getChild(PathElement.pathElement("childie", "1"));
            model = child.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("value-2", model.get("attrTwo").asString());

            types = child.getChildTypes();
            Assert.assertEquals(2, types.size());
            Assert.assertTrue(types.contains("grandchild"));
            Assert.assertTrue(types.contains("grandie"));
            names = child.getChildrenNames("grandchild");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("A"));
            names = child.getChildrenNames("grandie");
            Assert.assertEquals(1, names.size());
            Assert.assertTrue(names.contains("B"));

            Resource grand = child.getChild(PathElement.pathElement("grandchild", "A"));
            model = grand.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("value-A", model.get("attributeA").asString());

            grand = child.getChild(PathElement.pathElement("grandie", "B"));
            model = grand.getModel();
            Assert.assertEquals(1, model.keys().size());
            Assert.assertEquals("value-B", model.get("attributeB").asString());
        }
    }

    @Test
    public void testChainedTransformersWhenParentAddressChanges() throws Exception {
        TransformersSubRegistration subsystemReg = transformersSubRegistration.registerSubResource(PATH);
        TransformersSubRegistration leafOneReg = subsystemReg.registerSubResource(
                PathElement.pathElement("leaf-one", "one"),
                new PathAddressTransformer.ReplaceElementKey("one-leaf"),
                ResourceTransformer.DEFAULT, OperationTransformer.DEFAULT);
        TransformersSubRegistration leafTwoReg = subsystemReg.registerSubResource(
                PathElement.pathElement("leaf-two", "two"),
                new PathAddressTransformer.ReplaceElementKey("two-leaf"),
                ResourceTransformer.DEFAULT, OperationTransformer.DEFAULT);

        ChainedTransformationDescriptionBuilder chainedBuilder =
                TransformationDescriptionBuilder.Factory.createChainedInstance(PathElement.pathElement("chained", "one"), V3_0_0);
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
                .addChildRedirection(PathElement.pathElement("thing", "x"), PathElement.pathElement("Thing", "ex"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("Original", "One"), "attr1");
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .addChildRedirection(PathElement.pathElement("Thing", "ex"), PathElement.pathElement("THING", "X"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("One", "Two"), "attr1");
        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0).get(V1_0_0), leafOneReg);

        chainedBuilder =
                TransformationDescriptionBuilder.Factory.createChainedInstance(PathElement.pathElement("chained", "two"), V3_0_0);
        chainedBuilder.createBuilder(V3_0_0, V2_0_0)
                .addChildRedirection(PathElement.pathElement("thing", "y"), PathElement.pathElement("Thing", "why"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("Original", "Three"), "attr2");
        chainedBuilder.createBuilder(V2_0_0, V1_0_0)
                .addChildRedirection(PathElement.pathElement("Thing", "why"), PathElement.pathElement("THING", "Y"))
                .getAttributeBuilder()
                    .setValueConverter(new SimpleAttributeConverter("Three", "Four"), "attr2");
        TransformationDescription.Tools.register(chainedBuilder.build(V1_0_0, V2_0_0).get(V1_0_0), leafTwoReg);

        Resource leafOneResource = addChild(toto, "leaf-one", "one");
        Resource chainedOneResource = addChild(leafOneResource, "chained", "one");
        Resource thingXResource = addChild(chainedOneResource, "thing", "x");
        thingXResource.getModel().get("attr1").set("Original");

        Resource leafTwoResource = addChild(toto, "leaf-two", "two");
        Resource chainedTwoResource = addChild(leafTwoResource, "chained", "two");
        Resource thingYResource = addChild(chainedTwoResource, "thing", "y");
        thingYResource.getModel().get("attr2").set("Original");

        final Resource resource = transformResource(ModelVersion.create(1));

        Assert.assertEquals(1, resource.getChildTypes().size());
        Assert.assertEquals(1, resource.getChildrenNames("toto").size());
        Resource subsystem = resource.getChild(PathElement.pathElement("toto", "testSubsystem"));
        Assert.assertNotNull(subsystem);

        Assert.assertEquals(2, subsystem.getChildTypes().size());
        Assert.assertEquals(1, subsystem.getChildren("one-leaf").size());
        Resource oneLeaf = subsystem.getChild(PathElement.pathElement("one-leaf", "one"));
        Assert.assertNotNull(oneLeaf);
        Assert.assertEquals(1, oneLeaf.getChildTypes().size());
        Assert.assertEquals(1, oneLeaf.getChildren("chained").size());
        Resource chainedOne = oneLeaf.getChild(PathElement.pathElement("chained", "one"));
        Assert.assertNotNull(chainedOne);
        Assert.assertEquals(1, chainedOne.getChildTypes().size());
        Assert.assertEquals(1, chainedOne.getChildren("THING").size());
        Resource thingX = chainedOne.getChild(PathElement.pathElement("THING", "X"));
        Assert.assertNotNull(thingX);
        Assert.assertEquals(1, thingX.getModel().keys().size());
        Assert.assertEquals("Two", thingX.getModel().get("attr1").asString());

        Assert.assertEquals(1, subsystem.getChildren("two-leaf").size());
        Assert.assertEquals(1, subsystem.getChildren("two-leaf").size());
        Resource twoLeaf = subsystem.getChild(PathElement.pathElement("two-leaf", "two"));
        Assert.assertNotNull(twoLeaf);
        Assert.assertEquals(1, twoLeaf.getChildTypes().size());
        Assert.assertEquals(1, twoLeaf.getChildren("chained").size());
        Resource chainedTwo = twoLeaf.getChild(PathElement.pathElement("chained", "two"));
        Assert.assertNotNull(chainedTwo);
        Assert.assertEquals(1, chainedTwo.getChildTypes().size());
        Assert.assertEquals(1, chainedTwo.getChildren("THING").size());
        Resource thingY = chainedTwo.getChild(PathElement.pathElement("THING", "Y"));
        Assert.assertNotNull(thingY);
        Assert.assertEquals(1, thingY.getModel().keys().size());
        Assert.assertEquals("Four", thingY.getModel().get("attr2").asString());

    }

    private Resource addChild(Resource parent, String key, String value) {
        Resource resource = Resource.Factory.create();
        parent.registerChild(PathElement.pathElement(key, value), resource);
        return resource;
    }
    private Resource transformResource(ModelVersion version) throws OperationFailedException {
        final TransformationTarget target = create(registry, version);
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


    private static class SimpleAttributeConverter extends AttributeConverter.DefaultAttributeConverter {
        private final String expectedValue;
        private final String newValue;

        SimpleAttributeConverter(String expectedValue, String newValue) {
            this.expectedValue = expectedValue;
            this.newValue = newValue;
        }

        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            //System.out.println("-----> transform " + address + " " + attributeValue.asString() + " expected:" + expectedValue);
            Assert.assertEquals(expectedValue, attributeValue.asString());
            attributeValue.set(newValue);
        }
    }
}
