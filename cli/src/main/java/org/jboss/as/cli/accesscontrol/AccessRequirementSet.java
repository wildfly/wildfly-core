/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author Alexey Loubyansky
 *
 */
public abstract class AccessRequirementSet extends BaseAccessRequirement {

    protected List<AccessRequirement> requirements = Collections.emptyList();

    public void add(AccessRequirement requirement) {
        checkNotNullParam("requirement", requirement);

        if(requirements.isEmpty()) {
            requirements = Collections.singletonList(requirement);
        } else {
            if (requirements.size() == 1) {
                final List<AccessRequirement> tmp = requirements;
                requirements = new ArrayList<AccessRequirement>();
                requirements.addAll(tmp);
            }
            requirements.add(requirement);
        }
    }

    private String toString;
    @Override
    public String toString() {
        if(toString == null) {
            toString = requirements.toString();
        }
        return toString;
    }
}
