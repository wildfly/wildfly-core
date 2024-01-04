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
public interface BinaryServiceDescriptor<T> extends ServiceDescriptor<T> {

    /**
     * Resolves the dynamic name of the service using the specified segments.
     * @param parent the first dynamic segment
     * @param child the second dynamic segment
     * @return a tuple containing the resolved name and dynamic segments
     */
    default Map.Entry<String, String[]> resolve(String parent, String child) {
        return Map.entry(this.getName(), new String[] {
                Assert.checkNotNullParamWithNullPointerException("parent", parent),
                Assert.checkNotNullParamWithNullPointerException("child", child)
        });
    }

    /**
     * Provides a two segment service descriptor.
     * Typically implemented by enumerations providing service descriptors of the same type.
     * @param <T> the service value type
     */
    interface Provider<T> extends ServiceDescriptor.Provider<T, BinaryServiceDescriptor<T>>, BinaryServiceDescriptor<T> {
        @Override
        default Map.Entry<String, String[]> resolve(String parent, String child) {
            return this.get().resolve(parent, child);
        }
    }

    /**
     * Creates a binary service descriptor with the specified name and type.
     * @param <T> the service type
     * @param name the service name
     * @param type the service type
     * @return a service descriptor
     */
    static <T> BinaryServiceDescriptor<T> of(String name, Class<T> type) {
        return new BinaryServiceDescriptor<>() {
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
     * Creates a binary service descriptor with the specified name and default descriptor.
     * @param <T> the service type
     * @param name the service name
     * @param defaultDescriptor the service descriptor used to resolve an undefined dynamic child name
     * @return a service descriptor
     */
    static <T> BinaryServiceDescriptor<T> of(String name, UnaryServiceDescriptor<T> defaultDescriptor) {
        return new BinaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Class<T> getType() {
                return defaultDescriptor.getType();
            }

            @Override
            public Map.Entry<String, String[]> resolve(String parent, String child) {
                return (child != null) ? BinaryServiceDescriptor.super.resolve(parent, child) : defaultDescriptor.resolve(parent);
            }
        };
    }
}
