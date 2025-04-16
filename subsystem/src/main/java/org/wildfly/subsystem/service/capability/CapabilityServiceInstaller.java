/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capability;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.common.function.Functions;
import org.wildfly.service.AsyncServiceBuilder;
import org.wildfly.service.Installer;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A {@link ResourceServiceInstaller} that encapsulates service installation into a {@link CapabilityServiceTarget}.
 * @author Paul Ferraro
 */
public interface CapabilityServiceInstaller extends ResourceServiceInstaller, Installer<CapabilityServiceTarget> {

    /**
     * Returns a {@link CapabilityServiceInstaller} builder for the specified capability whose installed service provides the specified value.
     * By default, the installed service will start when installed since the provided value is already available.
     * @param <V> the service value type
     * @param capability the target capability
     * @param value the service value
     * @return a service installer builder
     */
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
     */
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
     */
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
     */
    static <T, V> Builder<T, V> builder(RuntimeCapability<Void> capability, Function<T, V> mapper, Supplier<T> factory) {
        return new DefaultBuilder<>(capability, mapper, factory);
    }

    /**
     * Builds a {@link CapabilityServiceInstaller}.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface Builder<T, V> extends BlockingBuilder<Builder<T, V>>, UnaryBuilder<Builder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, RequirementServiceBuilder<?>, T, V> {
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

    class DefaultCapabilityServiceInstaller extends DefaultInstaller<CapabilityServiceTarget, CapabilityServiceBuilder<?>, RequirementServiceBuilder<?>> implements CapabilityServiceInstaller {

        DefaultCapabilityServiceInstaller(Configuration<CapabilityServiceBuilder<?>, RequirementServiceBuilder<?>> config, Function<CapabilityServiceTarget, CapabilityServiceBuilder<?>> serviceBuilderFactory) {
            super(config, serviceBuilderFactory);
        }
    }

    class DefaultBuilder<T, V> extends AbstractUnaryBuilder<Builder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> implements Builder<T, V> {
        private volatile boolean blocking = false;

        DefaultBuilder(RuntimeCapability<Void> capability, Function<T, V> mapper, Supplier<T> factory) {
            super(mapper, factory, new BiFunction<>() {
                @Override
                public Consumer<V> apply(CapabilityServiceBuilder<?> builder, Collection<ServiceName> names) {
                    return names.isEmpty() ? builder.provides(capability) : builder.provides(new RuntimeCapability[] { capability }, names.toArray(ServiceName[]::new));
                }
            });
        }

        @Override
        public Builder<T, V> blocking() {
            this.blocking = true;
            return this;
        }

        @Override
        public CapabilityServiceInstaller build() {
            boolean blocking = this.blocking;
            // If no stop task is specified, we can stop synchronously
            AsyncServiceBuilder.Async async = this.hasStopTask() ? AsyncServiceBuilder.Async.START_AND_STOP : AsyncServiceBuilder.Async.START_ONLY;
            return new DefaultCapabilityServiceInstaller(this, new Function<>() {
                @Override
                public CapabilityServiceBuilder<?> apply(CapabilityServiceTarget target) {
                    CapabilityServiceBuilder<?> builder = target.addService();
                    return blocking ? new AsyncCapabilityServiceBuilder<>(builder, async) : builder;
                }
            });
        }

        @Override
        protected Builder<T, V> builder() {
            return this;
        }
    }
}
