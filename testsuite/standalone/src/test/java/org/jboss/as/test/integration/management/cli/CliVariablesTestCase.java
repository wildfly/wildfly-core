/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import org.apache.commons.lang3.StringUtils;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.parsing.UnresolvedVariableException;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.wildfly.core.testrunner.WildflyTestRunner;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;


/**
 * Tests for 'set' and 'unset' commands
 *
 * @author Martin Schvarcbacher
 */
@RunWith(WildflyTestRunner.class)
public class CliVariablesTestCase {

    private static final String JBOSS_CLI_RC_PROP = "jboss.cli.rc";
    private static final String VAR_NAME_RC = "test_var_rc_name";
    private static final String VAR_VALUE_RC = "test_var_rc_value";

    private static final String VAR_NAME_SCRIPT = "test_var_script_name";
    private static final String VAR_VALUE_SCRIPT = "test_var_script_value";

    private static final File TMP_JBOSS_CLI_RC;
    private static final File TMP_JBOSS_CLI_SCRIPTSET_ONLY;
    private static final File TMP_JBOSS_CLI_SCRIPTSET_UNSET;

    static {
        TMP_JBOSS_CLI_RC = new File(new File(TestSuiteEnvironment.getTmpDir()), ".tmp-jbossclirc");
        TMP_JBOSS_CLI_SCRIPTSET_ONLY = new File(new File(TestSuiteEnvironment.getTmpDir()), ".tmp-jbosscli-setonly");
        TMP_JBOSS_CLI_SCRIPTSET_UNSET = new File(new File(TestSuiteEnvironment.getTmpDir()), ".tmp-jbosscli-setunset");
    }

