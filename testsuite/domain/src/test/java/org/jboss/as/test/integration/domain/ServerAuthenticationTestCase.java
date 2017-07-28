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

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_CONFIGURATION_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURITY_REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.AUTHENTICATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.Authentication;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

/**
 * @author Ken Wills <kwills@redhat.com>
 *
 * Basic tests for domain server auth when local realm authentication is removed.
 */
public class ServerAuthenticationTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil master;
    private static DomainLifecycleUtil slave;

    private static final String USERNAME = "testSuite";
    private static final String PASSWORD = "testSuitePassword";

    @BeforeClass
    public static void setupDomain() throws Exception {
        DomainTestSupport.Configuration config =  DomainTestSupport.Configuration.create(ServerAuthenticationTestCase.class.getSimpleName(),
                "domain-configs/domain-minimal.xml", "host-configs/host-master.xml", "host-configs/host-minimal.xml");
        testSupport = DomainTestSupport.createAndStartSupport(config);
        master = testSupport.getDomainMasterLifecycleUtil();
        slave = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        // TODO this seems a little racy adding back on shutdown, and gets rolled back sometimes. This should be fixed before adding
        // to DomainTestSuite suiteClasses.
        // ServerAuthenticationTestCase.addLocalAuth(master.getDomainClient(), "slave");
        testSupport.stop();
        testSupport = null;
        master = null;
        slave = null;
    }

    @Test
    public void testDisableLocalAuthAndStartServers() throws Exception {
        // add a user we can re-auth with
        final String testName = ServerAuthenticationTestCase.class.getSimpleName();
        final File domains = new File("target" + File.separator + "domains" + File.separator + testName);
        final File masterDir = new File(domains, "slave");
        final File domainConfigDir = new File(masterDir, "configuration");
        addUser(domainConfigDir, "ManagementRealm", USERNAME, PASSWORD);

        removeLocalAuth(master.getDomainClient(), "slave");

        // don't reset the configs.
        slave.getConfiguration().setRewriteConfigFiles(false);
        slave.stop();
        // update the slave config with the username / password since local auth is disabled
        slave.getConfiguration().setCallbackHandler(Authentication.getCallbackHandler(USERNAME, PASSWORD, "ManagementRealm"));
        slave.start();
        // verify local-auth is off on the slave
        ModelNode localSlaveAuth = getLocalAuthValue(master.getDomainClient(), "slave");
        assertEquals(localSlaveAuth.toJSONString(true), FAILED, localSlaveAuth.get(OUTCOME).asString());
        assertTrue(localSlaveAuth.get(FAILURE_DESCRIPTION).asString().startsWith("WFLYCTL0216:")); // resource not found
        // verify server connected and running
        verifyServerStarted(master.getDomainClient(), "slave", "main-three");
        // now restart the slave HC, but don't restart the servers and verify they've reconnected afterwards
        reloadHostController(master.getDomainClient(), "slave", false);
        slave.awaitHostController(System.currentTimeMillis());
        verifyServerStarted(master.getDomainClient(), "slave", "main-three");
    }

    private void addUser(final File domainConfigDir, final String realm, final String username, final String password) throws Exception {
        File usersFile = new File(domainConfigDir, "mgmt-users.properties");
        Files.write(usersFile.toPath(),
                (username + "=" + new UsernamePasswordHashUtil().generateHashedHexURP(username, realm, password.toCharArray()) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static PathAddress getLocalAuthPath(final String host) {
        return PathAddress.pathAddress(HOST, host)
                .append(CORE_SERVICE, MANAGEMENT)
                .append(SECURITY_REALM, "ManagementRealm")
                .append(AUTHENTICATION, LOCAL);
    }

    private static void removeLocalAuth(final DomainClient client, final String host) throws Exception {
        // remove local auth, requires restart
        final ModelNode op = Util.createEmptyOperation(REMOVE, getLocalAuthPath(host));
        final ModelNode result = client.execute(op);
        assertEquals(result.toJSONString(true), SUCCESS, result.get(OUTCOME).asString());
    }

    private static void reloadHostController(final DomainClient client, final String host, final boolean restartServers) throws Exception {
        final ModelNode op = Util.createEmptyOperation("reload", PathAddress.pathAddress(HOST, host));
        op.get(RESTART_SERVERS).set(restartServers);
        final ModelNode result = client.execute(op);
        assertEquals(result.toJSONString(true), SUCCESS, result.get(OUTCOME).asString());
    }

    private static void addLocalAuth(final DomainClient client, final String host) throws Exception {
        // remove local auth, requires restart
        final ModelNode op = Util.createEmptyOperation(ADD, getLocalAuthPath(host));
        op.get("default-user").set("$local");
        op.get("skip-group-loading").set("true");
        final ModelNode result = client.execute(op);
        assertEquals(result.toJSONString(true), SUCCESS, result.get(OUTCOME).asString());
    }

    private static ModelNode getLocalAuthValue(final ModelControllerClient client, final String host) throws Exception {
        final ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, getLocalAuthPath(host));
        return client.execute(op);
    }

    private void verifyServerStarted(final ModelControllerClient client, final String host, final String serverName) throws Exception {
        // verify server connected and running
        final ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.pathAddress(HOST, host).append(SERVER, serverName));
        op.get(NAME).set(RUNTIME_CONFIGURATION_STATE);
        final ModelNode result = client.execute(op);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertEquals("ok", result.get(RESULT).asString());
    }
}
