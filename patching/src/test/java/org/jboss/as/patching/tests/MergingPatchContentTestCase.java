/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.HashUtils.hashFile;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.runner.PatchingAssert.assertDefinedAbsentModule;
import static org.jboss.as.patching.runner.PatchingAssert.assertDefinedModule;
import static org.jboss.as.patching.runner.PatchingAssert.assertDirExists;
import static org.jboss.as.patching.runner.PatchingAssert.assertFileContent;
import static org.jboss.as.patching.runner.PatchingAssert.assertFileDoesNotExist;
import static org.jboss.as.patching.runner.PatchingAssert.assertFileExists;
import static org.jboss.as.patching.runner.PatchingAssert.assertPatchHasBeenApplied;
import static org.jboss.as.patching.runner.TestUtils.createPatchXMLFile;
import static org.jboss.as.patching.runner.TestUtils.createZippedPatchFile;
import static org.jboss.as.patching.runner.TestUtils.dump;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.touch;

import java.io.File;
import java.io.IOException;

import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.metadata.PatchMerger;
import org.jboss.as.patching.runner.AbstractTaskTestCase;
import org.jboss.as.patching.runner.ContentModificationUtils;
import org.jboss.as.patching.tool.PatchTool;
import org.jboss.as.patching.tool.PatchingResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class MergingPatchContentTestCase extends AbstractTaskTestCase {

    final String moduleName = "org.jboss.test";

    private File standaloneSh;
    private byte[] originalStandaloneHash;

    private File addedByCP1;
    private File addedByCP1RemovedByCP3;
    private File addedByCP2;

    private Patch cp1;
    private String baseCP1ID = "base-CP1";
    private String layer1CP1ID = "layer1-CP1";
    private String layer2CP1ID = "layer2-CP1";
    private ContentModification cp1BaseModuleAdded;
    private ContentModification cp1BaseModule2Added;
    private ContentModification cp1Layer1ModuleAdded;
    private ContentModification cp1Layer2ModuleAdded;
    private ContentModification cp1Layer2Module2Added;
    private ContentModification cp1StandaloneModified;
    private ContentModification cp1AddedByCP1Added;

    private Patch cp2;
    private String baseCP2ID = "base-CP2";
    private String layer2CP2ID = "layer2-CP2";
    private ContentModification cp2BaseModuleModified;
    private ContentModification cp2BaseModule2Removed;
    private ContentModification cp2StandaloneModified;
    private ContentModification cp2AddedByCp2Added;
    private ContentModification cp2Layer2ModuleRemoved;
    private ContentModification cp2Layer2Module3Added;

    private Patch cp3;
    private String baseCP3ID = "base-CP3";
    private ContentModification cp3BaseModuleModified;
    private ContentModification cp3BaseModule2Added;
    private ContentModification cp3StandaloneModified;

    private File cp1Zip;
    private File cp2Zip;
    private File cp3Zip;
    private File mergedZip;

    private PatchTool tool;

    @Before
    public void init() throws Exception {

        installLayers("layer1", "layer2");

        final File binDir = mkdir(env.getInstalledImage().getJbossHome(), "bin");

        standaloneSh = touch(binDir, "standalone.sh");
        dump(standaloneSh, "original script to run standalone server");
        originalStandaloneHash = hashFile(standaloneSh);

        addedByCP1 = new File(binDir, "added-by-cp1.txt");
        addedByCP1RemovedByCP3 = new File(binDir, "added-by-cp1-removed-by-cp3.txt");
        addedByCP2 = new File(binDir, "added-by-cp2.txt");

        tool = newPatchTool();
    }

    /**
     * This test creates three consequent CPs and applies them one by one first.
     * Then creates a merged CP.
     * Then rollsback one by one applying the merged CP and verifying the latest CP
     * has been applied.
     * This also tests the "undo", i.e. a rollback as one step, of the merged CP.
     */
    @Test
    public void testMain() throws Exception {

        final InstalledIdentity installedIdentity = loadInstalledIdentity();

        PatchingResult result;

        assertNotPatched();

        prepareCP1(installedIdentity);
        result = executePatch(tool, cp1Zip);
        assertCP1State(result);

        prepareCP2(installedIdentity);
        result = executePatch(tool, cp2Zip);
        assertCP2State(result);

        prepareCP3(installedIdentity);
        result = executePatch(tool, cp3Zip);
        assertCP3State(result);

//        ls(installedIdentity.getInstalledImage().getJbossHome());

        mergedZip = new File(tempDir, "merged-patch.zip");
        PatchMerger.merge(cp1Zip, cp2Zip, mergedZip);
        PatchMerger.merge(mergedZip, cp3Zip, mergedZip);

//        File mergedDir = mkdir(tempDir, "merged2");
//        ZipUtils.unzip(mergedZip, mergedDir);
//        ls(mergedDir);
//        less(new File(mergedDir, "patch.xml"));

        result = rollback(tool, cp3.getPatchId());
        assertCP2State(result);
        result = executePatch(tool, mergedZip);
        assertCP3State(result);

        result = rollback(tool, cp3.getPatchId());
        assertCP2State(result);
        result = rollback(tool, cp2.getPatchId());
        assertCP1State(result);
        result = executePatch(tool, mergedZip);
        assertCP3State(result);

        result = rollback(tool, cp3.getPatchId());
        assertCP1State(result);
        result = rollback(tool, cp1.getPatchId());
        assertNotPatched();

        result = executePatch(tool, mergedZip);
        assertCP3State(result);

        result = rollback(tool, cp3.getPatchId());
        assertNotPatched();
    }

    private void assertCP3State(PatchingResult result) throws Exception {
        assertPatchHasBeenApplied(result, cp3);

        assertFileExists(standaloneSh);
        assertFileContent(cp3StandaloneModified.getItem().getContentHash(), standaloneSh);
        assertFileExists(addedByCP1);
        assertFileContent(cp1AddedByCP1Added.getItem().getContentHash(), addedByCP1);
        assertFileDoesNotExist(addedByCP1RemovedByCP3);
        assertFileExists(addedByCP2);
        assertFileContent(cp2AddedByCp2Added.getItem().getContentHash(), addedByCP2);

        final InstalledIdentity installedIdentity = loadInstalledIdentity();
        File modulePatchDir = installedIdentity.getLayer(BASE).loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(baseCP3ID);
        assertDirExists(modulePatchDir);
        assertDefinedModule(modulePatchDir, moduleName, cp3BaseModuleModified.getItem().getContentHash());
        assertDefinedModule(modulePatchDir, moduleName + 2, cp3BaseModule2Added.getItem().getContentHash());

        modulePatchDir = installedIdentity.getLayer("layer1").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(layer1CP1ID);
        assertDirExists(modulePatchDir);
        assertDefinedModule(modulePatchDir, moduleName, cp1Layer1ModuleAdded.getItem().getContentHash());

        modulePatchDir = installedIdentity.getLayer("layer2").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(layer2CP2ID);
        assertDirExists(modulePatchDir);
        assertDefinedModule(modulePatchDir, moduleName + "2", cp1Layer2Module2Added.getItem().getContentHash());
        assertDefinedModule(modulePatchDir, moduleName + "3", cp2Layer2Module3Added.getItem().getContentHash());
        if(IoUtils.newFile(modulePatchDir, "org", "jboss", "test").exists()) {
            assertDefinedAbsentModule(modulePatchDir, moduleName);
        }
    }

    private void assertCP2State(PatchingResult result) throws Exception {
        assertPatchHasBeenApplied(result, cp2);

        assertFileExists(standaloneSh);
        assertFileContent(cp2StandaloneModified.getItem().getContentHash(), standaloneSh);
        assertFileExists(addedByCP1);
        assertFileContent(cp1AddedByCP1Added.getItem().getContentHash(), addedByCP1);
        assertFileExists(addedByCP1RemovedByCP3);
        assertFileExists(addedByCP2);
        assertFileContent(cp2AddedByCp2Added.getItem().getContentHash(), addedByCP2);

        final InstalledIdentity installedIdentity = loadInstalledIdentity();
        File modulePatchDiry = installedIdentity.getLayer(BASE).loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(baseCP2ID);
        assertDirExists(modulePatchDiry);
        assertDefinedModule(modulePatchDiry, moduleName, cp2BaseModuleModified.getItem().getContentHash());
        assertDefinedAbsentModule(modulePatchDiry, moduleName + "2");

        modulePatchDiry = installedIdentity.getLayer("layer1").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(layer1CP1ID);
        assertDirExists(modulePatchDiry);
        assertDefinedModule(modulePatchDiry, moduleName, cp1Layer1ModuleAdded.getItem().getContentHash());

        modulePatchDiry = installedIdentity.getLayer("layer2").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(layer2CP2ID);
        assertDirExists(modulePatchDiry);
        assertDefinedAbsentModule(modulePatchDiry, moduleName);
        assertDefinedModule(modulePatchDiry, moduleName + "2", cp1Layer2Module2Added.getItem().getContentHash());
        assertDefinedModule(modulePatchDiry, moduleName + "3", cp2Layer2Module3Added.getItem().getContentHash());
    }

    private void assertCP1State(PatchingResult result) throws Exception {
        assertPatchHasBeenApplied(result, cp1);

        assertFileExists(standaloneSh);
        assertFileContent(cp1StandaloneModified.getItem().getContentHash(), standaloneSh);
        assertFileExists(addedByCP1);
        assertFileContent(cp1AddedByCP1Added.getItem().getContentHash(), addedByCP1);
        assertFileExists(addedByCP1RemovedByCP3);
        assertFileDoesNotExist(addedByCP2);

        final InstalledIdentity installedIdentity = loadInstalledIdentity();
        File modulePatchDir = installedIdentity.getLayer(BASE).loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(baseCP1ID);
        assertDirExists(modulePatchDir);
        assertDefinedModule(modulePatchDir, moduleName, cp1BaseModuleAdded.getItem().getContentHash());
        assertDefinedModule(modulePatchDir, moduleName + "2", cp1BaseModule2Added.getItem().getContentHash());

        modulePatchDir = installedIdentity.getLayer("layer1").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(layer1CP1ID);
        assertDirExists(modulePatchDir);
        assertDefinedModule(modulePatchDir, moduleName, cp1Layer1ModuleAdded.getItem().getContentHash());

        modulePatchDir = installedIdentity.getLayer("layer2").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(layer2CP1ID);
        assertDirExists(modulePatchDir);
        assertDefinedModule(modulePatchDir, moduleName, cp1Layer2ModuleAdded.getItem().getContentHash());
        assertDefinedModule(modulePatchDir, moduleName + "2", cp1Layer2Module2Added.getItem().getContentHash());
    }

    private void assertNotPatched() throws Exception {

        assertFileExists(standaloneSh);
        assertFileContent(originalStandaloneHash, standaloneSh);
        assertFileDoesNotExist(addedByCP1);
        assertFileDoesNotExist(addedByCP1RemovedByCP3);
        assertFileDoesNotExist(addedByCP2);

        final InstalledIdentity installedIdentity = loadInstalledIdentity();
        Assert.assertTrue(installedIdentity.getAllInstalledPatches().isEmpty());
    }

    private void prepareCP3(final InstalledIdentity installedIdentity) throws IOException, Exception {
        final String cp3ID = "CP3";
        final File cp3Dir = mkdir(tempDir, cp3ID);

        cp3StandaloneModified = ContentModificationUtils.modifyMisc(cp3Dir, cp3ID, "updated by cp3", standaloneSh, "bin", standaloneSh.getName());
        cp3BaseModuleModified = ContentModificationUtils.modifyModule(cp3Dir, baseCP3ID, moduleName, cp2BaseModuleModified.getItem().getContentHash(), "cp3 content");

        // cp3BaseModule2Added = ContentModificationUtils.addModule(cp3Dir, baseCP3ID, moduleName + "2"); the patchgen tool
        // would generate an update instead
        final File absentModuleXml = IoUtils.newFile(installedIdentity.getLayer("base").loadTargetInfo()
                .getDirectoryStructure().getModulePatchDirectory(baseCP2ID), "org", "jboss", "test2", "main", "module.xml");
        cp3BaseModule2Added = ContentModificationUtils.modifyModule(cp3Dir, baseCP3ID, moduleName + "2",
                HashUtils.hashFile(absentModuleXml), "cp3 content");

        final ContentModification cp3AddedByCP1RemovedByCP3Removed = ContentModificationUtils.removeMisc(addedByCP1RemovedByCP3, "bin", addedByCP1RemovedByCP3.getName());

        cp3 = PatchBuilder.create()
                .setPatchId(cp3ID)
                .setDescription(randomString())
                .upgradeIdentity(installedIdentity.getIdentity().getName(), productConfig.getProductVersion() + "_CP2", productConfig.getProductVersion() + "_CP3")
                    .getParent()
                .upgradeElement(baseCP3ID, BASE, false)
                    .addContentModification(cp3BaseModuleModified)
                    .addContentModification(cp3BaseModule2Added)
                    .getParent()
                .addContentModification(cp3StandaloneModified)
                .addContentModification(cp3AddedByCP1RemovedByCP3Removed)
                .build();
        createPatchXMLFile(cp3Dir, cp3);
        cp3Zip = createZippedPatchFile(cp3Dir, cp3ID);
    }

    private void prepareCP2(final InstalledIdentity installedIdentity) throws Exception {

        final String cp2ID = "CP2";
        final File cp2Dir = mkdir(tempDir, cp2ID);

        cp2BaseModuleModified = ContentModificationUtils.modifyModule(cp2Dir, baseCP2ID, moduleName, cp1BaseModuleAdded.getItem().getContentHash(), "cp2 content");
        final File baseModule2Dir = IoUtils.newFile(
                installedIdentity.getLayer("base").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(baseCP1ID),
                "org", "jboss", "test2");
        cp2BaseModule2Removed = ContentModificationUtils.removeModule(baseModule2Dir, moduleName + 2);

        final File layer2ModuleDir = IoUtils.newFile(
                installedIdentity.getLayer("layer2").loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(layer2CP1ID),
                "org", "jboss", "test");
        cp2Layer2ModuleRemoved = ContentModificationUtils.removeModule(layer2ModuleDir, moduleName);
        cp2Layer2Module3Added = ContentModificationUtils.addModule(cp2Dir, layer2CP2ID, moduleName + "3");

        cp2StandaloneModified = ContentModificationUtils.modifyMisc(cp2Dir, cp2ID, "updated by cp2", cp1StandaloneModified.getItem().getContentHash(), "bin", standaloneSh.getName());
        cp2AddedByCp2Added = ContentModificationUtils.addMisc(cp2Dir, cp2ID, "added by cp2", "bin", addedByCP2.getName());

        cp2 = PatchBuilder.create()
                .setPatchId(cp2ID)
                .setDescription(randomString())
                .upgradeIdentity(installedIdentity.getIdentity().getName(), productConfig.getProductVersion() + "_CP1", productConfig.getProductVersion() + "_CP2")
                    .getParent()
                .upgradeElement(baseCP2ID, BASE, false)
                    .addContentModification(cp2BaseModuleModified)
                    .addContentModification(cp2BaseModule2Removed)
                    .getParent()
                .upgradeElement(layer2CP2ID, "layer2", false)
                    .addContentModification(cp2Layer2ModuleRemoved)
                    .addContentModification(cp2Layer2Module3Added)
                    .getParent()
                .addContentModification(cp2StandaloneModified)
                .addContentModification(cp2AddedByCp2Added)
                .build();
        createPatchXMLFile(cp2Dir, cp2);
        cp2Zip = createZippedPatchFile(cp2Dir, cp2ID);
    }

    private void prepareCP1(final InstalledIdentity installedIdentity) throws Exception {

        final String cp1ID = "CP1";
        final File cp1Dir = mkdir(tempDir, cp1ID);

        cp1BaseModuleAdded = ContentModificationUtils.addModule(cp1Dir, baseCP1ID, moduleName);
        cp1BaseModule2Added = ContentModificationUtils.addModule(cp1Dir, baseCP1ID, moduleName + 2);
        cp1Layer1ModuleAdded = ContentModificationUtils.addModule(cp1Dir, layer1CP1ID, moduleName);
        cp1Layer2ModuleAdded = ContentModificationUtils.addModule(cp1Dir, layer2CP1ID, moduleName);
        cp1Layer2Module2Added = ContentModificationUtils.addModule(cp1Dir, layer2CP1ID, moduleName + "2");
        cp1StandaloneModified = ContentModificationUtils.modifyMisc(cp1Dir, cp1ID, "updated by cp1", standaloneSh, "bin", standaloneSh.getName());
        cp1AddedByCP1Added = ContentModificationUtils.addMisc(cp1Dir, cp1ID, "added by cp1", "bin", addedByCP1.getName());
        final ContentModification cp1AddedByCP1RemovedByCP3Added = ContentModificationUtils.addMisc(cp1Dir, cp1ID, "added by cp1", "bin", addedByCP1RemovedByCP3.getName());

        cp1 = PatchBuilder.create()
                .setPatchId(cp1ID)
                .setDescription(randomString())
                .upgradeIdentity(installedIdentity.getIdentity().getName(), installedIdentity.getIdentity().getVersion(), productConfig.getProductVersion() + "_CP1")
                    .getParent()
                .upgradeElement(baseCP1ID, BASE, false)
                    .addContentModification(cp1BaseModuleAdded)
                    .addContentModification(cp1BaseModule2Added)
                    .getParent()
                .upgradeElement(layer1CP1ID, "layer1", false)
                    .addContentModification(cp1Layer1ModuleAdded)
                    .getParent()
                .upgradeElement(layer2CP1ID, "layer2", false)
                    .addContentModification(cp1Layer2ModuleAdded)
                    .addContentModification(cp1Layer2Module2Added)
                    .getParent()
                .addContentModification(cp1StandaloneModified)
                .addContentModification(cp1AddedByCP1Added)
                .addContentModification(cp1AddedByCP1RemovedByCP3Added)
                .build();
        createPatchXMLFile(cp1Dir, cp1);
        cp1Zip = createZippedPatchFile(cp1Dir, cp1ID);
    }
}
