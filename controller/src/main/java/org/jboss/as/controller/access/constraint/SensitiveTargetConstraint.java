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
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.access.rbac.StandardRole;

/**
 * {@link Constraint} related to whether a resource, attribute or operation is considered security sensitive.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SensitiveTargetConstraint extends AllowAllowNotConstraint {

    public static final SensitiveTargetConstraint.Factory FACTORY = new Factory();

    private static final SensitiveTargetConstraint SENSITIVE = new SensitiveTargetConstraint(true);
    private static final SensitiveTargetConstraint NOT_SENSITIVE = new SensitiveTargetConstraint(false);
    private static final SensitiveTargetConstraint ALLOWS = new SensitiveTargetConstraint(true, true);
    private static final SensitiveTargetConstraint DISALLOWS = new SensitiveTargetConstraint(false, true);

    private SensitiveTargetConstraint(boolean isSensitive) {
        super(isSensitive);
    }

    private SensitiveTargetConstraint(boolean allowsSensitive, boolean allowsNonSensitive) {
        super(allowsSensitive, allowsNonSensitive);
    }

    public static class Factory extends AbstractConstraintFactory {

        private final Map<SensitivityClassification.Key, SensitivityClassification> sensitivities =
                Collections.synchronizedMap(new HashMap<SensitivityClassification.Key, SensitivityClassification>());

        /** Singleton */
        private Factory() {
        }

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            if (role == StandardRole.ADMINISTRATOR
                    || role == StandardRole.SUPERUSER
                    || (role == StandardRole.AUDITOR
                            && actionEffect != Action.ActionEffect.WRITE_CONFIG
                            && actionEffect != Action.ActionEffect.WRITE_RUNTIME)) {
                return ALLOWS;
            }
            return DISALLOWS;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return (isSensitiveAction(action, actionEffect) || isSensitiveAttribute(target, actionEffect)) ? SENSITIVE : NOT_SENSITIVE;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return (isSensitiveAction(action, actionEffect) || isSensitiveResource(target, actionEffect)) ? SENSITIVE : NOT_SENSITIVE;
        }

        private boolean isSensitiveAction(Action action, Action.ActionEffect effect) {
            for (AccessConstraintDefinition constraintDefinition : action.getAccessConstraints()) {
                if (constraintDefinition instanceof SensitiveTargetAccessConstraintDefinition) {
                    SensitiveTargetAccessConstraintDefinition stcd = (SensitiveTargetAccessConstraintDefinition) constraintDefinition;
                    SensitivityClassification sensitivity = stcd.getSensitivity();
                    if (sensitivity.isSensitive(effect)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isSensitiveAttribute(TargetAttribute target, Action.ActionEffect effect) {
            for (AccessConstraintDefinition constraintDefinition : target.getAccessConstraints()) {
                if (constraintDefinition instanceof SensitiveTargetAccessConstraintDefinition) {
                    SensitiveTargetAccessConstraintDefinition stcd = (SensitiveTargetAccessConstraintDefinition) constraintDefinition;
                    SensitivityClassification sensitivity = stcd.getSensitivity();
                    if (sensitivity.isSensitive(effect)) {
                        return true;
                    }
                }
            }
            // Check the resource
            return isSensitiveResource(target.getTargetResource(), effect);
        }

        private boolean isSensitiveResource(TargetResource target, Action.ActionEffect effect) {
            for (AccessConstraintDefinition constraintDefinition : target.getAccessConstraints()) {
                if (constraintDefinition instanceof SensitiveTargetAccessConstraintDefinition) {
                    SensitiveTargetAccessConstraintDefinition stcd = (SensitiveTargetAccessConstraintDefinition) constraintDefinition;
                    SensitivityClassification sensitivity = stcd.getSensitivity();
                    if (sensitivity.isSensitive(effect)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Stores a sensitivity classification for use in constraints.
         *
         * @param sensitivity the classification
         * @return either the provided classification, or if a compatible one with the same key is already present, that one
         *
         * @throws AssertionError if a classification with the same key is already register and it is not
         *           {@linkplain SensitivityClassification#isCompatibleWith(AbstractSensitivity) compatible with} the
         *           one to be added
         */
        public final SensitivityClassification addSensitivity(SensitivityClassification sensitivity) {
            SensitivityClassification.Key key = sensitivity.getKey();
            SensitivityClassification existing = sensitivities.get(key);
            SensitivityClassification result;
            if (existing == null) {
                sensitivities.put(key, sensitivity);
                result = sensitivity;
            } else {
                // Check for programming error -- SensitivityClassification with same key created with
                // differing default settings
                assert existing.isCompatibleWith(sensitivity)
                        : "incompatible " + sensitivity.getClass().getSimpleName();
                result = existing;
            }
            return result;
        }

        public Collection<SensitivityClassification> getSensitivities(){
            return Collections.unmodifiableCollection(sensitivities.values());
        }

        @Override
        protected int internalCompare(AbstractConstraintFactory other) {
            // We have no preference
            return 0;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            return (action.getImpact() == JmxAction.Impact.CLASSLOADING || target.isNonFacadeMBeansSensitive()) ? SENSITIVE : NOT_SENSITIVE;
        }
    }
}
