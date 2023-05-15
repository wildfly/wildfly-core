/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    static <V> ServiceValueExecutorRegistry<V> create() {
        return new ServiceValueExecutorRegistry<>() {
            private final Map<ServiceDependency<V>, AtomicReference<V>> references = new ConcurrentHashMap<>();

            private AtomicReference<V> create(ServiceDependency<V> dependency) {
                return new AtomicReference<>();
            }

            @Override
            public Consumer<V> add(ServiceDependency<V> dependency) {
                AtomicReference<V> reference = this.references.computeIfAbsent(dependency, this::create);
                return reference::set;
            }

            @Override
            public void remove(ServiceDependency<V> dependency) {
                AtomicReference<V> reference = this.references.remove(dependency);
                if (reference != null) {
                    reference.set(null);
                }
            }

            @Override
            public FunctionExecutor<V> getExecutor(ServiceDependency<V> dependency) {
                AtomicReference<V> reference = this.references.get(dependency);
                return (reference != null) ? FunctionExecutor.of(reference::get) : null;
            }
        };
    }

}
