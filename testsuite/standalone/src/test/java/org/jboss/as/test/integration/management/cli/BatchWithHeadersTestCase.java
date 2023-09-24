/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import jakarta.inject.Inject;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class BatchWithHeadersTestCase {

    @Inject
    protected ManagementClient managementClient;

    @Test
    public void testSuccessfulTryBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(null);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            doTest(ctx);
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testSuccessfulTry() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            doTest(ctx);
        } finally {
            ctx.terminateSession();
        }
    }

    private void doTest(CommandContext ctx) throws Exception {
        ctx.handle("batch");
        ctx.handle(":write-attribute(name=name,value=test");
        final ModelNode batchRequest = ctx.buildRequest("run-batch --headers={allow-resource-service-restart=true}");
        assertTrue(batchRequest.hasDefined("operation"));
        assertEquals("composite", batchRequest.get("operation").asString());
        assertTrue(batchRequest.hasDefined("address"));
        assertTrue(batchRequest.get("address").asList().isEmpty());
        assertTrue(batchRequest.hasDefined("steps"));
        List<ModelNode> steps = batchRequest.get("steps").asList();
        assertEquals(1, steps.size());
        final ModelNode op = steps.get(0);
        assertTrue(op.hasDefined("address"));
        assertTrue(op.get("address").asList().isEmpty());
        assertTrue(op.hasDefined("operation"));
        assertEquals("write-attribute", op.get("operation").asString());
        assertEquals("name", op.get("name").asString());
        assertEquals("test", op.get("value").asString());
        assertTrue(batchRequest.hasDefined("operation-headers"));
        final ModelNode headers = batchRequest.get("operation-headers");
        assertEquals("true", headers.get("allow-resource-service-restart").asString());
        ctx.handle("discard-batch");
    }
}
