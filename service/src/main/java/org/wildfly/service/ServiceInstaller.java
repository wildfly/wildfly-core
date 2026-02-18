/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.Functions;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Encapsulates service installation into a {@link ServiceTarget}.
 * @author Paul Ferraro
 */
public interface ServiceInstaller extends Installer<ServiceTarget> {

    /**
     * Builds an installer of a service.
     */
    interface Builder extends Installer.Builder<Builder, ServiceInstaller, ServiceTarget, ServiceBuilder<?>> {

        /**
         * Returns a {@link ServiceInstaller} builder that installs the specified installer into a child target.
         * By default, the installed service will start when installed.
         * @param installer a service installer
         * @return a service installer builder
         */
        static Builder of(ServiceInstaller installer) {
            return new DefaultBuilder(new org.jboss.msc.Service() {
                @Override
                public void start(StartContext context) {
                    installer.install(context.getChildTarget());
                }

                @Override
                public void stop(StopContext context) {
                    // Services installed into child target are auto-removed after this service stops.
                }
            }).startWhen(StartWhen.INSTALLED);
        }
    }

    /**
     * Builds an installer of a service that provides a value.
     * @param <V> the service value type
     */
    interface ValueBuilder<B, V> extends Installer.ValueBuilder<B, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, V> {

        /**
         * Configures the {@link ServiceName} of the value provided by the installed service, created from the specified descriptor.
         * @param descriptor a service descriptor
         * @return a reference to this builder
         */
        default B provides(NullaryServiceDescriptor<V> descriptor) {
            return this.provides(ServiceName.parse(descriptor.getName()));
        }

        /**
         * Configures the {@link ServiceName} of the value provided by the installed service, created from the specified descriptor.
         * @param descriptor a service descriptor
         * @param name a dynamic segment
         * @return a reference to this builder
         */
        default B provides(UnaryServiceDescriptor<V> descriptor, String name) {
            Map.Entry<String, String[]> resolved = descriptor.resolve(name);
            return this.provides(ServiceName.parse(resolved.getKey()).append(resolved.getValue()));
        }

        /**
         * Configures the {@link ServiceName} of the value provided by the installed service, created from the specified descriptor.
         * @param descriptor a service descriptor
         * @param parent the first dynamic segment
         * @param child the second dynamic segment
         * @return a reference to this builder
         */
        default B provides(BinaryServiceDescriptor<V> descriptor, String parent, String child) {
            Map.Entry<String, String[]> resolved = descriptor.resolve(parent, child);
            return this.provides(ServiceName.parse(resolved.getKey()).append(resolved.getValue()));
        }

        /**
         * Configures the {@link ServiceName} of the value provided by the installed service, created from the specified descriptor.
         * @param descriptor a service descriptor
         * @param grandparent the first dynamic segment
         * @param parent the second dynamic segment
         * @param child the third dynamic segment
         * @return a reference to this builder
         */
        default B provides(TernaryServiceDescriptor<V> descriptor, String grandparent, String parent, String child) {
            Map.Entry<String, String[]> resolved = descriptor.resolve(grandparent, parent, child);
            return this.provides(ServiceName.parse(resolved.getKey()).append(resolved.getValue()));
        }

        /**
         * Configures the {@link ServiceName} of the value provided by the installed service, created from the specified descriptor.
         * @param descriptor a service descriptor
         * @param greatGrandparent the first dynamic segment
         * @param grandparent the second dynamic segment
         * @param parent the third dynamic segment
         * @param child the fourth dynamic segment
         * @return a reference to this builder
         */
        default B provides(QuaternaryServiceDescriptor<V> descriptor, String greatGrandparent, String grandparent ,String parent, String child) {
            Map.Entry<String, String[]> resolved = descriptor.resolve(greatGrandparent, grandparent, parent, child);
            return this.provides(ServiceName.parse(resolved.getKey()).append(resolved.getValue()));
        }
    }

