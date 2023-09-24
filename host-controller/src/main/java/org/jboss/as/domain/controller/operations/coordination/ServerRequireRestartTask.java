/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.server.operations.ServerProcessStateHandler.REQUIRE_RESTART_OPERATION;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Task setting the remote server in a 'restart-required' state.
 *
 * @author Emanuel Muckenhuber
 */
class ServerRequireRestartTask implements Callable<OperationResponse> {

    public static final String OPERATION_NAME = REQUIRE_RESTART_OPERATION;;
    public static final ModelNode OPERATION;

    static {
        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set(OPERATION_NAME);
        operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        operation.protect();
        OPERATION = operation;
    }

    private final ServerIdentity identity;
    private final ProxyController controller;
    private final OperationResponse originalResult;
    private final BlockingTimeout blockingTimeout;

    public ServerRequireRestartTask(final ServerIdentity identity, ProxyController controller, final OperationResponse originalResult, BlockingTimeout blockingTimeout) {
        this.identity = identity;
        this.controller = controller;
        this.originalResult = originalResult;
        this.blockingTimeout = blockingTimeout;
    }

    @Override
    public OperationResponse call() throws Exception {
        try {
            //
            final AtomicReference<ModelController.OperationTransaction> txRef = new AtomicReference<ModelController.OperationTransaction>();
            final ProxyController.ProxyOperationControl proxyControl = new ProxyController.ProxyOperationControl() {

                @Override
                public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode result) {
                    txRef.set(transaction);
                }

                @Override
                public void operationFailed(ModelNode response) {
                    DomainControllerLogger.HOST_CONTROLLER_LOGGER.debugf("server restart required operation failed: %s", response);
                }

                @Override
                public void operationCompleted(OperationResponse response) {
                    //
                }
            };
            // Execute
            final ModelNode operation = createOperation(identity);
            controller.execute(operation, OperationMessageHandler.DISCARD, proxyControl, OperationAttachments.EMPTY, blockingTimeout);
            final ModelController.OperationTransaction tx = txRef.get();
            if(tx != null) {
                // Commit right away
                tx.commit();
            } else {
                DomainControllerLogger.HOST_CONTROLLER_LOGGER.failedToSetServerInRestartRequireState(identity.getServerName());
            }
        } catch (Exception e) {
            DomainControllerLogger.HOST_CONTROLLER_LOGGER.debugf(e, "failed to send the server restart required operation");
        }
        return originalResult;
    }

    /**
     * Transform the operation into something the proxy controller understands.
     *
     * @param identity the server identity
     * @return the transformed operation
     */
    private static ModelNode createOperation(ServerIdentity identity) {
        // The server address
        final ModelNode address = new ModelNode();
        address.add(ModelDescriptionConstants.HOST, identity.getHostName());
        address.add(ModelDescriptionConstants.RUNNING_SERVER, identity.getServerName());
        //
        final ModelNode operation = OPERATION.clone();
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
        return operation;
    }

}
