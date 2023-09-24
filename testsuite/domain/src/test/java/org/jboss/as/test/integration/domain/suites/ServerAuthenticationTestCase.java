/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.waitUntilState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Ken Wills <kwills@redhat.com>
 *
 * Basic tests for domain server auth when local realm authentication is removed.
 */
@Ignore("[WFCORE-5549] Unable to remove JBOSS_LOCAL_USER with Elytron configuration.")
public class ServerAuthenticationTestCase {

    private static final int FAILED_RELOAD_TIMEOUT_MILLIS = 10000;
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil primaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ServerAuthenticationTestCase.class.getSimpleName());
        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        primaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @After
    public void afterTest() throws Exception {
        // add back local auth to secondary
        addLocalAuth(primaryLifecycleUtil.getDomainClient(), "secondary");
    }

    @Test
    public void testDisableLocalAuthAndStartServers() throws Exception {
        // Check local auth is enabled at the outset and that the servers start
        assertTrue(localAuthEnabled(primaryLifecycleUtil.getDomainClient(), "secondary"));
        waitUntilState(primaryLifecycleUtil.getDomainClient(), "secondary", "main-three", "STARTED");
        waitUntilState(primaryLifecycleUtil.getDomainClient(), "secondary", "other-two", "STARTED");

        // disable local-auth on the secondary
        removeLocalAuth(primaryLifecycleUtil.getDomainClient(), "secondary");

        // reload secondary, and wait for it to register with primary
        reloadHostController(primaryLifecycleUtil.getDomainClient(), "secondary", true);
        awaitHostControllerRegistration(primaryLifecycleUtil.getDomainClient(), "secondary");
        waitUntilState(primaryLifecycleUtil.getDomainClient(), "secondary", "main-three", "STARTED");
        waitUntilState(primaryLifecycleUtil.getDomainClient(), "secondary", "other-two", "STARTED");

        // verify local-auth is off on the secondary
        assertFalse(localAuthEnabled(primaryLifecycleUtil.getDomainClient(), "secondary"));

        // now restart the secondary HC, but don't restart the servers and verify they've reconnected afterwards
        reloadHostController(primaryLifecycleUtil.getDomainClient(), "secondary", false);
        // wait until the secondary has re-registered with the primary
        awaitHostControllerRegistration(primaryLifecycleUtil.getDomainClient(), "secondary");
        waitUntilState(primaryLifecycleUtil.getDomainClient(), "secondary", "main-three", "STARTED");
        waitUntilState(primaryLifecycleUtil.getDomainClient(), "secondary", "other-two", "STARTED");
    }

    private static PathAddress getAuthenticationFactoryPath(final String host) {
        return PathAddress.pathAddress(HOST, host)
                .append(SUBSYSTEM, "elytron")
                .append("sasl-authentication-factory", "management-sasl-authentication");
    }

    private static void redefineMechanismConfiguration(final DomainClient client, final String host, final boolean includeLocalAuth) throws Exception {
        final PathAddress factoryAddress = getAuthenticationFactoryPath(host);

        final ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, factoryAddress);
        operation.get(NAME).set("mechanism-configurations");

        final ModelNode value = new ModelNode();

        // Always add DIGEST-MD5
        final ModelNode digestMd5 = new ModelNode();
        digestMd5.get("mechanism-name").set("DIGEST-MD5");

        final ModelNode mechanismRealmConfigurations = new ModelNode();
        final ModelNode digestMd5RealmConfiguration = new ModelNode();
        digestMd5RealmConfiguration.get("realm-name").set("ManagementRealm");
        mechanismRealmConfigurations.add(digestMd5RealmConfiguration);
        digestMd5.get("mechanism-realm-configurations").set(mechanismRealmConfigurations);
        value.add(digestMd5);

        if (includeLocalAuth) {
            final ModelNode localAuth = new ModelNode();
            localAuth.get("mechanism-name").set("JBOSS-LOCAL-USER");
            localAuth.get("realm-mapper").set("local");
            value.add(localAuth);
        }
        operation.get(VALUE).set(value);

        final ModelNode result = client.execute(operation);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
    }

    private static void removeLocalAuth(final DomainClient client, final String host) throws Exception {
        // remove local auth, requires restart
        redefineMechanismConfiguration(client, host, false);
    }

    private static void reloadHostController(final DomainClient client, final String host, final boolean restartServers) throws Exception {
        final ModelNode op = Util.createEmptyOperation("reload", PathAddress.pathAddress(HOST, host));
        op.get(RESTART_SERVERS).set(restartServers);
        final ModelNode result = client.execute(op);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
    }

    private void addLocalAuth(final DomainClient client, final String host) throws Exception {
        // remove local auth, requires restart
        redefineMechanismConfiguration(client, host, true);
    }

    private static boolean localAuthEnabled(final ModelControllerClient client, final String host) throws Exception {
        final PathAddress factoryAddress = getAuthenticationFactoryPath(host);

        final ModelNode operation = Util.createOperation(READ_ATTRIBUTE_OPERATION, factoryAddress);
        operation.get(NAME).set("mechanism-configurations");

        final ModelNode result = client.execute(operation);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());

        for (ModelNode value : result.get(RESULT).asList()) {
            if ("JBOSS-LOCAL-USER".equals(value.get("mechanism-name").asStringOrNull())) {
                return true;
            }
        }

        return false;
    }


    private boolean awaitHostControllerRegistration(final ModelControllerClient client, final String host) throws Exception {
        final long time = System.currentTimeMillis() + FAILED_RELOAD_TIMEOUT_MILLIS;
        do {
            Thread.sleep(100);
            if (lookupHostInModel(client, host)) {
                return true;
            }
        } while (System.currentTimeMillis() < time);
        return false;
    }

    // mechanism to wait for the the secondary to register with the primary HC so it is present in the model
    // before continuing.
    private boolean lookupHostInModel(final ModelControllerClient client, final String host) throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).add(HOST, host);
        operation.get(NAME).set(HOST_STATE);

        try {
            final ModelNode result = client.execute(operation);
            if (result.get(OUTCOME).asString().equals(SUCCESS)){
                final ModelNode model = result.require(RESULT);
                if (model.asString().equalsIgnoreCase("running")) {
                    return true;
                }
            }
        } catch (IOException e) {
            //
        }
        return false;
    }
}
