/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management.client;

import org.wildfly.extension.core.management.client.Process.RuntimeConfigurationState;

/**
 * Event sent when the runtime configuration state changes.
 * Transitions are : starting -> ok -> (reload-required -> restart-required ->) stopping.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class RuntimeConfigurationStateChangeEvent {

    private final RuntimeConfigurationState newState;
    private final RuntimeConfigurationState oldState;

    public RuntimeConfigurationStateChangeEvent(RuntimeConfigurationState oldState, RuntimeConfigurationState newState) {
        this.newState = newState;
        this.oldState = oldState;
    }

    public RuntimeConfigurationState getNewState() {
        return newState;
    }

    public RuntimeConfigurationState getOldState() {
        return oldState;
    }

    @Override
    public String toString() {
        return "RuntimeConfigurationStateChangeEvent{" + "newState=" + newState + ", oldState=" + oldState + '}';
    }

}
