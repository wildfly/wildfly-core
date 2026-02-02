/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import org.wildfly.service.descriptor.NullaryServiceDescriptor;

/**
 * A registrar for suspendable activity.
 * @author Paul Ferraro
 */
public interface SuspendableActivityRegistrar {
    NullaryServiceDescriptor<SuspendableActivityRegistrar> SERVICE_DESCRIPTOR = NullaryServiceDescriptor.of("org.wildfly.server.suspend-controller", SuspendableActivityRegistrar.class);

    /**
     * Registers the specified {@link SuspendableActivity} with default priority.
     * The registered {@link SuspendableActivity} will participate in suspend/resume operations until {@link SuspendableActivityRegistration#close()}.
     * @param activity suspendable activity that should complete prior to suspending/resuming the server
     * @return the suspendable activity registration
     */
    default SuspendableActivityRegistration register(SuspendableActivity activity) {
        return this.register(activity, SuspendPriority.DEFAULT);
    }

    /**
     * Registers the specified {@link SuspendableActivity}.
     * The registered {@link SuspendableActivity} will participate in suspend/resume operations until {@link SuspendableActivityRegistration#close()}.
     * @param activity suspendable activity that should complete prior to suspending/resuming the server
     * @param priority the suspend priority
     * @return the suspendable activity registration
     */
    SuspendableActivityRegistration register(SuspendableActivity activity, SuspendPriority priority);
}
