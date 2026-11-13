/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONNECTION_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE_TYPE;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that a connection-timeout attribute on the remote domain controller configuration
 * can be set and is respected during secondary Host Controller registration.
 *
 * The connection-timeout controls the per-attempt TCP connection timeout when the secondary
 * HC connects to the primary DC. It defaults to 5000 milliseconds.
 */
public class ConnectionTimeoutDomainTestCase {

    private static final long DEFAULT_CONNECTION_TIMEOUT = 5000L;

    private static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");

    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static DomainClient secondaryClient;
    private static DomainLifecycleUtil primaryLifecycleUtil;
    private static DomainLifecycleUtil secondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(
                ConnectionTimeoutDomainTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-primary.xml",
                "host-configs/host-secondary.xml"
        );

        testSupport = DomainTestSupport.create(configuration);
        testSupport.start();

        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        secondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
        primaryClient = primaryLifecycleUtil.getDomainClient();
        secondaryClient = secondaryLifecycleUtil.getDomainClient();
    }

    @AfterClass
    public static void shutdownDomain() {
        testSupport.close();
        testSupport = null;
        primaryClient = null;
        secondaryClient = null;
        primaryLifecycleUtil = null;
        secondaryLifecycleUtil = null;
    }

    @Test
    public void testSecondaryRegisteredWithDefaultTimeout() throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR);
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    @Test
    public void testDefaultConnectionTimeoutValue() throws Exception {
        ModelNode dcConfig = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                secondaryClient
        );

        ModelNode remoteConfig = dcConfig.get(REMOTE);
        Assert.assertTrue("Remote domain controller config should be defined", remoteConfig.isDefined());
        Assert.assertFalse("connection-timeout should not be set in host-secondary.xml", remoteConfig.hasDefined(CONNECTION_TIMEOUT));

        ModelNode description = DomainTestUtils.executeForResult(
                Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, SECONDARY_ADDR),
                secondaryClient
        );
        ModelNode timeoutDescription = description.get(ATTRIBUTES, DOMAIN_CONTROLLER, VALUE_TYPE, REMOTE, VALUE_TYPE, CONNECTION_TIMEOUT);
        Assert.assertEquals("Default connection-timeout should be 5000ms", DEFAULT_CONNECTION_TIMEOUT, timeoutDescription.get(DEFAULT).asLong());
    }

    @Test
    public void testInvalidConnectionTimeoutRejected() throws Exception {
        ModelNode originalDcConfig = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                secondaryClient
        );

        try {
            for (long invalidTimeout : new long[] {0L, -1L}) {
                ModelNode dcConfig = originalDcConfig.clone();
                dcConfig.get(REMOTE).get(CONNECTION_TIMEOUT).set(invalidTimeout);
                DomainTestUtils.executeForFailure(Util.getWriteAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER, dcConfig), secondaryClient);
            }
        } finally {
            ModelNode currentDcConfig = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                    secondaryClient
            );
            if (!currentDcConfig.equals(originalDcConfig)) {
                DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER, originalDcConfig), secondaryClient);
                reloadSecondary();
            }
        }
    }

    @Test
    public void testSetConnectionTimeout() throws Exception {
        final long customTimeout = 10000L;

        ModelNode originalDcConfig = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                secondaryClient
        );

        try {
            ModelNode dcConfig = originalDcConfig.clone();
            dcConfig.get(REMOTE).get(CONNECTION_TIMEOUT).set(customTimeout);

            ModelNode writeOp = Util.getWriteAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER, dcConfig);
            DomainTestUtils.executeForResult(writeOp, secondaryClient);

            ModelNode updatedDcConfig = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                    secondaryClient
            );
            Assert.assertEquals("Connection timeout should be updated to custom value", customTimeout, updatedDcConfig.get(REMOTE).get(CONNECTION_TIMEOUT).asLong());

            reloadSecondary();

            ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR);
            DomainTestUtils.executeForResult(op, primaryClient);

            ModelNode reloadedDcConfig = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                    secondaryClient
            );
            Assert.assertEquals("Connection timeout should survive reload", customTimeout, reloadedDcConfig.get(REMOTE).get(CONNECTION_TIMEOUT).asLong());
        } finally {
            ModelNode restoreOp = Util.getWriteAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER, originalDcConfig);
            DomainTestUtils.executeForResult(restoreOp, secondaryClient);

            reloadSecondary();
        }
    }

    @Test
    public void testSetConnectionTimeoutViaDeprecatedOperation() throws Exception {
        final long customTimeout = 15000L;

        ModelNode originalDcConfig = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                secondaryClient
        );
        ModelNode remoteConfig = originalDcConfig.get(REMOTE);

        try {
            ModelNode updatedRemoteConfig = remoteConfig.clone();
            updatedRemoteConfig.get(CONNECTION_TIMEOUT).set(customTimeout);
            replaceRemoteDomainController(updatedRemoteConfig);

            ModelNode updatedDcConfig = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                    secondaryClient
            );
            Assert.assertEquals("Connection timeout should be set via deprecated operation", customTimeout, updatedDcConfig.get(REMOTE).get(CONNECTION_TIMEOUT).asLong());

            reloadSecondary();
            DomainTestUtils.executeForResult(Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR), primaryClient);
        } finally {
            replaceRemoteDomainController(remoteConfig);

            reloadSecondary();
        }
    }

    @Test
    public void testConnectionTimeoutWithExpression() throws Exception {
        ModelNode originalDcConfig = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                secondaryClient
        );

        try {
            ModelNode dcConfig = originalDcConfig.clone();
            dcConfig.get(REMOTE).get(CONNECTION_TIMEOUT).set("${jboss.domain.connection.timeout:8000}");

            ModelNode writeOp = Util.getWriteAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER, dcConfig);
            DomainTestUtils.executeForResult(writeOp, secondaryClient);

            ModelNode updatedDcConfig = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                    secondaryClient
            );

            ModelNode timeoutNode = updatedDcConfig.get(REMOTE).get(CONNECTION_TIMEOUT);
            Assert.assertTrue("Connection timeout should be defined", timeoutNode.isDefined());

            reloadSecondary();
            DomainTestUtils.executeForResult(Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR), primaryClient);
        } finally {
            ModelNode restoreOp = Util.getWriteAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER, originalDcConfig);
            DomainTestUtils.executeForResult(restoreOp, secondaryClient);

            reloadSecondary();
        }
    }

    private static void reloadSecondary() throws Exception {
        DomainLifecycleUtil.ReloadParameters parameters = new DomainLifecycleUtil.ReloadParameters()
                .setRestartServers(false)
                .setWaitForServers(false);
        secondaryLifecycleUtil.reload("secondary", parameters);
        secondaryClient = secondaryLifecycleUtil.getDomainClient();
    }

    private static void replaceRemoteDomainController(ModelNode remoteConfig) throws Exception {
        DomainTestUtils.executeForResult(Util.createEmptyOperation("remove-remote-domain-controller", SECONDARY_ADDR), secondaryClient);

        ModelNode writeRemoteDc = Util.createEmptyOperation("write-remote-domain-controller", SECONDARY_ADDR);
        for (String key : remoteConfig.keys()) {
            writeRemoteDc.get(key).set(remoteConfig.get(key));
        }
        DomainTestUtils.executeForResult(writeRemoteDc, secondaryClient);
    }
}
