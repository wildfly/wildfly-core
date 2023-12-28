/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.function.Consumer;

import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.service.capture.ValueExecutorRegistry;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A registry of captured values.
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
        ValueExecutorRegistry<ServiceDependency<V>, V> registry = ValueExecutorRegistry.newInstance();
        return new ServiceValueExecutorRegistry<>() {
            @Override
            public Consumer<V> add(ServiceDependency<V> dependency) {
                return registry.add(dependency);
            }

            @Override
            public void remove(ServiceDependency<V> dependency) {
                registry.remove(dependency);
            }

            @Override
            public FunctionExecutor<V> getExecutor(ServiceDependency<V> dependency) {
                return registry.getExecutor(dependency);
            }
        };
    }
}
