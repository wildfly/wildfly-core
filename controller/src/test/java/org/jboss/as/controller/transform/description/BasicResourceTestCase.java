/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationResultTransformer;
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
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(Parameterized.class)
public class BasicResourceTestCase {
    @Parameterized.Parameters
    public static Iterable<Stability> parameters() {
        return EnumSet.allOf(Stability.class);
    }

    private static final PathElement PATH = PathElement.pathElement("toto", "testSubsystem");
    private static final AttributeDefinition COMMUNITY_SUBSYSTEM_ATTRIBUTE = new SimpleAttributeDefinitionBuilder("community", ModelType.BOOLEAN).setRequired(false).setDefaultValue(ModelNode.TRUE).setStability(Stability.COMMUNITY).build();
    private static final Collection<ResourceRegistration> DISCARDED_RESOURCES = EnumSet.allOf(Stability.class).stream().map(stability -> ResourceRegistration.of(PathElement.pathElement(stability.name()), stability)).collect(Collectors.toList());
    private static final PathElement DISCARD = PathElement.pathElement("discard");
    private static final PathElement DYNAMIC = PathElement.pathElement("dynamic");
    private static final PathElement DYNAMIC_REDIRECT_ORIGINAL = PathElement.pathElement("dynamic-redirect-original");
    private static final PathElement DYNAMIC_REDIRECT_NEW = PathElement.pathElement("dynamic-redirect-new");
    private static final ResourceRegistration FOO_RESOURCE = ResourceRegistration.of(PathElement.pathElement("child", "foo"), Stability.COMMUNITY);
    private static final AttributeDefinition FOO_ATTRIBUTE = new SimpleAttributeDefinitionBuilder("preview-attribute", ModelType.BOOLEAN).setRequired(false).setDefaultValue(ModelNode.TRUE).setStability(Stability.PREVIEW).build();
    private static final ResourceRegistration FOO_BAR_RESOURCE = ResourceRegistration.of(PathElement.pathElement("grandchild", "bar"), Stability.PREVIEW);
    private static final AttributeDefinition FOO_BAR_ATTRIBUTE = new SimpleAttributeDefinitionBuilder("experimental-attribute", ModelType.BOOLEAN).setRequired(false).setDefaultValue(ModelNode.TRUE).setStability(Stability.EXPERIMENTAL).build();

    private static final PathElement CONFIGURATION_TEST = PathElement.pathElement("configuration", "test");
    private static final PathElement TEST_CONFIGURATION = PathElement.pathElement("test", "configuration");

    private static final PathElement SETTING_DIRECTORY = PathElement.pathElement("setting", "directory");
    private static final PathElement DIRECTORY_SETTING = PathElement.pathElement("directory", "setting");

    private final Resource resourceRoot = Resource.Factory.create();
    private final TransformerRegistry registry;
    private final ManagementResourceRegistration resourceRegistration;

    private final TransformationDescription description;

    public BasicResourceTestCase(Stability stability) {
        this.resourceRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER, stability).createRegistration(ROOT);
        this.registry = TransformerRegistry.Factory.create(stability);
        final ResourceTransformationDescriptionBuilder builder = this.registry.createResourceTransformationDescriptionBuilder(PATH);

        DISCARDED_RESOURCES.forEach(builder::discardChildResource);

