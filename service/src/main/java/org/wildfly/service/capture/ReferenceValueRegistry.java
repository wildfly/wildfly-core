/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service.capture;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A registry of references with {@link ValueRegistry provider-side} and {@link ReferenceRegistry consumer-side} views.
 * This is intended as a lightweight alternative to fixed value MSC services.
 * @author Paul Ferraro
 */
public interface ReferenceValueRegistry<K, V> extends ReferenceRegistry<K, V>, ValueRegistry<K, V> {

    /**
     * Creates a new registry of value references.
     * @param <K> the registry key type
     * @param <V> the registry value type
     * @return a new registry instance
     */
    static <K, V> ReferenceValueRegistry<K, V> newInstance() {
        Map<K, AtomicReference<V>> references = new ConcurrentHashMap<>();
        Function<K, AtomicReference<V>> factory = new Function<>() {
            @Override
            public AtomicReference<V> apply(K key) {
                return new AtomicReference<>();
            }
        };
        return new ReferenceValueRegistry<>() {
            @Override
            public Consumer<V> add(K key) {
                return references.computeIfAbsent(key, factory)::set;
            }

            @Override
            public void remove(K key) {
                AtomicReference<V> reference = references.remove(key);
                if (reference != null) {
                    reference.set(null);
                }
            }

            @Override
            public Reference<V> getReference(K key) {
                return new Reference<>() {
                    @Override
                    public V get() {
                        AtomicReference<V> reference = references.get(key);
                        return (reference != null) ? reference.get() : null;
                    }
                };
            }
        };
    }
}
