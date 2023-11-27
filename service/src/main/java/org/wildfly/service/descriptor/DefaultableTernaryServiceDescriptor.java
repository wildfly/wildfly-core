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
public interface DefaultableTernaryServiceDescriptor<T> extends TernaryServiceDescriptor<T>, DefaultableServiceDescriptor<T> {

    static <T> DefaultableTernaryServiceDescriptor<T> of(String name, BinaryServiceDescriptor<T> defaultDescriptor) {
        return new DefaultableTernaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public BinaryServiceDescriptor<T> getDefaultServiceDescriptor() {
                return defaultDescriptor;
            }
        };
    }

    @Override
    BinaryServiceDescriptor<T> getDefaultServiceDescriptor();

    @Override
    default Map.Entry<String, String[]> resolve(String grandparent, String parent, String child) {
        return (child != null) ? TernaryServiceDescriptor.super.resolve(grandparent, parent, child) : this.getDefaultServiceDescriptor().resolve(grandparent, parent);
    }
}
