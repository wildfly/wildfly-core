/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service.capture;

import java.util.function.Consumer;

/**
 * A registry of captured values with {@link ValueRegistry provider-side} and {@link FunctionExecutorRegistry consumer-side} interfaces.
 * @author Paul Ferraro
 * @param <K> the registry key type
 * @param <V> the registry value type
 */
public interface ValueExecutorRegistry<K, V> extends ValueRegistry<K, V>, FunctionExecutorRegistry<K, V> {

    /**
     * Creates a new registry of executors.
     * @param <K> the registry key type
     * @param <V> the registry value type
     * @return a new executor registry.
     */
    static <K, V> ValueExecutorRegistry<K, V> newInstance() {
        ReferenceValueRegistry<K, V> registry = ReferenceValueRegistry.newInstance();
        return new ValueExecutorRegistry<>() {
            @Override
            public Consumer<V> add(K key) {
                return registry.add(key);
            }

            @Override
            public void remove(K key) {
                registry.remove(key);
            }

            @Override
            public FunctionExecutor<V> getExecutor(K key) {
                return FunctionExecutor.of(registry.getReference(key));
            }
        };
    }
}
