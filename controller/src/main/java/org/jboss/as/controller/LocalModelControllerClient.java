/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * A {@link ModelControllerClient} subinterface that does not throw {@link java.io.IOException}.
 * Used for clients that operate in the same VM as the target {@link ModelController} and hence
 * are not subject to IO failures associated with remote calls.
 *
 * @author Brian Stansberry
 */
public interface LocalModelControllerClient extends ModelControllerClient {

    @Override
    default ModelNode execute(ModelNode operation) {
        return execute(Operation.Factory.create(operation), OperationMessageHandler.DISCARD);
    }

    @Override
    default ModelNode execute(Operation operation) {
        return execute(operation, OperationMessageHandler.DISCARD);
    }

    @Override
    default ModelNode execute(ModelNode operation, OperationMessageHandler messageHandler) {
        return execute(Operation.Factory.create(operation), messageHandler);
    }

    @Override
    default ModelNode execute(Operation operation, OperationMessageHandler messageHandler) {
        OperationResponse or = executeOperation(operation, messageHandler);
        ModelNode result = or.getResponseNode();
        try {
            or.close();
        } catch (IOException e) {
            ControllerLogger.MGMT_OP_LOGGER.debugf(e, "Failed closing response to %s", operation);
        }
        return result;
    }

    @Override
    OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler);

    @Override
    void close();
}
