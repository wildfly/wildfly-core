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
 * Validates that the given parameter is a long in a given range.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class LongRangeValidator extends ModelTypeValidator implements MinMaxValidator {
    protected final long min;
    protected final long max;

    public LongRangeValidator(final long min) {
        this(min, Long.MAX_VALUE, false, false);
    }

    public LongRangeValidator(final long min, final Long max) {
        this(min, max, false, false);
    }

    public LongRangeValidator(final long min, final boolean nullable) {
        this(min, Integer.MAX_VALUE, nullable, false);
    }

    public LongRangeValidator(final long min, final long max, final boolean nullable, final boolean allowExpressions) {
        super(ModelType.LONG, nullable, allowExpressions, false);
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
            long val = value.asLong();
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
        return min;
    }

    @Override
    public Long getMax() {
        return max;
    }
}
