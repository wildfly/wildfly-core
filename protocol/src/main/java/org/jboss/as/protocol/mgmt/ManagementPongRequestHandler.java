/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import java.io.DataInput;
import java.io.IOException;

import org.jboss.as.protocol.StreamUtils;

/**
 * {@link ManagementRequestHandlerFactory} for dealing with a {@link ManagementPingRequest}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ManagementPongRequestHandler implements ManagementRequestHandlerFactory, ManagementRequestHandler<Void, Void> {

    private volatile long connectionId = System.currentTimeMillis();

    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(RequestHandlerChain handlers, ManagementRequestHeader header) {
        final byte operationId = header.getOperationId();
        switch (operationId) {
            case ManagementProtocol.TYPE_PING:
                handlers.registerActiveOperation(header.getBatchId(), null);
                return this;
        }
        return handlers.resolveNext();
    }

    @Override
    public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler,
                              final ManagementRequestContext<Void> context) throws IOException {

        final ManagementResponseHeader response = ManagementResponseHeader.create(context.getRequestHeader());

        final FlushableDataOutput output = context.writeMessage(response);
        try {
            output.write(ManagementProtocol.TYPE_PONG);
            output.writeLong(connectionId);
            output.writeByte(ManagementProtocol.RESPONSE_END);
            output.close();
        } finally {
            StreamUtils.safeClose(output);
        }
        resultHandler.done(null);
    }

    /**
     * Update the id we send in pong responses
     */
    public void resetConnectionId() {
        connectionId = System.currentTimeMillis();
    }

    /**
     * Gets the current id we send in pong responses
     * @return
     */
    public long getConnectionId() {
        return connectionId;
    }
}
