/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.common.net.Inet;

/**
 * Validates that the given parameter is a string that can be converted into an InetAddress.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class InetAddressValidator extends ModelTypeValidator {

    public InetAddressValidator(final boolean nullable, final boolean allowExpressions) {
        super(ModelType.STRING, nullable, allowExpressions, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined() && value.getType() != ModelType.EXPRESSION) {
            String str = value.asString();
            if (Inet.parseInetAddress(str) == null) {
                throw new OperationFailedException("Address is invalid: \"" + str + "\"");
            }
        }
    }

}
