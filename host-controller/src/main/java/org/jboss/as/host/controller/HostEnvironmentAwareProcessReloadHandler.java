/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.operations.common.ProcessReloadHandler;
import org.jboss.as.version.Stability;
import org.jboss.msc.service.ServiceName;

public abstract class HostEnvironmentAwareProcessReloadHandler extends ProcessReloadHandler<HostRunningModeControl> {
    protected final HostControllerEnvironment environment;

    public HostEnvironmentAwareProcessReloadHandler(ServiceName rootService, HostRunningModeControl runningModeControl, ControlledProcessState processState, HostControllerEnvironment environment) {
        super(rootService, runningModeControl, processState);
        this.environment = environment;
    }

    protected void updateHostEnvironmentStability(Stability stability) {
        environment.setStability(stability);
    }
}
