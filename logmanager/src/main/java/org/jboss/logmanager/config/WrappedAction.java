/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.config;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class WrappedAction {

    /**
     * Executes the action with the {@linkplain Thread#getContextClassLoader() TCCL} set to the class loader of the type.
     *
     * @param action the action to run
     * @param cl     the class loader to use for the TCCL
     * @param <R>    the return type
     *
     * @return the value from the action
     */
    static <R> R execute(final Supplier<R> action, final ClassLoader cl) {
        if (cl == null) {
            return action.get();
        }
        final ClassLoader current = getTccl();
        try {
            setTccl(cl);
            return action.get();
        } finally {
            setTccl(current);
        }
    }

    /**
     * Executes the action with the {@linkplain Thread#getContextClassLoader() TCCL} set to the class loader of the type.
     *
     * @param action the action to run
     * @param cl     the class loader to use for the TCCL
     */
    static void execute(final Runnable action, final ClassLoader cl) {
        if (cl == null) {
            action.run();
        } else {
            final ClassLoader current = getTccl();
            try {
                setTccl(cl);
                action.run();
            } finally {
                setTccl(current);
            }
        }
    }

    private static ClassLoader getTccl() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        }
        return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> Thread.currentThread().getContextClassLoader());
    }

    private static void setTccl(final ClassLoader cl) {
        if (System.getSecurityManager() == null) {
            Thread.currentThread().setContextClassLoader(cl);
        } else {
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                Thread.currentThread().setContextClassLoader(cl);
                return null;
            });
        }
    }
}
