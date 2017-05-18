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
