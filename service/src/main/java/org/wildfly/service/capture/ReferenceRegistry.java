/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service.capture;

import java.util.function.Function;

/**
 * The consumer interface for a registry of captured values.
 * @author Paul Ferraro
 */
public interface ReferenceRegistry<K, V> {
    /**
     * Returns a supplier providing the value for the specified key, if present, or null otherwise.
     * @param key a registry key
     * @return a supplier providing the value for the specified key, if present, or null otherwise.
     */
    Reference<V> getReference(K key);

    /**
     * Returns a registry that provides mapped references using the specified mapping function.
     * @param <R> the mapped value type
     * @param mapper a mapping function
     * @return a registry that provides mapped references using the specified mapping function.
     */
    default <R> ReferenceRegistry<K, R> map(Function<? super V, ? extends R> mapper) {
        return new ReferenceRegistry<>() {
            @Override
            public Reference<R> getReference(K key) {
                return ReferenceRegistry.this.getReference(key).thenApply(mapper);
            }
        };
    }
}
