/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import java.io.DataInput;
import java.io.IOException;

/**
 * A handler for incoming requests.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public interface ManagementRequestHandler<T, A> {

    /**
     * Handle a request.
     *
     * @param input the data input
     * @param resultHandler the result handler which may be used to mark the operation as complete
     * @param context the request context
     * @throws IOException
     */
    void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<T> resultHandler, final ManagementRequestContext<A> context) throws IOException;

}
