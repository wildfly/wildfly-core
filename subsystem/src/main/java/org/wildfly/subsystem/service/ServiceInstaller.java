/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.suspend.SuspendPriority;
import org.jboss.as.server.suspend.SuspendableActivityRegistrar;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.Functions;
import org.wildfly.service.BlockingLifecycle;
import org.wildfly.service.Installer;
import org.wildfly.service.NonBlockingLifecycle;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A {@link ResourceServiceInstaller} that encapsulates service installation into a {@link RequirementServiceTarget}.
 * @author Paul Ferraro
 */
public interface ServiceInstaller extends ResourceServiceInstaller, DeploymentServiceInstaller, Installer<RequirementServiceTarget> {

    @Override
    default void install(DeploymentPhaseContext context) {
        this.install(context.getRequirementServiceTarget());
    }

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
     * Builds an installer of a service.
     */
    interface Builder extends Installer.Builder<Builder, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>> {

        /**
         * Returns a {@link ServiceInstaller} builder that installs the specified installer into a child target.
         * By default, the installed service will start when installed.
         * @param installer a service installer
         * @return a service installer builder
         */
        static Builder of(org.wildfly.service.ServiceInstaller installer) {
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

        /**
         * Returns a {@link ServiceInstaller} builder that installs the specified installer into a child target.
         * By default, the installed service will start when installed.
         * @param installer a service installer
         * @return a service installer builder
         */
        static Builder of(ServiceInstaller installer, CapabilityServiceSupport support) {
            return of(new org.wildfly.service.ServiceInstaller() {
                @Override
                public ServiceController<?> install(ServiceTarget target) {
                    return installer.install(RequirementServiceTarget.forTarget(target, support));
                }
            });
        }
    }

    /**
     * Builds an installer of a service that provides a value.
     * @param <B> the builder type
     * @param <V> the provided value type
     */
    interface ValueBuilder<B, V> extends Installer.ValueBuilder<B, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, V> {

        /**
         * Configures a service descriptor provided by this service.
         * @param descriptor a service descriptor
         * @return a reference to this builder
         */
        default B provides(NullaryServiceDescriptor<V> descriptor) {
            return this.provides(ServiceName.parse(descriptor.getName()));
        }

        /**
         * Configures a service descriptor provided by this service.
         * @param descriptor a service descriptor
         * @param name a dynamic segment
         * @return a reference to this builder
         */
        default B provides(UnaryServiceDescriptor<V> descriptor, String name) {
            Map.Entry<String, String[]> resolved = descriptor.resolve(name);
            return this.provides(ServiceName.parse(resolved.getKey()).append(resolved.getValue()));
        }

        /**
         * Configures a service descriptor provided by this service.
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
         * Configures a service descriptor provided by this service.
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
         * Configures a service descriptor provided by this service.
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
    interface BlockingBuilder<T, V> extends Installer.BlockingLifecycleBuilder<BlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V>, ValueBuilder<BlockingBuilder<T, V>, V> {
        @Override
        <R> BlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified provider via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(Supplier<V> provider) {
            return new DefaultBlockingBuilder<>(provider, Function.identity());
        }

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified service dependency.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param dependency the dependency providing the service value
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(ServiceDependency<V> dependency) {
            Supplier<V> provider = dependency;
            return of(provider).requires(dependency);
        }

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified provider via asynchronously executed blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @param executor an executor dependency
         * @return a service installer builder
         */
        static <V> BlockingBuilder<V, V> of(Supplier<V> provider, ServiceDependency<Executor> executor) {
            return new DefaultAsyncBlockingBuilder<>(provider, Function.identity(), executor);
        }
    }

