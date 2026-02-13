/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.service;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.server.suspend.ServerSuspendController;
import org.jboss.as.server.suspend.SuspendPriority;
import org.jboss.as.server.suspend.SuspendableActivity;
import org.jboss.as.server.suspend.SuspendableActivityRegistrar;
import org.jboss.as.server.suspend.SuspendableActivityRegistration;
import org.wildfly.service.NonBlockingLifecycle;

/**
 * A non-blocking lifecycle decorator that auto-registers {@link SuspendableActivity} to auto-stop on suspend and auto-start on resume.
 * @author Paul Ferraro
 */
public class SuspendableNonBlockingLifecycle implements NonBlockingLifecycle {
    public static <T> Function<T, NonBlockingLifecycle> compose(Function<? super T, ? extends NonBlockingLifecycle> lifecycleProvider, Supplier<SuspendableActivityRegistrar> registrar, Supplier<SuspendPriority> priority) {
        return new Function<>() {
            @Override
            public NonBlockingLifecycle apply(T value) {
                return new SuspendableNonBlockingLifecycle(lifecycleProvider.apply(value), registrar.get(), priority.get());
            }
        };
    }

    private final NonBlockingLifecycle lifecycle;
    private final SuspendableActivityRegistrar registrar;
    private final SuspendPriority priority;
    private final AtomicReference<SuspendableActivityRegistration> registration = new AtomicReference<>();

    public SuspendableNonBlockingLifecycle(NonBlockingLifecycle lifecycle, SuspendableActivityRegistrar registrar, SuspendPriority priority) {
        this.lifecycle = lifecycle;
        this.registrar = registrar;
        this.priority = priority;
    }

    @Override
    public boolean isStarted() {
        return this.lifecycle.isStarted();
    }

    @Override
    public boolean isStopped() {
        return this.lifecycle.isStopped();
    }

    @Override
    public CompletionStage<Void> start() {
        SuspendableActivityRegistration registration = this.registrar.register(new NonBlockingLifecycleSuspendableActivity(this.lifecycle), this.priority);
        // There should be no existing registration, but if so, close it
        Optional.ofNullable(this.registration.getAndSet(registration)).ifPresent(SuspendableActivityRegistration::close);
        // If server is suspended, defer service start until resume
        return (registration.getState() == ServerSuspendController.State.RUNNING) ? this.lifecycle.start() : SuspendableActivity.COMPLETED;
    }

    @Override
    public CompletionStage<Void> stop() {
        SuspendableActivityRegistration registration = this.registration.getAndSet(null);
        // If registration was null, we could never have started
        if (registration == null) return SuspendableActivity.COMPLETED;
        // If we are suspended, SuspendableActivity.suspend(...) will have already stopped the service
        CompletionStage<Void> stop = (registration.getState() == ServerSuspendController.State.RUNNING) ? this.lifecycle.stop() : SuspendableActivity.COMPLETED;
        return stop.whenComplete((ignore, exception) -> registration.close());
    }
}
