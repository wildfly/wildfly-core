/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.logmanager.config;

import java.io.File;

/**
 * Represents a possible value expression. A value is said to be an expression if it matches a <code>
 * ${system.property:DEFAULT_VALUE}</code> pattern.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) do we need this or even want to use it?
public interface ValueExpression<T> {

    final ValueExpression<String> NULL_STRING_EXPRESSION = new ValueExpression<String>() {
        @Override
        public String getResolvedValue() {
            return null;
        }

        @Override
        public boolean isExpression() {
            return false;
        }

        @Override
        public String getValue() {
            return null;
        }
    };

    final ValueExpression<Boolean> NULL_BOOLEAN_EXPRESSION = new ValueExpression<Boolean>() {
        @Override
        public Boolean getResolvedValue() {
            return null;
        }

        @Override
        public boolean isExpression() {
            return false;
        }

        @Override
        public String getValue() {
            return null;
        }
    };

    /**
     * The resolved value.
     * <p/>
     * If this is an {@link #isExpression() expression} the resolved value will be the value from a system property or
     * the default value from the expression if the system property is not set.
     * <p/>
     * If this is <b>not</b> an {@link #isExpression() expression} the value returned will be the non-expression value
     * or {@code null} if allowed for the property.
     *
     * @return the resolved value
     */
    T getResolvedValue();

    /**
     * Checks whether this is an expression or not.
     *
     * @return {@code true} if this is an expression, otherwise {@code false}
     */
    boolean isExpression();

    /**
     * Gets the value of the value which may or may not be an {@link #isExpression() expression}.
     *
     * @return the value
     */
    String getValue();

    @Override
    String toString();

    static <T> ValueExpression<T> constant(final T value) {
        return new ValueExpression<T>() {
            @Override
            public T getResolvedValue() {
                return value;
            }

            @Override
            public boolean isExpression() {
                return false;
            }

            @Override
            public String getValue() {
                return value == null ? null : String.valueOf(value);
            }
        };
    }

    static ValueExpression<String> constant(final String value) {
        return new ValueExpression<>() {
            @Override
            public String getResolvedValue() {
                return value;
            }

            @Override
            public boolean isExpression() {
                return false;
            }

            @Override
            public String getValue() {
                return value;
            }
        };
    }

    /**
     * Resolves the value expression from an expression.
     *
     * @param <T> the value type
     */
    public interface Resolver<T> {

        /**
         * Resolves the value of an expression.
         *
         * @param expression the expression to parse
         *
         * @return the value expression
         */
        ValueExpression<T> resolve(String expression);
    }

    public static Resolver<String> STRING_RESOLVER = new Resolver<String>() {

        private static final int INITIAL = 0;
        private static final int GOT_DOLLAR = 1;
        private static final int GOT_OPEN_BRACE = 2;
        private static final int RESOLVED = 3;
        private static final int DEFAULT = 4;

        @Override
        public ValueExpression<String> resolve(final String expression) {
            if (expression == null) return NULL_STRING_EXPRESSION;
            boolean isExpression = false;
            final StringBuilder builder = new StringBuilder();
            final char[] chars = expression.toCharArray();
            final int len = chars.length;
            int state = 0;
            int start = -1;
            int nameStart = -1;
            for (int i = 0; i < len; i++) {
                char ch = chars[i];
                switch (state) {
                    case INITIAL: {
                        switch (ch) {
                            case '$': {
                                state = GOT_DOLLAR;
                                continue;
                            }
                            default: {
                                builder.append(ch);
                                continue;
                            }
                        }
                        // not reachable
                    }
                    case GOT_DOLLAR: {
                        switch (ch) {
                            case '$': {
                                builder.append(ch);
                                state = INITIAL;
                                continue;
                            }
                            case '{': {
                                start = i + 1;
                                nameStart = start;
                                state = GOT_OPEN_BRACE;
                                continue;
                            }
                            default: {
                                // invalid; emit and resume
                                builder.append('$').append(ch);
                                state = INITIAL;
                                continue;
                            }
                        }
                        // not reachable
                    }
                    case GOT_OPEN_BRACE: {
                        switch (ch) {
                            case ':':
                            case '}':
                            case ',': {
                                final String name = expression.substring(nameStart, i).trim();
                                if ("/".equals(name)) {
                                    builder.append(File.separator);
                                    state = ch == '}' ? INITIAL : RESOLVED;
                                    continue;
                                } else if (":".equals(name)) {
                                    builder.append(File.pathSeparator);
                                    state = ch == '}' ? INITIAL : RESOLVED;
                                    continue;
                                }
                                isExpression = true;
                                String val = System.getProperty(name);
                                if (val == null && name.startsWith("env.")) {
                                    val = System.getenv(name);
                                }
                                if (val != null) {
                                    builder.append(val);
                                    state = ch == '}' ? INITIAL : RESOLVED;
                                    continue;
                                } else if (ch == ',') {
                                    nameStart = i + 1;
                                    continue;
                                } else if (ch == ':') {
                                    start = i + 1;
                                    state = DEFAULT;
                                    continue;
                                } else {
                                    builder.append(expression.substring(start - 2, i + 1));
                                    state = INITIAL;
                                    continue;
                                }
                            }
                            default: {
                                continue;
                            }
                        }
                        // not reachable
                    }
                    case RESOLVED: {
                        if (ch == '}') {
                            state = INITIAL;
                        }
                        continue;
                    }
                    case DEFAULT: {
                        if (ch == '}') {
                            state = INITIAL;
                            builder.append(expression.substring(start, i));
                        }
                        continue;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }
            switch (state) {
                case GOT_DOLLAR: {
                    builder.append('$');
                    break;
                }
                case DEFAULT:
                case GOT_OPEN_BRACE: {
                    builder.append(expression.substring(start - 2));
                    break;
                }
            }
            final String resolvedValue = builder.toString();
            if (isExpression) {
                return new ValueExpressionImpl<>(expression, resolvedValue);
            }
            return new ValueExpressionImpl<>(null, resolvedValue);
        }
    };

    public static Resolver<Boolean> BOOLEAN_RESOLVER = new Resolver<Boolean>() {
        @Override
        public ValueExpression<Boolean> resolve(final String expression) {
            if (expression == null) return NULL_BOOLEAN_EXPRESSION;
            final ValueExpression<String> stringResult = STRING_RESOLVER.resolve(expression);
            final Boolean value = stringResult.getResolvedValue() == null ? null : Boolean.valueOf(stringResult.getResolvedValue());
            return new ValueExpressionImpl<Boolean>(expression, value);
        }
    };
}
