/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
