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

package org.jboss.as.test.patching;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.cli.CLIPatchInfoUtil;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.runner.ContentModificationUtils;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.runner.TestUtils.createModule0;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.test.patching.PatchingTestUtil.AS_VERSION;
import static org.jboss.as.test.patching.PatchingTestUtil.BASE_MODULE_DIRECTORY;
import static org.jboss.as.test.patching.PatchingTestUtil.MODULES_PATH;
import static org.jboss.as.test.patching.PatchingTestUtil.PRODUCT;
import static org.jboss.as.test.patching.PatchingTestUtil.assertPatchElements;
import static org.jboss.as.test.patching.PatchingTestUtil.createPatchXMLFile;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class RemotePatchInfoUnitTestCase extends AbstractPatchingTestCase {

    private ByteArrayOutputStream bytesOs;
    private CommandContext ctx;

    private List<File> createdFiles = new ArrayList<File>();

    @Before
    public void before() throws Exception {
        bytesOs = new ByteArrayOutputStream();
        String controller =   "remote+http://" + NetworkUtils.formatPossibleIpv6Address(System.getProperty("node0", "127.0.0.1")) + ":9990";
        ctx = CommandContextFactory.getInstance().newCommandContext(controller, null, null, System.in, bytesOs);
    }

    @Test
    public void testMain() throws Exception {

        final File miscDir = new File(PatchingTestUtil.AS_DISTRIBUTION, "miscDir");
        createdFiles.add(miscDir);

        // prepare the patch
        String oneOffID = randomString();
        File oneOffDir = mkdir(tempDir, oneOffID);

        // create a module to be updated w/o a conflict
        String patchElementId = randomString();
        final File baseModuleDir = PatchingTestUtil.BASE_MODULE_DIRECTORY;
        String moduleName = "module-test";
        final File moduleDir = createModule0(baseModuleDir, moduleName);
        createdFiles.add(moduleDir);

        ProductConfig productConfig = new ProductConfig(PRODUCT, AS_VERSION, "main");

        final File zippedOneOff = createOneOff(oneOffDir, oneOffID, patchElementId, moduleDir, productConfig);
        handle("patch apply " + zippedOneOff.getAbsolutePath());

        final String cpId = "cp1";
        final String cpElementId = "cp1-element";
        final File cpDir = mkdir(tempDir, cpId);
        final File zippedCP = createCP(cpDir, cpId, cpElementId, patchElementId, miscDir, baseModuleDir, moduleName, productConfig);
        handle("patch apply " + zippedCP.getAbsolutePath());

        productConfig = new ProductConfig(PRODUCT, AS_VERSION + "_CP" + cpId, "main");

        final String oneOff1 = "oneOff1";
        final String oneOffElement1 = "oneOff1element";
        final File oneOff1Dir = mkdir(tempDir, oneOff1);
        final File zippedOneOff1 = createOneOff2(oneOff1Dir, oneOff1, oneOffElement1, cpElementId, miscDir, baseModuleDir,  moduleName, productConfig);
        handle("patch apply " + zippedOneOff1.getAbsolutePath());

        final String oneOff2 = "oneOff2";
        final String oneOffElement2 = "oneOff2element";
        final File oneOff2Dir = mkdir(tempDir, oneOff2);
        final File zippedOneOff2 = createOneOff2(oneOff2Dir, oneOff2, oneOffElement2, oneOffElement1, miscDir, baseModuleDir,  moduleName, productConfig);
        handle("patch apply " + zippedOneOff2.getAbsolutePath());

        handle("patch info");
        Map<String, String> table = CLIPatchInfoUtil.parseTable(bytesOs.toByteArray());
        assertEquals(3, table.size());
        assertEquals(AS_VERSION, table.get("Version")); // the CP didn't include the version module update
        assertEquals(cpId, table.get("Cumulative patch ID"));
        assertEquals(oneOff2 + ',' + oneOff1, table.get("One-off patches"));

        handle("patch info --verbose");
        final ByteArrayInputStream bis = new ByteArrayInputStream(bytesOs.toByteArray());
        final InputStreamReader reader = new InputStreamReader(bis, StandardCharsets.UTF_8);
        final BufferedReader buf = new BufferedReader(reader);
        try {
            table = CLIPatchInfoUtil.parseTable(buf);
            assertEquals(3, table.size());
            assertEquals(AS_VERSION, table.get("Version"));
            assertEquals(cpId, table.get("Cumulative patch ID"));
            assertEquals(oneOff2 + ',' + oneOff1, table.get("One-off patches"));
            // layers
            table = CLIPatchInfoUtil.parseTable(buf);
            assertEquals(3, table.size());
            assertEquals(table.toString(), "base", table.get("Layer"));
            assertEquals(cpElementId, table.get("Cumulative patch ID"));
            assertEquals(oneOffElement2 + ',' + oneOffElement1, table.get("One-off patches"));
        } finally {
            IoUtils.safeClose(buf);
        }
    }

    protected File createCP(File cpDir, String cpID, String elementCpID, String overridenElementId, final File miscDir,
            final File baseModuleDir, String moduleName,
            final ProductConfig productConfig) throws Exception {

        final File patchedModule = IoUtils.newFile(baseModuleDir, ".overlays", overridenElementId, moduleName);

        final ContentModification fileModified2 = ContentModificationUtils.modifyMisc(cpDir, cpID, "another file update", new File(miscDir, "test-file"), "miscDir", "test-file");
        final ContentModification moduleModified2 = ContentModificationUtils.modifyModule(cpDir, elementCpID, patchedModule, "another module update");

        final Patch cp = PatchBuilder.create()
                .setPatchId(cpID)
                .setDescription(descriptionFor(cpID))
                .setLink("http://test.two")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion() + "_CP" + cpID)
                .getParent()
                .addContentModification(fileModified2)
                .upgradeElement(elementCpID, "base", false)
                .setDescription(descriptionFor(elementCpID))
                .addContentModification(moduleModified2)
                .getParent()
                .build();
        createPatchXMLFile(cpDir, cp);
        File zippedCP = PatchingTestUtil.createZippedPatchFile(cpDir, cpID);
        return zippedCP;
    }

    protected File createOneOff2(File patchDir, String patchId, String elementId, String overridenElementId, final File miscDir,
            final File baseModuleDir, String moduleName,
            final ProductConfig productConfig) throws Exception {

        final File patchedModule = IoUtils.newFile(baseModuleDir, ".overlays", overridenElementId, moduleName);
        final ContentModification fileModified = ContentModificationUtils.modifyMisc(patchDir, patchId, "another file update", new File(miscDir, "test-file"), "miscDir", "test-file");
        final ContentModification moduleModified = ContentModificationUtils.modifyModule(patchDir, elementId, patchedModule, "another module update");

        final Patch patch = PatchBuilder.create()
                .setPatchId(patchId)
                .setDescription(descriptionFor(patchId))
                .setLink("http://test.two")
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .addContentModification(fileModified)
                .oneOffPatchElement(elementId, "base", false)
                .setDescription(descriptionFor(elementId))
                .addContentModification(moduleModified)
                .getParent()
                .build();
        createPatchXMLFile(patchDir, patch);
        return PatchingTestUtil.createZippedPatchFile(patchDir, patchId);
    }

    protected File createOneOff(File oneOffDir, String oneOffId, String patchElementId, File moduleDir, ProductConfig productConfig) throws Exception {
        // patch misc file
        final ContentModification miscFileAdded = ContentModificationUtils.addMisc(oneOffDir, oneOffId, "Hello World!", "miscDir", "test-file");
        // patch module
        final ContentModification moduleModified = ContentModificationUtils.modifyModule(oneOffDir, patchElementId, moduleDir, "new resource in the module");

        final Patch oneOff = PatchBuilder.create()
                .setPatchId(oneOffId)
                .setDescription(descriptionFor(oneOffId))
                .setLink("http://test.one")
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .addContentModification(miscFileAdded)
                .oneOffPatchElement(patchElementId, "base", false)
                .setDescription(descriptionFor(patchElementId))
                .addContentModification(moduleModified)
                .getParent()
                .build();
        createPatchXMLFile(oneOffDir, oneOff);
        return PatchingTestUtil.createZippedPatchFile(oneOffDir, oneOffId);
    }

    @Override
    protected void rollbackAllPatches() throws Exception {

        boolean success = true;

        try {
            final String infoCommand = "patch info --json-output";
            final String rollbackCommand = "patch rollback --patch-id=%s --reset-configuration=true";
            boolean doRollback = true;
            while (doRollback) {
                doRollback = false;

                final String output = handle(infoCommand);
                final ModelNode result = ModelNode.fromJSONString(output).get("result");
                if (result.has("patches")) {
                    final List<ModelNode> patchesList = result.get("patches").asList();
                    if (!patchesList.isEmpty()) {
                        doRollback = true;
                        for (ModelNode n : patchesList) {
                            String command = String.format(rollbackCommand, n.asString());
                            handle(command);
                        }
                    }
                }
                if (result.has("cumulative-patch-id")) {
                    final String cumulativePatchId = result.get("cumulative-patch-id").asString();
                    if (!cumulativePatchId.equalsIgnoreCase(BASE)) {
                        doRollback = true;
                        String command = String.format(rollbackCommand, cumulativePatchId);
                        handle(command);
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            success = false;
        }

        if(ctx != null) {
            ctx.terminateSession();
        }

        for(File f : createdFiles) {
            if(IoUtils.recursiveDelete(f)) {
                f.deleteOnExit();
            }
        }

        assertPatchElements(new File(MODULES_PATH), null);
        if(!success) {
            // Reset installation state
            final File home = new File(PatchingTestUtil.AS_DISTRIBUTION);
            PatchingTestUtil.resetInstallationState(home, BASE_MODULE_DIRECTORY);
            Assert.fail("Failed to rollback applied patches");
        }
    }

    private String handle(final String line) throws CommandLineException {
        controller.start();
        if(ctx.getModelControllerClient() == null) {
            ctx.connectController();
        }
        bytesOs.reset();
        ctx.handle(line);
        controller.stop();
        return new String(bytesOs.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String descriptionFor(String patchId) {
        return "description for " + patchId;
    }
}
