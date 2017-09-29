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
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
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
            }

            {
                String cmd = "reload --start-mode=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() == 3);
                assertEquals(candidates.toString(), Arrays.asList("admin-only",
                        "normal", "suspend"), candidates);
            }

            {
                String cmd = "/subsystem=elytron/token-realm=JwtRealm:add(jwt={}";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList(","), candidates);
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
            }

            {
                String cmd = "help --";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--commands"));
            }

            {
                String cmd = "help l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("ls"));
            }

            {
                String cmd = "help ls";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains(" "));
            }

            {
                String cmd = "help :";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
            }

            {
                String cmd = "help deployment ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("deploy-file"));
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
            }
        } finally {
            ctx.terminateSession();
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
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive", "bytes*", "empty*", "hash*",
                        "input-stream-index*", "path*", "relative-to", "url*"), candidates);
            }

            {

                String prefix = System.currentTimeMillis() + "cliCompletionTest";
                File f = File.createTempFile(prefix, null);
                f.deleteOnExit();
                File parent = f.getParentFile();
                String cmd = op + "[{input-stream-index=" + escapePath(parent.getAbsolutePath() + File.separator + prefix);
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList(f.getName()), candidates);
            }

            {
                String prefix = System.currentTimeMillis() + "cliCompletionTest";
                File f = File.createTempFile(prefix, null);
                f.deleteOnExit();
                String cmd = op + "[{input-stream-index=" + escapePath(f.getAbsolutePath());
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("}"), candidates);
            }

            {
                String cmd = op + "[{path=xxx,";
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive*", "relative-to"), candidates);
            }

            {
                String cmd = op + "[{relative-to=xxx,";
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive", "path*"), candidates);
            }

            {
                String cmd = op + "[{archive=true,";
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("empty*", "hash*", "path*", "relative-to"), candidates);
            }

            {
                String cmd = op + "[{empty=true";
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList(","), candidates);
            }

            {
                String cmd = op + "[{empty=true,";
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive"), candidates);
            }

            {
                String cmd = op + "[{empty=true,archive=true";
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("}"), candidates);
            }

            {
                // If the prefix of a property name is typed,
                // the property name (if it exists, hidden or not) is proposed.
                String cmd = op + "[{empty=true,archive=true,i";
                final List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("input-stream-index"), candidates);
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

}
