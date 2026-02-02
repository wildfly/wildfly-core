/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import org.jboss.as.server.suspend.ServerSuspendController.Context;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;

/**
 * A registry of suspendable server activity.
 * @deprecated Superseded by {@link SuspendableActivityRegistrar}.
 */
@Deprecated(forRemoval = true, since = "32.0")
public interface SuspendableActivityRegistry extends SuspendableActivityRegistrar, SuspensionStateProvider {
    NullaryServiceDescriptor<SuspendableActivityRegistry> SERVICE_DESCRIPTOR = SuspendableActivityRegistrar.SERVICE_DESCRIPTOR.asType(SuspendableActivityRegistry.class);

    /**
     * Identifies a priority group of suspendable activity.
     * @deprecated Superseded by {@link SuspendPriority}.
     */
    @Deprecated(forRemoval = true, since = "32.0")
    interface SuspendPriority {
        int ordinal();

        SuspendPriority FIRST = of(org.jboss.as.server.suspend.SuspendPriority.FIRST.ordinal());
        SuspendPriority DEFAULT = of(org.jboss.as.server.suspend.SuspendPriority.DEFAULT.ordinal());
        SuspendPriority LAST = of(org.jboss.as.server.suspend.SuspendPriority.LAST.ordinal());

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
     * If this activity was already added to this registry, it will retain its original priority.
     * If registered while the server is suspended the activity itself will perform a blocking "auto-suspend", by waiting for its {@link SuspendableActivity#suspend(ServerSuspendContext)} to complete.
     * @param activity suspendable activity that should complete prior to suspending/resuming the server
     */
    default void registerActivity(SuspendableActivity activity) {
        this.registerActivity(activity, SuspendPriority.DEFAULT);
    }

    /**
     * Registers the specified {@link SuspendableActivity} to this registry using the specified priority.
     * If this activity was already added to this registry, it will retain its original priority.
     * If registered while the server is suspended the activity itself will perform a blocking "auto-suspend", by waiting for its {@link SuspendableActivity#suspend(ServerSuspendContext)} to complete.
     * @param activity suspendable activity that should complete prior to suspending/resuming the server
     * @param priority the priority group with which this activity should be registered
     */
    default void registerActivity(SuspendableActivity activity, SuspendPriority priority) {
        int ordinal = priority.ordinal();
        if ((ordinal < org.jboss.as.server.suspend.SuspendPriority.FIRST.ordinal()) || (ordinal > org.jboss.as.server.suspend.SuspendPriority.LAST.ordinal())) {
            throw new IllegalArgumentException(String.valueOf(ordinal));
        }
        org.jboss.as.server.suspend.SuspendPriority suspendPriority = org.jboss.as.server.suspend.SuspendPriority.values()[ordinal];
        try {
            SuspendableActivityRegistration registration = this.register(activity, suspendPriority);
            if (registration.getState() != State.RUNNING) {
                // if the activity is added when we are not running we just immediately suspend it
                // this should only happen at boot, so there should be no outstanding requests anyway
                // note that this means there is no execution group grouping of these calls.
                activity.suspend(Context.STARTUP).toCompletableFuture().join();
            }
        } catch (IllegalArgumentException e) {
            // If activity was already registered, swallow exception per existing API contract
        }
    }

    /**
     * Unregisters the specified {@link SuspendableActivity} from this registry, if it was previously added.
     * @param activity suspendable activity previously registered via {@link #registerActivity(SuspendableActivity)} or {@link #registerActivity(SuspendableActivity, SuspendPriority)}.
     */
    void unregisterActivity(SuspendableActivity activity);
}
