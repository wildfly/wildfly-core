/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.validators;

import static org.jboss.as.logging.Logging.createOperationFailure;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.resolvers.SizeResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Date: 07.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SizeValidator extends ModelTypeValidator {

    public SizeValidator() {
        this(false);
    }

    public SizeValidator(final boolean nullable) {
        super(ModelType.STRING, nullable);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            final String stringValue = value.asString();
            try {
                SizeResolver.INSTANCE.parseSize(value);
            } catch (IllegalArgumentException e) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidSize(stringValue));
            } catch (IllegalStateException e) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidSize(stringValue));
            }
        }
    }
}
