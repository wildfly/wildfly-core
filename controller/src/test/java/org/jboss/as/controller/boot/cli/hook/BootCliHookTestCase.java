/*
 * JBoss, Home of Professional Open Source
 * Copyright 2019, JBoss Inc., and individual contributors as indicated
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

package org.jboss.as.controller.boot.cli.hook;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Executors;

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class BootCliHookTestCase extends AbstractControllerTestBase {

    private File directory;
    private File cliFile;
    private File doneMarkerFile;
    private File restartInitiated;
    private File embeddedServerNeedsRestart;

    private RunningModeControl runningModeControl;

    public BootCliHookTestCase() {
        super(ProcessType.STANDALONE_SERVER);
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        // register the global operations to be able to call :read-attribute and :write-attribute
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, processType);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);

        rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);
        rootRegistration.registerOperationHandler(BootCliReloadHandler.DEFINITION, BootCliReloadHandler.INSTANCE);
        rootRegistration.registerOperationHandler(BootCliShutdownHandler.DEFINITION, BootCliShutdownHandler.INSTANCE);
        rootRegistration.registerOperationHandler(ForceRestartRequiredHandler.DEFINITION, ForceRestartRequiredHandler.INSTANCE);
    }

    @Before
    public void setupController() throws InterruptedException {
        //Override the method that starts the controller so that we can
        //set things before booting it up
    }

    @Before
    public void setupDirectoryAndFiles() throws Exception {
        File file = new File("target/boot-hook-files").getAbsoluteFile();
        Path path = file.toPath();
        if (Files.exists(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.createDirectories(path);

        directory = file;
        cliFile = new File(directory, "cli-script-file");
        doneMarkerFile = new File(directory, "wf-cli-invoker-result");
        restartInitiated = new File(directory, "wf-cli-shutdown-initiated");
        embeddedServerNeedsRestart = new File(directory, "wf-restart-embedded-server");

        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY, directory.getAbsolutePath());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.test.override.cli.boot.invoker", TestAdditionalBootCliScriptInvoker.class.getName());
        WildFlySecurityManager.clearPropertyPrivileged(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY);
        WildFlySecurityManager.clearPropertyPrivileged(AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY);

        runningModeControl = new RunningModeControl(RunningMode.ADMIN_ONLY);

        TestAdditionalBootCliScriptInvoker.commands = null;
        TestAdditionalBootCliScriptInvoker.shouldError = false;
        TestAdditionalBootCliScriptInvoker.restartRequired = false;

        BootCliReloadHandler.INSTANCE.parameters = null;
        BootCliShutdownHandler.INSTANCE.parameters = null;
    }

    @Test(expected = IllegalStateException.class)
    public void testNonExistentOperationFileFails() throws Exception {
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY, new File(directory, "non-existent").getAbsolutePath());
        startController();
    }

    @Test(expected = IllegalStateException.class)
    public void testNotAdminOnlyFails() throws Exception {
        runningModeControl = new RunningModeControl(RunningMode.NORMAL);
        createCliScript("One\nTwo");
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY, cliFile.getAbsolutePath());
        startController();
    }

    @Test(expected = IllegalStateException.class)
    public void testNoMarkerDirWhenSkippingReload() throws Exception {
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY, "true");
        WildFlySecurityManager.clearPropertyPrivileged(AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY);
        createCliScript("One\nTwo");
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY, cliFile.getAbsolutePath());
        startController();
    }

    @Test(expected = IllegalStateException.class)
    public void testNotStandaloneOrEmbedded() throws Exception {
        super.processType = ProcessType.DOMAIN_SERVER;
        createCliScript("One\nTwo");
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY, cliFile.getAbsolutePath());
        startController();
    }

    @Test(expected = IllegalStateException.class)
    public void testNonExistentMarkerDirectory() throws Exception {
        File file = new File(directory, "non-existent");
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY, file.getAbsolutePath());
        startController();
    }

    @Test
    public void testErrorInCliCommandsStandalone() throws Exception {
        testErrorInCliCommands();
    }

    @Test
    public void testErrorInCliCommandsEmbedded() throws Exception {
        processType = ProcessType.EMBEDDED_SERVER;
        testErrorInCliCommands();
    }

    public void testErrorInCliCommands() throws Exception {
        TestAdditionalBootCliScriptInvoker.shouldError = true;
        createCliScript("One\nTwo\n");
        try {
            startController();
            Assert.fail("Should have had an error starting server");
        } catch (IllegalStateException expected) {
        }
        Assert.assertEquals("One\nTwo\n", TestAdditionalBootCliScriptInvoker.commands);

        String marker = readFile(doneMarkerFile);
        Assert.assertEquals("failed\n", marker);
    }

    @Test
    public void testReloadStandalone() throws Exception {
        testReloadScenario();
    }

    @Test
    public void testReloadEmbedded() throws Exception {
        processType = ProcessType.EMBEDDED_SERVER;
        testReloadScenario();
    }

    private void testReloadScenario() throws Exception {
        createCliScript("One\nTwo\n");
        startController();
        Assert.assertEquals("One\nTwo\n", TestAdditionalBootCliScriptInvoker.commands);
        Assert.assertNotNull(BootCliReloadHandler.INSTANCE.parameters);
        Assert.assertEquals(0, BootCliReloadHandler.INSTANCE.parameters.size());

        // After reload all our system properties should be unset
        checkNullProperty(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY);

        String marker = readFile(doneMarkerFile);
        Assert.assertEquals("success\n", marker);

        // :shutdown should not have been called and there should be no marker files
        // indicating that restart is happening
        Assert.assertNull(BootCliShutdownHandler.INSTANCE.parameters);
        Assert.assertFalse(Files.exists(this.embeddedServerNeedsRestart.toPath()));
        Assert.assertFalse(Files.exists(this.restartInitiated.toPath()));
    }

    @Test
    public void testShutdownToRestartScenarioStandalone() throws Exception {
        testShutdownToRestartScenario(false);
    }

    @Test
    public void testShutdownToRestartScenarioEmbedded() throws Exception {
        processType = ProcessType.EMBEDDED_SERVER;
        testShutdownToRestartScenario(true);
    }

    private void testShutdownToRestartScenario(boolean embedded) throws Exception {
        TestAdditionalBootCliScriptInvoker.restartRequired = true;
        createCliScript("One\nTwo\n");
        startController();
        Assert.assertEquals("One\nTwo\n", TestAdditionalBootCliScriptInvoker.commands);
        if (embedded) {
            // For an embedded server we don't actually call shutdown
            Assert.assertNull(BootCliShutdownHandler.INSTANCE.parameters);
        } else {
            // For a standalone server we execute :shutdown(restart=true)
            Assert.assertNotNull(BootCliShutdownHandler.INSTANCE.parameters);
            Assert.assertEquals(1, BootCliShutdownHandler.INSTANCE.parameters.size());
            Assert.assertEquals(true, BootCliShutdownHandler.INSTANCE.parameters.get(RESTART).asBoolean());
        }

        // After we're done all our system properties should be unset. (However for a
        // restart of a real server the script will set all these again)
        checkNullProperty(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY);

        String marker = readFile(doneMarkerFile);
        Assert.assertEquals("success\n", marker);

        // :reload should not have happened
        Assert.assertNull(BootCliReloadHandler.INSTANCE.parameters);

        // We should have a marker indicating that restart is in progress.
        // There is a different file for the embedded/standalone cases
        Assert.assertEquals(embedded, Files.exists(this.embeddedServerNeedsRestart.toPath()));
        Assert.assertEquals(!embedded, Files.exists(this.restartInitiated.toPath()));
    }


    @Test
    public void testKeepAliveScenarioStandalone() throws Exception {
        testKeepAliveScenario();
    }

    @Test
    public void testKeepAliveScenarioEmbedded() throws Exception {
        processType = ProcessType.EMBEDDED_SERVER;
        testKeepAliveScenario();
    }

    public void testKeepAliveScenario() throws Exception {
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY, "true");

        createCliScript("One\nTwo\n");
        startController();
        Assert.assertEquals("One\nTwo\n", TestAdditionalBootCliScriptInvoker.commands);

        // All our system properties should be unset
        checkNullProperty(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY);

        String marker = readFile(doneMarkerFile);
        Assert.assertEquals("success\n", marker);

        // :shutdown and :restart should not have been called and there should be no marker files
        // indicating that restart is happening
        Assert.assertNull(BootCliReloadHandler.INSTANCE.parameters);
        Assert.assertNull(BootCliShutdownHandler.INSTANCE.parameters);
        Assert.assertFalse(Files.exists(this.embeddedServerNeedsRestart.toPath()));
        Assert.assertFalse(Files.exists(this.restartInitiated.toPath()));
    }

    @Test
    public void testRestartingScenarioStandalone() throws Exception {
        // For the embedded case, shutdown will just be a noop, and the 'wf-restart-embedded-server'
        // will be written. However, the 'wf-restart-embedded-server' is not used by this mechanism,
        // it is a marker for the calling cloud scripts to handle.
        //
        // So this only happens in a standalone server, where the restart is
        // retriggered by the script upon :shutdown(restart=true)
        // The restart will pass in the same system properties as the original process
        // and it will still start in admin mode. The presence of the marker file
        // tells it to not apply the CLI commands and to reload

        // All relevant properties will be the same as before
        createCliScript("One\nTwo\n");
        // Create the marker
        Files.createFile(restartInitiated.toPath());

        startController();

        // We did not run the CLI commands again
        Assert.assertNull(TestAdditionalBootCliScriptInvoker.commands);

        // We did a reload
        Assert.assertNotNull(BootCliReloadHandler.INSTANCE.parameters);
        Assert.assertEquals(0, BootCliReloadHandler.INSTANCE.parameters.size());

        // After reload all our system properties should be unset
        checkNullProperty(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY);
        checkNullProperty(AdditionalBootCliScriptInvoker.SKIP_RELOAD_PROPERTY);

        // :shutdown should not have been called and there should be no marker files
        // indicating that restart is happening
        Assert.assertNull(BootCliShutdownHandler.INSTANCE.parameters);
        Assert.assertFalse(Files.exists(embeddedServerNeedsRestart.toPath()));
        Assert.assertFalse(Files.exists(restartInitiated.toPath()));
        // Also there should be no marker file about applying the CLI commands
        Assert.assertFalse(Files.exists(doneMarkerFile.toPath()));
    }


    private void checkNullProperty(String propertyName) {
        Assert.assertNull(WildFlySecurityManager.getPropertyPrivileged(propertyName, null));
    }

    private void startController() throws InterruptedException {
        super.setupController();
        if (container.isShutdown()) {
            throw new IllegalStateException();
        }
    }

    private void createCliScript(String content) throws IOException {
        writeToFile(cliFile, content);
        WildFlySecurityManager.setPropertyPrivileged(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY, cliFile.getAbsolutePath());
    }

    static void writeToFile(File file, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }

    static String readFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                sb.append(line + "\n");
                line = reader.readLine();
            }
            return sb.toString();
        }
    }

    protected AbstractControllerTestBase.ModelControllerService createModelControllerService(ProcessType processType) {
        return new BootCliHookModelControllerService(processType, runningModeControl);
    }

    public class BootCliHookModelControllerService extends AbstractControllerTestBase.ModelControllerService {

        public BootCliHookModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl) {
            super(processType, runningModeControl, () -> Executors.newSingleThreadExecutor());
        }

        @Override
        protected void postBoot() {
            try {
                executeAdditionalCliBootScript();
            } finally {
            }
        }
    }

}
