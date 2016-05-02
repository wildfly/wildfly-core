/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller.access.constraint;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.rbac.StandardRole;
import org.jboss.as.controller.logging.ControllerLogger;

/**
 * Constraint related to whether the target resource has an address or ObjectName that
 * {@link java.util.regex.Pattern#matches(String, CharSequence) matches} one of a set of allowed patterns.
 *
 * @author Brian Stansberry
 */
public class PatternScopedConstraint extends AbstractConstraint implements Constraint, ScopingConstraint {


    public static final ScopingConstraintFactory FACTORY = new Factory();

    private static final PatternScopedConstraint GLOBAL_USER = new PatternScopedConstraint();

    private final boolean user;
    private final boolean global;
    private final String  target;
    private final PatternsHolder patternsHolder;
    private final boolean readOnly;
    private final PatternScopedConstraint readOnlyConstraint;

    /** Constructor for the global user constraint */
    private PatternScopedConstraint() {
        super();
        this.user = true;
        this.global = true;
        this.readOnly = false;
        this.readOnlyConstraint = null;
        this.patternsHolder = new PatternsHolder();
        this.target = null;
    }

    /** Constructor for the required constraint */
    private PatternScopedConstraint(String target) {
        super();
        this.user = false;
        this.global = false;
        this.patternsHolder = null;
        this.target = target;
        this.readOnly = false;
        this.readOnlyConstraint = null;
    }

    /** Constructor for the management layer to use when new roles are created. */
    public PatternScopedConstraint(List<Pattern> allowed) {
        super();
        this.user = true;
        this.global = false;
        this.patternsHolder = new PatternsHolder(allowed);
        this.target = null;
        this.readOnly = false;
        this.readOnlyConstraint = new PatternScopedConstraint(this.patternsHolder, true);
    }

    /**
     * Creates the constraint the standard constraint will return from {@link #getOutofScopeReadConstraint()}
     * Only call from {@link PatternScopedConstraint#PatternScopedConstraint(java.util.List)}
     */
    private PatternScopedConstraint(PatternsHolder patternsHolder, boolean readOnly) {
        super();
        this.user = true;
        this.global = false;
        this.patternsHolder = patternsHolder;
        this.target = null;
        this.readOnly = readOnly;
        this.readOnlyConstraint = null;
    }

    public void setAllowedPatterns(List<Pattern> allowed) {
        assert !global : "constraint is global";
        assert readOnlyConstraint != null : "invalid cast";
        this.patternsHolder.patterns = new LinkedHashSet<>(allowed);
    }

    @Override
    public boolean violates(Constraint other, Action.ActionEffect actionEffect) {
        if (other instanceof PatternScopedConstraint) {
            PatternScopedConstraint psc = (PatternScopedConstraint) other;
            if (user) {
                assert !psc.user : "illegal comparison";
                if (!readOnly) {
                    if (!global) {
                        if (actionEffect == Action.ActionEffect.WRITE_RUNTIME || actionEffect == Action.ActionEffect.WRITE_CONFIG) {
                            Set<Pattern> ourPatterns = patternsHolder.patterns;
                            String theTarget = psc.target;
                            //  Ok as long as one of our patterns match
                            boolean anyMatch = anyMatch(ourPatterns, theTarget);
                            if (!anyMatch) {
                                ControllerLogger.ACCESS_LOGGER.tracef("pattern constraint violated " +
                                                "for action %s due to no match between target and allowed patterns",
                                        actionEffect);
                            }
                            return !anyMatch;
                        } // else non-writes are ok; fall through
                    } // else global user is ok for everything
                } // else any read is ok; fall through
            } else {
                assert psc.user : "illegal comparison";
                return other.violates(this, actionEffect);
            }
        }
        return false;
    }

    private boolean anyMatch(Set<Pattern> ourSpecific, String target) {

        boolean matched = false;
        if (target != null) {
            for (Pattern pattern : ourSpecific) {
                if (pattern.matcher(target).matches()) {
                    matched = true;
                    break;
                }
            }
        }
        return matched;
    }


    @Override
    public boolean replaces(Constraint other) {
        return other instanceof PatternScopedConstraint && (readOnly || readOnlyConstraint != null);
    }

    // Scoping Constraint

    @Override
    public ScopingConstraintFactory getFactory() {
        assert readOnlyConstraint != null : "invalid cast";
        return FACTORY;
    }

    @Override
    public Constraint getStandardConstraint() {
        assert readOnlyConstraint != null : "invalid cast";
        return this;
    }

    @Override
    public Constraint getOutofScopeReadConstraint() {
        assert readOnlyConstraint != null : "invalid cast";
        return readOnlyConstraint;
    }

    private static class PatternsHolder {
        private volatile Set<Pattern> patterns = new LinkedHashSet<>();
        private PatternsHolder() {
            // no-op
        }
        private PatternsHolder(Collection<Pattern> patterns) {
            this.patterns.addAll(patterns);
        }
    }

    private static class Factory extends AbstractConstraintFactory implements ScopingConstraintFactory {

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            return GLOBAL_USER;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return new PatternScopedConstraint(target.getTargetResource().getResourceAddress().toCLIStyleString());
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return new PatternScopedConstraint(target.getResourceAddress().toCLIStyleString());
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            ObjectName on = target.getObjectName();
            return new PatternScopedConstraint(on == null ? null : on.getCanonicalName());
        }

        @Override
        protected int internalCompare(AbstractConstraintFactory other) {
            // We prefer going first
            return this.equals(other) ? 0 : -1;
        }
    }
}
