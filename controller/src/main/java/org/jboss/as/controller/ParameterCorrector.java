/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * Allows ease-of-use adjustment of operation parameter values to conform to operation handler expectations.
 * A corrector will be invoked in {@link OperationContext.Stage Stage.MODEL} before the parameter value is
 * {@link org.jboss.as.controller.operations.validation.ParameterValidator#validateParameter(String, ModelNode) validated},
 * so it has a chance to adjust the value if necessary. The corrected value will be what is passed to the validator.
 * <p/>
 * Some example uses:
 * <ul>
 *     <li>Simple formatting corrections to canonical formats, e.g. case correction</li>
 *     <li>Compatibility migrations, where a previously legal value is 'corrected' to
 *         a currently legal value with the same semantic</li>
 * </ul>
 * <p/>
 * Implementations should ensure the given {@code parameterValue} meets expectations (e.g. is of the
 * expected {@code ModelType}) before performing any correction. If it does not, the {@code parameterValue}
 * should simply be returned unmodified. A {@code ParameterCorrector} should not attempt to reject unexpected values;
 * that is the task of a {@link org.jboss.as.controller.operations.validation.ParameterValidator ParameterValidator}.
 * <p/>
 * <strong>Note:</strong> If an original operation parameter value contained nodes of {@code ModelType.STRING}
 * where the string included expression syntax, a replacement node of {@code ModelType.EXPRESSION} will be
 * passed to the parameter corrector, allowing the corrector to easily differentiate expressions from ordinary
 * strings. The given {@code parameterValue} will not be the value of the resolved expression.
 * Generally, a corrector should return expression nodes unmodified.
 *
 * @author Alexey Loubyansky
 */
@FunctionalInterface
public interface ParameterCorrector {

    /**
     * Adjusts a parameter value before use.
     *
     * @param parameterValue  the operation parameter value. Will not be {@code null}
     * @param currentValue  the current value of an attribute or complex attribute field or element
     *                      whose value would be changed by the operation to {@code parameterValue}.
     *                      May be {@code null} or an undefined node if a 'current value' is not
     *                      relevant to the operation.
     * @return  the value that actually should be used. Cannot be {@code null}
     */
    ModelNode correct(ModelNode parameterValue, ModelNode currentValue);
}
