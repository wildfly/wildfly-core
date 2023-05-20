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
public interface DefaultableQuaternaryServiceDescriptor<T> extends QuaternaryServiceDescriptor<T>, DefaultableServiceDescriptor<T> {

    static <T> DefaultableQuaternaryServiceDescriptor<T> of(String name, TernaryServiceDescriptor<T> defaultDescriptor) {
        return new DefaultableQuaternaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public TernaryServiceDescriptor<T> getDefaultServiceDescriptor() {
                return defaultDescriptor;
            }
        };
    }

    @Override
    TernaryServiceDescriptor<T> getDefaultServiceDescriptor();

    @Override
    default Map.Entry<String, String[]> resolve(String greatGrandparent, String grandparent, String parent, String child) {
        return (child != null) ? QuaternaryServiceDescriptor.super.resolve(greatGrandparent, grandparent, parent, child) : this.getDefaultServiceDescriptor().resolve(greatGrandparent, grandparent, parent);
    }

}