    /**
     * Builds an installer of a service derived from a value with a configurable blocking lifecycle.
     * Unless explicitly specified, services installed via this builder will start when required, if providing a value (via {@link ValueBuilder#provides(ServiceName)}; or when available, otherwise.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface BlockingBuilder<T, V> extends Installer.BlockingLifecycleBuilder<BlockingBuilder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V>, ValueBuilder<BlockingBuilder<T, V>, V> {
        @Override
        <R> BlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(Supplier<V> provider) {
            return new DefaultBlockingBuilder<>(provider, Function.identity());
        }

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides a value supplied by a service dependency.
         * @param <V> the service value type
         * @param dependency the dependency providing the service value
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(ServiceDependency<V> dependency) {
            Supplier<V> provider = dependency;
            return of(provider).requires(dependency);
        }

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory, made non-blocking via the specified executor.
         * @param <V> the service value type
         * @param provider provides the service value
         * @param executor the executor used to run blocking operations
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(Supplier<V> provider, ServiceDependency<Executor> executor) {
            return new AsyncBlockingBuilder<>(provider, Function.identity(), executor);
        }
    }

    /**
     * Builds an installer of a service derived from a value with a configurable non-blocking lifecycle.
     * Unless explicitly specified, services installed via this builder will start when required, if providing a value (via {@link ValueBuilder#provides(ServiceName)}; or when available, otherwise.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface NonBlockingBuilder<T, V> extends Installer.NonBlockingLifecycleBuilder<NonBlockingBuilder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V>, ValueBuilder<NonBlockingBuilder<T, V>, V> {
        @Override
        <R> NonBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> NonBlockingBuilder<V, V> of(Supplier<CompletionStage<V>> provider) {
            return new DefaultNonBlockingBuilder<>(provider, Function.identity());
        }
    }

    /**
     * Builds an installer of a service derived from a value with an inherent blocking lifecycle.
     * Unless explicitly specified, services installed via this builder will start when required, if providing a value (via {@link ValueBuilder#provides(ServiceName)}; or when available, otherwise.
     * @param <V> the provided value type
     */
    interface BlockingLifecycleBuilder<V> extends ValueBuilder<BlockingLifecycleBuilder<V>, V> {
        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides a value with an inherent blocking lifecycle.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> BlockingLifecycleBuilder<V> of(Supplier<V> provider) {
            return new DefaultBlockingLifecycleBuilder<>(BlockingBuilder.of(provider).withLifecycle(Function.identity()));
        }

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides a value with an inherent blocking lifecycle, made non-blocking via the specified executor.
         * @param <V> the service value type
         * @param provider provides the service value
         * @param executor the executor used to run blocking operations
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> BlockingLifecycleBuilder<V> of(Supplier<V> provider, ServiceDependency<Executor> executor) {
            return new DefaultBlockingLifecycleBuilder<>(BlockingBuilder.of(provider, executor).withLifecycle(Function.identity()));
        }
    }

    /**
     * Builds an installer of a service derived from a value with an inherent non-blocking lifecycle.
     * Unless explicitly specified, services installed via this builder will start when required, if providing a value (via {@link ValueBuilder#provides(ServiceName)}; or when available, otherwise.
     * @param <V> the provided value type
     */
    interface NonBlockingLifecycleBuilder<V> extends ValueBuilder<NonBlockingLifecycleBuilder<V>, V> {
        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides a value with an inherent non-blocking lifecycle.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends NonBlockingLifecycle> NonBlockingLifecycleBuilder<V> of(Supplier<CompletionStage<V>> provider) {
            return new DefaultNonBlockingLifecycleBuilder<>(NonBlockingBuilder.of(provider).withLifecycle(Function.identity()));
        }
    }

