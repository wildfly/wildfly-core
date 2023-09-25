/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import java.util.List;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.wildfly.common.Assert;

/**
 * Validates parameters of type {@link org.jboss.dmr.ModelType#OBJECT}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 *
 */
public class MapValidator extends ModelTypeValidator implements ParameterValidator {

    private final int min;
    private final int max;
    private final ParameterValidator elementValidator;

    /**
     * Constructs a new {@code MapValidator}
     *
     * @param elementValidator validator for list elements
     */
    public MapValidator(ParameterValidator elementValidator) {
        this(elementValidator, false, 1, Integer.MAX_VALUE);
    }

    /**
     * Constructs a new {@code MapValidator}
     *
     * @param elementValidator validator for map values
     * @param nullable {@code true} if the model node for the list can be {@code null} or {@link org.jboss.dmr.ModelType#UNDEFINED}
     */
    public MapValidator(ParameterValidator elementValidator, boolean nullable) {
        this(elementValidator, nullable, 1, Integer.MAX_VALUE);
    }

    /**
     * Constructs a new {@code MapValidator}
     *
     * @param elementValidator validator for map values
     * @param nullable {@code true} if the model node for the map can be {@code null} or {@link org.jboss.dmr.ModelType#UNDEFINED}
     * @param minSize minimum number of elements in the list
     * @param maxSize maximum number of elements in the list
     */
    public MapValidator(ParameterValidator elementValidator, boolean nullable, int minSize, int maxSize) {
        super(ModelType.OBJECT, nullable, false, false);
        Assert.checkNotNullParam("elementValidator", elementValidator);
        this.min = minSize;
        this.max = maxSize;
        this.elementValidator = elementValidator;
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            List<Property> list = value.asPropertyList();
            int size = list.size();
            if (size < min) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMinSize(size, parameterName, min));
            }
            else if (size > max) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMaxSize(size, parameterName, max));
            }
            else {
                for (Property property : list) {
                    elementValidator.validateParameter(parameterName, property.getValue());
                }
            }
        }
    }

}
