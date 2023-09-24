/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.validators;

import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Checks the value to see if it's a valid {@link Level}.
 * <p/>
 * Date: 13.07.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class LogLevelValidator extends ModelTypeValidator implements AllowedValuesValidator {
    private static final Level[] LEVELS = {
            org.jboss.logmanager.Level.ALL,
            org.jboss.logmanager.Level.CONFIG,
            org.jboss.logmanager.Level.DEBUG,
            org.jboss.logmanager.Level.ERROR,
            org.jboss.logmanager.Level.FATAL,
            org.jboss.logmanager.Level.FINE,
            org.jboss.logmanager.Level.FINER,
            org.jboss.logmanager.Level.FINEST,
            org.jboss.logmanager.Level.INFO,
            org.jboss.logmanager.Level.OFF,
            org.jboss.logmanager.Level.SEVERE,
            org.jboss.logmanager.Level.TRACE,
            org.jboss.logmanager.Level.WARN,
            org.jboss.logmanager.Level.WARNING
    };

    private final List<Level> allowedValues;
    private final List<ModelNode> nodeValues;

    public LogLevelValidator(final boolean nullable) {
        this(nullable, false);
    }

    private LogLevelValidator(final boolean nullable, final boolean allowExpressions) {
        this(nullable, allowExpressions, LEVELS);
    }

    private LogLevelValidator(final boolean nullable, final boolean allowExpressions, final Level... levels) {
        super(ModelType.STRING, nullable, allowExpressions);
        allowedValues = Arrays.asList(levels);
        allowedValues.sort(LevelComparator.INSTANCE);
        nodeValues = new ArrayList<>(allowedValues.size());
        for (Level level : allowedValues) {
            nodeValues.add(new ModelNode(level.getName()));
        }
    }

    @Override
    public void validateParameter(final String parameterName, final ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            final String levelString = value.asString();
            try {
                final Level level = Level.parse(levelString);
                if (!allowedValues.contains(level)) {
                    throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidLogLevel(levelString));
                }
            } catch (IllegalArgumentException e) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidLogLevel(levelString));
            }
        }
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        return nodeValues;
    }

    private static class LevelComparator implements Comparator<Level> {

        static final int EQUAL = 0;
        static final int LESS = -1;
        static final int GREATER = 1;

        static final LevelComparator INSTANCE = new LevelComparator();

        @Override
        public int compare(final Level o1, final Level o2) {
            int result = EQUAL;
            final int left = o1.intValue();
            final int right = o2.intValue();
            if (left < right) {
                result = LESS;
            } else if (left > right) {
                result = GREATER;
            }
            return result;
        }
    }
}
