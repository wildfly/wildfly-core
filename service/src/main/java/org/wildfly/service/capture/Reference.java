/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service.capture;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Encapsulates a readable reference to a value.
 * @author Paul Ferraro
 * @param <V> the referenced value type
 */
public interface Reference<V> extends Supplier<V> {
    /**
     * Returns a mapped reference using the specified mapping function, only applied when present.
     * @param <R> the mapped reference type
     * @param mapper a mapping function
     * @return a mapped reference using the specified mapping function.
     */
    default <R> Reference<R> thenApply(Function<? super V, ? extends R> mapper) {
        return new Reference<>() {
            @Override
            public R get() {
                V value = Reference.this.get();
                return (value != null) ? mapper.apply(value) : null;
            }
        };
    }

    /**
     * Returns a runnable that consumes the referenced value, if present
     * @param consumer a consumer of the referenced value
     * @return a runnable that consumes the referenced value, if present
     */
    default Runnable thenAccept(Consumer<? super V> consumer) {
        return new Runnable() {
            @Override
            public void run() {
                V value = Reference.this.get();
                if (value != null) {
                    consumer.accept(value);
                }
            }
        };
    }
}
