/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.patching;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.Constants.MODULES;
import static org.jboss.as.patching.Constants.SYSTEM;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.test.patching.PatchingTestUtil.MODULES_PATH;
import static org.jboss.as.test.patching.PatchingTestUtil.assertPatchElements;
import static org.jboss.as.test.patching.PatchingTestUtil.createPatchXMLFile;
import static org.jboss.as.test.patching.PatchingTestUtil.createZippedPatchFile;
import static org.jboss.as.test.patching.PatchingTestUtil.randomString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.version.ProductConfig;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class CPRollingbackOneOffTestCase extends AbstractPatchingTestCase {

    private static File jarA;
    private static File jarB;
    private static File jarC;
    private static byte[] jarABytes;
    private static byte[] jarBBytes;
    private static byte[] jarCBytes;

    private static class A {
    }
    private static class B {
    }
    private static class C {
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "a.jar");
        jar.addClass(A.class);
        jarA = new File(TestSuiteEnvironment.getTmpDir() + File.separator + "a.jar");
        new ZipExporterImpl(jar).exportTo(jarA, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        IoUtils.copyStreamAndClose(new FileInputStream(jarA), bytes);
        jarABytes = bytes.toByteArray();

        jar = ShrinkWrap.create(JavaArchive.class, "b.jar");
        jar.addClass(B.class);
        jarB = new File(TestSuiteEnvironment.getTmpDir() + File.separator + "b.jar");
        new ZipExporterImpl(jar).exportTo(jarB, true);
        bytes = new ByteArrayOutputStream();
        IoUtils.copyStreamAndClose(new FileInputStream(jarB), bytes);
        jarBBytes = bytes.toByteArray();

        jar = ShrinkWrap.create(JavaArchive.class, "c.jar");
        jar.addClass(C.class);
        jarC = new File(TestSuiteEnvironment.getTmpDir() + File.separator + "c.jar");
        new ZipExporterImpl(jar).exportTo(jarC, true);
        bytes = new ByteArrayOutputStream();
        IoUtils.copyStreamAndClose(new FileInputStream(jarC), bytes);
        jarCBytes = bytes.toByteArray();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        jarA.delete();
        jarB.delete();
        jarC.delete();
    }

    protected ProductConfig productConfig;
    protected CommandContext ctx;


    @Before
    public void setup() throws Exception {
        productConfig = new ProductConfig(PatchingTestUtil.PRODUCT, PatchingTestUtil.AS_VERSION, "main");
        ctx = CLITestUtil.getCommandContext();
    }

    @After
    public void cleanup() throws Exception {
        ctx.terminateSession();
    }

    /**
     * One-off patch updates a module then a CP updates another module rolling back the one-off.
     */
    @Test
    public void testOneOffCP() throws Exception {

        // build a one-off patch for the base installation
        // with 1 updated file
        String patchID = randomString();
        File patchDir = mkdir(tempDir, patchID);
        final String patchElement1Id = randomString();

        // create a module to be updated w/o a conflict
        File baseModuleDir = newFile(new File(PatchingTestUtil.AS_DISTRIBUTION), MODULES, SYSTEM, LAYERS, BASE);
        String moduleAName = "org.a.module-a";
        Module moduleA = new Module.Builder(moduleAName)
                .resourceRoot(new ResourceItem(jarA.getName(), jarABytes))
                .miscFile(new ResourceItem("resource-a", "resource a in the module".getBytes()))
                .build();
        File moduleADir = moduleA.writeToDisk(new File(MODULES_PATH));

        String moduleBName = "org.b.module-b";
        Module moduleB = new Module.Builder(moduleBName)
                .resourceRoot(new ResourceItem(jarB.getName(), jarBBytes))
                .miscFile(new ResourceItem("resource-b", "resource b in the module".getBytes()))
                .build();
        File moduleBDir = moduleB.writeToDisk(new File(MODULES_PATH));

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource a one-off".getBytes())).build();
        // create the patch with the updated module
        ContentModification moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement1Id, HashUtils.hashFile(moduleADir), moduleA);

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(patchElement1Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        // no patches applied
        assertPatchElements(baseModuleDir, null);

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);

        patchID = randomString();
        final String patchElement2Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleB = new Module.Builder(moduleBName).miscFile(new ResourceItem("resource-b", "resource b cp".getBytes())).build();
        // create the patch with the updated module
        ContentModification moduleBModified = ContentModificationUtils.modifyModule(patchDir, patchElement2Id, HashUtils.hashFile(moduleBDir), moduleB);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion() + "_CP1")
                .getParent()
                .upgradeElement(patchElement2Id, "base", false)
                .addContentModification(moduleBModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, null);
        assertControllerStarts();
    }

    /**
     * - CP patch updates a module;
     * - one-off patch updates the same module;
     * - CP updates another module.
     */
    @Test
    public void testCPOneOffCP() throws Exception {

        // build a one-off patch for the base installation
        // with 1 updated file
        String patchID = randomString();
        File patchDir = mkdir(tempDir, patchID);
        final String patchElement1Id = randomString();

        // create a module to be updated w/o a conflict
        File baseModuleDir = newFile(new File(PatchingTestUtil.AS_DISTRIBUTION), MODULES, SYSTEM, LAYERS, BASE);
        String moduleAName = "org.a.module-a";
        Module moduleA = new Module.Builder(moduleAName)
                .resourceRoot(new ResourceItem(jarA.getName(), jarABytes))
                .miscFile(new ResourceItem("resource-a", "resource a in the module".getBytes()))
                .build();
        File moduleADir = moduleA.writeToDisk(new File(MODULES_PATH));
        String moduleBName = "org.b.module-b";
        Module moduleB = new Module.Builder(moduleBName)
                .resourceRoot(new ResourceItem(jarB.getName(), jarBBytes))
                .miscFile(new ResourceItem("resource-b", "resource b in the module".getBytes()))
                .build();
        File moduleBDir = moduleB.writeToDisk(new File(MODULES_PATH));

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource cp1".getBytes())).build();
        // create the patch with the updated module
        ContentModification moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement1Id, HashUtils.hashFile(moduleADir), moduleA);

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion())
                .getParent()
                .upgradeElement(patchElement1Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        // no patches applied
        assertPatchElements(baseModuleDir, null);

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);

        patchID = randomString();
        final String patchElement2Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource oneoff1".getBytes())).build();
        // create the patch with the updated module
        moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement2Id, moduleAModified.getItem().getContentHash(), moduleA);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(patchElement2Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();


        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id}, false);

        patchID = randomString();
        final String patchElement3Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleB = new Module.Builder(moduleBName).miscFile(new ResourceItem("resource-b", "resource b cp".getBytes())).build();
        // create the patch with the updated module
        ContentModification moduleBModified = ContentModificationUtils.modifyModule(patchDir, patchElement3Id, HashUtils.hashFile(moduleBDir), moduleB);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion() + "_CP2")
                .getParent()
                .upgradeElement(patchElement3Id, "base", false)
                .addContentModification(moduleBModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id, patchElement3Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, null);
        assertControllerStarts();
    }

    /**
     * - CP patch updates a module;
     * - one-off patch updates the same module;
     * - CP updates the same module;
     * - one-off patch updates the same module.
     */
    @Test
    public void testCPOneOffCPOneOff() throws Exception {

        // build a one-off patch for the base installation
        // with 1 updated file
        String patchID = randomString();
        File patchDir = mkdir(tempDir, patchID);
        final String patchElement1Id = randomString();

        // create a module to be updated w/o a conflict
        File baseModuleDir = newFile(new File(PatchingTestUtil.AS_DISTRIBUTION), MODULES, SYSTEM, LAYERS, BASE);
        String moduleAName = "org.a.module-a";
        Module moduleA = new Module.Builder(moduleAName)
                .resourceRoot(new ResourceItem(jarA.getName(), jarABytes))
                .miscFile(new ResourceItem("resource-a", "resource a in the module".getBytes()))
                .build();
        File moduleADir = moduleA.writeToDisk(new File(MODULES_PATH));

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource cp1".getBytes())).build();
        // create the patch with the updated module
        ContentModification moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement1Id, HashUtils.hashFile(moduleADir), moduleA);

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion())
                .getParent()
                .upgradeElement(patchElement1Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        // no patches applied
        assertPatchElements(baseModuleDir, null);

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);

        patchID = randomString();
        final String patchElement2Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource oneoff1".getBytes())).build();
        // create the patch with the updated module
        moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement2Id, moduleAModified.getItem().getContentHash(), moduleA);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(patchElement2Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();


        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id}, false);

        patchID = randomString();
        final String patchElement3Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource oneoff1".getBytes())).build();
        // create the patch with the updated module
        moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement3Id, moduleAModified.getItem().getContentHash(), moduleA);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion())
                .getParent()
                .upgradeElement(patchElement3Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id, patchElement3Id}, false);

        patchID = randomString();
        final String patchElement4Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource oneoff2".getBytes())).build();
        // create the patch with the updated module
        moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement4Id, moduleAModified.getItem().getContentHash(), moduleA);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(patchElement4Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();


        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id, patchElement3Id, patchElement4Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id, patchElement3Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, null);
        assertControllerStarts();
    }

    /**
     * - CP patch updates a module;
     * - one-off patch updates another module;
     * - CP updates another module.
     */
    @Test
    public void testCPAOneOffBCPC() throws Exception {

        // build a one-off patch for the base installation
        // with 1 updated file
        String patchID = randomString();
        File patchDir = mkdir(tempDir, patchID);
        final String patchElement1Id = randomString();

        // create a module to be updated w/o a conflict
        final File baseModuleDir = newFile(new File(PatchingTestUtil.AS_DISTRIBUTION), MODULES, SYSTEM, LAYERS, BASE);
        final String moduleAName = "org.a.module-a";
        Module moduleA = new Module.Builder(moduleAName)
                .resourceRoot(new ResourceItem(jarA.getName(), jarABytes))
                .miscFile(new ResourceItem("resource-a", "resource a in the module".getBytes()))
                .build();
        final File moduleADir = moduleA.writeToDisk(new File(MODULES_PATH));
        final String moduleBName = "org.b.module-b";
        Module moduleB = new Module.Builder(moduleBName)
                .resourceRoot(new ResourceItem(jarB.getName(), jarBBytes))
                .miscFile(new ResourceItem("resource-b", "resource b in the module".getBytes()))
                .build();
        final File moduleBDir = moduleB.writeToDisk(new File(MODULES_PATH));
        final String moduleCName = "org.c.module-c";
        Module moduleC = new Module.Builder(moduleCName)
                .resourceRoot(new ResourceItem(jarC.getName(), jarCBytes))
                .miscFile(new ResourceItem("resource-c", "resource c in the module".getBytes()))
                .build();
        File moduleCDir = moduleC.writeToDisk(new File(MODULES_PATH));

        moduleA = new Module.Builder(moduleAName).miscFile(new ResourceItem("resource-a", "resource cp1".getBytes())).build();
        final ContentModification moduleAModified = ContentModificationUtils.modifyModule(patchDir, patchElement1Id, HashUtils.hashFile(moduleADir), moduleA);

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion())
                .getParent()
                .upgradeElement(patchElement1Id, "base", false)
                .addContentModification(moduleAModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        // no patches applied
        assertPatchElements(baseModuleDir, null);

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);

        patchID = randomString();
        final String patchElement2Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleB = new Module.Builder(moduleBName).miscFile(new ResourceItem("resource-b", "resource oneoff1".getBytes())).build();
        final ContentModification moduleBModified = ContentModificationUtils.modifyModule(patchDir, patchElement2Id, HashUtils.hashFile(moduleBDir), moduleB);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(patchElement2Id, "base", false)
                .addContentModification(moduleBModified)
                .getParent()
                .build();


        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id}, false);

        patchID = randomString();
        final String patchElement3Id = randomString();
        patchDir = mkdir(tempDir, patchID);

        moduleC = new Module.Builder(moduleCName).miscFile(new ResourceItem("resource-c", "resource c cp".getBytes())).build();
        final ContentModification moduleCModified = ContentModificationUtils.modifyModule(patchDir, patchElement3Id, HashUtils.hashFile(moduleCDir), moduleC);

        patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion())
                .getParent()
                .upgradeElement(patchElement3Id, "base", false)
                .addContentModification(moduleCModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch);
        zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        applyPatch(zippedPatch);
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id, patchElement3Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id, patchElement2Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, new String[]{patchElement1Id}, false);
        assertControllerStarts();

        rollbackLast();
        assertPatchElements(baseModuleDir, null);
        assertControllerStarts();
    }

    private void assertControllerStarts() throws CommandLineException {
        startController();
        ctx.connectController();
        ctx.handle("/core-service=module-loading:list-resource-loader-paths(module=org.a.module-a)");
        ctx.handle("/core-service=module-loading:list-resource-loader-paths(module=org.b.module-b)");
        ctx.disconnectController();
        stopController();
    }

    private void rollbackLast() throws Exception {
      startController();
      try {
          ctx.connectController();
          ctx.handle("patch rollback --reset-configuration=true");
      } catch (Exception e) {
          throw e;
      } finally {
          stopController();
      }
  }

    private void applyPatch(File zippedPatch) throws Exception {
        startController();
        try {
            ctx.connectController();
            ctx.handle("patch apply " + zippedPatch.getAbsolutePath());
        } catch (Exception e) {
            throw e;
        } finally {
           stopController();
        }
    }


    private void stopController() {
        controller.stop();
    }

    private void startController() {
        controller.start();
    }
}
