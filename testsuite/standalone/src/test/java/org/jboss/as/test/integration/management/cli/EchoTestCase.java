/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Alexey Loubyansky
 *
 */
@RunWith(WildFlyRunner.class)
public class EchoTestCase {

    private static ByteArrayOutputStream cliOut;

    @BeforeClass
    public static void setup() throws Exception {
        cliOut = new ByteArrayOutputStream();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        cliOut = null;
    }

    @Test
    public void testMain() throws Exception {

        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        ctx.setVariable("a", "aa");
        try {
            ctx.handle("echo \\$a is resolved to $a");
            assertEquals("$a is resolved to aa", new String(cliOut.toByteArray(), StandardCharsets.UTF_8).trim());
        } finally {
            ctx.terminateSession();
            cliOut.reset();
        }
    }
}
