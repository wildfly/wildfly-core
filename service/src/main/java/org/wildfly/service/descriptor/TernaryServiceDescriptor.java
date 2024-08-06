/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

import java.util.Map;

/**
 * Describes a service by its name, provided value type, and dynamic name resolution mechanism.
 * @author Paul Ferraro
 * @param <T> the type of the value provided by the described service
 */
public interface TernaryServiceDescriptor<T> extends ServiceDescriptor<T> {

    /**
     * Resolves the dynamic name the service using the specified segments.
     * @param grandparent the first dynamic segment
     * @param parent the second dynamic segment
     * @param child the third dynamic segment
     * @return a tuple containing the resolved name and dynamic segments, or null if segments are not resolvable.
     */
    default Map.Entry<String, String[]> resolve(String grandparent, String parent, String child) {
        return (grandparent != null) && (parent != null) && (child != null) ? Map.entry(this.getName(), new String[] { grandparent, parent, child }) : null;
    }

    @Override
    default <U extends T> TernaryServiceDescriptor<U> asType(Class<U> type) {
        return new TernaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return TernaryServiceDescriptor.this.getName();
            }

            @Override
            public Class<U> getType() {
                return type;
            }

            @Override
            public Map.Entry<String, String[]> resolve(String grandparent, String parent, String child) {
                return TernaryServiceDescriptor.this.resolve(grandparent, parent, child);
            }
        };
    }

    /**
     * Provides a three segment service descriptor.
     * Typically implemented by enumerations providing service descriptors of the same type.
     * @param <T> the service value type
     */
    interface Provider<T> extends ServiceDescriptor.Provider<T, TernaryServiceDescriptor<T>>, TernaryServiceDescriptor<T> {
        @Override
        default Map.Entry<String, String[]> resolve(String grandparent, String parent, String child) {
            return this.get().resolve(grandparent, parent, child);
        }
    }

    /**
     * Creates a ternary service descriptor with the specified name and type.
     * @param <T> the service type
     * @param name the service name
     * @param type the service type
     * @return a service descriptor
     */
    static <T> TernaryServiceDescriptor<T> of(String name, Class<T> type) {
        return new TernaryServiceDescriptor<>() {
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
     * Creates a ternary service descriptor with the specified name and default service descriptor.
     * @param <T> the service type
     * @param name the service name
     * @param defaultDescriptor the service descriptor used to resolve an undefined dynamic child name
     * @return a service descriptor
     */
    static <T> TernaryServiceDescriptor<T> of(String name, BinaryServiceDescriptor<T> defaultDescriptor) {
        return new TernaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Class<T> getType() {
                return defaultDescriptor.getType();
            }

            @Override
            public Map.Entry<String, String[]> resolve(String grandparent, String parent, String child) {
                return (child != null) ? TernaryServiceDescriptor.super.resolve(grandparent, parent, child) : defaultDescriptor.resolve(grandparent, parent);
            }
        };
    }
}
