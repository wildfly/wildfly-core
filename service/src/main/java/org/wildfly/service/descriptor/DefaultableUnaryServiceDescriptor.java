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
public interface DefaultableUnaryServiceDescriptor<T> extends UnaryServiceDescriptor<T>, DefaultableServiceDescriptor<T> {

    /**
     * Creates a unary service descriptor with the specified name and default descriptor
     * @param <T> the service type
     * @param name the service name
     * @param defaultDescriptor the default descriptor
     * @return a service descriptor
     */
    static <T> DefaultableUnaryServiceDescriptor<T> of(String name, NullaryServiceDescriptor<T> defaultDescriptor) {
        return new DefaultableUnaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public NullaryServiceDescriptor<T> getDefaultServiceDescriptor() {
                return defaultDescriptor;
            }
        };
    }

    @Override
    NullaryServiceDescriptor<T> getDefaultServiceDescriptor();

    @Override
    default Map.Entry<String, String[]> resolve(String name) {
        return (name != null) ? UnaryServiceDescriptor.super.resolve(name) : this.getDefaultServiceDescriptor().resolve();
    }
}
