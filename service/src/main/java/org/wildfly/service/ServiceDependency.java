/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
    default ServiceDependency<V> andThen(Consumer<? super ServiceBuilder<?>> after) {
        Objects.requireNonNull(after);
        return new ServiceDependency<>() {
            @Override
            public void accept(ServiceBuilder<?> builder) {
                ServiceDependency.this.accept(builder);
                after.accept(builder);
            }

            @Override
            public V get() {
                return ServiceDependency.this.get();
            }
        };
    }

    @Override
    default <R> ServiceDependency<R> map(Function<V, R> mapper) {
        Objects.requireNonNull(mapper);
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

    @Override
    default <T, R> ServiceDependency<R> combine(Dependency<ServiceBuilder<?>, T> dependency, BiFunction<V, T, R> mapper) {
        Objects.requireNonNull(dependency);
        Objects.requireNonNull(mapper);
        return new ServiceDependency<>() {
            @Override
            public void accept(ServiceBuilder<?> builder) {
                ServiceDependency.this.accept(builder);
                dependency.accept(builder);
            }

            @Override
            public R get() {
                return mapper.apply(ServiceDependency.this.get(), dependency.get());
            }
        };
    }

    /**
     * Returns an empty pseudo-dependency whose {@link #get()} returns null.
     * @param <V> the value type
     * @return an empty service dependency
     */
    @SuppressWarnings("unchecked")
    static <V> ServiceDependency<V> empty() {
        return (ServiceDependency<V>) SimpleServiceDependency.EMPTY;
    }

    /**
     * Returns a pseudo-dependency whose {@link #get()} returns the specified value.
     * @param <V> the value type
     * @param value a service value
     * @return a pseudo-dependency whose {@link #get()} returns the specified value.
     */
    static <V> ServiceDependency<V> of(V value) {
        return (value != null) ? new SimpleServiceDependency<>(value) : empty();
    }

    /**
     * Returns a pseudo-dependency whose {@link #get()} returns the value from the specified supplier.
     * @param <V> the value type
     * @param supplier a service value supplier
     * @return a pseudo-dependency whose {@link #get()} returns the value from the specified supplier.
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
        return (name != null) ? new DefaultServiceDependency<>(name) : empty();
    }

    class SimpleServiceDependency<V> extends SimpleDependency<ServiceBuilder<?>, V> implements ServiceDependency<V> {
        static final ServiceDependency<Object> EMPTY = new SimpleServiceDependency<>(null);

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
