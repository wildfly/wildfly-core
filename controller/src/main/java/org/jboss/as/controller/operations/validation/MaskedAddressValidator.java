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
 * Validates that the given parameter is a string that can be converted into a masked InetAddress.
 *
 * @author Jason T, Greene
 */
public class MaskedAddressValidator extends ModelTypeValidator {

    public MaskedAddressValidator(final boolean nullable, final boolean allowExpressions) {
        super(ModelType.STRING, nullable, allowExpressions, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined() && value.getType() != ModelType.EXPRESSION) {
            parseMasked(value);
        }
    }

    public static ParsedResult parseMasked(ModelNode value) throws OperationFailedException {
        final String[] split = value.asString().split("/");
        if (split.length != 2) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAddressMaskValue(value.asString()));
        }
        try {
            // TODO - replace with non-dns routine
            InetAddress address = InetAddress.getByName(split[0]);
            int mask = Integer.parseInt(split[1]);

            int max = address.getAddress().length * 8;
            if (mask > max) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAddressMask(split[1], "> " + max));
            } else if (mask < 0) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAddressMask(split[1], "< 0"));
            }

            return new ParsedResult(address, mask);
        } catch (final UnknownHostException e) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAddressValue(split[0], e.getLocalizedMessage()));
        } catch (final NumberFormatException e) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAddressMask(split[1], e.getLocalizedMessage()));
        }
    }

    public static class ParsedResult {
        public InetAddress address;
        public int mask;

        public ParsedResult(InetAddress address, int mask) {
            this.address = address;
            this.mask = mask;
        }
    }
}
