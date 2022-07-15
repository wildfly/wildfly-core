/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
 */

package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.Util;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Alexey Loubyansky
 *
 */
// Required by Bootable JAR in order to create the installation directory
@RunWith(WildFlyRunner.class)
public class RcFileTestCase {

    private static final String JBOSS_CLI_RC_PROP = "jboss.cli.rc";
    private static final String VAR_NAME = "test_var_name";
    private static final String VAR_VALUE = "test_var_value";

    private static final File TMP_JBOSS_CLI_RC;
    static {
        TMP_JBOSS_CLI_RC = new File(new File(TestSuiteEnvironment.getTmpDir()), ".tmp-jbossclirc");
    }

    @BeforeClass
    public static void setup() {
        ensureRemoved(TMP_JBOSS_CLI_RC);
        BufferedWriter writer = null;
        try {
            writer = Files.newBufferedWriter(TMP_JBOSS_CLI_RC.toPath(), StandardCharsets.UTF_8);
            writer.write("set " + VAR_NAME + "=" + VAR_VALUE);
            writer.newLine();
        } catch(IOException e) {
            fail(e.getLocalizedMessage());
        } finally {
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @AfterClass
    public static void cleanUp() {
        ensureRemoved(TMP_JBOSS_CLI_RC);
    }

    @Before
    public void beforeTest() {
        TestSuiteEnvironment.setSystemProperty(JBOSS_CLI_RC_PROP, TMP_JBOSS_CLI_RC.getAbsolutePath());
    }

    @After
    public void afterTest() {
        TestSuiteEnvironment.clearSystemProperty(JBOSS_CLI_RC_PROP);
    }

    @Test
    public void testAPI() throws Exception {
        CommandContext ctx = null;
        try {
            ctx = CommandContextFactory.getInstance().newCommandContext();
            assertEquals(VAR_VALUE, ctx.getVariable(VAR_NAME));
        } finally {
            if(ctx != null) {
                ctx.terminateSession();
            }
        }
    }

    @Test
    public void testScript() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("echo $" + VAR_NAME)
                .addJavaOption( "-D" + JBOSS_CLI_RC_PROP + "=" + TMP_JBOSS_CLI_RC.getAbsolutePath());
        cli.executeNonInteractive();

        assertEquals("CLI Errors: '" + cli.getOutput() + "'", 0, cli.getProcessExitValue());
        assertTrue(cli.getOutput().endsWith(VAR_VALUE + Util.LINE_SEPARATOR));
    }

    protected static void ensureRemoved(File f) {
        if(f.exists()) {
            if(!f.delete()) {
                fail("Failed to delete " + f.getAbsolutePath());
            }
        }
    }
}
