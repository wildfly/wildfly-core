/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.dmr.ModelNode;

/**
 * Reports the server launch type
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class LaunchTypeHandler implements OperationStepHandler {

    private final ServerEnvironment.LaunchType launchType;

    public LaunchTypeHandler(final ServerEnvironment.LaunchType launchType) {
        this.launchType = launchType;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.getResult().set(launchType.toString());
    }
}
