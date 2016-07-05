/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
