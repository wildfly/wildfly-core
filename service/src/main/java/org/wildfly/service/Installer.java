/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jboss.msc.Service;
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
     * Returns a composite dependency that delegates to the specified dependencies.
     * @param <T> the consumed object type
     * @param dependency1 a consumer
     * @param dependency2 a consumer
     * @return a composite dependency
     */
    static <T> Consumer<T> combine(Consumer<T> dependency1, Consumer<T> dependency2) {
        return combine(List.of(dependency1, dependency2));
    }

    /**
     * Returns a composite dependency that delegates to the specified dependencies.
     * @param <T> the consumed object type
     * @param dependencies an arbitrary number of dependencies
     * @return a composite dependency
     */
    static <T> Consumer<T> combine(@SuppressWarnings("unchecked") Consumer<T>... dependencies) {
        return combine(List.of(dependencies));
    }

    /**
     * Returns a composite dependency that delegates to the specified dependencies.
     * @param <T> the consumed object type
     * @param dependencies an arbitrary number of dependencies
     * @return a composite dependency
     */
    static <T> Consumer<T> combine(Iterable<? extends Consumer<T>> dependencies) {
        return new Consumer<>() {
            @Override
            public void accept(T value) {
                for (Consumer<? super T> dependency : dependencies) {
                    dependency.accept(value);
                }
            }
        };
    }

    /**
     * Builds an installer.
     * @param <B> the builder type
     * @param <I> the installer type
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface Builder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends ServiceBuilder<?>, T, V> {
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
         * Configures the dependency of the installed service.
         * @param dependency a dependency
         * @return a reference to this builder
         */
        B withDependency(Consumer<SB> dependency);

        /**
         * Configures the dependencies of the installed service.
         * @param dependency1 a dependency
         * @param dependency2 a second dependency
         * @return a reference to this builder
         */
        default B withDependencies(Consumer<SB> dependency1, Consumer<SB> dependency2) {
            return this.withDependency(combine(dependency1, dependency2));
        }

        /**
         * Configures the dependencies of the installed service.
         * @param dependencies a variable number of dependencies
         * @return a reference to this builder
         */
        default B withDependencies(@SuppressWarnings("unchecked") Consumer<SB>... dependencies) {
            return this.withDependency(combine(dependencies));
        }

        /**
         * Configures the dependencies of the installed service.
         * @param dependencies a variable number of dependencies
         * @return a reference to this builder
         */
        default B withDependencies(Iterable<? extends Consumer<SB>> dependencies) {
            return this.withDependency(combine(dependencies));
        }

        /**
         * Configures a service name provided by this service.
         * @param name a service name
         * @return a reference to this builder
         */
        B provides(ServiceName name);

        /**
         * Configures a task to run on {@link org.jboss.msc.Service#start(org.jboss.msc.service.StartContext)}.
         * @param task a task consuming the service value
         * @return a reference to this builder
         */
        B onStart(Consumer<V> task);

        /**
         * Configures a task to run on {@link org.jboss.msc.Service#stop(org.jboss.msc.service.StopContext)}.
         * @param task a task consuming the service value source
         * @return a reference to this builder
         */
        B onStop(Consumer<T> task);

        /**
         * Builds a service installer.
         * @return a service installer
         */
        I build();
    }

    /**
     * Encapsulates the configuration of an {@link Installer}.
     * @param <SB> the service builder type
     * @param <DSB> the dependency service builder type
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface Configuration<SB extends DSB, DSB extends ServiceBuilder<?>, T, V> {
        /**
         * Returns a function that maps the source type to the service type
         * @return a mapping function
         */
        Function<T, V> getMapper();

        /**
         * Returns a supplier of the source value of the service
         * @return a source value supplier
         */
        Supplier<T> getFactory();

        /**
         * Returns the initial mode of the installed service.
         * @return a service mode
         */
        ServiceController.Mode getInitialMode();

        /**
         * Returns a function returning a consumer into which the service should inject its provided value
         * @return a provider function
         */
        Function<SB, Consumer<V>> getProvider();

        /**
         * Returns the dependency of this service
         * @return a service dependency
         */
        Consumer<DSB> getDependency();

        /**
         * Returns a task that consumes the service value on {@link org.jboss.msc.Service#start(org.jboss.msc.service.StartContext)}.
         * @return a service value consumer
         */
        Consumer<V> getStartTask();

        /**
         * Returns a task that consumes the source value on {@link org.jboss.msc.Service#stop(org.jboss.msc.service.StopContext)}.
         * Typically used to close/destroys the value returned by @@link {@link #getFactory()}.
         * @return a source value consumer
         */
        Consumer<T> getStopTask();

        /**
         * Returns a service builder decorator used during service installation
         * @return a service builder decorator
         */
        UnaryOperator<SB> getServiceBuilderDecorator();
    }

    /**
     * Generic abstract installer implementation that installs a {@link DefaultService}.
     * @param <ST> the service target type
     * @param <SB> the service builder type
     * @param <DSB> the dependency service builder type
     * @param <T> the source value type
     * @param <V> the provided value type of the service
     */
    abstract class AbstractInstaller<ST extends ServiceTarget, SB extends DSB, DSB extends ServiceBuilder<?>, T, V> implements Installer<ST> {

        private final Function<ST, SB> serviceBuilderFactory;
        private final Function<SB, Consumer<V>> provider;
        private final Function<T, V> mapper;
        private final Supplier<T> factory;
        private final ServiceController.Mode mode;
        private final Consumer<DSB> dependency;
        private final Consumer<V> startTask;
        private final Consumer<T> stopTask;
        private final UnaryOperator<SB> decorator;

        protected AbstractInstaller(Installer.Configuration<SB, DSB, T, V> config, Function<ST, SB> serviceBuilderFactory) {
            this.serviceBuilderFactory = serviceBuilderFactory;
            this.provider = config.getProvider();
            this.mapper = config.getMapper();
            this.factory = config.getFactory();
            this.mode = config.getInitialMode();
            this.dependency = config.getDependency();
            this.startTask = config.getStartTask();
            this.stopTask = config.getStopTask();
            this.decorator = config.getServiceBuilderDecorator();
        }

        @Override
        public ServiceController<?> install(ST target) {
            SB builder = this.decorator.apply(this.serviceBuilderFactory.apply(target));
            Consumer<V> injector = this.provider.apply(builder);
            this.dependency.accept(builder);
            Service service = new DefaultService<>(combine(injector, this.startTask), this.mapper, this.factory, this.stopTask);
            return builder.setInstance(service).setInitialMode(this.mode).install();
        }

        protected abstract static class Builder<B, I extends Installer<ST>, ST extends ServiceTarget, SB extends DSB, DSB extends ServiceBuilder<?>, T, V> implements Installer.Builder<B, I, ST, DSB, T, V>, Installer.Configuration<SB, DSB, T, V> {
            private final List<ServiceName> names = new LinkedList<>();
            private final Function<T, V> mapper;
            private final Supplier<T> factory;
            private volatile ServiceController.Mode mode = ServiceController.Mode.ON_DEMAND;
            private volatile Consumer<DSB> dependency = Functions.discardingConsumer();
            private volatile Consumer<V> startTask = Functions.discardingConsumer();
            private volatile Consumer<T> stopTask = Functions.discardingConsumer();

            protected Builder(Function<T, V> mapper, Supplier<T> factory) {
                this.mapper = mapper;
                this.factory = factory;
            }

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
            public B withDependency(Consumer<DSB> dependency) {
                this.dependency = dependency;
                return this.builder();
            }

            @Override
            public B provides(ServiceName name) {
                this.names.add(name);
                return this.builder();
            }

            @Override
            public B onStart(Consumer<V> consumer) {
                this.startTask = consumer;
                return this.builder();
            }

            @Override
            public B onStop(Consumer<T> consumer) {
                this.stopTask = consumer;
                return this.builder();
            }

            @Override
            public Function<T, V> getMapper() {
                return this.mapper;
            }

            @Override
            public Supplier<T> getFactory() {
                return this.factory;
            }

            @Override
            public ServiceController.Mode getInitialMode() {
                return this.mode;
            }

            protected List<ServiceName> getServiceNames() {
                return List.copyOf(this.names);
            }

            @Override
            public Function<SB, Consumer<V>> getProvider() {
                List<ServiceName> names = this.getServiceNames();
                return new Function<>() {
                    @Override
                    public Consumer<V> apply(SB builder) {
                        return !names.isEmpty() ? builder.provides(names.toArray(ServiceName[]::new)) : Functions.discardingConsumer();
                    }
                };
            }

            @Override
            public Consumer<DSB> getDependency() {
                return this.dependency;
            }

            @Override
            public Consumer<V> getStartTask() {
                return this.startTask;
            }

            @Override
            public Consumer<T> getStopTask() {
                return this.stopTask;
            }
        }
    }

    class DefaultService<T, V> implements Service {

        private final Consumer<V> consumer;
        private final Function<T, V> mapper;
        private final Supplier<T> factory;
        private final Consumer<T> stopTask;

        private volatile T value;

        DefaultService(Consumer<V> consumer, Function<T, V> mapper, Supplier<T> factory, Consumer<T> stopTask) {
            this.consumer = consumer;
            this.mapper = mapper;
            this.factory = factory;
            this.stopTask = stopTask;
        }

        @Override
        public void start(StartContext context) throws StartException {
            try {
                this.value = this.factory.get();
                this.consumer.accept(this.mapper.apply(this.value));
            } catch (RuntimeException | Error e) {
                throw new StartException(e);
            }
        }

        @Override
        public void stop(StopContext context) {
            try {
                this.stopTask.accept(this.value);
            } finally {
                this.value = null;
                this.consumer.accept(null);
            }
        }
    }
}