    @BeforeClass
    public static void setup() {
        ensureRemoved(TMP_JBOSS_CLI_RC);
        ensureRemoved(TMP_JBOSS_CLI_SCRIPTSET_ONLY);
        ensureRemoved(TMP_JBOSS_CLI_RC);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TMP_JBOSS_CLI_RC))) {
            writer.write("set " + VAR_NAME_RC + "=" + VAR_VALUE_RC);
            writer.newLine();
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TMP_JBOSS_CLI_SCRIPTSET_ONLY))) {
            writer.write("set " + VAR_NAME_SCRIPT + "=" + VAR_VALUE_SCRIPT);
            writer.newLine();
            writer.write("set");
            writer.newLine();
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TMP_JBOSS_CLI_SCRIPTSET_UNSET))) {
            writer.write("set " + VAR_NAME_SCRIPT + "=" + VAR_VALUE_SCRIPT);
            writer.newLine();
            writer.write("unset " + VAR_NAME_SCRIPT);
            writer.newLine();
            writer.write("$" + VAR_NAME_SCRIPT);
            writer.newLine();
            writer.write("set");
            writer.newLine();
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    @AfterClass
    public static void cleanUp() {
        ensureRemoved(TMP_JBOSS_CLI_RC);
        ensureRemoved(TMP_JBOSS_CLI_SCRIPTSET_ONLY);
        ensureRemoved(TMP_JBOSS_CLI_SCRIPTSET_UNSET);
    }

    /**
     * Tests setting variables from --file and RC simultaneously
     * @throws Exception
     */
    @Test
    public void testPropertiesDifferentSources() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--file=" + TMP_JBOSS_CLI_SCRIPTSET_ONLY.getAbsolutePath())
                .addJavaOption("-D" + JBOSS_CLI_RC_PROP + "=" + TMP_JBOSS_CLI_RC.getAbsolutePath());

        final String result = cli.executeNonInteractive();
        assertTrue(cli.getProcessExitValue() == 0);
        assertNotNull(result);
        assertTrue(result.contains(VAR_NAME_SCRIPT + "=" + VAR_VALUE_SCRIPT));
        assertTrue(result.contains(VAR_NAME_RC + "=" + VAR_VALUE_RC));
    }

    /**
     * Tests set and unset when ran from --file
     * @throws Exception
     */
    @Test
    public void testSetUnsetVariableInFile() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--file=" + TMP_JBOSS_CLI_SCRIPTSET_UNSET.getAbsolutePath());
        final String result = cli.executeNonInteractive();

        assertTrue(cli.getProcessExitValue() != 0); //fails on non-existing variable
        assertNotNull(result);
        assertTrue(result.contains("Unrecognized variable " + VAR_NAME_SCRIPT));
    }

    /**
     * Tests if 'set' with no parameters prints all currently set variables
     * @throws Exception
     */
    @Test
    public void testSetPrintsAllVariables() throws Exception {
        final String VAR1_NAME = "variable_1";
        final String VAR2_NAME = "variable_2";

        final String VAR1_VALUE = "value_1";
        final String VAR2_VALUE = "value_2";

        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);

        ctx.handle("set " + VAR1_NAME + "=" + VAR1_VALUE);
        ctx.handle("set " + VAR2_NAME + "=" + VAR2_VALUE);
        cliOut.reset();

        ctx.handle("echo $" + VAR1_NAME);
        ctx.handle("echo $" + VAR2_NAME);
        String echoResult = cliOut.toString();
        assertTrue(echoResult.contains(VAR1_VALUE));
        assertTrue(echoResult.contains(VAR2_VALUE));

        cliOut.reset();
        ctx.handle("set"); //print all variables
        String setResult = cliOut.toString();
        assertTrue(setResult.contains(VAR1_NAME + "=" + VAR1_VALUE));
        assertTrue(setResult.contains(VAR2_NAME + "=" + VAR2_VALUE));
        assertTrue(ctx.getExitCode() == 0);
    }

    /**
     * Tests variable chaining: set var2=$var1
     * @throws Exception
     */
    @Test
    public void testVariableChaining() throws Exception {
        final String VAR1_NAME = "variable_1";
        final String VAR2_NAME = "variable_2";

        final String VAR1_VALUE = "value_1";
        final String VAR2_VALUE = "$" + VAR1_NAME;

        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);

        ctx.handle("set " + VAR1_NAME + "=" + VAR1_VALUE);
        ctx.handle("set " + VAR2_NAME + "=" + VAR2_VALUE);
        cliOut.reset();

        ctx.handle("echo $" + VAR1_NAME);
        ctx.handle("echo $" + VAR2_NAME);
        String echoResult = cliOut.toString();
        assertTrue(echoResult.contains(VAR1_VALUE));
        assertFalse(echoResult.contains(VAR2_VALUE));
        assertTrue(StringUtils.countMatches(echoResult, VAR1_VALUE) == 2);

        cliOut.reset();
        ctx.handle("set"); //print all variables
        String setResult = cliOut.toString();
        assertTrue(setResult.contains(VAR1_NAME + "=" + VAR1_VALUE));
        assertTrue(setResult.contains(VAR2_NAME + "=" + VAR1_VALUE));
        assertTrue(ctx.getExitCode() == 0);
    }

    /**
     * Tests the 'unset' command
     * @throws Exception
     */
    @Test
    public void testSetAndUnsetVariables() throws Exception {
        final String VAR1_NAME = "variable_1";
        final String VAR1_VALUE = "value_1";

        final ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);

        ctx.handle("set " + VAR1_NAME + "=" + VAR1_VALUE);
        cliOut.reset();
        ctx.handle("echo $" + VAR1_NAME);
        assertTrue(cliOut.toString().contains(VAR1_VALUE));
        ctx.handle("unset " + VAR1_NAME);
        try {
            ctx.handle("$" + VAR1_NAME);
            fail(VAR1_NAME + " should be unset");
        } catch (UnresolvedVariableException ex) {
            //expected
        }
        cliOut.reset();
        ctx.handle("set");
        assertFalse(cliOut.toString().contains(VAR1_NAME));
        assertTrue(ctx.getExitCode() == 0);
    }

    /**
     * Tests that variable is properly displayed although line contains leading
     * whitespaces.
     *
     * @throws Exception
     */
    @Test
    public void testLeadingWhitespaces() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper();
        cli.executeInteractive();
        try {
            String expected = "XXXXXX";
            cli.clearOutput();
            cli.pushLineAndWaitForResults("          set var=" + expected);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("          echo $var");
            String[] lines = cli.getOutput().split(System.getProperty("line.separator"));
            // lines[0] == echo command
            // lines[1] == echoed content
            // lines[3] = prompt.
            Assert.assertEquals(expected, lines[1]);
        } finally {
            cli.destroyProcess();
        }
    }
    private static void ensureRemoved(File f) {
        if (f.exists()) {
            if (!f.delete()) {
                fail("Failed to delete " + f.getAbsolutePath());
            }
        }
    }
}

