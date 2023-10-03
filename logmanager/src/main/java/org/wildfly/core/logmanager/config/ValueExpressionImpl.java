/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ValueExpressionImpl<T> implements ValueExpression<T> {

    private final String expression;
    private final T resolvedValue;

    ValueExpressionImpl(final String expression, final T resolvedValue) {
        this.expression = expression;
        this.resolvedValue = resolvedValue;
    }

    @Override
    public T getResolvedValue() {
        return resolvedValue;
    }

    @Override
    public boolean isExpression() {
        return expression != null;
    }

    @Override
    public String getValue() {
        return expression == null ? (resolvedValue == null ? null : String.valueOf(resolvedValue)) : expression;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
