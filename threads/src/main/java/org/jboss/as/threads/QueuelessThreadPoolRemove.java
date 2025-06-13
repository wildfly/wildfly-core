/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Removes a queueless thread pool.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class QueuelessThreadPoolRemove extends AbstractRemoveStepHandler {

    private final QueuelessThreadPoolAdd addHandler;

    public QueuelessThreadPoolRemove(QueuelessThreadPoolAdd addHandler) {
        this.addHandler = addHandler;
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final ThreadPoolManagementUtils.EnhancedQueueThreadPoolParameters params =
                ThreadPoolManagementUtils.parseQueuelessThreadPoolParameters(context, operation, model, addHandler.isBlocking());
        ThreadPoolManagementUtils.removeThreadPoolService(params.getName(), addHandler.getServiceNameBase(),
                params.getThreadFactory(), addHandler.getThreadFactoryResolver(),
                params.getHandoffExecutor(), addHandler.getHandoffExecutorResolver(),
                context);
    }

    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        addHandler.performRuntime(context, operation, model);
    }
}
