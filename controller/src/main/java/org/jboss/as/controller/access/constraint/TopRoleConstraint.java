/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access.constraint;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.rbac.StandardRole;

/**
 * A {@link Constraint} for writing to the top level standard roles i.e. 'Auditor' and 'SuperUser'.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class TopRoleConstraint extends AllowAllowNotConstraint {

    private static final PathElement MGMT_ELEMENT = PathElement.pathElement(CORE_SERVICE, MANAGEMENT);
    private static final PathElement AUTHZ_ELEMENT = PathElement.pathElement(ACCESS, AUTHORIZATION);

    public static final ConstraintFactory FACTORY = new Factory();

    private static final TopRoleConstraint TOP_ROLE = new TopRoleConstraint(true);
    private static final TopRoleConstraint NOT_TOP_ROLE = new TopRoleConstraint(false);
    private static final TopRoleConstraint ALLOWS = new TopRoleConstraint(true, true);
    private static final TopRoleConstraint DISALLOWS = new TopRoleConstraint(false, true);

    private TopRoleConstraint(boolean isTopRole) {
        super(isTopRole);
    }

    private TopRoleConstraint(boolean allowsTopRole, boolean allowsNonTopRole) {
        super(allowsTopRole, allowsNonTopRole);
    }

    static class Factory extends AbstractConstraintFactory {

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            if (actionEffect != ActionEffect.WRITE_CONFIG && actionEffect != ActionEffect.WRITE_RUNTIME) {
                return ALLOWS;
            }
            return role == StandardRole.SUPERUSER ? ALLOWS : DISALLOWS;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return getRequiredConstraint(actionEffect, action, target.getTargetResource());
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return isTopRole(target) ? TOP_ROLE : NOT_TOP_ROLE;
        }

        private boolean isTopRole(TargetResource target) {
            return isTopRole(target.getResourceAddress());
        }

        private boolean isTopRole(PathAddress address) {
            if (address.size() >= 3) {
                PathElement roleMapping;
                if (MGMT_ELEMENT.equals(address.getElement(0)) && AUTHZ_ELEMENT.equals(address.getElement(1))
                        && ROLE_MAPPING.equals((roleMapping = address.getElement(2)).getKey())) {
                    String roleName = roleMapping.getValue();
                    return StandardRole.AUDITOR.name().equalsIgnoreCase(roleName)
                            || StandardRole.SUPERUSER.name().equalsIgnoreCase(roleName);
                }
            }

            return false;
        }

        @Override
        protected int internalCompare(AbstractConstraintFactory other) {
            return 0;
        }

        @Override
        public Constraint getRequiredConstraint(ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            return NOT_TOP_ROLE;
        }
    }

}
