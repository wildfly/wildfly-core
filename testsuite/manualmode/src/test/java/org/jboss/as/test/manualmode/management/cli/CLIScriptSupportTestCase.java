/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import jakarta.inject.Inject;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test various connection states of the CLI class.
 *
 * @author Jean-Francois Denise (jdenise@redhat.com)
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class CLIScriptSupportTestCase {

    private static final File ROOT = new File(System.getProperty("jboss.home"));
    private static final String JBOSS_HOME = " --jboss-home="
            + ROOT.getAbsolutePath();

    @Inject
    private ServerController serverController;

    @Test
    public void testConnectStatus() {
        // Replace default adrress with contextual one for the embedded server to use
        // the right one.
        System.setProperty("jboss.bind.address.management", TestSuiteEnvironment.getServerAddress());

        // Offline instance
        CLI cli = CLI.newInstance();

        try {
            copyConfig("standalone.xml", "standalone-cli.xml", true);
        } catch (IOException ex) {
            Assert.fail("Exception copying configuration: " + ex);
        }

        // start an embedded server
        executeCommand(cli, "embed-server --std-out=echo "
                + "--server-config=standalone-cli.xml" + JBOSS_HOME);
        try {
            // Enable management
            executeCommand(cli, "reload --admin-only=false");

            // Start a clean CLI
            CLI cli2 = CLI.newInstance();

            // Make an invalid connect
            checkFail(() -> cli2.connect(TestSuiteEnvironment.getServerAddress(),
                    123,
                    null,
                    null));

            // Make a valid connect
            cli2.connect(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), null, null);
            try {
                executeCommand(cli2, "version");
            } finally {
                cli2.disconnect();
            }
        } finally {
            cli.cmd("stop-embedded-server");
        }
    }

    @Test
    public void testDisconnect() {
        CLI cli = CLI.newInstance();
        checkFail(() -> cli.disconnect());
    }

    @Test
    public void testTerminate() {
        CLI cli = CLI.newInstance();
        // Terminate offline CommandContext.
        cli.terminate();
    }

    @Test
    public void testBatch() {
        serverController.start();
        CLI cli = CLI.newInstance();
        try {
            cli.connect(serverController.getClient().getMgmtAddress(),
                    serverController.getClient().getMgmtPort(), null, null);
            addProperty(cli, "prop1", "prop1_a");
            addProperty(cli, "prop2", "prop2_a");

            cli.cmd("batch");
            writeProperty(cli, "prop1", "prop1_b");
            writeProperty(cli, "prop2", "prop2_b");
            cli.cmd("run-batch");
            assertEquals("prop1_b", readProperty(cli, "prop1"));
            assertEquals("prop2_b", readProperty(cli, "prop2"));
        } finally {
            removeProperty(cli, "prop1");
            removeProperty(cli, "prop2");
            cli.terminate();
            serverController.stop();
        }
    }

    @Test
    public void testFailedConnect() {
        CLI cli = CLI.newInstance();
        try {
            // Make an invalid connect
            checkFail(() -> cli.connect(TestSuiteEnvironment.getServerAddress(),
                    123,
                    null,
                    null));
            // re-use the same instance to start an embedded server.
            executeCommand(cli, "embed-server --std-out=echo");
            try {
                executeCommand(cli, ":read-resource()");
            } finally {
                executeCommand(cli, "stop-embedded-server");
            }
        } finally {
            cli.terminate();
        }
    }

    private static void checkFail(Runnable runner) {
        boolean failed = false;
        try {
            runner.run();
        } catch (RuntimeException ex) {
            failed = true;
        }
        if (!failed) {
            Assert.fail("Should have failed");
        }
    }

    private static void executeCommand(CLI cli, String cmd) {
        Result res = cli.cmd(cmd);
        if (!res.isSuccess()) {
            Assert.fail("Invalid response " + res.getResponse().asString());
        }
    }

    private static void addProperty(CLI cli, String name, String value) {
        cli.cmd("/system-property=" + name + ":add(value=" + value + ")");
    }

    private static String readProperty(CLI cli, String name) {
        Result res = cli.cmd("/system-property=" + name
                + ":read-attribute(name=value)");
        return res.getResponse().get("result").asString();
    }

    private static void removeProperty(CLI cli, String name) {
        cli.cmd("/system-property=" + name + ":remove");
    }

    private static void writeProperty(CLI cli, String name, String value) {
        cli.cmd("/system-property=" + name
                + ":write-attribute(name=value,value=" + value + ")");
    }

    private static void copyConfig(String base, String newName,
            boolean requiresExists) throws IOException {
        File configDir = new File(ROOT, "standalone" + File.separatorChar
                + "configuration");
        File baseFile = new File(configDir, base);
        assertTrue(!requiresExists || baseFile.exists());
        File newFile = new File(configDir, newName);
        Files.copy(baseFile.toPath(), newFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
    }

}
