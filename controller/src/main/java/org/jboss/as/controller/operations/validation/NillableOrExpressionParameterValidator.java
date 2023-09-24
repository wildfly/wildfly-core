/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.List;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.Assert;

/**
 * {@link ParameterValidator} that validates undefined values and expression types, delegating to a provided
 * validator for everything else.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NillableOrExpressionParameterValidator implements ParameterValidator, MinMaxValidator, AllowedValuesValidator {

    private final ParameterValidator delegate;
    private final Boolean allowNull;
    private final boolean allowExpression;

    /**
     * Creates a new {@code NillableOrExpressionParameterValidator}.
     *
     * @param delegate validator to delegate to once null and expression validation is done. Cannot be {@code null}
     * @param allowNull whether undefined values are allowed. If this param is {@code null}, checking for undefined
     *                  is delegated to the provided {@code delegate}
     * @param allowExpression  whether expressions are allowed
     *
     * @throws java.lang.IllegalArgumentException if {@code delegate} is {@code null}
     */
    public NillableOrExpressionParameterValidator(ParameterValidator delegate, Boolean allowNull, boolean allowExpression) {
        Assert.checkNotNullParam("delegate", delegate);
        this.delegate = delegate;
        this.allowNull = allowNull;
        this.allowExpression = allowExpression;
    }


    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        switch (value.getType()) {
            case EXPRESSION:
                if (!allowExpression) {
                    throw ControllerLogger.ROOT_LOGGER.expressionNotAllowed(parameterName);
                }
                break;
            case UNDEFINED:
                if (allowNull != null) {
                    if (!allowNull) {
                        throw ControllerLogger.ROOT_LOGGER.nullNotAllowed(parameterName);
                    }
                    break;
                } // else fall through and let the delegate validate
            default:
                delegate.validateParameter(parameterName, value);
        }
    }

    @Override
    public Long getMin() {
        return (delegate instanceof MinMaxValidator) ? ((MinMaxValidator) delegate).getMin() : null;
    }

    @Override
    public Long getMax() {
        return (delegate instanceof MinMaxValidator) ? ((MinMaxValidator) delegate).getMax() : null;
    }

    public ParameterValidator getDelegate() {
        return delegate;
    }

    public Boolean getAllowNull() {
        return allowNull;
    }

    public boolean isAllowExpression() {
        return allowExpression;
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        return (delegate instanceof AllowedValuesValidator) ? ((AllowedValuesValidator) delegate).getAllowedValues() : null;
    }
}
