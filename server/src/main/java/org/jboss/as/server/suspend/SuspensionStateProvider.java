/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import org.wildfly.service.descriptor.NullaryServiceDescriptor;

/**
 * Provides the suspend state of the server.
 */
public interface SuspensionStateProvider {
    NullaryServiceDescriptor<SuspensionStateProvider> SERVICE_DESCRIPTOR = NullaryServiceDescriptor.of("org.wildfly.server.suspend-controller", SuspensionStateProvider.class);

    enum State {
        RUNNING,
        PRE_SUSPEND,
        SUSPENDING,
        SUSPENDED
    }

    /**
     * Returns the current suspension state.
     * @return the current suspension stage.
     */
    State getState();
}
