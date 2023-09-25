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
 * Removes an unbounded queue thread pool.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class UnboundedQueueThreadPoolRemove extends AbstractRemoveStepHandler {

    private final UnboundedQueueThreadPoolAdd addHandler;

    public UnboundedQueueThreadPoolRemove(UnboundedQueueThreadPoolAdd addHandler) {
        this.addHandler = addHandler;
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final ThreadPoolManagementUtils.BaseThreadPoolParameters params =
                ThreadPoolManagementUtils.parseUnboundedQueueThreadPoolParameters(context, operation, model);
        ThreadPoolManagementUtils.removeThreadPoolService(params.getName(),  addHandler.getCapability(), addHandler.getServiceNameBase(),
                params.getThreadFactory(), addHandler.getThreadFactoryResolver(),
                context);
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        addHandler.performRuntime(context, operation, model);
    }
}
