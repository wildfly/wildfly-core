/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public class StringAllowedValuesValidator extends ModelTypeValidator implements AllowedValuesValidator {

    private List<ModelNode> allowedValues = new ArrayList<>();

    public StringAllowedValuesValidator(String... values) {
        super(ModelType.STRING);
        for (String value : values) {
            allowedValues.add(new ModelNode().set(value));
        }
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            if (!allowedValues.contains(value)) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidValue(value.asString(), parameterName, allowedValues));
            }
        }
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        return this.allowedValues;
    }
}
