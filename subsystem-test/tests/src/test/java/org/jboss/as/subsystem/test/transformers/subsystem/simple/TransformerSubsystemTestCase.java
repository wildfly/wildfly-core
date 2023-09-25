/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.transformers.subsystem.simple;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
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
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TransformerSubsystemTestCase extends AbstractSubsystemBaseTest {
    private static final Path LEGACY_ARCHIVE = Paths.get("target/legacy-archive-transformers.jar");

    public TransformerSubsystemTestCase() {
        super(VersionedExtensionCommon.SUBSYSTEM_NAME, new VersionedExtension2());
    }

    @BeforeClass
    public static void createLegacyJars() throws IOException {
        JavaArchive legacySubsystemArchive = ShrinkWrap.create(JavaArchive.class, "legacy-archive-transformers.jar");
        legacySubsystemArchive.addPackage(VersionedExtension2.class.getPackage());
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

    @Test
    public void testTransformersAS() throws Exception {
        testTransformers(ModelTestControllerVersion.MASTER);
    }
    @Test
    public void testTransformersEAP740() throws Exception {
        testTransformers(ModelTestControllerVersion.EAP_7_4_0);
    }

    private void testTransformers(ModelTestControllerVersion controllerVersion) throws Exception {
        ModelVersion oldVersion = ModelVersion.create(1, 0, 0);
        KernelServicesBuilder builder = createKernelServicesBuilder(null)
                .setSubsystemXml(getSubsystemXml());
        builder.createLegacyKernelServicesBuilder(null, controllerVersion, oldVersion)
                .setExtensionClassName(VersionedExtension1.class.getName())
                .addSimpleResourceURL(LEGACY_ARCHIVE.toString())
                .skipReverseControllerCheck();
        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(oldVersion);
        Assert.assertNotNull(legacyServices);


        ModelNode mainModel = mainServices.readWholeModel();
        ModelNode mainSubsystem = mainModel.get(SUBSYSTEM, "versioned-subsystem");
        Assert.assertEquals(3, mainSubsystem.keys().size());
        Assert.assertEquals("This is only a test", mainSubsystem.get("test-attribute").asString());
        Assert.assertTrue(mainSubsystem.hasDefined("new-element"));
        Assert.assertTrue(mainSubsystem.get("new-element").hasDefined("test"));
        Assert.assertTrue(mainSubsystem.hasDefined("renamed"));
        Assert.assertTrue(mainSubsystem.get("renamed").hasDefined("element"));

        ModelNode legacyModel = legacyServices.readWholeModel();
        ModelNode legacySubsystem = legacyModel.get(SUBSYSTEM, "versioned-subsystem");
        Assert.assertEquals(2, legacySubsystem.keys().size());
        Assert.assertEquals("This is only a test", legacySubsystem.get("test-attribute").asString());
        Assert.assertTrue(legacySubsystem.hasDefined("element"));
        Assert.assertTrue(legacySubsystem.get("element").hasDefined("renamed"));

        checkSubsystemModelTransformation(mainServices, oldVersion);

        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "versioned-subsystem");
        ModelNode writeAttribute = Util.getWriteAttributeOperation(subsystemAddress, "test-attribute", "do reject");
        OperationTransformer.TransformedOperation op = mainServices.executeInMainAndGetTheTransformedOperation(writeAttribute, oldVersion);
        Assert.assertFalse(op.rejectOperation(success()));

        //The model now has the 'magic' old value which gets put into the transformer attachment, which the reject transformer
        //will reject
        writeAttribute = Util.getWriteAttributeOperation(subsystemAddress, "test-attribute", "something else");
        op = mainServices.executeInMainAndGetTheTransformedOperation(writeAttribute, oldVersion);
        Assert.assertTrue(op.rejectOperation(success()));
        legacyServices.shutdown();
        mainServices.shutdown();
    }

    private static ModelNode success() {
        final ModelNode result = new ModelNode();
        result.get(OUTCOME).set(SUCCESS);
        result.get(RESULT);
        return result;
    }
}
