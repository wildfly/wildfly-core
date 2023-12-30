/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capability;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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
     * @param <V> the service value type
     * @param capability the target capability
     * @param value the service value
     * @return a service installer builder
     */
    static <V> Builder<V, V> builder(RuntimeCapability<Void> capability, V value) {
        return builder(capability, Functions.constantSupplier(value)).asActive();
    }

    /**
     * Returns a {@link CapabilityServiceInstaller} builder for the specified capability whose installed service provides the value supplied by the specified dependency.
     * @param <V> the service value type
     * @param capability the target capability
     * @param dependency a service dependency
     * @return a service installer builder
     */
    static <V> Builder<V, V> builder(RuntimeCapability<Void> capability, ServiceDependency<V> dependency) {
        Supplier<V> supplier = dependency;
        return builder(capability, supplier).requires(dependency).asPassive();
    }

    /**
     * Returns a {@link CapabilityServiceInstaller} builder for the specified capability whose installed service provides the value supplied by the specified factory.
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
     * @param <T> the source value type
     * @param <V> the service value type
     * @param capability the target capability
     * @param mapper a function that returns the service value given the value supplied by the factory
     * @param factory provides the input to the specified mapper
     * @return a service installer builder
     */
    static <T, V> Builder<T, V> builder(RuntimeCapability<Void> capability, Function<T, V> mapper, Supplier<T> factory) {
        return new DefaultCapabilityServiceInstaller.Builder<>(capability, mapper, factory);
    }

    /**
     * Builds a {@link CapabilityServiceInstaller}.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    interface Builder<T, V> extends Installer.Builder<Builder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, RequirementServiceBuilder<?>, T, V> {
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

    /**
     * A default {@link CapabilityServiceInstaller} that installs a generic service.
     * @param <T> the source value type
     * @param <V> the service value type
     */
    class DefaultCapabilityServiceInstaller<T, V> extends AbstractInstaller<CapabilityServiceTarget, CapabilityServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> implements CapabilityServiceInstaller {
        private static final Function<CapabilityServiceTarget, CapabilityServiceBuilder<?>> FACTORY = CapabilityServiceTarget::addService;

        DefaultCapabilityServiceInstaller(Installer.Configuration<CapabilityServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> config) {
            super(config, FACTORY);
        }

        static class Builder<T, V> extends AbstractInstaller.Builder<CapabilityServiceInstaller.Builder<T, V>, CapabilityServiceInstaller, CapabilityServiceTarget, CapabilityServiceBuilder<?>, RequirementServiceBuilder<?>, T, V> implements CapabilityServiceInstaller.Builder<T, V> {
            private final RuntimeCapability<Void> capability;
            private volatile boolean sync = true;
            // If no stop task is specified, we can stop synchronously
            private volatile AsyncServiceBuilder.Async async = AsyncServiceBuilder.Async.START_ONLY;

            Builder(RuntimeCapability<Void> capability, Function<T, V> mapper, Supplier<T> factory) {
                super(mapper, factory);
                this.capability = capability;
            }

            @Override
            public CapabilityServiceInstaller.Builder<T, V> async() {
                this.sync = false;
                return this;
            }

            @Override
            public Function<CapabilityServiceBuilder<?>, Consumer<V>> getProvider() {
                RuntimeCapability<Void> capability = this.capability;
                List<ServiceName> names = this.getServiceNames();
                return new Function<>() {
                    @Override
                    public Consumer<V> apply(CapabilityServiceBuilder<?> builder) {
                        return names.isEmpty() ? builder.provides(capability) : builder.provides(new RuntimeCapability[] { capability }, names.toArray(ServiceName[]::new));
                    }
                };
            }

            @Override
            public CapabilityServiceInstaller.Builder<T, V> onStop(Consumer<T> consumer) {
                this.async = AsyncServiceBuilder.Async.START_AND_STOP;
                return super.onStop(consumer);
            }

            @Override
            public UnaryOperator<CapabilityServiceBuilder<?>> getServiceBuilderDecorator() {
                AsyncServiceBuilder.Async async = this.async;
                return this.sync ? UnaryOperator.identity() : new UnaryOperator<>() {
                    @Override
                    public CapabilityServiceBuilder<?> apply(CapabilityServiceBuilder<?> builder) {
                        return new AsyncCapabilityServiceBuilder<>(builder, async);
                    }
                };
            }

            @Override
            public CapabilityServiceInstaller build() {
                return new DefaultCapabilityServiceInstaller<>(this);
            }

            @Override
            protected CapabilityServiceInstaller.Builder<T, V> builder() {
                return this;
            }
        }
    }
}
