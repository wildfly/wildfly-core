/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.Functions;

/**
 * Encapsulates installation into a generic service target.
 * @author Paul Ferraro
 * @param <ST> the service target type
 */
public interface Installer<ST extends ServiceTarget> {
    /**
     * Installs a service into the specified target.
     * @param target a service target
     * @return a service controller
     */
    ServiceController<?> install(ST target);

    /**
     * Enumerates the possible start conditions of a service.
     */
    enum StartWhen {
        /**
         * Indicates that a service and its dependencies should start when required by another service that wants to start.
         * Once started, the service will automatically stop when there are no longer any services requiring it.
         * This is the default start condition.
         * @see {@link ServiceController.Mode#ON_DEMAND}
         */
        REQUIRED(ServiceController.Mode.ON_DEMAND),
        /**
         * Indicates that a service should start automatically after all of its dependencies have started.
         * Once started, the service will automatically stop when any of its dependencies wants to stop.
         * This condition does not prompt any dependencies to start.
         * @see {@link ServiceController.Mode#PASSIVE}
         */
        AVAILABLE(ServiceController.Mode.PASSIVE),
        /**
         * Indicates that a service and its dependencies should start automatically after it is installed.
         * Once started, the service will only stop if removed.
         * Ideally, we only want services to start when necessary, thus this condition should be used sparingly.
         * @see {@link ServiceController.Mode#ACTIVE}
         */
        INSTALLED(ServiceController.Mode.ACTIVE),
        ;
        private final ServiceController.Mode mode;

        StartWhen(ServiceController.Mode mode) {
            this.mode = mode;
        }

        ServiceController.Mode getMode() {
            return this.mode;
        }
    }

    /**
     * Builds an installer of a service.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     */
    interface Builder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>> {
        /**
         * Configures a dependency of the installed service.
         * @param dependency a dependency
         * @return a reference to this builder
         */
        B requires(Consumer<? super SB> dependency);

        /**
         * Configures dependencies of the installed service.
         * @param dependencies a variable number of dependencies
         * @return a reference to this builder
         */
        default B requires(Iterable<? extends Consumer<? super SB>> dependencies) {
            return this.requires(new Consumer<>() {
                @Override
                public void accept(SB builder) {
                    for (Consumer<? super SB> dependency : dependencies) {
                        dependency.accept(builder);
                    }
                }
            });
        }

        /**
         * Specifies when the installed service should start.
         * @param condition the start condition
         * @return a reference to this builder
         */
        B startWhen(StartWhen condition);

        /**
         * Configures the installed service to automatically start when all of its dependencies are available
         * and to automatically stop when any of its dependencies are no longer available.
         * @return a reference to this builder
         * @deprecated Superseded by {@link #startWhen(StartWhen)}
         */
        @Deprecated(forRemoval = true, since = "29.0")
        default B asPassive() {
            return this.startWhen(StartWhen.AVAILABLE);
        }

        /**
         * Configures the installed service to start immediately after installation, forcing any dependencies to start.
         * @return a reference to this builder
         * @deprecated Superseded by {@link #startWhen(StartWhen)}
         */
        @Deprecated(forRemoval = true, since = "29.0")
        default B asActive() {
            return this.startWhen(StartWhen.INSTALLED);
        }

        /**
         * Configures the specified task to be run after the installed service is started.
         * @param task a task to execute upon service start
         * @return a reference to this builder
         */
        B onStart(Runnable task);

        /**
         * Configures the specified task to be run after the installed service is stopped.
         * @param task a task to execute upon service stop
         * @return a reference to this builder
         */
        B onStop(Runnable task);

        /**
         * Configures the specified task to be run upon removal of the installed service.
         * @param task a task to execute upon service removal
         * @return a reference to this builder
         */
        B onRemove(Runnable task);

        /**
         * Builds a service installer.
         * @return a service installer
         */
        I build();
    }

