/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import java.util.function.Consumer;

/**
 * Optimised version of {@link org.wildfly.common.function.Functions#discardingConsumer()} that avoids redundant object allocation during {@link Consumer#andThen(Consumer)}.
 * @author Paul Ferraro
 */
enum DiscardingConsumer implements Consumer<Object> {
    INSTANCE;

    /**
     * Returns a consumer that discards its parameter.
     * @param <T> the consumed object type
     * @return a consumer that discards its parameter.
     */
    @SuppressWarnings("unchecked")
    static <T> Consumer<T> of() {
        return (Consumer<T>) INSTANCE;
    }

    @Override
    public void accept(Object value) {
        // Do nothing
    }

    @Override
    public Consumer<Object> andThen(Consumer<? super Object> after) {
        // Avoid creating a redundant new consumer instance
        return (Consumer<Object>) after;
    }
}