    /**
     * Implemented by builders with asynchronous service support.
     * @param <B> the builder type
     * @deprecated Superseded by {@link ServiceInstaller.BlockingLifecycleBuilder#of(Supplier, ServiceDependency)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    interface AsyncBuilder<B> {
        /**
         * Indicates that the installed service should start and, if a stop task was specified, stop asynchronously.
         * @param executor supplies the executor used for asynchronous execution
         * @return a reference to this builder
         */
        B async(Supplier<Executor> executor);
    }

    /**
     * Builds a {@link ServiceInstaller} whose service provides a single value.
     * @param <T> the source value type
     * @param <V> the service value type
     * @deprecated Superseded by {@link ServiceInstaller.BlockingBuilder}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    interface UnaryBuilder<T, V> extends AsyncBuilder<UnaryBuilder<T, V>>, ValueBuilder<UnaryBuilder<T, V>, V>, Installer.UnaryBuilder<UnaryBuilder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V> {
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the specified value.
     * By default, the installed service will start when installed since the provided value is already available.
     * @param <V> the service value type
     * @param value the service value
     * @return a service installer builder
     * @deprecated Superseded by {@link ServiceInstaller.BlockingBuilder#of(Supplier)}
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <V> UnaryBuilder<V, V> builder(V value) {
        return builder(Functions.constantSupplier(value)).startWhen(StartWhen.INSTALLED);
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified dependency.
     * By default, the installed service will start when the specified dependency is available.
     * @param <V> the service value type
     * @param dependency a service dependency
     * @return a service installer builder
     * @deprecated Superseded by {@link ServiceInstaller.BlockingBuilder#of(ServiceDependency)}
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <V> UnaryBuilder<V, V> builder(ServiceDependency<V> dependency) {
        Supplier<V> supplier = dependency;
        return builder(supplier).requires(dependency).startWhen(StartWhen.AVAILABLE);
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory.
     * By default, the installed service will start when required.
     * @param <V> the service value type
     * @param factory provides the service value
     * @return a service installer builder
     * @deprecated Superseded by {@link ServiceInstaller.BlockingBuilder#of(Supplier)}
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <V> UnaryBuilder<V, V> builder(Supplier<V> factory) {
        return builder(Function.identity(), factory);
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory and mapping function.
     * By default, the installed service will start when required.
     * @param <T> the source value type
     * @param <V> the service value type
     * @param mapper a function that returns the service value given the value supplied by the factory
     * @param factory provides the input to the specified mapper
     * @return a service installer builder
     * @deprecated Superseded by {@link ServiceInstaller.BlockingBuilder#of(Supplier)} followed by {@link ServiceInstaller.BlockingBuilder#map(Function)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <T, V> UnaryBuilder<T, V> builder(Function<T, V> mapper, Supplier<T> factory) {
        return new LegacyBlockingBuilder<>(factory, mapper).startWhen(StartWhen.REQUIRED);
    }

    /**
     * Returns a {@link ServiceInstaller} builder that installs the specified installer into a child target.
     * By default, the installed service will start when installed.
     * @param installer a service installer
     * @return a service installer builder
     * @deprecated Superseded by {@link ServiceInstaller.Builder#of(ServiceInstaller)}
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static Builder builder(ServiceInstaller installer) {
        return Builder.of(installer);
    }

    /**
     * Returns a {@link ServiceInstaller} builder that executes the specified tasks on {@link Service#start(StartContext)} and {@link Service#stop(StopContext)}, respectively.
     * By default, the installed service will start when available.
     * @param startTask a start task
     * @param stopTask a stop task
     * @return a service installer builder
     * @deprecated Superseded by {@link ServiceInstaller.BlockingBuilder#of(Supplier)} followed by {@link ServiceInstaller.BlockingBuilder#withLifecycle(Function)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static Builder builder(Runnable startTask, Runnable stopTask) {
        return new DefaultBuilder(new Service() {
            @Override
            public void start(StartContext context) {
                startTask.run();
            }

            @Override
            public void stop(StopContext context) {
                stopTask.run();
            }
        });
    }

    class DefaultBuilder extends Installer.AbstractBuilder<Builder, ServiceInstaller, ServiceTarget, ServiceBuilder<?>> implements Builder {
        private final org.jboss.msc.Service service;

        DefaultBuilder(org.jboss.msc.Service service) {
            this.service = service;
        }

        @Override
        public Builder get() {
            return this;
        }

        @Override
        public Function<? super ServiceBuilder<?>, org.jboss.msc.Service> getServiceFactory() {
            return new DiscardingFunction<>(this.service);
        }

        @Override
        public ServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class DefaultBlockingBuilder<T, V> extends AbstractBlockingLifecycleBuilder<BlockingBuilder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V> implements BlockingBuilder<T, V> {

        <R> DefaultBlockingBuilder(DefaultBlockingBuilder<T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
        }

        DefaultBlockingBuilder(Supplier<T> provider, Function<? super T, ? extends V> mapper) {
            super(provider, mapper);
        }

        @Override
        public BlockingBuilder<T, V> get() {
            return this;
        }

        @Override
        public <R> BlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultBlockingBuilder<>(this, mapper);
        }

        @Override
        public ServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class DefaultNonBlockingBuilder<T, V> extends AbstractNonBlockingLifecycleBuilder<NonBlockingBuilder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V> implements NonBlockingBuilder<T, V> {

        private <R> DefaultNonBlockingBuilder(DefaultNonBlockingBuilder<T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
        }

        DefaultNonBlockingBuilder(Supplier<CompletionStage<T>> provider, Function<? super T, ? extends V> mapper) {
            super(provider, mapper);
        }

        @Override
        public NonBlockingBuilder<T, V> get() {
            return this;
        }

        @Override
        public <R> NonBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultNonBlockingBuilder<>(this, mapper);
        }

        @Override
        public ServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class AsyncBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<BlockingBuilder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V> implements BlockingBuilder<T, V> {
        private final NonBlockingBuilder<T, V> builder;
        private final Supplier<Executor> executor;

        AsyncBlockingBuilder(Supplier<T> provider, Function<? super T, ? extends V> mapper, ServiceDependency<Executor> executor) {
            this(new DefaultNonBlockingBuilder<T, V>(compose(provider, executor), mapper).requires(executor), executor);
        }

        private AsyncBlockingBuilder(NonBlockingBuilder<T, V> builder, Supplier<Executor> executor) {
            super(builder, executor);
            this.builder = builder;
            this.executor = executor;
        }

        @Override
        public BlockingBuilder<T, V> get() {
            return this;
        }

        @Override
        public <R> AsyncBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper) {
            return new AsyncBlockingBuilder<>(this.builder.map(mapper), this.executor);
        }
    }

    class DefaultBlockingLifecycleBuilder<T extends BlockingLifecycle, V> extends AbstractValueBuilderDecorator<BlockingLifecycleBuilder<V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, V> implements BlockingLifecycleBuilder<V> {
        private final BlockingBuilder<T, V> builder;

        DefaultBlockingLifecycleBuilder(BlockingBuilder<T, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public BlockingLifecycleBuilder<V> get() {
            return this;
        }

        @Override
        public <R> BlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultBlockingLifecycleBuilder<>(this.builder.map(mapper));
        }
    }

    class DefaultNonBlockingLifecycleBuilder<T extends NonBlockingLifecycle, V> extends AbstractValueBuilderDecorator<NonBlockingLifecycleBuilder<V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, V> implements NonBlockingLifecycleBuilder<V> {
        private final NonBlockingBuilder<T, V> builder;

        DefaultNonBlockingLifecycleBuilder(NonBlockingBuilder<T, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public NonBlockingLifecycleBuilder<V> get() {
            return this;
        }

        @Override
        public <R> NonBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultNonBlockingLifecycleBuilder<>(this.builder.map(mapper));
        }
    }

    @Deprecated
    class LegacyBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<UnaryBuilder<T, V>, ServiceInstaller, ServiceTarget, ServiceBuilder<?>, T, V> implements UnaryBuilder<T, V> {
        private static final Supplier<Executor> DEFAULT_EXECUTOR = Functions.constantSupplier(Runnable::run);

        private final AtomicReference<Supplier<Executor>> reference;

        LegacyBlockingBuilder(Supplier<T> provider, Function<T, V> mapper) {
            this(provider, mapper, new AtomicReference<>(DEFAULT_EXECUTOR));
        }

        private LegacyBlockingBuilder(Supplier<T> provider, Function<T, V> mapper, AtomicReference<Supplier<Executor>> reference) {
            super(new DefaultNonBlockingBuilder<>(new Supplier<>() {
                @Override
                public CompletionStage<T> get() {
                    try {
                        return CompletableFuture.supplyAsync(provider, reference.getPlain().get());
                    } catch (RejectedExecutionException e) {
                        return CompletableFuture.supplyAsync(provider);
                    }
                }
            }, mapper), () -> reference.getPlain().get());
            this.reference = reference;
        }

        @Override
        public UnaryBuilder<T, V> async(Supplier<Executor> executor) {
            this.reference.setPlain(executor);
            return this;
        }

        @Override
        public UnaryBuilder<T, V> get() {
            return this;
        }
    }

    class DefaultServiceInstaller extends Installer.AbstractInstaller<ServiceTarget, ServiceBuilder<?>> implements ServiceInstaller {

        DefaultServiceInstaller(Installer.Configuration<ServiceBuilder<?>> config) {
            super(config, ServiceTarget::addService);
        }
    }
}
