/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates whether the provided string represents a valid character set.
 *
 * @author <a href="mailto:szaldana@redhat.com">Sonia Zaldana</a>
 */
public class CharsetValidator extends ModelTypeValidator {

    public CharsetValidator() {
        super(ModelType.STRING);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            try {
                Charset hashCharset = Charset.forName(value.asString());
            } catch (IllegalCharsetNameException e) {
                throw ControllerLogger.ROOT_LOGGER.illegalCharsetName(value.asString());
            } catch (UnsupportedCharsetException e) {
                throw ControllerLogger.ROOT_LOGGER.unsupportedCharset(value.asString());
            }
        }
    }
}
