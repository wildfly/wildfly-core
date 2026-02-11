/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.as.controller.operations.validation.Bound;
import org.jboss.as.controller.operations.validation.Bounded;
import org.jboss.as.controller.operations.validation.BoundedParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Paul Ferraro
 */
public class BoundedAttributeDefinition<T> extends ResolvableAttributeDefinition<T> implements Bounded<T> {

    private final Comparator<T> comparator;
    private final Bounded<T> bounds;

    protected BoundedAttributeDefinition(Builder<T, ? extends BoundedAttributeDefinition<T>, ?> builder, Bounded<T> bounds) {
        super(builder);
        this.bounds = bounds;
        this.comparator = builder.comparator;
    }

    Comparator<T> getComparator() {
        return this.comparator;
    }

    @Override
    public Bound<T> getLowerBound() {
        return this.bounds.getLowerBound();
    }

    @Override
    public Bound<T> getUpperBound() {
        return this.bounds.getUpperBound();
    }

    /**
     * Builds a duration-based attribute definition.
     * By default, this attribute allows expressions and may not define a negative duration.
     * Default bounds may be overridden via {@link #withLowerBound(Bound)} and/or {@link #withUpperBound(Bound)}.
     * Because validation and runtime resolution are tightly coupled, the following methods are unsupported.
     * <ul>
     * <li>{@link #setAllowedValues(int...)}</li>
     * <li>{@link #setAllowedValues(ModelNode...)}</li>
     * <li>{@link #setAllowedValues(String...)}</li>
     * <li>{@link #setDefaultValue(ModelNode)}</li>
     * <li>{@link #setValidator(org.jboss.as.controller.operations.validation.ParameterValidator)}</li>
     * </ul>
     */
    public abstract static class Builder<T, A extends BoundedAttributeDefinition<T>, B extends Builder<T, A, B>> extends ResolvableAttributeDefinition.Builder<T, A, B> {

        private final Function<ModelNode, T> resolver;
        private final Comparator<T> comparator;
        private final BoundedParameterValidator.Builder<T> validatorBuilder;

        protected Builder(String attributeName, ModelType type, Function<ModelNode, T> resolver, Function<T, ModelNode> formatter, Comparator<T> comparator) {
            super(attributeName, type, resolver, formatter);
            this.resolver = resolver;
            this.comparator = comparator;
            this.validatorBuilder = BoundedParameterValidator.builder(type, resolver, comparator);
        }

        /**
         * Creates a new attribute definition builder based on the specified attribute definition.
         * @param basis the basis for this attribute definition builder
         */
        protected Builder(A basis) {
            this(basis.getName(), basis);
        }

        /**
         * Creates a new attribute definition builder based on the specified attribute definition.
         * @param attributeName the name of this attribute
         * @param basis the basis for this attribute definition builder
         */
        protected Builder(String attributeName, A basis) {
            super(attributeName, basis);
            this.resolver = basis.getResolver();
            this.comparator = basis.getComparator();
            super.setValidator(null);
            if (!basis.getAllowedValues().isEmpty()) {
                super.setAllowedValues(basis.getAllowedValues().stream().map(this.resolver).collect(Collectors.toSet()));
            } else {
                this.withValidator(null);
            }
            this.validatorBuilder = BoundedParameterValidator.builder(this.getType(), this.resolver, this.comparator).withLowerBound(basis.getLowerBound()).withUpperBound(basis.getUpperBound());
        }

        /**
         * Requires a lower bound for the resolved duration.
         * @param lowerBound a lower bound
         * @return a reference to this builder
         */
        public B withLowerBound(Bound<T> lowerBound) {
            if ((lowerBound != null) && (this.getDefaultValue() != null)) {
                // Default value must be within bounds
                this.validateLowerBound(this.resolver.apply(this.getDefaultValue()), lowerBound);
            }
            this.validatorBuilder.withLowerBound(lowerBound);
            return this.builder();
        }

        /**
         * Requires both lower and upper bounds for the resolved duration.
         * @param upperBound an upper bound
         * @return a reference to this builder
         */
        public B withUpperBound(Bound<T> upperBound) {
            if ((upperBound != null) && (this.getDefaultValue() != null)) {
                // Default value must be within bounds
                this.validateUpperBound(this.resolver.apply(this.getDefaultValue()), upperBound);
            }
            this.validatorBuilder.withUpperBound(upperBound);
            return this.builder();
        }

        @Override
        public B setDefaultValue(T defaultValue) {
            if (defaultValue != null) {
                // Default value must be within bounds
                this.validateLowerBound(defaultValue, this.validatorBuilder.getLowerBound());
                this.validateUpperBound(defaultValue, this.validatorBuilder.getUpperBound());
            }
            return super.setDefaultValue(defaultValue);
        }

        private void validateLowerBound(T value, Bound<T> lowerBound) {
            if (lowerBound != null) {
                int compare = this.comparator.compare(lowerBound.get(), value);
                if ((compare > 0) || ((compare == 0) && lowerBound.isExclusive())) {
                    throw new IllegalArgumentException(lowerBound.get().toString());
                }
            }
        }

        private void validateUpperBound(T value, Bound<T> upperBound) {
            if (upperBound != null) {
                int compare = this.comparator.compare(value, upperBound.get());
                if ((compare > 0) || ((compare == 0) && upperBound.isExclusive())) {
                    throw new IllegalArgumentException(upperBound.get().toString());
                }
            }
        }

        @Override
        public A build() {
            BoundedParameterValidator<T> validator = this.validatorBuilder.build();
            this.withValidator(validator);
            return this.build(validator);
        }

        protected abstract A build(Bounded<T> bounds);
    }
}
