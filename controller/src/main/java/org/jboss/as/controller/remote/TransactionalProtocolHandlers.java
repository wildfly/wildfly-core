/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.remote;

import java.io.IOException;

import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.dmr.ModelNode;

/**
 * Factory to create a {@link TransactionalProtocolClient}.
 *
 * @author Emanuel Muckenhuber
 */
public final class TransactionalProtocolHandlers {

    private TransactionalProtocolHandlers() {
        //
    }

    /**
     * Create a transactional protocol client.
     *
     * @param channelAssociation the channel handler
     * @return the transactional protocol client
     */
    public static TransactionalProtocolClient createClient(final ManagementChannelHandler channelAssociation) {
        final TransactionalProtocolClientImpl client = new TransactionalProtocolClientImpl(channelAssociation);
        channelAssociation.addHandlerFactory(client);
        return client;
    }

    /**
     * Wrap an operation's parameters in a simple encapsulating object
     * @param operation  the operation
     * @param messageHandler the message handler
     * @param attachments  the attachments
     * @return  the encapsulating object
     */
    public static TransactionalProtocolClient.Operation wrap(final ModelNode operation, final OperationMessageHandler messageHandler, final OperationAttachments attachments) {
        return new TransactionalOperationImpl(operation, messageHandler, attachments);
    }

    /**
     * Execute blocking for a prepared result.
     *
     * @param operation the operation to execute
     * @param client the protocol client
     * @return the prepared operation
     * @throws IOException
     * @throws InterruptedException
     */
    public static TransactionalProtocolClient.PreparedOperation<TransactionalProtocolClient.Operation> executeBlocking(final ModelNode operation, TransactionalProtocolClient client) throws IOException, InterruptedException {
        final BlockingQueueOperationListener<TransactionalProtocolClient.Operation> listener = new BlockingQueueOperationListener<>();
        client.execute(listener, operation, OperationMessageHandler.DISCARD, OperationAttachments.EMPTY);
        return listener.retrievePreparedOperation();
    }

}
