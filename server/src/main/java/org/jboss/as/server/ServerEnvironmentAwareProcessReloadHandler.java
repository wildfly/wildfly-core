/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.operations.common.ProcessReloadHandler;
import org.jboss.as.version.Stability;
import org.jboss.msc.service.ServiceName;

public abstract class ServerEnvironmentAwareProcessReloadHandler extends ProcessReloadHandler<RunningModeControl> {
    protected final ServerEnvironment environment;

    public ServerEnvironmentAwareProcessReloadHandler(ServiceName rootService, RunningModeControl runningModeControl, ControlledProcessState processState, ServerEnvironment environment) {
        super(rootService, runningModeControl, processState);
        this.environment = environment;
    }

    protected void updateServerEnvironmentStability(Stability stability) {
        environment.setStability(stability);
    }
}
