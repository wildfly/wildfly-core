/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.wildfly.common.function.ExceptionFunction;

/**
 * The consumer interface for a registry of captured values.
 * @author Paul Ferraro
 * @param <K> the registry key type
 * @param <V> the registry value type
 */
public interface FunctionExecutorRegistry<K, V> {
    /**
     * Returns an executor for the specified key.
     * @param key a registry key
     * @return an executor for the specified key.
     */
    FunctionExecutor<V> getExecutor(K key);

    /**
     * Returns a registry of executors composed from the executors of this registry.
     * @param <T> the composed executor type
     * @param mapper a composition function
     * @return a registry of executors composed from the executors of this registry.
     */
    default <T> FunctionExecutorRegistry<K, T> compose(Function<? super V, ? extends T> composer) {
        return new FunctionExecutorRegistry<>() {
            @Override
            public FunctionExecutor<T> getExecutor(K key) {
                return FunctionExecutorRegistry.this.getExecutor(key).compose(composer);
            }
        };
    }

    /**
     * Returns a value registry that consumes values via the corresponding executor.
     * @param <KK> the key type of the composed registry
     * @param <VV> the value type of the composeed registry
     * @param composer a mapping function for the registry key
     * @param consumer a consumer of the registry value.
     * @return a value registry that applies values via the corresponding executor.
     */
    default <T> ValueRegistry<K, T> compose(BiConsumer<? super V, ? super T> consumer) {
        return new ValueRegistry<>() {
            @Override
            public Consumer<T> add(K key) {
                FunctionExecutor<V> executor = FunctionExecutorRegistry.this.getExecutor(key);
                return new Consumer<>() {
                    @Override
                    public void accept(T value) {
                        executor.execute(new ExceptionFunction<>() {
                            @Override
                            public Object apply(V serviceValue) {
                                consumer.accept(serviceValue, value);
                                return null;
                            }
                        });
                    }
                };
            }

            @Override
            public void remove(K key) {
                // No-op
            }
        };
    }
}
