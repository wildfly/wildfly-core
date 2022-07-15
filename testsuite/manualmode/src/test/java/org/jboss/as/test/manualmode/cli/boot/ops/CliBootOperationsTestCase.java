/*
 * JBoss, Home of Professional Open Source
 * Copyright 2020, JBoss Inc., and individual contributors as indicated
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

package org.jboss.as.test.manualmode.cli.boot.ops;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_MODE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class CliBootOperationsTestCase {
    // Taken from org.wildfly.core.testrunner.Server
    private static final String DEFAULT_JVM_ARGS =  "-Xmx512m -XX:MaxMetaspaceSize=256m";

    @Inject
    protected ServerController container;

    private String originalJvmArgs;

    protected String timestamp;

    protected File markerDirectory;

    @Before
    public void setup() throws Exception {
        originalJvmArgs = WildFlySecurityManager.getPropertyPrivileged("jvm.args", null);
        timestamp = String.valueOf(System.currentTimeMillis());
        File target = new File("target").getAbsoluteFile();
        if (!Files.exists(target.toPath())) {
            throw new IllegalStateException("No target/ directory");
        }

        File parent = new File(target, "cli-boot-ops");
        if (!Files.exists(parent.toPath())) {
            Files.createDirectories(parent.toPath());
        }

        markerDirectory = new File(parent, timestamp);
        if (Files.exists(markerDirectory.toPath())) {
            throw new IllegalStateException(markerDirectory.getAbsolutePath() + " already exists");
        }

    }

    @After
    public void tearDown() {
        if (container.isStarted()) {
            container.stop(true);
        }
        if (originalJvmArgs != null) {
            WildFlySecurityManager.setPropertyPrivileged("jvm.args", originalJvmArgs);
        } else {
            WildFlySecurityManager.clearPropertyPrivileged("jvm.args");
        }
    }

    @Test
    public void testNormalReload() throws Exception {
        new JvmArgsBuilder()
                .setCliCommands(getAddPropertyCliCommand("startAndReload"))
                .build();
        container.startInAdminMode();
        try {
            // In the standard use case we should have the marker and be reloaded to
            // normal mode
            waitForMarkerFileAndReload("success\n");
            waitForRunningMode("NORMAL");
            checkSystemProperty("startAndReload");
            checkRestartInitiated(false);

        } finally {
            container.stop();
        }
    }

    @Test
    public void testSkipReload() throws Exception {
        new JvmArgsBuilder()
                .setSkipReload()
                .setCliCommands(getAddPropertyCliCommand("startAndReload"))
                .build();

        int i = countShutdowns();
        container.startInAdminMode();
        try {
            // When skipping reload we should have the marker but not be reloaded
            waitForMarkerFile("success\n");

            // The number of shutdowns should be the same as before since we are not reloaded
            Assert.assertEquals(i, countShutdowns());
            Assert.assertEquals("ADMIN_ONLY", getRunningMode());
            checkSystemProperty("startAndReload");
            checkRestartInitiated(false);

        } finally {
            container.stop();
        }
    }

    @Test
    public void testRestartRequired() throws Exception {
        new JvmArgsBuilder()
                .setCliCommands(
                        ":server-set-restart-required\n" +
                            getAddPropertyCliCommand("restartRequired"))
                .build();

        try {
            container.startInAdminMode();
        } catch (Exception possible) {
            // The restart we asked for may kick in before the container has had a chance
            // to check if the server has started, which in turn may confuse it
        }

        try {
            // When skipping reload we should have the marker but not be reloaded
            waitForMarkerFile("success\n");

            if (container.isStarted()) {
                // The server should no longer be running, but the test framework doesn't allow us to check this.
                // Executing commands should fail since it is no longer running
                try {
                    container.getClient().executeForResult(
                            Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS));
                } catch (Exception expected) {
                    expected.printStackTrace();
                }
                container.stop();
            }

            // TODO it would be nice to be able to check the process exit code was 10 (the value the script uses to restart the server)

            checkRestartInitiated(true);

            // Start the server again (simulating what the script does). The properties will be the same.
            // This will load the server up in admin-only mode, and the cli boot ops mechanism will eventually
            // reload the server into normal mode
            container.startInAdminMode();

            waitForRunningMode("NORMAL");

            checkSystemProperty("restartRequired");
            checkRestartInitiated(false);
        } finally {
            container.stop();
        }
    }


    @Test
    public void testBadCliCommands() throws Exception {
        new JvmArgsBuilder()
                .setCliCommands("nonsense")
                .build();

        try {
            container.startInAdminMode();
        } catch (Exception expected) {
            // The server may or may not have failed. The marker file contains the truth
        }
        try {
            // When skipping reload we should have the marker but not be reloaded
            waitForMarkerFile("failed\n");

            checkRestartInitiated(false);

        } finally {
            container.stop();
        }
    }


    void checkRestartInitiated(boolean shouldExist) {
        File file = new File(markerDirectory, "wf-cli-shutdown-initiated");
        boolean exists = Files.exists(file.toPath());
        if (shouldExist) {
            Assert.assertTrue(exists);
        } else {
            Assert.assertFalse(exists);
        }
    }

    void waitForMarkerFile(String expected) throws Exception {
        File file = getMarkerFile();
        long end = System.currentTimeMillis() + TimeoutUtil.adjust(5000);
        String contents = null;
        while (System.currentTimeMillis() < end) {
            Thread.sleep(100);
            if (Files.exists(file.toPath())) {
                contents = readFileContents(file);
            }
            if (contents != null) {
                break;
            }
        }

        if (!Files.exists(file.toPath())) {
            Assert.fail("Did not see marker file before timeout");
        }
        Assert.assertEquals(expected, contents);
    }

    private String readFileContents(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                sb.append(line + "\n");
                line = reader.readLine();
            }

            if (sb.length() == 0) {
                return null;
            }
            return sb.toString();
        }
    }

    void waitForMarkerFileAndReload(String expected) throws Exception {
        container.waitForLiveServerToReload(5000);
    }

    private File getMarkerFile() {
        return new File(markerDirectory, "wf-cli-invoker-result");
    }

    private void waitForRunningMode(String runningMode) throws Exception {
        // Following a reload to normal mode, we might read the running mode too early and hit the admin-only server
        // Cycle around a bit to make sure we get the server reloaded into normal mode
        long end = System.currentTimeMillis() + TimeoutUtil.adjust(10000);
        while (true) {
            try {
                Thread.sleep(100);
                Assert.assertEquals("NORMAL", getRunningMode());
                break;
            } catch (Throwable e) {
                if (System.currentTimeMillis() >= end) {
                    throw e;
                }
            }
        }
    }

    String getRunningMode() throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.EMPTY_ADDRESS, RUNNING_MODE);
        ModelNode result = container.getClient().executeForResult(op);
        return result.asString();
    }

    void checkSystemProperty(String expectedValue) throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress("system-property", getPropertyName()), "value");
        ModelNode result = container.getClient().executeForResult(op);
        Assert.assertEquals(expectedValue, result.asString());
    }

    String getAddPropertyCliCommand(String value) {
        return "/system-property=" + getPropertyName() + ":add(value=" + value +")";
    }

    private String getPropertyName() {
        return "cli-boot-ops-test-" + timestamp;
    }

    int countShutdowns() throws Exception {

        File file = new File("target/wildfly-core/standalone/log/server.log");
        if (!Files.exists(file.toPath())) {
            return 0;
        }

        // This appears in the message, which happens when reload is initiated:
        // WFLYSRV0050: WildFly Core 11.0.0.Beta6-SNAPSHOT stopped in 8ms
        String msg = "WFLYSRV0050: ";

        int i = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.contains(msg)) {
                    i++;
                }
                line = reader.readLine();
            }
        }
        return i;
    }

    class JvmArgsBuilder {
        private StringBuilder sb = new StringBuilder(DEFAULT_JVM_ARGS);
        private String commands;
        private boolean skipReload;


        JvmArgsBuilder setCliCommands(String commands) {
            this.commands = commands;
            return this;
        }

        JvmArgsBuilder setSkipReload() throws Exception {
            skipReload = true;
            return this;
        }

        void build() throws Exception {
            Files.createDirectories(markerDirectory.toPath());
            sb.append(" -D" + AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY + "=" + markerDirectory.getAbsolutePath());

            File cliFile = new File(markerDirectory, "commands.cli");
            Files.createFile(cliFile.toPath());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(cliFile))) {
                writer.write(commands == null ? "" : commands);
                writer.write("\n\n");
            }
            sb.append(" -D" + AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY + "=" + cliFile.getAbsolutePath());

            if (skipReload) {
                sb.append(" -D" + AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY + "=true");
            }

            // Propagate this property since it has the maven repository information which is needed on CI
            if (WildFlySecurityManager.getPropertyPrivileged("cli.jvm.args", null) != null) {
                sb.append(" " + WildFlySecurityManager.getPropertyPrivileged("cli.jvm.args", null));
            }

            WildFlySecurityManager.setPropertyPrivileged("jvm.args", sb.toString());
        }
    }
}
