/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.rbac.StandardRole;

/**
 * {@link Constraint} related to whether a resource, attribute or operation is
 * related to administrative audit logging.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class AuditConstraint extends AllowAllowNotConstraint {

    public static final ConstraintFactory FACTORY = new Factory();

    private static final AuditConstraint AUDIT = new AuditConstraint(true);
    private static final AuditConstraint NOT_AUDIT = new AuditConstraint(false);
    private static final AuditConstraint ALLOWS = new AuditConstraint(true, true);
    private static final AuditConstraint DISALLOWS = new AuditConstraint(false, true);

    private AuditConstraint(boolean isAudit) {
        super(isAudit);
    }

    private AuditConstraint(boolean allowsAudit, boolean allowsNonAudit) {
        super(allowsAudit, allowsNonAudit);
    }

    static class Factory extends AbstractConstraintFactory {

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            if (actionEffect == ActionEffect.ADDRESS) {
                return ALLOWS;
            }
            return role == StandardRole.AUDITOR || role == StandardRole.SUPERUSER ? ALLOWS : DISALLOWS;
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
            // We prefer going ahead of anything except a ScopingConstraint
            return other instanceof ScopingConstraintFactory ? 1 : -1;
        }

        @Override
        public Constraint getRequiredConstraint(ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            return NOT_AUDIT;
        }
    }
}
