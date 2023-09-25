/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.wildfly.core.testrunner.WildFlyRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * WFCORE-1714 Test for Variables Completion
 *
 * @author wangc
 */
@RunWith(WildFlyRunner.class)
public class VariablesCompletionTestCase {

    private static final String FOO_NAME = "foo";
    private static final String FOO_VALUE = "/subsystem=logging";
    private static final String FOOBAR_NAME = "foobar";
    private static final String FOOBAR_VALUE = "/subsystem=logging/console-handler=CONSOLE";

    private static CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        // explicitly define default consoleInput to set proper operationCandidatesProvider in CommandContextImpl
        cli = new CLIWrapper(true, null, System.in);
        assertTrue(cli.isConnected());

        // set both variables
        cli.sendLine("set " + FOO_NAME + "=" + FOO_VALUE);
        cli.sendLine("set " + FOOBAR_NAME + "=" + FOOBAR_VALUE);

        cli.sendLine("echo $" + FOO_NAME);
        assertTrue(cli.readOutput().contains(FOO_VALUE));
        cli.sendLine("echo $" + FOOBAR_NAME);
        assertTrue(cli.readOutput().contains(FOOBAR_VALUE));
    }

    @After
    public void cleanUp() throws CommandLineException {
        cli.quit();
    }

    @Test
    public void testDolloarSymbolCompletion() throws Exception {
        // test $ symbol [standalone@localhost:9990 /] $
        String command = "$";
        List<String> candidates = fetchCandidates(command, command.length(), cli.getCommandContext());
        assertNotNull(candidates);
        assertEquals(Arrays.asList(FOO_NAME, FOOBAR_NAME), candidates);
    }

    @Test
    public void testIncompleteFirstVariableCompletion() {
        // test incomplete first variable name [standalone@localhost:9990 /] $f
        String command = "$f";
        List<String> candidates = fetchCandidates(command, command.length(), cli.getCommandContext());
        assertNotNull(candidates);
        assertEquals(Arrays.asList(FOO_NAME, FOOBAR_NAME), candidates);
    }

    @Test
    public void testCompleteFirstVariableCompletion() {
        // test complete first variable name [standalone@localhost:9990 /] $foo
        String command = "$foo";
        List<String> candidates = fetchCandidates(command, command.length(), cli.getCommandContext());
        assertNotNull(candidates);
        assertEquals(Arrays.asList("/", ":", FOOBAR_NAME), candidates);
    }

    @Test
    public void testIncompleteSecondVariableCompletion() {
        // test incomplete second variable name [standalone@localhost:9990 /] $foob
        String command = "$foob";
        List<String> candidates = fetchCandidates(command, command.length(), cli.getCommandContext());
        assertNotNull(candidates);
        assertEquals(Arrays.asList(FOOBAR_NAME), candidates);
    }

    @Test
    public void testCompleteSecondVariableCompletion() {
        // test complete second variable name [standalone@localhost:9990 /] $foobar
        String command = "$" + FOOBAR_NAME;
        List<String> candidates = fetchCandidates(command, command.length(), cli.getCommandContext());
        assertNotNull(candidates);
        assertEquals(Arrays.asList("/", ":"), candidates);
    }

    private List<String> fetchCandidates(String buffer, int cursor, CommandContext ctx) {
        ArrayList<String> candidates = new ArrayList<String>();
        ctx.getDefaultCommandCompleter().complete(ctx, buffer, cursor, candidates);
        return candidates;
    }
}
