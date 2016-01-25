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

import java.io.File;

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
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class CPRollingbackOneOffTestCase extends AbstractPatchingTestCase {

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
        File patchDir = mkdir(tempDir, patchID);
        final String patchElement1Id = randomString();

        // create a module to be updated w/o a conflict
        File baseModuleDir = newFile(new File(PatchingTestUtil.AS_DISTRIBUTION), MODULES, SYSTEM, LAYERS, BASE);
        String moduleAName = "org.a.module-a";
        Module moduleA = new Module.Builder(moduleAName)
                .miscFile(new ResourceItem("resource-a", "resource a in the module".getBytes()))
                .build();
        File moduleADir = moduleA.writeToDisk(new File(MODULES_PATH));
        String moduleBName = "org.b.module-b";
        Module moduleB = new Module.Builder(moduleBName)
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

        controller.start();
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

        controller.start();
        try {
            ctx.connectController();
            ctx.handle("patch apply " + zippedPatch.getAbsolutePath());
        } catch (Exception e) {
            ctx.terminateSession();
            throw e;
        } finally {
            controller.stop();
        }
    }
}
