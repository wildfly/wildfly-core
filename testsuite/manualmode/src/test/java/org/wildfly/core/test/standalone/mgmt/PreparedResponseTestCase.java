/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class PreparedResponseTestCase {

    public static Logger LOGGER = Logger.getLogger(PreparedResponseTestCase.class);
    private static final long FREQUENCY = TimeoutUtil.adjust(50);
    private static final long SHUTDOWN_WAITING_TIME = TimeoutUtil.adjust(3000);
    private static final long RESTART_WAITING_TIME = TimeoutUtil.adjust(20000);

    @Inject
    protected static ServerController controller;

    @BeforeClass
    public static void startAndSetupContainer() throws Exception {
        ExtensionUtils.createExtensionModule(BlockerExtension.MODULE_NAME, BlockerExtension.class, EmptySubsystemParser.class.getPackage());
        controller.start();
        try (ManagementClient managementClient = controller.getClient()) {
            managementClient.executeForResult(Operations.createAddOperation(PathAddress.pathAddress(EXTENSION, BlockerExtension.MODULE_NAME).toModelNode()));
            managementClient.executeForResult(Operations.createAddOperation(PathAddress.pathAddress(SUBSYSTEM, BlockerExtension.SUBSYSTEM_NAME).toModelNode()));
        }
    }

    @AfterClass
    public static void stopContainer() throws Exception {
        try (ManagementClient managementClient = getManagementClient()) {
            managementClient.executeForResult(Operations.createRemoveOperation(PathAddress.pathAddress(EXTENSION, BlockerExtension.MODULE_NAME).toModelNode()));
        }
        controller.stop();
        ExtensionUtils.deleteExtensionModule(BlockerExtension.MODULE_NAME);
    }

    private void block(ManagementClient client) throws UnsuccessfulOperationException {
        ModelNode blockOp = Operations.createOperation("block", PathAddress.pathAddress(SUBSYSTEM, BlockerExtension.SUBSYSTEM_NAME).toModelNode());
        blockOp.get("block-point").set("SERVICE_STOP");
        blockOp.get("block-time").set(SHUTDOWN_WAITING_TIME);
        client.executeForResult(blockOp);
    }

    @Test
    public void reloadServer() throws Exception {
        try (ManagementClient managementClient = getManagementClient()) {
            block(managementClient);
            long timeout = SHUTDOWN_WAITING_TIME + System.currentTimeMillis();
            ServerReload.executeReload(managementClient.getControllerClient(), false);
            while (System.currentTimeMillis() < timeout) {
                Thread.sleep(FREQUENCY);
                try {
                    managementClient.isServerInRunningState();
                } catch (RuntimeException ex) {
                    break;
                }
            }
            Assert.assertFalse(System.currentTimeMillis() < timeout);
            timeout = RESTART_WAITING_TIME + System.currentTimeMillis();
            while (System.currentTimeMillis() < timeout) {
                Thread.sleep(FREQUENCY);
                try {
                    managementClient.isServerInRunningState();
                } catch (RuntimeException ex) {
                    break;
                }
            }
        }
    }

    @Test
    public void shutdownServer() throws Exception {
        try (ManagementClient managementClient = getManagementClient()) {
            block(managementClient);
            long timeout = SHUTDOWN_WAITING_TIME + System.currentTimeMillis();
            managementClient.executeForResult(Operations.createOperation(SHUTDOWN, new ModelNode().setEmptyList()));
            while (System.currentTimeMillis() < timeout) {
                Thread.sleep(FREQUENCY);
                try {
                    controller.getClient().isServerInRunningState();
                } catch (RuntimeException ex) {
                    break;
                }
            }
            Assert.assertFalse(System.currentTimeMillis() < timeout);
            Assert.assertFalse(controller.getClient().isServerInRunningState());
        }
        Assert.assertFalse(controller.getClient().isServerInRunningState());
        try {
            controller.stop(); //Server is already stopped but the ServerController doesn't know it
        } catch (RuntimeException ex) {
            //ignore the exception as it is expected
        }
        controller.start();
    }

    static ManagementClient getManagementClient() {
        final ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient();
        return new ManagementClient(client, TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), "remote+http");
    }
}
