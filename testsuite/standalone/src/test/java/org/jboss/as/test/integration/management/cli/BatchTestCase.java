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

import java.io.ByteArrayOutputStream;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.CommandContextImpl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
public class BatchTestCase extends AbstractCliTestBase {

    @Inject
    protected ManagementClient managementClient;

    @BeforeClass
    public static void before() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void after() throws Exception {
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void testRunBatchBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        try {
            doRunBatch(ctx);
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testRunBatch() throws Exception {
        doRunBatch(cli.getCommandContext());
    }

    void doRunBatch(CommandContext ctx) throws Exception {
        addProperty("prop1", "prop1_a", ctx);
        addProperty("prop2", "prop2_a", ctx);

        ctx.handle("batch");
        writeProperty("prop1", "prop1_b", ctx);
        writeProperty("prop2", "prop2_b", ctx);

        assertEquals("prop1_a", readProperty("prop1", ctx));
        assertEquals("prop2_a", readProperty("prop2", ctx));

        ctx.handle("run-batch");

        assertEquals("prop1_b", readProperty("prop1", ctx));
        assertEquals("prop2_b", readProperty("prop2", ctx));

        ctx.handle("batch");
        removeProperty("prop1", ctx);
        removeProperty("prop2", ctx);
        ctx.handle("holdback-batch dbatch");
        ctx.handle("batch dbatch");

        assertTrue(Util.isValidPath(ctx.getModelControllerClient(), "system-property", "prop1"));
        assertTrue(Util.isValidPath(ctx.getModelControllerClient(), "system-property", "prop2"));

        ctx.handle("run-batch");
        assertFalse(Util.isValidPath(ctx.getModelControllerClient(), "system-property", "prop1"));
        assertFalse(Util.isValidPath(ctx.getModelControllerClient(), "system-property", "prop2"));
    }

    @Test
    public void testRollbackBatch() throws Exception {
        doTestRollbackBatch(cli.getCommandContext(), () -> {
            return cli.readOutput();
        });
    }

    @Test
    public void testRollbackBatchBoot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandContext ctx = new CommandContextImpl(out);
        ctx.bindClient(managementClient.getControllerClient());
        try {
            doTestRollbackBatch(ctx, () -> {
                String str = out.toString();
                out.reset();
                return str;
            });
        } finally {
            ctx.terminateSession();
        }
    }

    private void doTestRollbackBatch(CommandContext ctx, Supplier<String> supplier) throws Exception {

        addProperty("prop1", "prop1_a", ctx);

        ctx.handle("batch");
        addProperty("prop2", "prop2_a", ctx);
        addProperty("prop1", "prop1_b", ctx);

        assertEquals("prop1_a", readProperty("prop1", ctx));
        assertFalse(Util.isValidPath(ctx.getModelControllerClient(), "system-property", "prop2"));

        ctx.handleSafe("run-batch");
        String line = supplier.get();
        String expectedErrorCode = ControllerLogger.ROOT_LOGGER.compositeOperationFailed();
        expectedErrorCode = expectedErrorCode.substring(0, expectedErrorCode.indexOf(':'));
        assertTrue("Batch did not fail. " + line, line.contains(expectedErrorCode));
        assertTrue("Operation is not contained in error message.",
                line.contains("/system-property=prop1:add(value=prop1_b)"));
        assertEquals("prop1_a", readProperty("prop1", ctx));
        assertFalse(Util.isValidPath(ctx.getModelControllerClient(), "system-property", "prop2"));

        ctx.handle("discard-batch");

        removeProperty("prop1", ctx);
        assertFalse(Util.isValidPath(ctx.getModelControllerClient(), "system-property", "prop1"));
    }

    protected void addProperty(String name, String value, CommandContext ctx) throws Exception {
        ctx.handle("/system-property=" + name + ":add(value=" + value + ")");
    }

    protected void writeProperty(String name, String value, CommandContext ctx) throws Exception {
        ctx.handle("/system-property=" + name + ":write-attribute(name=value,value=" + value + ")");
    }

    protected String readProperty(String name, CommandContext ctx) throws Exception {
        ModelNode mn = ctx.buildRequest("/system-property=" + name + ":read-attribute(name=value");
        ModelNode ret = ctx.getModelControllerClient().execute(mn);
        return ret.get("result").asString();
    }

    protected void removeProperty(String name, CommandContext ctx) throws Exception {
        ctx.handle("/system-property=" + name + ":remove");
    }
}
