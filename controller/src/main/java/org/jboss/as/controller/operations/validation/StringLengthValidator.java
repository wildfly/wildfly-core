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
 * Validates that the given parameter is a string of an allowed length.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class StringLengthValidator extends ModelTypeValidator implements MinMaxValidator {
    protected final int min;
    protected final int max;

    /**
     * Equivalent to {@code this(min, Integer.MAX_VALUE, false, false)}.
     * @param min the minimum length of the string
     */
    public StringLengthValidator(final int min) {
        this(min, Integer.MAX_VALUE, false, false);
    }

    public StringLengthValidator(final int min, final int max) {
        this(min, max, false, false);
    }

    /**
     * Equivalent to {@code this(min, Integer.MAX_VALUE, nullable, false)}.
     * @param min the minimum length of the string
     * @param nullable {@code true} is an {@link ModelType#UNDEFINED} value is valid
     */
    public StringLengthValidator(final int min, final boolean nullable) {
        this(min, Integer.MAX_VALUE, nullable, false);
    }

    /**
     * Creates a new {@code StringLengthValidator}.
     *
     * @param min the minimum length of the string
     * @param max the maximum length of the string
     * @param nullable {@code true} if an {@link ModelType#UNDEFINED} value is valid
     * @param allowExpressions {@code true} if an {@link ModelType#EXPRESSION} value is valid
     */
    public StringLengthValidator(final int min, final int max, final boolean nullable, final boolean allowExpressions) {
        super(ModelType.STRING, nullable, allowExpressions, false);
        this.min = min;
        this.max = max;
    }


    /**
     * Equivalent to {@code this(min, Integer.MAX_VALUE, nullable, allowExpressions)}.
     * @param min the minimum length of the string
     * @param nullable {@code true} if an {@link ModelType#UNDEFINED} value is valid
     * @param allowExpressions {@code true} if an {@link ModelType#EXPRESSION} value is valid
     */
    public StringLengthValidator(final int min, final boolean nullable, final boolean allowExpressions) {
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
            if (str.length() < min) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMinLength(str, parameterName, min));
            }
            else if (str.length() > max) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMaxLength(str, parameterName, max));
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
