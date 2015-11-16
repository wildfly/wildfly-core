/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.subsystem.test.transformers.subsystem.map_to_child_resource;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

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

    @BeforeClass
    public static void createLegacyJars() throws MalformedURLException {
        JavaArchive legacySubsystemArchive = ShrinkWrap.create(JavaArchive.class, "legacy-archive.jar");
        legacySubsystemArchive.addPackage(NewExtension.class.getPackage());
        StreamExporter exporter = legacySubsystemArchive.as(ZipExporter.class);
        File file = new File("target/legacy-archive.jar");
        if (file.exists()) {
            file.delete();
        }
        exporter.exportTo(file);
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return "<subsystem xmlns=\"" + VersionedExtensionCommon.EXTENSION_NAME + "\"/>";
    }

    @Override
    public void testSubsystem() throws Exception {
        //Don't do the checks, we have a fake parser, and the purpose of this test is completely different
    }

    @Override
    public void testSchema() throws Exception {
        //This test does not have a schema for the subsystem so simply override the test method with this no-impl
    }

    @Override
    public void testSchemaOfSubsystemTemplates() throws Exception {
        //This test does not have a schema for the subsystem so simply override the test method with this no-impl
    }

    @Test
    public void testTransformers() throws Exception {
        ModelVersion oldVersion = ModelVersion.create(1, 0, 0);
        KernelServicesBuilder builder = createKernelServicesBuilder(null)
                .setSubsystemXml(getSubsystemXml());
        builder.createLegacyKernelServicesBuilder(null, ModelTestControllerVersion.MASTER, oldVersion)
                .setExtensionClassName(OldExtension.class.getName())
                .addSimpleResourceURL("target/legacy-archive.jar")
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
