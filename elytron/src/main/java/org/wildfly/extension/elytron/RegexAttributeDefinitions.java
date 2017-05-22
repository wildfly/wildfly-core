/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Attribute definition and utility methods related to regular expressions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RegexAttributeDefinitions {

    static final SimpleAttributeDefinition PATTERN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATTERN, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new RexExValidator())
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PATTERN_CAPTURE_GROUP = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATTERN, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new CaptureGroupRexExValidator())
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static class RexExValidator extends StringLengthValidator {

        private RexExValidator() {
            super(1, false, false);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);

            String pattern = value.asString();

            try {
                Pattern.compile(pattern);
            } catch (IllegalArgumentException e) {
                throw ROOT_LOGGER.invalidRegularExpression(pattern, e);
            }
        }

    }

    private static class CaptureGroupRexExValidator extends RexExValidator {

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);

            String pattern = value.asString();

            final int groupCount = Pattern.compile(pattern).matcher("").groupCount();
            if (groupCount < 1) {
                throw ROOT_LOGGER.patternRequiresCaptureGroup(pattern);
            }
        }

    }

}
