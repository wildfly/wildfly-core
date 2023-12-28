/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service.capture;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A registry of captured values.
 * @author Paul Ferraro
 * @param <K> the registry key type
 * @param <V> the registry value type
 */
public interface ValueExecutorRegistry<K, V> extends ValueRegistry<K, V>, FunctionExecutorRegistry<K, V> {

    /**
     * Creates a new registry of values.
     * @param <K> the registry key type
     * @param <V> the registry value type
     * @return a new registry instance
     */
    static <K, V> ValueExecutorRegistry<K, V> newInstance() {
        return new ValueExecutorRegistry<>() {
            private final Map<K, AtomicReference<V>> references = new ConcurrentHashMap<>();

            private AtomicReference<V> create(K dependency) {
                return new AtomicReference<>();
            }

            @Override
            public Consumer<V> add(K key) {
                AtomicReference<V> reference = this.references.computeIfAbsent(key, this::create);
                return reference::set;
            }

            @Override
            public void remove(K key) {
                AtomicReference<V> reference = this.references.remove(key);
                if (reference != null) {
                    reference.set(null);
                }
            }

            @Override
            public FunctionExecutor<V> getExecutor(K key) {
                AtomicReference<V> reference = this.references.get(key);
                return (reference != null) ? FunctionExecutor.of(reference::get) : null;
            }
        };
    }
}
