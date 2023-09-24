/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class MultipleLinesCommandsTestCase {

    private static String[] operation;
    private static String[] command;

    @BeforeClass
    public static void init() {
        final String lineSep = TestSuiteEnvironment.getSystemProperty("line.separator");

        operation = new String[]{
                ":\\" + lineSep,
                "read-resource(\\" + lineSep,
                "include-defaults=true,\\" + lineSep,
                "recursive=false)"
        };

        command = new String[]{
                "read-attribute\\" + lineSep,
                "product-name\\" + lineSep,
                "--verbose"
        };
    }

    protected void handleAsOneString(String[] arr) throws Exception {
        final StringBuilder buf = new StringBuilder();
        for(String line : arr) {
            buf.append(line);
        }
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            ctx.handle(buf.toString());
        } finally {
            ctx.terminateSession();
        }
    }

    protected void handleInPieces(String[] arr) throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            for(String line : arr) {
                ctx.handle(line);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testOperationAsOneString() throws Exception {
        handleAsOneString(operation);
    }

    @Test
    public void testOperationInPieces() throws Exception {
        handleInPieces(operation);
    }

    @Test
    public void testCommandAsOneString() throws Exception {
        handleAsOneString(command);
    }

    @Test
    public void testCommandInPieces() throws Exception {
        handleInPieces(command);
    }
}