    /**
     * A mappable object.
     * @param <V> the mappable value type
     */
    interface Mappable<V> {
        /**
         * Returns a mappable object providing the mapped type.
         * @param <R> the service value type
         * @param mapper the function returning the service value
         * @return a mappable object providing the mapped type.
         */
        <R> Mappable<R> map(Function<? super V, ? extends R> mapper);
    }

    /**
     * Builds an installer of a service providing a value.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <V> the service value type
     */
    interface ValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, V> extends Builder<B, I, ST, SB> {
        /**
         * Configures the {@link ServiceName} of the value provided by this service.
         * @param name a service name
         * @return a reference to this builder
         */
        B provides(ServiceName name);

        /**
         * Configures a captor that consumes the provided value on {@link org.jboss.msc.Service#start(org.jboss.msc.service.StartContext)}, and null on {@link org.jboss.msc.Service#stop(StopContext)}.
         * @param captor a consumer of the provided value
         * @return a reference to this builder
         */
        B withCaptor(Consumer<? super V> captor);
    }

    /**
     * Builds an installer of a service providing a mappable value.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <V> the provided value type
     */
    interface MappableValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, V> extends ValueBuilder<B, I, ST, SB, V>, Mappable<V> {
    }

    /**
     * Builds an installer of a service derived from a value with a configurable blocking lifecycle.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface BlockingValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends MappableValueBuilder<B, I, ST, SB, V> {
        /**
         * Configures a task to run on {@link org.jboss.msc.Service#start(org.jboss.msc.service.StartContext)}.
         * @param start specifies an operation that must complete before the service value is available
         * @return a reference to this builder
         */
        default B onStart(Consumer<? super T> start) {
            return this.withLifecycle(BlockingLifecycle.compose(start, Functions.discardingConsumer()));
        }

        /**
         * Configures a task to run on {@link org.jboss.msc.Service#stop(org.jboss.msc.service.StopContext)}.
         * @param stop specifies an operation that must complete when the service value is no longer available
         * @return a reference to this builder
         */
        default B onStop(Consumer<? super T> stop) {
            return this.withLifecycle(BlockingLifecycle.compose(Functions.discardingConsumer(), stop));
        }

