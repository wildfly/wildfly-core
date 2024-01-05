/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

import java.util.Map;

import org.wildfly.common.Assert;

/**
 * Describes a service by its name, provided value type, and dynamic name resolution mechanism.
 * @author Paul Ferraro
 * @param <T> the type of the value provided by the described service
 */
public interface UnaryServiceDescriptor<T> extends ServiceDescriptor<T> {

    /**
     * Resolves the dynamic name of the service using the specified segment.
     * @param reference a dynamic segment
     * @return a tuple containing the resolved name and dynamic segments
     */
    default Map.Entry<String, String[]> resolve(String reference) {
        return Map.entry(this.getName(), new String[] {
                Assert.checkNotNullParamWithNullPointerException("reference", reference)
        });
    }

    /**
     * Provides a one segment service descriptor.
     * Typically implemented by enumerations providing service descriptors of the same type.
     * @param <T> the service value type
     */
    interface Provider<T> extends ServiceDescriptor.Provider<T, UnaryServiceDescriptor<T>>, UnaryServiceDescriptor<T> {
        @Override
        default Map.Entry<String, String[]> resolve(String reference) {
            return this.get().resolve(reference);
        }
    }

    /**
     * Creates a unary service descriptor with the specified name and type.
     * @param <T> the service type
     * @param name the service name
     * @param type the service type
     * @return a service descriptor
     */
    static <T> UnaryServiceDescriptor<T> of(String name, Class<T> type) {
        return new UnaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Class<T> getType() {
                return type;
            }
        };
    }

    /**
     * Creates a unary service descriptor with the specified name and default service descriptor.
     * @param <T> the service type
     * @param name the service name
     * @param defaultDescriptor the service descriptor used to resolve an undefined dynamic name
     * @return a service descriptor
     */
    static <T> UnaryServiceDescriptor<T> of(String name, NullaryServiceDescriptor<T> defaultDescriptor) {
        return new UnaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Class<T> getType() {
                return defaultDescriptor.getType();
            }

            @Override
            public Map.Entry<String, String[]> resolve(String name) {
                return (name != null) ? UnaryServiceDescriptor.super.resolve(name) : defaultDescriptor.resolve();
            }
        };
    }
}
