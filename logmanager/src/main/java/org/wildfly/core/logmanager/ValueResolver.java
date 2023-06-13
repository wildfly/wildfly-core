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

package org.wildfly.core.logmanager;

import java.nio.charset.Charset;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.logging.Level;

import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * Resolves values for handlers, filters, formatters or POJO's for the log manager.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ValueResolver {

    private final ContextConfiguration contextConfiguration;
    // TODO (jrp) should this be required?
    private final Class<?> beanClass;

    private ValueResolver(final ContextConfiguration contextConfiguration, final Class<?> beanClass) {
        this.contextConfiguration = contextConfiguration;
        this.beanClass = beanClass;
    }

    static ValueResolver of(final ContextConfiguration contextConfiguration, final Class<?> beanClass) {
        return new ValueResolver(contextConfiguration, beanClass);
    }

    Object resolve(final String propertyName, final Class<?> paramType, final String value, final Function<String, Object> definedResolver) {
        if (value == null) {
            if (paramType.isPrimitive()) {
                final var result = primitiveDefault(paramType);
                if (result != null) {
                    return result;
                }
                throw new IllegalArgumentException(
                        String.format("Cannot assign null value to primitive property \"%s\" of %s", propertyName, beanClass));
            }
            return null;
        }
        final var trimmedValue = value.trim();
        if (paramType == String.class) {
            // Don't use the trimmed value for strings
            return value;
        } else if (paramType == Level.class) {
            return contextConfiguration.getContext().getLevelForName(trimmedValue);
        } else if (paramType == java.util.logging.Logger.class) {
            return contextConfiguration.getContext().getLogger(trimmedValue);
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return Boolean.valueOf(trimmedValue);
        } else if (paramType == byte.class || paramType == Byte.class) {
            return Byte.valueOf(trimmedValue);
        } else if (paramType == short.class || paramType == Short.class) {
            return Short.valueOf(trimmedValue);
        } else if (paramType == int.class || paramType == Integer.class) {
            return Integer.valueOf(trimmedValue);
        } else if (paramType == long.class || paramType == Long.class) {
            return Long.valueOf(trimmedValue);
        } else if (paramType == float.class || paramType == Float.class) {
            return Float.valueOf(trimmedValue);
        } else if (paramType == double.class || paramType == Double.class) {
            return Double.valueOf(trimmedValue);
        } else if (paramType == char.class || paramType == Character.class) {
            return !trimmedValue.isEmpty() ? trimmedValue.charAt(0) : 0;
        } else if (paramType == TimeZone.class) {
            return TimeZone.getTimeZone(trimmedValue);
        } else if (paramType == Charset.class) {
            return Charset.forName(trimmedValue);
        } else if (paramType.isAssignableFrom(Level.class)) {
            return Level.parse(trimmedValue);
        } else if (paramType.isEnum()) {
            return Enum.valueOf(paramType.asSubclass(Enum.class), trimmedValue);
        } else if (contextConfiguration.hasObject(trimmedValue)) {
            return contextConfiguration.getObject(trimmedValue);
        } else {
            if (definedResolver != null) {
                final var result = definedResolver.apply(propertyName);
                if (result != null) {
                    return result;
                }
            }
            throw new IllegalArgumentException("Unknown parameter type for property " + propertyName + " on " + beanClass);
        }
    }

    private Object primitiveDefault(final Class<?> paramType) {
        if (paramType == boolean.class) {
            return false;
        } else if (paramType == byte.class) {
            return (byte) 0x00;
        } else if (paramType == short.class) {
            return (short) 0;
        } else if (paramType == int.class) {
            return 0;
        } else if (paramType == long.class) {
            return 0L;
        } else if (paramType == float.class) {
            return 0.0f;
        } else if (paramType == double.class) {
            return 0.0d;
        } else if (paramType == char.class) {
            return '\u0000';
        }
        return null;
    }
}
