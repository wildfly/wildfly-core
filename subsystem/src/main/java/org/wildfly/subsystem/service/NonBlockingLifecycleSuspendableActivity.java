/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.service;

import java.util.concurrent.CompletionStage;

import org.jboss.as.server.suspend.ServerResumeContext;
import org.jboss.as.server.suspend.ServerSuspendContext;
import org.jboss.as.server.suspend.SuspendableActivity;
import org.wildfly.service.NonBlockingLifecycle;

/**
 * Generic suspendable activity that uses a {@link NonBlockingLifecycle} to auto-start on resume and auto-stop on suspend.
 * @author Paul Ferraro
 */
public class NonBlockingLifecycleSuspendableActivity implements SuspendableActivity {
    private final NonBlockingLifecycle lifecycle;

    public NonBlockingLifecycleSuspendableActivity(NonBlockingLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public CompletionStage<Void> suspend(ServerSuspendContext context) {
        return this.lifecycle.isStarted() ? this.lifecycle.stop() : SuspendableActivity.COMPLETED;
    }

    @Override
    public CompletionStage<Void> resume(ServerResumeContext context) {
        return this.lifecycle.isStopped() ? this.lifecycle.start() : SuspendableActivity.COMPLETED;
    }
}
