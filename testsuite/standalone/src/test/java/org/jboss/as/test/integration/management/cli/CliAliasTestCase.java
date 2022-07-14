/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.integration.management.cli;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the 'alias' and 'unalias' command in interactive mode of jboss-cli
 *
 * @author Martin Schvarcbacher mschvarc@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class CliAliasTestCase {

    private static final String VALID_ALIAS_NAME = "DEBUG123_ALIAS";
    private static final String VALID_ALIAS_COMMAND = "'/subsystem=io:read-resource'";

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    @Before
    public final void setupUserHome() {
        System.setProperty("user.home", temporaryUserHome.getRoot().toPath().toString());
    }

    /**
     * Tests the alias command for the following naming pattern: [a-zA-Z0-9_]+
     *
     * @throws Exception
     */
    @Test
    public void testValidAliasCommandInteractive() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            assertFalse(cli.getOutput().contains(VALID_ALIAS_NAME));
            assertFalse(cli.getOutput().contains(VALID_ALIAS_COMMAND));
            cli.pushLineAndWaitForResults("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
            cli.clearOutput();

            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assertTrue(allAliases.contains(VALID_ALIAS_NAME));
            assertTrue(allAliases.contains(VALID_ALIAS_COMMAND));

            cli.pushLineAndWaitForResults("unalias " + VALID_ALIAS_NAME);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliasesCleared = cli.getOutput().replaceAll("\r", "");
            assertFalse(allAliasesCleared.contains(VALID_ALIAS_NAME));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests alias command containing invalid symbols in the name
     * NOTE: if this test fails in the future, see:
     * <a href="https://issues.jboss.org/browse/JBEAP-5009">JBEAP-5009</a>
     * <a href="https://issues.jboss.org/browse/JBEAP-4938">JBEAP-4938</a>
     *
     * @throws Exception
     */
    @Test
    public void testInvalidAliasCommandInteractive() throws Exception {
        final String INVALID_ALIAS_NAME = "TMP-DEBUG123-INVALID456-ALIAS789"; //does not match [a-zA-Z0-9_]+ regex
        final String INVALID_ALIAS_COMMAND = "'/subsystem=notfound:invalid-command'";
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_NAME));
            assertFalse(cli.getOutput().contains(INVALID_ALIAS_COMMAND));
            cli.pushLineAndWaitForResults("alias " + INVALID_ALIAS_NAME + "=" + INVALID_ALIAS_COMMAND);
            cli.clearOutput();
            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            //see: https://issues.jboss.org/browse/JBEAP-5009
            assertFalse(allAliases.contains(INVALID_ALIAS_NAME));
            assertFalse(allAliases.contains(INVALID_ALIAS_COMMAND));
            cli.ctrlCAndWaitForClose();
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Tests if existing aliases are read from .aesh_aliases at the start of interactive session
     *
     * @throws Exception
     */
    @Test
    public void testManuallyAddedAlias() throws Exception {
        final File aliasFile = temporaryUserHome.newFile(".aesh_aliases");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(aliasFile), "UTF-8"))) {
            writer.write("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
            writer.newLine();
        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        }
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias");
            String allAliases = cli.getOutput().replaceAll("\r", "");
            assertTrue(allAliases.contains("alias "));
            assertTrue(allAliases.contains(VALID_ALIAS_NAME));
            assertTrue(allAliases.contains(VALID_ALIAS_COMMAND));
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
    }

    /**
     * Create an alias in interactive mode and verify it was persisted in .aesh_aliases file at the end of the session
     * <a href="https://issues.jboss.org/browse/WFCORE-1294">WFCORE-1294</a>
     *
     * @throws Exception
     */
    @Test
    public void testAliasPersistence() throws Exception {
        final File aliasFile = temporaryUserHome.newFile(".aesh_aliases");
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
            cli.pushLineAndWaitForClose("quit");
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
        assertAliasSaved(aliasFile);
    }

    /**
     * Regression test for <a href="https://issues.jboss.org/browse/WFCORE-1853">WFCORE-1853</a>:
     * Using Ctrl+C can result in aesh aliases not being saved on Windows
     *
     * @throws Exception
     */
    @Test
    public void testAliasPersistenceCtrlC() throws Exception {
        final File aliasFile = temporaryUserHome.newFile(".aesh_aliases");
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString());
        try {
            cli.executeInteractive();
            cli.pushLineAndWaitForResults("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND);
            cli.ctrlCAndWaitForClose(); //see: WFCORE-1853
        } catch (Exception ex) {
            fail(ex.getLocalizedMessage());
        } finally {
            cli.destroyProcess();
        }
        assertAliasSaved(aliasFile);
    }

    private void assertAliasSaved(File aliasFile) throws IOException {
        assertTrue("No .aesh_aliases file found", aliasFile.exists());
        List<String> aliasesInFile = Files.readAllLines(aliasFile.toPath(), Charset.defaultCharset());
        boolean found = false;
        for (String line : aliasesInFile) {
            if (line.contains("alias " + VALID_ALIAS_NAME + "=" + VALID_ALIAS_COMMAND)) {
                found = true;
                break;
            }
        }
        assertTrue("Alias was not saved to .aesh_aliases", found);
    }
}
