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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.cli.CLIPatchInfoUtil;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.runner.ContentModificationUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
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
public class RemotePatchInfoPatchIdUnitTestCase extends AbstractPatchingTestCase {

    private ByteArrayOutputStream bytesOs;
    private CommandContext ctx;

    private List<File> createdFiles = new ArrayList<File>();

    @Before
    public void before() throws Exception {
        bytesOs = new ByteArrayOutputStream();

        String controller = "remote+http://" + TestSuiteEnvironment.getHttpAddress() + ":9990";
        ctx = CommandContextFactory.getInstance().newCommandContext(controller, null, null, System.in, bytesOs);
    }

    @Test
    public void testMain() throws Exception {

        final File miscDir = new File(PatchingTestUtil.AS_DISTRIBUTION, "miscDir");
        createdFiles.add(miscDir);

        final String fileContent = "Hello World!";
        // prepare the patch
        String oneOffID = randomString();
        File oneOffDir = mkdir(tempDir, oneOffID);
        ContentModification miscFileAdded = ContentModificationUtils.addMisc(oneOffDir, oneOffID,
                fileContent, "miscDir", "test-file");

        // create a module to be updated w/o a conflict
        String patchElementId = randomString();
        final File baseModuleDir = PatchingTestUtil.BASE_MODULE_DIRECTORY;
        String moduleName = "module-test";
        final File moduleDir = createModule0(baseModuleDir, moduleName);
        createdFiles.add(moduleDir);
        // create the patch with the updated module
        ContentModification moduleModified = ContentModificationUtils.modifyModule(oneOffDir, patchElementId, moduleDir, "new resource in the module");

        ProductConfig productConfig = new ProductConfig(PRODUCT, AS_VERSION, "main");
        final String oneOffDescr = "A one-off patch adding a misc file.";
        final String oneOffElementDescr = "A one-off element patch";
        Patch oneOff = PatchBuilder.create()
                .setPatchId(oneOffID)
                .setDescription(oneOffDescr)
                .setLink("http://test.one")
                .oneOffPatchIdentity(productConfig.getProductName(), productConfig.getProductVersion())
                .getParent()
                .addContentModification(miscFileAdded)
                .oneOffPatchElement(patchElementId, "base", false)
                .setDescription(oneOffElementDescr)
                .addContentModification(moduleModified)
                .getParent()
                .build();
        createPatchXMLFile(oneOffDir, oneOff);
        File zippedOneOff = PatchingTestUtil.createZippedPatchFile(oneOffDir, oneOffID);

        // apply the patch and check if server is in restart-required mode
        handle("patch apply " + zippedOneOff.getAbsolutePath());

        String cpID = randomString();
        String elementCpID = randomString();
        File cpDir = mkdir(tempDir, cpID);

        final File patchedModule = IoUtils.newFile(baseModuleDir, ".overlays", patchElementId, moduleName);

        final ContentModification fileModified2 = ContentModificationUtils.modifyMisc(cpDir, cpID, "another file update", new File(miscDir, "test-file"), "miscDir", "test-file");
        final ContentModification moduleModified2 = ContentModificationUtils.modifyModule(cpDir, elementCpID, patchedModule, "another module update");

        final String cpDescr = "A CP adding a misc file.";
        final String cpElementDescr = "A CP element";
        Patch cp = PatchBuilder.create()
                .setPatchId(cpID)
                .setDescription(cpDescr)
                .setLink("http://test.two")
                .upgradeIdentity(productConfig.getProductName(), productConfig.getProductVersion(), productConfig.getProductVersion() + "_CP" + cpID)
                .getParent()
                .addContentModification(fileModified2)
                .upgradeElement(elementCpID, "base", false)
                .setDescription(cpElementDescr)
                .addContentModification(moduleModified2)
                .getParent()
                .build();
        createPatchXMLFile(cpDir, cp);
        File zippedCP = PatchingTestUtil.createZippedPatchFile(cpDir, cpID);

        handle("patch apply " + zippedCP.getAbsolutePath());

        handle("patch info --patch-id=" + oneOffID);
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), oneOffID, "http://test.one", true,
                productConfig.getProductName(), productConfig.getProductVersion(), oneOffDescr);

        Map<String,String> element = new HashMap<String,String>();
        element.put("Patch ID", patchElementId);
        element.put("Name", "base");
        element.put("Type", "layer");
        element.put("Description", oneOffElementDescr);
        handle("patch info --patch-id=" + oneOffID + " --verbose");
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), oneOffID, "http://test.one", true,
                productConfig.getProductName(), productConfig.getProductVersion(), oneOffDescr, Collections.singletonList(element));

        handle("patch info --patch-id=" + cpID);
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), cpID, "http://test.two", false,
                productConfig.getProductName(), productConfig.getProductVersion(), cpDescr);

        element.put("Patch ID", elementCpID);
        element.put("Name", "base");
        element.put("Type", "layer");
        element.put("Description", cpElementDescr);
        handle("patch info --patch-id=" + cpID + " --verbose");
        CLIPatchInfoUtil.assertPatchInfo(bytesOs.toByteArray(), cpID, "http://test.two", false,
                productConfig.getProductName(), productConfig.getProductVersion(), cpDescr, Collections.singletonList(element));
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
}
