/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt.support;

import java.util.concurrent.TimeUnit;

/**
 * A handle to a processor of management requests which can be used to coordinate a controlled shutdown
 * of a processor that allows active operations to complete before shutting down.
 *
 * TODO this should be redone to use callbacks to signal when all operations are completed
 *
 * @author Emanuel Muckenhuber
 */
public interface ManagementChannelShutdownHandle {

    /**
     * Don't allow new operations, but still allow requests for existing ones.
     *
     * <p>This method does not wait for previously submitted operations to be
     * completed. Use {@link #awaitCompletion awaitCompletion} to do that.
     * </p>
     */
    void shutdown();

    /**
     * This will attempt to cancel all active operations, without waiting for their completion.
     */
    void shutdownNow();

    /**
     * Await the completion of all currently active operations.
     *
     * @param timeout the timeout
     * @param unit the time unit
     * @return {@code false} if the timeout was reached and there were still active operations
     * @throws InterruptedException
     */
    boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException;

}
