/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

import java.util.Map;

/**
 * Describes a service by its name, provided value type, and name resolution mechanism.
 * @author Paul Ferraro
 * @param <T> the type of the value provided by the described service
 */
public interface NullaryServiceDescriptor<T> extends ServiceDescriptor<T> {

    /**
     * Creates a service descriptor with the specified name and type
     * @param <T> the service type
     * @param name the service name
     * @param type the service type
     * @return a service descriptor
     */
    static <T> NullaryServiceDescriptor<T> of(String name, Class<T> type) {
        return new NullaryServiceDescriptor<>() {
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
     * Resolves the constant name of the service.
     * @return a tuple containing the resolved name and zero segments
     */
    default Map.Entry<String, String[]> resolve() {
        return Map.entry(this.getName(), new String[0]);
    }
}
