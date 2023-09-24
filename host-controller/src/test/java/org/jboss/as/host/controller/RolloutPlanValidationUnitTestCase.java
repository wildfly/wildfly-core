/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONCURRENT_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILED_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILURE_PERCENTAGE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ACROSS_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLING_TO_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.domain.controller.resources.DomainRootDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class RolloutPlanValidationUnitTestCase {

    @Test
    public void testMissingInSeries() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        rolloutPlan.get(ROLLOUT_PLAN);
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("Rollout plan is missing in-series");
        } catch(OperationFailedException e) {
            // expected
        }
    }

    @Test
    public void testTooManyChildren() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        inSeries.add().get(SERVER_GROUP).get("group1");
        rolloutPlan.get(ROLLOUT_PLAN, ROLLBACK_ACROSS_GROUPS).set(true);
        rolloutPlan.get(ROLLOUT_PLAN, "unrecognized");
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("Rollout plan has too many children");
        } catch(OperationFailedException e) {
            // expected
        }
    }

    @Test
    public void testUnrecorgnizedChild() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        inSeries.add().get(SERVER_GROUP).get("group1");
        rolloutPlan.get(ROLLOUT_PLAN, "unrecognized");
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("Rollout plan has unrecognized child.");
        } catch(OperationFailedException e) {
            // expected
        }
    }

    @Test
    public void testInSeriesNotDefined() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("in-series undefined");
        } catch(OperationFailedException e) {
            // expected
        }
    }

    @Test
    public void testServerGroupUndefined() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        inSeries.add().get(SERVER_GROUP);
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("server-group undefined");
        } catch(OperationFailedException e) {
            // expected
        }
    }

    @Test
    public void testServerGroupMissingName() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        inSeries.add().get(SERVER_GROUP).setEmptyObject();
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("server-group name missing");
        } catch(OperationFailedException e) {
            // expected
        }
    }

    @Test
    public void testServerGroupOnlyName() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        inSeries.add().get(SERVER_GROUP).get("group1");
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testServerGroupTwoManyNames() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        ModelNode serverGroup = inSeries.add().get(SERVER_GROUP);
        serverGroup.get("group1");
        serverGroup.get("group2");
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("server-group has too many names");
        } catch(OperationFailedException e) {
            // expected
        }
    }

    @Test
    public void testServerGroupWithMaxFailurePercentage() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode group = inSeries.add().get(SERVER_GROUP).get("group1");
        group.get(MAX_FAILURE_PERCENTAGE).set(10);
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testServerGroupWithMaxFailedServers() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode group = inSeries.add().get(SERVER_GROUP).get("group1");
        group.get(MAX_FAILED_SERVERS).set(10);
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testServerGroupWithRollingToServers() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode group = inSeries.add().get(SERVER_GROUP).get("group1");
        group.get(ROLLING_TO_SERVERS).set(true);
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testServerGroupWithUnrecognizedProp() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode group = inSeries.add().get(SERVER_GROUP).get("group1");
        group.get("unrecognized").set(true);
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("unrecognized property");
        } catch(OperationFailedException expected) {
        }
    }

    @Test
    public void testServerGroupWithAllProps() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode group = inSeries.add().get(SERVER_GROUP).get("group1");
        group.get(ROLLING_TO_SERVERS).set(true);
        group.get(MAX_FAILURE_PERCENTAGE).set(1);
        group.get(MAX_FAILED_SERVERS).set(1);
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testEmptyConcurrentGroups() throws Exception {
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        inSeries.add().get(CONCURRENT_GROUPS);
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("concurrent groups is empty");
        } catch(OperationFailedException expected) {
        }
    }

    @Test
    public void testConcurrentGroupsWithUndefinedGroup() throws Exception {
        // this doesn't make sense actually
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode concurrent = inSeries.add().get(CONCURRENT_GROUPS);
        concurrent.get("group1");
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testConcurrentGroupsWithTwoUndefinedGroups() throws Exception {
        // this doesn't make sense actually
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode concurrent = inSeries.add().get(CONCURRENT_GROUPS);
        concurrent.get("group1");
        concurrent.get("group2");
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testConcurrentGroupsWithProps() throws Exception {
        // this doesn't make sense actually
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode concurrent = inSeries.add().get(CONCURRENT_GROUPS);
        concurrent.get("group1");
        final ModelNode group = concurrent.get("group2");
        group.get(ROLLING_TO_SERVERS).set(true);
        group.get(MAX_FAILURE_PERCENTAGE).set(1);
        group.get(MAX_FAILED_SERVERS).set(1);
        validateRolloutPlanStructure(rolloutPlan);
    }

    @Test
    public void testConcurrentGroupsWithUnrecognizedProp() throws Exception {
        // this doesn't make sense actually
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        final ModelNode concurrent = inSeries.add().get(CONCURRENT_GROUPS);
        concurrent.get("group1");
        final ModelNode group = concurrent.get("group2");
        group.get(ROLLING_TO_SERVERS).set(true);
        group.get(MAX_FAILURE_PERCENTAGE).set(1);
        group.get("unrecognized").set(1);
        try {
            validateRolloutPlanStructure(rolloutPlan);
            Assert.fail("unrecognized prop");
        } catch(OperationFailedException expected) {}
    }

    @Test
    public void testMix() throws Exception {
        // this doesn't make sense actually
        final ModelNode rolloutPlan = new ModelNode();
        final ModelNode inSeries = rolloutPlan.get(ROLLOUT_PLAN, IN_SERIES);
        ModelNode concurrent = inSeries.add().get(CONCURRENT_GROUPS);
        ModelNode group = concurrent.get("groupA");
        group.get(ROLLING_TO_SERVERS).set(true);
        group.get(MAX_FAILURE_PERCENTAGE).set(20);
        concurrent.get("groupB");

        group = inSeries.add().get(SERVER_GROUP).get("groupC");
        group.get(ROLLING_TO_SERVERS).set(false);
        group.get(MAX_FAILED_SERVERS).set(1);

        concurrent = inSeries.add().get(CONCURRENT_GROUPS);
        group = concurrent.get("groupD");
        group.get(ROLLING_TO_SERVERS).set(true);
        group.get(MAX_FAILURE_PERCENTAGE).set(20);
        concurrent.get("groupE");

        inSeries.add().get(SERVER_GROUP).get("groupF");

        validateRolloutPlanStructure(rolloutPlan);
    }

    private void validateRolloutPlanStructure(ModelNode rolloutPlan) throws OperationFailedException {
        new DomainRootDefinition.RolloutPlanValidator().validateParameter("plan", rolloutPlan);
    }
}
