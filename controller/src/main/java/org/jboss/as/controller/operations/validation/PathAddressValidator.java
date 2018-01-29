/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller.operations.validation;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Validates that a node can be converted to a {@link PathAddress}.
 *
 * @author Brian Stansberry
 */
public class PathAddressValidator implements ParameterValidator {

    public static final PathAddressValidator INSTANCE = new PathAddressValidator();

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        try {
            PathAddress.pathAddress(value);
        } catch (IllegalArgumentException iae) {
            throw ControllerLogger.MGMT_OP_LOGGER.invalidAddressFormat(value);
        }
    }
}
