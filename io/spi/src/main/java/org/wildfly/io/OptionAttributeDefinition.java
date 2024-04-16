/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.io;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.xnio.Option;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class OptionAttributeDefinition extends SimpleAttributeDefinition {
    private final Option option;
    private final Class<?> optionType;

    private OptionAttributeDefinition(Builder builder, Option<?> option, Class<?> optionType) {
        super(builder);
        this.option = option;
        this.optionType = optionType;

    }

    public Option<?> getOption() {
        return option;
    }

    public Class<?> getOptionType() {
        return optionType;
    }

    @SuppressWarnings("unchecked")
    public OptionMap.Builder resolveOption(final ExpressionResolver context, final ModelNode model, OptionMap.Builder builder) throws OperationFailedException {
        ModelNode value = resolveModelAttribute(context, model);
        if (value.isDefined()) {
            if (getType() == ModelType.INT) {
                builder.set((Option<Integer>) option, value.asInt());
            } else if (getType() == ModelType.LONG) {
                builder.set(option, value.asLong());
            } else if (getType() == ModelType.BOOLEAN) {
                builder.set(option, value.asBoolean());
            } else if (optionType.isEnum()) {
                builder.set(option, option.parseValue(value.asString(), option.getClass().getClassLoader()));
            }else if (option.getClass().getSimpleName().equals("SequenceOption")) {
                builder.setSequence(option, value.asString().split("\\s*,\\s*"));
            } else if (getType() == ModelType.STRING) {
                builder.set(option, value.asString());
            } else {
                throw new OperationFailedException("Don't know how to handle: " + option + " with value: " + value);
            }
        }
        return builder;
    }

    public static Builder builder(String attributeName, Option<?> option) {
        return new Builder(attributeName, option);
    }

    public static class Builder extends AbstractAttributeDefinitionBuilder<Builder, OptionAttributeDefinition> {
        private Option<?> option;
        private Class<?> optionType;

        public Builder(String attributeName, Option<?> option) {
            this(attributeName, option, null);
        }

        public Builder(String attributeName, Option<?> option, ModelNode defaultValue) {
            super(attributeName, determineModelType(option), true);
            this.option = option;
            this.optionType = determineOptionType(option); // Not happy with the duplicate call :\
            setDefaultValue(defaultValue);
        }

        @Override
        public OptionAttributeDefinition build() {
            return new OptionAttributeDefinition(this, option, optionType);
        }

        private static ModelType determineModelType(Option<?> option) {
                Class<?> optionType = determineOptionType(option);

                if (optionType.isAssignableFrom(Integer.class)) {
                    return ModelType.INT;
                } else if (optionType.isAssignableFrom(Long.class)) {
                    return ModelType.LONG;
                } else if (optionType.isAssignableFrom(BigInteger.class)) {
                    return ModelType.BIG_INTEGER;
                } else if (optionType.isAssignableFrom(Double.class)) {
                    return ModelType.DOUBLE;
                } else if (optionType.isAssignableFrom(BigDecimal.class)) {
                    return ModelType.BIG_DECIMAL;
                } else if (optionType.isEnum() || optionType.isAssignableFrom(String.class)) {
                    return ModelType.STRING;
                } else if (optionType.isAssignableFrom(Boolean.class)) {
                    return ModelType.BOOLEAN;
                } else {
                    return ModelType.UNDEFINED;
                }
        }

        private static Class<?> determineOptionType(Option<?> option) {
            try {
                final Field typeField;
                if (option.getClass().getSimpleName().equals("SequenceOption")) {
                    typeField = option.getClass().getDeclaredField("elementType");
                } else {
                    typeField = option.getClass().getDeclaredField("type");
                }

                typeField.setAccessible(true);
                Class<?> optionType = (Class<?>) typeField.get(option);
                return optionType;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }


}
