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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jboss.as.cli.Util;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class CliConfigTestCase {

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    @Test
    public void testEchoCommand() throws Exception {
        File f = createConfigFile(true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains("[disconnected /] version"));
    }

    @Test
    public void testNoEchoCommand() throws Exception {
        File f = createConfigFile(false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertFalse(result, result.contains("[disconnected /] version"));
    }

    @Test
    public void testWorkFlowEchoCommand() throws Exception {
        File f = createConfigFile(true);
        File script = createScript();
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--file=" + script.getAbsolutePath())
                .addCliArgument("--controller=" +
                        TestSuiteEnvironment.getServerAddress() + ":" +
                        TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains(":read-attribute(name=foo)"));
        assertTrue(result, result.contains("/system-property=catch:add(value=bar)"));
        assertTrue(result, result.contains("/system-property=finally:add(value=bar)"));
        assertTrue(result, result.contains("/system-property=finally2:add(value=bar)"));
        assertTrue(result, result.contains("if (outcome == success) of /system-property=catch:read-attribute(name=value)"));
        assertTrue(result, result.contains("set prop=Catch\\ block\\ was\\ executed"));
        assertTrue(result, result.contains("/system-property=finally:write-attribute(name=value, value=if)"));

        assertFalse(result, result.contains("/system-property=catch2:add(value=bar)"));
        assertFalse(result, result.contains("set prop=Catch\\ block\\ wasn\\'t\\ executed"));
        assertFalse(result, result.contains("/system-property=finally:write-attribute(name=value, value=else)"));

        assertTrue(result, result.contains("/system-property=catch:remove()"));
        assertTrue(result, result.contains("/system-property=finally:remove()"));
        assertTrue(result, result.contains("/system-property=finally2:remove()"));
    }

    @Test
    public void testConfigTimeoutCommand() throws Exception {
        File f = createConfigFile(false, 1);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        cli.executeInteractive();
        testTimeout(cli, 1);
    }

    @Test
    public void testOptionTimeoutCommand() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect")
                .addCliArgument("--command-timeout=77");
        cli.executeInteractive();
        testTimeout(cli, 77);
    }

    @Test
    public void testOptionFile() throws Exception {
        testFileOption("file");
        testFileOption("properties");
    }

    private void testFileOption(String optionName) throws Exception {
        File f = new File(temporaryUserHome.getRoot(), "a-script"
                + System.currentTimeMillis() + ".cli");
        f.createNewFile();
        f.deleteOnExit();
        {
            CliProcessWrapper cli = new CliProcessWrapper()
                    .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                    .addCliArgument("--" + optionName + "=" + "~" + File.separator + f.getName());
            try {
                cli.executeNonInteractive();
                assertFalse(cli.getOutput(), cli.getOutput().contains(f.getName()));
            } finally {
                cli.destroyProcess();
            }
        }

        {
            CliProcessWrapper cli = new CliProcessWrapper()
                    .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                    .addCliArgument("--" + optionName + "=" + "~"
                            + temporaryUserHome.getRoot().getName() + File.separator + f.getName());
            try {
                cli.executeNonInteractive();
                assertFalse(cli.getOutput(), cli.getOutput().contains(f.getName()));
            } finally {
                cli.destroyProcess();
            }
        }

        {
            String invalidPath = "~" + System.currentTimeMillis() + "@testOptionFile" + File.separator + f.getName();
            CliProcessWrapper cli = new CliProcessWrapper()
                    .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                    .addCliArgument("--" + optionName + "=" + invalidPath);
            try {
                cli.executeNonInteractive();
                assertTrue(cli.getOutput(), cli.getOutput().contains(f.getName()));
            } finally {
                cli.destroyProcess();
            }
        }
    }

    @Test
    public void testNegativeConfigTimeoutCommand() throws Exception {
        File f = createConfigFile(false, -1);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        String output = cli.executeNonInteractive();
        assertTrue(output, output.contains("The command-timeout must be a valid positive integer"));
    }

    @Test
    public void testNegativeOptionTimeoutCommand() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect")
                .addCliArgument("--command-timeout=-1");
        String output = cli.executeNonInteractive();
        assertTrue(output, output.contains("The command-timeout must be a valid positive integer"));
    }

    @Test
    public void testNonInteractiveCommandTimeout() throws Exception {
        File f = createConfigFile(true, 1);
        File script = createScript2();
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--file=" + script.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains("Timeout exception for run-batch"));
    }

    @Test
    public void testValidateOperation() throws Exception {
        File f = createConfigFile(false, 0, true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        cli.executeInteractive();
        cli.pushLineAndWaitForResults(":read-children-names(aaaaa,child-type=subsystem");
        String str = cli.getOutput();
        assertTrue(str, str.contains("'aaaaa' is not found among the supported properties:"));
    }

    @Test
    public void testNotValidateOperation() throws Exception {
        File f = createConfigFile(false, 0, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        cli.executeInteractive();
        cli.pushLineAndWaitForResults(":read-children-names(aaaaa,child-type=subsystem");
        String str = cli.getOutput();
        assertTrue(str, str.contains("\"outcome\" => \"success\","));
    }

    private void testTimeout(CliProcessWrapper cli, int config) throws Exception {
        cli.pushLineAndWaitForResults("command-timeout get");
        String str = cli.getOutput();
        assertEquals("Original value " + str, getValue(str), "" + config);
        cli.clearOutput();
        cli.pushLineAndWaitForResults("command-timeout set 99");
        cli.clearOutput();
        cli.pushLineAndWaitForResults("command-timeout get");
        assertEquals(getValue(cli.getOutput()), "" + 99);
        cli.clearOutput();
        cli.pushLineAndWaitForResults("command-timeout reset config");
        cli.clearOutput();
        cli.pushLineAndWaitForResults("command-timeout get");
        assertEquals(getValue(cli.getOutput()), "" + config);
    }

    private static String getValue(String line) {
        int i = line.indexOf("\n");
        if (i > 0) {
            line = line.substring(i + 1);
            i = line.indexOf("\n");
            if (i > 0) {
                // On Windows, \r\n, don't keep it in the returned value.
                line = line.substring(0, (Util.isWindows() ? i - 1 : i));
            }
        }
        return line;
    }

    private static File createScript() {
         File f = new File(TestSuiteEnvironment.getTmpDir(), "test-script" +
                System.currentTimeMillis() + ".cli");
        f.deleteOnExit();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            stream.write("try\n");
            stream.write("    :read-attribute(name=foo)\n");
            stream.write("catch\n");
            stream.write("    /system-property=catch:add(value=bar)\n");
            stream.write("finally\n");
            stream.write("    /system-property=finally:add(value=bar)\n");
            stream.write("end-try\n");

            stream.write("try\n");
            stream.write("    /system-property=catch:read-attribute(name=value)\n");
            stream.write("catch\n");
            stream.write("    /system-property=catch2:add(value=bar)\n");
            stream.write("finally\n");
            stream.write("    /system-property=finally2:add(value=bar)\n");
            stream.write(" end-try\n");

            stream.write("/system-property=*:read-resource\n");
            stream.write("if (outcome == success) of /system-property=catch:read-attribute(name=value)\n");
            stream.write("    set prop=Catch\\ block\\ was\\ executed\n");
            stream.write("    /system-property=finally:write-attribute(name=value, value=if)\n");
            stream.write("else\n");
            stream.write("    set prop=Catch\\ block\\ wasn\\'t\\ executed\n");
            stream.write("    /system-property=finally:write-attribute(name=value, value=else)\n");
            stream.write("end-if\n");
            stream.write("/system-property=catch:remove()\n");
            stream.write("/system-property=finally:remove()\n");
            stream.write("/system-property=finally2:remove()\n");
        } catch (IOException ex) {
           fail("Failure creating script file " + ex);
        }
        return f;
    }

    private static File createScript2() {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-script"
                + System.currentTimeMillis() + ".cli");
        f.deleteOnExit();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            // This one should timeout in 1 sec...
            stream.write("batch\n");
            for (int i = 0; i < 300; i++) {
                stream.write(":read-resource(recursive)\n");
            }
            stream.write("run-batch\n");
        } catch (IOException ex) {
            fail("Failure creating script file " + ex);
        }
        return f;
    }

    private static File createConfigFile(Boolean enable) {
        return createConfigFile(enable, 0);
    }

    private static File createConfigFile(Boolean enable, int timeout) {
        return createConfigFile(enable, timeout, true);
    }

    private static File createConfigFile(Boolean enable, int timeout, Boolean validate) {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-jboss-cli" +
                System.currentTimeMillis() + ".xml");
        f.deleteOnExit();
        String namespace = "urn:jboss:cli:3.1";
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        try (Writer stream = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            XMLStreamWriter writer = output.createXMLStreamWriter(stream);
            writer.writeStartDocument();
            writer.writeStartElement("jboss-cli");
            writer.writeDefaultNamespace(namespace);
            writer.writeStartElement("echo-command");
            writer.writeCharacters(enable.toString());
            writer.writeEndElement(); //echo-command
            if (timeout != 0) {
                writer.writeStartElement("command-timeout");
                writer.writeCharacters("" + timeout);
                writer.writeEndElement(); //command-timeout
            }
            writer.writeStartElement("validate-operation-requests");
            writer.writeCharacters(validate.toString());
            writer.writeEndElement(); //validate-operation-requests

            writer.writeEndElement(); //jboss-cli
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        } catch (XMLStreamException | IOException ex) {
            fail("Failure creating config file " + ex);
        }
        return f;
    }
}
