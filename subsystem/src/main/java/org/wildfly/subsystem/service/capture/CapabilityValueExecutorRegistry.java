/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.service.capture;

import java.util.function.Consumer;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.ServiceName;
import org.wildfly.service.BlockingLifecycle;
import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.service.capture.FunctionExecutorRegistry;
import org.wildfly.service.capture.ServiceValueExecutorRegistry;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * A registry of captured values with {@link CapabilityValueRegistry provider-side} and {@link CapabilityExecutorRegistry consumer-side} interfaces.
 * @author Paul Ferraro
 * @param <V> the service value type
 */
public interface CapabilityValueExecutorRegistry<V> extends CapabilityValueRegistry<V>, FunctionExecutorRegistry<PathAddress, V> {

    /**
     * Returns a registry of values captured from services of the specified capability.
     * @param <V> the value type provided by the services of the specified capability.
     * @param capability a capability from whom values will be captured
     * @return a registry of values captured from services of the specified capability.
     */
    static <V> CapabilityValueExecutorRegistry<V> of(RuntimeCapability<Void> capability) {
        ServiceValueExecutorRegistry<V> registry = ServiceValueExecutorRegistry.newInstance();
        return new CapabilityValueExecutorRegistry<>() {
            private ServiceName getServiceName(PathAddress address) {
                return capability.isDynamicallyNamed() ? capability.getCapabilityServiceName(address) : capability.getCapabilityServiceName();
            }

            @Override
            public Consumer<V> add(PathAddress address) {
                return registry.add(this.getServiceName(address));
            }

            @Override
            public void remove(PathAddress address) {
                registry.remove(this.getServiceName(address));
            }

            @Override
            public FunctionExecutor<V> getExecutor(PathAddress address) {
                return registry.getExecutor(this.getServiceName(address));
            }

            @Override
            public ServiceInstaller capture(PathAddress address) {
                ServiceDependency<V> dependency = ServiceDependency.on(this.getServiceName(address));
                CapabilityValueRegistry<V> registry = this;
                Consumer<V> start = new Consumer<>() {
                    @Override
                    public void accept(V value) {
                        registry.add(address).accept(value);
                    }
                };
                Consumer<V> stop = new Consumer<>() {
                    @Override
                    public void accept(V value) {
                        registry.remove(address);
                    }
                };
                return ServiceInstaller.BlockingBuilder.of(dependency).withLifecycle(BlockingLifecycle.compose(start, stop)).build();
            }
        };
    }
}
