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
 * Validates that the given parameter is a string of an allowed length in bytes.
 *
 * @author Stefano Maestri (c) 2011 Red Hat Inc.
 */
public class StringBytesLengthValidator extends ModelTypeValidator implements MinMaxValidator {
    protected final int min;
    protected final int max;

    public StringBytesLengthValidator(final int min) {
        this(min, Integer.MAX_VALUE, false, false);
    }

    public StringBytesLengthValidator(final int min, final boolean nullable) {
        this(min, Integer.MAX_VALUE, nullable, false);
    }

    public StringBytesLengthValidator(final int min, final int max, final boolean nullable, final boolean allowExpressions) {
        super(ModelType.STRING, nullable, allowExpressions, false);
        this.min = min;
        this.max = max;
    }

    public StringBytesLengthValidator(final int min, final boolean nullable, final boolean allowExpressions) {
        this(min,Integer.MAX_VALUE,nullable,allowExpressions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined() && value.getType() != ModelType.EXPRESSION) {
            String str = value.asString();
            if (str.getBytes().length < min) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMinBytesLength(str, parameterName, min));
            }
            else if (str.getBytes().length > max) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMaxBytesLength(str, parameterName, max));
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