    /**
     * Builds an installer of a service derived from a value with a configurable non-blocking lifecycle.
     * Unless explicitly specified, services installed via this builder will start when required, if providing a value (via {@link ValueBuilder#provides(ServiceName)}; or when available, otherwise.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface NonBlockingBuilder<T, V> extends Installer.NonBlockingLifecycleBuilder<NonBlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V>, ValueBuilder<NonBlockingBuilder<T, V>, V> {
        @Override
        <R> NonBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified provider.
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
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified provider via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> BlockingLifecycleBuilder<V> of(Supplier<V> provider) {
            return new DefaultBlockingLifecycleBuilder<>(new DefaultBlockingBuilder<>(provider, Function.identity()).withLifecycle(Function.identity()));
        }

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified provider via asynchronously executed blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @param executor an executor dependency
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> BlockingLifecycleBuilder<V> of(Supplier<V> provider, ServiceDependency<Executor> executor) {
            return new DefaultBlockingLifecycleBuilder<>(new DefaultAsyncBlockingBuilder<>(provider, Function.identity(), executor).withLifecycle(Function.identity()));
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
            return new DefaultNonBlockingLifecycleBuilder<>(new DefaultNonBlockingBuilder<>(provider, Function.identity()).withLifecycle(Function.identity()));
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
     * Builds an installer of a service derived from a value with a configurable blocking lifecycle that auto-stops on suspend and auto-starts on resume.
     * Unless explicitly specified, services installed via this builder will start when required, if providing a value (via {@link ValueBuilder#provides(ServiceName)}; or when available, otherwise.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface SuspendableBlockingBuilder<T, V> extends Installer.BlockingLifecycleBuilder<SuspendableBlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V>, SuspendableValueBuilder<SuspendableBlockingBuilder<T, V>, V> {
        @Override
        <R> SuspendableBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified provider via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> SuspendableBlockingBuilder<V, V> of(Supplier<V> provider) {
            return new DefaultSuspendableBlockingBuilder<>(provider, Function.identity());
        }
    }

    /**
     * Builds an installer of a service derived from a value with a configurable non-blocking lifecycle that auto-stops on suspend and auto-starts on resume.
     * Unless explicitly specified, services installed via this builder will start when required, if providing a value (via {@link ValueBuilder#provides(ServiceName)}; or when available, otherwise.
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface SuspendableNonBlockingBuilder<T, V> extends Installer.NonBlockingLifecycleBuilder<SuspendableNonBlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V>, SuspendableValueBuilder<SuspendableNonBlockingBuilder<T, V>, V> {
        @Override
        <R> SuspendableNonBlockingBuilder<T, R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified provider via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V> SuspendableNonBlockingBuilder<V, V> of(Supplier<CompletionStage<V>> provider) {
            return new DefaultSuspendableNonBlockingBuilder<>(provider, Function.identity());
        }
    }

    /**
     * Builds an installer of a service derived from a value with a suspendable inherent blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <V> the provided value type
     */
    interface SuspendableBlockingLifecycleBuilder<V> extends SuspendableValueBuilder<SuspendableBlockingLifecycleBuilder<V>, V> {
        @Override
        <R> SuspendableBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends BlockingLifecycle> SuspendableBlockingLifecycleBuilder<V> of(Supplier<V> provider) {
            return new DefaultSuspendableBlockingLifecycleBuilder<>(SuspendableBlockingBuilder.of(provider).withLifecycle(Function.identity()));
        }
    }


    /**
     * Builds an installer of a service derived from a value with a suspendable inherent non-blocking lifecycle.
     * By default, services installed via this builder will start when required.
     * @param <V> the provided value type
     */
    interface SuspendableNonBlockingLifecycleBuilder<V> extends SuspendableValueBuilder<SuspendableNonBlockingLifecycleBuilder<V>, V> {
        @Override
        <R> SuspendableNonBlockingLifecycleBuilder<R> map(Function<? super V, ? extends R> mapper);

        /**
         * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory via blocking operations.
         * By default, the installed service will start when required.
         * @param <V> the service value type
         * @param provider provides the service value
         * @return a service installer builder
         */
        static <V extends NonBlockingLifecycle> SuspendableNonBlockingLifecycleBuilder<V> of(Supplier<CompletionStage<V>> provider) {
            return new DefaultSuspendableNonBlockingLifecycleBuilder<>(SuspendableNonBlockingBuilder.of(provider).withLifecycle(Function.identity()));
        }
    }

