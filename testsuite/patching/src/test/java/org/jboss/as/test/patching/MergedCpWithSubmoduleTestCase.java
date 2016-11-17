/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.patching;

import static org.jboss.as.patching.IoUtils.mkdir;
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
import org.jboss.as.patching.IoUtils;
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
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class MergedCpWithSubmoduleTestCase extends AbstractPatchingTestCase {

    private final String topModuleName = "org.wildfly.test.topmodule";
    private final String subModuleName = "org.wildfly.test.topmodule.impl";
    private ResourceItem cp1ResourceItem1;
    private ResourceItem cp1ResourceItem2;
    private String cp1Slot;

    @Test
    public void testSubmoduleUpdated() throws Exception {
        File patchTmpDir = mkdir(tempDir, randomString("patchdir"));

        final String cp1Id = "cp1";
        final String cp2Id = "cp2";
        final String cp1AsVersion = AS_VERSION + cp1Id;
        final String cp2AsVersion = AS_VERSION + cp2Id;

        final File cp1Zip = createCp1(cp1Id, AS_VERSION, cp1AsVersion, patchTmpDir);
        final File cp2Zip = createSubmoduleUpdateCp2(cp2Id, cp1AsVersion, cp1Id, cp2AsVersion, patchTmpDir);

        final File mergedCp1Cp2Zip = PatchMerger.merge(cp1Zip, cp2Zip, new File("merged-cp1-cp2.zip"));
        mergedCp1Cp2Zip.deleteOnExit();

        // apply bundle
        controller.start();
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(mergedCp1Cp2Zip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();
    }

    @Test
    public void testTopModuleSlotUpdated() throws Exception {
        File patchTmpDir = mkdir(tempDir, randomString("patchdir"));

        final String cp1Id = "cp1";
        final String cp2Id = "cp2";
        final String cp1AsVersion = AS_VERSION + cp1Id;
        final String cp2AsVersion = AS_VERSION + cp2Id;

        final File cp1Zip = createCp1(cp1Id, AS_VERSION, cp1AsVersion, patchTmpDir);
        final File cp2Zip = createTopModuleSlotAndSubmoduleUpdateCp2(cp2Id, cp1AsVersion, cp1Id, cp2AsVersion, patchTmpDir);

        final File mergedCp1Cp2Zip = PatchMerger.merge(cp1Zip, cp2Zip, new File("merged-cp1-cp2.zip"));
        mergedCp1Cp2Zip.deleteOnExit();

        // apply bundle
        controller.start();
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(mergedCp1Cp2Zip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();
    }

    @Test
    public void testTopModuleMainSlotUpdated() throws Exception {
        File patchTmpDir = mkdir(tempDir, randomString("patchdir"));

        final String cp1Id = "cp1";
        final String cp2Id = "cp2";
        final String cp1AsVersion = AS_VERSION + cp1Id;
        final String cp2AsVersion = AS_VERSION + cp2Id;

        final File cp1Zip = createCp1(cp1Id, AS_VERSION, cp1AsVersion, patchTmpDir);
        final File cp2Zip = createTopModuleMainSlotUpdateCp2(cp2Id, cp1AsVersion, cp1Id, cp2AsVersion, patchTmpDir);

        final File mergedCp1Cp2Zip = PatchMerger.merge(cp1Zip, cp2Zip, new File("merged-cp1-cp2.zip"));
        mergedCp1Cp2Zip.deleteOnExit();

        // apply bundle
        controller.start();
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(mergedCp1Cp2Zip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();
    }

    @Test
    public void testAllUpdated() throws Exception {
        File patchTmpDir = mkdir(tempDir, randomString("patchdir"));

        final String cp1Id = "cp1";
        final String cp2Id = "cp2";
        final String cp1AsVersion = AS_VERSION + cp1Id;
        final String cp2AsVersion = AS_VERSION + cp2Id;

        final File cp1Zip = createCp1(cp1Id, AS_VERSION, cp1AsVersion, patchTmpDir);
        final File cp2Zip = createAllUpdatedCp2(cp2Id, cp1AsVersion, cp1Id, cp2AsVersion, patchTmpDir);

        final File mergedCp1Cp2Zip = PatchMerger.merge(cp1Zip, cp2Zip, new File("merged-cp1-cp2.zip"));
        mergedCp1Cp2Zip.deleteOnExit();

        // apply bundle
        controller.start();
        Assert.assertTrue("Patch should be accepted",
                CliUtilsForPatching.applyPatch(mergedCp1Cp2Zip.getAbsolutePath()));
        Assert.assertTrue("server should be in restart-required mode",
                CliUtilsForPatching.doesServerRequireRestart());
        controller.stop();
    }

    private File createCp1(String cpId, String asVersion, final String targetAsVersion, File targetDir)
            throws Exception {

        final String cp1LayerPatchId = "layer" + cpId;
        File cpPatchDir = mkdir(tempDir, cpId);

        cp1ResourceItem1 = new ResourceItem("testFile1", "content1".getBytes());
        cp1ResourceItem2 = new ResourceItem("testFile2", "content2".getBytes());

        final Module topModuleMain = new Module.Builder(topModuleName)
                .resourceRoot(cp1ResourceItem1)
                .resourceRoot(cp1ResourceItem2)
                .build();
        final Module topModule41 = new Module.Builder(topModuleName)
               .slot("slot41")
               .resourceRoot(cp1ResourceItem1)
               .resourceRoot(cp1ResourceItem2)
               .build();

        final Module subModule = new Module.Builder(subModuleName)
                .resourceRoot(cp1ResourceItem1)
                .resourceRoot(cp1ResourceItem2)
                .build();

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        cp1Slot = ProductInfo.getVersionModuleSlot();
        final String originalVersionModulePath = MODULES_PATH + FILE_SEPARATOR + versionModuleName
                .replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + cp1Slot;
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        final ContentModification topModuleMainAdded = ContentModificationUtils.addModule(cpPatchDir, cp1LayerPatchId, topModuleMain);
        final ContentModification topModule41Added = ContentModificationUtils.addModule(cpPatchDir, cp1LayerPatchId, topModule41);
        final ContentModification subModuleAdded = ContentModificationUtils.addModule(cpPatchDir, cp1LayerPatchId, subModule);
        ContentModification versionModuleModified = ContentModificationUtils
                .modifyModule(cpPatchDir, cp1LayerPatchId,
                        HashUtils.hashFile(new File(originalVersionModulePath)), modifiedModule);

        Patch cpPatch = PatchBuilder.create()
                .setPatchId(cpId)
                .setDescription("A cp patch.")
                .upgradeIdentity(PRODUCT, asVersion, targetAsVersion)
                .getParent()
                .upgradeElement(cp1LayerPatchId, "base", false)
                // the order of the submodule and topmodule modifications is important in this case
                .addContentModification(subModuleAdded)
                .addContentModification(topModuleMainAdded)
                .addContentModification(topModule41Added)
                .addContentModification(versionModuleModified)
                .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, cpId, targetDir);
    }

    private File createSubmoduleUpdateCp2(String cpId, String asVersion, final String currentCpId,
                           final String targetAsVersion, File targetDir) throws Exception {
        final String layerPatchId = "layer" + cpId;
        File cpPatchDir = mkdir(tempDir, cpId);

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // Calculate the target hash of the currently active module
        final String currentLayerPatchId = "layer" + currentCpId;
        final File originalVersionModulePath = IoUtils.newFile(tempDir, currentCpId,
                currentLayerPatchId, Constants.MODULES,
                versionModuleName.replace('.', File.separatorChar),
                ProductInfo.getVersionModuleSlot());
        byte[] patchedAsVersionHash = HashUtils.hashFile(originalVersionModulePath);
        assert patchedAsVersionHash != null;

        ContentModification versionModuleModified = ContentModificationUtils
                .modifyModule(cpPatchDir, layerPatchId, patchedAsVersionHash, modifiedModule);

        final File subModulePath = IoUtils.newFile(tempDir, currentCpId,
                currentLayerPatchId, Constants.MODULES,
                subModuleName.replace('.', File.separatorChar),
                "main");
        final ResourceItem cp2ResourceItem1 = new ResourceItem("testFile3", "content1".getBytes());
        final Module subModule = new Module.Builder(subModuleName)
                .resourceRoot(cp1ResourceItem1)
                .resourceRoot(cp1ResourceItem2)
                .resourceRoot(cp2ResourceItem1)
                .build();
        final byte[] currentSubmoduleHash = HashUtils.hashFile(subModulePath);
        assert currentSubmoduleHash != null;
        final ContentModification subModuleModified = ContentModificationUtils
                .modifyModule(cpPatchDir, layerPatchId, currentSubmoduleHash, subModule);


        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch cpPatch = PatchBuilder.create()
                .setPatchId(cpId)
                .setDescription("A cp patch.")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(),
                        targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchId, "base", false)
                .addContentModification(subModuleModified)
                .addContentModification(versionModuleModified)
                .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, cpId, targetDir);
    }

    private File createTopModuleSlotAndSubmoduleUpdateCp2(String cpId, String asVersion, final String currentCpId, final String targetAsVersion,
            File targetDir) throws Exception {
        final String layerPatchId = "layer" + cpId;
        File cpPatchDir = mkdir(tempDir, cpId);

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // Calculate the target hash of the currently active module
        final String currentLayerPatchId = "layer" + currentCpId;
        final File originalVersionModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                versionModuleName.replace('.', File.separatorChar), ProductInfo.getVersionModuleSlot());
        byte[] patchedAsVersionHash = HashUtils.hashFile(originalVersionModulePath);
        assert patchedAsVersionHash != null;

        ContentModification versionModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, layerPatchId,
                patchedAsVersionHash, modifiedModule);

        final ResourceItem cp2ResourceItem1 = new ResourceItem("testFile3", "content1".getBytes());
        final ContentModification subModuleModified;
        {
            final File subModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                    subModuleName.replace('.', File.separatorChar), "main");
            final Module subModule = new Module.Builder(subModuleName)
                    .resourceRoot(cp1ResourceItem1)
                    .resourceRoot(cp1ResourceItem2)
                    .resourceRoot(cp2ResourceItem1)
                    .build();
            final byte[] currentSubmoduleHash = HashUtils.hashFile(subModulePath);
            assert currentSubmoduleHash != null;
            subModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, layerPatchId,
                    currentSubmoduleHash, subModule);
        }

        final ContentModification topModule41Modified;
        {
            final File topModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                    topModuleName.replace('.', File.separatorChar), "slot41");
            final Module topModule41 = new Module.Builder(topModuleName)
                    .slot("slot41")
                    .resourceRoot(cp1ResourceItem1)
                    .resourceRoot(cp1ResourceItem2)
                    .resourceRoot(cp2ResourceItem1)
                    .build();
            final byte[] currentTopModuleHash = HashUtils.hashFile(topModulePath);
            assert currentTopModuleHash != null;
            topModule41Modified = ContentModificationUtils
                    .modifyModule(cpPatchDir, layerPatchId, currentTopModuleHash, topModule41);
        }

        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch cpPatch = PatchBuilder.create().setPatchId(cpId).setDescription("A cp patch.")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchId, "base", false)
                    .addContentModification(topModule41Modified)
                    .addContentModification(subModuleModified)
                    .addContentModification(versionModuleModified)
                    .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, cpId, targetDir);
    }

    private File createTopModuleMainSlotUpdateCp2(String cpId, String asVersion, final String currentCpId, final String targetAsVersion,
            File targetDir) throws Exception {
        final String layerPatchId = "layer" + cpId;
        File cpPatchDir = mkdir(tempDir, cpId);

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // Calculate the target hash of the currently active module
        final String currentLayerPatchId = "layer" + currentCpId;
        final File originalVersionModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                versionModuleName.replace('.', File.separatorChar), ProductInfo.getVersionModuleSlot());
        byte[] patchedAsVersionHash = HashUtils.hashFile(originalVersionModulePath);
        assert patchedAsVersionHash != null;

        ContentModification versionModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, layerPatchId,
                patchedAsVersionHash, modifiedModule);

        final ResourceItem cp2ResourceItem1 = new ResourceItem("testFile3", "content1".getBytes());
        final ContentModification topModuleModified;
        {
            final File topModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                    topModuleName.replace('.', File.separatorChar), "main");
            final Module topModule = new Module.Builder(topModuleName)
                    .resourceRoot(cp1ResourceItem1)
                    .resourceRoot(cp1ResourceItem2)
                    .resourceRoot(cp2ResourceItem1)
                    .build();
            final byte[] currentTopModuleHash = HashUtils.hashFile(topModulePath);
            assert currentTopModuleHash != null;
            topModuleModified = ContentModificationUtils
                    .modifyModule(cpPatchDir, layerPatchId, currentTopModuleHash, topModule);
        }

        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch cpPatch = PatchBuilder.create().setPatchId(cpId).setDescription("A cp patch.")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchId, "base", false)
                    .addContentModification(topModuleModified)
                    .addContentModification(versionModuleModified)
                    .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, cpId, targetDir);
    }

    private File createAllUpdatedCp2(String cpId, String asVersion, final String currentCpId, final String targetAsVersion,
            File targetDir) throws Exception {
        final String layerPatchId = "layer" + cpId;
        File cpPatchDir = mkdir(tempDir, cpId);

        // Create the version module
        final String versionModuleName = ProductInfo.getVersionModule();
        final Module modifiedModule = PatchingTestUtil.createVersionModule(targetAsVersion);

        // Calculate the target hash of the currently active module
        final String currentLayerPatchId = "layer" + currentCpId;
        final File originalVersionModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                versionModuleName.replace('.', File.separatorChar), ProductInfo.getVersionModuleSlot());
        byte[] patchedAsVersionHash = HashUtils.hashFile(originalVersionModulePath);
        assert patchedAsVersionHash != null;

        ContentModification versionModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, layerPatchId,
                patchedAsVersionHash, modifiedModule);

        final ResourceItem cp2ResourceItem1 = new ResourceItem("testFile3", "content1".getBytes());
        final ContentModification subModuleModified;
        {
            final File subModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                    subModuleName.replace('.', File.separatorChar), "main");
            final Module subModule = new Module.Builder(subModuleName)
                    .resourceRoot(cp1ResourceItem1)
                    .resourceRoot(cp1ResourceItem2)
                    .resourceRoot(cp2ResourceItem1)
                    .build();
            final byte[] currentSubmoduleHash = HashUtils.hashFile(subModulePath);
            assert currentSubmoduleHash != null;
            subModuleModified = ContentModificationUtils.modifyModule(cpPatchDir, layerPatchId,
                    currentSubmoduleHash, subModule);
        }

        final ContentModification topModule41Modified;
        {
            final File topModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                    topModuleName.replace('.', File.separatorChar), "slot41");
            final Module topModule41 = new Module.Builder(topModuleName)
                    .slot("slot41")
                    .resourceRoot(cp1ResourceItem1)
                    .resourceRoot(cp1ResourceItem2)
                    .resourceRoot(cp2ResourceItem1)
                    .build();
            final byte[] currentTopModuleHash = HashUtils.hashFile(topModulePath);
            assert currentTopModuleHash != null;
            topModule41Modified = ContentModificationUtils
                    .modifyModule(cpPatchDir, layerPatchId, currentTopModuleHash, topModule41);
        }

        final ContentModification topModuleMainModified;
        {
            final File topModulePath = IoUtils.newFile(tempDir, currentCpId, currentLayerPatchId, Constants.MODULES,
                    topModuleName.replace('.', File.separatorChar), "main");
            final Module topModuleMain = new Module.Builder(topModuleName)
                    .slot("main")
                    .resourceRoot(cp1ResourceItem1)
                    .resourceRoot(cp1ResourceItem2)
                    .resourceRoot(cp2ResourceItem1)
                    .build();
            final byte[] currentTopModuleHash = HashUtils.hashFile(topModulePath);
            assert currentTopModuleHash != null;
            topModuleMainModified = ContentModificationUtils
                    .modifyModule(cpPatchDir, layerPatchId, currentTopModuleHash, topModuleMain);
        }

        ProductConfig productConfig = new ProductConfig(PRODUCT, asVersion, "main");
        Patch cpPatch = PatchBuilder.create().setPatchId(cpId).setDescription("A cp patch.")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), targetAsVersion)
                .getParent()
                .upgradeElement(layerPatchId, "base", false)
                    .addContentModification(topModuleMainModified)
                    .addContentModification(topModule41Modified)
                    .addContentModification(subModuleModified)
                    .addContentModification(versionModuleModified)
                    .getParent()
                .build();
        createPatchXMLFile(cpPatchDir, cpPatch);
        return createZippedPatchFile(cpPatchDir, cpId, targetDir);
    }
}
