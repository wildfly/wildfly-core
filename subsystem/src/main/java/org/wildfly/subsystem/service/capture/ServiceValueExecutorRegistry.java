/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.function.Consumer;

import org.jboss.msc.service.ServiceName;
import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.service.capture.ValueExecutorRegistry;

/**
 * A registry of captured values with {@link ServiceValueRegistry provider-side} and {@link FunctionExecutorRegistry consumer-side} interfaces.
 * @author Paul Ferraro
 * @param <V> the captured value type
 */
public interface ServiceValueExecutorRegistry<V> extends ServiceValueRegistry<V>, FunctionExecutorRegistry<V> {

    /**
     * Creates a new {@link ServiceValueExecutorRegistry}.
     * @param <V> the captured value type
     * @return a new value executor registry
     */
    static <V> ServiceValueExecutorRegistry<V> newInstance() {
        ValueExecutorRegistry<ServiceName, V> registry = ValueExecutorRegistry.newInstance();
        return new ServiceValueExecutorRegistry<>() {
            @Override
            public Consumer<V> add(ServiceName name) {
                return registry.add(name);
            }

            @Override
            public void remove(ServiceName name) {
                registry.remove(name);
            }

            @Override
            public FunctionExecutor<V> getExecutor(ServiceName name) {
                return registry.getExecutor(name);
            }
        };
    }
}
