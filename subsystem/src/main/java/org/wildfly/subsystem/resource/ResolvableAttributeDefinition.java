/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import java.util.Set;
import java.util.function.Function;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ChainedParameterValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.SetValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * An attribute definition that can auto-resolve to some value.
 * @author Paul Ferraro
 */
public class ResolvableAttributeDefinition<T> extends AttributeDefinition implements ResourceModelResolver<T> {

    private final Function<ModelNode, T> resolver;
    private final Function<T, ModelNode> formatter;

    protected ResolvableAttributeDefinition(Builder<T, ? extends ResolvableAttributeDefinition<T>, ?> builder) {
        super(builder);
        this.resolver = builder.resolver;
        this.formatter = builder.formatter;
    }

    protected Function<ModelNode, T> getResolver() {
        return this.resolver;
    }

    protected Function<T, ModelNode> getFormatter() {
        return this.formatter;
    }

    @Override
    public T resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode value = this.resolveModelAttribute(context, model);
        return value.isDefined() ? this.resolver.apply(value) : null;
    }

    /**
     * Builds an auto-resolving attribute definition.
     * By default, this attribute allows expressions.
     * Because validation and runtime resolution are tightly coupled, the following methods will throw {@link UnsupportedOperationException}.
     * <ul>
     * <li>{@link #setAllowedValues(int...)}</li>
     * <li>{@link #setAllowedValues(ModelNode...)}</li>
     * <li>{@link #setAllowedValues(String...)}</li>
     * <li>{@link #setDefaultValue(ModelNode)}</li>
     * <li>{@link #setValidator(org.jboss.as.controller.operations.validation.ParameterValidator)}</li>
     * </ul>
     */
    public abstract static class Builder<T, A extends ResolvableAttributeDefinition<T>, B extends Builder<T, A, B>> extends AbstractAttributeDefinitionBuilder<B, A> {

        private Set<T> allowed = Set.of();
        private final Function<ModelNode, T> resolver;
        private final Function<T, ModelNode> formatter;

        protected Builder(String attributeName, ModelType type, Function<ModelNode, T> resolver, Function<T, ModelNode> formatter) {
            super(attributeName, type);
            this.setAllowExpression(true);
            this.setFlags(Flag.RESTART_RESOURCE_SERVICES);
            this.resolver = resolver;
            this.formatter = formatter;
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
            this.formatter = basis.getFormatter();
        }

        protected abstract B builder();

        protected B withValidator(ParameterValidator validator) {
            ParameterValidator existing = this.getValidator();
            return super.setValidator((existing != null) && (validator != null) ? new ChainedParameterValidator(existing, validator) : validator);
        }

        public B setAllowedValues(Set<T> values) {
            // If default value was defined, ensure it is be allowed
            if (!values.isEmpty() && (this.getDefaultValue() != null) && !values.contains(this.resolver.apply(this.getDefaultValue()))) {
                throw new IllegalArgumentException(values.toString());
            }
            this.allowed = values;
            return this.withValidator(!values.isEmpty() ? new SetValidator<>(this.getType(), this.resolver, this.formatter, values) : null);
        }

        public B setDefaultValue(T value) {
            if (value != null) {
                // If values are constrained, default value must be allowed
                if (!this.allowed.isEmpty() && !this.allowed.contains(value)) {
                    throw new IllegalArgumentException(value.toString());
                }
                this.setRequired(false);
                super.setDefaultValue(this.formatter.apply(value));
            }
            return this.builder();
        }

        @Override
        public B setDefaultValue(ModelNode defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public B setAllowedValues(ModelNode... allowedValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public B setAllowedValues(String... allowedValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public B setAllowedValues(int... allowedValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public B setValidator(ParameterValidator validator) {
            throw new UnsupportedOperationException();
        }
    }
}
