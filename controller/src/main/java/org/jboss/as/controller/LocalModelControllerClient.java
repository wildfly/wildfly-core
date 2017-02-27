/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
