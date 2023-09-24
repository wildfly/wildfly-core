/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Operation removing a {@link DeploymentScannerService}.
 *
 * @author Emanuel Muckenhuber
 */
class DeploymentScannerRemove extends AbstractRemoveStepHandler {
    static final DeploymentScannerRemove INSTANCE = new DeploymentScannerRemove();

    private DeploymentScannerRemove() {
        //
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final String name = address.getLastElement().getValue();
        final ServiceName serviceName = DeploymentScannerService.getServiceName(name);
        final ServiceName pathServiceName = serviceName.append("path");

        context.removeService(serviceName);
        context.removeService(pathServiceName);
    }

    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) {
        try {
            DeploymentScannerAdd.performRuntime(context, operation, model, DeploymentScannerAdd.createScannerExecutorService(), null);
        } catch (OperationFailedException e) {
            // The operation context will handle it
            throw new RuntimeException(e);
        }
    }
}
