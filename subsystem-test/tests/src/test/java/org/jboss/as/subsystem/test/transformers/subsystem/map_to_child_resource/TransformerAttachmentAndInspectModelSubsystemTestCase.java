/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.transformers.subsystem.map_to_child_resource;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.as.subsystem.test.transformers.subsystem.simple.VersionedExtensionCommon;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TransformerAttachmentAndInspectModelSubsystemTestCase extends AbstractSubsystemBaseTest {

    public TransformerAttachmentAndInspectModelSubsystemTestCase() {
        super(NewExtension.SUBSYSTEM_NAME, new NewExtension());
    }
    private static final Path LEGACY_ARCHIVE = Paths.get("target/legacy-archive.jar");

    @BeforeClass
    public static void createLegacyJars() throws IOException {
        JavaArchive legacySubsystemArchive = ShrinkWrap.create(JavaArchive.class, "legacy-archive.jar");
        legacySubsystemArchive.addPackage(NewExtension.class.getPackage());
        StreamExporter exporter = legacySubsystemArchive.as(ZipExporter.class);
        Files.deleteIfExists(LEGACY_ARCHIVE);
        exporter.exportTo(LEGACY_ARCHIVE.toFile());
    }
    @AfterClass
    public static void clean() throws IOException {
        Files.deleteIfExists(LEGACY_ARCHIVE);
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return "<subsystem xmlns=\"" + VersionedExtensionCommon.EXTENSION_NAME + "\"/>";
    }

    @Override
    public void testSubsystem() throws Exception {
        //Don't do the checks, we have a fake parser, and the purpose of this test is completely different
    }


    @Test
    public void testTransformers() throws Exception {
        ModelVersion oldVersion = ModelVersion.create(1, 0, 0);
        KernelServicesBuilder builder = createKernelServicesBuilder(null)
                .setSubsystemXml(getSubsystemXml());
        builder.createLegacyKernelServicesBuilder(null, ModelTestControllerVersion.MASTER, oldVersion)
                .setExtensionClassName(OldExtension.class.getName())
                .addSimpleResourceURL("target/legacy-archive.jar")
                .dontPersistXml()//don't test xml persistence as woodstox parser for legacy test will break it
                .skipReverseControllerCheck();
        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(oldVersion);
        Assert.assertNotNull(legacyServices);


        ModelNode mainModel = mainServices.readWholeModel();
        ModelNode legacyModel = legacyServices.readWholeModel();
        checkModels(mainModel, legacyModel, "Hello", "one", "A", "two", "B");

        //Check the resource transformation results in the same model as the one from the add ops
        Assert.assertEquals(legacyModel.get(SUBSYSTEM, "test-subsystem"), mainServices.readTransformedModel(oldVersion).get(SUBSYSTEM, "test-subsystem"));

        //Remove, change, and add things back to the normal state
        final PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "test-subsystem");

        ModelNode write1 = getMapRemoveOperation(subsystemAddress, "properties", "one");
        transformAndExecuteInLegacyController(mainServices, oldVersion, write1);
        checkModels(mainServices.readWholeModel(), legacyServices.readWholeModel(), "Hello", "two", "B");

        ModelNode write2 = getMapPutOperation(subsystemAddress, "properties", "two", "b");
        transformAndExecuteInLegacyController(mainServices, oldVersion, write2);
        checkModels(mainServices.readWholeModel(), legacyServices.readWholeModel(), "Hello", "two", "b");

        ModelNode write3 = getMapRemoveOperation(subsystemAddress, "properties", "two");
        transformAndExecuteInLegacyController(mainServices, oldVersion, write3);
        checkModels(mainServices.readWholeModel(), legacyServices.readWholeModel(), "Hello");

        ModelNode write4 = getMapPutOperation(subsystemAddress, "properties", "one", "A");
        transformAndExecuteInLegacyController(mainServices, oldVersion, write4);
        checkModels(mainServices.readWholeModel(), legacyServices.readWholeModel(), "Hello", "one", "A");

        ModelNode write5 = getMapPutOperation(subsystemAddress, "properties", "two", "B");
        transformAndExecuteInLegacyController(mainServices, oldVersion, write5);
        checkModels(mainServices.readWholeModel(), legacyServices.readWholeModel(), "Hello", "one", "A", "two", "B");

        //Now try to do the same with a composite
        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        composite.get(STEPS).add(write1);
        composite.get(STEPS).add(write2);
        composite.get(STEPS).add(write3);
        transformAndExecuteInLegacyController(mainServices, oldVersion, composite);
        checkModels(mainServices.readWholeModel(), legacyServices.readWholeModel(), "Hello");

        composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        composite.get(STEPS).add(write4);
        composite.get(STEPS).add(write5);
        transformAndExecuteInLegacyController(mainServices, oldVersion, composite);
        checkModels(mainServices.readWholeModel(), legacyServices.readWholeModel(), "Hello", "one", "A", "two", "B");
        legacyServices.shutdown();
        mainServices.shutdown();
    }


    private void checkModels(ModelNode mainModel, ModelNode legacyModel, String testValue, String... properties) {
        checkMainModel(mainModel, testValue, properties);
        checkLegacyModel(legacyModel, testValue.toUpperCase(), properties);
    }

    private void checkMainModel(ModelNode model, String testValue, String... properties) {
        Assert.assertEquals(0, properties.length % 2);

        ModelNode mainSubsystem = model.get(SUBSYSTEM, "test-subsystem");
        Assert.assertEquals(2, mainSubsystem.keys().size());
        Assert.assertEquals(testValue, mainSubsystem.get("test").asString());
        ModelNode props = mainSubsystem.get("properties");
        Assert.assertEquals(properties.length / 2, props.isDefined() ? props.keys().size() : 0);
        for (int i = 0 ; i < properties.length ; i += 2) {
            Assert.assertEquals(properties[i + 1], props.get(properties[i]).asString());
        }
    }

    private void checkLegacyModel(ModelNode model, String testValue, String... properties) {
        Assert.assertEquals(0, properties.length % 2);

        ModelNode mainSubsystem = model.get(SUBSYSTEM, "test-subsystem");
        Assert.assertEquals(2, mainSubsystem.keys().size());
        Assert.assertEquals(testValue, mainSubsystem.get("test").asString());
        ModelNode props = mainSubsystem.get("property");
        Assert.assertEquals(properties.length/2, props.isDefined() ? props.keys().size() : 0);
        for (int i = 0 ; i < properties.length ; i += 2) {
            ModelNode property = props.get(properties[i]);
            Assert.assertTrue(property.isDefined());
            Assert.assertEquals(1, property.keys().size());
            Assert.assertEquals(properties[i + 1], property.get("value").asString());
        }
    }

    private ModelNode getMapRemoveOperation(PathAddress addr, String attrName, String key) {
        ModelNode op = Util.createEmptyOperation("map-remove", addr);
        op.get("name").set(attrName);
        op.get("key").set(key);
        return op;
    }

    private ModelNode getMapPutOperation(PathAddress addr, String attrName, String key, String value) {
        ModelNode op = Util.createEmptyOperation("map-put", addr);
        op.get("name").set(attrName);
        op.get("key").set(key);
        op.get("value").set(value);
        return op;
    }

    private void transformAndExecuteInLegacyController(KernelServices mainServices, ModelVersion oldVersion, ModelNode operation) {
        TransformedOperation op = mainServices.executeInMainAndGetTheTransformedOperation(operation, oldVersion);
        Assert.assertFalse(op.rejectOperation(success()));
        if (op.getTransformedOperation() != null) {
            ModelTestUtils.checkOutcome(mainServices.getLegacyServices(oldVersion).executeOperation(op.getTransformedOperation()));
        }
    }
    private static ModelNode success() {
        final ModelNode result = new ModelNode();
        result.get(OUTCOME).set(SUCCESS);
        result.get(RESULT);
        return result;
    }
}
