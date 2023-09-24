/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.io.ByteArrayOutputStream;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class EchoDMRTestCase {
     @Test
    public void testCompact() throws CliInitializationException, CommandLineException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        try {
            String cmd = "/subsystem=foo:op(arg1=14, arg2=[{i1=\"val1\", i2=\"val2\"}], arg3=\"val3\")";
            ctx.handle("echo-dmr " + cmd);
            String out = consoleOutput.toString();
            out = out.substring(0, out.lastIndexOf("\n"));
            consoleOutput.reset();
            ctx.handle("echo-dmr --compact " + cmd);
            String compact = consoleOutput.toString();
            compact = compact.substring(0, compact.lastIndexOf("\n"));
            Assert.assertTrue(out, out.contains("\n"));
            Assert.assertFalse(compact, compact.contains("\n"));
            ModelNode mn = ModelNode.fromString(out);
            ModelNode mnCompact = ModelNode.fromString(compact);
            Assert.assertEquals(mnCompact.toString(), mn, mnCompact);
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testHeaders() throws CliInitializationException, CommandLineException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        try {
            String cmd = "/subsystem=foo:op(arg1=14, arg2=[{i1=\"val1\", i2=\"val2\"}], arg3=\"val3\"){allow-resource-service-restart=true;foo=\"1 2 3\";blocking-timeout=12}";
            ctx.handle("echo-dmr " + cmd);
            String out = consoleOutput.toString();
            out = out.substring(0, out.lastIndexOf("\n"));
            ModelNode mn = ModelNode.fromString(out);
            ModelNode headers = mn.get(Util.OPERATION_HEADERS);
            Assert.assertEquals("1 2 3", headers.get("foo").asString());
            Assert.assertEquals("true", headers.get("allow-resource-service-restart").asString());
            Assert.assertEquals("12", headers.get("blocking-timeout").asString());
        } finally {
            ctx.terminateSession();
        }
    }

}
