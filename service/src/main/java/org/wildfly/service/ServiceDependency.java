/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * Encapsulates a dependency on a service value.
 * @author Paul Ferraro
 */
public interface ServiceDependency<V> extends Dependency<ServiceBuilder<?>, V> {

    @Override
    default <R> ServiceDependency<R> map(Function<V, R> mapper) {
        return new ServiceDependency<>() {
            @Override
            public void accept(ServiceBuilder<?> builder) {
                ServiceDependency.this.accept(builder);
            }

            @Override
            public R get() {
                return mapper.apply(ServiceDependency.this.get());
            }
        };
    }

    /**
     * Returns a pseudo-dependency whose {@link #get()} returns the specified value.
     * @param <V> the value type
     * @param value a service value
     * @return a service dependency
     */
    @SuppressWarnings("unchecked")
    static <V> ServiceDependency<V> of(V value) {
        return (value != null) ? new SimpleServiceDependency<>(value) : (ServiceDependency<V>) SimpleServiceDependency.NULL;
    }

    /**
     * Returns a pseudo-dependency whose {@link #get()} returns the value from the specified supplier.
     * @param <V> the value type
     * @param supplier a service value supplier
     * @return a service dependency
     * @throws NullPointerException if {@code supplier} was null
     */
    static <V> ServiceDependency<V> from(Supplier<V> supplier) {
        Objects.requireNonNull(supplier);
        return new SuppliedServiceDependency<>(supplier);
    }

    /**
     * Returns a dependency on the service with the specified name.
     * @param <V> the dependency type
     * @param name a service name
     * @return a service dependency
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

    class SuppliedServiceDependency<V> extends SuppliedDependency<ServiceBuilder<?>, V> implements ServiceDependency<V> {
        SuppliedServiceDependency(Supplier<V> supplier) {
            super(supplier);
        }
    }

    class DefaultServiceDependency<V> extends DefaultDependency<ServiceBuilder<?>, V> implements ServiceDependency<V> {

        DefaultServiceDependency(ServiceName name) {
            super(name);
        }
    }
}
