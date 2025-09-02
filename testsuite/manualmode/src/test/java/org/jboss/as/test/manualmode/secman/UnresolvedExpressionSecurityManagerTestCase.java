/*
 * Copyright The WildFly Authors SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.secman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.FilePermission;
import java.nio.file.Paths;
import java.security.AllPermission;
import java.util.Collection;
import java.util.Collections;
import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.AssumeTestGroupUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.logging.LoggingUtil;
import org.jboss.as.test.shared.logging.TestLogHandlerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class UnresolvedExpressionSecurityManagerTestCase {

    @Inject
    private ServerController container;

    private static final String JBOSS_HOME = "jboss.home";
    private static final String JBOSS_SERVER_LOG_DIR = "jboss.server.log.dir";
    private static final String HANDLER_NAME =
            UnresolvedExpressionSecurityManagerTestCase.class.getSimpleName();
    private static final String LOG_FILE_NAME = HANDLER_NAME + ".log";
    private static final String CANNOT_RESOLVE_EXPRESSION_ID = "WFLYCTL0211";
    private static final String SERVER_STARTED_ID = "WFLYSRV0025";

    private ModelControllerClient client;
    private final LogHandlerSetup logHandlerSetup = new LogHandlerSetup();

    @BeforeClass
    public static void beforeClass() {
        AssumeTestGroupUtil.assumeNotBootableJar();
    }

    @Before
    public void setUp() throws Exception {
        container.start();
        client = TestSuiteEnvironment.getModelControllerClient();
        logHandlerSetup.setup(client);
        setUpLogDirPath();
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (client != null) {
                logHandlerSetup.tearDown(client);
                client.close();
            }
        } finally {
            tearLogFilePath();
            if (container.isStarted()) {
                container.stop();
            }
        }
    }

    @Test
    public void testUnresolvedExpressionInMaximumPermsFails() throws Exception {
        // with security manager enabled, boot should fail
        ServerReload.executeReloadAndWaitForCompletion(client, true);
        try {
            addSecurityManagerSubsystemWithUnresolvedExpression(client, true);
            triggerFailedReloadToNormalMode(client);
            verifyBootFailureInLogs();
        } finally {
            cleanupSecurityManagerSubsystem(client, true);
        }

    }

    @Test
    public void testUnresolvedExpressionInMinimumPermsFails() throws Exception {
        // with security manager enabled, boot should fail
        ServerReload.executeReloadAndWaitForCompletion(client, true);
        try {
            addSecurityManagerSubsystemWithUnresolvedExpression(client, false);
            triggerFailedReloadToNormalMode(client);
            verifyBootFailureInLogs();
        } finally {
            cleanupSecurityManagerSubsystem(client, false);
        }
    }

    private void addSecurityManagerSubsystemWithUnresolvedExpression(ModelControllerClient client,
            boolean isMaximumPermissions) throws Exception {
        ModelNode address = Operations.createAddress("subsystem", "security-manager",
                "deployment-permissions", "default");

        // Create maximum-permissions with an unresolved expression
        ModelNode permsParent = new ModelNode(); // maximum-permissions or minimum-permissions
        ModelNode permission = new ModelNode();
        permission.get("class").set(FilePermission.class.getName());
        // fails in normal mode
        permission.get("name").set("${undefined.security.manager.permission}");
        permission.get("actions").set("write,delete");
        permsParent.add(permission);

        String permType = isMaximumPermissions ? "maximum-permissions" : "minimum-permissions";
        ModelNode writePerms =
                Operations.createWriteAttributeOperation(address, permType, permsParent);

        ModelNode result = client.execute(writePerms);
        assertTrue("Failed to write " + permType + ": " + result,
                Operations.isSuccessfulOutcome(result));
    }

    private void triggerFailedReloadToNormalMode(ModelControllerClient client) throws Exception {
        if (AssumeTestGroupUtil.isSecurityManagerDisabled()) {
            ServerReload.executeReloadAndWaitForCompletion(client, false);
            return;
        }
        ModelNode reloadOp = Operations.createOperation("reload");
                reloadOp.get("admin-only").set(false);
        assertTrue("Failed to \"fail\" reload server",
                Operations.isSuccessfulOutcome(client.execute(reloadOp)));
        container.stop();

    }

    private void verifyBootFailureInLogs() throws Exception {
        boolean foundExpressionError =
                LoggingUtil.hasLogMessage(LOG_FILE_NAME, CANNOT_RESOLVE_EXPRESSION_ID);

        assertTrue("Expected expression resolution error (" + CANNOT_RESOLVE_EXPRESSION_ID + ") in "
                + LOG_FILE_NAME, foundExpressionError);
        int countServerStarted = LoggingUtil.countLogMessage(LOG_FILE_NAME, SERVER_STARTED_ID);
        // when security manager is disabled, boot failure is not expected
        if (AssumeTestGroupUtil.isSecurityManagerDisabled()) {
            assertEquals("Expected server start (" + SERVER_STARTED_ID + ") twice in "
                    + LOG_FILE_NAME + " due to successful reload", 2, countServerStarted);
            return;
        }
        assertEquals(
                "Expected server start (" + SERVER_STARTED_ID + ") once in " + LOG_FILE_NAME
                        + " due to failed reload when security manager is enabled",
                countServerStarted, 1);

    }

    private void cleanupSecurityManagerSubsystem(ModelControllerClient client,
            boolean isMaximumPermissions) throws Exception {
        if (container.isStarted()) {
            ServerReload.executeReloadAndWaitForCompletion(client, true);
        } else {
            container.startInAdminMode();
            client = TestSuiteEnvironment.getModelControllerClient();
        }
        ModelNode address = Operations.createAddress("subsystem", "security-manager",
                "deployment-permissions", "default");
        if (isMaximumPermissions) {
            // Reset maximum-permissions to default (AllPermission)
            ModelNode maxPerms = new ModelNode();
            ModelNode allPermission = new ModelNode();
            allPermission.get("class").set(AllPermission.class.getName());
            maxPerms.add(allPermission);

            ModelNode writeMaxPerms = Operations.createWriteAttributeOperation(address,
                    "maximum-permissions", maxPerms);
            ModelNode result = client.execute(writeMaxPerms);
            assertTrue("Failed to reset maximum-permissions in the security-manager subsystem: "
                    + result, Operations.isSuccessfulOutcome(result));
            return;
        }
        // remove minimum-permissions attribute
        ModelNode undefineMinPerms =
                Operations.createUndefineAttributeOperation(address, "minimum-permissions");
        ModelNode result = client.execute(undefineMinPerms);
        assertTrue("Failed to undefine minimum-permissions in the security-manager subsystem: "
                + result, Operations.isSuccessfulOutcome(result));

    }

    private static class LogHandlerSetup extends TestLogHandlerSetupTask {

        @Override
        public Collection<String> getCategories() {
            return Collections.singleton("org.jboss.as");
        }

        @Override
        public String getLevel() {
            return "INFO";
        }

        @Override
        public String getHandlerName() {
            return HANDLER_NAME;
        }

        @Override
        public String getLogFileName() {
            return LOG_FILE_NAME;
        }
    }

    private void setUpLogDirPath() {
        // setting "jboss.server.log.dir" property for LoggingUtil
        String jbossHome = System.getProperty(JBOSS_HOME);
        assertTrue("jboss.home system property must be set",
                jbossHome != null && !jbossHome.isEmpty());

        String logDir = Paths.get(jbossHome, "standalone", "log").toString();
        System.setProperty(JBOSS_SERVER_LOG_DIR, logDir);
    }

    private void tearLogFilePath() {
        System.clearProperty(JBOSS_SERVER_LOG_DIR);
    }
}
