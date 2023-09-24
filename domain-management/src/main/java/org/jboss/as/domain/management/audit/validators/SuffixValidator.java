/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.audit.validators;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.text.SimpleDateFormat;

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
            final String suffix = value.asString();
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
                            throw DomainManagementLogger.ROOT_LOGGER.suffixContainsMillis(suffix);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                throw DomainManagementLogger.ROOT_LOGGER.invalidSuffix(suffix);
            }
        }
    }
}
