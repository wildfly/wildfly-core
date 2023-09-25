/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.dmr.ModelNode;
/**
 * Reports the current server {@link org.jboss.as.controller.RunningMode}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class RunningModeReadHandler implements OperationStepHandler {


    private final RunningModeControl runningModeControl;

    public RunningModeReadHandler(RunningModeControl runningModeControl) {
        this.runningModeControl = runningModeControl;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.getResult().set(runningModeControl.getRunningMode().name());
    }


}
