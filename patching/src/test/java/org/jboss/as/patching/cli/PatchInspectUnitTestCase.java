/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.cli;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.Constants.SYSTEM;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.runner.TestUtils.createBundle0;
import static org.jboss.as.patching.runner.TestUtils.createInstalledImage;
import static org.jboss.as.patching.runner.TestUtils.createModule0;
import static org.jboss.as.patching.runner.TestUtils.createPatchXMLFile;
import static org.jboss.as.patching.runner.TestUtils.createZippedPatchFile;
import static org.jboss.as.patching.runner.TestUtils.dump;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.touch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.metadata.BundledPatch;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.runner.AbstractTaskTestCase;
import org.jboss.as.patching.runner.ContentModificationUtils;
import org.jboss.as.patching.runner.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 *
 * @author Alexey Loubyansky
 */
public class PatchInspectUnitTestCase extends AbstractTaskTestCase {

    private ByteArrayOutputStream bytesOs;
    private CommandContext ctx;

    @Before
    public void before() throws Exception {
        bytesOs = new ByteArrayOutputStream();
        // to avoid the need to reset the terminal manually after the tests, e.g. 'stty sane'
        org.jboss.aesh.console.settings.Settings.getInstance().setTerminal(new org.jboss.aesh.terminal.TestTerminal());
        ctx = CommandContextFactory.getInstance().newCommandContext(null, null, null, System.in, bytesOs);
    }

    @After
    public void after() throws Exception {
        if(ctx != null) {
            ctx.terminateSession();
        }
        if(bytesOs != null) {
            bytesOs = null;
        }
    }

    @Test
    public void testMain() throws Exception {
        final File binDir = createInstalledImage(env, "patch-inspect-test", productConfig.getProductName(), productConfig.getProductVersion());

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
        // patch the file
        ContentModification fileModified = ContentModificationUtils.modifyMisc(patchDir, patchID, "updated script", miscFile, "bin", fileName);

        // create a bundle to be updated w/o a conflict
        File baseBundleDir = newFile(env.getInstalledImage().getBundlesDir(), SYSTEM, LAYERS, BASE);
        String bundleName = "bundle-test";
        File bundleDir = createBundle0(baseBundleDir, bundleName, "bundle content");
        // patch the bundle
        ContentModification bundleModified = ContentModificationUtils.modifyBundle(patchDir, patchElementId, bundleDir, "updated bundle content");

        //TestUtils.tree(env.getInstalledImage().getJbossHome());

        final String patchIDDescr = "this is one-off patch 1";
        final String oneOffElementDescr = "one-off element";
        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(patchIDDescr)
                .setLink("http://test.one")
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .addContentModification(fileModified)
                .oneOffPatchElement(patchElementId, "base", false)
                .setDescription(oneOffElementDescr)
                .addContentModification(moduleModified)
                .addContentModification(bundleModified)
                .getParent()
                .build();

        // create the patch
        createPatchXMLFile(patchDir, patch, false);

        final File zippedOneOff = createZippedPatchFile(patchDir, patch.getPatchId());

        // next patch
        final String patchID2 = randomString();
        final String patchElementId2 = randomString();

        ContentModification fileModified2 = fileModified;
        ContentModification moduleModified2 = moduleModified;
        ContentModification bundleModified2 = bundleModified;

        final String patchID2Descr = "This is cumulative patch 2";
        final String cpElementDescr = "CP element";
        Patch patch2 = PatchBuilder.create()
                .setPatchId(patchID2)
                .setDescription(patchID2Descr)
                .setLink("http://test.two")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductName() + "CP2")
                .getParent()
                .addContentModification(fileModified2)
                .upgradeElement(patchElementId2, "base", false)
                .setDescription(cpElementDescr)
                .addContentModification(moduleModified2)
                .addContentModification(bundleModified2)
                .getParent()
                .build();

        createPatchXMLFile(patchDir, patch2, false);
        final File zippedCP = createZippedPatchFile(patchDir, patch2.getPatchId());

        //TestUtils.tree(env.getInstalledImage().getJbossHome());

