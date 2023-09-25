/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.aesh.terminal.utils.Config;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.Namespace;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class CliConfigTestCase  {

    private final String LINE_SEPARATOR = System.getProperty("line.separator");

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    @Test
    public void testEchoCommand() throws Exception {
        File f = createConfigFile(true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version")
                .addCliArgument("--no-color-output");
        try {
            final String result = cli.executeNonInteractive();
            assertNotNull(result);
            assertTrue(result, result.contains("[disconnected /] version"));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testNoEchoCommand() throws Exception {
        File f = createConfigFile(false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--command=version")
                .addCliArgument("--no-color-output");
        try {
            final String result = cli.executeNonInteractive();
            assertNotNull(result);
            assertFalse(result, result.contains("[disconnected /] version"));
        } finally {
            cli.destroyProcess();
        }
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
        try {
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
        } finally {
            cli.destroyProcess();
        }
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
        try {
            cli.executeInteractive();
            cli.clearOutput();
            testTimeout(cli, 1);
        } finally {
            cli.destroyProcess();
        }
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
        try {
            cli.executeInteractive();
            cli.clearOutput();
            testTimeout(cli, 77);
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testOptionFile() throws Exception {
        testFileOption("file", false);
        testFileOption("properties", true);
    }

    private void testFileOption(String optionName, boolean interactive) throws Exception {
        File f = new File(temporaryUserHome.getRoot(), "a-script"
                + System.currentTimeMillis() + ".cli");
        f.createNewFile();
        f.deleteOnExit();
        {
            CliProcessWrapper cli = new CliProcessWrapper()
                    .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                    .addCliArgument("--" + optionName + "=" + "~" + File.separator + f.getName());
            try {
                if (interactive) {
                    cli.executeInteractive(null);
                } else {
                    cli.executeNonInteractive();
                }
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
                if (interactive) {
                    cli.executeInteractive(null);
                } else {
                    cli.executeNonInteractive();
                }
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
        try {
            String output = cli.executeNonInteractive();
            assertTrue(output, output.contains("The command-timeout must be a valid positive integer"));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testNegativeOptionTimeoutCommand() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect")
                .addCliArgument("--command-timeout=-1");
        try {
            String output = cli.executeNonInteractive();
            assertTrue(output, output.contains("The command-timeout must be a valid positive integer"));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testNonInteractiveCommandTimeout() throws Exception {
        File f = createConfigFile(true, 1);
        File script = createScriptBatch();
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--file=" + script.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        try {
            final String result = cli.executeNonInteractive();
            assertNotNull(result);
            assertTrue(result, result.contains("Timeout exception for run-batch"));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testValidateOperation() throws Exception {
        File f = createConfigFile(false, 0, true, false, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-children-names(aaaaa,child-type=subsystem");
            String str = cli.getOutput();
            assertTrue(str, str.contains("'aaaaa' is not found among the supported properties:"));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testNoValidateOperationFlag() throws Exception {
        File f = createConfigFile(false, 0, true, false, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--no-operation-validation")
                .addCliArgument("--connect");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-children-names(aaaaa,child-type=subsystem");
            String str = cli.getOutput();
            assertTrue(str, str.contains("\"outcome\" => \"success\","));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testNotValidateOperation() throws Exception {
        File f = createConfigFile(false, 0, false, false, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-children-names(aaaaa,child-type=subsystem");
            String str = cli.getOutput();
            assertTrue(str, str.contains("\"outcome\" => \"success\","));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testOutputJSONViaConfig() throws Exception {
        File f = createConfigFile(false, 0, false, true, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--connect")
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--command=:read-resource");
        try {
            final String result = cli.executeNonInteractive();
            assertNotNull(result);
            assertTrue(result, result.contains("\"product-name\" : "));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testInteractiveOutputJSON() throws Exception {
        File f = createConfigFile(false, 0, false, false, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect")
                .addCliArgument("--output-json");
        try {
            cli.executeInteractive();

            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-resource");
            assertTrue(cli.getOutput(), cli.getOutput().contains("\"product-name\" : "));

            cli.clearOutput();
            cli.pushLineAndWaitForResults("echo-dmr :read-resource");
            assertTrue(cli.getOutput(), cli.getOutput().contains("\"operation\" : \"read-resource\""));

            cli.clearOutput();
            cli.pushLineAndWaitForResults("batch");
            cli.pushLineAndWaitForResults(":read-resource");
            cli.pushLineAndWaitForResults("run-batch --verbose");
            assertTrue(cli.getOutput(), cli.getOutput().contains("\"product-name\" : "));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testOutputDmrByDefault() throws Exception {
        File f = createConfigFile(false, 0, false, false, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect")
                .addCliArgument("--command=:read-resource");
        try {
            final String result = cli.executeNonInteractive();
            assertNotNull(result);
            assertTrue(result, result.contains("\"product-name\" => "));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testInteractiveOutputDmrByDefault() throws Exception {
        File f = createConfigFile(false, 0, false, false, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");
        try {
            cli.executeInteractive();

            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-resource");
            assertTrue(cli.getOutput(), cli.getOutput().contains("\"product-name\" => "));

            cli.clearOutput();
            cli.pushLineAndWaitForResults("echo-dmr :read-resource");
            assertTrue(cli.getOutput(), cli.getOutput().contains("\"operation\" => \"read-resource\""));

            cli.clearOutput();
            cli.pushLineAndWaitForResults("batch");
            cli.pushLineAndWaitForResults(":read-resource");
            cli.pushLineAndWaitForResults("run-batch --verbose");
            assertTrue(cli.getOutput(), cli.getOutput().contains("\"product-name\" => "));
        } finally {
            cli.destroyProcess();
        }
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

    @Test
    public void testColorOutputViaConfigFile() throws Exception {
        File f = createConfigFile(false, 0, true, false, true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                                        + TestSuiteEnvironment.getServerAddress() + ":"
                                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");

        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("read-attribute");
            String out = cli.getOutput();
            assertTrue(out.contains("Required argument --name is not specified."));
            // Error message: red color, bold and high intensity with default background
            assertTrue(out.contains("\u001B[1;91;109m") && out.contains("\u001B[0m" + LINE_SEPARATOR));

            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-resource");
            out = cli.getOutput();
            assertTrue(out.contains("\"outcome\" => \"success\""));
            // Success message: default color, normal style and intensity with default background
            assertTrue(out.contains("\u001B[;39;49m") && out.contains("\u001B[0m" + LINE_SEPARATOR));
        } finally {
            cli.destroyProcess();
            f.delete();
        }

        // Testing with success message blue and error message cyan
        f = createConfigFileWithColors(false, 0, true, false, true, "cyan", "", "blue", "", "");
        cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                                        + TestSuiteEnvironment.getServerAddress() + ":"
                                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");

        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("read-attribute");
            String out = cli.getOutput();
            assertTrue(out.contains("Required argument --name is not specified."));
            // Error message: cyan color, bold and high intensity with default background
            assertTrue(out.contains("\u001B[1;96;109m") && out.contains("\u001B[0m" + LINE_SEPARATOR));

            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-resource");
            out = cli.getOutput();
            assertTrue(out.contains("\"outcome\" => \"success\""));
            // Success message: blue color, normal style and intensity with default background
            assertTrue(out.contains("\u001B[;34;49m") && out.contains("\u001B[0m" + LINE_SEPARATOR));
        } finally {
            cli.destroyProcess();
            f.delete();
        }
    }

    @Test
    public void testColorOutputDisabledViaConfigFile() throws Exception {
        File f = createConfigFile(false, 0, true, false, false);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");

        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("read-attribute");
            String out = cli.getOutput();
            assertTrue(out.contains("Required argument --name is not specified."));
            assertFalse("Output contains ANSII color codes.", out.contains("\u001B["));
        } finally {
            cli.destroyProcess();
            f.delete();
        }
    }

    @Test
    public void testColorOutputViaCliArgument() throws Exception {
        File f = createConfigFile(false, 0, true, false, true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                                        + TestSuiteEnvironment.getServerAddress() + ":"
                                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect");

        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("read-attribute");
            String out = cli.getOutput();
            assertTrue(out.contains("Required argument --name is not specified."));
            // Error message: red color, bold and high intensity with default background
            assertTrue(out.contains("\u001B[1;91;109m") && out.contains("\u001B[0m" + LINE_SEPARATOR));

            cli.clearOutput();
            cli.pushLineAndWaitForResults(":read-resource");
            out = cli.getOutput();
            assertTrue(out.contains("\"outcome\" => \"success\""));
            // Success message: default color, normal style and intensity with default background
            assertTrue(out.contains("\u001B[;39;49m") && out.contains("\u001B[0m" + LINE_SEPARATOR));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testColorOutputDisabledViaCliArgument() throws Exception {
        File f = createConfigFile(false, 0, true, false, true);
        CliProcessWrapper cli = new CliProcessWrapper()
                .setCliConfig(f.getAbsolutePath())
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect")
                .addCliArgument("--no-color-output");

        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("read-attribute");
            String out = cli.getOutput();
            assertTrue(out.contains("Required argument --name is not specified."));
            assertFalse("Output contains ANSII color codes.", out.contains("\u001B["));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testOptionResolveParameterValues() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addJavaOption("-DfooValue=bar")
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort())
                .addCliArgument("--connect")
                .addCliArgument("--resolve-parameter-values");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            cli.pushLineAndWaitForResults("echo-dmr /system-property=foo:add(value=${fooValue})");
            String out = cli.getOutput();
            String dmr = out.substring(out.indexOf(Config.getLineSeparator()), out.lastIndexOf("}") + 1);
            ModelNode mn = ModelNode.fromString(dmr);
            assertEquals("bar", mn.get(Util.VALUE).asString());
        } finally {
            cli.destroyProcess();
        }
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

    private static File createScriptBatch() {
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
        return createConfigFile(enable, 0, false, false, false);
    }

    private static File createConfigFile(Boolean enable, Boolean colorOutput) {
        return createConfigFile(enable, 0, false, false, true);
    }

    private static File createConfigFile(Boolean enable, int timeout) {
        return createConfigFile(enable, timeout, true, false, false);
    }

    private static File createConfigFile(Boolean enable, int timeout,
            Boolean validate, Boolean outputJSON, Boolean colorOutput) {
        return CliConfigUtils.createConfigFile(enable, timeout, validate, outputJSON, colorOutput, true);
    }

    private static File createConfigFileWithColors(Boolean enable, int timeout,
            Boolean validate, Boolean outputJSON, Boolean colorOutput, String error,
            String warn, String success, String required, String batch) {
        File f = new File(TestSuiteEnvironment.getTmpDir(), "test-jboss-cli" +
                System.currentTimeMillis() + ".xml");
        f.deleteOnExit();
        String namespace = Namespace.CURRENT.getUriString();
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

            writer.writeStartElement("output-json");
            writer.writeCharacters(outputJSON.toString());
            writer.writeEndElement(); //output-json

            CliConfigUtils.writeColorConfig(writer, colorOutput, error, warn, success, required, batch);

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
