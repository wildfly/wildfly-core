/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.msc.Service;
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
         * Configures the installed service to automatically start when all of its dependencies are available
         * and to automatically stop when any of its dependencies are no longer available.
         * @return a reference to this builder
         */
        B asPassive();

        /**
         * Configures the installed service to start immediately after installation, forcing any dependencies to start.
         * @return a reference to this builder
         */
        B asActive();

        /**
         * Builds a service installer.
         * @return a service installer
         */
        I build();
    }

    /**
     * Implemented by builds with asynchronous service support.
     * @param <B> the builder type
     */
    interface AsyncBuilder<B> {
        /**
         * Indicates that the installed service should start and, if a stop task is configured, stop asynchronously.
         * @return a reference to this builder
         */
        B async();
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

        protected DefaultInstaller(Installer.Configuration<SB, DSB> config, Function<ST, SB> serviceBuilderFactory) {
            this.serviceBuilderFactory = serviceBuilderFactory;
            this.serviceFactory = config.getServiceFactory();
            this.mode = config.getInitialMode();
            this.dependency = config.getDependency();
        }

        @Override
        public ServiceController<?> install(ST target) {
            SB builder = this.serviceBuilderFactory.apply(target);
            this.dependency.accept(builder);
            return builder.setInstance(this.serviceFactory.apply(builder)).setInitialMode(this.mode).install();
        }
    }

    abstract class AbstractBuilder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends DSB, DSB extends ServiceBuilder<?>> implements Installer.Builder<B, I, ST, DSB>, Installer.Configuration<SB, DSB> {
        private volatile ServiceController.Mode mode = ServiceController.Mode.ON_DEMAND;
        private volatile Consumer<DSB> dependency = Functions.discardingConsumer();

        protected abstract B builder();

        @Override
        public B asPassive() {
            this.mode = ServiceController.Mode.PASSIVE;
            return this.builder();
        }

        @Override
        public B asActive() {
            this.mode = ServiceController.Mode.ACTIVE;
            return this.builder();
        }

        @Override
        public B requires(Consumer<DSB> dependency) {
            this.dependency = (this.dependency == Functions.<DSB>discardingConsumer()) ? dependency : this.dependency.andThen(dependency);
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
