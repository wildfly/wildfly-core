/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
import static org.jboss.as.test.patching.PatchingTestUtil.dump;
import static org.jboss.as.test.patching.PatchingTestUtil.randomString;
import static org.jboss.as.test.patching.PatchingTestUtil.touch;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.as.version.ProductConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;


/**
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class RollbackLastUnitTestCase extends AbstractPatchingTestCase {

    protected ProductConfig productConfig;

    @Before
    public void setup() throws Exception {
        productConfig = new ProductConfig(PatchingTestUtil.PRODUCT, PatchingTestUtil.AS_VERSION, "main");
    }

    @Test
    public void testMain() throws Exception {

        // build a one-off patch for the base installation
        // with 1 updated file
        String patchID = randomString();
        String patchElementId = randomString();
        File patchDir = mkdir(tempDir, patchID);

        // create a module to be updated w/o a conflict
        File baseModuleDir = newFile(new File(PatchingTestUtil.AS_DISTRIBUTION), MODULES, SYSTEM, LAYERS, BASE);
        String moduleName = "module-test";
        Module module = new Module.Builder(moduleName)
                .miscFile(new ResourceItem("resource-test", "new resource in the module".getBytes(StandardCharsets.UTF_8)))
                .build();
        File moduleDir = module.writeToDisk(new File(MODULES_PATH));

        // create the patch with the updated module
        ContentModification moduleModified = ContentModificationUtils.modifyModule(patchDir, patchElementId, HashUtils.hashFile(moduleDir), module);

        // create a file for the conflict
        String fileName = "file-test.txt";
        File miscFile = touch(new File(PatchingTestUtil.AS_DISTRIBUTION, "bin"), fileName);
        dump(miscFile, "original script to run standalone AS7");
        byte[] originalFileHash = HashUtils.hashFile(miscFile);
        // patch the file
        ContentModification fileModified = ContentModificationUtils.modifyMisc(patchDir, patchID, "updated script", miscFile, "bin", fileName);

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
        createPatchXMLFile(patchDir, patch);

        File zippedPatch = createZippedPatchFile(patchDir, patch.getPatchId());

        // no patches applied
        assertPatchElements(baseModuleDir, null);

        controller.start();

        // apply the patch using the cli
        CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            ctx.handle("patch apply " + zippedPatch.getAbsolutePath());
        } catch (Exception e) {
            ctx.terminateSession();
            throw e;
        } finally {
            controller.stop();
        }

        // first patch applied
        assertPatchElements(baseModuleDir, new String[]{patchElementId}, false);

        byte[] patch1FileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(originalFileHash, patch1FileHash);

        // next patch
        final String patchID2 = randomString();
        final String patchElementId2 = randomString();

        final File patchedModule = newFile(baseModuleDir, ".overlays", patchElementId, moduleName);

        Module modifiedModule = new Module.Builder(moduleName)
                .miscFile(new ResourceItem("resource-test", "another module update".getBytes(StandardCharsets.UTF_8)))
                .build();

        ContentModification fileModified2 = ContentModificationUtils.modifyMisc(patchDir, patchID2, "another file update", miscFile, "bin", fileName);
        ContentModification moduleModified2 = ContentModificationUtils.modifyModule(patchDir, patchElementId2, HashUtils.hashFile(patchedModule), modifiedModule);

        Patch patch2 = PatchBuilder.create()
                .setPatchId(patchID2)
                .setDescription(randomString())
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion() + "CP1", productConfig.getProductName() + "CP2")
                .getParent()
                .addContentModification(fileModified2)
                .upgradeElement(patchElementId2, "base", false)
                .addContentModification(moduleModified2)
                .getParent()
                .build();

        createPatchXMLFile(patchDir, patch2);
        File zippedPatch2 = createZippedPatchFile(patchDir, patch2.getPatchId());

        controller.start();
        try {
            ctx.handle("patch apply " + zippedPatch2.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            ctx.terminateSession();
            throw e;
        } finally {
            controller.stop();
        }

        // both patches applied
        assertPatchElements(baseModuleDir, new String[]{patchElementId, patchElementId2}, false);

        byte[] patch2FileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(patch1FileHash, patch2FileHash);
        assertNotEqual(originalFileHash, patch2FileHash);

        controller.start();
        try {
            ctx.handle("patch rollback --reset-configuration=false");
        } catch (Exception e) {
            ctx.terminateSession();
            throw e;
        } finally {
            controller.stop();
        }

        byte[] curFileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(curFileHash, patch2FileHash);
        assertArrayEquals(curFileHash, patch1FileHash);
        assertNotEqual(curFileHash, originalFileHash);

        controller.start();
        try {
            // only the first patch is present
            assertPatchElements(baseModuleDir, new String[]{patchElementId}, false);

            ctx.handle("patch rollback --reset-configuration=false");
        } catch (Exception e) {
            ctx.terminateSession();
            throw e;
        } finally {
            ctx.terminateSession();
            controller.stop();
        }

        try {
            controller.start();

            // no patches present
            assertPatchElements(baseModuleDir, null, false);
        } finally {
            controller.stop();
        }

        curFileHash = HashUtils.hashFile(miscFile);
        assertNotEqual(curFileHash, patch2FileHash);
        assertNotEqual(curFileHash, patch1FileHash);
        assertArrayEquals(curFileHash, originalFileHash);
    }


    private static void assertNotEqual(byte[] a1, byte[] a2) {
        if (a1.length != a2.length) {
            return;
        }
        for (int i = 0; i < a1.length; ++i) {
            if (a1[i] != a2[i]) {
                return;
            }
        }
        fail("arrays are equal");
    }
}
