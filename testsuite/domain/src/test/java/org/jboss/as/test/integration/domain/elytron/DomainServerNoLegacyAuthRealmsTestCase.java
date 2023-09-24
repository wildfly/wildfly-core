/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.waitUntilState;
import static org.junit.Assert.assertEquals;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This tests a managed servers starting and connecting back to the primary and secondary HCs which have
 * no legacy <security-realm> defined. Local auth must be available on the HCs for the servers to connect.
 */
public class DomainServerNoLegacyAuthRealmsTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil primary;
    private static DomainLifecycleUtil secondary;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.create(
                DomainTestSupport.Configuration.create(SecondaryHostControllerElytronAuthenticationTestCase.class.getSimpleName(),
                        "domain-configs/domain-minimal.xml",
                        "host-configs/host-primary-elytron-no-legacy-realms.xml", "host-configs/host-secondary-elytron-no-legacy-realms.xml"));

        primary = testSupport.getDomainPrimaryLifecycleUtil();
        secondary = testSupport.getDomainSecondaryLifecycleUtil();
        testSupport.start();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        testSupport = null;
        primary = null;
        secondary = null;
    }

    @Test
    public void testDomainServersStartWithNoLegacyAuthRealms() throws Exception {
        // verify servers connected and running
        waitUntilState(primary.getDomainClient(), "primary", "server-one", "STARTED");
        waitUntilState(primary.getDomainClient(), "primary", "server-two", "STARTED");
        waitUntilState(primary.getDomainClient(), "secondary", "server-one", "STARTED");
        waitUntilState(primary.getDomainClient(), "secondary", "server-two", "STARTED");

        // TODO replace all these with simple calls to lifecycleUtil once WFCORE-3373 is done
        reloadHostController(primary, false);
        primary.awaitHostController(System.currentTimeMillis());
        waitUntilState(primary.getDomainClient(), "primary", "server-one", "STARTED");
        waitUntilState(primary.getDomainClient(), "primary", "server-two", "STARTED");

        reloadHostController(primary, true);
        primary.awaitHostController(System.currentTimeMillis());
        waitUntilState(primary.getDomainClient(), "primary", "server-one", "STARTED");
        waitUntilState(primary.getDomainClient(), "primary", "server-two", "STARTED");

        reloadServer(primary.getDomainClient(), "primary", "server-one");
        waitUntilState(primary.getDomainClient(), "primary", "server-one", "STARTED");
        waitUntilState(primary.getDomainClient(), "primary", "server-two", "STARTED");

        // now reload the secondary HC, but don't restart the servers and verify they've reconnected afterwards
        reloadHostController(secondary, false);
        secondary.awaitHostController(System.currentTimeMillis());
        waitUntilState(primary.getDomainClient(), "secondary", "server-one", "STARTED");
        waitUntilState(primary.getDomainClient(), "secondary", "server-two", "STARTED");
    }

    private static void reloadHostController(final DomainLifecycleUtil lifecycleUtil, final boolean restartServers) throws Exception {
        ModelNode op = Util.createEmptyOperation(RELOAD, lifecycleUtil.getAddress());
        op.get(RESTART_SERVERS).set(restartServers);
        final ModelNode result = lifecycleUtil.executeAwaitConnectionClosed(op);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
    }

    private static void reloadServer(final DomainClient client, final String host, final String serverName) throws Exception {
        final PathAddress path = PathAddress.pathAddress(HOST, host).append(SERVER_CONFIG, serverName);
        ModelNode op = Util.createEmptyOperation("reload", path);
        final ModelNode result = client.execute(op);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
    }

}