        bytesOs.reset();
        ctx.handle("patch inspect " + zippedOneOff.getAbsolutePath());
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), patchID, "http://test.one", true,
                productConfig.getProductName(), productConfig.getProductVersion(), patchIDDescr);

        final Map<String,String> oneOffElements = new HashMap<String,String>();
        oneOffElements.put("Patch ID", patchElementId);
        oneOffElements.put("Name", "base");
        oneOffElements.put("Type", "layer");
        oneOffElements.put("Description", oneOffElementDescr);
        bytesOs.reset();
        ctx.handle("patch inspect " + zippedOneOff.getAbsolutePath() + " --verbose");
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), patchID, "http://test.one", true,
                productConfig.getProductName(), productConfig.getProductVersion(), patchIDDescr, Collections.singletonList(oneOffElements));

        bytesOs.reset();
        ctx.handle("patch inspect " + zippedCP);
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), patchID2, "http://test.two", false,
                productConfig.getProductName(), productConfig.getProductVersion(), patchID2Descr);

        final Map<String,String> cpElements = new HashMap<String,String>();
        cpElements.put("Patch ID", patchElementId2);
        cpElements.put("Name", "base");
        cpElements.put("Type", "layer");
        cpElements.put("Description", cpElementDescr);
        bytesOs.reset();
        ctx.handle("patch inspect " + zippedCP + " --verbose");
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), patchID2, "http://test.two", false,
                productConfig.getProductName(), productConfig.getProductVersion(), patchID2Descr, Collections.singletonList(cpElements));

        // Bundle
        final File patchBundleDir = mkdir(tempDir, "bundle");
        IoUtils.copy(zippedOneOff, new File(patchBundleDir, zippedOneOff.getName()));
        IoUtils.copy(zippedCP, new File(patchBundleDir, zippedCP.getName()));
        final List<BundledPatch.BundledPatchEntry> bundledPatches = new ArrayList<BundledPatch.BundledPatchEntry>();
        bundledPatches.add(new BundledPatch.BundledPatchEntry(patchID, zippedOneOff.getName()));
        bundledPatches.add(new BundledPatch.BundledPatchEntry(patchID2, zippedCP.getName()));
        TestUtils.createPatchBundleXMLFile(patchBundleDir, bundledPatches);
        final File zippedBundle = createZippedPatchFile(patchBundleDir, "patch-bundle.zip");

        bytesOs.reset();
        ctx.handle("patch inspect " + zippedBundle);

        ByteArrayInputStream bis = new ByteArrayInputStream(bytesOs.toByteArray());
        BufferedReader reader = new BufferedReader(new InputStreamReader(bis));
        try {
            assertTrue(reader.ready());
            CLIPatchInfoUtil.assertPatchInfo(reader, patchID, "http://test.one", true, productConfig.getProductName(),
                    productConfig.getProductVersion(), patchIDDescr);
            assertTrue(reader.ready());
            CLIPatchInfoUtil.assertPatchInfo(reader, patchID2, "http://test.two", false, productConfig.getProductName(),
                    productConfig.getProductVersion(), patchID2Descr);
            assertFalse(reader.ready());
        } finally {
            IoUtils.safeClose(bis);
        }

        bytesOs.reset();
        ctx.handle("patch inspect " + zippedBundle + " --verbose");

        bis = new ByteArrayInputStream(bytesOs.toByteArray());
        reader = new BufferedReader(new InputStreamReader(bis));
        try {
            assertTrue(reader.ready());
            assertEquals("CONTENT OF " + zippedOneOff.getName() + ':', reader.readLine());
            assertTrue(reader.ready());
            assertEquals("", reader.readLine());
            assertTrue(reader.ready());
            CLIPatchInfoUtil.assertPatchInfo(reader, patchID, "http://test.one", true,
                    productConfig.getProductName(), productConfig.getProductVersion(), patchIDDescr, Collections.singletonList(oneOffElements));
            assertTrue(reader.ready());
            assertEquals("CONTENT OF " + zippedCP.getName() + ':', reader.readLine());
            assertTrue(reader.ready());
            assertEquals("", reader.readLine());
            assertTrue(reader.ready());
            CLIPatchInfoUtil.assertPatchInfo(reader, patchID2, "http://test.two", false,
                    productConfig.getProductName(), productConfig.getProductVersion(), patchID2Descr, Collections.singletonList(cpElements));
            assertFalse(reader.ready());
        } finally {
            IoUtils.safeClose(bis);
        }
    }
}
