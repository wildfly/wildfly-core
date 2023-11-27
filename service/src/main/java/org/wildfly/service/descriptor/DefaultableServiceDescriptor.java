/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

/**
 * Describes a service by its name, provided value type, and default descriptor.
 * @author Paul Ferraro
 * @param <T> the type of the value provided by the described service
 */
public interface DefaultableServiceDescriptor<T> extends ServiceDescriptor<T> {

    /**
     * Identifies the default service descriptor associated with this capability service descriptor.
     * @return a service descriptor
     */
    ServiceDescriptor<T> getDefaultServiceDescriptor();

    @Override
    default Class<T> getType() {
        return this.getDefaultServiceDescriptor().getType();
    }
}
