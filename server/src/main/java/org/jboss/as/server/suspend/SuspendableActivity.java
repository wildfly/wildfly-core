/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Provides stages that must complete prior to suspending or resuming the server.
 */
public interface SuspendableActivity {
    CompletionStage<Void> COMPLETED = CompletableFuture.completedStage(null);

    /**
     * Returns a prepare stage to complete prior to server suspension.
     * Default implementation returns a completed stage.
     * @param context the server suspend context
     * @return a prepare stage to complete prior to server suspension.
     */
    default CompletionStage<Void> prepare(ServerSuspendContext context) {
        return COMPLETED;
    }

    /**
     * Returns a suspend stage to complete upon server suspension.
     * @param context the server suspend context
     * @return a suspend stage to complete upon server suspension.
     */
    CompletionStage<Void> suspend(ServerSuspendContext context);

    /**
     * Returns a resume stage to complete upon resuming a suspended server.
     * @param context the server resume context
     * @return a resume stage to complete upon resuming a suspended server.
     */
    CompletionStage<Void> resume(ServerResumeContext context);
}
