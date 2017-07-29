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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_CONFIGURATION_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
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

    private static final String OK = "ok";

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.create(
                DomainTestSupport.Configuration.create(SlaveHostControllerElytronAuthenticationTestCase.class.getSimpleName(),
                        "domain-configs/domain-minimal.xml",
                        "host-configs/host-master-elytron-no-legacy-realms.xml", "host-configs/host-slave-elytron-no-legacy-realms.xml"));

        master = testSupport.getDomainMasterLifecycleUtil();
        slave = testSupport.getDomainSlaveLifecycleUtil();
        testSupport.start();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.stop();
        testSupport = null;
        master = null;
        slave = null;
    }

    @Test
    public void testDomainServersStartWithNoLegacyAuthRealms() throws Exception {
        // verify server connected and running
        verifyServerStarted(master.getDomainClient(), "master", "server-one");
        verifyServerStarted(master.getDomainClient(), "master", "server-two");
        verifyServerStarted(master.getDomainClient(), "slave", "server-two");
        verifyServerStarted(master.getDomainClient(), "slave", "server-one");

        reloadHostController(master.getDomainClient(), "master", false);
        master.awaitHostController(System.currentTimeMillis());
        verifyServerStarted(master.getDomainClient(), "master", "server-one");
        verifyServerStarted(master.getDomainClient(), "master", "server-two");

        reloadHostController(master.getDomainClient(), "master", true);
        master.awaitHostController(System.currentTimeMillis());
        verifyServerStarted(master.getDomainClient(), "master", "server-one");
        verifyServerStarted(master.getDomainClient(), "master", "server-two");

        reloadServer(master.getDomainClient(), "master", "server-one");
        master.awaitHostController(System.currentTimeMillis());
        verifyServerStarted(master.getDomainClient(), "master", "server-two");
        verifyServerStarted(master.getDomainClient(), "master", "server-one");

        // now reload the slave HC, but don't restart the servers and verify they've reconnected afterwards
        reloadHostController(slave.getDomainClient(), "slave", false);
        slave.awaitHostController(System.currentTimeMillis());
        verifyServerStarted(master.getDomainClient(), "slave", "server-two");
        verifyServerStarted(master.getDomainClient(), "slave", "server-one");
    }

    private static void reloadHostController(final DomainClient client, final String host, final boolean restartServers) throws Exception {
        ModelNode op = Util.createEmptyOperation(RELOAD, PathAddress.pathAddress(HOST, host));
        op.get(RESTART_SERVERS).set(restartServers);
        final ModelNode result = client.execute(op);
        assertEquals(result.toJSONString(true), SUCCESS, result.get(OUTCOME).asString());
    }

    private static void reloadServer(final DomainClient client, final String host, final String serverName) throws Exception {
        final PathAddress path = PathAddress.pathAddress(HOST, host).append(SERVER_CONFIG, serverName);
        ModelNode op = Util.createEmptyOperation("reload", path);
        final ModelNode result = client.execute(op);
        assertEquals(result.toJSONString(true), SUCCESS, result.get(OUTCOME).asString());
    }

    private void verifyServerStarted(final ModelControllerClient client, final String host, final String serverName) throws Exception {
        // verify server connected and running
        final PathAddress serverPath = PathAddress.pathAddress(HOST, host)
                .append(SERVER, serverName);
        final ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, serverPath);
        op.get(NAME).set(RUNTIME_CONFIGURATION_STATE);
        final ModelNode result = client.execute(op);
        assertEquals(result.toJSONString(true), SUCCESS, result.get(OUTCOME).asString());
        assertEquals(result.toJSONString(true), "ok", result.get(RESULT).asString());
    }
}
