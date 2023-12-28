/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.function.Consumer;

import org.jboss.msc.service.ServiceName;

/**
 * A registry of captured values.
 * @author Paul Ferraro
 * @param <V> the captured value type
 */
public interface ServiceValueExecutorRegistry<V> extends ServiceValueRegistry<V>, FunctionExecutorRegistry<ServiceName, V> {

    /**
     * Creates a new {@link ServiceValueExecutorRegistry}.
     * @param <V> the captured value type
     * @return a new value executor registry
     */
    static <V> ServiceValueExecutorRegistry<V> newInstance() {
        ValueExecutorRegistry<ServiceName, V> registry = ValueExecutorRegistry.newInstance();
        return new ServiceValueExecutorRegistry<>() {
            @Override
            public Consumer<V> add(ServiceName key) {
                return registry.add(key);
            }

            @Override
            public void remove(ServiceName key) {
                registry.remove(key);
            }

            @Override
            public FunctionExecutor<V> getExecutor(ServiceName key) {
                return registry.getExecutor(key);
            }
        };
    }
}
