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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class CliCompletionTestCase {

    @Test
    public void test() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "reload ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.contains("--start-mode"));
                assertFalse(candidates.contains("--admin-only"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.contains("--start-mode"));
                assertFalse(candidates.contains("--admin-only"));
            }

            {
                String cmd = "reload --start-mode=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 3);
                assertEquals(candidates.toString(), Arrays.asList("admin-only",
                        "normal", "suspend"), candidates);
                candidates = complete(ctx, cmd, false);
                assertTrue(candidates.toString(),
                        candidates.size() == 3);
                assertEquals(candidates.toString(),
                        Arrays.asList("admin-only",
                                "normal", "suspend"), candidates);
            }

            {
                String cmd = "/subsystem=elytron/token-realm=JwtRealm:add(jwt={}";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList(","), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(candidates.toString(), Arrays.asList(","),
                        candidates);
            }

            {
                String cmd = "/subsystem=logging/logger=cdsc:add(category=cdsc,"
                        + "filter={accept=true,all={},change-level=ALL,not={},"
                        + "level-range={min-level=ALL,max-level=ALL,"
                        + "max-inclusive=true,min-inclusive=false}";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList(","), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(candidates.toString(), Arrays.asList(","),
                        candidates);
            }

            {
                String cmd = "help";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("help"));
                candidates = complete(ctx, cmd, true);
                assertTrue(candidates.toString(),
                        candidates.get(0).equals("help"));
            }

            {
                String cmd = "help --";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--commands"));
                candidates = complete(ctx, cmd, true);
                assertTrue(candidates.toString(),
                        candidates.contains("--commands"));
            }

            {
                String cmd = "help l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("ls"));
                assertTrue(candidates.toString(), candidates.contains("list-batch"));
                candidates = complete(ctx, cmd, true);
                assertTrue(candidates.toString(),
                        candidates.contains("ls"));
                assertTrue(candidates.toString(),
                        candidates.contains("list-batch"));
            }

            {
                String cmd = "help ls";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("ls"));
                candidates = complete(ctx, cmd, true);
                assertTrue(candidates.toString(),
                        candidates.contains("ls"));
            }

            {
                String cmd = "help :";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
                candidates = complete(ctx, cmd, true);
                assertTrue(candidates.toString(),
                        candidates.contains("read-resource"));
            }

            {
                String cmd = "help deployment ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("deploy-file"));
                candidates = complete(ctx, cmd, true);
                assertTrue(candidates.toString(),
                        candidates.contains("deploy-file"));
            }

            {
                String cmd = "deploy";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
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

            {
                String cmd = "deployment";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
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

            {
                String cmd = "/subsystem";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("="));
                candidates = complete(ctx, cmd, false);
                assertTrue(candidates.toString(),
                        candidates.contains("="));
            }
            {
                String cmd = ":read-resource(p";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("proxies"));
                candidates = complete(ctx, cmd, false);
                assertTrue(candidates.toString(),
                        candidates.contains("proxies"));
            }
        } finally {
            ctx.terminateSession();
        }
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
