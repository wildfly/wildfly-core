/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
