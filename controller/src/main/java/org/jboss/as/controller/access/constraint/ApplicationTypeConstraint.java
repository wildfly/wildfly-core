/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.rbac.StandardRole;

/**
 * @link Constraint} related to whether a resource, attribute or operation is related to an application.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class ApplicationTypeConstraint extends AllowAllowNotConstraint {

    public static final ApplicationTypeConstraint.Factory FACTORY = new Factory();

    private static final ApplicationTypeConstraint APPLICATION = new ApplicationTypeConstraint(true);
    private static final ApplicationTypeConstraint NON_APPLICATION = new ApplicationTypeConstraint(false);

    private ApplicationTypeConstraint(boolean isApplication) {
        super(isApplication);
    }

    private ApplicationTypeConstraint(boolean allowsApplication, boolean allowsNonApplication) {
        super(allowsApplication, allowsNonApplication);
    }

    public static class Factory extends AbstractConstraintFactory {

        private final Map<ApplicationTypeConfig.Key, ApplicationTypeConfig> typeConfigs =
                Collections.synchronizedMap(new HashMap<ApplicationTypeConfig.Key, ApplicationTypeConfig>());

        /** Singleton */
        private Factory() {}

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            boolean allowsNonApplication = role != StandardRole.DEPLOYER
                    || (actionEffect != Action.ActionEffect.WRITE_CONFIG && actionEffect != Action.ActionEffect.WRITE_RUNTIME);
            return new ApplicationTypeConstraint(true, allowsNonApplication);
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return (isApplicationType(target.getTargetResource())
                    || isApplicationType(action)
                    || isApplicationType(target)) ? APPLICATION : NON_APPLICATION;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return (isApplicationType(action) || isApplicationType(target)) ? APPLICATION : NON_APPLICATION;
        }

        private boolean isApplicationType(Action action) {
            for (AccessConstraintDefinition constraintDefinition : action.getAccessConstraints()) {
                if (constraintDefinition instanceof ApplicationTypeAccessConstraintDefinition) {
                    ApplicationTypeAccessConstraintDefinition atcd = (ApplicationTypeAccessConstraintDefinition) constraintDefinition;
                    ApplicationTypeConfig atc = atcd.getApplicationTypeConfig();
                    if (atc.isApplicationType()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isApplicationType(TargetAttribute target) {
            for (AccessConstraintDefinition constraintDefinition : target.getAccessConstraints()) {
                if (constraintDefinition instanceof ApplicationTypeAccessConstraintDefinition) {
                    ApplicationTypeAccessConstraintDefinition atcd = (ApplicationTypeAccessConstraintDefinition) constraintDefinition;
                    ApplicationTypeConfig atc = atcd.getApplicationTypeConfig();
                    if (atc.isApplicationType()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isApplicationType(TargetResource target) {
            for (AccessConstraintDefinition constraintDefinition : target.getAccessConstraints()) {
                if (constraintDefinition instanceof ApplicationTypeAccessConstraintDefinition) {
                    ApplicationTypeAccessConstraintDefinition atcd = (ApplicationTypeAccessConstraintDefinition) constraintDefinition;
                    ApplicationTypeConfig atc = atcd.getApplicationTypeConfig();
                    if (atc.isApplicationType()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Stores an ApplicationTypeConfig for use in constraints.
         *
         * @param applicationTypeConfig the config
         * @return either the provided confing, or if a compatible one with the same key is already present, that one
         *
         * @throws AssertionError if a config with the same key is already register and it is not
         *           {@linkplain ApplicationTypeConfig#isCompatibleWith(ApplicationTypeConfig)} compatible with} the
         *           one to be added
         */
        public ApplicationTypeConfig addApplicationTypeConfig(ApplicationTypeConfig applicationTypeConfig) {
            ApplicationTypeConfig.Key key = applicationTypeConfig.getKey();
            ApplicationTypeConfig existing = typeConfigs.get(key);
            ApplicationTypeConfig result;
            if (existing == null) {
                typeConfigs.put(key, applicationTypeConfig);
                result = applicationTypeConfig;
            } else {
                // Check for programming error -- ApplicationTypeConfigs with same key created with
                // differing default settings
                assert existing.isCompatibleWith(applicationTypeConfig)
                        : "incompatible " + applicationTypeConfig.getClass().getSimpleName();
                result = existing;
            }
            return result;
        }


        public Collection<ApplicationTypeConfig> getApplicationTypeConfigs(){
            return Collections.unmodifiableCollection(typeConfigs.values());
        }

        @Override
        protected int internalCompare(AbstractConstraintFactory other) {
            // We have no preference
            return 0;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            return NON_APPLICATION;
        }
    }
}
