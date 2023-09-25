/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Performs validation on detyped operation parameters.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@FunctionalInterface
public interface ParameterValidator {

    /**
     * Validate the parameter with the given name.
     *
     * @param parameterName the name of the parameter. Cannot be {@code null}
     * @param value the parameter value. Cannot be {@code null}
     *
     * @throws OperationFailedException if the value is not valid
     */
    void validateParameter(String parameterName, ModelNode value) throws OperationFailedException;
}
