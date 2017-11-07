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

import static org.jboss.as.test.patching.PatchingTestUtil.MODULES_PATH;
import static org.jboss.as.test.patching.PatchingTestUtil.randomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.test.patching.util.module.Module;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ShowHistoryUnitTestCase extends PatchInfoTestBase {

    private String[] patchIds;
    private boolean[] patchTypes;

    @Before
    public void setup() throws Exception {
        patchIds = new String[8];
        patchTypes = new boolean[patchIds.length];
        for (int i = 0; i < patchIds.length; ++i) {
            patchIds[i] = randomString();
            patchTypes[i] = (i % 3 == 0);
        }
    }

    @Test
    public void testUnpatched() throws Exception {
        final ModelNode response = showHistory();

        assertTrue(response.has("outcome"));
        assertEquals("success", response.get("outcome").asString());
        assertTrue(response.has("result"));
        final List<ModelNode> list = response.get("result").asList();
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCP() throws Exception {
        Module module = new Module.Builder("module-test").
                miscFile(new ResourceItem("resource-test", ("module resource").getBytes(StandardCharsets.UTF_8))).
                build();
        File moduleDir = module.writeToDisk(new File(MODULES_PATH));

        byte[] targetHash = HashUtils.hashFile(moduleDir);
        targetHash = applyCP("cp1", targetHash);

        final ModelNode response = showHistory();

        assertTrue(response.has("outcome"));
        assertEquals("success", response.get("outcome").asString());
        assertTrue(response.has("result"));
        final List<ModelNode> list = response.get("result").asList();
        assertEquals(1, list.size());
        assertPatchInfo(list.get(0), "cp1", "cumulative", "false");
    }

    @Test
    public void testOneOff() throws Exception {
        Module module = new Module.Builder("module-test").
                miscFile(new ResourceItem("resource-test", ("module resource").getBytes(StandardCharsets.UTF_8))).
                build();
        File moduleDir = module.writeToDisk(new File(MODULES_PATH));

        byte[] targetHash = HashUtils.hashFile(moduleDir);
        targetHash = applyOneOff("oneoff1", targetHash);

        final ModelNode response = showHistory();

        assertTrue(response.has("outcome"));
        assertEquals("success", response.get("outcome").asString());
        assertTrue(response.has("result"));
        final List<ModelNode> list = response.get("result").asList();
        assertEquals(1, list.size());
        assertPatchInfo(list.get(0), "oneoff1", "one-off", "false");
    }

    @Test
    public void testOneOffAndCP() throws Exception {
        Module module = new Module.Builder("module-test").
                miscFile(new ResourceItem("resource-test", ("module resource").getBytes(StandardCharsets.UTF_8))).
                build();
        File moduleDir = module.writeToDisk(new File(MODULES_PATH));

        byte[] targetHash = HashUtils.hashFile(moduleDir);
        targetHash = applyOneOff("oneoff1", targetHash);
        targetHash = applyCP("cp1", targetHash);

        final ModelNode response = showHistory();

        assertTrue(response.has("outcome"));
        assertEquals("success", response.get("outcome").asString());
        assertTrue(response.has("result"));
        final List<ModelNode> list = response.get("result").asList();
        assertEquals(2, list.size());
        assertPatchInfo(list.get(0), "cp1", "cumulative", "false");
        assertPatchInfo(list.get(1), "oneoff1", "one-off", "false");
    }

    @Test
    public void testMain() throws Exception {

        // create a module
        Module module = new Module.Builder("module-test").
                miscFile(new ResourceItem("resource-test", ("module resource").getBytes(StandardCharsets.UTF_8))).
                build();
        File moduleDir = module.writeToDisk(new File(MODULES_PATH));

        byte[] targetHash = HashUtils.hashFile(moduleDir);
        for (int i = 0; i < patchIds.length; ++i) {
            if (patchTypes[i]) {
                targetHash = applyCP(patchIds[i], targetHash);
            } else {
                targetHash = applyOneOff(patchIds[i], targetHash);
            }
        }

        final ModelNode response = showHistory();

        assertTrue(response.has("outcome"));
        assertEquals("success", response.get("outcome").asString());
        assertTrue(response.has("result"));
        final List<ModelNode> list = response.get("result").asList();
        assertEquals(patchIds.length, list.size());
        for (int i = 0; i < patchIds.length; ++i) {
            assertPatchInfo(list.get(i), patchIds[patchIds.length - 1 - i], patchTypes[patchTypes.length - 1 - i] ? "cumulative" : "one-off", "false");
        }
    }

    protected void assertPatchInfo(final ModelNode info, final String patchId, final String type, String agedOut) {
        assertEquals(patchId, info.get("patch-id").asString());
        assertTrue(info.has("type"));
        assertEquals(type, info.get("type").asString());
        assertTrue(info.has("applied-at"));
        assertEquals(agedOut, info.get(Constants.AGED_OUT).asString());
    }

    private ModelNode showHistory() throws IOException {
        controller.start();
        ModelControllerClient client = null;
        final ModelNode response;
        try {
            client = controller.getClient().getControllerClient();
            ModelNode op = new ModelNode();
            op.get("address").add("core-service", "patching");
            op.get("operation").set("show-history");
            response = client.execute(op);
        } finally {
            if (client != null) {
                client.close();
            }
            controller.stop();
        }
        return response;
    }
}
