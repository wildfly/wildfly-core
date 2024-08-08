/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

/**
 * A context for server resume handling.
 */
public interface ServerResumeContext {
    /**
     * Indicates whether the server is resuming as part of the startup sequence.
     * @return true, if the server is starting, false otherwise.
     */
    boolean isStarting();
}
