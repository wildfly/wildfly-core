/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates that a value can resolve to a multicast address.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class MulticastAddressValidator extends StringLengthValidator {

    public MulticastAddressValidator(final boolean allowNull, final boolean allowExpressions) {
        super(1, allowNull, allowExpressions);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);

        if (value.isDefined() && value.getType() != ModelType.EXPRESSION) {
            String inetAddr = value.asString();
            try {
                final InetAddress mcastAddr = InetAddress.getByName(inetAddr);
                if (!mcastAddr.isMulticastAddress()) {
                    throw ControllerLogger.ROOT_LOGGER.invalidMulticastAddress(inetAddr, parameterName);
                }
            } catch (final UnknownHostException e) {
                throw ControllerLogger.ROOT_LOGGER.unknownMulticastAddress(e, inetAddr, parameterName);
            }

        }
    }
}
