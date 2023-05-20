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
public interface DefaultableBinaryServiceDescriptor<T> extends BinaryServiceDescriptor<T>, DefaultableServiceDescriptor<T> {

    static <T> DefaultableBinaryServiceDescriptor<T> of(String name, UnaryServiceDescriptor<T> defaultDescriptor) {
        return new DefaultableBinaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public UnaryServiceDescriptor<T> getDefaultServiceDescriptor() {
                return defaultDescriptor;
            }
        };
    }

    @Override
    UnaryServiceDescriptor<T> getDefaultServiceDescriptor();

    @Override
    default Map.Entry<String, String[]> resolve(String parent, String child) {
        return (child != null) ? BinaryServiceDescriptor.super.resolve(parent, child) : this.getDefaultServiceDescriptor().resolve(parent);
    }
}
