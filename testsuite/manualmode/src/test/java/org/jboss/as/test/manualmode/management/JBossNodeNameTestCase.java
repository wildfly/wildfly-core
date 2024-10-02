/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.management;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test for various jboss.node.name and jboss.server.name configurations.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class JBossNodeNameTestCase {
    private static final String TEST_NODE_NAME = "test-node-name";
    private static final String TEST_BOOT_SERVER_NAME = "test-boot-server-name";
    private static final String TEST_MGMT_SERVER_NAME = "test-mgmt-server-name";

    @Inject
    private ServerController serverController;

    /**
     * Test default when neither jboss.node.name or jboss.server.name are provided
     * Both should have the same value
     */
    @Test
    public void testDefault() throws UnsuccessfulOperationException {
        try {
            serverController.start();
            String jbossNodeName = resolveSystemProperty(ServerEnvironment.NODE_NAME);
            String jbossServerName = resolveSystemProperty(ServerEnvironment.SERVER_NAME);
            Assert.assertEquals("jboss.node.name and jboss.server.name should be the same.", jbossServerName, jbossNodeName);
        } finally {
            serverController.stop();
        }
    }

    /**
     * Test server started with -Djboss.node.name
     * jboss.server.name should default to hostname
     */
    @Test
    public void testNodeNameNoServerName() throws UnsuccessfulOperationException {
        final String originalArgs = System.getProperty("jvm.args");
        try {
            System.setProperty("jvm.args", originalArgs + " -Djboss.node.name=" + TEST_NODE_NAME);
            serverController.start();
            String jbossNodeName = resolveSystemProperty(ServerEnvironment.NODE_NAME);
            Assert.assertEquals("System property " + ServerEnvironment.NODE_NAME + " has wrong value.", TEST_NODE_NAME, jbossNodeName);
            // jboss.server.name defaults to hostname based on ServerEnvironment.configureQualifiedHostName logic
            String jbossServerName = resolveSystemProperty(ServerEnvironment.SERVER_NAME);
            Assert.assertNotEquals("jboss.node.name and jboss.server.name should be different.", jbossServerName, jbossNodeName);
        } finally {
            System.setProperty("jvm.args", originalArgs);
            serverController.stop();
        }
    }

    /**
     * Test server started with -Djboss.server.name
     * Both jboss.server.name and jboss.node.name should have the same provided value
     */
    @Test
    public void testBootServerNameNoNodeName() throws UnsuccessfulOperationException {
        final String originalArgs = System.getProperty("jvm.args");
        try {
            System.setProperty("jvm.args", originalArgs + " -Djboss.server.name=" + TEST_BOOT_SERVER_NAME);
            serverController.start();
            validateSystemProperty(ServerEnvironment.SERVER_NAME, TEST_BOOT_SERVER_NAME);
            validateSystemProperty(ServerEnvironment.NODE_NAME, TEST_BOOT_SERVER_NAME);
        } finally {
            System.setProperty("jvm.args", originalArgs);
            serverController.stop();
        }
    }

    /**
     * Test jboss.server.name configured via mgmt operation
     * Both jboss.server.name and jboss.node.name should have the same provided value
     */
    @Test
    public void testMgmtServerNameNoNodeName() throws UnsuccessfulOperationException {
        try {
            serverController.start();
            setServerNameViaManagementOperation(TEST_MGMT_SERVER_NAME);
            serverController.reload();
            validateSystemProperty(ServerEnvironment.SERVER_NAME, TEST_MGMT_SERVER_NAME);
            validateSystemProperty(ServerEnvironment.NODE_NAME, TEST_MGMT_SERVER_NAME);
        } finally {
            resetServerNameViaManagementOperation();
            serverController.stop();
        }
    }

    /**
     * Test jboss.server.name configured via both mgmt operation and -Djboss.server.name
     * Both jboss.server.name and jboss.node.name should have the same provided value defined in server configuration,
     * not from system property
     */
    @Test
    public void testMgmtBootServerNameNoNodeName() throws UnsuccessfulOperationException {
        final String originalArgs = System.getProperty("jvm.args");
        try {
            System.setProperty("jvm.args", originalArgs + " -Djboss.server.name=" + TEST_BOOT_SERVER_NAME);
            serverController.start();
            setServerNameViaManagementOperation(TEST_MGMT_SERVER_NAME);
            serverController.reload();
            validateSystemProperty(ServerEnvironment.SERVER_NAME, TEST_MGMT_SERVER_NAME);
            validateSystemProperty(ServerEnvironment.NODE_NAME, TEST_MGMT_SERVER_NAME);
        } finally {
            System.setProperty("jvm.args", originalArgs);
            resetServerNameViaManagementOperation();
            serverController.stop();
        }
    }

    /**
     * Test jboss.server.name configured via mgmt operation and -Djboss.node.name provided during boot
     * jboss.server.name should have value provided in server configuration via management operation
     * jboss.node.name should have value provided via system property
     */
    @Test
    public void testMgmtServerNameNodeName() throws UnsuccessfulOperationException {
        final String originalArgs = System.getProperty("jvm.args");
        try {
            System.setProperty("jvm.args", originalArgs + " -Djboss.server.name=" + TEST_BOOT_SERVER_NAME + " -Djboss.node.name=" + TEST_NODE_NAME);
            serverController.start();
            setServerNameViaManagementOperation(TEST_MGMT_SERVER_NAME);
            serverController.reload();
            validateSystemProperty(ServerEnvironment.SERVER_NAME, TEST_MGMT_SERVER_NAME);
            validateSystemProperty(ServerEnvironment.NODE_NAME, TEST_NODE_NAME);
        } finally {
            System.setProperty("jvm.args", originalArgs);
            serverController.stop();
        }
    }

    /**
     * Test server started with both -Djboss.server.name and -Djboss.node.name
     * jboss.server.name should have value provided via system property
     * jboss.node.name should have value provided via system property
     */
    @Test
    public void testBootServerNameNodeName() throws UnsuccessfulOperationException {
        final String originalArgs = System.getProperty("jvm.args");
        try {
            System.setProperty("jvm.args", originalArgs + " -Djboss.server.name=" + TEST_BOOT_SERVER_NAME + " -Djboss.node.name=" + TEST_NODE_NAME);
            serverController.start();
            validateSystemProperty(ServerEnvironment.SERVER_NAME, TEST_BOOT_SERVER_NAME);
            validateSystemProperty(ServerEnvironment.NODE_NAME, TEST_NODE_NAME);
        } finally {
            System.setProperty("jvm.args", originalArgs);
            serverController.stop();
        }
    }

    private void validateSystemProperty(final String propertyName, final String expectedValue) throws UnsuccessfulOperationException {
        Assert.assertEquals("System property " + propertyName + " has wrong value.", expectedValue, resolveSystemProperty(propertyName));
    }

    private String resolveSystemProperty(final String propertyName) throws UnsuccessfulOperationException {
        ModelNode operation = Util.createEmptyOperation("resolve-expression", PathAddress.EMPTY_ADDRESS);
        operation.get("expression").set("${" + propertyName + "}");
        ModelNode response = serverController.getClient().executeForResult(operation);
        return response.asString();
    }

    private void setServerNameViaManagementOperation(final String serverName) throws UnsuccessfulOperationException {
        ModelNode changeServerNameOperation = Util.getWriteAttributeOperation(PathAddress.EMPTY_ADDRESS, "name", serverName);
        serverController.getClient().executeForResult(changeServerNameOperation);
    }

    private void resetServerNameViaManagementOperation() throws UnsuccessfulOperationException {
        ModelNode resetServerNameOperation = Util.getUndefineAttributeOperation(PathAddress.EMPTY_ADDRESS, "name");
        serverController.getClient().executeForResult(resetServerNameOperation);
    }
}
