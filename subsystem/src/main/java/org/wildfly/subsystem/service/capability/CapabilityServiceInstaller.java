/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capability;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.server.suspend.SuspendPriority;
import org.jboss.as.server.suspend.SuspendableActivityRegistrar;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.common.function.Functions;
import org.wildfly.service.BlockingLifecycle;
import org.wildfly.service.Installer;
import org.wildfly.service.NonBlockingLifecycle;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.Suspendable;
import org.wildfly.subsystem.service.SuspendableNonBlockingLifecycle;

/**
 * A {@link ResourceServiceInstaller} that encapsulates service installation into a {@link CapabilityServiceTarget}.
 * @author Paul Ferraro
 */
public interface CapabilityServiceInstaller extends ResourceServiceInstaller, Installer<CapabilityServiceTarget> {
    @Override
    default Consumer<OperationContext> install(OperationContext context) {
        ServiceController<?> controller = this.install(context.getCapabilityServiceTarget());
        return new Consumer<>() {
            @Override
            public void accept(OperationContext context) {
                context.removeService(controller);
            }
        };
    }

    /**
     * Builds an installer of a service that provides a value.
     * @param <B> the builder type
     * @param <V> the provided value type
     */
    interface ValueBuilder<B, V> extends Installer.ValueBuilder<B, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, V> {
        @Override
        <R> ValueBuilder<?, R> map(Function<? super V, ? extends R> mapper);
    }

    /**
     * Builds an installer of a service derived from a value with a configurable blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface BlockingBuilder<T, V> extends Installer.BlockingLifecycleBuilder<BlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V>, ValueBuilder<BlockingBuilder<T, V>, V> {
        @Override
        <R> BlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified provider.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(RuntimeCapability<Void> capability, Supplier<V> provider) {
            return new DefaultBlockingBuilder<>(capability, provider, Function.identity());
        }

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified service dependency.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param dependency the dependency providing the service value
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(RuntimeCapability<Void> capability, ServiceDependency<V> dependency) {
            Supplier<V> provider = dependency;
            return of(capability, provider).requires(dependency);
        }

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via asynchronously executed blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @param executor an executor dependency
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(RuntimeCapability<Void> capability, Supplier<V> provider, ServiceDependency<Executor> executor) {
            return new DefaultAsyncBlockingBuilder<>(capability, provider, Function.identity(), executor);
        }
    }

    /**
     * Builds an installer of a service derived from a value with a configurable non-blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface NonBlockingBuilder<T, V> extends Installer.NonBlockingLifecycleBuilder<NonBlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V>, ValueBuilder<NonBlockingBuilder<T, V>, V> {
        @Override
        <R> NonBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> NonBlockingBuilder<V, V> of(RuntimeCapability<Void> capability, Supplier<CompletionStage<V>> provider) {
            return new DefaultNonBlockingBuilder<>(capability, provider, Function.identity());
        }
    }

    /**
     * Builds an installer of a service derived from a value with an inherent lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <V> the provided value type
     */
    interface BlockingLifecycleBuilder<V> extends ValueBuilder<BlockingLifecycleBuilder<V>, V> {
        @Override
        <R> BlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> BlockingLifecycleBuilder<V> of(RuntimeCapability<Void> capability, Supplier<V> provider) {
            return new DefaultBlockingLifecycleBuilder<>(BlockingBuilder.of(capability, provider).withLifecycle(Function.identity()));
        }

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via asynchronously executed blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @param executor an executor dependency
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> BlockingLifecycleBuilder<V> of(RuntimeCapability<Void> capability, Supplier<V> provider, ServiceDependency<Executor> executor) {
            return new DefaultBlockingLifecycleBuilder<>(BlockingBuilder.of(capability, provider, executor).withLifecycle(Function.identity()));
        }
    }


    /**
     * Builds an installer of a service derived from a value with an inherent lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <V> the provided value type
     */
    interface NonBlockingLifecycleBuilder<V> extends ValueBuilder<NonBlockingLifecycleBuilder<V>, V> {
        @Override
        <R> NonBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends NonBlockingLifecycle> NonBlockingLifecycleBuilder<V> of(RuntimeCapability<Void> capability, Supplier<CompletionStage<V>> provider) {
            return new DefaultNonBlockingLifecycleBuilder<>(NonBlockingBuilder.of(capability, provider).withLifecycle(Function.identity()));
        }
    }

