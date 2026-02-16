/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.secman;

import java.io.FilePermission;
import java.security.AllPermission;
import java.util.Collection;
import java.util.Collections;

import jakarta.inject.Inject;

import org.jboss.logmanager.Level;
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

import static org.junit.Assert.assertTrue;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class UnresolvedExpressionSecurityManagerTestCase {

    @Inject
    private ServerController container;

    private static final String handlerName =
            UnresolvedExpressionSecurityManagerTestCase.class.getSimpleName();
    private static final String LOG_FILE_NAME = handlerName + ".log";
    private static final String CANNOT_RESOLVE_EXPRESSION_ID = "WFLYCTL0211";
    private static final String SECMGR_SUBSYSTEM_BOOT_FAILED_ID = "WFLYCTL0193";

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
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (client != null) {
                logHandlerSetup.tearDown(client);
                client.close();
            }
        } finally {
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
            addSecurityManagerSubsystemWithUnresolvedExpression(true);
            triggerFailedReloadToNormalMode();
        } finally {
            cleanupSecurityManagerSubsystem(true);
        }
        verifyBootFailureInLogs();
    }

    @Test
    public void testUnresolvedExpressionInMinimumPermsFails() throws Exception {
        // with security manager enabled, boot should fail
        ServerReload.executeReloadAndWaitForCompletion(client, true);
        try {
            addSecurityManagerSubsystemWithUnresolvedExpression(false);
            triggerFailedReloadToNormalMode();
        } finally {
            cleanupSecurityManagerSubsystem(false);
        }
        verifyBootFailureInLogs();
    }

    private void addSecurityManagerSubsystemWithUnresolvedExpression(boolean isMaximumPermissions)
            throws Exception {
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

        ModelNode result = this.client.execute(writePerms);
        assertTrue("Failed to write " + permType + ": " + result,
                Operations.isSuccessfulOutcome(result));
    }

    private void triggerFailedReloadToNormalMode() throws Exception {
        if (AssumeTestGroupUtil.isSecurityManagerDisabled()) {
            ServerReload.executeReloadAndWaitForCompletion(this.client, false);
            return;
        }
        ModelNode reloadOp = Operations.createOperation("reload");
                reloadOp.get("admin-only").set(false);
        assertTrue("Failed to \"fail\" reload server",
                Operations.isSuccessfulOutcome(this.client.execute(reloadOp)));
        container.stop();

    }

    private void verifyBootFailureInLogs() throws Exception {
        boolean foundExpressionError =
                LoggingUtil.hasLogMessage(this.client, handlerName, CANNOT_RESOLVE_EXPRESSION_ID);

        assertTrue("Expected expression resolution error (" + CANNOT_RESOLVE_EXPRESSION_ID + ") in "
                + LOG_FILE_NAME, foundExpressionError);
        // when security manager is disabled, boot failure is not expected
        boolean bootFailed =
                LoggingUtil.hasLogMessage(this.client, handlerName,
                        SECMGR_SUBSYSTEM_BOOT_FAILED_ID);
        if (AssumeTestGroupUtil.isSecurityManagerDisabled()) {
            assertTrue("Did not expect server shutdown (" + SECMGR_SUBSYSTEM_BOOT_FAILED_ID
                    + ") in " + LOG_FILE_NAME, !bootFailed);
            return;
        }
        assertTrue("Expected server shutdown (" + SECMGR_SUBSYSTEM_BOOT_FAILED_ID + ") in "
                + LOG_FILE_NAME, bootFailed);
    }

    private void cleanupSecurityManagerSubsystem(boolean isMaximumPermissions) throws Exception {
        if (container.isStarted()) {
            ServerReload.executeReloadAndWaitForCompletion(this.client, true);
        } else {
            container.startInAdminMode();
            this.client = TestSuiteEnvironment.getModelControllerClient();
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
            ModelNode result = this.client.execute(writeMaxPerms);
            assertTrue("Failed to reset maximum-permissions in the security-manager subsystem: "
                    + result, Operations.isSuccessfulOutcome(result));
        } else {
            // Remove minimum-permissions attribute
            ModelNode undefineMinPerms = Operations.createUndefineAttributeOperation(address, "minimum-permissions");
            ModelNode result = this.client.execute(undefineMinPerms);
            assertTrue("Failed to undefine minimum-permissions in the security-manager subsystem: "
                    + result, Operations.isSuccessfulOutcome(result));
        }
        // Reload to normal mode before tearDown to fully release log handler resources.
        // Without this, file locks persist on Windows preventing log file deletion.
        ServerReload.executeReloadAndWaitForCompletion(client, false);
    }

    private static class LogHandlerSetup extends TestLogHandlerSetupTask {

        @Override
        public Collection<String> getCategories() {
            return Collections.singleton("org.jboss.as.controller.management-operation");
        }

        @Override
        public String getLevel() {
            return Level.ERROR.getName();
        }

        @Override
        public String getHandlerName() {
            return handlerName;
        }

        @Override
        public String getLogFileName() {
            return LOG_FILE_NAME;
        }
    }

}
