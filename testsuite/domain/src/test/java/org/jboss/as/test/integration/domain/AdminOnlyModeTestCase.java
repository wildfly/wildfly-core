/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
//import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Tests of running domain hosts in admin-only move.
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class AdminOnlyModeTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    private static final ModelNode slave = new ModelNode();
    private static final ModelNode mainOne = new ModelNode();
    private static final ModelNode newServerConfigAddress = new ModelNode();
    private static final ModelNode newRunningServerAddress = new ModelNode();

    static {
        // (host=slave)
        slave.add("host", "secondary");
        // (host=slave),(server-config=new-server)
        newServerConfigAddress.add("host", "secondary");
        newServerConfigAddress.add("server-config", "new-server");
        // (host=slave),(server=new-server)
        newRunningServerAddress.add("host", "secondary");
        newRunningServerAddress.add("server", "new-server");
        // (host=master),(server-config=main-one)
        mainOne.add("host", "primary");
        mainOne.add("server-config", "main-one");
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.createAndStartDefaultSupport(AdminOnlyModeTestCase.class.getSimpleName());

        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();

        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
    }

    @Test
    public void testAdminOnlyMode() throws Exception {

        final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

        // restart master HC in admin only mode
        ModelNode op = new ModelNode();
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(OP).set("reload");
        op.get("admin-only").set(true);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);

        // Try to reconnect to the hc
        domainMasterLifecycleUtil.connect();
        domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());

        op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(NAME).set("running-mode");

        // Validate that we are started in the --admin-only mode
        final ModelNode result = executeForResult(masterClient, op);
        Assert.assertEquals("ADMIN_ONLY", result.asString());

        // restart back to normal mode
        op = new ModelNode();
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(OP).set("reload");
        op.get("admin-only").set(false);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);

        // Try to reconnect to the hc
        domainMasterLifecycleUtil.connect();
        domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());

        // check that the servers are up
        domainMasterLifecycleUtil.awaitServers(System.currentTimeMillis());

        // Wait for the slave to reconnect
        waitForHost(masterClient, "secondary");
        domainSlaveLifecycleUtil.awaitServers(System.currentTimeMillis());
    }

    @Test
    public void testAdminOnlyModeRestartServers() throws Exception {
        final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

        // restart master HC in admin only mode
        ModelNode op = new ModelNode();
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(OP).set("reload");
        op.get("admin-only").set(true);
        op.get("restart-servers").set(false);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);

        // Try to reconnect to the hc
        domainMasterLifecycleUtil.connect();
        domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());

        op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(NAME).set("running-mode");

        // Validate that we are started in the --admin-only mode
        final ModelNode result = executeForResult(masterClient, op);
        Assert.assertEquals("ADMIN_ONLY", result.asString());

        // restart back to normal mode
        op = new ModelNode();
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(OP).set("reload");
        op.get("admin-only").set(false);
        op.get("restart-servers").set(true);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);

        // Try to reconnect to the hc
        domainMasterLifecycleUtil.connect();
        domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());

        // check that the servers are up
        domainMasterLifecycleUtil.awaitServers(System.currentTimeMillis());

        // Wait for the slave to reconnect
        waitForHost(masterClient, "secondary");
        domainSlaveLifecycleUtil.awaitServers(System.currentTimeMillis());
    }

    @Test
    public void testServersCannotStartInAdminOnlyMode() throws Exception {
        final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

        // restart master HC in admin only mode
        ModelNode op = new ModelNode();
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(OP).set("reload");
        op.get("admin-only").set(true);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);

        // Try to reconnect to the hc
        domainMasterLifecycleUtil.connect();
        domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());

        op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(NAME).set("running-mode");

        // Validate that we are started in the --admin-only mode
        ModelNode result = executeForResult(masterClient, op);
        Assert.assertEquals("ADMIN_ONLY", result.asString());

        result = executeOperation(masterClient, null, START_SERVERS);
        Assert.assertTrue(result.asString(), result.get(FAILURE_DESCRIPTION).asString().contains("WFLYHC0048"));

        result = executeOperation(masterClient, PathAddress.pathAddress(SERVER_GROUP, "main-server-group"), START_SERVERS);
        Assert.assertTrue(result.asString(), result.get(FAILURE_DESCRIPTION).asString().contains("WFLYHC0048"));

        result = executeOperation(masterClient, PathAddress.pathAddress(mainOne), START);
        Assert.assertTrue(result.asString(), result.get(FAILURE_DESCRIPTION).asString().contains("WFLYHC0048"));

        // restart back to normal mode
        op = new ModelNode();
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(OP).set("reload");
        op.get("admin-only").set(false);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);

        // check that the servers are up
        domainMasterLifecycleUtil.awaitServers(System.currentTimeMillis());
    }


    private ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws IOException {
        final ModelNode result = client.execute(operation);
        return validateResponse(result);
    }

    private ModelNode validateResponse(ModelNode response) {
        return validateResponse(response, true);
    }

    private ModelNode validateResponse(ModelNode response, boolean validateResult) {

        if(! SUCCESS.equals(response.get(OUTCOME).asString())) {
            System.out.println("Failed response:");
            System.out.println(response);
            Assert.fail(response.get(FAILURE_DESCRIPTION).toString());
        }

        if (validateResult) {
            Assert.assertTrue("result exists", response.has(RESULT));
        }
        return response.get(RESULT);
    }

    private static void waitForHost(final ModelControllerClient client, final String hostName) throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(CHILD_TYPE).set(HOST);
        final ModelNode host = new ModelNode().set(hostName);
        final long timeout = 30L;
        final TimeUnit timeUnit = TimeUnit.SECONDS;
        final long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        for(;;) {
            final long remaining = deadline - System.currentTimeMillis();
            final ModelNode result = client.execute(operation);
            if(result.get(RESULT).asList().contains(host)) {
                return;
            }
            if(remaining <= 0) {
                Assert.fail(hostName + " did not register within 30 seconds");
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Assert.fail("Interrupted while waiting for registration of host " + hostName);
            }
        }
    }

    private ModelNode executeOperation(final ModelControllerClient client, PathAddress address, String opName) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(opName);
        if (address == null) {
            operation.get(OP_ADDR).setEmptyList();
        } else {
            operation.get(OP_ADDR).set(address.toModelNode());
        }
        return client.execute(operation);
    }

}
