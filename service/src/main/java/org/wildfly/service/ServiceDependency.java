/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * Encapsulates a dependency on a service value.
 * @author Paul Ferraro
 */
public interface ServiceDependency<V> extends Dependency<ServiceBuilder<?>, V> {

    /**
     * Returns a pseudo-dependency supplying a fixed value.
     * @return a dependency supplier
     */
    @SuppressWarnings("unchecked")
    static <V> ServiceDependency<V> of(V value) {
        return (value != null) ? new SimpleServiceDependency<>(value) : (ServiceDependency<V>) SimpleServiceDependency.NULL;
    }

    /**
     * Returns a dependency on the service with the specified name.
     * @return a dependency supplier
     */
    static <V> ServiceDependency<V> on(ServiceName name) {
        return (name != null) ? new DefaultServiceDependency<>(name) : of(null);
    }

    class SimpleServiceDependency<V> extends SimpleDependency<ServiceBuilder<?>, V> implements ServiceDependency<V> {
        static final ServiceDependency<Object> NULL = new SimpleServiceDependency<>(null);

        SimpleServiceDependency(V value) {
            super(value);
        }
    }

    class DefaultServiceDependency<V> extends DefaultDependency<ServiceBuilder<?>, V> implements ServiceDependency<V> {

        DefaultServiceDependency(ServiceName name) {
            super(name);
        }
    }
}
