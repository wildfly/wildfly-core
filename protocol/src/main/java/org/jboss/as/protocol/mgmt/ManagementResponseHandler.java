/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

/**
 * A local handler for responses to a management request.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public interface ManagementResponseHandler<T, A> extends ManagementRequestHandler<T, A> {

    /**
     * Handle a failed response.
     *
     * @param header the header
     * @param resultHandler the result handler
     */
    void handleFailed(ManagementResponseHeader header, ActiveOperation.ResultHandler<T> resultHandler);

}
