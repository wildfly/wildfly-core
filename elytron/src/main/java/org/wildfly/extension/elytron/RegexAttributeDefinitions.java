/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
            .setValidator(new RegExValidator())
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PATTERN_CAPTURE_GROUP = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATTERN, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new CaptureGroupRexExValidator())
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static class RegExValidator extends StringLengthValidator {

        private RegExValidator() {
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

    private static class CaptureGroupRexExValidator extends RegExValidator {

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
