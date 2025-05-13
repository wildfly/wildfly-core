/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Specialization of {@link ExecutorService} that disables
 * caller attempts to shut invoke shutdown methods.
 *
 * @author Alexey Loubyansky
 */
public interface ManagedExecutorService extends ExecutorService {

    /**
     * Does nothing.
     */
    @Override
    default void shutdown() {
        // Don't shut down managed executor
    }

    /**
     * Does nothing other than return an empty list.
     *
     * @return an empty list
     */
    @Override
    default List<Runnable> shutdownNow() {
        // Don't shut down managed executor
        return Collections.emptyList();
    }
}
