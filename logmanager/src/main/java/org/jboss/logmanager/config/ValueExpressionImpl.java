/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.config;

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
