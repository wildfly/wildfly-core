/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The provider interface for a registry of captured values.
 * @author Paul Ferraro
 * @param <K> the registry key type
 * @param <V> the registry value type
 */
public interface ValueRegistry<K, V> {

    /**
     * Adds a value registration for the specified key.
     * @param key a registry key
     * @return a consumer into which a value can be captured
     */
    Consumer<V> add(K key);

    /**
     * Removes the registration for the specified key, after which any consumers created via {@link #add(Object)} will no longer function.
     * @param key a registry key
     */
    void remove(K key);

    /**
     * Returns a registry composed from this registry via the specified key/value composition functions.
     * @param <KK> the key composition type
     * @param <VV> the value composition type
     * @param keyComposer a key composition function
     * @param valueComposer a value composition function
     * @return a registry that applies composed from this registry via the specified key/value composition functions.
     */
    default <KK, VV> ValueRegistry<KK, VV> compose(Function<? super KK, ? extends K> keyComposer, Function<? super VV, ? extends V> valueComposer) {
        return new ValueRegistry<>() {
            @Override
            public Consumer<VV> add(KK key) {
                Consumer<V> consumer = ValueRegistry.this.add(keyComposer.apply(key));
                return new Consumer<>() {
                    @Override
                    public void accept(VV value) {
                        consumer.accept(valueComposer.apply(value));
                    }
                };
            }

            @Override
            public void remove(KK key) {
                ValueRegistry.this.remove(keyComposer.apply(key));
            }
        };
    }

    /**
     * Returns a registry that adds/removes values to/from this and the specified registry.
     * @param registry a registry into/from which values will also be added/removed
     * @return a registry that adds/removes values to/from this and the specified registry.
     */
    default ValueRegistry<K, V> andThen(ValueRegistry<? super K, ? super V> registry) {
        return new ValueRegistry<>() {
            @Override
            public Consumer<V> add(K key) {
                return ValueRegistry.this.add(key).andThen(registry.add(key));
            }

            @Override
            public void remove(K key) {
                registry.remove(key);
                ValueRegistry.this.remove(key);
            }
        };
    }
}
