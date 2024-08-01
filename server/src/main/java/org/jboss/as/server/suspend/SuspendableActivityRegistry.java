/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import org.wildfly.service.descriptor.NullaryServiceDescriptor;

/**
 * A registry of suspendable server activity.
 */
public interface SuspendableActivityRegistry extends SuspensionStateProvider {
    NullaryServiceDescriptor<SuspendableActivityRegistry> SERVICE_DESCRIPTOR = SuspensionStateProvider.SERVICE_DESCRIPTOR.asType(SuspendableActivityRegistry.class);

    /**
     * Identifies a priority group of suspendable activity.
     */
    interface SuspendPriority {
        int ordinal();

        SuspendPriority FIRST = of(0);
        SuspendPriority DEFAULT = of(5);
        SuspendPriority LAST = of(10);

        /**
         * Creates a suspend priority using the specified ordinal
         * @param ordinal a value between 0 and 10, inclusively
         * @return a suspend priority
         */
        static SuspendPriority of(int ordinal) {
            return new SuspendPriority() {
                @Override
                public int ordinal() {
                    return ordinal;
                }
            };
        }
    }

    /**
     * Registers the specified {@link SuspendableActivity} to this registry using {@link SuspendPriority#DEFAULT}.
     * @param activity suspendable activity that should complete prior to suspending/resuming the server
     */
    default void registerActivity(SuspendableActivity activity) {
        this.registerActivity(activity, SuspendPriority.DEFAULT);
    }

    /**
     * Registers the specified {@link SuspendableActivity} to this registry using the specified priority.
     * If this activity was already added to this registry, it will retain its original priority.
     * @param activity suspendable activity that should complete prior to suspending/resuming the server
     * @param priority the priority group with which this activity should be registered
     */
    void registerActivity(SuspendableActivity activity, SuspendPriority priority);

    /**
     * Unregisters the specified {@link SuspendableActivity} from this registry, if it was previously added.
     * @param activity suspendable activity previously registered via {@link #registerActivity(SuspendableActivity)} or {@link #registerActivity(SuspendableActivity, SuspendPriority)}.
     */
    void unregisterActivity(SuspendableActivity activity);
}