        builder.getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(COMMUNITY_SUBSYSTEM_ATTRIBUTE.getDefaultValue()), COMMUNITY_SUBSYSTEM_ATTRIBUTE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, COMMUNITY_SUBSYSTEM_ATTRIBUTE)
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "test")
                .setValueConverter(AttributeConverter.Factory.createHardCoded(ModelNode.TRUE), "othertest")
                .end();

        // Create a child resource based on an attribute
        final ResourceTransformationDescriptionBuilder attrResourceBuilder = builder.addChildResource(PathElement.pathElement("attribute-resource"))
                .getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "test-resource")
                .end()
                .setCustomResourceTransformer(new ResourceTransformer() {
                    @Override
                    public void transformResource(final ResourceTransformationContext context, final PathAddress address, final Resource resource) throws OperationFailedException {
                        // Remote the test-resource attribute
                        final ModelNode model = resource.getModel();
                        final ModelNode testResource = model.remove("test-resource");
                        // Add the current resource
                        context.addTransformedResource(PathAddress.EMPTY_ADDRESS, resource);
                        // Use the test-resource attribute to create a child
                        final Resource child = Resource.Factory.create();
                        child.getModel().get("attribute").set(testResource);
                        context.addTransformedResource(PathAddress.EMPTY_ADDRESS.append(PathElement.pathElement("resource", "test")), child);
                        context.processChildren(resource);
                    }
                });

        attrResourceBuilder.addChildRedirection(PathElement.pathElement("resource-attribute"), new PathAddressTransformer() {
            @Override
            public PathAddress transform(PathElement current, Builder builder) {
                return builder.next(); // skip the current element
            }
        })
                .getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "test-attribute").end()
                .setCustomResourceTransformer(new ResourceTransformer() {
                    @Override
                    public void transformResource(ResourceTransformationContext context, PathAddress address, Resource resource) throws OperationFailedException {
                        // Get the current attribute
                        final ModelNode attribute = resource.getModel().get("test-attribute");
                        // Add it to the existing resource
                        final Resource existing = context.readTransformedResource(PathAddress.EMPTY_ADDRESS); //
                        final ModelNode model = existing.getModel();
                        model.get("test-attribute").set(attribute);

                    }
                });


        builder.addOperationTransformationOverride("test-operation")
                .setValueConverter(AttributeConverter.Factory.createHardCoded(ModelNode.TRUE), "operation-test")
                .inheritResourceAttributeDefinitions()
                .end();

        builder.addOperationTransformationOverride("rename-operation")
                .rename("new-name-op")
                .setValueConverter(AttributeConverter.Factory.createHardCoded(ModelNode.TRUE), "operation-test")
                .end();

        builder.addOperationTransformationOverride("operation-with-transformer")
                .setCustomOperationTransformer(new OperationTransformer() {
                    @Override
                    public TransformedOperation transformOperation(TransformationContext context, PathAddress address,
                                                                   ModelNode operation) throws OperationFailedException {
                        ModelNode remove = operation.clone();
                        remove.get(OP).set(REMOVE);
                        remove.remove("test");

                        ModelNode add = operation.clone();
                        add.get(OP).set(ADD);
                        add.get("new").set("shiny");

                        ModelNode composite = new ModelNode();
                        composite.get(OP).set(COMPOSITE);
                        composite.get(OP_ADDR).setEmptyList();
                        composite.get(STEPS).add(remove);
                        composite.get(STEPS).add(add);

                        return new TransformedOperation(composite, OperationResultTransformer.ORIGINAL_RESULT);
                    }
                });

        // Discard all
        builder.discardChildResource(DISCARD);

        builder.addChildResource(DYNAMIC, new TestDynamicDiscardPolicy())
                .getAttributeBuilder()
                .setValueConverter(new AttributeConverter.DefaultAttributeConverter() {
                    @Override
                    protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
                        attributeValue.set(attributeValue.asString().toUpperCase(Locale.ENGLISH));
                    }
                }, "attribute");

        builder.addChildRedirection(DYNAMIC_REDIRECT_ORIGINAL, DYNAMIC_REDIRECT_NEW, new TestDynamicDiscardPolicy())
                .getAttributeBuilder()
                .setValueConverter(new AttributeConverter.DefaultAttributeConverter() {
                    @Override
                    protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
                        attributeValue.set(attributeValue.asString().toUpperCase(Locale.ENGLISH));
                    }
                }, "attribute");



        // configuration=test/setting=directory > test=configuration/directory=setting
        builder.addChildRedirection(CONFIGURATION_TEST, TEST_CONFIGURATION)
                .getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, "test-config").end()
                .addChildRedirection(SETTING_DIRECTORY, DIRECTORY_SETTING);

        builder.addChildResource(FOO_RESOURCE).getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(FOO_ATTRIBUTE.getDefaultValue()), FOO_ATTRIBUTE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, FOO_ATTRIBUTE)
                .end()
                .addChildResource(FOO_BAR_RESOURCE).getAttributeBuilder()
                        .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(FOO_BAR_ATTRIBUTE.getDefaultValue()), FOO_BAR_ATTRIBUTE)
                        .addRejectCheck(RejectAttributeChecker.DEFINED, FOO_BAR_ATTRIBUTE)
                        .end();

        // Register at the server root
        this.description = builder.build();

        // test
        final Resource toto = Resource.Factory.create();
        toto.getModel().get("test").set("onetwothree");

        // discard
        final Resource discard = Resource.Factory.create();
        discard.getModel().get("attribute").set("two");
        toto.registerChild(PathElement.pathElement("discard", "one"), discard);

        //dynamic discard
        final Resource dynamicKeep = Resource.Factory.create();
        dynamicKeep.getModel().get("attribute").set("keep");
        toto.registerChild(PathElement.pathElement("dynamic", "keep"), dynamicKeep);
        final Resource dynamicReject = Resource.Factory.create();
        dynamicReject.getModel().get("attribute").set("reject");
        toto.registerChild(PathElement.pathElement("dynamic", "reject"), dynamicReject);
        final Resource dynamicDiscard = Resource.Factory.create();
        dynamicDiscard.getModel().get("attribute").set("discard");
        toto.registerChild(PathElement.pathElement("dynamic", "discard"), dynamicDiscard);

        //dynamic discard and child redirection
        final Resource dynamicRedirectKeep = Resource.Factory.create();
        dynamicRedirectKeep.getModel().get("attribute").set("keep");
        toto.registerChild(PathElement.pathElement("dynamic-redirect-original", "keep"), dynamicRedirectKeep);
        final Resource dynamicRedirectReject = Resource.Factory.create();
        dynamicRedirectReject.getModel().get("attribute").set("reject");
        toto.registerChild(PathElement.pathElement("dynamic-redirect-original", "reject"), dynamicRedirectReject);
        final Resource dynamicRedirectDiscard = Resource.Factory.create();
        dynamicRedirectDiscard.getModel().get("attribute").set("discard");
        toto.registerChild(PathElement.pathElement("dynamic-redirect-original", "discard"), dynamicRedirectDiscard);

        // configuration
        final Resource configuration = Resource.Factory.create();
        final Resource setting = Resource.Factory.create();
        configuration.registerChild(SETTING_DIRECTORY, setting);
        toto.registerChild(CONFIGURATION_TEST, configuration);

        // attribute-resource
        final Resource attrResource = Resource.Factory.create();
        attrResource.getModel().get("test-resource").set("abc");
        toto.registerChild(PathElement.pathElement("attribute-resource", "test"), attrResource);

        // resource-attribute
        final Resource resourceAttr = Resource.Factory.create();
        resourceAttr.getModel().get("test-attribute").set("test");
        attrResource.registerChild(PathElement.pathElement("resource-attribute", "test"), resourceAttr);

        for (ResourceRegistration discardedResource : DISCARDED_RESOURCES) {
            if (this.resourceRegistration.enables(discardedResource)) {
                toto.registerChild(PathElement.pathElement(discardedResource.getPathElement().getKey(), "discard"), Resource.Factory.create());
            }
        }

        if (this.resourceRegistration.enables(COMMUNITY_SUBSYSTEM_ATTRIBUTE)) {
            toto.getModel().get(COMMUNITY_SUBSYSTEM_ATTRIBUTE.getName()).set(COMMUNITY_SUBSYSTEM_ATTRIBUTE.getDefaultValue());
        }
        if (this.resourceRegistration.enables(FOO_RESOURCE)) {
            Resource foo = Resource.Factory.create();
            toto.registerChild(FOO_RESOURCE.getPathElement(), foo);
            if (this.resourceRegistration.enables(FOO_ATTRIBUTE)) {
                foo.getModel().get(FOO_ATTRIBUTE.getName()).set(FOO_ATTRIBUTE.getDefaultValue());
            }
            if (this.resourceRegistration.enables(FOO_BAR_RESOURCE)) {
                Resource bar = Resource.Factory.create();
                foo.registerChild(FOO_BAR_RESOURCE.getPathElement(), bar);
                if (this.resourceRegistration.enables(FOO_BAR_ATTRIBUTE)) {
                    bar.getModel().get(FOO_BAR_ATTRIBUTE.getName()).set(FOO_BAR_ATTRIBUTE.getDefaultValue());
                }
            }
        }

        resourceRoot.registerChild(PATH, toto);

        // Register the description
        final TransformersSubRegistration reg = registry.getServerRegistration(ModelVersion.create(1));
        TransformationDescription.Tools.register(description, reg);

    }

    @Test
    public void testResourceTransformation() throws Exception {

        final ModelNode address = new ModelNode();
        address.add("toto", "testSubsystem");

        final ModelNode node = new ModelNode();
        node.get(ModelDescriptionConstants.OP).set("add");
        node.get(ModelDescriptionConstants.OP_ADDR).set(address);

        final OperationTransformer.TransformedOperation op = transformOperation(ModelVersion.create(1), node);
        Assert.assertNotNull(op);

        final Resource resource = transformResource(ModelVersion.create(1));

        Assert.assertNotNull(resource);
        final Resource toto = resource.getChild(PATH);
        final ModelNode model = toto.getModel();
        Assert.assertNotNull(toto);
        Assert.assertFalse(toto.hasChild(PathElement.pathElement("discard", "one")));
        Assert.assertFalse(toto.hasChild(CONFIGURATION_TEST));

        Assert.assertFalse(toto.hasChild(PathElement.pathElement("dynamic", "discard")));
        Assert.assertFalse(toto.hasChild(PathElement.pathElement("dynamic", "reject")));
        Resource dynamicKeep = toto.getChild(PathElement.pathElement("dynamic", "keep"));
        Assert.assertEquals("KEEP", dynamicKeep.getModel().get("attribute").asString());

        Assert.assertFalse(toto.hasChildren("dynamic-redirect-original")); //Make sure that we didn't keep the originals
        Assert.assertFalse(toto.hasChild(PathElement.pathElement("dynamic-redirect-new", "discard")));
        Assert.assertFalse(toto.hasChild(PathElement.pathElement("dynamic-redirect-new", "reject")));
        Resource dynamicRedirectKeep = toto.getChild(PathElement.pathElement("dynamic-redirect-new", "keep"));
        Assert.assertEquals("KEEP", dynamicRedirectKeep.getModel().get("attribute").asString());

        final Resource attResource = toto.getChild(PathElement.pathElement("attribute-resource", "test"));
        Assert.assertNotNull(attResource);
        final ModelNode attResourceModel = attResource.getModel();
        Assert.assertFalse(attResourceModel.get("test-resource").isDefined());  // check that the resource got removed
        Assert.assertTrue(attResourceModel.hasDefined("test-attribute"));
        Assert.assertTrue(attResource.hasChild(PathElement.pathElement("resource", "test")));

        for (ResourceRegistration discardedResource : DISCARDED_RESOURCES) {
            if (this.resourceRegistration.enables(discardedResource)) {
                Assert.assertFalse(toto.hasChild(discardedResource.getPathElement()));
            }
        }
        if (this.resourceRegistration.enables(COMMUNITY_SUBSYSTEM_ATTRIBUTE)) {
            Assert.assertFalse(model.hasDefined(COMMUNITY_SUBSYSTEM_ATTRIBUTE.getName()));
        }
        if (this.resourceRegistration.enables(FOO_RESOURCE)) {
            Assert.assertTrue(toto.hasChild(FOO_RESOURCE.getPathElement()));
            Resource foo = toto.getChild(FOO_RESOURCE.getPathElement());
            if (this.resourceRegistration.enables(FOO_ATTRIBUTE)) {
                Assert.assertFalse(foo.getModel().hasDefined(FOO_ATTRIBUTE.getName()));
                if (this.resourceRegistration.enables(FOO_BAR_RESOURCE)) {
                    Assert.assertTrue(foo.hasChild(FOO_BAR_RESOURCE.getPathElement()));
                    Resource bar = foo.getChild(FOO_BAR_RESOURCE.getPathElement());
                    if (this.resourceRegistration.enables(FOO_BAR_ATTRIBUTE)) {
                        Assert.assertFalse(bar.getModel().hasDefined(FOO_BAR_ATTRIBUTE.getName()));
                    }
                }
            }
        }
    }

    @Test
    public void testAddOperation() throws Exception {

        final ModelNode address = new ModelNode();
        address.add("toto", "testSubsystem");

        final ModelNode node = new ModelNode();
        node.get(ModelDescriptionConstants.OP).set("add");
        node.get(ModelDescriptionConstants.OP_ADDR).set(address);
        node.get("test").set("${one:two}");

        OperationTransformer.TransformedOperation op = transformOperation(ModelVersion.create(1), node);
        Assert.assertTrue(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 1), node);
        Assert.assertTrue(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 5), node);
        Assert.assertTrue(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));

        node.get("test").set("concrete");
        op = transformOperation(ModelVersion.create(1), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 5), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));
    }

    @Test
    public void testWriteAttribute() throws Exception {

        final ModelNode address = new ModelNode();
        address.add("toto", "testSubsystem");

        final ModelNode node = new ModelNode();
        node.get(ModelDescriptionConstants.OP).set("write-attribute");
        node.get(ModelDescriptionConstants.OP_ADDR).set(address);
        node.get("name").set("test");
        node.get("value").set("${one:two}");

        OperationTransformer.TransformedOperation op = transformOperation(ModelVersion.create(1), node);
        Assert.assertTrue(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 1), node);
        Assert.assertTrue(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 5), node);
        Assert.assertTrue(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));

        node.get("value").set("test");
        op = transformOperation(ModelVersion.create(1), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 5), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));
    }

    @Test
    public void testAlias() throws Exception {

        final ModelNode address = new ModelNode();
        address.add("toto", "testSubsystem");
        address.add("configuration", "test");

        final ModelNode node = new ModelNode();
        node.get(ModelDescriptionConstants.OP).set("add");
        node.get(ModelDescriptionConstants.OP_ADDR).set(address);
        node.get("test-config").set("${one:two}");

        validateAliasTransformation(node.clone(), ModelVersion.create(1));
        validateAliasTransformation(node.clone(), ModelVersion.create(1, 0, 1));
        validateAliasTransformation(node.clone(), ModelVersion.create(1, 0, 5));

        OperationTransformer.TransformedOperation op = transformOperation(ModelVersion.create(1, 1),  node.clone());
        Assert.assertFalse(op.rejectOperation(success()));

        node.get("test-config").set("concrete");
        op = transformOperation(ModelVersion.create(1), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 0, 5), node);
        Assert.assertFalse(op.rejectOperation(success()));
        op = transformOperation(ModelVersion.create(1, 1), node);
        Assert.assertFalse(op.rejectOperation(success()));
    }

    private void validateAliasTransformation(final ModelNode node, final ModelVersion version) throws OperationFailedException, NoSuchElementException {
        OperationTransformer.TransformedOperation op = transformOperation(version,  node);
        Assert.assertTrue(op.rejectOperation(success()));
        PathAddress transformed = PathAddress.pathAddress(op.getTransformedOperation().require(OP_ADDR));
        Assert.assertEquals("test", transformed.getLastElement().getKey());
        Assert.assertEquals("configuration", transformed.getLastElement().getValue());
    }

    @Test
    public void testOperationOverride() throws Exception {

        final ModelNode address = new ModelNode();
        address.add("toto", "testSubsystem");

        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set("test-operation");
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
        operation.get("test").set("${one:two}");

        validateOperationOverride(operation, ModelVersion.create(1));
        validateOperationOverride(operation, ModelVersion.create(1, 0, 1));
        validateOperationOverride(operation, ModelVersion.create(1, 0, 5));

        OperationTransformer.TransformedOperation op = transformOperation(ModelVersion.create(1, 1), operation);
        Assert.assertFalse(op.rejectOperation(success())); // inherited
    }

    private void validateOperationOverride(final ModelNode operation, final ModelVersion version) throws IllegalArgumentException, OperationFailedException {
        OperationTransformer.TransformedOperation op = transformOperation(version, operation);
        ModelNode transformed = op.getTransformedOperation();
        Assert.assertTrue(transformed.get("operation-test").asBoolean()); // explicit
        Assert.assertTrue(transformed.get("othertest").asBoolean()); // inherited
        Assert.assertTrue(op.rejectOperation(success())); // inherited
    }

    @Test
    public void testOperationTransformer() throws Exception {

        final ModelNode address = new ModelNode();
        address.add("toto", "testSubsystem");

        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set("operation-with-transformer");
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
        operation.get("test").set("a");

        validateCompositeOperationTransformation(operation, ModelVersion.create(1));
        validateCompositeOperationTransformation(operation, ModelVersion.create(1, 0 , 1));
        validateCompositeOperationTransformation(operation, ModelVersion.create(1, 0 , 5));
    }

    private void validateCompositeOperationTransformation(final ModelNode operation, final ModelVersion version) throws OperationFailedException {
        OperationTransformer.TransformedOperation op = transformOperation(version, operation);
        final ModelNode transformed = op.getTransformedOperation();
        System.out.println(transformed);
        Assert.assertEquals(COMPOSITE, transformed.get(OP).asString());
        Assert.assertEquals(new ModelNode().setEmptyList(), transformed.get(OP_ADDR));
        Assert.assertEquals(ModelType.LIST, transformed.get(STEPS).getType());
        Assert.assertEquals(2, transformed.get(STEPS).asList().size());

        ModelNode remove = transformed.get(STEPS).asList().get(0);
        Assert.assertEquals(2, remove.keys().size());
        Assert.assertEquals(REMOVE, remove.get(OP).asString());
        Assert.assertEquals(PATH, PathAddress.pathAddress(remove.get(OP_ADDR)).iterator().next());

        ModelNode add = transformed.get(STEPS).asList().get(1);
        Assert.assertEquals(4, add.keys().size());
        Assert.assertEquals(ADD, add.get(OP).asString());
        Assert.assertEquals(PATH, PathAddress.pathAddress(add.get(OP_ADDR)).iterator().next());
        Assert.assertEquals("a", add.get("test").asString());
        Assert.assertEquals("shiny", add.get("new").asString());
    }


    @Test
    public void testRenameOperation() throws Exception {

        final ModelNode address = new ModelNode();
        address.add("toto", "testSubsystem");

        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set("rename-operation");
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
        operation.get("param").set("test");

        validateRenamedOperation(operation, ModelVersion.create(1));
        validateRenamedOperation(operation, ModelVersion.create(1, 0, 1));
        validateRenamedOperation(operation, ModelVersion.create(1, 0, 5));

        OperationTransformer.TransformedOperation op = transformOperation(ModelVersion.create(1, 1), operation);
        final ModelNode notTransformed = op.getTransformedOperation();
        Assert.assertEquals("rename-operation", notTransformed.get(OP).asString());
    }

    private void validateRenamedOperation(final ModelNode operation, final ModelVersion version) throws IllegalArgumentException, OperationFailedException {
        OperationTransformer.TransformedOperation op = transformOperation(version, operation);
        final ModelNode transformed = op.getTransformedOperation();
        Assert.assertEquals("new-name-op", transformed.get(OP).asString());
        Assert.assertEquals("test", transformed.get("param").asString());
        Assert.assertTrue(transformed.get("operation-test").asBoolean()); // explicit
        Assert.assertFalse(transformed.hasDefined("othertest")); // not inherited
        Assert.assertFalse(op.rejectOperation(success())); // inherited
    }

    @Test
    public void testDynamicDiscardOperations() throws Exception {
        PathAddress subsystem = PathAddress.pathAddress("toto", "testSubsystem");

        final PathAddress keepAddress = subsystem.append("dynamic", "keep");
        final ModelNode opKeep = Util.createAddOperation(keepAddress);
        opKeep.get("attribute").set("keep");
        OperationTransformer.TransformedOperation txKeep = transformOperation(ModelVersion.create(1), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("KEEP", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));
        txKeep = transformOperation(ModelVersion.create(1, 0, 1), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("KEEP", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));
        txKeep = transformOperation(ModelVersion.create(1, 0, 5), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("KEEP", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));
        txKeep = transformOperation(ModelVersion.create(1, 1), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("keep", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));

        final ModelNode opDiscard = Util.createAddOperation(subsystem.append("dynamic", "discard"));
        OperationTransformer.TransformedOperation txDiscard = transformOperation(ModelVersion.create(1), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNull(txDiscard.getTransformedOperation());
        txDiscard = transformOperation(ModelVersion.create(1, 0, 1), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNull(txDiscard.getTransformedOperation());
        txDiscard = transformOperation(ModelVersion.create(1, 0, 5), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNull(txDiscard.getTransformedOperation());
        txDiscard = transformOperation(ModelVersion.create(1, 1), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNotNull(txDiscard.getTransformedOperation());

        final ModelNode opReject = Util.createAddOperation(subsystem.append("dynamic", "reject"));
        OperationTransformer.TransformedOperation txReject = transformOperation(ModelVersion.create(1), opReject.clone());
        Assert.assertTrue(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
        txReject = transformOperation(ModelVersion.create(1, 0, 1), opReject.clone());
        Assert.assertTrue(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
        txReject = transformOperation(ModelVersion.create(1, 0, 5), opReject.clone());
        Assert.assertTrue(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
        txReject = transformOperation(ModelVersion.create(1, 1), opReject.clone());
        Assert.assertFalse(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
    }

    @Test
    public void testDynamicDiscardRedirectOperations() throws Exception {
        PathAddress subsystem = PathAddress.pathAddress("toto", "testSubsystem");

        final PathAddress keepNewAddress = subsystem.append("dynamic-redirect-new", "keep");
        final PathAddress keepOriginalAddress = subsystem.append("dynamic-redirect-original", "keep");
        final ModelNode opKeep = Util.createAddOperation(keepOriginalAddress);
        opKeep.get("attribute").set("keep");
        OperationTransformer.TransformedOperation txKeep = transformOperation(ModelVersion.create(1), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("KEEP", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepNewAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));
        txKeep = transformOperation(ModelVersion.create(1, 0, 1), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("KEEP", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepNewAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));
        txKeep = transformOperation(ModelVersion.create(1, 0, 5), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("KEEP", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepNewAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));
        txKeep = transformOperation(ModelVersion.create(1, 1), opKeep.clone());
        Assert.assertFalse(txKeep.rejectOperation(success()));
        Assert.assertNotNull(txKeep.getTransformedOperation());
        Assert.assertEquals("keep", txKeep.getTransformedOperation().get("attribute").asString());
        Assert.assertEquals(keepOriginalAddress, PathAddress.pathAddress(txKeep.getTransformedOperation().get(OP_ADDR)));

        final ModelNode opDiscard = Util.createAddOperation(subsystem.append("dynamic-redirect-original", "discard"));
        OperationTransformer.TransformedOperation txDiscard = transformOperation(ModelVersion.create(1), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNull(txDiscard.getTransformedOperation());
        txDiscard = transformOperation(ModelVersion.create(1, 0, 1), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNull(txDiscard.getTransformedOperation());
        txDiscard = transformOperation(ModelVersion.create(1, 0, 5), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNull(txDiscard.getTransformedOperation());
        txDiscard = transformOperation(ModelVersion.create(1, 1), opDiscard.clone());
        Assert.assertFalse(txDiscard.rejectOperation(success()));
        Assert.assertNotNull(txDiscard.getTransformedOperation());

        final ModelNode opReject = Util.createAddOperation(subsystem.append("dynamic-redirect-original", "reject"));
        OperationTransformer.TransformedOperation txReject = transformOperation(ModelVersion.create(1), opReject.clone());
        Assert.assertTrue(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
        txReject = transformOperation(ModelVersion.create(1, 0, 1), opReject.clone());
        Assert.assertTrue(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
        txReject = transformOperation(ModelVersion.create(1, 0, 5), opReject.clone());
        Assert.assertTrue(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
        txReject = transformOperation(ModelVersion.create(1, 1), opReject.clone());
        Assert.assertFalse(txReject.rejectOperation(success()));
        Assert.assertNotNull(txReject.getTransformedOperation());
    }



    private Resource transformResource(final ModelVersion version) throws OperationFailedException {
        final TransformationTarget target = create(registry, version);
        final ResourceTransformationContext context = createContext(target);
        return getTransfomers(target).transformResource(context, resourceRoot);
    }

    private OperationTransformer.TransformedOperation transformOperation(final ModelVersion version, final ModelNode operation) throws OperationFailedException {
        final TransformationTarget target = create(registry, version);
        final TransformationContext context = createContext(target);
        return getTransfomers(target).transformOperation(context, operation);
    }

    private ResourceTransformationContext createContext(final TransformationTarget target) {
        return Transformers.Factory.create(target, resourceRoot, resourceRegistration,
                ExpressionResolver.TEST_RESOLVER, RunningMode.NORMAL, ProcessType.STANDALONE_SERVER, null);
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

    private static final ResourceDefinition ROOT = new SimpleResourceDefinition(PathElement.pathElement("test"), NonResolvingResourceDescriptionResolver.INSTANCE);

    private static ModelNode success() {
        final ModelNode result = new ModelNode();
        result.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
        result.get(ModelDescriptionConstants.RESULT);
        return result;
    }

    private static class TestDynamicDiscardPolicy implements DynamicDiscardPolicy {
        @Override
        public DiscardPolicy checkResource(TransformationContext context, PathAddress address) {
            String action = address.getLastElement().getValue();
            switch (action) {
                case "keep":
                    return DiscardPolicy.NEVER;
                case "discard":
                    return DiscardPolicy.DISCARD_AND_WARN;
                case "reject":
                    return DiscardPolicy.REJECT_AND_WARN;
            }
            throw new IllegalArgumentException("Unknown address");
        }
    }
}
