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

import static org.jboss.as.test.patching.PatchingTestUtil.MODULES_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
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
public class PatchInfoTestCase extends PatchInfoTestBase {

    private static final String BASE = "base";
    private static final String CUMULATIVE_PATCH_ID = "Cumulative patch ID";
    private static final String LAYER = "Layer";
    private static final String NONE = "none";
    private static final String ONE_OFF_PATCHES = "One-off patches";
    private static final String VERSION = "Version";

    private CLIOutputReader reader = new CLIOutputReader();
    private ByteArrayOutputStream bytesOs;

    private File moduleDir;

    @Before
    public void setup() throws Exception {
        bytesOs = new ByteArrayOutputStream();
        final Module module = new Module.Builder("module-test").
                miscFile(new ResourceItem("resource-test", ("module resource").getBytes())).
                build();
        moduleDir = module.writeToDisk(new File(MODULES_PATH));
    }

    @After
    public void cleanup() throws Exception {
        if(bytesOs != null) {
            bytesOs = null;
        }
    }

    @Test
    public void testUnpatched() throws Exception {
        readPatchInfo(false);
        Map<String,String> table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals(NONE, table.get(ONE_OFF_PATCHES));
        assertEquals(BASE, table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());

        readPatchInfo(true);
        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals(NONE, table.get(ONE_OFF_PATCHES));
        assertEquals(BASE, table.get(CUMULATIVE_PATCH_ID));

        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertEquals(BASE, table.get(LAYER));
        assertEquals(NONE, table.get(ONE_OFF_PATCHES));
        assertEquals(BASE, table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());
    }

    @Test
    public void testCP() throws Exception {

        byte[] targetHash = HashUtils.hashFile(moduleDir);
        targetHash = applyCP("cp1", "base-cp1", targetHash);

        readPatchInfo(false);
        Map<String,String> table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals(NONE, table.get(ONE_OFF_PATCHES));
        assertEquals("cp1", table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());

        readPatchInfo(true);
        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals(NONE, table.get(ONE_OFF_PATCHES));
        assertEquals("cp1", table.get(CUMULATIVE_PATCH_ID));

        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertEquals(BASE, table.get(LAYER));
        assertEquals(NONE, table.get(ONE_OFF_PATCHES));
        assertEquals("base-cp1", table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());
    }

    @Test
    public void testOneOff() throws Exception {

        byte[] targetHash = HashUtils.hashFile(moduleDir);
        targetHash = applyOneOff("oneOff1", "base-oneOff1", targetHash);

        readPatchInfo(false);
        Map<String,String> table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals("oneOff1", table.get(ONE_OFF_PATCHES));
        assertEquals(BASE, table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());

        readPatchInfo(true);
        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals("oneOff1", table.get(ONE_OFF_PATCHES));
        assertEquals(BASE, table.get(CUMULATIVE_PATCH_ID));

        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertEquals(BASE, table.get(LAYER));
        assertEquals("base-oneOff1", table.get(ONE_OFF_PATCHES));
        assertEquals(BASE, table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());
    }

    @Test
    public void testCPAndOneOffsOnline() throws Exception {
        doTestCPAndOneOffs(false);
    }

    @Test
    public void testCPAndOneOffsOffline() throws Exception {
        doTestCPAndOneOffs(true);
    }

    private void doTestCPAndOneOffs(boolean offline) throws Exception {
        byte[] targetHash = HashUtils.hashFile(moduleDir);
        targetHash = applyCP("cp1", "base-cp1", targetHash);
        targetHash = applyOneOff("oneOff1", "base-oneOff1", targetHash);
        targetHash = applyOneOff("oneOff2", "base-oneOff2", targetHash);

        readPatchInfo(false, offline);
        Map<String,String> table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals("oneOff2,oneOff1", table.get(ONE_OFF_PATCHES));
        assertEquals("cp1", table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());

        readPatchInfo(true, offline);
        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertTrue(table.containsKey(VERSION));
        assertEquals("oneOff2,oneOff1", table.get(ONE_OFF_PATCHES));
        assertEquals("cp1", table.get(CUMULATIVE_PATCH_ID));

        table = reader.readTable();
        assertNotNull(table);
        assertEquals(3, table.size());
        assertEquals(BASE, table.get(LAYER));
        assertEquals("base-oneOff2,base-oneOff1", table.get(ONE_OFF_PATCHES));
        assertEquals("base-cp1", table.get(CUMULATIVE_PATCH_ID));
        assertNull(reader.readTable());
    }

    private void readPatchInfo(boolean verbose) throws Exception {
        readPatchInfo(verbose, false);
    }

    private void readPatchInfo(boolean verbose, boolean offline) throws Exception {

        String line = "patch info";
        if(verbose) {
            line += " --verbose";
        }

        // to avoid the need to reset the terminal manually after the tests, e.g. 'stty sane'
        System.setProperty("aesh.terminal", "org.jboss.aesh.terminal.TestTerminal");
        final CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(null, null, null, System.in, bytesOs);

        if(!offline) {
            controller.start();
            final ModelControllerClient client = ModelControllerClient.Factory.create(TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort());
            ctx.bindClient(client);
        } else {
            line += " --distribution=" + PatchingTestUtil.AS_DISTRIBUTION;
        }

        bytesOs.reset();
        try {
           ctx.handle(line);
        } finally {
            ctx.terminateSession();
            if(!offline) {
                controller.stop();
            }
        }
        reader.refresh();
    }

    class CLIOutputReader {

        String line;
        BufferedReader reader;

        CLIOutputReader() {
        }

        void refresh() {
            if(bytesOs.size() > 0) {
                reader = new BufferedReader(new StringReader(new String(bytesOs.toByteArray())));
            } else {
                reader = null;
            }
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
}
