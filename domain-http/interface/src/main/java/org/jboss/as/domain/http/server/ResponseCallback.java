/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import org.jboss.as.controller.client.OperationResponse;

/**
 * Callback to prevent the response will be sent multiple times.
 */
abstract class ResponseCallback {
    private volatile boolean complete;

    void sendResponse(final OperationResponse response) {
        if (complete) {
            return;
        }
        complete = true;
        doSendResponse(response);
    }

    abstract void doSendResponse(OperationResponse response);
}