    /**
     * Builds a {@link ServiceInstaller} whose service provides a single value.
     * @param <T> the source value type
     * @param <V> the service value type
     * @deprecated Superseded by {@link BlockingBuilder}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    interface UnaryBuilder<T, V> extends Installer.BlockingBuilder<UnaryBuilder<T, V>>, ValueBuilder<UnaryBuilder<T, V>, V>, Installer.UnaryBuilder<UnaryBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> {
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the specified value.
     * By default, the installed service will start when installed since the provided value is already available.
     * @param <V> the service value type
     * @param value the service value
     * @return a service installer builder
     * @deprecated Superseded by {@link BlockingBuilder#of(Supplier)}.
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
     * @deprecated Superseded by {@link BlockingBuilder#of(ServiceDependency)}.
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
     * @deprecated Superseded by {@link BlockingBuilder#of(Supplier)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static <V> UnaryBuilder<V, V> builder(Supplier<V> factory) {
        return builder(Function.identity(), factory).startWhen(StartWhen.REQUIRED);
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory and mapping function.
     * By default, the installed service will start when required.
     * @param <T> the source value type
     * @param <V> the service value type
     * @param mapper a function that returns the service value given the value supplied by the provider
     * @param factory provides the input to the specified mapper
     * @return a service installer builder
     * @deprecated Superseded by {@link BlockingBuilder#of(Supplier)} followed by {@link BlockingBuilder#map(Function)}.
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
     * @deprecated Superseded by {@link Builder#of(org.wildfly.service.ServiceInstaller)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static Builder builder(org.wildfly.service.ServiceInstaller installer) {
        return Builder.of(installer);
    }

    /**
     * Returns a {@link ServiceInstaller} builder that installs the specified installer into a child target.
     * By default, the installed service will start when installed.
     * @param installer a service installer
     * @param support support for capabilities
     * @return a service installer builder
     * @deprecated Superseded by {@link Builder#of(ServiceInstaller, CapabilityServiceSupport)}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    static Builder builder(ServiceInstaller installer, CapabilityServiceSupport support) {
        return Builder.of(installer, support);
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
            public void start(StartContext context) throws StartException {
                startTask.run();
            }

            @Override
            public void stop(StopContext context) {
                stopTask.run();
            }
        });
    }

    class DefaultBuilder extends Installer.AbstractBuilder<Builder, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>> implements Builder {
        private final org.jboss.msc.Service service;

        DefaultBuilder(org.jboss.msc.Service service) {
            this.service = service;
        }

        @Override
        public Builder get() {
            return this;
        }

        @Override
        public Function<? super RequirementServiceBuilder<?>, org.jboss.msc.Service> getServiceFactory() {
            return builder -> this.service;
        }

        @Override
        public ServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class DefaultBlockingBuilder<T, V> extends AbstractBlockingLifecycleBuilder<BlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> implements BlockingBuilder<T, V> {

        private <R> DefaultBlockingBuilder(DefaultBlockingBuilder<T, R> builder, Function<? super R, ? extends V> mapper) {
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

    class DefaultNonBlockingBuilder<T, V> extends AbstractNonBlockingLifecycleBuilder<NonBlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> implements NonBlockingBuilder<T, V> {

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

    class DefaultAsyncBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<BlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> implements BlockingBuilder<T, V> {
        private final NonBlockingBuilder<T, V> builder;
        private final Supplier<Executor> executor;

        private DefaultAsyncBlockingBuilder(Supplier<T> provider, Function<? super T, ? extends V> mapper, ServiceDependency<Executor> executor) {
            this(new DefaultNonBlockingBuilder<T, V>(compose(provider, executor), mapper).requires(executor), executor);
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

    class DefaultBlockingLifecycleBuilder<T extends BlockingLifecycle, V> extends AbstractValueBuilderDecorator<BlockingLifecycleBuilder<V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, V> implements BlockingLifecycleBuilder<V> {
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

    class DefaultNonBlockingLifecycleBuilder<T extends NonBlockingLifecycle, V> extends AbstractValueBuilderDecorator<NonBlockingLifecycleBuilder<V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, V> implements NonBlockingLifecycleBuilder<V> {
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

    class DefaultSuspendableNonBlockingBuilder<T, V> extends AbstractNonBlockingLifecycleBuilder<SuspendableNonBlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> implements SuspendableNonBlockingBuilder<T, V> {
        private final AtomicReference<SuspendPriority> priority;

        private <R> DefaultSuspendableNonBlockingBuilder(DefaultSuspendableNonBlockingBuilder<T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
            this.priority = builder.priority;
        }

        DefaultSuspendableNonBlockingBuilder(Supplier<CompletionStage<T>> provider, Function<? super T, ? extends V> mapper) {
            super(provider, mapper);
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
        public ServiceInstaller build() {
            return new DefaultServiceInstaller(this);
        }
    }

    class DefaultSuspendableBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<SuspendableBlockingBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> implements SuspendableBlockingBuilder<T, V> {
        private final SuspendableNonBlockingBuilder<T, V> builder;
        private final Supplier<Executor> executor;

        DefaultSuspendableBlockingBuilder(Supplier<T> provider, Function<? super T, ? extends V> mapper) {
            this(provider, mapper, ServiceDependency.on(Capabilities.MANAGEMENT_EXECUTOR));
        }

        private DefaultSuspendableBlockingBuilder(Supplier<T> provider, Function<? super T, ? extends V> mapper, ServiceDependency<Executor> executor) {
            this(new DefaultSuspendableNonBlockingBuilder<T, V>(compose(provider, executor), mapper).requires(executor), executor);
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

    abstract class AbstractSuspendableValueBuilderDecorator<B, V> extends AbstractValueBuilderDecorator<B, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, V> implements SuspendableValueBuilder<B, V> {
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
    class LegacyBlockingBuilder<T, V> extends AbstractAsyncBlockingLifecycleBuilder<UnaryBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> implements UnaryBuilder<T, V> {
        private static final Supplier<Executor> DEFAULT_EXECUTOR = Functions.constantSupplier(Runnable::run);

        private final DefaultNonBlockingBuilder<T, V> builder;
        private final AtomicReference<Supplier<Executor>> reference;

        LegacyBlockingBuilder(Supplier<T> provider, Function<T, V> mapper) {
            this(provider, mapper, new AtomicReference<>(DEFAULT_EXECUTOR));
        }

        private LegacyBlockingBuilder(Supplier<T> provider, Function<T, V> mapper, AtomicReference<Supplier<Executor>> reference) {
            this(provider, mapper, new Supplier<>() {
                @Override
                public Executor get() {
                    return reference.get().get();
                }
            }, reference);
        }

        private LegacyBlockingBuilder(Supplier<T> provider, Function<T, V> mapper, Supplier<Executor> executor, AtomicReference<Supplier<Executor>> reference) {
            this(new DefaultNonBlockingBuilder<>(compose(provider, executor), mapper), executor, reference);
        }

        private LegacyBlockingBuilder(DefaultNonBlockingBuilder<T, V> builder, Supplier<Executor> executor, AtomicReference<Supplier<Executor>> reference) {
            super(builder, executor);
            this.builder = builder;
            this.reference = reference;
        }

        @Override
        public UnaryBuilder<T, V> blocking() {
            ServiceDependency<Executor> executor = ServiceDependency.on(Capabilities.MANAGEMENT_EXECUTOR);
            this.reference.setPlain(executor);
            return this.requires(executor);
        }

        @Override
        public UnaryBuilder<T, V> get() {
            return this;
        }

        @Override
        public ServiceInstaller build() {
            return new DefaultServiceInstaller(this.builder);
        }
    }

    class DefaultServiceInstaller extends Installer.AbstractInstaller<RequirementServiceTarget, RequirementServiceBuilder<?>> implements ServiceInstaller {

        DefaultServiceInstaller(Installer.Configuration<RequirementServiceBuilder<?>> config) {
            super(config, RequirementServiceTarget::addService);
        }
    }
}
