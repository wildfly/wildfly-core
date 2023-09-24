/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.domain.management.access.RbacSanityCheckOperation;
import org.jboss.dmr.ModelNode;

/**
 * The remove handler for the Native Remoting Interface when running a standalone server.
 * (This reuses a connector from the remoting subsystem).
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NativeRemotingManagementRemoveHandler extends ReloadRequiredRemoveStepHandler {

    public static final NativeRemotingManagementRemoveHandler INSTANCE = new NativeRemotingManagementRemoveHandler();

    private NativeRemotingManagementRemoveHandler() {
    }

    @Override
    protected void performRemove(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {
        RbacSanityCheckOperation.addOperation(context);
        super.performRemove(context, operation, model);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return context.getProcessType() != ProcessType.EMBEDDED_SERVER || context.getRunningMode() != RunningMode.ADMIN_ONLY;
    }
}