        /**
         * Configures a blocking lifecycle of the provided value.
         * @param lifecycle a function returning the blocking lifecycle of the provided value
         * @return a reference to this builder
         */
        B withLifecycle(Function<? super T, ? extends BlockingLifecycle> lifecycle);
    }

    /**
     * Builds an installer of a service derived from a value with a configurable non-blocking lifecycle.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface NonBlockingValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends MappableValueBuilder<B, I, ST, SB, V> {
        /**
         * Configures a task to run on {@link org.jboss.msc.Service#start(org.jboss.msc.service.StartContext)}.
         * @param start specifies a stage that must complete before the service value is available
         * @return a reference to this builder
         */
        default B onStart(Function<? super T, CompletionStage<Void>> start) {
            return this.withLifecycle(NonBlockingLifecycle.compose(start, DiscardingFunction.complete()));
        }

        /**
         * Configures a task to run on {@link org.jboss.msc.Service#stop(org.jboss.msc.service.StopContext)}.
         * @param stop specifies a stage that must complete when the service value is no longer available
         * @return a reference to this builder
         */
        default B onStop(Function<? super T, CompletionStage<Void>> stop) {
            return this.withLifecycle(NonBlockingLifecycle.compose(DiscardingFunction.complete(), stop));
        }

        /**
         * Configures a blocking lifecycle of the provided value.
         * @param lifecycle a function returning the blocking lifecycle of the provided value
         * @return a reference to this builder
         */
        B withLifecycle(Function<? super T, ? extends NonBlockingLifecycle> lifecycle);
    }

    /**
     * Builds an installer of a service derived from a value with a blocking lifecycle.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface BlockingLifecycleValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T extends BlockingLifecycle, V> extends MappableValueBuilder<B, I, ST, SB, V> {
    }

    /**
     * Builds an installer of a service derived from a value with a non-blocking lifecycle.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <T> the source value type
     * @param <V> the provided value type
     */
    interface NonBlockingLifecycleValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T extends NonBlockingLifecycle, V> extends MappableValueBuilder<B, I, ST, SB, V> {
    }

    /**
     * Implemented by builders with blocking service support.
     * @param <B> the builder type
     */
    @Deprecated(forRemoval = true, since = "32.0")
    interface BlockingBuilder<B> {
        /**
         * Indicates that the installed service performs blocking operations on start and/or stop, and should be instrumented accordingly.
         * @return a reference to this builder
         */
        B blocking();
    }

    /**
     * Builds a {@link ServiceInstaller} whose service provides a single value.
     * @param <T> the source value type
     * @param <V> the service value type
     * @deprecated Superseded by {@link BlockingValueBuilder}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    interface UnaryBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends BlockingValueBuilder<B, I, ST, SB, T, V> {
        @Override
        default <R> Mappable<R> map(Function<? super V, ? extends R> mapper) {
            // Legacy builders do not support fluent mapping
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Encapsulates the configuration of an {@link Installer} of an MSC service.
     * @param <SB> the service builder type
     */
    interface Configuration<SB extends ServiceBuilder<?>> {

        /**
         * Returns the initial mode of the installed service.
         * @return a service mode
         */
        ServiceController.Mode getInitialMode();

        /**
         * Returns the dependency of this service
         * @return a service dependency
         */
        Consumer<? super SB> getDependency();

        /**
         * Returns the factory of this service
         * @return a service factory
         */
        Function<? super SB, org.jboss.msc.Service> getServiceFactory();

        /**
         * Returns tasks to be run per lifecycle event.
         * The returned map is either fully populated, or an empty map, if this service has no lifecycle tasks.
         * @return a potentially empty map of tasks to be run per lifecycle event.
         */
        Map<LifecycleEvent, Collection<Runnable>> getLifecycleTasks();
    }

    abstract class AbstractBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>> implements Builder<B, I, ST, SB>, Configuration<SB>, Supplier<B> {
        private final AtomicReference<ServiceController.Mode> mode;
        private final AtomicReference<ServiceController.Mode> defaultMode;
        private final AtomicReference<Consumer<SB>> dependency;
        private final Map<LifecycleEvent, Collection<Runnable>> lifecycleTasks;

        protected AbstractBuilder(AbstractBuilder<?, I, ST, SB> builder) {
            this.mode = builder.mode;
            this.defaultMode = builder.defaultMode;
            this.dependency = builder.dependency;
            this.lifecycleTasks = builder.lifecycleTasks;
        }

        protected AbstractBuilder() {
            this.mode = new AtomicReference<>();
            // By default, MSC services start eagerly, which not a sensible default for WildFly subsystems.
            // Services installed via this builder start ON_DEMAND by default, if providing a value; or PASSIVE by default, otherwise.
            this.defaultMode = new AtomicReference<>(ServiceController.Mode.PASSIVE);
            this.dependency = new AtomicReference<>(DiscardingConsumer.of());
            this.lifecycleTasks = new EnumMap<>(LifecycleEvent.class);
        }

        @Override
        public B startWhen(StartWhen condition) {
            this.mode.setPlain(condition.getMode());
            return this.get();
        }

        protected B withDefaultMode(ServiceController.Mode mode) {
            this.defaultMode.setPlain(mode);
            return this.get();
        }

        @Override
        public B requires(Consumer<? super SB> dependency) {
            this.dependency.getAndUpdate(new ConsumerChainingOperator<>(dependency));
            return this.get();
        }

        @Override
        public B onStart(Runnable task) {
            return this.onEvent(LifecycleEvent.UP, task);
        }

        @Override
        public B onStop(Runnable task) {
            return this.onEvent(LifecycleEvent.DOWN, task);
        }

        @Override
        public B onRemove(Runnable task) {
            return this.onEvent(LifecycleEvent.REMOVED, task);
        }

        private B onEvent(LifecycleEvent event, Runnable task) {
            if (this.lifecycleTasks.isEmpty()) {
                // Populate EnumMap lazily, when needed
                for (LifecycleEvent e : EnumSet.allOf(LifecycleEvent.class)) {
                    this.lifecycleTasks.put(e, (e == event) ? List.of(task) : List.of());
                }
            } else {
                Collection<Runnable> tasks = this.lifecycleTasks.get(event);
                if (tasks.isEmpty()) {
                    this.lifecycleTasks.put(event, List.of(task));
                } else {
                    if (tasks.size() == 1) {
                        tasks = new LinkedList<>(tasks);
                        this.lifecycleTasks.put(event, tasks);
                    }
                    tasks.add(task);
                }
            }
            return this.get();
        }

        @Override
        public ServiceController.Mode getInitialMode() {
            return Optional.ofNullable(this.mode.getPlain()).orElse(this.defaultMode.getPlain());
        }

        @Override
        public Consumer<? super SB> getDependency() {
            return this.dependency.getPlain();
        }

        @Override
        public Map<LifecycleEvent, Collection<Runnable>> getLifecycleTasks() {
            // Return empty map or fully unmodifiable copy
            if (this.lifecycleTasks.isEmpty()) return Map.of();
            Map<LifecycleEvent, Collection<Runnable>> result = new EnumMap<>(LifecycleEvent.class);
            for (Map.Entry<LifecycleEvent, Collection<Runnable>> entry : this.lifecycleTasks.entrySet()) {
                result.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
    }

    abstract class AbstractValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends AbstractBuilder<B, I, ST, SB> implements ValueBuilder<B, I, ST, SB, V>, Function<Consumer<T>, org.jboss.msc.Service> {
        private final List<ServiceName> names;
        private final Function<? super T, ? extends V> mapper;
        private final AtomicReference<Consumer<V>> captor;

        protected <R> AbstractValueBuilder(AbstractValueBuilder<?, I, ST, SB, T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder);
            this.mapper = builder.mapper.andThen(new Function<R, V>() {
                @Override
                public V apply(R value) {
                    builder.captor.getPlain().accept(value);
                    return mapper.apply(value);
                }
            });
            this.names = builder.names;
            this.captor = new AtomicReference<>(new Consumer<>() {
                @Override
                public void accept(V value) {
                    if (value == null) {
                        builder.captor.getPlain().accept(null);
                    }
                }
            });
        }

        protected AbstractValueBuilder(Function<? super T, ? extends V> mapper) {
            this.mapper = mapper;
            this.names = new LinkedList<>();
            this.captor = new AtomicReference<>(DiscardingConsumer.of());
        }

        protected BiFunction<SB, Collection<ServiceName>, Consumer<V>> provides() {
            return new BiFunction<>() {
                @Override
                public Consumer<V> apply(SB builder, Collection<ServiceName> names) {
                    return !names.isEmpty() ? builder.provides(names.toArray(ServiceName[]::new)) : DiscardingConsumer.of();
                }
            };
        }

        @Override
        public B provides(ServiceName name) {
            this.names.add(name);
            return this.withDefaultMode(ServiceController.Mode.ON_DEMAND);
        }

        @Override
        public B withCaptor(Consumer<? super V> captor) {
            this.captor.getAndUpdate(new ConsumerChainingOperator<>(captor));
            return this.get();
        }

        @Override
        public Function<? super SB, org.jboss.msc.Service> getServiceFactory() {
            BiFunction<SB, Collection<ServiceName>, Consumer<V>> provider = this.provides();
            List<ServiceName> names = List.copyOf(this.names);
            Consumer<V> captor = this.captor.getPlain();
            Function<? super T, ? extends V> mapper = this.mapper;
            Function<Consumer<T>, org.jboss.msc.Service> factory = this;
            return new Function<>() {
                @Override
                public Service apply(SB builder) {
                    Consumer<V> injector = provider.apply(builder, names).andThen(captor);
                    Consumer<T> mappedInjector = new Consumer<>() {
                        @Override
                        public void accept(T value) {
                            injector.accept(Optional.ofNullable(value).map(mapper).orElse(null));
                        }
                    };
                    return factory.apply(mappedInjector);
                }
            };
        }
    }

    abstract class AbstractBlockingValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends AbstractValueBuilder<B, I, ST, SB, T, V> implements BlockingValueBuilder<B, I, ST, SB, T, V> {
        private final Supplier<T> provider;
        private final List<Function<? super T, ? extends BlockingLifecycle>> lifecycles;

        protected <R> AbstractBlockingValueBuilder(AbstractBlockingValueBuilder<?, I, ST, SB, T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
            this.provider = builder.provider;
            this.lifecycles = builder.lifecycles;
        }

        protected AbstractBlockingValueBuilder(Supplier<T> provider, Function<? super T, ? extends V> mapper) {
            super(mapper);
            this.provider = provider;
            this.lifecycles = new LinkedList<>();
        }

        @Override
        public B withLifecycle(Function<? super T, ? extends BlockingLifecycle> lifecycle) {
            this.lifecycles.add(lifecycle);
            return this.get();
        }

        @Override
        public Service apply(Consumer<T> captor) {
            return new BlockingValueService<T>(this.provider, this.getLifecycleFactory(), captor);
        }

        private Function<? super T, ? extends BlockingLifecycle> getLifecycleFactory() {
            List<Function<? super T, ? extends BlockingLifecycle>> factories = List.copyOf(this.lifecycles);
            return (factories.size() == 1) ? factories.get(0) : new Function<>() {
                @Override
                public BlockingLifecycle apply(T value) {
                    if (factories.isEmpty()) return BlockingLifecycle.NONE;
                    List<BlockingLifecycle> lifecycles = new ArrayList<>(factories.size());
                    for (Function<? super T, ? extends BlockingLifecycle> factory : factories) {
                        lifecycles.add(factory.apply(value));
                    }
                    return new BlockingLifecycle() {
                        @Override
                        public boolean isStarted() {
                            return lifecycles.stream().allMatch(Lifecycle::isStarted);
                        }

                        @Override
                        public boolean isStopped() {
                            return lifecycles.stream().allMatch(Lifecycle::isStopped);
                        }

                        @Override
                        public void start() {
                            for (BlockingLifecycle lifecycle : lifecycles) {
                                if (lifecycle.isStopped()) {
                                    lifecycle.start();
                                }
                            }
                        }

                        @Override
                        public void stop() {
                            ListIterator<BlockingLifecycle> iterator = lifecycles.listIterator(lifecycles.size());
                            while (iterator.hasPrevious()) {
                                BlockingLifecycle lifecycle = iterator.previous();
                                if (lifecycle.isStarted()) {
                                    lifecycle.stop();
                                }
                            }
                        }
                    };
                }
            };
        }
    }

    abstract class AbstractNonBlockingValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends AbstractValueBuilder<B, I, ST, SB, T, V> implements NonBlockingValueBuilder<B, I, ST, SB, T, V> {
        private final Supplier<CompletionStage<T>> provider;
        private final List<Function<? super T, ? extends NonBlockingLifecycle>> lifecycles;

        protected <R> AbstractNonBlockingValueBuilder(AbstractNonBlockingValueBuilder<?, I, ST, SB, T, R> builder, Function<? super R, ? extends V> mapper) {
            super(builder, mapper);
            this.provider = builder.provider;
            this.lifecycles = builder.lifecycles;
        }

        protected AbstractNonBlockingValueBuilder(Supplier<CompletionStage<T>> provider, Function<? super T, ? extends V> mapper) {
            super(mapper);
            this.provider = provider;
            this.lifecycles = new LinkedList<>();
        }

        @Override
        public B withLifecycle(Function<? super T, ? extends NonBlockingLifecycle> lifecycle) {
            this.lifecycles.add(lifecycle);
            return this.get();
        }

        @Override
        public Service apply(Consumer<T> captor) {
            return new NonBlockingValueService<T>(this.provider, this.getLifecycleFactory(), captor);
        }

        private Function<? super T, ? extends NonBlockingLifecycle> getLifecycleFactory() {
            List<Function<? super T, ? extends NonBlockingLifecycle>> factories = List.copyOf(this.lifecycles);
            return (factories.size() == 1) ? factories.get(0) : new Function<>() {
                @Override
                public NonBlockingLifecycle apply(T value) {
                    if (factories.isEmpty()) return NonBlockingLifecycle.NONE;
                    List<NonBlockingLifecycle> lifecycles = new ArrayList<>(factories.size());
                    for (Function<? super T, ? extends NonBlockingLifecycle> factory : factories) {
                        lifecycles.add(factory.apply(value));
                    }
                    return new NonBlockingLifecycle() {
                        @Override
                        public boolean isStarted() {
                            return lifecycles.stream().allMatch(Lifecycle::isStarted);
                        }

                        @Override
                        public boolean isStopped() {
                            return lifecycles.stream().allMatch(Lifecycle::isStopped);
                        }

                        @Override
                        public CompletionStage<Void> start() {
                            CompletionStage<Void> result = COMPLETED;
                            for (NonBlockingLifecycle lifecycle : lifecycles) {
                                if (lifecycle.isStopped()) {
                                    result = result.thenCompose(ignore -> lifecycle.start());
                                }
                            }
                            return result;
                        }

                        @Override
                        public CompletionStage<Void> stop() {
                            CompletionStage<Void> result = COMPLETED;
                            ListIterator<NonBlockingLifecycle> iterator = lifecycles.listIterator(lifecycles.size());
                            while (iterator.hasPrevious()) {
                                NonBlockingLifecycle lifecycle = iterator.previous();
                                if (lifecycle.isStarted()) {
                                    result = result.thenCompose(ignore -> lifecycle.stop());
                                }
                            }
                            return result;
                        }
                    };
                }
            };
        }
    }

    abstract class AbstractBuilderDecorator<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>> implements Builder<B, I, ST, SB>, Supplier<B> {
        private final Builder<?, I, ST, SB> builder;

        AbstractBuilderDecorator(Builder<?, I, ST, SB> builder) {
            this.builder = builder;
        }

        @Override
        public B requires(Consumer<? super SB> dependency) {
            this.builder.requires(dependency);
            return this.get();
        }

        @Override
        public B startWhen(StartWhen condition) {
            this.builder.startWhen(condition);
            return this.get();
        }

        @Override
        public B onStart(Runnable task) {
            this.builder.onStart(task);
            return this.get();
        }

        @Override
        public B onStop(Runnable task) {
            this.builder.onStop(task);
            return this.get();
        }

        @Override
        public B onRemove(Runnable task) {
            this.builder.onRemove(task);
            return this.get();
        }

        @Override
        public I build() {
            return this.builder.build();
        }
    }

    abstract class AbstractValueBuilderDecorator<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, V> extends AbstractBuilderDecorator<B, I, ST, SB> implements ValueBuilder<B, I, ST, SB, V> {
        private final ValueBuilder<?, I, ST, SB, V> builder;

        AbstractValueBuilderDecorator(ValueBuilder<?, I, ST, SB, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public B provides(ServiceName name) {
            this.builder.provides(name);
            return this.get();
        }

        @Override
        public B withCaptor(Consumer<? super V> captor) {
            this.builder.withCaptor(captor);
            return this.get();
        }
    }

    abstract class AbstractBlockingValueBuilderDecorator<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends AbstractValueBuilderDecorator<B, I, ST, SB, V> implements BlockingValueBuilder<B, I, ST, SB, T, V> {
        private final BlockingValueBuilder<?, I, ST, SB, T, V> builder;

        AbstractBlockingValueBuilderDecorator(BlockingValueBuilder<?, I, ST, SB, T, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public B withLifecycle(Function<? super T, ? extends BlockingLifecycle> lifecycle) {
            this.builder.withLifecycle(lifecycle);
            return this.get();
        }
    }

    abstract class AbstractBlockingLifecycleValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T extends BlockingLifecycle, V> extends AbstractValueBuilderDecorator<B, I, ST, SB, V> implements BlockingLifecycleValueBuilder<B, I, ST, SB, T, V> {

        protected AbstractBlockingLifecycleValueBuilder(BlockingValueBuilder<?, I, ST, SB, T, V> builder) {
            super(builder);
        }
    }

    abstract class AbstractNonBlockingValueBuilderDecorator<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends AbstractValueBuilderDecorator<B, I, ST, SB, V> implements NonBlockingValueBuilder<B, I, ST, SB, T, V> {
        private final NonBlockingValueBuilder<?, I, ST, SB, T, V> builder;

        AbstractNonBlockingValueBuilderDecorator(NonBlockingValueBuilder<?, I, ST, SB, T, V> builder) {
            super(builder);
            this.builder = builder;
        }

        @Override
        public B withLifecycle(Function<? super T, ? extends NonBlockingLifecycle> lifecycle) {
            this.builder.withLifecycle(lifecycle);
            return this.get();
        }
    }

    abstract class AbstractNonBlockingLifecycleValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T extends NonBlockingLifecycle, V> extends AbstractValueBuilderDecorator<B, I, ST, SB, V> implements NonBlockingLifecycleValueBuilder<B, I, ST, SB, T, V> {

        protected AbstractNonBlockingLifecycleValueBuilder(NonBlockingValueBuilder<?, I, ST, SB, T, V> builder) {
            super(builder);
        }
    }

    abstract class AbstractAsyncBlockingValueBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends AbstractValueBuilderDecorator<B, I, ST, SB, V> implements BlockingValueBuilder<B, I, ST, SB, T, V> {
        protected static <T> Supplier<CompletionStage<T>> compose(Supplier<T> provider, Supplier<Executor> executor) {
            return new Supplier<>() {
                @Override
                public CompletionStage<T> get() {
                    try {
                        return CompletableFuture.supplyAsync(provider, executor.get());
                    } catch (RejectedExecutionException e) {
                        // Resort to work stealing
                        return CompletableFuture.supplyAsync(provider);
                    }
                }
            };
        }

        private final NonBlockingValueBuilder<?, I, ST, SB, T, V> builder;
        private final Supplier<Executor> executor;

        protected AbstractAsyncBlockingValueBuilder(NonBlockingValueBuilder<?, I, ST, SB, T, V> builder, Supplier<Executor> executor) {
            super(builder);
            this.builder = builder;
            this.executor = executor;
        }

        @Override
        public B withLifecycle(Function<? super T, ? extends BlockingLifecycle> lifecycle) {
            Supplier<Executor> executor = this.executor;
            this.builder.withLifecycle(new Function<>() {
                @Override
                public NonBlockingLifecycle apply(T value) {
                    return NonBlockingLifecycle.async(lifecycle.apply(value), executor.get());
                }
            });
            return this.get();
        }
    }

    /**
     * Abstract installer configured from a {@link Installer.Configuration}.
     * @param <ST> the service target type
     * @param <SB> the service builder type
     */
    abstract class AbstractInstaller<ST extends ServiceTarget, SB extends ServiceBuilder<?>> implements Installer<ST> {

        private final Function<ST, SB> serviceBuilderFactory;
        private final ServiceController.Mode mode;
        private final Consumer<? super SB> dependency;
        private final Function<? super SB, org.jboss.msc.Service> serviceFactory;
        private final Map<LifecycleEvent, Collection<Runnable>> lifecycleTasks;

        protected AbstractInstaller(Installer.Configuration<SB> config, Function<ST, SB> serviceBuilderFactory) {
            this.serviceBuilderFactory = serviceBuilderFactory;
            this.serviceFactory = config.getServiceFactory();
            this.mode = config.getInitialMode();
            this.dependency = config.getDependency();
            this.lifecycleTasks = config.getLifecycleTasks();
        }

        @Override
        public ServiceController<?> install(ST target) {
            SB builder = this.serviceBuilderFactory.apply(target);
            this.dependency.accept(builder);
            // N.B. map of tasks is either empty or fully populated
            if (!this.lifecycleTasks.isEmpty()) {
                Map<LifecycleEvent, Collection<Runnable>> tasks = this.lifecycleTasks;
                builder.addListener(new LifecycleListener() {
                    @Override
                    public void handleEvent(ServiceController<?> controller, LifecycleEvent event) {
                        tasks.get(event).forEach(Runnable::run);
                    }
                });
            }
            return builder.setInstance(this.serviceFactory.apply(builder)).setInitialMode(this.mode).install();
        }
    }

    /**
     * A generic service implementation using blocking start/stop operations on a provided value.
     * @param <T> the service value type
     */
    class BlockingValueService<T> implements org.jboss.msc.Service {
        private static final Logger LOGGER = Logger.getLogger(BlockingValueService.class);

        private final Supplier<T> provider;
        private final Function<? super T, ? extends BlockingLifecycle> lifecycleProvider;
        private final Consumer<? super T> captor;

        private final AtomicReference<BlockingLifecycle> lifecycle = new AtomicReference<>();

        BlockingValueService(Supplier<T> provider, Function<? super T, ? extends BlockingLifecycle> lifecycleProvider, Consumer<? super T> captor) {
            this.provider = provider;
            this.captor = captor;
            this.lifecycleProvider = lifecycleProvider;
        }

        @Override
        public void start(StartContext context) throws StartException {
            try {
                T value = this.provider.get();
                BlockingLifecycle lifecycle = this.lifecycleProvider.apply(value);
                this.lifecycle.set(lifecycle);
                if (lifecycle.isStopped()) {
                    lifecycle.start();
                }
                this.captor.accept(value);
            } catch (Throwable e) {
                throw new StartException(e);
            }
        }

        @Override
        public void stop(StopContext context) {
            this.captor.accept(null);
            BlockingLifecycle lifecycle = this.lifecycle.getAndSet(null);
            if ((lifecycle != null) && lifecycle.isStarted()) {
                try {
                    lifecycle.stop();
                } catch (Throwable e) {
                    LOGGER.warn(e.getLocalizedMessage(), e);
                }
            }
        }
    }

    /**
     * A service implementation using non-blocking start/stop operations.
     * @param <T> the service value type
     */
    class NonBlockingValueService<T> implements org.jboss.msc.Service {
        private static final Logger LOGGER = Logger.getLogger(NonBlockingValueService.class);

        private final Supplier<CompletionStage<T>> provider;
        private final Function<? super T, ? extends NonBlockingLifecycle> lifecycleProvider;
        private final Consumer<T> captor;
        private final AtomicReference<NonBlockingLifecycle> reference = new AtomicReference<>();

        NonBlockingValueService(Supplier<CompletionStage<T>> provider, Function<? super T, ? extends NonBlockingLifecycle> lifecycleProvider, Consumer<T> captor) {
            this.provider = provider;
            this.lifecycleProvider = lifecycleProvider;
            this.captor = captor;
        }

        @Override
        public void start(StartContext context) {
            this.provider.get().thenCompose(value -> {
                NonBlockingLifecycle lifecycle = this.lifecycleProvider.apply(value);
                this.reference.set(lifecycle);
                CompletionStage<Void> start = lifecycle.isStopped() ? lifecycle.start() : NonBlockingLifecycle.COMPLETED;
                return start.thenRun(() -> this.captor.accept(value));
            }).whenComplete((ignore, e) -> {
                if (e != null) {
                    context.failed(new StartException(e));
                } else {
                    context.complete();
                }
            });
            context.asynchronous();
        }

        @Override
        public void stop(StopContext context) {
            this.captor.accept(null);
            NonBlockingLifecycle lifecycle = this.reference.getAndSet(null);
            if ((lifecycle != null) && lifecycle.isStarted()) {
                lifecycle.stop().whenComplete((ignore, e) -> {
                    if (e != null) {
                        LOGGER.warn(e.getLocalizedMessage(), e);
                    }
                    context.complete();
                });
                context.asynchronous();
            }
        }
    }
}
