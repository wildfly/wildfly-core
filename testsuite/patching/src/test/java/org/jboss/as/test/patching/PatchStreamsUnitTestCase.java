/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.test.patching.PatchingTestUtil.BASE_MODULE_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class PatchStreamsUnitTestCase extends PatchInfoTestBase {

    private static final File LAYERS_CONF = new File(PatchingTestUtil.MODULES_DIRECTORY, "layers.conf");
    private static final File LAYERS_CONF_COPY = new File(PatchingTestUtil.MODULES_DIRECTORY, "layers.conf-copy");

    private ByteArrayOutputStream bytesOs;
    private CLIOutputReader reader = new CLIOutputReader();

    private File moduleADir;
    private File moduleBDir;
    private File moduleCDir;

    private CommandContext cli;

    @BeforeClass
    public static void prepareInstallation() throws Exception {
        installLayers("layerA", "layerB", "layerC");
    }

    @AfterClass
    public static void restoreInstallation() throws Exception {
        uninstallLayers("layerA", "layerB", "layerC");
    }

    @Before
    public void setup() throws Exception {
        bytesOs = new ByteArrayOutputStream();
        moduleADir = createModule("layerA");
        moduleBDir = createModule("layerB");
        moduleCDir = createModule("layerC");

        cli = CommandContextFactory.getInstance().newCommandContext(null, null, null, System.in, bytesOs);

        final ModelControllerClient client = ModelControllerClient.Factory.create(TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort());
        cli.bindClient(client);
    }

    @After
    public void cleanup() throws Exception {
        if(bytesOs != null) {
            bytesOs = null;
        }
        if(cli != null) {
            cli.terminateSession();
        }
    }

    @Override
    protected void rollbackAllPatches() throws Exception {
        // aging out overlays makes it impossible to rollback past a certain point
        // so no rolling back here
        final File home = new File(PatchingTestUtil.AS_DISTRIBUTION);
        PatchingTestUtil.resetInstallationState(home, BASE_MODULE_DIRECTORY);
    }

    @Test
    public void testMain() throws Exception {

        byte[] aHash = HashUtils.hashFile(moduleADir);
        byte[] bHash = HashUtils.hashFile(moduleBDir);
        byte[] cHash = HashUtils.hashFile(moduleCDir);

        aHash = applyPatch("ProductA", "1.0.0.Beta1", "1.0.0", "A_1.0.0", "layerA", "layerA_1.0.0", aHash);
        aHash = applyPatch("ProductA", "1.0.0", null, "A_ONE_OFF_0", "layerA", "layerA_ONE_OFF_0", aHash);
        bHash = applyPatch("ProductB", "2.0.0.Beta2", "2.0.0", "B_2.0.0", "layerB", "layerB_2.0.0", bHash);
        bHash = applyPatch("ProductB", "2.0.0", null, "B_ONE_OFF_0", "layerB", "layerB_ONE_OFF_0", bHash);
        cHash = applyPatch("ProductC", "3.0.0.Beta3", "3.0.0", "C_3.0.0", "layerC", "layerC_3.0.0", cHash);
        cHash = applyPatch("ProductC", "3.0.0", null, "C_ONE_OFF_0", "layerC", "layerC_ONE_OFF_0", cHash);

        aHash = applyPatch("ProductA", "1.0.0", "1.0.1", "A_CP1", "layerA", "layerA_CP1", aHash);
        aHash = applyPatch("ProductA", "1.0.1", null, "A_ONE_OFF_1", "layerA", "layerA_ONE_OFF_1", aHash);
        bHash = applyPatch("ProductB", "2.0.0", "2.0.1", "B_CP1", "layerB", "layerB_CP1", bHash);
        aHash = applyPatch("ProductA", "1.0.1", null, "A_ONE_OFF_2", "layerA", "layerA_ONE_OFF_2", aHash);
        bHash = applyPatch("ProductB", "2.0.1", null, "B_ONE_OFF_1", "layerB", "layerB_ONE_OFF_1", bHash);
        cHash = applyPatch("ProductC", "3.0.0", "3.0.1", "C_CP1", "layerC", "layerC_CP1", cHash);
        cHash = applyPatch("ProductC", "3.0.1", null, "C_ONE_OFF_1", "layerC", "layerC_ONE_OFF_1", cHash);
        bHash = applyPatch("ProductB", "2.0.1", null, "B_ONE_OFF_2", "layerB", "layerB_ONE_OFF_2", bHash);
        cHash = applyPatch("ProductC", "3.0.1", null, "C_ONE_OFF_2", "layerC", "layerC_ONE_OFF_2", cHash);

        controller.start();
        try {
            assertPatchStreamNames(productConfig.getProductName(), "ProductA", "ProductB", "ProductC");
            assertPatches("ProductA", "1.0.1", "A_CP1", "A_ONE_OFF_2", "A_ONE_OFF_1");
            assertPatches("ProductB", "2.0.1", "B_CP1", "B_ONE_OFF_2", "B_ONE_OFF_1");
            assertPatches("ProductC", "3.0.1", "C_CP1", "C_ONE_OFF_2", "C_ONE_OFF_1");
        } finally {
            controller.stop();
        }

        rollback("ProductA", "A_ONE_OFF_2");
        rollbackLast("ProductB");

        controller.start();
        try {
            assertPatchStreamNames(productConfig.getProductName(), "ProductA", "ProductB", "ProductC");
            assertPatches("ProductA", "1.0.1", "A_CP1", "A_ONE_OFF_1");
            assertPatches("ProductB", "2.0.1", "B_CP1", "B_ONE_OFF_1");
            assertPatches("ProductC", "3.0.1", "C_CP1", "C_ONE_OFF_2", "C_ONE_OFF_1");

            assertHistory("ProductA", new HistoryEntry("A_ONE_OFF_1", true), new HistoryEntry("A_CP1", false),
                    new HistoryEntry("A_ONE_OFF_0", true), new HistoryEntry("A_1.0.0", false));
            assertHistory("ProductB", new HistoryEntry("B_ONE_OFF_1", true), new HistoryEntry("B_CP1", false),
                    new HistoryEntry("B_ONE_OFF_0", true), new HistoryEntry("B_2.0.0", false));
            assertHistory("ProductC", new HistoryEntry("C_ONE_OFF_2", true), new HistoryEntry("C_ONE_OFF_1", true), new HistoryEntry("C_CP1", false),
                    new HistoryEntry("C_ONE_OFF_0", true), new HistoryEntry("C_3.0.0", false));

            assertOverlays("layerA", true, "layerA_ONE_OFF_1", "layerA_CP1", "layerA_ONE_OFF_0", "layerA_1.0.0");
            assertOverlays("layerB", true, "layerB_ONE_OFF_1", "layerB_CP1", "layerB_ONE_OFF_0", "layerB_2.0.0");
            assertOverlays("layerC", true, "layerC_ONE_OFF_1", "layerC_CP1", "layerC_ONE_OFF_0", "layerC_3.0.0");

            // ageout overlays for specific stream
            cliHandle("/core-service=patching/patch-stream=ProductA:ageout-history");
            assertOverlays("layerA", false, "layerA_ONE_OFF_0", "layerA_1.0.0");
            assertOverlays("layerA", true, "layerA_ONE_OFF_1", "layerA_CP1");
            assertOverlays("layerB", true, "layerB_ONE_OFF_1", "layerB_CP1", "layerB_ONE_OFF_0", "layerB_2.0.0");
            assertOverlays("layerC", true, "layerC_ONE_OFF_1", "layerC_CP1", "layerC_ONE_OFF_0", "layerC_3.0.0");

            // ageout overlays for all the rest
            cliHandle("/core-service=patching:ageout-history");
            assertOverlays("layerA", false, "layerA_ONE_OFF_0", "layerA_1.0.0");
            assertOverlays("layerA", true, "layerA_ONE_OFF_1", "layerA_CP1");
            assertOverlays("layerB", false, "layerB_ONE_OFF_0", "layerB_2.0.0");
            assertOverlays("layerB", true, "layerB_ONE_OFF_1", "layerB_CP1");
            assertOverlays("layerC", false, "layerC_ONE_OFF_0", "layerC_3.0.0");
            assertOverlays("layerC", true, "layerC_ONE_OFF_1", "layerC_CP1");
        } finally {
            controller.stop();
        }
    }

    protected void assertHistory(final String stream, HistoryEntry... entries) throws Exception {

        cliHandle("patch history --patch-stream=" + stream);
        final List<ModelNode> list = ModelNode.fromJSONString(reader.readOutput()).get("result").asList();
        assertEquals(entries.length, list.size());
        for(int i = 0; i < entries.length; ++i) {
            final HistoryEntry entry = entries[i];
            final ModelNode item = list.get(i);
            assertEquals(entry.patchId, item.get("patch-id").asString());
            assertEquals(entry.oneOff ? "one-off" : "cumulative", item.get("type").asString());
            assertTrue(item.hasDefined("applied-at"));
        }
    }

    protected void rollback(final String stream, final String patchId) throws CommandLineException {
        String line = "patch rollback --reset-configuration=true --patch-stream=" + stream;
        if(patchId != null) {
            line += " --patch-id=" + patchId;
        }
        controller.start();
        try {
            cliHandle(line);
        } finally {
            controller.stop();
        }
    }

    protected void rollbackLast(final String stream) throws CommandLineException {
        rollback(stream, null);
    }

    protected void assertPatches(String stream, String version, String cp, String... oneOffs) throws Exception {
        cliHandle("patch info --patch-stream=" + stream);
        final Map<String, String> result = reader.readTable();
        assertEquals(version, result.get("Version"));
        assertEquals(cp, result.get("Cumulative patch ID"));
        final StringBuilder buf = new StringBuilder();
        if (oneOffs.length > 0) {
            buf.append(oneOffs[0]);
            for (int i = 1; i < oneOffs.length; ++i) {
                buf.append(',').append(oneOffs[i]);
            }
        }
        assertEquals(buf.toString(), result.get("One-off patches"));
    }

    protected void assertPatchStreamNames(String... names) throws CommandLineException {
        cliHandle("/core-service=patching:read-children-names(child-type=patch-stream)");
        ModelNode node = ModelNode.fromString(reader.readOutput());
        final List<String> streams = new ArrayList<String>(names.length);
        for(ModelNode item : node.get("result").asList()) {
            streams.add(item.asString());
        }

        assertEquals(names.length, streams.size());
        for(String name : names) {
            assertTrue(streams.contains(name));
        }
    }

    protected void cliHandle(String line) throws CommandLineException {
        bytesOs.reset();
        cli.handle(line);
        reader.refresh();
    }

    protected File createModule(String targetLayer) throws IOException {
        final Module module = new Module.Builder("module-test").
                miscFile(new ResourceItem("resource-test", ("module resource").getBytes(StandardCharsets.UTF_8))).
                build();
        return module.writeToDisk(new File(PatchingTestUtil.LAYERS_DIRECTORY, targetLayer));
    }

    private static void installLayers(String... layers) throws IOException {
        installLayers(false, layers);
    }

    private static void installLayers(boolean excludeBase, String... layers) throws IOException {
        for (String layer : layers) {
            IoUtils.mkdir(PatchingTestUtil.LAYERS_DIRECTORY, layer);
        }

        final Properties props = new Properties();
        final StringBuilder str = new StringBuilder();
        for (int i = 0; i < layers.length; i++) {
            if (i > 0) {
                str.append(',');
            }
            str.append(layers[i]);
        }
        props.put(Constants.LAYERS, str.toString());
        props.put(Constants.EXCLUDE_LAYER_BASE, String.valueOf(excludeBase));

        if(LAYERS_CONF.exists()) {
            IoUtils.copy(LAYERS_CONF, LAYERS_CONF_COPY);
        }

        final FileOutputStream os = new FileOutputStream(LAYERS_CONF);
        try {
            props.store(os, "");
        } finally {
            IoUtils.safeClose(os);
        }
    }

    private static void uninstallLayers(String... layers) throws IOException {
        for (String layer : layers) {
            IoUtils.recursiveDelete(IoUtils.newFile(PatchingTestUtil.LAYERS_DIRECTORY, layer));
        }
        if(LAYERS_CONF_COPY.exists()) {
            IoUtils.copy(LAYERS_CONF_COPY, LAYERS_CONF);
        } else {
            IoUtils.recursiveDelete(LAYERS_CONF);
        }
    }

    protected void assertOverlays(final String layer, boolean exist, final String... patches) {

        final File base = newFile(PatchingTestUtil.LAYERS_DIRECTORY, layer);
        final File overlays = new File(base, ".overlays");
        for (final String patch : patches) {
            final File overlay = new File(overlays, patch);
            assertEquals("Overlay for layer " + layer + " patch " + patch + (exist ? " exists" : " does not exist"), exist, overlay.exists());
        }
    }

    class CLIOutputReader {

        String line;
        BufferedReader reader;

        CLIOutputReader() {
        }

        void refresh() {
            if(bytesOs.size() > 0) {
                reader = new BufferedReader(new StringReader(new String(bytesOs.toByteArray(), StandardCharsets.UTF_8)));
            } else {
                reader = null;
            }
        }

        protected String readOutput() {
            return new String(bytesOs.toByteArray(), StandardCharsets.UTF_8);
        }

        protected Map<String, String> readTable() throws IOException {

            String line = reader.readLine();
            if(line == null) {
                return null;
            }
            if(line.isEmpty()) {
                return Collections.emptyMap();
            }

            final Map<String, String> map = new HashMap<String,String>();
            addLine(map, line);

            while(!(line == null || line.isEmpty())) {
                addLine(map, line);
                line = reader.readLine();
            }
            return map;
        }

        protected void addLine(Map<String, String> map, String line) {
            final int i = line.indexOf(':');
            if(i < 0) {
                map.put(line, null);
            } else {
                final String key = line.substring(0, i).trim();
                final String value;
                if(i + 1 < line.length()) {
                    value = line.substring(i + 1, line.length()).trim();
                } else {
                    value = "";
                }
                map.put(key, value);
            }
        }
    }

    static class HistoryEntry {
        final String patchId;
        final boolean oneOff;

        HistoryEntry(String patchId, boolean oneOff) {
            this.patchId = patchId;
            this.oneOff = oneOff;
        }
    }
}
