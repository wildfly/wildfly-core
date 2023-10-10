/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test completion of properties with and without rollout support.
 *
 * @author Jean-Francois Denise (jdenise@redhat.com)
 */
public class CliCompletionTestCase {

    private static DomainTestSupport testSupport;

    private static CommandContext ctx;

    /**
     * Create DomainTestSupport and initialize CommandContext before all tests
     */
    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = CLITestSuite.createSupport(
                CliCompletionTestCase.class.getSimpleName());
        ctx = CLITestUtil.getCommandContext(testSupport, System.in, System.out);
        ctx.connectController();
    }

    /**
     * Stop DomainTestSupport and terminate CommandContext after all tests are executed
     */
    @AfterClass
    public static void tearDownDomain() throws Exception {
        ctx.terminateSession();
        CLITestSuite.stopSupport();
    }

    /**
     * Checks CLI completion for "help" command
     */
    @Test
    public void helpTest() {
        for (List<String> candidates : getCandidatesLists("help", true, -1)) {
            assertTrue(candidates.toString(), candidates.contains("help"));
        }
    }

    /**
     * Checks CLI completion for "help --" command
     */
    @Test
    public void helpWithUnifinishedArgumentTest() {
        for (List<String> candidates : getCandidatesLists("help --", true, -1)) {
            assertTrue(candidates.toString(), candidates.contains("--commands"));
        }
    }

    /**
     * Checks CLI completion for "help l" command
     */
    @Test
    public void helpWithLCharTest() {
        for (List<String> candidates : getCandidatesLists("help l", true, -1)) {
            assertTrue(candidates.toString(), candidates.contains("ls"));
            assertTrue(candidates.toString(), candidates.contains("list-batch"));
        }
    }

    /**
     * Checks CLI completion for "help ls" command
     */
    @Test
    public void helpWithLsTest() {
        for (List<String> candidates : getCandidatesLists("help ls", true, -1)) {
                assertTrue(candidates.toString(), candidates.contains("ls"));
        }
    }

    /**
     * Checks CLI completion for "help :" command
     */
    @Test
    public void helpWithColonTest() {
        for (List<String> candidates : getCandidatesLists("help :", true, -1)) {
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
        }
    }

    /**
     * Checks CLI completion for "help deployment " command
     */
    @Test
    public void helpWithDeploymentTest() {
        for (List<String> candidates : getCandidatesLists("help deployment ", true, -1)) {
                assertTrue(candidates.toString(), candidates.contains("deploy-file"));
        }
    }

    /**
     * Checks CLI completion for "patch" command
     */
    @Test
    public void patchTest() {
        for (List<String> candidates : getCandidatesLists("patch", true, -1)) {
            assertTrue(candidates.toString(), candidates.contains("patch"));
        }
    }

    /**
     * Checks CLI completion for "pat" command
     */
    @Test
    public void patchCompleteTest() {
        for (List<String> candidates : getCandidatesLists("pat", true, -1)) {
            assertTrue(candidates.toString(), candidates.contains("patch"));
        }
    }

    /**
     * Checks CLI completion for "patch in" command
     */
    @Test
    public void patchInfoCompleteTest() {
        for (List<String> candidates : getCandidatesLists("patch in", true, -1)) {
            assertTrue(candidates.toString(), candidates.contains("info"));
        }
    }

    @Test
    public void testPropertiesNoValue() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = ":read-resource(";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":read-resource(recursive";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":read-resource(recursive,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":reload-servers(start-mode=normal,blocking";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertFalse(candidates.toString(), candidates.contains(","));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertFalse(candidates.toString(), candidates.contains(","));
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testPropertiesNoNegatedValue() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {

            {
                String cmd = " ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
                candidates = complete(ctx, cmd, true, -1);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
            }

            {
                String cmd = ":read-resource(";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
                assertTrue(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
                assertTrue(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
            }

            {
                String cmd = ":read-resource(" + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertFalse(candidates.toString(), candidates.contains("recursive-depth"));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertFalse(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":read-resource(" + Util.NOT_OPERATOR + "recursive";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains(")"));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains(")"));
            }

            {
                String cmd = ":read-resource(" + Util.NOT_OPERATOR + "recursive," + Util.NOT_OPERATOR + "resolve-expressions,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.contains("recursive"));
                candidates = complete(ctx, cmd, false, -1);
                assertFalse(candidates.toString(), candidates.contains("recursive"));
            }

            {
                String cmd = ":reload-servers(" + Util.NOT_OPERATOR + "blocking";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains(","));

                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains(","));

            }

            {
                String cmd = ":reload-servers(start-mode=normal," + Util.NOT_OPERATOR + "blocking";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(")"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(")"));
            }

            {
                String cmd = ":reload-servers(blocking=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("false"));

                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("false"));
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testRolloutGroupPropertiesNoValue() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 4);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failed-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failure-percentage"));
                assertTrue(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.size() == 4);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failed-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failure-percentage"));
                assertTrue(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains("=false"));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains("=false"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("max-failed-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failure-percentage"));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("max-failed-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failure-percentage"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers,max-failed-servers=1,max-failure-percentage=2";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(")"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(")"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(max-failed-servers=1,max-failure-percentage=2,rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains(")"));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains(")"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(" + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(" + Util.NOT_OPERATOR + "rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers,"));
                candidates = complete(ctx, cmd, false, 53);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers,"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(max-failed-servers=1,max-failure-percentage=2," + Util.NOT_OPERATOR + "rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains("=false"));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains("=false"));
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testCommandsCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {

            {
                String cmd = "batch --file";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--file="));
                candidates = complete(ctx, cmd, false, 6);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--file="));
            }

            {
                String cmd = "cd --no-validation";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--no-validation "));
                candidates = complete(ctx, cmd, false, 3);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--no-validation "));
            }

            {
                String cmd = "cd /deployment-";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
                candidates = complete(ctx, cmd, false, 4);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
            }

            {
                String cmd = "clear";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("clear"));
                candidates = complete(ctx, cmd, true, 0);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("clear"));
            }

            {
                String cmd = "clear ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--help"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "read-operation --node";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
                candidates = complete(ctx, cmd, false, 15);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
            }

            {
                String cmd = "read-operation --node=toto";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, true, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "read-operation --headers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers="));
                candidates = complete(ctx, cmd, false, 15);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers="));
            }

            {
                String cmd = "ls --resolve-expressions";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--resolve-expressions "));
                candidates = complete(ctx, cmd, false, 3);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--resolve-expressions "));
            }

            {
                String cmd = "ls -";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--help"));
                candidates = complete(ctx, cmd, false, 3);
                assertTrue(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "ls --resolve-expressions";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.contains("--help"));
                candidates = complete(ctx, cmd, false, -1);
                assertFalse(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "reload ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
                candidates = complete(ctx, cmd, false, -1);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
            }

            {
                // The parsing will concider the ! as a value and will be not
                // able to complete a value starting with the not operator
                String cmd = "reload " + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "reload --admin-only";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--admin-only="));
                candidates = complete(ctx, cmd, false, 7);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--admin-only="));
            }

            {
                String cmd = "reload --admin-only=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("true"));
                assertTrue(candidates.toString(), candidates.contains("false"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("true"));
                assertTrue(candidates.toString(), candidates.contains("false"));
            }

            {
                String cmd = "reload --admin-only=true --server-config=jss --use-current-server-config=true --headers={";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testCommandsExclusionArgCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "batch -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "batch --file";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--file="));
                candidates = complete(ctx, cmd, false, 6);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--file="));
            }

            {
                String cmd = "undeploy -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "deployment undeploy -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --disable";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --clear";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --enable";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --disable --cl";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "deploy -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "undeploy -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testCommandsCompletion2() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {

            {
                String cmd = "command add --node-type=/subsystem=logging/logger ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--command-name"));
                assertTrue(candidates.toString(), candidates.contains("--property-id"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--command-name"));
                assertTrue(candidates.toString(), candidates.contains("--property-id"));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--command-name="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--command-name".length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--command-name="));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("logger"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("logger"));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=logger";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(" "));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(" "));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=logger ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--property-id"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--property-id"));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=logger --property-id";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--property-id="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--property-id".length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--property-id="));
            }

            ctx.handle("command add --node-type=/system-property --command-name=prop");

            {
                String cmd = "prop ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("add"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("add"));
            }

            {
                String cmd = "prop add ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "prop add --name";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--name="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--name".length());
                assertTrue(candidates.toString(), candidates.contains("--name="));
            }

            {
                String cmd = "prop add --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--value"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("--value"));
            }

            {
                String cmd = "prop add --name=toto --value";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--value="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--value".length());
                assertTrue(candidates.toString(), candidates.contains("--value="));
            }

            {
                String cmd = "command add --node-child=/core-service=management/access=authorization ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--command-name"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--command-name"));
            }

            {
                String cmd = "command add --node-child=/core-service=management/access=authorization --command-name";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--command-name="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--command-name".length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--command-name="));
            }

            {
                String cmd = "command add --node-child=/core-service=management/access=authorization --command-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("authorization"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("authorization"));
            }

            {
                String cmd = "command add ---node-child=/core-service=management/access=authorization --command-name=authorization";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.isEmpty());
                candidates = complete(ctx, cmd, true, 0);
                assertTrue(candidates.isEmpty());
            }

            ctx.handle("command add --node-child=/core-service=management/access=authorization --command-name=authorization");

            {
                String cmd = "authorization ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--provider"));
                assertFalse(candidates.toString(), candidates.contains("add"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("--provider"));
                assertFalse(candidates.toString(), candidates.contains("add"));
            }

            {
                String cmd = "authorization read-attribute ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deploy ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--url"));
                assertTrue(candidates.toString(), candidates.contains("--name"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains("--url"));
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deployment ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("deploy-url"));
                assertTrue(candidates.toString(), candidates.contains("deploy-file"));
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.contains("deploy-url"));
                assertTrue(candidates.toString(), candidates.contains("deploy-file"));
            }

            {
                String cmd = "deployment deploy-url";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains(" "));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains(" "));
            }

            {
                String cmd = "deployment list --l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains(" "));
                candidates = complete(ctx, cmd, false, -1);
                assertTrue(candidates.toString(), candidates.contains(" "));
            }

            {
                String cmd = "deployment deploy-file ccc ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.contains("--name"));
                assertFalse(candidates.toString(), candidates.contains("--runtime-name"));
                candidates = complete(ctx, cmd, null, -1);
                assertFalse(candidates.toString(), candidates.contains("--name"));
                assertFalse(candidates.toString(), candidates.contains("--runtime-name"));
            }

            {
                String cmd = "rollout-plan";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rollout-plan"));
                candidates = complete(ctx, cmd, true, 0);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rollout-plan"));
            }

            {
                String cmd = "rollout-plan ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.contains("--commands"));
                assertFalse(candidates.toString(), candidates.contains("--properties"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.contains("--commands"));
                assertFalse(candidates.toString(), candidates.contains("--properties"));
            }

            {
                String cmd = "rollout-plan --help ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--commands"));
                assertTrue(candidates.toString(), candidates.contains("--properties"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--commands"));
                assertTrue(candidates.toString(), candidates.contains("--properties"));
            }

            {
                String cmd = "rollout-plan --name=csac ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--content"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "rollout-plan csac ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--help"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "run-batch --verbose";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--verbose "));
                candidates = complete(ctx, cmd, false, cmd.length() - "--verbose".length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--verbose "));
            }

            {
                // The parsing will concider the ! as a value and will be not
                // able to complete a value starting with the not operator
                String cmd = "read-attribute " + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "read-attribute --node";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--node".length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
            }

            {
                String cmd = "read-attribute --node=. ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertTrue(candidates.toString(), candidates.contains("name"));
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertTrue(candidates.toString(), candidates.contains("name"));
            }

            {
                String cmd = "read-attribute --node=/deployment-";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
                candidates = complete(ctx, cmd, false, cmd.length() - "deployment-".length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
            }

            {
                String cmd = "read-attribute --verbose";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--verbose "));
                candidates = complete(ctx, cmd, false, cmd.length() - "--verbose".length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--verbose "));
            }

            {
                String cmd = "read-attribute na";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("name"));
                assertTrue(candidates.toString(), candidates.contains("namespaces"));
                candidates = complete(ctx, cmd, false, cmd.length() - "na".length());
                assertTrue(candidates.toString(), candidates.contains("name"));
                assertTrue(candidates.toString(), candidates.contains("namespaces"));
            }

            {
                String cmd = "read-attribute name";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(" "));
                assertTrue(candidates.toString(), candidates.contains("namespaces"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(" "));
                assertTrue(candidates.toString(), candidates.contains("namespaces"));
            }

            {
                String cmd = "read-attribute name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "read-attribute management-minor-version";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(" "));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.contains(" "));
            }

            {
                String cmd = "read-operation --node";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--node".length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
            }

            {
                String cmd = "read-operation --node=. ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
            }

            {
                String cmd = "read-operation --node=/deployment-";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() >= 1);
                assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.size() >= 1);
                assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
            }

            {
                String cmd = "reload ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.contains("--start-mode"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.contains("--start-mode"));
            }

            {
                String cmd = "reload --admin-only";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--admin-only="));
                candidates = complete(ctx, cmd, false, cmd.length() - "--admin-only".length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--admin-only="));
            }

            {
                String cmd = "reload --admin-only=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(Util.TRUE));
                assertTrue(candidates.toString(), candidates.contains(Util.FALSE));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(Util.TRUE));
                assertTrue(candidates.toString(), candidates.contains(Util.FALSE));
            }

            {
                String cmd = "set toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "set toto=l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "set toto=`";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains(":"));
                assertTrue(candidates.toString(), candidates.contains("ls"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains(":"));
                assertTrue(candidates.toString(), candidates.contains("ls"));
            }

            {
                String cmd = "set toto=`l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("ls"));
                candidates = complete(ctx, cmd, false, cmd.length() - 1);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("ls"));
            }

            {
                String cmd = "unset toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testCommandsDeloymentOverlay() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "deployment-overlay ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains("--name"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deployment-overlay add ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "deployment-overlay add --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--content"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "deployment-overlay remove ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "deployment-overlay remove --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--content"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "deployment-overlay link --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains("--content"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "deployment-overlay redeploy-affected ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--name"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deployment-overlay redeploy-affected --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "deployment-overlay list-content --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                assertTrue(candidates.toString(), candidates.contains("-l"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                assertTrue(candidates.toString(), candidates.contains("-l"));
            }

            {
                String cmd = "deployment-overlay list-links --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() >= 2);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                assertTrue(candidates.toString(), candidates.contains("-l"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() >= 2);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                assertTrue(candidates.toString(), candidates.contains("-l"));
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testHeaders() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();

        {
            String cmd = "deployment-info --headers=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("{"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("{"), candidates);
        }

        {
            String cmd = "deployment info --headers=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("{"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("{"), candidates);
        }
        testHeader("ls -l --headers=", ctx);
        testHeader(":read-resource()", ctx);
    }

    private void testHeader(String radical, CommandContext ctx) throws Exception {
        {
            String cmd = radical + "{";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
        }

        {
            String cmd = radical + "{  ";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
        }

        {
            String cmd = radical + "{al";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("allow-resource-service-restart"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length() - 2);
            assertEquals(Arrays.asList("allow-resource-service-restart"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("="), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("="), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("false", "true"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("false", "true"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=t";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("true"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length() - 1);
            assertEquals(Arrays.asList("true"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("true;"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length() - "true".length());
            assertEquals(Arrays.asList("true;"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("rollback-on-runtime-failure"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length() - "rollback".length());
            assertEquals(Arrays.asList("rollback-on-runtime-failure"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("="), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("="), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("false", "true"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("false", "true"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=f";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("false"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length() - 1);
            assertEquals(Arrays.asList("false"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("false;"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length() - "false".length());
            assertEquals(Arrays.asList("false;"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("blocking-timeout", "rollout"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("blocking-timeout", "rollout"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;b";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("blocking-timeout"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length() - 1);
            assertEquals(Arrays.asList("blocking-timeout"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("="), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("="), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList(), candidates);
            candidates = complete(ctx, cmd, null, -1);
            assertEquals(Arrays.asList(), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout=14";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList(), candidates);
            candidates = complete(ctx, cmd, null, -1);
            assertEquals(Arrays.asList(), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout=14;";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("rollout"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("rollout"), candidates);
        }

        {
            String cmd = radical + "{foo=\"1 2 3\";allow-resource-service-restart=true;rollback-on-runtime-failure=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("false", "true"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("false", "true"), candidates);
        }

        {
            String cmd = radical + "{foo=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList(), candidates);
            candidates = complete(ctx, cmd, null, -1);
            assertEquals(Arrays.asList(), candidates);
        }

        {
            String cmd = radical + "{foo=\"";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList(), candidates);
            candidates = complete(ctx, cmd, null, -1);
            assertEquals(Arrays.asList(), candidates);
        }

        {
            String cmd = radical + "{foo=\"1 2 3";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList(), candidates);
            candidates = complete(ctx, cmd, null, -1);
            assertEquals(Arrays.asList(), candidates);
        }

        {
            String cmd = radical + "{foo=\"1 2 3\";";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
            candidates = complete(ctx, cmd, false, cmd.length());
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
        }
    }

    @Test
    public void testLs() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        {
            String cmd = "ls ";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertFalse(candidates.toString(), candidates.isEmpty());
            assertFalse(candidates.toString(), candidates.contains("--storage"));
            candidates = complete(ctx, cmd, false, cmd.length());
            assertFalse(candidates.toString(), candidates.isEmpty());
            assertFalse(candidates.toString(), candidates.contains("--storage"));
        }

        {
            String cmd = "ls -l ";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertFalse(candidates.toString(), candidates.isEmpty());
            assertTrue(candidates.toString(), candidates.contains("--storage"));
            assertTrue(candidates.toString(), candidates.contains("--max"));
            assertTrue(candidates.toString(), candidates.contains("--min"));
            assertTrue(candidates.toString(), candidates.contains("--description"));
            assertTrue(candidates.toString(), candidates.contains("--nillable"));
            candidates = complete(ctx, cmd, false, cmd.length());
            assertFalse(candidates.toString(), candidates.isEmpty());
            assertTrue(candidates.toString(), candidates.contains("--storage"));
            assertTrue(candidates.toString(), candidates.contains("--max"));
            assertTrue(candidates.toString(), candidates.contains("--min"));
            assertTrue(candidates.toString(), candidates.contains("--description"));
            assertTrue(candidates.toString(), candidates.contains("--nillable"));
        }

        {
            String cmd = "ls /deployment-";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.size() == 1);
            assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
            candidates = complete(ctx, cmd, false, 4);
            assertTrue(candidates.toString(), candidates.size() == 1);
            assertTrue(candidates.toString(), candidates.contains("deployment-overlay="));
        }
    }

    @Test
    public void testCommandsCompletion3() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "echo-dmr /serv";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("server-group="));
                candidates = complete(ctx, cmd, false, "echo-dmr".length() + 2);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("server-group="));
            }
            {
                String cmd = "if (true) of /serv";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("server-group="));
                candidates = complete(ctx, cmd, false, cmd.length() - "serv".length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("server-group="));
            }
            {
                ctx.handle("batch");
                ctx.handle(":read-resource()");
                try {
                    String cmd = "edit-batch-line 1 /serv";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue(candidates.toString(), candidates.size() == 1);
                    assertTrue(candidates.toString(), candidates.contains("server-group="));
                    candidates = complete(ctx, cmd, false, cmd.length() - "serv".length());
                    assertTrue(candidates.toString(), candidates.size() == 1);
                    assertTrue(candidates.toString(), candidates.contains("server-group="));
                } finally {
                    if (ctx.isBatchMode()) {
                        ctx.handle("discard-batch");
                    }
                }
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testAttachmentCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "attachment ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 3);
                assertEquals(candidates.toString(), Arrays.asList("--help",
                        "display", "save"), candidates);
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 3);
                assertEquals(candidates.toString(), Arrays.asList("--help",
                        "display", "save"), candidates);
            }
            {
                String cmd = "attachment d";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("display "),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 1);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("display "),
                        candidates);
            }

            {
                String cmd = "attachment s";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("save "),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 1);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("save "),
                        candidates);
            }

            {
                String cmd = "attachment ww";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "attachment ww ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "attachment display ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("--operation"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("--operation"),
                        candidates);
            }

            {
                String cmd = "attachment       display      ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("--operation"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertEquals(candidates.toString(), Arrays.asList("--operation"),
                        candidates);
            }

            {
                String cmd = "attachment display --operation=:read-resou";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() >= 1);
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
                candidates = complete(ctx, cmd, false, cmd.length() - "read-resou".length());
                assertTrue(candidates.toString(), candidates.size() >= 1);
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
            }

            {
                String cmd = "attachment display --operation=:read-resource() ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "attachment display --operation=:read-resource() ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "attachment save --operation=:read-resource() --";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 4);
                assertEquals(candidates.toString(), Arrays.asList("--createDirs", "--file",
                        "--headers", "--overwrite"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 2);
                assertTrue(candidates.toString(), candidates.size() == 4);
                assertEquals(candidates.toString(), Arrays.asList("--createDirs", "--file",
                        "--headers", "--overwrite"),
                        candidates);
            }

            {
                String cmd = "attachment save --operation=:read-resource() --file=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testInvalidOperation() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "/subsystem=ohnoabug:dodo(cc";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null, -1);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testNoArgumentCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "cd --hel";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--help"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - "--hel".length());
                assertEquals(candidates.toString(), Arrays.asList("--help"),
                        candidates);
            }

            {
                String cmd = "cd --no";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--no-validation"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - "--no".length());
                assertEquals(candidates.toString(), Arrays.asList("--no-validation"),
                        candidates);
            }

            {
                String cmd = "ls --hel";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--help"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - "--hel".length());
                assertEquals(candidates.toString(), Arrays.asList("--help"),
                        candidates);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testVariablesCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            ctx.handle("set varName=foo");
            ctx.handle("set varName2=barbarbarbar");
            {
                String cmd = "/deployment=$";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("varName", "varName2"),
                        candidates);
                candidates = complete(ctx, cmd, null, cmd.length());
                assertEquals(candidates.toString(), Arrays.asList("varName", "varName2"),
                        candidates);
            }

            {
                String cmd = "/deployment=$varName:read-co";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 21, offset);
                assertEquals(candidates.toString(), Arrays.asList("read-content"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - "read-co".length());
                assertEquals(candidates.toString(), Arrays.asList("read-content"),
                        candidates);
            }

            {
                String cmd = "/server-group=$varName/deployment=$varName2:wh";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 44, offset);
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - "wh".length());
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
            }

            {
                String cmd = "/server-group=$varName/deployment=$varName2:read-resource() {a";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 61, offset);
                assertEquals(candidates.toString(), Arrays.asList("allow-resource-service-restart"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 1);
                assertEquals(candidates.toString(), Arrays.asList("allow-resource-service-restart"),
                        candidates);
            }

            {
                String cmd = "/server-group=$varName/deployment=$varName2:read-resource() {allow-resource-service-restart";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 91, offset);
                assertEquals(candidates.toString(), Arrays.asList("="),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length());
                assertEquals(candidates.toString(), Arrays.asList("="),
                        candidates);
            }

            {
                String cmd = "/server-group=$varName/deployment=$varName2:read-resource() {allow-resource-service-restart=t";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 92, offset);
                assertEquals(candidates.toString(), Arrays.asList("true"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 1);
                assertEquals(candidates.toString(), Arrays.asList("true"),
                        candidates);
            }

            {
                String cmd = "/server-group=$varName/deployment=$varName2:read-resource() {allow-resource-service-restart=$varName2;rollback-on-runtime-failure=f";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 130, offset);
                assertEquals(candidates.toString(), Arrays.asList("false"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 1);
                assertEquals(candidates.toString(), Arrays.asList("false"),
                        candidates);
            }

            {
                String cmd = "attachment display --operation=/server-group=$varName/deployment=$varName2:wh";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 75, offset);
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 2);
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
            }

            {
                String cmd = "if () of /server-group=$varName/deployment=$varName2:wh";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 53, offset);
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 2);
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
            }

            {
                ctx.handle("batch");
                try {
                    ctx.handle(":read-resource");
                    String cmd = "edit-batch-line 1 /server-group=$varName/deployment=$varName2:wh";
                    List<String> candidates = new ArrayList<>();
                    int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertEquals(candidates.toString(), 62, offset);
                    assertEquals(candidates.toString(), Arrays.asList("whoami"),
                            candidates);
                    candidates = complete(ctx, cmd, false, cmd.length() - 2);
                    assertEquals(candidates.toString(), Arrays.asList("whoami"),
                            candidates);
                } finally {
                    ctx.handle("discard-batch");
                }
            }

            {
                String cmd = "echo-dmr /server-group=$varName/deployment=$varName2:wh";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), 53, offset);
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
                candidates = complete(ctx, cmd, false, cmd.length() - 2);
                assertEquals(candidates.toString(), Arrays.asList("whoami"),
                        candidates);
            }

            {
                String cmd = "deployment deploy-file $";
                List<String> candidates = new ArrayList<>();
                int offset = ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), cmd.length(), offset);
                assertEquals(candidates.toString(), Arrays.asList("varName", "varName2"),
                        candidates);
                candidates = complete(ctx, cmd, null, cmd.length());
                assertEquals(candidates.toString(), Arrays.asList("varName", "varName2"),
                        candidates);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testFor() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "for var ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("in"), candidates);
                candidates = complete(ctx, cmd, false, cmd.length());
                assertEquals(candidates.toString(), Arrays.asList("in"), candidates);
            }

            {
                String cmd = "for var in ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains(":"));
                candidates = complete(ctx, cmd, false, cmd.length());
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains(":"));
            }

            {
                String cmd = "done ";
                ctx.handle("for var in :read-resource");
                try {
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue(candidates.toString(), candidates.contains("--discard"));
                    candidates = complete(ctx, cmd, false, cmd.length());
                    assertTrue(candidates.toString(), candidates.contains("--discard"));
                } finally {
                    ctx.handle("done --discard");
                }
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    @Ignore("https://issues.jboss.org/browse/WFCORE-3489")
    public void testIfInFor() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();

        try {

            ctx.handle("for var in :read-resource");
            try {

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"if\": " + candidates.toString(), candidates.contains("if"));
                    assertFalse("candidates contain \"else\": " + candidates.toString(), candidates.contains("else"));
                    assertFalse("candidates contain \"end-if\": " + candidates.toString(), candidates.contains("end-if"));
                }

                ctx.handle("if (outcome == success) of /system-property=test:read-resource");
                try {

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"if\": " + candidates.toString(), candidates.contains("if"));
                        assertTrue("candidates do not contain \"else\": " + candidates.toString(), candidates.contains("else"));
                        assertTrue("candidates do not contain \"end-if\": " + candidates.toString(), candidates.contains("end-if"));
                    }

                    ctx.handle("else");

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"if\": " + candidates.toString(), candidates.contains("if"));
                        assertFalse("candidates contain \"else\": " + candidates.toString(), candidates.contains("else"));
                        assertTrue("candidates do not contain \"end-if\": " + candidates.toString(), candidates.contains("end-if"));
                    }

                } finally {
                    ctx.handle("end-if");
                }

            } finally {
                ctx.handle("done --discard");
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    @Ignore("https://issues.jboss.org/browse/WFCORE-3489")
    public void testForInIf() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();

        try {

            ctx.handle("if (outcome == success) of /system-property=test:read-resource");
            try {

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"for\": " + candidates.toString(), candidates.contains("for"));
                    assertFalse("candidates contain \"done\": " + candidates.toString(), candidates.contains("done"));
                }

                ctx.handle("for var in :read-resource");

                try {

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"for\": " + candidates.toString(), candidates.contains("for"));
                        assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                    }

                } finally {
                    ctx.handle("done --discard");
                }

                ctx.handle("else");

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"for\": " + candidates.toString(), candidates.contains("for"));
                    assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                }

                ctx.handle("for var in :read-resource");

                try {

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"for\": " + candidates.toString(), candidates.contains("for"));
                        assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                    }

                } finally {
                    ctx.handle("done --discard");
                }

            } finally {
                ctx.handle("end-if");
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    @Ignore("https://issues.jboss.org/browse/WFCORE-3489")
    public void testTryInFor() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();

        try {

            ctx.handle("for var in :read-resource");
            try {

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"try\": " + candidates.toString(), candidates.contains("try"));
                    assertFalse("candidates contain \"catch\": " + candidates.toString(), candidates.contains("catch"));
                    assertFalse("candidates contain \"finally\": " + candidates.toString(), candidates.contains("finally"));
                    assertFalse("candidates contain \"end-try\": " + candidates.toString(), candidates.contains("end-try"));
                }

                ctx.handle("try");
                try {

                    try {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"try\": " + candidates.toString(), candidates.contains("try"));
                        assertTrue("candidates do not contain \"catch\": " + candidates.toString(), candidates.contains("catch"));
                        assertTrue("candidates do not contain \"finally\": " + candidates.toString(), candidates.contains("finally"));
                        assertTrue("candidates do not contain \"end-try\": " + candidates.toString(), candidates.contains("end-try"));
                    } finally {
                        ctx.handle("catch");
                    }

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"try\": " + candidates.toString(), candidates.contains("try"));
                        assertFalse("candidates contain \"catch\": " + candidates.toString(), candidates.contains("catch"));
                        assertTrue("candidates do not contain \"finally\": " + candidates.toString(), candidates.contains("finally"));
                        assertTrue("candidates do not contain \"end-try\": " + candidates.toString(), candidates.contains("end-try"));
                    }

                    ctx.handle("finally");

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"try\": " + candidates.toString(), candidates.contains("try"));
                        assertFalse("candidates contain \"catch\": " + candidates.toString(), candidates.contains("catch"));
                        assertFalse("candidates contain \"finally\": " + candidates.toString(), candidates.contains("finally"));
                        assertTrue("candidates do not contain \"end-try\": " + candidates.toString(), candidates.contains("end-try"));
                    }

                } finally {
                    ctx.handle("end-try");
                }

            } finally {
                ctx.handle("done --discard");
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    @Ignore("https://issues.jboss.org/browse/WFCORE-3489")
    public void testForInTry() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();

        try {

            ctx.handle("try");
            try {

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"for\": " + candidates.toString(), candidates.contains("for"));
                    assertFalse("candidates contain \"done\": " + candidates.toString(), candidates.contains("done"));
                }

                ctx.handle("for var in :read-resource");

                try {

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"for\": " + candidates.toString(), candidates.contains("for"));
                        assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                    }

                } finally {
                    ctx.handle("done --discard");
                    ctx.handle("catch");
                }

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"for\": " + candidates.toString(), candidates.contains("for"));
                    assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                }

                ctx.handle("for var in :read-resource");

                try {

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"for\": " + candidates.toString(), candidates.contains("for"));
                        assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                    }

                } finally {
                    ctx.handle("done --discard");
                }

                ctx.handle("finally");

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"for\": " + candidates.toString(), candidates.contains("for"));
                    assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                }

                ctx.handle("for var in :read-resource");

                try {

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"for\": " + candidates.toString(), candidates.contains("for"));
                        assertTrue("candidates do not contain \"done\": " + candidates.toString(), candidates.contains("done"));
                    }

                } finally {
                    ctx.handle("done --discard");
                }

            } finally {
                ctx.handle("end-try");
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    @Ignore("https://issues.jboss.org/browse/WFCORE-3489")
    public void testBatchInFor() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();

        try {

            ctx.handle("for var in :read-resource");
            try {

                {
                    String cmd = "";
                    List<String> candidates = new ArrayList<>();
                    ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                            cmd.length(), candidates);
                    assertTrue("candidates do not contain \"batch\": " + candidates.toString(), candidates.contains("batch"));
                    assertFalse("candidates contain \"run-batch\": " + candidates.toString(), candidates.contains("run-batch"));
                    assertFalse("candidates contain \"discard-batch\": " + candidates.toString(), candidates.contains("discard-batch"));
                }

                ctx.handle("batch");
                try {

                    {
                        String cmd = "";
                        List<String> candidates = new ArrayList<>();
                        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                                cmd.length(), candidates);
                        assertFalse("candidates contain \"batch\": " + candidates.toString(), candidates.contains("batch"));
                        assertTrue("candidates do not contain \"run-batch\": " + candidates.toString(), candidates.contains("run-batch"));
                        assertTrue("candidates do not contain \"discard-batch\": " + candidates.toString(), candidates.contains("discard-batch"));
                    }

                } finally {
                    ctx.handle("discard-batch");
                }

            } finally {
                ctx.handle("done --discard");
            }

        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testRequiredArgument() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = ":write-attribute(";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(Arrays.asList("name*", "value"), candidates);
                candidates = complete(ctx, cmd, false, cmd.length());
                assertEquals(Arrays.asList("name*", "value"), candidates);
            }

            {
                String cmd = ":write-attribute(value=toto,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(Arrays.asList("name"), candidates);
                candidates = complete(ctx, cmd, false, cmd.length());
                assertEquals(Arrays.asList("name"), candidates);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testCommandsDeployment() throws Exception {
        testDeployAction("deploy-file", true);
        testDeployAction("deploy-url", false);
        testDisableAction("disable-all");
        testDisableAction("disable foo");
        testEnableAction("enable-all");
        testEnableAction("enable foo");
        testDeploymentInfo();
        testUndeploy();
        testCliArchiveAction("deploy-cli-archive");
        testCliArchiveAction("undeploy-cli-archive");
    }

    private void testDeployAction(String action, boolean unmanaged) {
        {
            String cmd = "deployment " + action + " foo ";
            List<String> candidates = complete(ctx, cmd, null, cmd.length());
            assertTrue(candidates.toString(), candidates.contains("--all-server-groups"));
            assertTrue(candidates.toString(), candidates.contains("--server-groups="));
            assertFalse(candidates.toString(), candidates.contains("--disabled"));
            assertFalse(candidates.toString(), candidates.contains("--enabled"));
            assertTrue(candidates.toString(), candidates.contains("--replace"));
            assertTrue(candidates.toString(), candidates.contains("--name="));
            assertTrue(candidates.toString(), candidates.contains("--runtime-name="));
            if (unmanaged) {
                assertTrue(candidates.toString(), candidates.contains("--unmanaged"));
            }
        }
        {
            String cmd = "deployment " + action + " foo --all-server-groups ";
            List<String> candidates = complete(ctx, cmd, null, cmd.length());
            assertFalse(candidates.toString(), candidates.contains("--replace"));
        }
        {
            String cmd = "deployment " + action + " foo --server-groups=foo ";
            List<String> candidates = complete(ctx, cmd, null, cmd.length());
            assertFalse(candidates.toString(), candidates.contains("--replace"));
        }
        {
            String cmd = "deployment " + action + " foo --replace ";
            List<String> candidates = complete(ctx, cmd, null, cmd.length());
            assertFalse(candidates.toString(), candidates.contains("--all-server-groups"));
            assertFalse(candidates.toString(), candidates.contains("--server-groups="));
        }
        {
            String cmd = "deployment " + action + " foo ";
            testServerGroupsCompletion(cmd);
        }
    }

    private void testDisableAction(String action) {
        String cmd = "deployment " + action + " ";
        List<String> candidates = complete(ctx, cmd, null, cmd.length());
        assertTrue(candidates.toString(), candidates.contains("--all-relevant-server-groups"));
        assertTrue(candidates.toString(), candidates.contains("--server-groups="));
        testServerGroupsCompletion(cmd);
    }

    private void testDeploymentInfo() {
        String cmd = "deployment info ";
        List<String> candidates = complete(ctx, cmd, null, cmd.length());
        assertTrue(candidates.toString(), candidates.contains("--server-group="));
    }

    private void testEnableAction(String action) {
        String cmd = "deployment " + action + " ";
        List<String> candidates = complete(ctx, cmd, null, cmd.length());
        assertTrue(candidates.toString(), candidates.contains("--all-server-groups"));
        assertTrue(candidates.toString(), candidates.contains("--server-groups="));
        testServerGroupsCompletion(cmd);
    }

    private void testUndeploy() {
        String cmd = "deployment undeploy foo ";
        List<String> candidates = complete(ctx, cmd, null, cmd.length());
        assertTrue(candidates.toString(), candidates.contains("--all-relevant-server-groups"));
        assertTrue(candidates.toString(), candidates.contains("--server-groups="));
        testServerGroupsCompletion(cmd);
    }

    private void testCliArchiveAction(String action) {
        String cmd = "deployment " + action + " foo ";
        List<String> candidates = complete(ctx, cmd, null, cmd.length());
        assertTrue(candidates.toString(), candidates.contains("--script="));
    }

    private void testServerGroupsCompletion(String command) {
        String cmd = command + " " + "--server-groups=";
        List<String> candidates = complete(ctx, cmd, null, cmd.length());
        assertTrue(candidates.toString(), candidates.size() >= 2);
        List<String> sgroups = candidates;
        String sg1 = candidates.get(0).substring(0, candidates.get(0).length() / 2);
        cmd = command + " " + "--server-groups=" + sg1;
        candidates = complete(ctx, cmd, null, 0);
        assertEquals(candidates.toString(), Arrays.asList(sgroups.get(0)), candidates);
        cmd = command + " " + "--server-groups=" + sgroups.get(0);
        candidates = complete(ctx, cmd, null, 0);
        assertEquals(candidates.toString(), Arrays.asList(","), candidates);
        cmd = command + " " + "--server-groups=" + sgroups.get(0) + ",";
        candidates = complete(ctx, cmd, null, 0);
        assertFalse(candidates.toString(), candidates.contains(sgroups.get(0)));
        cmd = command + " " + "--server-groups="
                + sgroups.get(0) + "," + sgroups.get(1);
        candidates = complete(ctx, cmd, null, 0);
        assertFalse(candidates.toString(), candidates.contains(sgroups.get(1)));
    }

    /**
     * Legacy way of CLI completion
     */
    private List<String> oldWayCompletion(String cmd) {
        List<String> candidates = new ArrayList<>();
        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                cmd.length(), candidates);
        return candidates;
    }

    /**
     * Return two lists with candidates for CLI completion. Each list of candidates is generated by different way.
     */
    private List<List<String>> getCandidatesLists(String cmd, Boolean separator, int offset) {
        List<List<String>> candidatesLists = new ArrayList<>();

        // old way completion
        List<String> candidates1 = oldWayCompletion(cmd);

        // aesh-readline completion
        List<String> candidates2 = complete(ctx, cmd, separator, offset);

        candidatesLists.add(candidates1);
        candidatesLists.add(candidates2);
        return candidatesLists;
    }

    // This completion is what aesh-readline completion is calling, so more
    // similar to interactive CLI session
    private List<String> complete(CommandContext ctx, String cmd, Boolean separator, int offset) {
        Completion<AeshCompleteOperation> completer
                = (Completion<AeshCompleteOperation>) ctx.getDefaultCommandCompleter();
        AeshCompleteOperation op = new AeshCompleteOperation(cmd, cmd.length());
        completer.complete(op);
        if (separator != null) {
            assertEquals(op.hasAppendSeparator(), separator);
        }
        if (offset > 0) {
            assertEquals(op.getOffset(), offset);
        }
        List<String> candidates = new ArrayList<>();
        for (TerminalString ts : op.getCompletionCandidates()) {
            candidates.add(ts.getCharacters());
        }
        return candidates;
    }
}
