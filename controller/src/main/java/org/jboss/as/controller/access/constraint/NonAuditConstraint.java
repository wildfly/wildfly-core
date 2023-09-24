/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.rbac.StandardRole;

/**
 * {@link Constraint} related to whether a resource, attribute or operation is NOT
 * related to administrative audit logging.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class NonAuditConstraint extends AllowAllowNotConstraint {

    public static final ConstraintFactory FACTORY = new Factory();

    private static final NonAuditConstraint AUDIT = new NonAuditConstraint(true);
    private static final NonAuditConstraint NOT_AUDIT = new NonAuditConstraint(false);
    private static final NonAuditConstraint ALLOWS = new NonAuditConstraint(true, true);
    private static final NonAuditConstraint DISALLOWS = new NonAuditConstraint(true, false);

    private NonAuditConstraint(boolean isAudit) {
        super(isAudit);
    }

    private NonAuditConstraint(boolean allowsAudit, boolean allowsNonAudit) {
        super(allowsAudit, allowsNonAudit);
    }

    private static class Factory extends AbstractConstraintFactory {

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            return role == StandardRole.AUDITOR
                    && (actionEffect == Action.ActionEffect.WRITE_CONFIG
                         || actionEffect == Action.ActionEffect.WRITE_RUNTIME)
                    ? DISALLOWS : ALLOWS;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return getRequiredConstraint(actionEffect, action, target.getTargetResource());
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return isAuditResource(target) ? AUDIT : NOT_AUDIT;
        }

        private boolean isAuditResource(TargetResource target) {
            return AuditLogAddressUtil.isAuditLogAddress(target.getResourceAddress());
        }

        @Override
        protected int internalCompare(AbstractConstraintFactory other) {
            // We prefer going ahead of anything except a ScopingConstraint or AuditConstraint
            return (other instanceof ScopingConstraintFactory || other instanceof AuditConstraint.Factory)  ? 1 : -1;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            return NOT_AUDIT;
        }
    }
}
