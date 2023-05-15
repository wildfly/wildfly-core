/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.function.Supplier;

import org.wildfly.common.function.ExceptionFunction;

/**
 * Encapsulates execution of a function.
 * @author Paul Ferraro
 * @param <V> the type of the function argument
 */
public interface FunctionExecutor<V> {

    static <V> FunctionExecutor<V> of(Supplier<V> reference) {
        return new FunctionExecutor<>() {
            @Override
            public <R, E extends Exception> R execute(ExceptionFunction<V, R, E> function) throws E {
                V value = reference.get();
                return (value != null) ? function.apply(value) : null;
            }
        };
    }

    /**
     * Executes the given function.
     * @param <R> the return type
     * @param <E> the exception type
     * @param function a function to execute
     * @return the result of the function
     * @throws E if the function fails to execute
     */
    <R, E extends Exception> R execute(ExceptionFunction<V, R, E> function) throws E;
}
