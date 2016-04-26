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
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.AfterClass;
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
                assertTrue(candidates.toString(), candidates.contains("rolling-to-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failure-percentage"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains(","));
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains("=false"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains("max-failed-servers"));
                assertTrue(candidates.toString(), candidates.contains("max-failure-percentage"));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(rolling-to-servers,max-failed-servers=1,max-failure-percentage=2";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertFalse(candidates.toString(), candidates.contains(","));
            }

            {
                String cmd = ":reload-servers(blocking){rollout main-server-group(max-failed-servers=1,max-failure-percentage=2,rolling-to-servers";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                        cmd.length() - 1, candidates);
                assertTrue(candidates.toString(), candidates.contains(")"));
                assertTrue(candidates.toString(), candidates.contains("=false"));
                assertFalse(candidates.toString(), candidates.contains(","));
            }

        } finally {
            ctx.terminateSession();
        }
    }
}
