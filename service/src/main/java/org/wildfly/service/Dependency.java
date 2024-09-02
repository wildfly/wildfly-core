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
import org.wildfly.common.function.Functions;

/**
 * Encapsulates logic for registering a value providing dependency with a service builder.
 * @author Paul Ferraro
 */
public interface Dependency<B extends ServiceBuilder<?>, V> extends Consumer<B>, Supplier<V> {

    /**
     * Returns a dependency whose value is the result of applying the specified mapping function.
     * @param <R> the mapped value type
     * @param mapper a mapping function
     * @return a dependency whose value is the result of applying the specified mapping function.
     */
    default <R> Dependency<B, R> map(Function<V, R> mapper) {
        return new Dependency<>() {
            @Override
            public void accept(B builder) {
                Dependency.this.accept(builder);
            }

            @Override
            public R get() {
                return mapper.apply(Dependency.this.get());
            }
        };
    }

    /**
     * Returns a dependency that combines this dependency with the specified dependency, whose value is determined by the specified mapping function.
     * @param <T> the other dependency type
     * @param <R> the mapped value type
     * @param dependency another dependency
     * @param mapper a mapping function
     * @return a dependency that combines this dependency with the specified dependency, whose value is determined by the specified mapping function.
     * @throws NullPointerException if {@code dependency} or {@code mapper} were null.
     */
    default <T, R> Dependency<B, R> combine(Dependency<B, T> dependency, BiFunction<V, T, R> mapper) {
        Objects.requireNonNull(dependency);
        Objects.requireNonNull(mapper);
        return new Dependency<>() {
            @Override
            public void accept(B builder) {
                Dependency.this.accept(builder);
                dependency.accept(builder);
            }

            @Override
            public R get() {
                return mapper.apply(Dependency.this.get(), dependency.get());
            }
        };
    }

    class SimpleDependency<B extends ServiceBuilder<?>, V> implements Dependency<B, V> {

        private final V value;

        protected SimpleDependency(V value) {
            this.value = value;
        }

        @Override
        public V get() {
            return this.value;
        }

        @Override
        public void accept(B builder) {
            // Nothing to register
        }
    }

    class SuppliedDependency<B extends ServiceBuilder<?>, V> implements Dependency<B, V> {

        private final Supplier<V> supplier;

        protected SuppliedDependency(Supplier<V> supplier) {
            this.supplier = supplier;
        }

        @Override
        public V get() {
            return this.supplier.get();
        }

        @Override
        public void accept(B builder) {
            // Nothing to register
        }
    }

    abstract class AbstractDependency<B extends ServiceBuilder<?>, V> implements Dependency<B, V>, Function<B, Supplier<V>> {

        private volatile Supplier<V> supplier = Functions.constantSupplier(null);

        @Override
        public V get() {
            return this.supplier.get();
        }

        @Override
        public void accept(B builder) {
            this.supplier = this.apply(builder);
        }
    }

    class DefaultDependency<B extends ServiceBuilder<?>, V> extends AbstractDependency<B, V> {

        private final ServiceName name;

        protected DefaultDependency(ServiceName name) {
            this.name = name;
        }

        @Override
        public Supplier<V> apply(B builder) {
            return builder.requires(this.name);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof DefaultDependency)) return false;
            return this.name.equals(((DefaultDependency<?, ?>) object).name);
        }

        @Override
        public String toString() {
            return this.name.toString();
        }
    }
}
