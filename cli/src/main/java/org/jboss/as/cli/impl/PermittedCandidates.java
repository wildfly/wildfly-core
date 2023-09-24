/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.accesscontrol.AccessRequirement;

/**
 * @author Alexey Loubyansky
 *
 */
public abstract class PermittedCandidates implements DefaultCompleter.CandidatesProvider {

    public static class ValueWithAccessRequirement {

        private final String value;
        private final AccessRequirement requirement;

        public ValueWithAccessRequirement(String value, AccessRequirement requirement) {
            this.value = checkNotNullParam("value", value);
            this.requirement = checkNotNullParam("requirement", requirement);
        }

        void visit(CommandContext ctx, List<String> allowed) {
            if(requirement.isSatisfied(ctx)) {
                allowed.add(value);
            }
        }
    }

    private static class StaticPermittedCandidates extends PermittedCandidates {

        private final List<ValueWithAccessRequirement> values = new ArrayList<ValueWithAccessRequirement>();

        @Override
        protected List<ValueWithAccessRequirement> getValues(CommandContext ctx) {
            return values;
        }

        @Override
        protected void add(ValueWithAccessRequirement value) {
            values.add(value);
        }
    }

    public static PermittedCandidates create(String value, AccessRequirement requirement) {
        final PermittedCandidates provider = new StaticPermittedCandidates();
        return provider.add(value, requirement);
    }

    protected abstract List<ValueWithAccessRequirement> getValues(CommandContext ctx);

    protected abstract void add(ValueWithAccessRequirement value);

    public PermittedCandidates add(String value, AccessRequirement requirement) {
        add(new ValueWithAccessRequirement(value, requirement));
        return this;
    }

    @Override
    public Collection<String> getAllCandidates(CommandContext ctx) {
        final List<String> allowed = new ArrayList<String>();
        if(ctx.getConfig().isAccessControl()) {
            for(ValueWithAccessRequirement value : getValues(ctx)) {
                value.visit(ctx, allowed);
            }
        } else {
            for(ValueWithAccessRequirement value : getValues(ctx)) {
                allowed.add(value.value);
            }
        }
        return allowed;
    }
}
