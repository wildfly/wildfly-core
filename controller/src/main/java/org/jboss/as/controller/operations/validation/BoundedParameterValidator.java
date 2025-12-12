/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.Comparator;
import java.util.function.Function;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A parameter validator that enforces a lower and/or upper bound.
 * @author Paul Ferraro
 * @param <T> the resolved value type
 */
public interface BoundedParameterValidator<T> extends Bounded<T>, ParameterValidator {
    /**
     * Builds a bounded parameter validator.
     * @param <T> the resolved value type
     */
    interface Builder<T> extends Bounded<T> {
        /**
         * Applies the specified lower bound to resolved parameter values.
         * @param bound a lower bound.
         * @return a reference to this builder
         * @throws IllegalArgumentException if the specified bound is not compatible with a previously defined upper bound.
         */
        Builder<T> withLowerBound(Bound<T> bound);

        /**
         * Applies the specified upper bound to resolved parameter values.
         * @param bound an upper bound.
         * @return a reference to this builder
         * @throws IllegalArgumentException if the specified bound is not compatible with a previously defined lower bound.
         */
        Builder<T> withUpperBound(Bound<T> bound);

        /**
         * Builds a bounded parameter validator.
         * @return a bounded parameter validator.
         */
        BoundedParameterValidator<T> build();
    }

    /**
     * Creates a builder of a parameter validator that validates against integer bounds.
     * @return a builder of a parameter validator that validates against integer bounds.
     */
    static Builder<Integer> integerBuilder() {
        return builder(ModelType.INT, ModelNode::asIntOrNull);
    }

    /**
     * Creates a builder of a parameter validator that validates against long bounds.
     * @return a builder of a parameter validator that validates against long bounds.
     */
    static Builder<Long> longBuilder() {
        return builder(ModelType.LONG, ModelNode::asLongOrNull);
    }

    /**
     * Creates a builder of a parameter validator that validates against double bounds.
     * @return a builder of a parameter validator that validates against double bounds.
     */
    static Builder<Double> doubleBuilder() {
        return builder(ModelType.DOUBLE, ModelNode::asDoubleOrNull);
    }

    /**
     * Creates a builder of a parameter validator that validates against bounds.
     * @param <T> the resolved type
     * @param type the model type
     * @param resolver the resolved value type
     * @return a parameter validator builder
     */
    static <T extends Comparable<T>> Builder<T> builder(ModelType type, Function<ModelNode, T> resolver) {
        return builder(type, resolver, Comparator.naturalOrder());
    }

    /**
     * Creates a builder of a parameter validator that validates against bounds.
     * @param <T> the resolved type
     * @param type the model type
     * @param resolver the resolved value type
     * @param comparator the comparator used to validate any configured bounds
     * @return a parameter validator builder
     */
    public static <T> Builder<T> builder(ModelType type, Function<ModelNode, T> resolver, Comparator<T> comparator) {
        return new Builder<>() {
            private Bound<T> lower = null;
            private Bound<T> upper = null;

            @Override
            public Builder<T> withLowerBound(Bound<T> bound) {
                if (this.upper != null) {
                    // Ensure proposed lower bound is compatible with existing upper bound
                    int result = comparator.compare(bound.get(), this.upper.get());
                    if ((result > 0) || ((result == 0) && (bound.isExclusive() || this.upper.isExclusive()))) {
                        throw new IllegalArgumentException(bound.get().toString());
                    }
                }
                this.lower = bound;
                return this;
            }

            @Override
            public Builder<T> withUpperBound(Bound<T> bound) {
                if (this.lower != null) {
                    // Ensure proposed upper bound is compatible with existing lower bound
                    int result = comparator.compare(this.lower.get(), bound.get());
                    if ((result > 0) || ((result == 0) && (this.lower.isExclusive() || bound.isExclusive()))) {
                        throw new IllegalArgumentException(bound.get().toString());
                    }
                }
                this.upper = bound;
                return this;
            }

            @Override
            public Bound<T> getLowerBound() {
                return this.lower;
            }

            @Override
            public Bound<T> getUpperBound() {
                return this.upper;
            }

            @Override
            public BoundedParameterValidator<T> build() {
                return new BoundedValidator<>(type, resolver, comparator, this.lower, this.upper);
            }

            static class BoundedValidator<T> extends ModelTypeValidator implements BoundedParameterValidator<T> {
                private final Function<ModelNode, T> resolver;
                private final Comparator<T> comparator;
                private final Bound<T> lowerBound;
                private final Bound<T> upperBound;

                BoundedValidator(ModelType type, Function<ModelNode, T> resolver, Comparator<T> comparator, Bound<T> lowerBound, Bound<T> upperBound) {
                    super(type);
                    this.resolver = resolver;
                    this.comparator = comparator;
                    this.lowerBound = lowerBound;
                    this.upperBound = upperBound;
                }

                @Override
                public Bound<T> getLowerBound() {
                    return this.lowerBound;
                }

                @Override
                public Bound<T> getUpperBound() {
                    return this.upperBound;
                }

                @Override
                public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
                    super.validateParameter(parameterName, value);
                    if (value.isDefined() && (value.getType() != ModelType.EXPRESSION)) {
                        try {
                            T resolved = this.resolver.apply(value);
                            if (this.lowerBound != null) {
                                int result = this.comparator.compare(this.lowerBound.get(), resolved);
                                if (this.lowerBound.isExclusive()) {
                                    if (result >= 0) {
                                        throw ControllerLogger.MGMT_OP_LOGGER.exclusiveLowerBoundExceeded(parameterName, resolved, this.lowerBound.get());
                                    }
                                } else if (result > 0) {
                                    throw ControllerLogger.MGMT_OP_LOGGER.inclusiveLowerBoundExceeded(parameterName, resolved, this.lowerBound.get());
                                }
                            }
                            if (this.upperBound != null) {
                                int result = this.comparator.compare(resolved, this.upperBound.get());
                                if (this.upperBound.isExclusive()) {
                                    if (result >= 0) {
                                        throw ControllerLogger.MGMT_OP_LOGGER.exclusiveUpperBoundExceeded(parameterName, resolved, this.upperBound.get());
                                    }
                                } else if (result > 0) {
                                    throw ControllerLogger.MGMT_OP_LOGGER.inclusiveUpperBoundExceeded(parameterName, resolved, this.upperBound.get());
                                }
                            }
                        } catch (RuntimeException e) {
                            // Resolution failed
                            throw new OperationFailedException(e);
                        }
                    }
                }
            }
        };
    }
}
