/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import java.util.concurrent.CompletionStage;

import org.wildfly.service.descriptor.NullaryServiceDescriptor;

public interface ServerSuspendController extends SuspensionStateProvider {
    NullaryServiceDescriptor<ServerSuspendController> SERVICE_DESCRIPTOR = SuspensionStateProvider.SERVICE_DESCRIPTOR.asType(ServerSuspendController.class);

    enum Context implements ServerSuspendContext {
        STARTUP(true, false),
        RUNNING(false, false),
        SHUTDOWN(false, true),
        ;
        private final boolean starting;
        private final boolean stopping;

        Context(boolean starting, boolean stopping) {
            this.starting = starting;
            this.stopping = stopping;
        }

        @Override
        public boolean isStarting() {
            return this.starting;
        }

        @Override
        public boolean isStopping() {
            return this.stopping;
        }
    }

    /**
     * Suspends all registered activity and Transitions the controller from {@link State#RUNNING} to {@link State#SUSPENDED} via the intermediate states: {@link State#PRE_SUSPEND} and {@link State#SUSPENDING}
     * @return a completion stage that completes after {@link SuspendableActivity#prepare(ServerSuspendContext)} completion, followed by {@link SuspendableActivity#suspend(ServerSuspendContext)} completion for all registered activity.
     */
    CompletionStage<Void> suspend(ServerSuspendContext context);

    /**
     * Resumes all registered activity and transitions the controller from {@link State#SUSPENDED} to {@link State#RUNNING}.
     * @return a stage that will complete after {@link SuspendableActivity#cancel(ServerSuspendCancelContext)} completion for all registered activity.
     */
    CompletionStage<Void> resume(ServerResumeContext context);

    /**
     * Resets the state of the suspend controller to {@link State#SUSPENDED}.
     */
    void reset();

    /**
     * Adds the specified server suspension event listener to this controller.
     * @param listener a server suspension event listener
     */
    void addListener(OperationListener listener);

    /**
     * Removes the specified server suspension event listener from this controller.
     * @param listener a server suspension event listener
     */
    void removeListener(OperationListener listener);
}
