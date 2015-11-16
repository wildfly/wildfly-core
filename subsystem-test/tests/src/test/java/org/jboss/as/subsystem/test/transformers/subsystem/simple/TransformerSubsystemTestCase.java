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
package org.jboss.as.subsystem.test.transformers.subsystem.simple;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TransformerSubsystemTestCase extends AbstractSubsystemBaseTest {

    public TransformerSubsystemTestCase() {
        super(VersionedExtensionCommon.SUBSYSTEM_NAME, new VersionedExtension2());
    }

    @BeforeClass
    public static void createLegacyJars() throws MalformedURLException {
        JavaArchive legacySubsystemArchive = ShrinkWrap.create(JavaArchive.class, "legacy-archive.jar");
        legacySubsystemArchive.addPackage(VersionedExtension2.class.getPackage());
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

    @Test
    public void testTransformersAS() throws Exception {
        testTransformers(ModelTestControllerVersion.MASTER);
    }

    @Test
    public void testTransformersEAP620() throws Exception {
        testTransformers(ModelTestControllerVersion.EAP_6_2_0);
    }

    @Test
    public void testTransformersEAP630() throws Exception {
        testTransformers(ModelTestControllerVersion.EAP_6_3_0);
    }

    @Test
    public void testTransformersEAP640() throws Exception {
        testTransformers(ModelTestControllerVersion.EAP_6_4_0);
    }

    @Override
    public void testSchema() throws Exception {
        //This test does not have a schema for the subsystem so simply override the test method with this no-impl
    }

    @Override
    public void testSchemaOfSubsystemTemplates() throws Exception {
        //This test does not have a schema for the subsystem so simply override the test method with this no-impl
    }

    private void testTransformers(ModelTestControllerVersion controllerVersion) throws Exception {
        ModelVersion oldVersion = ModelVersion.create(1, 0, 0);
        KernelServicesBuilder builder = createKernelServicesBuilder(null)
                .setSubsystemXml(getSubsystemXml());
        builder.createLegacyKernelServicesBuilder(null, controllerVersion, oldVersion)
                .setExtensionClassName(VersionedExtension1.class.getName())
                .addSimpleResourceURL("target/legacy-archive.jar")
                .skipReverseControllerCheck();
        KernelServices mainServices = builder.build();
        KernelServices legacyServices = mainServices.getLegacyServices(oldVersion);
        Assert.assertNotNull(legacyServices);


        ModelNode mainModel = mainServices.readWholeModel();
        ModelNode mainSubsystem = mainModel.get(SUBSYSTEM, "test-subsystem");
        Assert.assertEquals(3, mainSubsystem.keys().size());
        Assert.assertEquals("This is only a test", mainSubsystem.get("test-attribute").asString());
        Assert.assertTrue(mainSubsystem.hasDefined("new-element"));
        Assert.assertTrue(mainSubsystem.get("new-element").hasDefined("test"));
        Assert.assertTrue(mainSubsystem.hasDefined("renamed"));
        Assert.assertTrue(mainSubsystem.get("renamed").hasDefined("element"));

        ModelNode legacyModel = legacyServices.readWholeModel();
        ModelNode legacySubsystem = legacyModel.get(SUBSYSTEM, "test-subsystem");
        Assert.assertEquals(2, legacySubsystem.keys().size());
        Assert.assertEquals("This is only a test", legacySubsystem.get("test-attribute").asString());
        Assert.assertTrue(legacySubsystem.hasDefined("element"));
        Assert.assertTrue(legacySubsystem.get("element").hasDefined("renamed"));

        generateLegacySubsystemResourceRegistrationDmr(mainServices, oldVersion);
        checkSubsystemModelTransformation(mainServices, oldVersion);

        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "test-subsystem");
        ModelNode writeAttribute = Util.getWriteAttributeOperation(subsystemAddress, "test-attribute", "do reject");
        OperationTransformer.TransformedOperation op = mainServices.executeInMainAndGetTheTransformedOperation(writeAttribute, oldVersion);
        Assert.assertFalse(op.rejectOperation(success()));

        //The model now has the 'magic' old value which gets put into the transformer attachment, which the reject transformer
        //will reject
        writeAttribute = Util.getWriteAttributeOperation(subsystemAddress, "test-attribute", "something else");
        op = mainServices.executeInMainAndGetTheTransformedOperation(writeAttribute, oldVersion);
        Assert.assertTrue(op.rejectOperation(success()));
    }

    private static ModelNode success() {
        final ModelNode result = new ModelNode();
        result.get(OUTCOME).set(SUCCESS);
        result.get(RESULT);
        return result;
    }
}
