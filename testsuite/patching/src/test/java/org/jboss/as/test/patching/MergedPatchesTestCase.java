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

import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.test.patching.PatchingTestUtil.AS_DISTRIBUTION;
import static org.jboss.as.test.patching.PatchingTestUtil.AS_VERSION;
import static org.jboss.as.test.patching.PatchingTestUtil.FILE_SEPARATOR;
import static org.jboss.as.test.patching.PatchingTestUtil.MODULES_PATH;
import static org.jboss.as.test.patching.PatchingTestUtil.PRODUCT;
import static org.jboss.as.test.patching.PatchingTestUtil.createPatchXMLFile;
import static org.jboss.as.test.patching.PatchingTestUtil.createZippedPatchFile;
import static org.jboss.as.test.patching.PatchingTestUtil.randomString;

import java.io.File;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.metadata.PatchMerger;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.as.version.ProductConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Martin Simka, updated for WildFly 10 by Jan Martiska
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class MergedPatchesTestCase extends AbstractPatchingTestCase {

    /**
     * creates 4 CPs
     * creates bundle containing 4 CPs
     * applies bundle
     * rolls back back to base version
     *
     * @throws Exception
     */
    @Test
    public void testApplyMergedPatch() throws Exception {
        File patchTmpDir = mkdir(tempDir, randomString("patchdir"));

        final String cpPatchID = randomString("cp1");
        final String cpPatchID2 = randomString("cp2");
        final String cpPatchID3 = randomString("cp3");
        final String cpPatchID4 = randomString("cp4");
        final String eapWithCP = "EAP with cp patch";
        final String eapWithCP2 = "EAP with cp patch 2";
        final String eapWithCP3 = "EAP with cp patch 3";
        final String eapWithCP4 = "EAP with cp patch 4";

        File cpZip = createCP1(cpPatchID, AS_VERSION, eapWithCP, patchTmpDir);
        File cpZip2 = createCP2(cpPatchID2, eapWithCP, cpPatchID, eapWithCP2, patchTmpDir);
        File cpZip3 = createCP3(cpPatchID3, eapWithCP2, cpPatchID2, eapWithCP3, patchTmpDir);
        File cpZip4 = createCP2(cpPatchID4, eapWithCP3, cpPatchID3, eapWithCP4, patchTmpDir);

        final File mergedCp1Cp2 = PatchMerger.merge(cpZip, cpZip2, new File("merged-cp1-cp2.zip"));
        mergedCp1Cp2.deleteOnExit();
        final File mergedCp1Cp2Cp3 = PatchMerger
                .merge(mergedCp1Cp2, cpZip3, new File("merged-cp1-cp2-cp3.zip"));
        mergedCp1Cp2Cp3.deleteOnExit();
        final File mergedCp1Cp2Cp3Cp4 = PatchMerger
                .merge(mergedCp1Cp2Cp3, cpZip4, new File("merged-cp1-cp2-cp3-cp4.zip"));
        mergedCp1Cp2Cp3Cp4.deleteOnExit();

        // apply bundle
        controller.start();
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(mergedCp1Cp2Cp3Cp4.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify installed cp version
        controller.start();
        Assert.assertTrue("The patch " + cpPatchID4 + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID4));
        Assert.assertTrue("Module " + cp1AddedModuleName + " should exist", CliUtilsForPatching.doesModuleExist(cp1AddedModuleName));


        // rollback to base
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify base
        controller.start();
        Assert.assertTrue("The patch " + Constants.BASE + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(Constants.BASE));
        Assert.assertFalse("Module " + cp1AddedModuleName + " should not exist", CliUtilsForPatching.doesModuleExist(cp1AddedModuleName));
        controller.stop();
    }

    /**
     * creates 4 CPs
     * creates bundle containing 4 CPs (merging them together)
     * applies CP1
     * applies CP2
     * applies one-off
     * applies bundle
     * rolls back to CP2, verifies the one-off is still there
     * rolls back to CP1
     * rolls back to base
     *
     * @throws Exception
     */
    @Test
    public void testApplyBundleSkipTwoCPs() throws Exception {
        File patchTmpDir = mkdir(tempDir, randomString("patchdir"));

        final String cpPatchID = randomString("cp1");
        final String cpPatchID2 = randomString("cp2");
        final String cpPatchID3 = randomString("cp3");
        final String cpPatchID4 = randomString("cp4");
        final String oneOffId = randomString("oneoff");
        final String eapWithCP = "EAP with cp patch";
        final String eapWithCP2 = "EAP with cp patch 2";
        final String eapWithCP3 = "EAP with cp patch 3";
        final String eapWithCP4 = "EAP with cp patch 4";


        File cpZip = createCP1(cpPatchID, AS_VERSION, eapWithCP, patchTmpDir);
        File cpZip2 = createCP2(cpPatchID2, eapWithCP, cpPatchID, eapWithCP2, patchTmpDir);
        File cpZip3 = createCP3(cpPatchID3, eapWithCP2, cpPatchID2, eapWithCP3, patchTmpDir);
        File cpZip4 = createCP2(cpPatchID4, eapWithCP3, cpPatchID3, eapWithCP4, patchTmpDir);
        File oneOffZip = createOneOffPatchAddingMiscFile(oneOffId, eapWithCP2);

        final File mergedCp1Cp2 = PatchMerger.merge(cpZip, cpZip2, new File("merged-cp1-cp2.zip"));
        mergedCp1Cp2.deleteOnExit();
        final File mergedCp1Cp2Cp3 = PatchMerger
                .merge(mergedCp1Cp2, cpZip3, new File("merged-cp1-cp2-cp3.zip"));
        mergedCp1Cp2Cp3.deleteOnExit();
        final File mergedCp1Cp2Cp3Cp4 = PatchMerger
                .merge(mergedCp1Cp2Cp3, cpZip4, new File("merged-cp1-cp2-cp3-cp4.zip"));
        mergedCp1Cp2Cp3Cp4.deleteOnExit();

        // apply cp1
        controller.start();
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(cpZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify cp1 and apply cp2
        controller.start();
        Assert.assertTrue("The patch " + cpPatchID + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID));
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(cpZip2.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify cp2 and apply oneoff
        controller.start();
        Assert.assertTrue("The patch " + cpPatchID2 + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID2));
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(oneOffZip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify cp2 + oneoff and apply bundle to cp4
        controller.start();
        Assert.assertTrue("The patch " + cpPatchID2 + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID2));
        Assert.assertTrue("The patch " + oneOffId + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffId));
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(mergedCp1Cp2Cp3Cp4.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify cp4 and rollback cp4 back to cp2
        controller.start();
        Assert.assertTrue("The patch " + cpPatchID4 + " should be listed as installed",
                CliUtilsForPatching.getCumulativePatchId().equalsIgnoreCase(cpPatchID4));
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify cp2 + oneoff and roll back the oneoff
        controller.start();
        Assert.assertEquals("The patch " + cpPatchID2 + " should be listed as installed",
                cpPatchID2, CliUtilsForPatching.getCumulativePatchId());
        Assert.assertTrue("The patch " + oneOffId + " should be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffId));
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackPatch(oneOffId));
        controller.stop();

        // verify oneoff is not installed, rollback to cp1
        controller.start();
        Assert.assertFalse("The patch " + oneOffId + " should not be listed as installed",
                CliUtilsForPatching.getInstalledPatches().contains(oneOffId));
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

        // verify cp1 and roll back to base
        controller.start();
        Assert.assertEquals("The patch " + cpPatchID + " should be listed as installed",
                cpPatchID, CliUtilsForPatching.getCumulativePatchId());
        Assert.assertTrue("Rollback should be accepted", CliUtilsForPatching.rollbackCumulativePatch(true));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();

    }

    /*    -------------- THINGS RELATED TO CP1 ------------- */

    private String cp1AddedModuleName;
    private String cp1LayerPatchID;
    private ResourceItem cp1ResourceItem1;
    private ResourceItem cp1ResourceItem2;
    private String cp1Slot;

    private File createCP1(String patchID, String asVersion, final String targetAsVersion, File targetDir)
            throws Exception {
        cp1LayerPatchID = "layer" + patchID;
        File cpPatchDir = mkdir(tempDir, patchID);

        cp1AddedModuleName = "org.wildfly.test." + randomString("cp1");

        cp1ResourceItem1 = new ResourceItem("testFile1", "content1".getBytes());
        cp1ResourceItem2 = new ResourceItem("testFile2", "content2".getBytes());

        Module newModule = new Module.Builder(cp1AddedModuleName)
                .miscFile(cp1ResourceItem1)
                .miscFile(cp1ResourceItem2)
                .build();

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        cp1Slot = ProductInfo.getVersionModuleSlot();
        final String originalVersionModulePath = MODULES_PATH + FILE_SEPARATOR + versionModuleName
                .replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + cp1Slot;
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        ContentModification moduleAdded = ContentModificationUtils
                .addModule(cpPatchDir, cp1LayerPatchID, newModule);
        ContentModification versionModuleModified = ContentModificationUtils
                .modifyModule(cpPatchDir, cp1LayerPatchID,
                        HashUtils.hashFile(new File(originalVersionModulePath)), modifiedModule);

        Patch cpPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A cp patch.")
                .upgradeIdentity(PRODUCT, asVersion, targetAsVersion)
                .getParent()
                .upgradeElement(cp1LayerPatchID, "base", false)
                .addContentModification(moduleAdded)
                .addContentModification(versionModuleModified)
                .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, patchID, targetDir);
    }

    private File createCP2(String patchID, String asVersion, final String currentPatch,
                           final String targetAsVersion, File targetDir) throws Exception {
        String layerPatchID = "layer" + patchID;
        File cpPatchDir = mkdir(tempDir, patchID);
        final String moduleName = "org.wildfly.test." + randomString("cp2");

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // Calculate the target hash of the currently active module
        final String currentLayerPatchID = "layer" + currentPatch;
        File originalVersionModulePath = new File(tempDir, currentPatch);
        originalVersionModulePath = new File(originalVersionModulePath, currentLayerPatchID);
        originalVersionModulePath = new File(originalVersionModulePath, Constants.MODULES);
        originalVersionModulePath = newFile(originalVersionModulePath, versionModuleName.split("\\."));
        originalVersionModulePath = new File(originalVersionModulePath, ProductInfo.getVersionModuleSlot());
        byte[] patchedAsVersionHash = HashUtils.hashFile(originalVersionModulePath);
        assert patchedAsVersionHash != null;

        final ResourceItem resourceItem1 = new ResourceItem("testFile1", "content1".getBytes());
        final ResourceItem resourceItem2 = new ResourceItem("testFile2", "content2".getBytes());

        Module newModule = new Module.Builder(moduleName)
                .miscFile(resourceItem1)
                .miscFile(resourceItem2)
                .build();

        ContentModification moduleAdded = ContentModificationUtils
                .addModule(cpPatchDir, layerPatchID, newModule);
        ContentModification versionModuleModified = ContentModificationUtils
                .modifyModule(cpPatchDir, layerPatchID, patchedAsVersionHash, modifiedModule);

        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch cpPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A cp patch.")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(),
                        targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchID, "base", false)
                .addContentModification(versionModuleModified)
                .addContentModification(moduleAdded)
                .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, patchID, targetDir);
    }

    private File createCP3(String patchID, String asVersion, final String currentPatch,
                           final String targetAsVersion, File targetDir) throws Exception {
        String layerPatchID = "layer" + patchID;
        File cpPatchDir = mkdir(tempDir, patchID);

        // Also see if we can update jboss-modules
        final File installation = new File(AS_DISTRIBUTION);
        final File patchDir = new File(cpPatchDir, patchID);
        final ContentModification jbossModulesModification = PatchingTestUtil
                .updateModulesJar(installation, patchDir);

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // Calculate the target hash of the currently active module
        final String currentLayerPatchID = "layer" + currentPatch;
        File originalVersionModulePath = new File(tempDir, currentPatch);
        originalVersionModulePath = new File(originalVersionModulePath, currentLayerPatchID);
        originalVersionModulePath = new File(originalVersionModulePath, Constants.MODULES);
        originalVersionModulePath = newFile(originalVersionModulePath, versionModuleName.split("\\."));
        originalVersionModulePath = new File(originalVersionModulePath, ProductInfo.getVersionModuleSlot());
        byte[] patchedAsVersionHash = HashUtils.hashFile(originalVersionModulePath);
        assert patchedAsVersionHash != null;

        ContentModification versionModuleModified = ContentModificationUtils
                .modifyModule(cpPatchDir, layerPatchID, patchedAsVersionHash, modifiedModule);

        Patch cpPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A cp patch.")
                .upgradeIdentity(PRODUCT, asVersion, targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchID, "base", false)
                .addContentModification(versionModuleModified)
                .getParent()
                .addContentModification(jbossModulesModification)
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, patchID, targetDir);
    }

    private File createOneOffPatchAddingMiscFile(String patchID, String asVersion) throws Exception {
        File oneOffPatchDir = mkdir(tempDir, patchID);
        ContentModification miscFileAdded = ContentModificationUtils.addMisc(oneOffPatchDir, patchID,
                "test content", "awesomeDirectory", "awesomeFile");
        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch oneOffPatch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription("A one-off patch adding a misc file.")
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .addContentModification(miscFileAdded)
                .build();
        createPatchXMLFile(oneOffPatchDir, oneOffPatch);
        return createZippedPatchFile(oneOffPatchDir, patchID);
    }

}
