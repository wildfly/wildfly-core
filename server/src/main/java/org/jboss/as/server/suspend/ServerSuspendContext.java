/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

/**
 * A context for server suspend handling.
 */
public interface ServerSuspendContext extends ServerResumeContext {
    /**
     * Indicates whether the server is suspending as part of the shutdown sequence.
     * @return true, if the server is stopping, false otherwise.
     */
    boolean isStopping();
}
