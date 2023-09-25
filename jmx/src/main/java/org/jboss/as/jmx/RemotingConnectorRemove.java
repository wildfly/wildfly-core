/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * @author Stuart Douglas
 */
class RemotingConnectorRemove extends AbstractRemoveStepHandler {

    static final RemotingConnectorRemove INSTANCE = new RemotingConnectorRemove();

    private RemotingConnectorRemove() {
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        context.removeService(RemotingConnectorService.SERVICE_NAME);
    }

    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        RemotingConnectorAdd.INSTANCE.performRuntime(context, operation, model);
    }
}
