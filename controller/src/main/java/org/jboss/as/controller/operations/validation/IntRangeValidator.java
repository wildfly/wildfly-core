/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates that the given parameter is an int in a given range.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class IntRangeValidator extends ModelTypeValidator implements MinMaxValidator {
    protected final int min;
    protected final int max;

    public IntRangeValidator(final int min) {
        this(min, Integer.MAX_VALUE, false, false);
    }

    public IntRangeValidator(final int min, final int max) {
        this(min, max, false, false);
    }

    public IntRangeValidator(final int min, final boolean nullable) {
        this(min, Integer.MAX_VALUE, nullable, false);
    }

    public IntRangeValidator(final int min, final boolean nullable, final boolean allowExpressions) {
        this(min, Integer.MAX_VALUE, nullable, allowExpressions);
    }

    public IntRangeValidator(final int min, final int max, final boolean nullable, final boolean allowExpressions) {
        super(ModelType.INT, nullable, allowExpressions, false);
        this.min = min;
        this.max = max;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined() && value.getType() != ModelType.EXPRESSION) {
            int val = value.asInt();
            if (val < min) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMinValue(val, parameterName, min));
            }
            else if (val > max) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMaxValue(val, parameterName, max));
            }
        }
    }

    @Override
    public Long getMin() {
        return (long) min;
    }

    @Override
    public Long getMax() {
        return (long) max;
    }

}
