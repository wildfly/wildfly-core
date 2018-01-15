/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
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
 *
 */
package org.jboss.as.test.integration.management.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class CliCompletionTestCase {

    private static CommandContext ctx;

    /**
     * Initialize CommandContext before all tests
     */
    @BeforeClass
    public static void init() throws Exception {
        ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
    }

    /**
     * Terminate CommandContext after all tests are executed
     */
    @AfterClass
    public static void close() {
        ctx.terminateSession();
    }

    @Test
    public void reloadWithSpaceTest() {
        for (List<String> candidates : getCandidatesLists("reload ", null)) {
            assertTrue(candidates.contains("--start-mode"));
            assertFalse(candidates.contains("--admin-only"));
        }
    }

    @Test
    public void reloadWithArgumentTest() {
        for (List<String> candidates : getCandidatesLists("reload --start-mode=", false)) {
            assertTrue(candidates.toString(), candidates.size() == 3);
            assertEquals(candidates.toString(), Arrays.asList("admin-only",
                    "normal", "suspend"), candidates);
        }
    }

    @Test
    public void operationWithObjectAsAttributeTest() {
        for (List<String> candidates : getCandidatesLists("/subsystem=elytron/token-realm=JwtRealm:add(jwt={}", false)) {
            assertEquals(candidates.toString(), Arrays.asList(","), candidates);
        }
    }

    @Test
    public void complexOperationWithObjectAsAttributeTest() {
        for (List<String> candidates : getCandidatesLists("/subsystem=logging/logger=cdsc:add(category=cdsc,"
                + "filter={accept=true,all={},change-level=ALL,not={},"
                + "level-range={min-level=ALL,max-level=ALL,"
                + "max-inclusive=true,min-inclusive=false}", false)) {
            assertEquals(candidates.toString(), Arrays.asList(","), candidates);
        }
    }

    /**
     * Checks CLI completion for "help" command
     */
    @Test
    public void helpTest() {
        for (List<String> candidates : getCandidatesLists("help", true)) {
            assertTrue(candidates.toString(), candidates.contains("help"));
        }
    }

    /**
     * Checks CLI completion for "help --" command
     */
    @Test
    public void helpWithUnifinishedArgumentTest() {
        for (List<String> candidates : getCandidatesLists("help --", true)) {
            assertTrue(candidates.toString(), candidates.contains("--commands"));
        }
    }

    /**
     * Checks CLI completion for "help l" command
     */
    @Test
    public void helpWithLCharTest() {
        for (List<String> candidates : getCandidatesLists("help l", true)) {
            assertTrue(candidates.toString(), candidates.contains("ls"));
            assertTrue(candidates.toString(), candidates.contains("list-batch"));
        }
    }

    /**
     * Checks CLI completion for "help ls" command
     */
    @Test
    public void helpWithLsTest() {
        for (List<String> candidates : getCandidatesLists("help ls", true)) {
            assertTrue(candidates.toString(), candidates.contains("ls"));
        }
    }

    /**
     * Checks CLI completion for "help :" command
     */
    @Test
    public void helpWithColonTest() {
        for (List<String> candidates : getCandidatesLists("help :", true)) {
            assertTrue(candidates.toString(), candidates.contains("read-resource"));
        }
    }

    /**
     * Checks CLI completion for "help deployment " command
     */
    @Test
    public void helpWithDeploymentTest() {
        for (List<String> candidates : getCandidatesLists("help deployment ", true)) {
                assertTrue(candidates.toString(), candidates.contains("deploy-file"));
        }
    }

    @Test
    public void deployTest() {
        String cmd = "deploy";
        List<String> candidates = oldWayCompletion(cmd);
        assertTrue(candidates.toString(), candidates.size() == 4);
        assertEquals(candidates.toString(), Arrays.asList("deploy",
                "deployment", "deployment-info", "deployment-overlay"),
                candidates);
        candidates = complete(ctx, cmd, true);
        assertTrue(candidates.toString(),
                candidates.size() == 4);
        // Sorting of candidates is done in the aesh display layer
        assertTrue(candidates.toString(), candidates.contains("deploy"));
        assertTrue(candidates.toString(), candidates.contains("deployment"));
        assertTrue(candidates.toString(), candidates.contains("deployment-info"));
        assertTrue(candidates.toString(), candidates.contains("deployment-overlay"));
    }

    @Test
    public void deploymentTest() {
        String cmd = "deployment";
        List<String> candidates = oldWayCompletion(cmd);
        assertTrue(candidates.toString(), candidates.size() == 3);
        assertEquals(candidates.toString(), Arrays.asList(
                "deployment", "deployment-info", "deployment-overlay"),
                candidates);
        candidates = complete(ctx, cmd, true);
        assertTrue(candidates.toString(),
                candidates.size() == 3);
        // Sorting of candidates is done in the aesh display layer
        assertTrue(candidates.toString(), candidates.contains("deployment"));
        assertTrue(candidates.toString(), candidates.contains("deployment-info"));
        assertTrue(candidates.toString(), candidates.contains("deployment-overlay"));
    }

    @Test
    public void subsystemTest() {
        for (List<String> candidates : getCandidatesLists("/subsystem", false)) {
                assertTrue(candidates.toString(), candidates.contains("="));
        }
    }

    @Test
    public void operationWithCharAsAttributeTest() {
        for (List<String> candidates : getCandidatesLists(":read-resource(p", false)) {
                assertTrue(candidates.toString(), candidates.contains("proxies"));
        }
    }

    @Test
    public void connectTest() {
        for (List<String> candidates : getCandidatesLists("connect ", true)) {
                assertTrue(candidates.toString(), candidates.contains("--bind="));
        }
    }

    @Test
    public void connectWithUnifinishedArgumentTest() {
        for (List<String> candidates : getCandidatesLists("connect --", true)) {
                assertTrue(candidates.toString(), candidates.contains("--bind="));
        }
    }

    @Test
    public void testDeploymentAdd() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        String op = "/deployment=toto:add(content=";
        try {
            {
                String cmd = op + "[{";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive", "bytes*", "empty*", "hash*",
                        "input-stream-index*", "path*", "relative-to", "url*"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive", "bytes*", "empty*", "hash*",
                        "input-stream-index*", "path*", "relative-to", "url*"), candidates);
            }

            {
                String prefix = System.currentTimeMillis() + "cliCompletionTest";
                File f = File.createTempFile(prefix, null);
                f.deleteOnExit();
                File parent = f.getParentFile();
                String cmd = op + "[{input-stream-index=" + escapePath(parent.getAbsolutePath() + File.separator + prefix);
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList(f.getName()), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList(f.getName()), candidates);
            }

            {
                String prefix = System.currentTimeMillis() + "cliCompletionTest";
                File f = File.createTempFile(prefix, null);
                f.deleteOnExit();
                String cmd = op + "[{input-stream-index=" + escapePath(f.getAbsolutePath());
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("}"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("}"), candidates);
            }

            {
                String cmd = op + "[{path=xxx,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive*", "relative-to"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive*", "relative-to"), candidates);
            }

            {
                String cmd = op + "[{relative-to=xxx,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive", "path*"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive", "path*"), candidates);
            }

            {
                String cmd = op + "[{archive=true,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("empty*", "hash*", "path*", "relative-to"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("empty*", "hash*", "path*", "relative-to"), candidates);
            }

            {
                String cmd = op + "[{empty=true";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList(","), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList(","), candidates);
            }

            {
                String cmd = op + "[{empty=true,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive"), candidates);
            }

            {
                String cmd = op + "[{empty=true,archive=true";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("}"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("}"), candidates);
            }

            {
                // If the prefix of a property name is typed,
                // the property name (if it exists, hidden or not) is proposed.
                String cmd = op + "[{empty=true,archive=true,i";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("input-stream-index"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("input-stream-index"), candidates);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void operatorArgumentCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "version |";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("grep"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("grep"));
            }

            {
                String cmd = "version | ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("grep"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("grep"));
            }

            {
                String cmd = "version >";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "version > ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "version >>";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "version >> ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }
        } finally {
            ctx.terminateSession();
        }
    }

    private String escapePath(String filePath) {
        if (Util.isWindows()) {
            StringBuilder builder = new StringBuilder();
            for (char c : filePath.toCharArray()) {
                if (c == '\\') {
                    builder.append('\\');
                }
                builder.append(c);
            }
            return builder.toString();
        } else {
            return filePath;
        }
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
    private List<List<String>> getCandidatesLists(String cmd, Boolean separator) {
        List<List<String>> candidatesLists = new ArrayList<>();

        // old way completion
        List<String> candidates1 = oldWayCompletion(cmd);

        // aesh-readline completion
        List<String> candidates2 = complete(ctx, cmd, separator);

        candidatesLists.add(candidates1);
        candidatesLists.add(candidates2);
        return candidatesLists;
    }

    // This completion is what aesh-readline completion is calling, so more
    // similar to interactive CLI session
    private List<String> complete(CommandContext ctx, String cmd, Boolean separator) {
        Completion<AeshCompleteOperation> completer
                = (Completion<AeshCompleteOperation>) ctx.getDefaultCommandCompleter();
        AeshCompleteOperation op = new AeshCompleteOperation(cmd, cmd.length());
        completer.complete(op);
        if (separator != null) {
            assertEquals(op.hasAppendSeparator(), separator);
        }
        List<String> candidates = new ArrayList<>();
        for (TerminalString ts : op.getCompletionCandidates()) {
            candidates.add(ts.getCharacters());
        }
        return candidates;
    }
}
