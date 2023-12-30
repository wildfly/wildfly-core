/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.msc.service.ServiceController;
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
        Supplier<V> supplier = dependency;
        return builder(supplier).requires(dependency).asPassive();
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
    interface Builder<T, V> extends Installer.Builder<Builder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, T, V> {
        /**
         * Indicates that the installed service should start and, if a stop task is configured, stop asynchronously.
         * @return a reference to this builder
         */
        Builder<T, V> async();
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

    /**
     * Default service installer using native MSC service installation.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    class DefaultServiceInstaller<T, V> extends AbstractInstaller<RequirementServiceTarget, RequirementServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> implements ServiceInstaller {
        private static final Function<RequirementServiceTarget, RequirementServiceBuilder<?>> FACTORY = RequirementServiceTarget::addService;

        DefaultServiceInstaller(Installer.Configuration<RequirementServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> config) {
            super(config, FACTORY);
        }

        static class Builder<T, V> extends AbstractInstaller.Builder<ServiceInstaller.Builder<T, V>, ServiceInstaller, RequirementServiceTarget, RequirementServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> implements ServiceInstaller.Builder<T, V> {
            private volatile boolean sync = true;
            // If no stop task is specified, we can stop synchronously
            private volatile AsyncServiceBuilder.Async async = AsyncServiceBuilder.Async.START_ONLY;

            Builder(Function<T, V> mapper, Supplier<T> factory) {
                super(mapper, factory);
            }

            @Override
            public ServiceInstaller.Builder<T, V> async() {
                this.sync = false;
                return this;
            }

            @Override
            public ServiceInstaller.Builder<T, V> onStop(Consumer<T> consumer) {
                this.async = AsyncServiceBuilder.Async.START_AND_STOP;
                return super.onStop(consumer);
            }

            @Override
            public UnaryOperator<RequirementServiceBuilder<?>> getServiceBuilderDecorator() {
                AsyncServiceBuilder.Async async = this.async;
                return this.sync ? UnaryOperator.identity() : new UnaryOperator<>() {
                    @Override
                    public RequirementServiceBuilder<?> apply(RequirementServiceBuilder<?> builder) {
                        return new AsyncServiceBuilder<>(builder, async);
                    }
                };
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
