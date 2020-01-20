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
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.inject.Inject;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
public class BatchFileTestCase {

    @Inject
    protected ManagementClient managementClient;

    private static final String FILE_NAME = "jboss-cli-batch-file-test.cli";
    private static final File TMP_FILE;

    static {
        TMP_FILE = new File(new File(TestSuiteEnvironment.getTmpDir()), FILE_NAME);
    }

    @AfterClass
    public static void cleanUp() {
        if (TMP_FILE.exists()) {
            TMP_FILE.delete();
        }
    }

    @Test
    public void testBatchFile() throws Exception {
        createFile(new String[]{"/system-property=batchfiletest:add(value=true)"});

        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            doTestBatchFile(ctx);
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testBatchFileBoot() throws Exception {
        createFile(new String[]{"/system-property=batchfiletest:add(value=true)"});

        final CommandContext ctx = new CommandContextImpl(null);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            doTestBatchFile(ctx);
        } finally {
            ctx.terminateSession();
        }
    }

    void doTestBatchFile(CommandContext ctx) throws Exception {
        ctx.handle("batch --file=" + TMP_FILE.getAbsolutePath());
        final ModelNode batchRequest = ctx.buildRequest("run-batch");
        assertTrue(batchRequest.hasDefined("operation"));
        assertEquals("composite", batchRequest.get("operation").asString());
        assertTrue(batchRequest.hasDefined("address"));
        assertTrue(batchRequest.get("address").asList().isEmpty());
        assertTrue(batchRequest.hasDefined("steps"));
        List<ModelNode> steps = batchRequest.get("steps").asList();
        assertEquals(1, steps.size());
        final ModelNode op = steps.get(0);
        assertTrue(op.hasDefined("address"));
        List<Property> address = op.get("address").asPropertyList();
        assertEquals(1, address.size());
        assertEquals("system-property", address.get(0).getName());
        assertEquals("batchfiletest", address.get(0).getValue().asString());

        assertTrue(op.hasDefined("operation"));
        assertEquals("add", op.get("operation").asString());
        assertEquals("true", op.get("value").asString());
        ctx.handle("discard-batch");
    }

    @Test
    public void testRunBatchFile() throws Exception {
        createFile(new String[]{"/system-property=batchfiletest:add(value=true)",
            "",
            "# comments",
            "/system-property=batchfiletest:write-attribute(value=false)"});

        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            doTestRunBatchFile(ctx);
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testRunBatchFileBoot() throws Exception {
        createFile(new String[]{"/system-property=batchfiletest:add(value=true)",
            "",
            "# comments",
            "/system-property=batchfiletest:write-attribute(value=false)"});

        final CommandContext ctx = new CommandContextImpl(null);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            doTestRunBatchFile(ctx);
        } finally {
            ctx.terminateSession();
        }
    }

    private void doTestRunBatchFile(CommandContext ctx) throws Exception {
        final ModelNode batchRequest = ctx.buildRequest("run-batch --file=" + TMP_FILE.getAbsolutePath() + " --headers={allow-resource-service-restart=true}");
        assertTrue(batchRequest.hasDefined("operation"));
        assertEquals("composite", batchRequest.get("operation").asString());
        assertTrue(batchRequest.hasDefined("address"));
        assertTrue(batchRequest.get("address").asList().isEmpty());
        assertTrue(batchRequest.hasDefined("steps"));
        List<ModelNode> steps = batchRequest.get("steps").asList();
        assertEquals(2, steps.size());
        ModelNode op = steps.get(0);
        assertTrue(op.hasDefined("address"));
        List<Property> address = op.get("address").asPropertyList();
        assertEquals(1, address.size());
        assertEquals("system-property", address.get(0).getName());
        assertEquals("batchfiletest", address.get(0).getValue().asString());

        assertTrue(op.hasDefined("operation"));
        assertEquals("add", op.get("operation").asString());
        assertEquals("true", op.get("value").asString());

        op = steps.get(1);
        assertTrue(op.hasDefined("address"));
        address = op.get("address").asPropertyList();
        assertEquals(1, address.size());
        assertEquals("system-property", address.get(0).getName());
        assertEquals("batchfiletest", address.get(0).getValue().asString());

        assertTrue(op.hasDefined("operation"));
        assertEquals("write-attribute", op.get("operation").asString());
        assertEquals("false", op.get("value").asString());

        assertTrue(batchRequest.hasDefined("operation-headers"));
        final ModelNode headers = batchRequest.get("operation-headers");
        assertEquals("true", headers.get("allow-resource-service-restart").asString());
    }

    protected void createFile(String[] cmd) {
        if (TMP_FILE.exists()) {
            if (!TMP_FILE.delete()) {
                fail("Failed to delete " + TMP_FILE.getAbsolutePath());
            }
        }

        try ( Writer writer = Files.newBufferedWriter(TMP_FILE.toPath(), StandardCharsets.UTF_8)) {
            for (String line : cmd) {
                writer.write("# Some comment\n");
                writer.write(line);
                writer.write('\n');
                writer.write("     \n");
                writer.write("\n");
            }
        } catch (IOException e) {
            fail("Failed to write to " + TMP_FILE.getAbsolutePath() + ": " + e.getLocalizedMessage());
        }
    }
}