    /**
     * Builds an installer of a service derived from a value that auto-stops on suspend and auto-starts on resume.
     * @param <B> the builder type
     * @param <V> the provided value type
     */
    interface SuspendableValueBuilder<B, V> extends ValueBuilder<B, V>, Suspendable<B> {
        @Override
        <R> SuspendableValueBuilder<?, R> map(Function<? super V, ? extends R> mapper);
    }

    /**
     * Builds an installer of a service derived from a value with a configurable suspendable blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface SuspendableBlockingBuilder<T, V> extends Installer.BlockingLifecycleBuilder<SuspendableBlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V>, SuspendableValueBuilder<SuspendableBlockingBuilder<T, V>, V> {
        @Override
        <R> SuspendableBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> SuspendableBlockingBuilder<V, V> of(RuntimeCapability<Void> capability, Supplier<V> provider) {
            return new DefaultSuspendableBlockingBuilder<>(capability, provider, Function.identity());
        }
    }

    /**
     * Builds an installer of a service derived from a value with a configurable suspendable non-blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface SuspendableNonBlockingBuilder<T, V> extends Installer.NonBlockingLifecycleBuilder<SuspendableNonBlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V>, SuspendableValueBuilder<SuspendableNonBlockingBuilder<T, V>, V> {
        @Override
        <R> SuspendableNonBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> SuspendableNonBlockingBuilder<V, V> of(RuntimeCapability<Void> capability, Supplier<CompletionStage<V>> provider) {
            return new DefaultSuspendableNonBlockingBuilder<>(capability, provider, Function.identity());
        }
    }

    /**
     * Builds an installer of a service derived from a value with a suspendable blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <V> the provided value type
     */
    interface SuspendableBlockingLifecycleBuilder<V> extends SuspendableValueBuilder<SuspendableBlockingLifecycleBuilder<V>, V> {
        @Override
        <R> SuspendableBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> SuspendableBlockingLifecycleBuilder<V> of(RuntimeCapability<Void> capability, Supplier<V> provider) {
            return new DefaultSuspendableBlockingLifecycleBuilder<>(SuspendableBlockingBuilder.of(capability, provider).withLifecycle(Function.identity()));
        }
    }


    /**
     * Builds an installer of a service derived from a value with a suspendable blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <V> the provided value type
     */
    interface SuspendableNonBlockingLifecycleBuilder<V> extends SuspendableValueBuilder<SuspendableNonBlockingLifecycleBuilder<V>, V> {
        @Override
        <R> SuspendableNonBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link CapabilityServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param capability the capability providing the service value
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends NonBlockingLifecycle> SuspendableNonBlockingLifecycleBuilder<V> forNonBlocking(RuntimeCapability<Void> capability, Supplier<CompletionStage<V>> provider) {
            return new DefaultSuspendableNonBlockingLifecycleBuilder<>(SuspendableNonBlockingBuilder.of(capability, provider).withLifecycle(Function.identity()));
        }
    }

