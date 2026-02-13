/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * An operator that chains consumers.
 * @author Paul Ferraro
 */
class ConsumerChainingOperator<T> implements UnaryOperator<Consumer<T>> {
    private final Consumer<? super T> after;

    ConsumerChainingOperator(Consumer<? super T> after) {
        this.after = after;
    }

    @Override
    public Consumer<T> apply(Consumer<T> before) {
        return before.andThen(this.after);
    }
}
