/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.util;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONCURRENT_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILED_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILURE_PERCENTAGE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ACROSS_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLING_TO_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.dmr.ModelNode;


/**
 *
 * Helper class building rollout plan model node.
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class RolloutPlanBuilder  {

    List<Object> series = new ArrayList<>();

    public static class RolloutPolicy {
        private final boolean rollingToServers;
        private final Integer maxFailurePercentage;
        private final Integer maxFailedServers;

        public RolloutPolicy(boolean rollingToServers, Integer maxFailurePercentage, Integer maxFailedServers) {
            this.rollingToServers = rollingToServers;
            this.maxFailurePercentage = maxFailurePercentage;
            this.maxFailedServers = maxFailedServers;
        }

        public ModelNode toModelNode() {
            ModelNode node = new ModelNode();

            node.get(ROLLING_TO_SERVERS).set(rollingToServers);
            if (maxFailedServers != null) node.get(MAX_FAILED_SERVERS).set(maxFailedServers);
            if (maxFailurePercentage != null) node.get(MAX_FAILURE_PERCENTAGE).set(maxFailurePercentage);

            return node;
        }

    }

    private boolean rollBackAcrossGroups = false;

    public void addGroup(String groupName, RolloutPolicy policy) {
        series.add(new Object[] {groupName, policy});
    }

    public void addConcurrentGroups(Map<String, RolloutPolicy> concurrentGroups) {
        series.add(concurrentGroups);
    }

    public String buildAsString() {
        ModelNode plan = build();
        String planString = plan.toString();
        planString = planString.replace("\n", " ");
        return planString;

    }
    @SuppressWarnings("unchecked")
    public ModelNode build() {
        ModelNode plan = new ModelNode();

        for (Object step : series) {
            if (step instanceof Object[]) {
                // single group
                Object[] pair = (Object[]) step;
                String groupName = (String) pair[0];
                RolloutPolicy policy = (RolloutPolicy) pair[1];
                ModelNode group = new ModelNode();
                group.get(groupName).set(policy.toModelNode());
                ModelNode serverGroup = new ModelNode();
                serverGroup.get(SERVER_GROUP).set(group);
                plan.get(IN_SERIES).add(serverGroup);
            } else {
                // concurrent groups
                Map<String, RolloutPolicy> concurrentGroups = (Map<String, RolloutPolicy>) step;
                ModelNode groups = new ModelNode();
                for(String groupName : concurrentGroups.keySet()) {
                    ModelNode group = new ModelNode();
                    group.get(groupName).set(concurrentGroups.get(groupName).toModelNode());
                    groups.get(groupName).set(group);
                }
                ModelNode concurrentGroupsNode = new ModelNode();
                concurrentGroupsNode.get(CONCURRENT_GROUPS).set(groups);
                plan.get(IN_SERIES).add(concurrentGroupsNode);
            }

        }

        plan.get(ROLLBACK_ACROSS_GROUPS).set(rollBackAcrossGroups);

        ModelNode rolloutPlan = new ModelNode();
        rolloutPlan.get(ROLLOUT_PLAN).set(plan);

        return rolloutPlan;
    }

    /**
     * @param rollBackAcrossGroups the rollBackAcrossGroups to set
     */
    public void setRollBackAcrossGroups(boolean rollBackAcrossGroups) {
        this.rollBackAcrossGroups = rollBackAcrossGroups;
    }

}
