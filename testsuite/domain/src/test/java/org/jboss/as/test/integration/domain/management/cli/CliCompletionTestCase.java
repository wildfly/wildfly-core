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
package org.jboss.as.test.integration.domain.management.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test completion of properties with and without rollout support.
 *
 * @author Jean-Francois Denise (jdenise@redhat.com)
 */
public class CliCompletionTestCase {

    private static DomainTestSupport testSupport;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = CLITestSuite.createSupport(
                CliCompletionTestCase.class.getSimpleName());
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        CLITestSuite.stopSupport();
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
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":read-resource(recursive";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":read-resource(recursive,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":reload-servers(blocking";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
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
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
            }

            {
                String cmd = ":read-resource(";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertTrue(candidates.toString(), candidates.contains("recursive-depth"));
                assertTrue(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
            }

            {
                String cmd = ":read-resource(" + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("recursive"));
                assertFalse(candidates.toString(), candidates.contains("recursive-depth"));
            }

            {
                String cmd = ":read-resource(" + Util.NOT_OPERATOR + "recursive";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains(")"));
            }

            {
                String cmd = ":read-resource(" + Util.NOT_OPERATOR + "recursive," + Util.NOT_OPERATOR + "resolve-expressions,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.contains("recursive"));
            }

            {
                String cmd = ":reload-servers(" + Util.NOT_OPERATOR + "blocking";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(")"));
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
                        cmd.length() - 1, candidates);
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
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains("=false"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("max-failed-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failure-percentage"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers,max-failed-servers=1,max-failure-percentage=2";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(")"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(max-failed-servers=1,max-failure-percentage=2,rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertTrue(candidates.toString(), candidates.contains(")"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(" + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(" + Util.NOT_OPERATOR + "rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers,"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(max-failed-servers=1,max-failure-percentage=2," + Util.NOT_OPERATOR + "rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
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
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--file="));
            }

            {
                String cmd = "cd --no-validation";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--no-validation "));
            }

            {
                String cmd = "clear";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("clear"));
            }

            {
                String cmd = "clear ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "read-operation --node";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
            }

            {
                String cmd = "read-operation --node=toto";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "read-operation --headers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers="));
            }

            {
                String cmd = "ls --resolve-expressions";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--resolve-expressions "));
            }

            {
                String cmd = "ls -";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "ls --resolve-expressions";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "reload ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains(Util.NOT_OPERATOR));
            }

            {
                // The parsing will concider the ! as a value and will be not
                // able to complete a value starting with the not operator
                String cmd = "reload " + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "reload --admin-only";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--admin-only="));
            }

            {
                String cmd = "reload --admin-only=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("true"));
                assertTrue(candidates.toString(), candidates.contains("false"));
            }

            {
                String cmd = "reload --admin-only=true --server-config=jss --use-current-server-config=true --headers={";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
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
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "batch --file";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--file="));
            }

            {
                String cmd = "undeploy -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --disable";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --clear";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --enable";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "history --disable --cl";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "deploy -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "undeploy -l";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
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
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--command-name"));
                assertTrue(candidates.toString(), candidates.contains("--property-id"));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--command-name="));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("logger"));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=logger";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains(" "));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=logger ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--property-id"));
            }

            {
                String cmd = "command add --node-type=/subsystem=logging/logger --command-name=logger --property-id";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--property-id="));
            }

            ctx.handle("command add --node-type=/system-property --command-name=prop");

            {
                String cmd = "prop ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("add"));
            }

            {
                String cmd = "prop add ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "prop add --name";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("--name="));
            }

            {
                String cmd = "prop add --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("--value"));
            }

            {
                String cmd = "prop add --name=toto --value";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("--value="));
            }

            {
                String cmd = "deploy ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("--url"));
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deploy ccc ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.contains("--url"));
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deploy --name=ccc ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.contains("--url"));
                assertFalse(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "rollout-plan ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.contains("--commands"));
                assertFalse(candidates.toString(), candidates.contains("--properties"));
            }

            {
                String cmd = "rollout-plan --help ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--commands"));
                assertTrue(candidates.toString(), candidates.contains("--properties"));
            }

            {
                String cmd = "rollout-plan --name=csac ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "rollout-plan csac ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--help"));
            }

            {
                String cmd = "run-batch --verbose";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--verbose "));
            }

            {
                // The parsing will concider the ! as a value and will be not
                // able to complete a value starting with the not operator
                String cmd = "read-attribute " + Util.NOT_OPERATOR;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "read-attribute --node";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
            }

            {
                String cmd = "read-attribute --node=. ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertTrue(candidates.toString(), candidates.contains("name"));
            }

            {
                String cmd = "read-attribute --verbose";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--verbose "));
            }

            {
                String cmd = "read-attribute na";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("name"));
                assertTrue(candidates.toString(), candidates.contains("namespaces"));
            }

            {
                String cmd = "read-attribute name";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("namespaces"));
            }

            {
                String cmd = "read-attribute name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "read-operation --node";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--node="));
            }

            {
                String cmd = "read-operation --node=. ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertTrue(candidates.toString(), candidates.contains("read-resource"));
            }

            {
                String cmd = "reload --admin-only";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--admin-only="));
            }

            {
                String cmd = "reload --admin-only=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains(Util.TRUE));
                assertTrue(candidates.toString(), candidates.contains(Util.FALSE));
            }

            {
                String cmd = "set toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "unset toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
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
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deployment-overlay add ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "deployment-overlay add --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "deployment-overlay remove ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--name"));
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "deployment-overlay remove --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "deployment-overlay link --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertFalse(candidates.toString(), candidates.contains("--content"));
            }

            {
                String cmd = "deployment-overlay redeploy-affected ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                assertTrue(candidates.toString(), candidates.contains("--name"));
            }

            {
                String cmd = "deployment-overlay redeploy-affected --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 1);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
            }

            {
                String cmd = "deployment-overlay list-content --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.size() == 2);
                assertTrue(candidates.toString(), candidates.contains("--headers"));
                assertTrue(candidates.toString(), candidates.contains("-l"));
            }

            {
                String cmd = "deployment-overlay list-links --name=toto ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
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
        testHeader("ls -l --headers=", ctx);
        testHeader(":read-resource()", ctx);
    }

    private void testHeader(String radical, CommandContext ctx) throws Exception {
        {
            String cmd = radical + "{";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
        }

        {
            String cmd = radical + "{  ";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("allow-resource-service-restart", "blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
        }

        {
            String cmd = radical + "{al";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("allow-resource-service-restart"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("="), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("false", "true"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=t";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("true"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("true;"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("blocking-timeout", "rollback-on-runtime-failure", "rollout"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("rollback-on-runtime-failure"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("="), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("false", "true"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=f";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("false"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("false;"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("blocking-timeout", "rollout"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;b";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("blocking-timeout"), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("="), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList(), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout=14";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList(), candidates);
        }

        {
            String cmd = radical + "{allow-resource-service-restart=true;rollback-on-runtime-failure=false;blocking-timeout=14;";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertEquals(Arrays.asList("rollout"), candidates);
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
                    cmd.length() - 1, candidates);
            assertFalse(candidates.toString(), candidates.isEmpty());
            assertFalse(candidates.toString(), candidates.contains("--storage"));
        }

        {
            String cmd = "ls -l ";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length() - 1, candidates);
            assertFalse(candidates.toString(), candidates.isEmpty());
            assertTrue(candidates.toString(), candidates.contains("--storage"));
            assertTrue(candidates.toString(), candidates.contains("--max"));
            assertTrue(candidates.toString(), candidates.contains("--min"));
            assertTrue(candidates.toString(), candidates.contains("--description"));
            assertTrue(candidates.toString(), candidates.contains("--nillable"));
        }
    }
}
