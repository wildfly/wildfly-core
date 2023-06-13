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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * Helper to lazily build an object.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"UnusedReturnValue"})
public class ObjectUpdater<T> {
    private final T instance;
    private final Map<String, String> properties;
    private final Set<PropertyValue> definedProperties;
    private final Function<String, Object> definedValueResolver;
    private final ValueResolver valueResolver;

    private ObjectUpdater(final ContextConfiguration contextConfiguration,
                          final Class<? extends T> baseClass, final T instance) {
        this.instance = instance;
        properties = new LinkedHashMap<>();
        definedProperties = new LinkedHashSet<>();
        definedValueResolver = (name) -> {
            final PropertyValue propertyValue = findDefinedProperty(name);
            return propertyValue == null ? null : propertyValue.value.get();
        };
        valueResolver = ValueResolver.of(contextConfiguration, baseClass);
    }

    /**
     * Create a new {@link ObjectUpdater}.
     *
     * @param baseClass the base type
     * @param instance  the instance to update
     * @param <T>       the type being created
     *
     * @return a new {@link ObjectUpdater}
     */
    public static <T> ObjectUpdater<T> of(final ContextConfiguration contextConfiguration,
                                          final Class<? extends T> baseClass, final T instance) {
        return new ObjectUpdater<>(contextConfiguration, baseClass, instance);
    }

    /**
     * Adds a property to be set on the object after it has been created.
     *
     * @param name  the name of the property
     * @param value a string representing the value
     *
     * @return this builder
     */
    public ObjectUpdater<T> addProperty(final String name, final String value) {
        properties.put(name, value);
        return this;
    }

    public ObjectUpdater<T> clearProperty(final String name) {
        properties.put(name, null);
        return this;
    }

    /**
     * Adds a defined property to be set after the object is created.
     *
     * @param name  the name of the property
     * @param type  the type of the property
     * @param value a supplier for the property value
     *
     * @return this builder
     */
    public ObjectUpdater<T> addDefinedProperty(final String name, final Class<?> type, final Supplier<?> value) {
        definedProperties.add(new PropertyValue(name, type, value));
        return this;
    }

    /**
     * Creates an object when the {@linkplain Supplier#get() supplier} value is accessed.
     *
     * @return a supplier which can create the object
     */
    public T update() {
        final Map<String, String> properties = Map.copyOf(this.properties);
        final T instance = this.instance;
        final Class<?> actualClass = instance.getClass();
        final String className = instance.getClass().getName();
        // Get all the setters
        final Map<Method, Object> setters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            final Method method = getPropertySetter(actualClass, entry.getKey());
            if (method == null) {
                throw new IllegalArgumentException(String
                        .format("Failed to locate setter for property \"%s\" on type \"%s\"",
                                entry.getKey(),
                                className));
            }
            // Get the value type for the setter
            Class<?> type = getPropertyType(method);
            if (type == null) {
                throw new IllegalArgumentException(String
                        .format("Failed to determine type for setter \"%s\" on type \"%s\"",
                                method.getName(),
                                className));
            }
            setters.put(method, valueResolver.resolve(entry.getKey(), type, entry.getValue(), definedValueResolver));
        }

        // Define known type parameters
        for (PropertyValue value : definedProperties) {
            final String methodName = getPropertySetterName(value.name);
            try {
                final Method method = actualClass.getMethod(methodName, value.type);
                setters.put(method, value.value.get());
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(String.format(
                        "Failed to find setter method for property \"%s\" on type \"%s\"", value.name, className), e);
            }
        }
        try {
            // Execute setters
            for (Map.Entry<Method, Object> entry : setters.entrySet()) {
                entry.getKey().invoke(instance, entry.getValue());
            }
            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Failed to instantiate class \"%s\"", className), e);
        }
    }

    private PropertyValue findDefinedProperty(final String name) {
        for (PropertyValue value : definedProperties) {
            if (name.equals(value.name)) {
                return value;
            }
        }
        return null;
    }

    private static Class<?> getPropertyType(final Method setter) {
        return setter != null ? setter.getParameterTypes()[0] : null;
    }

    private static Method getPropertySetter(Class<?> clazz, String propertyName) {
        final String set = getPropertySetterName(propertyName);
        for (Method method : clazz.getMethods()) {
            if ((method.getName().equals(set) && Modifier.isPublic(method.getModifiers()))
                    && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }

    private static String getPropertySetterName(final String propertyName) {
        return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private static class PropertyValue implements Comparable<PropertyValue> {
        final String name;
        final Class<?> type;
        final Supplier<?> value;

        private PropertyValue(final String name, final Class<?> type, final Supplier<?> value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        @Override
        public int compareTo(final PropertyValue o) {
            return name.compareTo(o.name);
        }
    }
}
