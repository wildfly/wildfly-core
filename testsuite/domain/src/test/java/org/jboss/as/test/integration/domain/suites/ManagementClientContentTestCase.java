/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ACROSS_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateFailedResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of using the domain content repo for storing and accessing management client content.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagementClientContentTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static long key = System.currentTimeMillis();

    private static final ModelNode ROLLOUT_PLANS_ADDRESS = new ModelNode().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
    private static final ModelNode ROLLOUT_PLAN_A = new ModelNode();
    private static final ModelNode ROLLOUT_PLAN_B = new ModelNode();
    private static final ModelNode ROLLOUT_PLAN_C = new ModelNode();

    static {
        ROLLOUT_PLANS_ADDRESS.protect();
        ModelNode main = new ModelNode();
        main.get("main-server-group");
        ModelNode other = new ModelNode();
        other.get("other-server-group");
        ROLLOUT_PLAN_A.get(ROLLOUT_PLAN, IN_SERIES).add().get("server-group").set(main);
        ROLLOUT_PLAN_A.protect();
        ROLLOUT_PLAN_B.get(ROLLOUT_PLAN, IN_SERIES).add().get("concurrent-groups").set(other);
        ROLLOUT_PLAN_B.protect();
        ROLLOUT_PLAN_C.get(ROLLOUT_PLAN, IN_SERIES).add().get("server-group").set(main);
        ROLLOUT_PLAN_C.get(ROLLOUT_PLAN, ROLLBACK_ACROSS_GROUPS).set(false);
        ROLLOUT_PLAN_C.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ManagementClientContentTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    private DomainClient primaryClient;
    private DomainClient secondaryClient;

    @Before
    public void setup() throws Exception {
        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
    }

    @Test
    public void testRolloutPlans() throws IOException {

        final String planA = getContentName();
        final ModelNode addressA = getContentAddress(ROLLOUT_PLANS_ADDRESS, ROLLOUT_PLAN, planA);
        final String planB = getContentName();
        final ModelNode addressB = getContentAddress(ROLLOUT_PLANS_ADDRESS, ROLLOUT_PLAN, planB);

        // Check overall hashes match on primary and secondary
        ModelNode overallHash = validateHashes(ROLLOUT_PLANS_ADDRESS, null, false);

        // Add content

        ModelNode op = Util.getEmptyOperation(ADD, addressA);
        op.get(CONTENT).set(ROLLOUT_PLAN_A);
        ModelNode response = primaryClient.execute(op);
        validateResponse(response);
        response = primaryClient.execute(getReadAttributeOperation(addressA, CONTENT));
        ModelNode returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_A, returnVal);

        // Confirm plan hashes match on primary and secondary
        validateHashes(addressA, new ModelNode(), true);
        // Check overall hashes match on primary and secondary
        overallHash = validateHashes(ROLLOUT_PLANS_ADDRESS, overallHash, true);

        // Add another

        op = Util.getEmptyOperation(ADD, addressB);
        op.get(CONTENT).set(ROLLOUT_PLAN_B);
        response = primaryClient.execute(op);
        validateResponse(response);
        response = primaryClient.execute(getReadAttributeOperation(addressB, CONTENT));
        returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_B, returnVal);

        // Confirm plan hashes match on primary and secondary
        validateHashes(addressB, new ModelNode(), true);
        // Check overall hashes match on primary and secondary
        overallHash = validateHashes(ROLLOUT_PLANS_ADDRESS, overallHash, true);

        // Validate read-children names

        op = Util.getEmptyOperation(READ_CHILDREN_NAMES_OPERATION, ROLLOUT_PLANS_ADDRESS);
        op.get(CHILD_TYPE).set(ROLLOUT_PLAN);
        response = primaryClient.execute(op);
        returnVal = validateResponse(response);
        List<ModelNode> plans = returnVal.asList();
        assertEquals(2, plans.size());
        for (ModelNode node : plans) {
            if (!planA.equals(node.asString())) {
                assertEquals(planB, node.asString());
            }
        }

        // Simple write-attribute

        op = Util.getEmptyOperation(WRITE_ATTRIBUTE_OPERATION, addressB);
        op.get(NAME).set(CONTENT);
        op.get(VALUE).set(ROLLOUT_PLAN_C);

        response = primaryClient.execute(op);
        validateResponse(response);
        response = primaryClient.execute(getReadAttributeOperation(addressB, CONTENT));
        returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_C, returnVal);

        // Confirm plan hashes match on primary and secondary
        ModelNode planBHash = validateHashes(addressB, new ModelNode(), true);
        // Check overall hashes match on primary and secondary
        overallHash = validateHashes(ROLLOUT_PLANS_ADDRESS, overallHash, true);

        // Store op

        op = Util.getEmptyOperation("store", addressB);
        op.get(HASH).set(planBHash);
        op.get(CONTENT).set(ROLLOUT_PLAN_B);

        response = primaryClient.execute(op);
        validateResponse(response);
        response = primaryClient.execute(getReadAttributeOperation(addressB, CONTENT));
        returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_B, returnVal);

        // Confirm plan hashes match on primary and secondary
        planBHash = validateHashes(addressB, planBHash, true);
        // Check overall hashes match on primary and secondary
        overallHash = validateHashes(ROLLOUT_PLANS_ADDRESS, overallHash, true);

        // Failed store op (wrong value in hash param)

        op = Util.getEmptyOperation("store", addressB);
        op.get(HASH).set(new byte[20]); // incorrect value
        op.get(CONTENT).set(ROLLOUT_PLAN_B);

        response = primaryClient.execute(op);
        validateFailedResponse(response);
        response = primaryClient.execute(getReadAttributeOperation(addressB, CONTENT));
        returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_B, returnVal);

        // Confirm plan hashes match on primary and secondary
        validateHashes(addressB, planBHash, false);
        // Check overall hashes match on primary and secondary
        overallHash = validateHashes(ROLLOUT_PLANS_ADDRESS, overallHash, false);

        // Remove plan

        op = Util.getEmptyOperation(REMOVE, addressB);
        response = primaryClient.execute(op);
        validateResponse(response);

        // Check overall hashes match on primary and secondary
        overallHash = validateHashes(ROLLOUT_PLANS_ADDRESS, overallHash, true);
    }

    private ModelNode validateHashes(ModelNode address, ModelNode currentHash, boolean expectChange) throws IOException {

        // Start with reads of the root resource
        ModelNode response = primaryClient.execute(getReadAttributeOperation(address, HASH));
        ModelNode overallHash = validateResponse(response);

        response = secondaryClient.execute(getReadAttributeOperation(address, HASH));
        ModelNode secondaryOverallHash = validateResponse(response);

        Assert.assertEquals(overallHash, secondaryOverallHash);

        if (currentHash != null) {
            if (expectChange) {
                assertFalse(overallHash.equals(currentHash));
            } else {
                assertTrue(overallHash.equals(currentHash));
            }
        }

        return overallHash;
    }

    private ModelNode getContentAddress(final ModelNode parentAddress, final String type, final String name) {
        return parentAddress.clone().add(type, name);
    }

    private String getContentName() {
        final String result = getClass().getSimpleName() + key;
        key++;
        return result;
    }

    private static ModelNode getReadAttributeOperation(ModelNode address, String attribute) {
        ModelNode result = getEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
        result.get(NAME).set(attribute);
        return result;
    }

    private static ModelNode getEmptyOperation(String operationName, ModelNode address) {
        ModelNode op = new ModelNode();
        op.get(OP).set(operationName);
        if (address != null) {
            op.get(OP_ADDR).set(address);
        }
        else {
            // Just establish the standard structure; caller can fill in address later
            op.get(OP_ADDR);
        }
        return op;
    }
}
