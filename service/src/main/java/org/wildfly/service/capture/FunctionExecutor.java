/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.function.Function;
import java.util.function.Supplier;

import org.wildfly.common.function.ExceptionFunction;

/**
 * Encapsulates execution of a single argument function.
 * @author Paul Ferraro
 * @param <V> the type of the function argument
 */
public interface FunctionExecutor<V> {

    /**
     * Creates a function executor from the specified reference.
     * @param <V> the value type of the function argument
     * @param reference a supplier of the function argument
     * @return a new function executor instance
     */
    static <V> FunctionExecutor<V> of(Supplier<? extends V> reference) {
        return new FunctionExecutor<>() {
            @Override
            public <R, E extends Exception> R execute(ExceptionFunction<? super V, ? extends R, E> function) throws E {
                V value = reference.get();
                return (value != null) ? function.apply(value) : null;
            }
        };
    }

    /**
     * Executes the specified function, using a value provided by an associated {@link ValueRegistry}.
     * @param <R> the return type
     * @param <E> the exception type
     * @param function a function to execute
     * @return the result of the function
     * @throws E if the function fails to execute
     */
    <R, E extends Exception> R execute(ExceptionFunction<? super V, ? extends R, E> function) throws E;

    /**
     * Returns a registry of executors composed from this registry via the specified function.
     * @param <T> the composition type
     * @param composer a composing function
     * @return a registry of executors composed from this registry via the specified function.
     */
    default <T> FunctionExecutor<T> compose(Function<? super V, ? extends T> composer) {
        return new FunctionExecutor<>() {
            @Override
            public <R, E extends Exception> R execute(ExceptionFunction<? super T, ? extends R, E> function) throws E {
                return FunctionExecutor.this.<R, E>execute(new ExceptionFunction<>() {
                    @Override
                    public R apply(V value) throws E {
                        return function.apply(composer.apply(value));
                    }
                });
            }
        };
    }
}
