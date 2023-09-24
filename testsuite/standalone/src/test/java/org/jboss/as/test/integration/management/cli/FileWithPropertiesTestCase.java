/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class FileWithPropertiesTestCase {

    private static final String SERVER_PROP_NAME = "cli-arg-test";
    private static final String CLI_PROP_NAME = "cli.prop.name";
    private static final String CLI_PROP_VALUE = "cli.prop.value";
    private static final String HOST_PROP_NAME = "cli.host.name";
    private static final String HOST_PROP_VALUE = TestSuiteEnvironment.getServerAddress();
    private static final String PORT_PROP_NAME = "cli.port.name";
    private static final String PORT_PROP_VALUE = String.valueOf(TestSuiteEnvironment.getServerPort());
    private static final String CONNECT_COMMAND = "connect ${" + HOST_PROP_NAME + "}:${" + PORT_PROP_NAME + "}";
    private static final String SET_PROP_COMMAND = "/system-property=" + SERVER_PROP_NAME + ":add(value=${cli.prop.name})";
    private static final String GET_PROP_COMMAND = "/system-property=" + SERVER_PROP_NAME + ":read-resource";
    private static final String REMOVE_PROP_COMMAND = "/system-property=" + SERVER_PROP_NAME + ":remove";

    private static final String SCRIPT_NAME = "jboss-cli-file-arg-test.cli";
    private static final String PROPS_NAME = "jboss-cli-file-arg-test.properties";
    private static final File SCRIPT_FILE;
    private static final File PROPS_FILE;
    private static final File TMP_JBOSS_CLI_FILE;
    static {
        SCRIPT_FILE = new File(new File(TestSuiteEnvironment.getTmpDir()), SCRIPT_NAME);
        PROPS_FILE = new File(new File(TestSuiteEnvironment.getTmpDir()), PROPS_NAME);
        TMP_JBOSS_CLI_FILE = new File(new File(TestSuiteEnvironment.getTmpDir()), "tmp-jboss-cli.xml");
    }

    @BeforeClass
    public static void setup() {
        ensureRemoved(TMP_JBOSS_CLI_FILE);
        jbossDist = TestSuiteEnvironment.getSystemProperty("jboss.dist");
        if(jbossDist == null) {
            fail("jboss.dist system property is not set");
        }
        final File jbossCliXml = new File(jbossDist, "bin" + File.separator + "jboss-cli.xml");
        if(!jbossCliXml.exists()) {
            fail(jbossCliXml + " doesn't exist.");
        }
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = Files.newBufferedReader(jbossCliXml.toPath(), StandardCharsets.UTF_8);
            writer = Files.newBufferedWriter(TMP_JBOSS_CLI_FILE.toPath(), StandardCharsets.UTF_8);
            String line = reader.readLine();
            boolean replaced = false;
            while(line != null) {
                if(!replaced) {
                    final int i = line.indexOf("<resolve-parameter-values>false</resolve-parameter-values>");
                    if(i >= 0) {
                        line = line.substring(0, i) + "<resolve-parameter-values>true</resolve-parameter-values>" +
                               line.substring(i + "<resolve-parameter-values>false</resolve-parameter-values>".length());
                        replaced = true;
                    }
                }
                writer.write(line);
                writer.newLine();
                line = reader.readLine();
            }
            if(!replaced) {
                fail(jbossCliXml.getAbsoluteFile() + " doesn't contain resolve-parameter-values element set to false.");
            }
        } catch(IOException e) {
            fail(e.getLocalizedMessage());
        } finally {
            if(reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {}
            }
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {}
            }
        }

        ensureRemoved(SCRIPT_FILE);
        ensureRemoved(PROPS_FILE);
        try {
            writer = Files.newBufferedWriter(SCRIPT_FILE.toPath(), StandardCharsets.UTF_8);
            writer.write(CONNECT_COMMAND);
            writer.newLine();
            writer.write(SET_PROP_COMMAND);
            writer.newLine();
            writer.write(GET_PROP_COMMAND);
            writer.newLine();
            writer.write(REMOVE_PROP_COMMAND);
            writer.newLine();
        } catch (IOException e) {
            fail("Failed to write to " + SCRIPT_FILE.getAbsolutePath() + ": " + e.getLocalizedMessage());
        } finally {
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }

        writer = null;
        try {
            writer = Files.newBufferedWriter(PROPS_FILE.toPath(), StandardCharsets.UTF_8);
            writer.write(CLI_PROP_NAME); writer.write('='); writer.write(CLI_PROP_VALUE); writer.newLine();
            writer.write(HOST_PROP_NAME); writer.write('='); writer.write(HOST_PROP_VALUE); writer.newLine();
            writer.write(PORT_PROP_NAME); writer.write('='); writer.write(PORT_PROP_VALUE); writer.newLine();
        } catch (IOException e) {
            fail("Failed to write to " + PROPS_FILE.getAbsolutePath() + ": " + e.getLocalizedMessage());
        } finally {
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @AfterClass
    public static void cleanUp() {
        ensureRemoved(SCRIPT_FILE);
        ensureRemoved(PROPS_FILE);
        ensureRemoved(TMP_JBOSS_CLI_FILE);
    }

    protected static void ensureRemoved(File f) {
        if(f.exists()) {
            if(!f.delete()) {
                fail("Failed to delete " + f.getAbsolutePath());
            }
        }
    }

    private String cliOutput;
    private static String jbossDist;

    /**
     * Ensures that system properties are resolved by the CLI.
     * @throws Exception
     */
    @Test
    public void testResolved() {
        assertEquals(0, execute(true, true));
        assertNotNull(cliOutput);
        assertEquals(CLI_PROP_VALUE, getValue("value"));
    }

    /**
     * Ensures that system properties are not resolved by the CLI.
     * @throws Exception
     */
    @Test
    public void testNotresolved() {
        assertEquals(1, execute(false, true));
        assertNotNull(cliOutput);
        assertEquals("failed", getValue("outcome"));
        assertTrue(getValue("failure-description").contains("WFLYCTL0211"));
    }

    protected String getValue(final String value) {
        final String valuePrefix = "\"" + value + "\" => \"";
        int i = cliOutput.indexOf(valuePrefix, 0);
        if(i < 0) {
            fail("The output doesn't contain '" + value + "': " + cliOutput);
        }
        int endQuote = cliOutput.indexOf('\"', valuePrefix.length() + i);
        if(endQuote < 0) {
            fail("The output doesn't contain '" + value + "': " + cliOutput);
        }

        return cliOutput.substring(i + valuePrefix.length(), endQuote);
    }

    protected int execute(boolean resolveProps, boolean logFailure) {
        final Path path;
        if (resolveProps) {
            path = TMP_JBOSS_CLI_FILE.toPath().toAbsolutePath();
        } else {
            path = Paths.get(jbossDist, "bin", "jboss-cli.xml");
        }
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(path.toString())
                .addCliArgument("--properties=" + PROPS_FILE.getAbsolutePath())
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":"
                        + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument("--connect")
                .addCliArgument("--file=" + SCRIPT_FILE.getAbsolutePath());
        try {
            cli.executeNonInteractive();
        } catch (IOException ex) {
            if (logFailure) {
                System.out.println("Exception " + ex.getLocalizedMessage());
            }
            return 1;
        }
        cliOutput = cli.getOutput();
        int exitCode = cli.getProcessExitValue();
        if (logFailure && exitCode != 0) {
            System.out.println("Command's output: '" + cli.getOutput() + "'");
        }
        return exitCode;
    }
}
