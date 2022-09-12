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

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests of HC lifecycle behavior when rollout plans are included in domain.xml.
 *
 * @author <a href="mailto:jondruse@jboss.com">Jiri Ondrusek</a>
 */
public class HostLifecycleWithRolloutPlanTestCase {

    private static final int TIMEOUT = 30000;

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    private static final PathAddress ROLLOUT_PLANS_ADDRESS = PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
    private static final ModelNode ROLLOUT_PLAN_A = new ModelNode();

    static {
        ModelNode main = new ModelNode();
        main.get("main-server-group");
        ROLLOUT_PLAN_A.get(ROLLOUT_PLAN, IN_SERIES).add().get("server-group").set(main);
        ROLLOUT_PLAN_A.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(HostLifecycleWithRolloutPlanTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    private DomainClient masterClient;

    @Before
    public void setup() throws Exception {
        masterClient = domainMasterLifecycleUtil.getDomainClient();
        cleanRolloutPlans();
    }

    @After
    public void tearDown() throws IOException {
        cleanRolloutPlans();
    }

    private void cleanRolloutPlans() throws IOException {
        ModelNode params = new ModelNode();
        params.get("child-type").set("rollout-plan");
        ModelNode rcn = Util.getOperation("read-children-names", ROLLOUT_PLANS_ADDRESS, params);
        ModelNode returnVal = validateResponse(masterClient.execute(rcn));
        if (returnVal.isDefined()) {
            for (ModelNode plan : returnVal.asList()) {
                final PathAddress addr = ROLLOUT_PLANS_ADDRESS.append(ROLLOUT_PLAN, plan.asString());
                validateResponse(masterClient.execute(Util.createRemoveOperation(addr)));
            }
        }
    }

    /**
     * Downstream error: if rollout plan is created and master is reloaded, slave is not able to connect to master.
     * (https://issues.jboss.org/browse/WFCORE-3965)
     * Test, which validates this fix, has to be also in upstream, to prevent possible regression.
     */
    @Test
    public void testReloadMasterWithRolloutPlan() throws IOException, TimeoutException, InterruptedException {

        // Add content
        addRolloutPlan();

        ModelNode hash = readHash(masterClient);
        assertTrue(hash.isDefined());

        reloadMaster();

        final ModelNode slave = new ModelNode();
        slave.get(OP).set(READ_ATTRIBUTE_OPERATION);
        slave.get(OP_ADDR).add(HOST, "secondary");
        slave.get(NAME).set(HOST_STATE);

        // Wait until slave is reconnected and running
        validateSlaveRunning();

        // Confirm correct hash
        ModelNode slaveHash = readHash(testSupport.getDomainSlaveLifecycleUtil().getDomainClient());
        assertEquals(hash, slaveHash);

    }

    /** Confirm a slave can start when it does not have the current rollout plans in its content repository. */
    @Test
    public void testSlaveBootWithMissingRolloutPlan() throws IOException {

        // Validate there is no rollout plan
        ModelNode origHash = readHash(masterClient);
        assertFalse(origHash.isDefined()); // this assert isn't really required; it would be ok if there was a hash
                                          // so long as the slave misses a change

        // No slave when we add the plan
        testSupport.getDomainSlaveLifecycleUtil().stop();
        validateSlaveDisconnected();

        addRolloutPlan();

        ModelNode hash = readHash(masterClient);
        assertTrue(hash.isDefined());
        assertNotEquals(origHash, hash);

        // Confirm the slave can start. This is the primary thing being tested here.
        testSupport.getDomainSlaveLifecycleUtil().start();

        // Also confirm correct hash on the slave
        ModelNode slaveHash = readHash(testSupport.getDomainSlaveLifecycleUtil().getDomainClient());
        assertEquals(hash, slaveHash);

    }

    private void addRolloutPlan() throws IOException {

        final PathAddress addressA = ROLLOUT_PLANS_ADDRESS.append(ROLLOUT_PLAN, "testPlan");

        // Add content
        ModelNode op = Util.createEmptyOperation(ADD, addressA);
        op.get(CONTENT).set(ROLLOUT_PLAN_A);
        ModelNode response = masterClient.execute(op);
        validateResponse(response);
        response = masterClient.execute(Util.getReadAttributeOperation(addressA, CONTENT));
        ModelNode returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_A, returnVal);

    }

    private ModelNode readHash(DomainClient client) throws IOException {
        return validateResponse(client.execute(Util.getReadAttributeOperation(ROLLOUT_PLANS_ADDRESS, HASH)));
    }

    /**
     * Invokes an op on the master to read the slave's host-state and confirm it is 'running',
     * polling until successful or TIMEOUT.
     * This confirms both the slave's state and that it is properly connected to the master.
     */
    private void validateSlaveRunning() {

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).add(HOST, "secondary");
        operation.get(NAME).set(HOST_STATE);

        final long time = System.currentTimeMillis() + TIMEOUT;
        do {
            try {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final ModelNode result = masterClient.execute(operation);
                if (result.get(OUTCOME).asString().equals(SUCCESS)) {
                    final ModelNode model = result.require(RESULT);
                    if (model.asString().equalsIgnoreCase("running")) {
                        return;
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        } while (System.currentTimeMillis() < time);

        fail("Cannot validate that host 'slave' is running");
    }

    private void reloadMaster() throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set("reload");
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(ADMIN_ONLY).set(false);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);
        // Try to reconnect to the hc
        domainMasterLifecycleUtil.connect();
        try {
            domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());
            domainMasterLifecycleUtil.awaitServers(System.currentTimeMillis());
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException("Failed waiting for host controller and servers to start.", e);
        }
    }

    private void validateSlaveDisconnected() {

        final ModelNode operation = new ModelNode();
        operation.get(OP).set("read-children-names");
        operation.get("child-type").set("host");

        final long time = System.currentTimeMillis() + TIMEOUT;
        do {
            try {
                final ModelNode result = validateResponse(masterClient.execute(operation));
                boolean hasSlave = false;
                for (ModelNode host : result.asList()) {
                    if ("secondary".equals(host.asString())) {
                        hasSlave = true;
                        break;
                    }
                }
                if (!hasSlave) {
                    return;
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        } while (System.currentTimeMillis() < time);

        fail("Cannot validate that host 'slave' is disconnected");
    }
}
