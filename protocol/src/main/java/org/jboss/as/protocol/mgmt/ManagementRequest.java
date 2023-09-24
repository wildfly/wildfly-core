/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import java.io.IOException;

/**
 * A management request.
 *
 * @author John Bailey
 * @author Kabir Khan
 * @author Emanuel Muckenhuber
 */
public interface ManagementRequest<T, A> extends ManagementResponseHandler<T, A> {

    /**
     * The operation type.
     *
     * @return the operation type
     */
    byte getOperationType();

    /**
     * Send the request.
     *
     * @param resultHandler the result handler
     * @param context the request context
     * @throws IOException for any error
     */
    void sendRequest(ActiveOperation.ResultHandler<T> resultHandler, ManagementRequestContext<A> context) throws IOException;

}
