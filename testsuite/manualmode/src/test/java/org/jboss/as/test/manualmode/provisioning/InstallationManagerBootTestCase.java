/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.provisioning;

import static org.jboss.as.test.shared.logging.LoggingUtil.hasLogMessage;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import jakarta.inject.Inject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.as.test.shared.AssumeTestGroupUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.logging.TestLogHandlerSetupTask;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.installationmanager.ManifestVersion;
import org.wildfly.test.installationmanager.TestInstallationManager;
import org.wildfly.test.installationmanager.TestInstallationManagerFactory;

/**
 * Tests Installation Manager integration behavior during the boot of a standalone mode environment.
 * It uses a mocked implementation of the installation manager, which provides dummy data for the test.
 * This test is run in manual mode as it requires the presence of an additional module at the time
 * that JBoss Modules first links the server kernel modules.
 * <p>
 * See InstallationManagerIntegrationTestCase for tests of the high-level Installation Manager commands
 * in a domain mode environment
 * <p>
 * See InstMgrResourceTestCase for low-level management operation unit testing.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class InstallationManagerBootTestCase {
    private static final String MODULE_NAME = "org.jboss.prospero";
    private static TestModule testModule;

    @Inject
    protected static ServerController controller;

    @BeforeClass
    public static void createTestModule() throws IOException {
        TestModule tm = new TestModule(MODULE_NAME, "org.wildfly.installation-manager.api");
        tm.addResource("test-mock-installation-manager.jar").addClass(TestInstallationManager.class).addClass(TestInstallationManagerFactory.class)
                .addAsManifestResource(new StringAsset("org.wildfly.test.installationmanager.TestInstallationManagerFactory"),
                        "services/org.wildfly.installationmanager.spi.InstallationManagerFactory");
        tm.create(true);
        testModule = tm;
        controller.start();
    }

    @AfterClass
    public static void removeTestModule() {
        if (testModule != null) {
            controller.stop();
            testModule.remove();
        }
    }

    @Test
    public void testChannelManifestLogging() throws Exception {

        TestInstallationManager.initialized = false;

        try (ModelControllerClient modelControllerClient = TestSuiteEnvironment.getModelControllerClient()) {

            // Validate the manifest information in server boot logging.
            LogHandlerSetup logHandlerSetup = new LogHandlerSetup("org.wildfly.core.installationmanager");
            logHandlerSetup.setup(modelControllerClient);
            try {
                // Reload to capture the log message
                ServerReload.executeReloadAndWaitForCompletion(modelControllerClient);

                assertLogText(modelControllerClient, "WFLYIM0023");

                TestInstallationManager.initialize();

                // The *exact* format of what is logged is unspecified (i.e. the use of ManifestVersion.toString()),
                // but we do require that we see id, description and version data for each channel.
                for (ManifestVersion mfv : TestInstallationManager.installedVersions) {
                    assertLogText(modelControllerClient, mfv.getChannelId());
                    assertLogText(modelControllerClient, mfv.getDescription());
                    assertLogText(modelControllerClient, mfv.getVersion());
                }
            } finally {
                logHandlerSetup.tearDown(modelControllerClient);
            }
        }
    }

    private static void assertLogText(ModelControllerClient modelControllerClient, String text) throws Exception {
        assertNotNull(text);
        assertTrue(text + " not found", hasLogMessage(modelControllerClient, "startup", text));
    }

    private static class LogHandlerSetup extends TestLogHandlerSetupTask {

        private final Set<String> categories;

        private LogHandlerSetup(String category) {
            this.categories = Set.of(category);
        }

        @Override
        public Collection<String> getCategories() {
            return categories;
        }

        @Override
        public String getLevel() {
            return "INFO";
        }

        @Override
        public String getHandlerName() {
            return "startup";
        }

        @Override
        public String getLogFileName() {
            return "startup.log";
        }
    }
}
