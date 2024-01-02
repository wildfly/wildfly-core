/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.Functions;
import org.wildfly.service.Installer;

/**
 * A {@link ResourceServiceInstaller} that encapsulates service installation into a {@link RequirementServiceTarget}.
 * @author Paul Ferraro
 */
public interface ServiceInstaller extends ResourceServiceInstaller, DeploymentServiceInstaller, Installer<RequirementServiceTarget> {

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the specified value.
     * @param <V> the service value type
     * @param value the service value
     * @return a service installer builder
     */
    static <V> UnaryBuilder<V, V> builder(V value) {
        return builder(Functions.constantSupplier(value)).asActive();
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified dependency.
     * @param <V> the service value type
     * @param dependency a service dependency
     * @return a service installer builder
     */
    static <V> UnaryBuilder<V, V> builder(ServiceDependency<V> dependency) {
        Supplier<V> supplier = dependency;
        return builder(supplier).requires(dependency).asPassive();
    }

    /**
     * Returns a {@link ServiceInstaller} builder whose installed service provides the value supplied by the specified factory.
     * @param <V> the service value type
     * @param factory provides the service value
     * @return a service installer builder
     */
    static <V> UnaryBuilder<V, V> builder(Supplier<V> factory) {
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
    static <T, V> UnaryBuilder<T, V> builder(Function<T, V> mapper, Supplier<T> factory) {
        return new DefaultUnaryBuilder<>(mapper, factory);
    }

    /**
     * Returns a {@link ServiceInstaller} builder that installs the specified installer into a child target.
     * @param installer a service installer
     * @return a service installer builder
     */
    static Builder builder(org.wildfly.service.ServiceInstaller installer) {
        return new DefaultNullaryBuilder(new Service() {
            @Override
            public void start(StartContext context) {
                installer.install(context.getChildTarget());
            }

            @Override
            public void stop(StopContext context) {
                // Services installed into child target are auto-removed after this service stops.
            }
        }).asActive();
    }

    /**
     * Returns a {@link ServiceInstaller} builder that executes the specified tasks on {@link Service#start(StartContext)} and {@link Service#stop(StopContext)}, respectively.
     * @param startTask a start task
     * @param stopTask a stop task
     * @return a service installer builder
     */
    static Builder builder(Runnable startTask, Runnable stopTask) {
        return new DefaultNullaryBuilder(new Service() {
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

    /**
     * Builds a {@link ServiceInstaller}.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface Builder extends AsyncBuilder<Builder>, Installer.Builder<Builder, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>> {
    }

    /**
     * Builds a {@link ServiceInstaller} whose installed service provides a single value.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface UnaryBuilder<T, V> extends AsyncBuilder<UnaryBuilder<T, V>>, Installer.UnaryBuilder<UnaryBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> {
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

    @Override
    default void install(DeploymentPhaseContext context) {
        this.install(context.getRequirementServiceTarget());
    }

    class DefaultServiceInstaller extends DefaultInstaller<RequirementServiceTarget, RequirementServiceBuilder<?>, RequirementServiceBuilder<?>> implements ServiceInstaller {

        DefaultServiceInstaller(Configuration<RequirementServiceBuilder<?>, RequirementServiceBuilder<?>> config, Function<RequirementServiceTarget, RequirementServiceBuilder<?>> serviceBuilderFactory) {
            super(config, serviceBuilderFactory);
        }
    }

    class DefaultNullaryBuilder extends AbstractNullaryBuilder<Builder, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, RequirementServiceBuilder<?>> implements Builder {
        private volatile boolean sync = true;

        DefaultNullaryBuilder(Service service) {
            super(service);
        }

        @Override
        public Builder async() {
            this.sync = false;
            return this;
        }

        @Override
        public ServiceInstaller build() {
            boolean sync = this.sync;
            return new DefaultServiceInstaller(this, new Function<>() {
                @Override
                public RequirementServiceBuilder<?> apply(RequirementServiceTarget target) {
                    RequirementServiceBuilder<?> builder = target.addService();
                    return !sync ? new AsyncServiceBuilder<>(builder, AsyncServiceBuilder.Async.START_AND_STOP) : builder;
                }
            });
        }

        @Override
        protected Builder builder() {
            return this;
        }
    }

    class DefaultUnaryBuilder<T, V> extends AbstractUnaryBuilder<UnaryBuilder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> implements UnaryBuilder<T, V> {
        private volatile boolean sync = true;

        DefaultUnaryBuilder(Function<T, V> mapper, Supplier<T> factory) {
            super(mapper, factory);
        }

        @Override
        public UnaryBuilder<T, V> async() {
            this.sync = false;
            return this;
        }

        @Override
        public ServiceInstaller build() {
            boolean sync = this.sync;
            // If no stop task is specified, we can stop synchronously
            AsyncServiceBuilder.Async async = this.hasStopTask() ? AsyncServiceBuilder.Async.START_AND_STOP : AsyncServiceBuilder.Async.START_ONLY;
            return new DefaultServiceInstaller(this, new Function<>() {
                @Override
                public RequirementServiceBuilder<?> apply(RequirementServiceTarget target) {
                    RequirementServiceBuilder<?> builder = target.addService();
                    return !sync ? new AsyncServiceBuilder<>(builder, async) : builder;
                }
            });
        }

        @Override
        protected UnaryBuilder<T, V> builder() {
            return this;
        }
    }
}
