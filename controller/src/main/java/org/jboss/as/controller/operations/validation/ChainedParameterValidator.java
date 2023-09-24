/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.Assert;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class ChainedParameterValidator implements ParameterValidator, MinMaxValidator, AllowedValuesValidator {
    private final ParameterValidator[] validators;


    public ChainedParameterValidator(final ParameterValidator... validators) {
        Assert.checkNotNullParam("validators", validators);
        this.validators = validators;
    }

    public static ParameterValidator chain(ParameterValidator... validators) {
        return new ChainedParameterValidator(validators);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        for (final ParameterValidator validator : validators)
            validator.validateParameter(parameterName, value);
    }

    @Override
    public Long getMin() {
        Long valMin = null;
        for (ParameterValidator validator : validators) {
            if (validator instanceof MinMaxValidator) {
                MinMaxValidator minMax = (MinMaxValidator) validator;
                Long val = minMax.getMin();
                if (val != null && (valMin == null || val.longValue() > valMin.longValue())) {
                    valMin = val;
                }
            }
        }
        return valMin;
    }

    @Override
    public Long getMax() {
        Long valMax = null;
        for (ParameterValidator validator : validators) {
            if (validator instanceof MinMaxValidator) {
                MinMaxValidator minMax = (MinMaxValidator) validator;
                Long val = minMax.getMax();
                if (val != null && (valMax == null || val.longValue() < valMax.longValue())) {
                    valMax = val;
                }
            }
        }
        return valMax;
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        List<ModelNode> allowed = null;
        for (ParameterValidator validator : validators) {
            if (validator instanceof AllowedValuesValidator) {
                AllowedValuesValidator avv = (AllowedValuesValidator) validator;
                List<ModelNode> val = avv.getAllowedValues();
                if (val != null) {
                    if (allowed == null) {
                        allowed = val;
                    } else {
                        List<ModelNode> copy = new ArrayList<ModelNode>();
                        for (ModelNode existing : allowed) {
                            if (val.contains(existing)) {
                                copy.add(existing);
                            }
                        }
                        allowed = copy;
                    }
                }
            }
        }
        return allowed;
    }
}
