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
 * Validates that a parameter is a byte[] of an acceptable length.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BytesValidator extends ModelTypeValidator implements MinMaxValidator{

    public static BytesValidator createSha1(final boolean nullable) {
        return new BytesValidator(20, 20, nullable);
    }

    private final int min;
    private final int max;

    /**
     * Creates a BytesValidator that allows potentially more than one type.
     * @param min the minimum length of the byte[]
     * @param max the maximum length of the byte[]
     * @param nullable whether an undefined value is allowed
     */
    public BytesValidator(final int min, final int max, final boolean nullable) {
        super(ModelType.BYTES, nullable);
        this.min = min;
        this.max = max;
    }

    /**
     * Creates a BytesValidator
     * @param min the minimum length of the byte[]
     * @param max the maximum length of the byte[]
     * @param otherValidTypes additional valid types (i.e one of those whose value can convert to a byte[].) May be {@code null}
     */
    public BytesValidator(final int min, final int max, final ModelType... otherValidTypes) {
        super(false, false, false, ModelType.BYTES, otherValidTypes);
        this.min = min;
        this.max = max;
    }

    @Override
    public Long getMin() {
        return (long) min;
    }

    @Override
    public Long getMax() {
        return (long) max;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined() && value.getType() != ModelType.EXPRESSION) {
            byte[] val = value.asBytes();
            if (val.length < min) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMinSize(val.length, parameterName, min));
            }
            else if (val.length > max) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMaxSize(val.length, parameterName, max));
            }
        }
    }
}
