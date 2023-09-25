/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Performs multiple {@link ParameterValidator parameter validations} against
 * a detyped operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ParametersValidator implements ParameterValidator {

    final Map<String, ParameterValidator> validators = Collections.synchronizedMap(new HashMap<>());

    public ParametersValidator() {
    }

    public ParametersValidator(ParametersValidator toCopy) {
        validators.putAll(toCopy.validators);
    }

    public void registerValidator(String parameterName, ParameterValidator validator) {
        validators.put(parameterName, validator);
    }

    public void validate(ModelNode operation) throws OperationFailedException {
        for (Map.Entry<String, ParameterValidator> entry : validators.entrySet()) {
            String paramName = entry.getKey();
            ModelNode paramVal = operation.has(paramName) ? operation.get(paramName) : new ModelNode();
            entry.getValue().validateParameter(paramName, paramVal);
        }
    }

    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        ParameterValidator parameterValidator = validators.get(parameterName);
        if (parameterValidator != null) parameterValidator.validateParameter(parameterName, value);
    }
}
