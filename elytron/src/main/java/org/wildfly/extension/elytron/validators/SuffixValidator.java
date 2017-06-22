/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron.validators;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;

import java.time.format.DateTimeFormatter;

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
                if (denySeconds && (suffix.contains("s") || suffix.contains("S"))) {
                    throw ElytronSubsystemMessages.ROOT_LOGGER.suffixContainsMillis(suffix);
                }
                DateTimeFormatter.ofPattern(suffix);
            } catch (IllegalArgumentException e) {
                throw ElytronSubsystemMessages.ROOT_LOGGER.invalidSuffix(suffix);
            }
        }
    }
}
