/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.cli;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.Constants.SYSTEM;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.runner.TestUtils.createInstalledImage;
import static org.jboss.as.patching.runner.TestUtils.createModule0;
import static org.jboss.as.patching.runner.TestUtils.createPatchXMLFile;
import static org.jboss.as.patching.runner.TestUtils.createZippedPatchFile;
import static org.jboss.as.patching.runner.TestUtils.dump;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.touch;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.runner.AbstractTaskTestCase;
import org.jboss.as.patching.runner.ContentModificationUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;


/**
 * @author Alexey Loubyansky
 *
 */
public class RollbackLastUnitTestCase extends AbstractTaskTestCase {

    @Test
    public void testMain() throws Exception {

        final File binDir = createInstalledImage(env, "consoleSlot", productConfig.getProductName(), productConfig.getProductVersion());

        // build a one-off patch for the base installation
        // with 1 updated file
        String patchID = randomString();
        String patchElementId = randomString();
        File patchDir = mkdir(tempDir, patchID);

        // create a module to be updated w/o a conflict
        File baseModuleDir = newFile(env.getInstalledImage().getModulesDir(), SYSTEM, LAYERS, BASE);
        String moduleName = "module-test";
        File moduleDir = createModule0(baseModuleDir, moduleName);
        // create the patch with the updated module
        ContentModification moduleModified = ContentModificationUtils.modifyModule(patchDir, patchElementId, moduleDir, "new resource in the module");

        // create a file for the conflict
        String fileName = "file-test.txt";
        File miscFile = touch(binDir, fileName);
        dump(miscFile, "original script to run standalone AS7");
        byte[] originalFileHash = HashUtils.hashFile(miscFile);
        // patch the file
        ContentModification fileModified = ContentModificationUtils.modifyMisc(patchDir, patchID, "updated script", miscFile, "bin", fileName);

        //TestUtils.tree(env.getInstalledImage().getJbossHome());

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion() + "CP1")
                .getParent()
                .addContentModification(fileModified)
                .upgradeElement(patchElementId, "base", false)
                .addContentModification(moduleModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch, false);

        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        // no patches applied
        assertPatchElements(baseModuleDir, null);

        // apply the patch using the cli
        CommandContextConfiguration.Builder config = new CommandContextConfiguration.Builder();
        config.setInitConsole(true);
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config.build());
        try {
            ctx.handle("patch apply " + zippedPatch.getAbsolutePath() + " --distribution=" + env.getInstalledImage().getJbossHome());
        } catch(Exception e) {
            ctx.terminateSession();
            throw e;
        }

        // first patch applied
        assertPatchElements(baseModuleDir, new String[]{patchElementId});

        byte[] patch1FileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(originalFileHash, patch1FileHash);

        // next patch
        final String patchID2 = randomString();
        final String patchElementId2 = randomString();

        final File patchedModule = newFile(baseModuleDir, ".overlays", patchElementId, moduleName);

        ContentModification fileModified2 = ContentModificationUtils.modifyMisc(patchDir, patchID2, "another file update", miscFile, "bin", fileName);
        ContentModification moduleModified2 = ContentModificationUtils.modifyModule(patchDir, patchElementId2, patchedModule, "another module update");

        Patch patch2 = PatchBuilder.create()
                .setPatchId(patchID2)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion() + "CP1", productConfig.getProductVersion() + "CP2")
                .getParent()
                .addContentModification(fileModified2)
                .upgradeElement(patchElementId2, "base", false)
                .addContentModification(moduleModified2)
                .getParent()
                .build();

        createPatchXMLFile(patchDir, patch2, false);
        File zippedPatch2 = createZippedPatchFile(patchDir, patch2.getPatchId());

        try {
            ctx.handle("patch apply " + zippedPatch2.getAbsolutePath() + " --distribution=" + env.getInstalledImage().getJbossHome());
        } catch(Exception e) {
            ctx.terminateSession();
            throw e;
        }

        // both patches applied
        assertPatchElements(baseModuleDir, new String[]{patchElementId, patchElementId2});

        byte[] patch2FileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(patch1FileHash, patch2FileHash);
        assertNotEqual(originalFileHash, patch2FileHash);

        //TestUtils.tree(env.getInstalledImage().getJbossHome());
        // rollback patch-id auto-completion.
        {
            String cmd = "patch rollback " + " --distribution=" + env.getInstalledImage().getJbossHome() + " ";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.size() == 2);
            assertTrue(candidates.toString(), candidates.contains(patchID));
            assertTrue(candidates.toString(), candidates.contains(patchID2));
        }

        // info patch-id auto-completion.
        {
            String cmd = "patch info " + " --distribution=" + env.getInstalledImage().getJbossHome() + " ";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.size() == 2);
            assertTrue(candidates.toString(), candidates.contains(patchID));
            assertTrue(candidates.toString(), candidates.contains(patchID2));
        }

        try {
            ctx.handle("patch rollback --reset-configuration=false --distribution=" + env.getInstalledImage().getJbossHome());
        } catch(Exception e) {
            ctx.terminateSession();
            throw e;
        }

        // only the first patch is present
        assertPatchElements(baseModuleDir, new String[]{patchElementId});

        byte[] curFileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(curFileHash, patch2FileHash);
        assertArrayEquals(curFileHash, patch1FileHash);
        assertNotEqual(curFileHash, originalFileHash);

        try {
            ctx.handle("patch rollback --reset-configuration=false --distribution=" + env.getInstalledImage().getJbossHome());
        } catch(Exception e) {
            ctx.terminateSession();
            throw e;
        } finally {
            ctx.terminateSession();
        }

        // no patches present
        assertPatchElements(baseModuleDir, null);

        curFileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(curFileHash, patch2FileHash);
        assertNotEqual(curFileHash, patch1FileHash);
        assertArrayEquals(curFileHash, originalFileHash);
    }

    private static void assertPatchElements(File baseModuleDir, String[] patchElements) {

        File modulesPatchesDir = new File(baseModuleDir, ".overlays");
        if(!modulesPatchesDir.exists()) {
            assertNull(patchElements);
            return;
        }
        assertTrue(modulesPatchesDir.exists());
        final List<File> patchDirs = Arrays.asList(modulesPatchesDir.listFiles(new FileFilter(){
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }}));
        if(patchElements == null) {
            assertTrue(patchDirs.isEmpty());
        } else {
            final List<String> ids = Arrays.asList(patchElements);
            assertEquals(patchDirs.size(), patchElements.length);
            for (File f : patchDirs) {
                assertTrue(ids.contains(f.getName()));
            }
        }
    }

    private static void assertNotEqual(byte[] a1, byte[] a2) {
        if(a1.length != a2.length) {
            return;
        }
        for(int i = 0; i < a1.length; ++i) {
            if(a1[i] != a2[i]) {
                return;
            }
        }
        fail("arrays are equal");
    }
}
