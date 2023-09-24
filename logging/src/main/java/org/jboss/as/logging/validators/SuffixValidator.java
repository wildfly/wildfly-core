/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.validators;

import static org.jboss.as.logging.Logging.createOperationFailure;

import java.text.SimpleDateFormat;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SuffixValidator extends ModelTypeValidator {
    private final boolean denySeconds;

    public SuffixValidator() {
        this(false, true);
    }

    public SuffixValidator(final boolean nullable, final boolean denySeconds) {
        super(ModelType.STRING, nullable);
        this.denySeconds = denySeconds;
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            String suffix = value.asString();
            // The suffixes .gz and .zip are allowed at the end to indicate the file should be compressed on rotation.
            // These are not valid SimpleDateFormat patterns and will fail validation. The suffix should be removed, if
            // present, before the date validation is done.
            if (suffix.endsWith(".zip")) {
                suffix = suffix.substring(0, suffix.length() - 4);
            } else if (suffix.endsWith(".gz")) {
                suffix = suffix.substring(0, suffix.length() - 3);
            }
            try {
                new SimpleDateFormat(suffix);
                if (denySeconds) {
                    for (int i = 0; i < suffix.length(); i++) {
                        char c = suffix.charAt(i);
                        if (c == '\'') {
                            c = suffix.charAt(++i);
                            while (c != '\'') {
                                c = suffix.charAt(++i);
                            }
                        }
                        if (c == 's' || c == 'S') {
                            throw createOperationFailure(LoggingLogger.ROOT_LOGGER.suffixContainsMillis(suffix));
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidSuffix(suffix));
            }
        }
    }
}
