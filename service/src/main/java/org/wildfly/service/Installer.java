/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
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
        B requires(Consumer<SB> dependency);

        /**
         * Configures dependencies of the installed service.
         * @param dependencies a variable number of dependencies
         * @return a reference to this builder
         */
        default B requires(Iterable<? extends Consumer<SB>> dependencies) {
            return this.requires(new Consumer<>() {
                @Override
                public void accept(SB builder) {
                    for (Consumer<SB> dependency : dependencies) {
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
         * @deprecated Superseded by {@link #withStartMode(StartWhen)}
         */
        @Deprecated(forRemoval = true, since = "29.0")
        default B asPassive() {
            return this.startWhen(StartWhen.AVAILABLE);
        }

        /**
         * Configures the installed service to start immediately after installation, forcing any dependencies to start.
         * @return a reference to this builder
         * @deprecated Superseded by {@link #withStartMode(StartWhen)}
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
     * Implemented by builders with blocking service support.
     * @param <B> the builder type
     */
    interface BlockingBuilder<B> {
        /**
         * Indicates that the installed service performs blocking operations on start and/or stop, and should be instrumented accordingly.
         * @return a reference to this builder
         */
        B blocking();
    }

    /**
     * Builds an installer of a service providing a single value.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface UnaryBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> extends Builder<B, I, ST, SB> {
        /**
         * Configures a service name provided by this service.
         * @param name a service name
         * @return a reference to this builder
         */
        B provides(ServiceName name);

        /**
         * Configures a captor invoked with the service value on {@link org.jboss.msc.Service#start(org.jboss.msc.service.StartContext)}, and with null on {@link org.jboss.msc.Service#stop(StopContext)}.
         * @param captor a consumer of the service value on start, and null on stop.
         * @return a reference to this builder
         */
        B withCaptor(Consumer<V> captor);

        /**
         * Configures a task to run on {@link org.jboss.msc.Service#start(org.jboss.msc.service.StartContext)}.
         * @param task a task consuming the service value source
         * @return a reference to this builder
         */
        B onStart(Consumer<T> task);

        /**
         * Configures a task to run on {@link org.jboss.msc.Service#stop(org.jboss.msc.service.StopContext)}.
         * @param task a task consuming the service value source
         * @return a reference to this builder
         */
        B onStop(Consumer<T> task);
    }

    /**
     * Encapsulates the configuration of an {@link Installer}.
     * @param <SB> the service builder type
     * @param <DSB> the dependency service builder type
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface Configuration<SB extends DSB, DSB extends ServiceBuilder<?>> {

        /**
         * Returns the initial mode of the installed service.
         * @return a service mode
         */
        ServiceController.Mode getInitialMode();

        /**
         * Returns the dependency of this service
         * @return a service dependency
         */
        Consumer<DSB> getDependency();

        /**
         * Returns the factory of this service
         * @return a service factory
         */
        Function<SB, Service> getServiceFactory();

        /**
         * Returns tasks to be run per lifecycle event.
         * The returned map is either fully populated, or an empty map, if this service has no lifecycle tasks.
         * @return a potentially empty map of tasks to be run per lifecycle event.
         */
        Map<LifecycleEvent, Collection<Runnable>> getLifecycleTasks();
    }

    /**
     * Generic abstract installer implementation that installs a {@link UnaryService}.
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <DSB> the dependency service builder type
     * @param <T> the source value type
     * @param <V> the provided value type of the service
     */
    class DefaultInstaller<ST extends ServiceTarget, SB extends DSB, DSB extends ServiceBuilder<?>> implements Installer<ST> {

        private final Function<ST, SB> serviceBuilderFactory;
        private final ServiceController.Mode mode;
        private final Consumer<DSB> dependency;
        private final Function<SB, Service> serviceFactory;
        private final Map<LifecycleEvent, Collection<Runnable>> lifecycleTasks;

        protected DefaultInstaller(Installer.Configuration<SB, DSB> config, Function<ST, SB> serviceBuilderFactory) {
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

    abstract class AbstractBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends DSB, DSB extends ServiceBuilder<?>> implements Installer.Builder<B, I, ST, DSB>, Installer.Configuration<SB, DSB> {
        // By default, MSC uses ACTIVE, but most services should start ON_DEMAND
        private volatile ServiceController.Mode mode = ServiceController.Mode.ON_DEMAND;
        private volatile Consumer<DSB> dependency = Functions.discardingConsumer();
        private volatile Map<LifecycleEvent, List<Runnable>> lifecycleTasks = Map.of();

        protected abstract B builder();

        @Override
        public B startWhen(StartWhen condition) {
            this.mode = condition.getMode();
            return this.builder();
        }

        @Override
        public B requires(Consumer<DSB> dependency) {
            this.dependency = (this.dependency == Functions.<DSB>discardingConsumer()) ? dependency : this.dependency.andThen(dependency);
            return this.builder();
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
                // Create EnumMap lazily, when needed
                this.lifecycleTasks = new EnumMap<>(LifecycleEvent.class);
                for (LifecycleEvent e : EnumSet.allOf(LifecycleEvent.class)) {
                    this.lifecycleTasks.put(e, (e == event) ? List.of(task) : List.of());
                }
            } else {
                List<Runnable> tasks = this.lifecycleTasks.get(event);
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
            return this.builder();
        }

        @Override
        public Mode getInitialMode() {
            return this.mode;
        }

        @Override
        public Consumer<DSB> getDependency() {
            return this.dependency;
        }

        @Override
        public Map<LifecycleEvent, Collection<Runnable>> getLifecycleTasks() {
            // Return empty map or fully unmodifiable copy
            if (this.lifecycleTasks.isEmpty()) return Map.of();
            Map<LifecycleEvent, Collection<Runnable>> result = new EnumMap<>(LifecycleEvent.class);
            for (Map.Entry<LifecycleEvent, List<Runnable>> entry : this.lifecycleTasks.entrySet()) {
                result.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
    }

    abstract class AbstractNullaryBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends DSB, DSB extends ServiceBuilder<?>> extends AbstractBuilder<B, I, ST, SB, DSB> implements Function<SB, Service> {
        private final Service service;

        protected AbstractNullaryBuilder(Service service) {
            this.service = service;
        }

        @Override
        public Function<SB, Service> getServiceFactory() {
            return this;
        }

        @Override
        public Service apply(SB builder) {
            return this.service;
        }
    }

    abstract class AbstractUnaryBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends DSB, DSB extends ServiceBuilder<?>, T, V> extends AbstractBuilder<B, I, ST, SB, DSB> implements Installer.UnaryBuilder<B, I, ST, DSB, T, V>, Function<SB, Service> {
        private final List<ServiceName> names = new LinkedList<>();
        private final Function<T, V> mapper;
        private final Supplier<T> factory;
        private final BiFunction<SB, Collection<ServiceName>, Consumer<V>> provider;
        private volatile Consumer<V> captor = Functions.discardingConsumer();
        private volatile Consumer<T> startTask = Functions.discardingConsumer();
        private volatile Consumer<T> stopTask = Functions.discardingConsumer();

        protected AbstractUnaryBuilder(Function<T, V> mapper, Supplier<T> factory) {
            this(mapper, factory, new BiFunction<>() {
                @Override
                public Consumer<V> apply(SB builder, Collection<ServiceName> names) {
                    return !names.isEmpty() ? builder.provides(names.toArray(ServiceName[]::new)) : Functions.discardingConsumer();
                }
            });
        }

        protected AbstractUnaryBuilder(Function<T, V> mapper, Supplier<T> factory, BiFunction<SB, Collection<ServiceName>, Consumer<V>> provider) {
            this.mapper = mapper;
            this.factory = factory;
            this.provider = provider;
        }

        @Override
        public B provides(ServiceName name) {
            this.names.add(name);
            return this.builder();
        }

        @Override
        public B withCaptor(Consumer<V> captor) {
            this.captor = compose(this.captor, captor);
            return this.builder();
        }

        @Override
        public B onStart(Consumer<T> task) {
            this.startTask = compose(this.startTask, task);
            return this.builder();
        }

        @Override
        public B onStop(Consumer<T> task) {
            this.stopTask = compose(this.stopTask, task);
            return this.builder();
        }

        private static <X> boolean isDefined(Consumer<X> task) {
            return task != Functions.discardingConsumer();
        }

        private static <X> Consumer<X> compose(Consumer<X> currentTask, Consumer<X> newTask) {
            return isDefined(currentTask) ? currentTask.andThen(newTask) : newTask;
        }

        @Override
        public Function<SB, Service> getServiceFactory() {
            return this;
        }

        @Override
        public Service apply(SB builder) {
            Consumer<V> injector = this.provider.apply(builder, this.names);
            Consumer<V> captor = this.captor;
            if (isDefined(captor)) {
                injector = isDefined(injector) ? injector.andThen(captor) : captor;
            }
            return new UnaryService<>(injector, this.mapper, this.factory, this.startTask, this.stopTask);
        }

        protected boolean hasStopTask() {
            return isDefined(this.stopTask);
        }
    }

    class UnaryService<T, V> implements Service {

        private final Consumer<V> captor;
        private final Function<T, V> mapper;
        private final Supplier<T> factory;
        private final Consumer<T> startTask;
        private final Consumer<T> stopTask;

        private volatile T value;

        UnaryService(Consumer<V> captor, Function<T, V> mapper, Supplier<T> factory, Consumer<T> startTask, Consumer<T> stopTask) {
            this.captor = captor;
            this.mapper = mapper;
            this.factory = factory;
            this.startTask = startTask;
            this.stopTask = stopTask;
        }

        @Override
        public void start(StartContext context) throws StartException {
            this.value = this.factory.get();
            this.startTask.accept(this.value);
            this.captor.accept(this.mapper.apply(this.value));
        }

        @Override
        public void stop(StopContext context) {
            try {
                this.stopTask.accept(this.value);
            } finally {
                this.value = null;
                this.captor.accept(null);
            }
        }
    }
}
