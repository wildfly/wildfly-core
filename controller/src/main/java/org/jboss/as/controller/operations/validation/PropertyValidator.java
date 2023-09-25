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
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PropertyValidator extends ModelTypeValidator {

    private final ParameterValidator valueValidator;

    public PropertyValidator(boolean nullable, ParameterValidator valueValidator) {
        super(ModelType.PROPERTY, nullable);
        this.valueValidator = valueValidator;
    }

    public void validateParameter(final String parameterName, final ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            if (value.asProperty().getName().length() < 1) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidMinLength(value.asProperty().getName(), parameterName, 1));
            }
            if (valueValidator != null) {
                valueValidator.validateParameter(parameterName, value.asProperty().getValue());
            }
        }
    }
}
