/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management.client;

import org.wildfly.extension.core.management.client.Process.RunningState;

/**
 * Event sent when the running state changes.
 * Transitions are : starting -> normal | admin-only (-> suspending -> suspended) -> stopping.
 * Those states are <strong>NOT</strong> available on a HostController.
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class RunningStateChangeEvent {

    private final RunningState oldState;
    private final RunningState newState;

    public RunningStateChangeEvent(RunningState oldState, RunningState newState) {
        this.oldState = oldState;
        this.newState = newState;
    }

    public RunningState getOldState() {
        return oldState;
    }

    public RunningState getNewState() {
        return newState;
    }

    @Override
    public String toString() {
        return "RunningStateChangeEvent{" + "oldState=" + oldState + ", newState=" + newState + '}';
    }

}
