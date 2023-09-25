/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class ConcurrentRolloutPlanGroup implements RolloutPlanGroup {

    private final List<SingleRolloutPlanGroup> groups = new ArrayList<SingleRolloutPlanGroup>();

    public void addGroup(RolloutPlanGroup group) {
        checkNotNullParam("group", group);
        if(!(group instanceof SingleRolloutPlanGroup)) {
            throw new IllegalArgumentException("Expected a single group but got " + group);
        }
        groups.add((SingleRolloutPlanGroup) group);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.impl.RolloutPlanGroup#toModelNode()
     */
    @Override
    public ModelNode toModelNode() throws CommandFormatException {
        ModelNode node = new ModelNode();
        ModelNode groupsNode = node.get(Util.CONCURRENT_GROUPS);
        for(SingleRolloutPlanGroup group : groups) {
            groupsNode.get(group.getGroupName()).set(group.toModelNode());
        }
        return node;
    }

    @Override
    public void addTo(ModelNode inSeries) throws CommandFormatException {
        inSeries.add().set(toModelNode());
    }

/*    public static void main(String[] args) throws Exception {
        ConcurrentRolloutPlanGroup concurrent = new ConcurrentRolloutPlanGroup();

        SingleRolloutPlanGroup group = new SingleRolloutPlanGroup("groupA");
        group.addProperty("rolling-to-servers", "true");
        group.addProperty("max-failure-percentage", "20");
        concurrent.addGroup(group);

        concurrent.addGroup(new SingleRolloutPlanGroup("groupB"));

        System.out.println(concurrent.toModelNode());
    }
*/}
