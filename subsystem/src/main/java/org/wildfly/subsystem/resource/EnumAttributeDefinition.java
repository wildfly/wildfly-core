/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * An attribute definition that resolves enum values.
 * @author Paul Ferraro
 */
public class EnumAttributeDefinition<E extends Enum<E>> extends ResolvableAttributeDefinition<E> {
    private static final UnaryOperator<String> TO_LOWER_CASE = value -> value.toLowerCase(Locale.ENGLISH);
    private static final UnaryOperator<String> TO_UPPER_CASE = value -> value.toUpperCase(Locale.ENGLISH);

    EnumAttributeDefinition(Builder<E> builder) {
        super(builder);
    }

    /**
     * Creates a new enum attribute definition builder that uses case-sensitive enum name resolution.
     * @param <E> the enum type
     * @param attributeName the attribute name
     * @param type the enum type
     * @return a new enum attribute definition builder that uses case-sensitive enum name resolution.
     */
    public static <E extends Enum<E>> Builder<E> nameBuilder(String attributeName, Class<E> type) {
        return nameBuilder(attributeName, type, UnaryOperator.identity());
    }

    /**
     * Creates a new enum attribute definition builder that uses upper case enum name resolution.
     * @param <E> the enum type
     * @param attributeName the attribute name
     * @param type the enum type
     * @return a new enum attribute definition builder that uses upper case enum name resolution.
     * @throws IllegalStateException if the enum does not have unique upper case {@link Enum#name()} values.
     */
    public static <E extends Enum<E>> Builder<E> upperCaseNameBuilder(String attributeName, Class<E> type) {
        return nameBuilder(attributeName, type, TO_UPPER_CASE);
    }

    /**
     * Creates a new enum attribute definition builder that uses lower case enum name resolution.
     * @param <E> the enum type
     * @param attributeName the attribute name
     * @param type the enum type
     * @return a new enum attribute definition builder that uses lower case enum name resolution.
     * @throws IllegalStateException if the enum does not have unique lower case {@link Enum#name()} values.
     */
    public static <E extends Enum<E>> Builder<E> lowerCaseNameBuilder(String attributeName, Class<E> type) {
        return nameBuilder(attributeName, type, TO_LOWER_CASE);
    }

    private static <E extends Enum<E>> Builder<E> nameBuilder(String attributeName, Class<E> type, UnaryOperator<String> transformer) {
        return builder(attributeName, type, transformer.compose(Enum::name), transformer);
    }

    /**
     * Creates a new enum attribute definition builder that resolves enum via their {@link Object#toString()} representation.
     * @param <E> the enum type
     * @param attributeName the attribute name
     * @param type the enum type
     * @return a new enum attribute definition builder that uses standard enum name resolution.
     * @throws IllegalStateException if the enum does not have unique {@link Object#toString()} values.
     */
    public static <E extends Enum<E>> Builder<E> toStringBuilder(String attributeName, Class<E> type) {
        return builder(attributeName, type, Object::toString, UnaryOperator.identity());
    }

    private static <E extends Enum<E>> Builder<E> builder(String attributeName, Class<E> type, Function<E, String> formatter, UnaryOperator<String> transformer) {
        Map<String, E> values = EnumSet.allOf(type).stream().collect(Collectors.toMap(formatter, Function.identity()));
        return builder(attributeName, type, new Function<>() {
                @Override
                public E apply(String value) {
                    E result = values.get(transformer.apply(value));
                    if (result == null) {
                        throw new IllegalArgumentException(ControllerLogger.MGMT_OP_LOGGER.invalidValue(value, attributeName, values.keySet()));
                    }
                    return result;
                }
            }, formatter);
    }

    static <E extends Enum<E>> Builder<E> builder(String attributeName, Class<E> type, Function<String, E> resolver, Function<E, String> formatter) {
        return new Builder<>(attributeName, type, resolver, formatter);
    }

    /**
     * Builds an enum attribute definition.
     * Because enum validation and runtime resolution are tightly coupled, the following methods are unsupported:
     * <ul>
     * <li>{@link #setAllowedValues(int...)}</li>
     * <li>{@link #setAllowedValues(ModelNode...)}</li>
     * <li>{@link #setAllowedValues(String...)}</li>
     * <li>{@link #setDefaultValue(ModelNode)}</li>
     * <li>{@link #setValidator(ParameterValidator)}</li>
     * </ul>
     * @param <E> the enum type
     */
    public static class Builder<E extends Enum<E>> extends ResolvableAttributeDefinition.Builder<E, EnumAttributeDefinition<E>, Builder<E>> {

        Builder(String attributeName, Class<E> type, Function<String, E> resolver, Function<E, String> formatter) {
            super(attributeName, ModelType.STRING, resolver.compose(ModelNode::asString), formatter.andThen(ModelNode::new));
            this.setAllowedValues(EnumSet.allOf(type));
        }

        /**
         * Creates an attribute definition based on the specified attribute definition
         * @param basis the basis for this attribute definition
         */
        public Builder(EnumAttributeDefinition<E> basis) {
            this(basis.getName(), basis);
        }

        /**
         * Creates an attribute definition based on the specified attribute definition
         * @param attributeName the attribute name
         * @param basis the basis for this attribute definition
         */
        public Builder(String attributeName, EnumAttributeDefinition<E> basis) {
            super(attributeName, basis);
        }

        @Override
        public EnumAttributeDefinition<E> build() {
            return new EnumAttributeDefinition<>(this);
        }

        @Override
        protected Builder<E> builder() {
            return this;
        }
    }
}
