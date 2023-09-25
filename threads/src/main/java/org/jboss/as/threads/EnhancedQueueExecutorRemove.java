/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.threads.ThreadPoolManagementUtils.EnhancedQueueThreadPoolParameters;
import org.jboss.dmr.ModelNode;

/**
 * Removes an {@code org.jboss.threads.EnhancedQueueExecutor}.
 */
class EnhancedQueueExecutorRemove extends AbstractRemoveStepHandler {

    private final EnhancedQueueExecutorAdd addHandler;

    EnhancedQueueExecutorRemove(EnhancedQueueExecutorAdd addHandler) {
        this.addHandler = addHandler;
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final EnhancedQueueThreadPoolParameters params =
                ThreadPoolManagementUtils.parseEnhancedQueueThreadPoolParameters(context, operation, model);
        ThreadPoolManagementUtils.removeThreadPoolService(params.getName(), addHandler.getCapability(), addHandler.getServiceNameBase(),
                params.getThreadFactory(), addHandler.getThreadFactoryResolver(),
                context);
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        addHandler.performRuntime(context, operation, model);
    }
}
