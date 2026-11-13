/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONNECTION_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the secondary Host Controller can boot and register with the primary DC
 * when the connection-timeout attribute is specified in the host XML configuration.
 *
 * Uses host-secondary-connection-timeout.xml which has connection-timeout="10000"
 * on the remote domain-controller element.
 */
public class ConnectionTimeoutXmlParsingTestCase {

    private static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");

    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static DomainClient secondaryClient;

    @BeforeClass
    public static void setupDomain() throws Exception {
        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(
                ConnectionTimeoutXmlParsingTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-primary.xml",
                "host-configs/host-secondary-connection-timeout.xml"
        );

        testSupport = DomainTestSupport.create(configuration);
        testSupport.start();

        primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        secondaryClient = testSupport.getDomainSecondaryLifecycleUtil().getDomainClient();
    }

    @AfterClass
    public static void shutdownDomain() {
        testSupport.close();
        testSupport = null;
        primaryClient = null;
        secondaryClient = null;
    }

    @Test
    public void testSecondaryRegisteredWithCustomTimeout() throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR);
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    @Test
    public void testConnectionTimeoutParsedFromXml() throws Exception {
        ModelNode dcConfig = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                secondaryClient
        );

        ModelNode remoteConfig = dcConfig.get(REMOTE);
        Assert.assertTrue("Remote domain controller config should be defined", remoteConfig.isDefined());

        Assert.assertTrue("connection-timeout should be defined", remoteConfig.hasDefined(CONNECTION_TIMEOUT));
        Assert.assertEquals("connection-timeout should be 10000ms as configured in XML", 10000L, remoteConfig.get(CONNECTION_TIMEOUT).asLong());
    }
}