    /**
     * Builds a {@link CapabilityServiceInstaller}.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    @Deprecated(forRemoval = true, since = "32.0")
    interface Builder<T, V> extends Installer.BlockingBuilder<Builder<T, V>>, Installer.UnaryBuilder<Builder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V> {
        @Override
        default <R> Builder<?, R> map(Function<? super V, ? extends R> mapper) {
            // Legacy builders do not support fluent mapping
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns a {@link CapabilityServiceInstaller} builder for the specified capability whose installed service provides the specified value.
     * By default, the installed service will start when installed since the provided value is already available.
     * @param <V> the service value type
     * @param capability the target capability
     * @param value the service value
     * @return a service installer builder
     * @deprecated Superseded by {@link CapabilityServiceInstaller.BlockingBuilder#of(RuntimeCapability, Supplier)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <V> Builder<V, V> builder(RuntimeCapability<Void> capability, V value) {
        return builder(capability, Functions.constantSupplier(value)).startWhen(StartWhen.INSTALLED);
    }

    /**
     * Returns a {@link CapabilityServiceInstaller} builder for the specified capability whose installed service provides the value supplied by the specified dependency.
     * By default, the installed service will start when the specified dependency is available.
     * @param <V> the service value type
     * @param capability the target capability
     * @param dependency a service dependency
     * @return a service installer builder
     * @deprecated Superseded by {@link CapabilityServiceInstaller.BlockingBuilder#of(RuntimeCapability, ServiceDependency)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <V> Builder<V, V> builder(RuntimeCapability<Void> capability, ServiceDependency<V> dependency) {
        Supplier<V> supplier = dependency;
        return builder(capability, supplier).requires(dependency).startWhen(StartWhen.AVAILABLE);
    }

    /**
     * Returns a {@link CapabilityServiceInstaller} builder for the specified capability whose installed service provides the value supplied by the specified factory.
     * By default, the installed service will start when required.
     * @param <V> the service value type
     * @param capability the target capability
     * @param factory provides the service value
     * @return a service installer builder
     * @deprecated Superseded by {@link BlockingBuilder#of(RuntimeCapability, Supplier, ServiceDependency)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <V> Builder<V, V> builder(RuntimeCapability<Void> capability, Supplier<V> factory) {
        return builder(capability, Function.identity(), factory);
    }

    /**
     * Returns a {@link CapabilityServiceInstaller} builder for the specified capability whose installed service provides the value supplied by the specified factory and mapping function.
     * By default, the installed service will start when required.
     * @param <T> the source value type
     * @param <V> the service value type
     * @param capability the target capability
     * @param mapper a function that returns the service value given the value supplied by the factory
     * @param factory provides the input to the specified mapper
     * @return a service installer builder
     * @deprecated Superseded by {@link BlockingBuilder#of(RuntimeCapability, Supplier, ServiceDependency)} followed by {@link BlockingBuilder#map(Function)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <T, V> Builder<T, V> builder(RuntimeCapability<Void> capability, Function<T, V> mapper, Supplier<T> factory) {
        return new LegacyBlockingBuilder<>(capability, factory, mapper);
    }

    class DefaultBlockingBuilder<T, V> extends AbstractBlockingLifecycleBuilder<BlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V> implements BlockingBuilder<T, V> {
        private final RuntimeCapability<Void> capability;

        private <R> DefaultBlockingBuilder(DefaultBlockingBuilder<T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
            this.capability = builder.capability;
            this.withDefaultMode(ServiceController.Mode.ON_DEMAND);
        }

        DefaultBlockingBuilder(RuntimeCapability<Void> capability, Supplier<T> provider, Function<? super T, ? extends V> mapper) {
            super(provider, mapper);
            this.capability = capability;
        }

        @Override
        protected BiFunction<CapabilityServiceBuilder<?>, Collection<ServiceName>, Consumer<V>> provides() {
            return new RuntimeCapabilityProvider<>(this.capability);
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
        public CapabilityServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class DefaultNonBlockingBuilder<T, V> extends AbstractNonBlockingLifecycleBuilder<NonBlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V> implements NonBlockingBuilder<T, V> {
        private final RuntimeCapability<Void> capability;

        private <R> DefaultNonBlockingBuilder(DefaultNonBlockingBuilder<T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
            this.capability = builder.capability;
        }

        DefaultNonBlockingBuilder(RuntimeCapability<Void> capability, Supplier<CompletionStage<T>> provider, Function<? super T, ? extends V> mapper) {
            super(provider, mapper);
            this.capability = capability;
            this.withDefaultMode(ServiceController.Mode.ON_DEMAND);
        }

        @Override
        protected BiFunction<CapabilityServiceBuilder<?>, Collection<ServiceName>, Consumer<V>> provides() {
            return new RuntimeCapabilityProvider<>(this.capability);
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
        public CapabilityServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class DefaultAsyncBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<BlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V> implements BlockingBuilder<T, V> {
        private final NonBlockingBuilder<T, V> builder;
        private final Supplier<Executor> executor;

        private DefaultAsyncBlockingBuilder(RuntimeCapability<Void> capability, Supplier<T> provider, Function<? super T, ? extends V> mapper, ServiceDependency<Executor> executor) {
            this(new DefaultNonBlockingBuilder<T, V>(capability, compose(provider, executor), mapper), executor);
            this.requires(executor);
        }

        private DefaultAsyncBlockingBuilder(NonBlockingBuilder<T, V> builder, Supplier<Executor> executor) {
            super(builder, executor);
            this.builder = builder;
            this.executor = executor;
        }

        @Override
        public BlockingBuilder<T, V> get() {
            return this;
        }

        @Override
        public <R> BlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultAsyncBlockingBuilder<>(this.builder.map(mapper), this.executor);
        }
    }

    class DefaultBlockingLifecycleBuilder<T extends BlockingLifecycle, V> extends AbstractValueBuilderDecorator<BlockingLifecycleBuilder<V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, V> implements BlockingLifecycleBuilder<V> {
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

    class DefaultNonBlockingLifecycleBuilder<T extends NonBlockingLifecycle, V> extends AbstractValueBuilderDecorator<NonBlockingLifecycleBuilder<V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, V> implements NonBlockingLifecycleBuilder<V> {
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

    class DefaultSuspendableNonBlockingBuilder<T, V> extends AbstractNonBlockingLifecycleBuilder<SuspendableNonBlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V> implements SuspendableNonBlockingBuilder<T, V> {
        private final RuntimeCapability<Void> capability;
        private final AtomicReference<SuspendPriority> priority;

        private <R> DefaultSuspendableNonBlockingBuilder(DefaultSuspendableNonBlockingBuilder<T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
            this.capability = builder.capability;
            this.priority = builder.priority;
        }

        DefaultSuspendableNonBlockingBuilder(RuntimeCapability<Void> capability, Supplier<CompletionStage<T>> provider, Function<? super T, ? extends V> mapper) {
            super(provider, mapper);
            this.capability = capability;
            this.priority = new AtomicReference<>(SuspendPriority.DEFAULT);
        }

        @Override
        public SuspendableNonBlockingBuilder<T, V> withSuspendPriority(SuspendPriority priority) {
            this.priority.setPlain(priority);
            return this;
        }

        @Override
        public SuspendableNonBlockingBuilder<T, V> withLifecycle(Function<? super T, ? extends NonBlockingLifecycle> lifecycle) {
            ServiceDependency<SuspendableActivityRegistrar> registrar = ServiceDependency.on(SuspendableActivityRegistrar.SERVICE_DESCRIPTOR);
            return this.requires(registrar).withLifecycle(SuspendableNonBlockingLifecycle.compose(lifecycle, registrar, this.priority::getPlain));
        }

        @Override
        public SuspendableNonBlockingBuilder<T, V> get() {
            return this;
        }

        @Override
        public <R> SuspendableNonBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultSuspendableNonBlockingBuilder<>(this, mapper);
        }

        @Override
        protected BiFunction<CapabilityServiceBuilder<?>, Collection<ServiceName>, Consumer<V>> provides() {
            return new RuntimeCapabilityProvider<>(this.capability);
        }

        @Override
        public CapabilityServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class DefaultSuspendableBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<SuspendableBlockingBuilder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V> implements SuspendableBlockingBuilder<T, V> {
        private final SuspendableNonBlockingBuilder<T, V> builder;
        private final Supplier<Executor> executor;

        DefaultSuspendableBlockingBuilder(RuntimeCapability<Void> capability, Supplier<T> provider, Function<? super T, ? extends V> mapper) {
            this(capability, provider, mapper, ServiceDependency.on(Capabilities.MANAGEMENT_EXECUTOR));
        }

        private DefaultSuspendableBlockingBuilder(RuntimeCapability<Void> capability, Supplier<T> provider, Function<? super T, ? extends V> mapper, ServiceDependency<Executor> executor) {
            this(new DefaultSuspendableNonBlockingBuilder<T, V>(capability, compose(provider, executor), mapper).requires(executor), executor);
        }

        private DefaultSuspendableBlockingBuilder(SuspendableNonBlockingBuilder<T, V> builder, Supplier<Executor> executor) {
            super(builder, executor);
            this.builder = builder;
            this.executor = executor;
        }

        @Override
        public SuspendableBlockingBuilder<T, V> withSuspendPriority(SuspendPriority priority) {
            this.builder.withSuspendPriority(priority);
            return this;
        }

        @Override
        public SuspendableBlockingBuilder<T, V> get() {
            return this;
        }

        @Override
        public <R> SuspendableBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultSuspendableBlockingBuilder<>(this.builder.map(mapper), this.executor);
        }
    }

    abstract class AbstractSuspendableValueBuilderDecorator<B, V> extends AbstractValueBuilderDecorator<B, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, V> implements SuspendableValueBuilder<B, V> {
        private final SuspendableValueBuilder<?, V> builder;

        AbstractSuspendableValueBuilderDecorator(SuspendableValueBuilder<?, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public B withSuspendPriority(SuspendPriority priority) {
            this.builder.withSuspendPriority(priority);
            return this.get();
        }
    }

    class DefaultSuspendableBlockingLifecycleBuilder<V> extends AbstractSuspendableValueBuilderDecorator<SuspendableBlockingLifecycleBuilder<V>, V> implements SuspendableBlockingLifecycleBuilder<V> {
        private final SuspendableValueBuilder<?, V> builder;

        DefaultSuspendableBlockingLifecycleBuilder(SuspendableValueBuilder<?, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public SuspendableBlockingLifecycleBuilder<V> get() {
            return this;
        }

        @Override
        public <R> SuspendableBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultSuspendableBlockingLifecycleBuilder<>(this.builder.map(mapper));
        }
    }

    class DefaultSuspendableNonBlockingLifecycleBuilder<V> extends AbstractSuspendableValueBuilderDecorator<SuspendableNonBlockingLifecycleBuilder<V>, V> implements SuspendableNonBlockingLifecycleBuilder<V> {
        private final SuspendableValueBuilder<?, V> builder;

        DefaultSuspendableNonBlockingLifecycleBuilder(SuspendableValueBuilder<?, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public SuspendableNonBlockingLifecycleBuilder<V> get() {
            return this;
        }

        @Override
        public <R> SuspendableNonBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper) {
            return new DefaultSuspendableNonBlockingLifecycleBuilder<>(this.builder.map(mapper));
        }
    }

    @Deprecated
    class LegacyBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<Builder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, T, V> implements Builder<T, V> {
        private static final Supplier<Executor> DEFAULT_EXECUTOR = Functions.constantSupplier(Runnable::run);

        private final AtomicReference<Supplier<Executor>> reference;

        LegacyBlockingBuilder(RuntimeCapability<Void> capability, Supplier<T> provider, Function<T, V> mapper) {
            this(capability, provider, mapper, new AtomicReference<>(DEFAULT_EXECUTOR));
        }

        private LegacyBlockingBuilder(RuntimeCapability<Void> capability, Supplier<T> provider, Function<T, V> mapper, AtomicReference<Supplier<Executor>> reference) {
            this(capability, provider, mapper, new Supplier<>() {
                @Override
                public Executor get() {
                    return reference.get().get();
                }
            }, reference);
        }

        private LegacyBlockingBuilder(RuntimeCapability<Void> capability, Supplier<T> provider, Function<T, V> mapper, Supplier<Executor> executor, AtomicReference<Supplier<Executor>> reference) {
            this(new DefaultNonBlockingBuilder<>(capability, compose(provider, executor), mapper), executor, reference);
        }

        private LegacyBlockingBuilder(NonBlockingBuilder<T, V> builder, Supplier<Executor> executor, AtomicReference<Supplier<Executor>> reference) {
            super(builder, executor);
            this.reference = reference;
        }

        @Override
        public Builder<T, V> blocking() {
            ServiceDependency<Executor> executor = ServiceDependency.on(Capabilities.MANAGEMENT_EXECUTOR);
            this.reference.setPlain(executor);
            return this.requires(executor);
        }

        @Override
        public Builder<T, V> get() {
            return this;
        }
    }

    class DefaultServiceInstaller extends AbstractInstaller<CapabilityServiceTarget, CapabilityServiceBuilder<?>> implements CapabilityServiceInstaller {

        DefaultServiceInstaller(Installer.Configuration<CapabilityServiceBuilder<?>> config) {
            super(config, CapabilityServiceTarget::addService);
        }
    }
}
