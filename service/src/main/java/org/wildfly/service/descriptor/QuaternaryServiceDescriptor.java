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
public interface QuaternaryServiceDescriptor<T> extends ServiceDescriptor<T> {

    /**
     * Resolves the dynamic name the service using the specified segments.
     * @param greatGrandparent the first dynamic segment
     * @param grandparent the second dynamic segment
     * @param parent the third dynamic segment
     * @param child the fourth dynamic segment
     * @return a tuple containing the resolved name and dynamic segments
     */
    default Map.Entry<String, String[]> resolve(String greatGrandparent, String grandparent, String parent, String child) {
        return Map.entry(this.getName(), new String[] {
                Assert.checkNotNullParamWithNullPointerException("greatGrandparent", greatGrandparent),
                Assert.checkNotNullParamWithNullPointerException("grandparent", grandparent),
                Assert.checkNotNullParamWithNullPointerException("parent", parent),
                Assert.checkNotNullParamWithNullPointerException("child", child)
        });
    }

    @Override
    default <U extends T> QuaternaryServiceDescriptor<U> asType(Class<U> type) {
        return new QuaternaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return QuaternaryServiceDescriptor.this.getName();
            }

            @Override
            public Class<U> getType() {
                return type;
            }

            @Override
            public Map.Entry<String, String[]> resolve(String greatGrandparent, String grandparent, String parent, String child) {
                return QuaternaryServiceDescriptor.this.resolve(greatGrandparent, grandparent, parent, child);
            }
        };
    }

    /**
     * Provides a four segment service descriptor.
     * Typically implemented by enumerations providing service descriptors of the same type.
     * @param <T> the service value type
     */
    interface Provider<T> extends ServiceDescriptor.Provider<T, QuaternaryServiceDescriptor<T>>, QuaternaryServiceDescriptor<T> {
        @Override
        default Map.Entry<String, String[]> resolve(String greatGrandparent, String grandparent, String parent, String child) {
            return this.get().resolve(greatGrandparent, grandparent, parent, child);
        }
    }

    /**
     * Creates a quaternary service descriptor with the specified name and type.
     * @param <T> the service type
     * @param name the service name
     * @param type the service type
     * @return a service descriptor
     */
    static <T> QuaternaryServiceDescriptor<T> of(String name, Class<T> type) {
        return new QuaternaryServiceDescriptor<>() {
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
     * Creates a quaternary service descriptor with the specified name and default service descriptor.
     * @param <T> the service type
     * @param name the service name
     * @param defaultDescriptor the service descriptor used to resolve an undefined dynamic child name
     * @return a service descriptor
     */
    static <T> QuaternaryServiceDescriptor<T> of(String name, TernaryServiceDescriptor<T> defaultDescriptor) {
        return new QuaternaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Class<T> getType() {
                return defaultDescriptor.getType();
            }

            @Override
            public  Map.Entry<String, String[]> resolve(String greatGrandparent, String grandparent, String parent, String child) {
                return (child != null) ? QuaternaryServiceDescriptor.super.resolve(greatGrandparent, grandparent, parent, child) : defaultDescriptor.resolve(greatGrandparent, grandparent, parent);
            }
        };
    }
}
