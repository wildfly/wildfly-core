/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.function.Consumer;

import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * A registry of captured service value executors.
 * @author Paul Ferraro
 * @param <V> the captured service value type
 */
public interface ResourceServiceValueExecutorRegistry<V> extends ResourceServiceValueRegistry<V>, FunctionExecutorRegistry<V> {

    /**
     * Creates a new {@link ResourceServiceValueExecutorRegistry}.
     * @param <V> the captured service value type
     * @return a new service value executor registry
     */
    static <V> ResourceServiceValueExecutorRegistry<V> create() {
        ServiceValueExecutorRegistry<V> registry = ServiceValueExecutorRegistry.create();
        return new ResourceServiceValueExecutorRegistry<>() {
            @Override
            public ServiceInstaller capture(ServiceDependency<V> dependency) {
                Consumer<V> startTask = new Consumer<>() {
                    @Override
                    public void accept(V value) {
                        registry.add(dependency).accept(value);
                    }
                };
                Consumer<V> stopTask = new Consumer<>() {
                    @Override
                    public void accept(V value) {
                        registry.remove(dependency);
                    }
                };
                return ServiceInstaller.builder(dependency).onStart(startTask).onStop(stopTask).asPassive().build();
            }

            @Override
            public FunctionExecutor<V> getExecutor(ServiceDependency<V> dependency) {
                return registry.getExecutor(dependency);
            }
        };
    }
}
