/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security manager related utilities for use in this package.
 * DO NOT EXPOSE THIS CLASS OUTSIDE THIS PACKAGE.
 *
 * @author Brian Stansberry
 */
final class SecurityActions {

    private SecurityActions() {}

    /**
     * Execute the given function, in a privileged block if a security manager is checking.
     * @param function the function
     * @param t        the argument to the function
     * @param <T>      the type of the argument to the function
     * @param <R>      the type of the function return value
     * @return         the return value of the function
     */
    static <T, R> R privilegedExecution(Function<T, R> function, T t) {
        return privilegedExecution().execute(function, t);
    }

    /**
     * Execute the given function, in a privileged block if a security manager is checking.
     * @param function the function
     * @param t        the first argument to the function
     * @param u        the second argument to the function
     * @param <T>      the type of the first argument to the function
     * @param <U>      the type of the second argument to the function
     * @param <R>      the type of the function return value
     * @return         the return value of the function
     */
    static <T, U, R> R privilegedExecution(BiFunction<T, U, R> function, T t, U u) {
        return privilegedExecution().execute(function, t, u);
    }

    /** Provides function execution in a doPrivileged block if a security manager is checking privileges */
    private static Execution privilegedExecution() {
        return WildFlySecurityManager.isChecking() ? Execution.PRIVILEGED : Execution.NON_PRIVILEGED;
    }

    /** Executes a function */
    private interface Execution {
        <T, R> R execute(Function<T, R> function, T t);
        <T, U, R> R execute(BiFunction<T, U, R> function, T t, U u);

        Execution NON_PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(Function<T, R> function, T t) {
                return function.apply(t);
            }
            @Override
            public <T, U, R> R execute(BiFunction<T, U, R> function, T t, U u) {
                return function.apply(t, u);
            }
        };

        Execution PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(Function<T, R> function, T t) {
                try {
                    return doPrivileged((PrivilegedExceptionAction<R>) () -> NON_PRIVILEGED.execute(function, t) );
                } catch (PrivilegedActionException e) {
                    throwConverted(e);
                    // Not reachable
                    throw new IllegalStateException();
                }
            }
            @Override
            public <T, U, R> R execute(BiFunction<T, U, R> function, T t, U u) {
                try {
                    return doPrivileged((PrivilegedExceptionAction<R>) () -> NON_PRIVILEGED.execute(function, t, u) );
                } catch (PrivilegedActionException e) {
                    throwConverted(e);
                    // Not reachable
                    throw new IllegalStateException();
                }
            }
        };

        static void throwConverted(PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                // Not possible as Function/BiFunction don't throw any checked exception
                throw new RuntimeException(cause);
            }
        }

    }
}
