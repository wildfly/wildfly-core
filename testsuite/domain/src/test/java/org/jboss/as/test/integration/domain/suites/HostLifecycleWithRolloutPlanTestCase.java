/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
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
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;

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
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    private DomainClient primaryClient;

    @Before
    public void setup() throws Exception {
        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
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
        ModelNode returnVal = validateResponse(primaryClient.execute(rcn));
        if (returnVal.isDefined()) {
            for (ModelNode plan : returnVal.asList()) {
                final PathAddress addr = ROLLOUT_PLANS_ADDRESS.append(ROLLOUT_PLAN, plan.asString());
                validateResponse(primaryClient.execute(Util.createRemoveOperation(addr)));
            }
        }
    }

    /**
     * Downstream error: if rollout plan is created and primary is reloaded, secondary is not able to connect to primary.
     * (https://issues.jboss.org/browse/WFCORE-3965)
     * Test, which validates this fix, has to be also in upstream, to prevent possible regression.
     */
    @Test
    public void testReloadPrimaryWithRolloutPlan() throws IOException, TimeoutException, InterruptedException {

        // Add content
        addRolloutPlan();

        ModelNode hash = readHash(primaryClient);
        assertTrue(hash.isDefined());

        reloadPrimary();

        final ModelNode secondary = new ModelNode();
        secondary.get(OP).set(READ_ATTRIBUTE_OPERATION);
        secondary.get(OP_ADDR).add(HOST, "secondary");
        secondary.get(NAME).set(HOST_STATE);

        // Wait until secondary is reconnected and running
        validateSecondaryRunning();

        // Confirm correct hash
        ModelNode secondaryHash = readHash(testSupport.getDomainSecondaryLifecycleUtil().getDomainClient());
        assertEquals(hash, secondaryHash);

    }

    /** Confirm a secondary can start when it does not have the current rollout plans in its content repository. */
    @Test
    public void testSecondaryBootWithMissingRolloutPlan() throws IOException {

        // Validate there is no rollout plan
        ModelNode origHash = readHash(primaryClient);
        assertFalse(origHash.isDefined()); // this assert isn't really required; it would be ok if there was a hash
                                          // so long as the secondary misses a change

        // No secondary when we add the plan
        testSupport.getDomainSecondaryLifecycleUtil().stop();
        validateSecondaryDisconnected();

        addRolloutPlan();

        ModelNode hash = readHash(primaryClient);
        assertTrue(hash.isDefined());
        assertNotEquals(origHash, hash);

        // Confirm the secondary can start. This is the primary thing being tested here.
        testSupport.getDomainSecondaryLifecycleUtil().start();

        // Also confirm correct hash on the secondary
        ModelNode secondaryHash = readHash(testSupport.getDomainSecondaryLifecycleUtil().getDomainClient());
        assertEquals(hash, secondaryHash);

    }

    private void addRolloutPlan() throws IOException {

        final PathAddress addressA = ROLLOUT_PLANS_ADDRESS.append(ROLLOUT_PLAN, "testPlan");

        // Add content
        ModelNode op = Util.createEmptyOperation(ADD, addressA);
        op.get(CONTENT).set(ROLLOUT_PLAN_A);
        ModelNode response = primaryClient.execute(op);
        validateResponse(response);
        response = primaryClient.execute(Util.getReadAttributeOperation(addressA, CONTENT));
        ModelNode returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_A, returnVal);

    }

    private ModelNode readHash(DomainClient client) throws IOException {
        return validateResponse(client.execute(Util.getReadAttributeOperation(ROLLOUT_PLANS_ADDRESS, HASH)));
    }

    /**
     * Invokes an op on the primary to read the secondary's host-state and confirm it is 'running',
     * polling until successful or TIMEOUT.
     * This confirms both the secondary's state and that it is properly connected to the primary.
     */
    private void validateSecondaryRunning() {

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
                final ModelNode result = primaryClient.execute(operation);
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

        fail("Cannot validate that host 'secondary' is running");
    }

    private void reloadPrimary() throws IOException {
        try {
            domainPrimaryLifecycleUtil.reload("primary", null, true);
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException("Failed waiting for host controller and servers to start.", e);
        }
    }

    private void validateSecondaryDisconnected() {

        final ModelNode operation = new ModelNode();
        operation.get(OP).set("read-children-names");
        operation.get("child-type").set("host");

        final long time = System.currentTimeMillis() + TIMEOUT;
        do {
            try {
                final ModelNode result = validateResponse(primaryClient.execute(operation));
                boolean hasSecondary = false;
                for (ModelNode host : result.asList()) {
                    if ("secondary".equals(host.asString())) {
                        hasSecondary = true;
                        break;
                    }
                }
                if (!hasSecondary) {
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

        fail("Cannot validate that host 'secondary' is disconnected");
    }
}
