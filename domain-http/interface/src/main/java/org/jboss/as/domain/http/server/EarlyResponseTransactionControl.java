/*
Copyright 2018 Red Hat, Inc.

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

package org.jboss.as.domain.http.server;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.remote.EarlyResponseSendListener;
import org.jboss.dmr.ModelNode;

/**
 * {@link org.jboss.as.controller.ModelController.OperationTransactionControl} that sends an
 * early response (i.e. at operation prepared phase rather than operation completion) to the client.
 *
 * @author Brian Stansberry
 */
final class EarlyResponseTransactionControl implements ModelController.OperationTransactionControl {
    private final ResponseCallback callback;
    private final boolean reload;

    EarlyResponseTransactionControl(ResponseCallback callback, ModelNode operation) {
        this.callback = callback;
        this.reload = RELOAD.equals(operation.get(OP).asString());
    }

    @Override
    public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode preparedResult) {
        // Shouldn't be called but if it is, send the result immediately
        operationPrepared(transaction, preparedResult, null);
    }

    @Override
    public void operationPrepared(ModelController.OperationTransaction transaction, final ModelNode preparedResult, OperationContext context) {
        transaction.commit();
        if (context == null || !reload) { // TODO deal with shutdown as well, the handlers for which have some
                                          // subtleties that need thought
            sendResponse(preparedResult);
        } else {
            context.attach(EarlyResponseSendListener.ATTACHMENT_KEY, new EarlyResponseSendListener() {
                @Override
                public void sendEarlyResponse(OperationContext.ResultAction resultAction) {
                    sendResponse(preparedResult);
                }
            });
        }
    }

    private void sendResponse(ModelNode preparedResult) {
        // Fix prepared result
        preparedResult.get(OUTCOME).set(SUCCESS);
        preparedResult.get(RESULT);
        callback.sendResponse(OperationResponse.Factory.createSimple(preparedResult));
    }
}
