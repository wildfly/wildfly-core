/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import java.util.regex.Pattern;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.rbac.StandardRole;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * {@link Constraint} related to whether an attribute is considered security sensitive
 * because it contains a vault expression.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SensitiveVaultExpressionConstraint extends AllowAllowNotConstraint {

    public static final ConstraintFactory FACTORY = new Factory();

    private static final Pattern VAULT_EXPRESSION_PATTERN = Pattern.compile(".*\\$\\{VAULT::.*::.*::.*}.*");

    private static final SensitiveVaultExpressionConstraint SENSITIVE = new SensitiveVaultExpressionConstraint(true);
    private static final SensitiveVaultExpressionConstraint NOT_SENSITIVE = new SensitiveVaultExpressionConstraint(false);
    private static final SensitiveVaultExpressionConstraint ALLOWS = new SensitiveVaultExpressionConstraint(true, true);
    private static final SensitiveVaultExpressionConstraint DISALLOWS = new SensitiveVaultExpressionConstraint(false, true);

    private SensitiveVaultExpressionConstraint(boolean sensitive) {
        super(sensitive);
    }

    private SensitiveVaultExpressionConstraint(boolean allowsSensitive, boolean allowsNonSensitive) {
        super(allowsSensitive, allowsNonSensitive);
    }

    private static class Factory extends AbstractConstraintFactory {

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            if (role == StandardRole.ADMINISTRATOR
                    || role == StandardRole.SUPERUSER
                    || role == StandardRole.AUDITOR) {
                return ALLOWS;
            }
            return DISALLOWS;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return isSensitiveAction(action, actionEffect, target) ? SENSITIVE : NOT_SENSITIVE;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return isSensitiveAction(action, actionEffect) ? SENSITIVE : NOT_SENSITIVE;
        }

        private boolean isSensitiveAction(Action action, Action.ActionEffect actionEffect) {
            if (VaultExpressionSensitivityConfig.INSTANCE.isSensitive(actionEffect)) {
                if (actionEffect == Action.ActionEffect.WRITE_RUNTIME || actionEffect == Action.ActionEffect.WRITE_CONFIG) {
                    ModelNode operation = action.getOperation();
                    for (Property property : operation.asPropertyList()) {
                        if (isSensitiveValue(property.getValue())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean isSensitiveAction(Action action, Action.ActionEffect actionEffect, TargetAttribute targetAttribute) {
            if (VaultExpressionSensitivityConfig.INSTANCE.isSensitive(actionEffect)) {
                if (actionEffect == Action.ActionEffect.WRITE_RUNTIME || actionEffect == Action.ActionEffect.WRITE_CONFIG) {
                    ModelNode operation = action.getOperation();
                    if (operation.hasDefined(targetAttribute.getAttributeName())) {
                        if (isSensitiveValue(operation.get(targetAttribute.getAttributeName()))) {
                            return true;
                        }
                    }
                    if (ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION.equals(operation.get(ModelDescriptionConstants.OP).asString())
                            && operation.hasDefined(ModelDescriptionConstants.VALUE)) {
                        if (isSensitiveValue(operation.get(ModelDescriptionConstants.VALUE))) {
                            return true;
                        }
                    }
                }
                if (actionEffect != Action.ActionEffect.ADDRESS) {
                    if (isSensitiveValue(targetAttribute.getCurrentValue())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isSensitiveValue(ModelNode value) {
            if (value.getType() == ModelType.EXPRESSION
                    || value.getType() == ModelType.STRING) {
                String valueString = value.asString();

                return VAULT_EXPRESSION_PATTERN.matcher(valueString).matches();
            }
            return false;
        }

        @Override
        protected int internalCompare(AbstractConstraintFactory other) {
            // We have no preference
            return 0;
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, JmxAction action, JmxTarget target) {
            //TODO We could do something like this if the action provided the new value and the target
            // provided the current value. But right now that data isn't provided.
//            if (VaultExpressionSensitivityConfig.INSTANCE.isSensitive(actionEffect)) {
//                if (actionEffect == Action.ActionEffect.WRITE_RUNTIME || actionEffect == Action.ActionEffect.WRITE_CONFIG) {
//                    if (action.getNewValue() instanceof String && isSensitiveValue(new ModelNode(action.getNewValue().toString()))) {
//                        return SENSITIVE;
//                    }
//                }
//                if (actionEffect != Action.ActionEffect.ADDRESS) {
//                    if (target.getCurrentValue() instanceof String && isSensitiveValue(new ModelNode(target.getCurrentValue().toString()))) {
//                        return SENSITIVE;
//                    }
//                }
//            }
            return NOT_SENSITIVE;
        }
    }
}

