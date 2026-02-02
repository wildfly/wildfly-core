/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import org.wildfly.common.function.Functions;

/**
 * Function that returns a supplied value, discarding its parameter.
 * @author Paul Ferraro
 * @param <T> the function parameter type
 * @param <R> the function return type
 */
class DiscardingFunction<T, R> implements Function<T, R> {
    private static final Function<?, ?> NULL = new DiscardingFunction<>(Functions.constantSupplier(null));
    private static final Function<?, CompletionStage<Void>> COMPLETE = new DiscardingFunction<>(CompletableFuture.completedStage(null));

    @SuppressWarnings("unchecked")
    static <T, R> Function<T, R> empty() {
        return (Function<T, R>) NULL;
    }

    @SuppressWarnings("unchecked")
    static <T> Function<T, CompletionStage<Void>> complete() {
        return (Function<T, CompletionStage<Void>>) COMPLETE;
    }

    private final Supplier<R> supplier;

    DiscardingFunction(R value) {
        this(Functions.constantSupplier(value));
    }

    DiscardingFunction(Supplier<R> supplier) {
        this.supplier = supplier;
    }

    @Override
    public R apply(T ignore) {
        return this.supplier.get();
    }
}
