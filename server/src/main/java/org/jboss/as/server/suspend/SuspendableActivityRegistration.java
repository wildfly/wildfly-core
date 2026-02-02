/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

/**
 * Encapsulates registration of some suspendable activity.
 * @author Paul Ferraro
 */
public interface SuspendableActivityRegistration extends SuspensionStateProvider, AutoCloseable {
    /**
     * Closes this registration.
     */
    @Override
    void close();
}
