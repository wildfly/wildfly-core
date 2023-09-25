/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Reports the process type
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ProcessTypeHandler implements OperationStepHandler {

    public static final ProcessTypeHandler INSTANCE = new ProcessTypeHandler();

    private ProcessTypeHandler() {
        // singleton
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.getResult().set("Server");
    }
}
