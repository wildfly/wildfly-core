/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * This tests a managed servers starting and connecting back to the master and slave HCs which have
 * no legacy <security-realm> defined. Local auth must be available on the HCs for the servers to connect.
 */
public class DomainServerNoLegacyAuthRealmsTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil master;
    private static DomainLifecycleUtil slave;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.create(
                DomainTestSupport.Configuration.create(SlaveHostControllerElytronAuthenticationTestCase.class.getSimpleName(),
                        "domain-configs/domain-minimal.xml",
                        "host-configs/host-primary-elytron-no-legacy-realms.xml", "host-configs/host-secondary-elytron-no-legacy-realms.xml"));

        master = testSupport.getDomainMasterLifecycleUtil();
        slave = testSupport.getDomainSlaveLifecycleUtil();
        testSupport.start();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        testSupport = null;
        master = null;
        slave = null;
    }

    @Test
    public void testDomainServersStartWithNoLegacyAuthRealms() throws Exception {
        // verify servers connected and running
        waitUntilState(master.getDomainClient(), "master", "server-one", "STARTED");
        waitUntilState(master.getDomainClient(), "master", "server-two", "STARTED");
        waitUntilState(master.getDomainClient(), "slave", "server-one", "STARTED");
        waitUntilState(master.getDomainClient(), "slave", "server-two", "STARTED");

        // TODO replace all these with simple calls to lifecycleUtil once WFCORE-3373 is done
        reloadHostController(master, false);
        master.awaitHostController(System.currentTimeMillis());
        waitUntilState(master.getDomainClient(), "master", "server-one", "STARTED");
        waitUntilState(master.getDomainClient(), "master", "server-two", "STARTED");

        reloadHostController(master, true);
        master.awaitHostController(System.currentTimeMillis());
        waitUntilState(master.getDomainClient(), "master", "server-one", "STARTED");
        waitUntilState(master.getDomainClient(), "master", "server-two", "STARTED");

        reloadServer(master.getDomainClient(), "master", "server-one");
        waitUntilState(master.getDomainClient(), "master", "server-one", "STARTED");
        waitUntilState(master.getDomainClient(), "master", "server-two", "STARTED");

        // now reload the slave HC, but don't restart the servers and verify they've reconnected afterwards
        reloadHostController(slave, false);
        slave.awaitHostController(System.currentTimeMillis());
        waitUntilState(master.getDomainClient(), "slave", "server-one", "STARTED");
        waitUntilState(master.getDomainClient(), "slave", "server-two", "STARTED");
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
