/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

/**
 * An implementation of {@code java.lang.Runnable} that wraps the original task
 * with control point operations, and exposes the original task.
 */
public final class ControlPointTask implements Runnable {
    private final Runnable originalTask;
    private final ControlPoint controlPoint;

    public ControlPointTask(final Runnable originalTask, final ControlPoint controlPoint) {
        this.originalTask = originalTask;
        this.controlPoint = controlPoint;
    }

    public Runnable getOriginalTask() {
        return originalTask;
    }

    @Override
    public void run() {
        try {
            controlPoint.beginExistingRequest();
            originalTask.run();
        } finally {
            controlPoint.requestComplete();
        }
    }
}
