/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.common.function.Functions;

/**
 * Encapsulates service installation into a {@link ServiceTarget}.
 * @author Paul Ferraro
 */
public interface ServiceInstaller extends Installer<ServiceTarget> {

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the specified value.
     * @param <V> the service value type
     * @param value the service value
     * @return a service installer builder
     */
    static <V> Builder<V, V> builder(V value) {
        return builder(Functions.constantSupplier(value)).asActive();
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified dependency.
     * @param <V> the service value type
     * @param dependency a service dependency
     * @return a service installer builder
     */
    static <V> Builder<V, V> builder(ServiceDependency<V> dependency) {
        return builder(dependency).withDependency(dependency).asPassive();
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory.
     * @param <V> the service value type
     * @param factory provides the service value
     * @return a service installer builder
     */
    static <V> Builder<V, V> builder(Supplier<V> factory) {
        return builder(Function.identity(), factory);
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory and mapping function.
     * @param <T> the source value type
     * @param <V> the service value type
     * @param mapper a function that returns the service value given the value supplied by the factory
     * @param factory provides the input to the specified mapper
     * @return a service installer builder
     */
    static <T, V> Builder<T, V> builder(Function<T, V> mapper, Supplier<T> factory) {
        return new DefaultServiceInstaller.Builder<>(mapper, factory);
    }

    /**
     * Builds a {@link ServiceInstaller}.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface Builder<T, V> extends Installer.Builder<Builder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V> {

        /**
         * Indicates that the installed service should start and, if a stop task was specified, stop asynchronously.
         * @param executor supplies the executor used for asynchronous execution
         * @return a reference to this builder
         */
        Builder<T, V> async(Supplier<Executor> executor);
    }

    /**
     * Abstract installer using native MSC service installation
     * @param <T> the source value type
     * @param <V> the service value type
     */
    abstract class AbstractServiceInstaller<T, V> extends AbstractInstaller<ServiceTarget, ServiceBuilder<?>, ServiceBuilder<?>, T, V> {
        private static final Function<ServiceTarget, ServiceBuilder<?>> FACTORY = ServiceTarget::addService;

        protected AbstractServiceInstaller(Installer.Configuration<ServiceBuilder<?>, ServiceBuilder<?>, T, V> config) {
            super(config, FACTORY);
        }

        protected abstract static class Builder<B, I extends Installer<ServiceTarget>, T, V> extends AbstractInstaller.Builder<B, I, ServiceTarget, ServiceBuilder<?>, ServiceBuilder<?>, T, V> {

            protected Builder(Function<T, V> mapper, Supplier<T> factory) {
                super(mapper, factory);
            }
        }
    }

    /**
     * A default service installer for native MSC service installation.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    class DefaultServiceInstaller<T, V> extends AbstractServiceInstaller<T, V> implements ServiceInstaller {

        DefaultServiceInstaller(Installer.Configuration<ServiceBuilder<?>, ServiceBuilder<?>, T, V> config) {
            super(config);
        }

        static class Builder<T, V> extends AbstractServiceInstaller.Builder<ServiceInstaller.Builder<T, V>, ServiceInstaller, T, V> implements ServiceInstaller.Builder<T, V> {
            private volatile Supplier<Executor> executor = null;
            // If no stop task is specified, we can stop synchronously
            private volatile AsyncServiceBuilder.Async async = AsyncServiceBuilder.Async.START_ONLY;

            Builder(Function<T, V> mapper, Supplier<T> factory) {
                super(mapper, factory);
            }

            @Override
            public UnaryOperator<ServiceBuilder<?>> getServiceBuilderDecorator() {
                Supplier<Executor> executor = this.executor;
                AsyncServiceBuilder.Async async = this.async;
                return (executor == null) ? UnaryOperator.identity() : new UnaryOperator<>() {
                    @Override
                    public ServiceBuilder<?> apply(ServiceBuilder<?> builder) {
                        return new AsyncServiceBuilder<>(builder, executor, async);
                    }
                };
            }

            @Override
            public ServiceInstaller.Builder<T, V> async(Supplier<Executor> executor) {
                this.executor = executor;
                return this;
            }

            @Override
            public ServiceInstaller.Builder<T, V> onStop(Consumer<T> consumer) {
                this.async = AsyncServiceBuilder.Async.START_AND_STOP;
                return super.onStop(consumer);
            }

            @Override
            public ServiceInstaller build() {
                return new DefaultServiceInstaller<>(this);
            }

            @Override
            protected ServiceInstaller.Builder<T, V> builder() {
                return this;
            }
        }
    }
}
