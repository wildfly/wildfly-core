/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link ParameterValidator} that validates the value is a string matching one of the {@link TimeUnit} names.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class TimeUnitValidator extends ModelTypeValidator implements AllowedValuesValidator {

    /** TimeUnitValidator where any TimeUnit is valid, but an undefined value is not */
    public static final TimeUnitValidator ANY_REQUIRED = new TimeUnitValidator(false, false);
    /** TimeUnitValidator where any TimeUnit is valid, as is an undefined value */
    public static final TimeUnitValidator ANY_OPTIONAL = new TimeUnitValidator(true, false);

    private final EnumSet<TimeUnit> allowedValues;

    public TimeUnitValidator(final boolean nullable, final TimeUnit... allowed) {
        this(nullable, false, allowed);
    }

    public TimeUnitValidator(final boolean nullable, final boolean allowExpressions) {
        super(ModelType.STRING, nullable, allowExpressions);
        allowedValues = EnumSet.allOf(TimeUnit.class);
    }

    public TimeUnitValidator(final boolean nullable, final boolean allowExpressions, final TimeUnit... allowed) {
        super(ModelType.STRING, nullable, allowExpressions);
        allowedValues = EnumSet.noneOf(TimeUnit.class);
        for (TimeUnit tu : allowed) {
            allowedValues.add(tu);
        }
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            String tuString = value.asString();
            TimeUnit tu = TimeUnit.valueOf(tuString.toUpperCase(Locale.ENGLISH));
            if (tu == null || !allowedValues.contains(tu)) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidValue(tuString, parameterName, allowedValues));
            }
        }
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        List<ModelNode> result = new ArrayList<ModelNode>();
        for (TimeUnit tu : allowedValues) {
            result.add(new ModelNode().set(tu.name()));
        }
        return result;
    }
}
