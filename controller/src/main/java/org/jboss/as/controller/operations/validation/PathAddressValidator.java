/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
