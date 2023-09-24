/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.Constants.SYSTEM;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.runner.PatchingAssert.assertDefinedAbsentModule;
import static org.jboss.as.patching.runner.PatchingAssert.assertDefinedModule;
import static org.jboss.as.patching.runner.PatchingAssert.assertDirExists;
import static org.jboss.as.patching.runner.PatchingAssert.assertPatchHasBeenApplied;
import static org.jboss.as.patching.runner.TestUtils.createModule0;
import static org.jboss.as.patching.runner.TestUtils.createPatchXMLFile;
import static org.jboss.as.patching.runner.TestUtils.createZippedPatchFile;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.tree;

import java.io.File;

import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.tool.PatchingResult;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012, Red Hat Inc
 */
public class ModuleTaskTestCase extends AbstractTaskTestCase{

    @Test
    public void testAddModule() throws Exception {
        // build a one-off patch for the base installation
        // with 1 added module
        String patchID = randomString();
        File patchDir = mkdir(tempDir, patchID);
        String baseLayerPatchID = randomString();
        String moduleName = randomString();

        ContentModification moduleAdded = ContentModificationUtils.addModule(patchDir, baseLayerPatchID, moduleName);

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(baseLayerPatchID, BASE, false)
                    .addContentModification(moduleAdded)
                    .getParent()
                .build();

        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patchID);

        PatchingResult result = executePatch(zippedPatch);

        assertPatchHasBeenApplied(result, patch);

        InstalledIdentity updatedInstalledIdentity = loadInstalledIdentity();
        File modulePatchDirectory = updatedInstalledIdentity.getLayers().get(0).loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(baseLayerPatchID);
        assertDirExists(modulePatchDirectory);
        assertDefinedModule(modulePatchDirectory, moduleName, moduleAdded.getItem().getContentHash());
    }

    @Test
    public void testRemoveModule() throws Exception {
        String moduleName = randomString();

        // create an empty module in the AS7 installation
        File baseModuleDir = newFile(env.getInstalledImage().getModulesDir(), SYSTEM, LAYERS, BASE);
        File moduleDir = createModule0(baseModuleDir, moduleName);

        // build a one-off patch for the installation base layer
        // with 1 module removed
        String baseLayerPatchID = randomString();
        Patch patch = PatchBuilder.create()
                .setPatchId(randomString())
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(baseLayerPatchID, BASE, false)
                    .addContentModification(ContentModificationUtils.removeModule(moduleDir))
                    .getParent()
                .build();

        // create the patch
        File patchDir = mkdir(tempDir, patch.getPatchId());
        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        PatchingResult result = executePatch(zippedPatch);
        assertPatchHasBeenApplied(result, patch);

        InstalledIdentity installedIdentity = loadInstalledIdentity();
        File modulesPatchDir = installedIdentity.getLayers().get(0).loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(baseLayerPatchID);
        assertDirExists(modulesPatchDir);
        assertDefinedAbsentModule(modulesPatchDir, moduleName);
    }

    @Test
    public void testUpdateModule() throws Exception {
        String moduleName = randomString();

        // create an empty module in the AS7 installation base layer
        File baseModuleDir = newFile(env.getInstalledImage().getModulesDir(), SYSTEM, LAYERS, BASE);
        File moduleDir = createModule0(baseModuleDir, moduleName);

        // build a one-off patch for the base installation
        // with 1 module updated
        String patchID = randomString();
        String baseLayerPatchID = randomString();
        File patchDir = mkdir(tempDir, patchID);

        // create the patch with the updated module
        ContentModification moduleModified = ContentModificationUtils.modifyModule(patchDir, baseLayerPatchID, moduleDir, "new resource in the module");

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .oneOffPatchElement(baseLayerPatchID, BASE, false)
                    .addContentModification(moduleModified)
                    .getParent()
                .build();
        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        PatchingResult result = executePatch(zippedPatch);
        assertPatchHasBeenApplied(result, patch);

        tree(env.getInstalledImage().getJbossHome());

        InstalledIdentity installedIdentity = loadInstalledIdentity();
        File modulesPatchDir = installedIdentity.getLayers().get(0).loadTargetInfo().getDirectoryStructure().getModulePatchDirectory(baseLayerPatchID);
        assertDirExists(modulesPatchDir);
        // check that the defined module is the updated one
        assertDefinedModule(modulesPatchDir, moduleName, moduleModified.getItem().getContentHash());
    }
}
