/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.impl;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class ExistingChannelModelControllerClient extends AbstractModelControllerClient {

    private final ManagementChannelHandler handler;
    protected ExistingChannelModelControllerClient(final ManagementChannelHandler handler) {
        this.handler = handler;
    }

    @Override
    protected ManagementChannelAssociation getChannelAssociation() throws IOException {
        return handler;
    }

    @Override
    public void close() throws IOException {
        handler.shutdown();
    }

    /**
     * Create and add model controller handler to an existing management channel handler.
     *
     * @param handler the channel handler
     * @return the created client
     */
    public static ModelControllerClient createAndAdd(final ManagementChannelHandler handler) {
        final ExistingChannelModelControllerClient client = new ExistingChannelModelControllerClient(handler);
        handler.addHandlerFactory(client);
        return client;
    }

    /**
     * Create a model controller client which is exclusively receiving messages on an existing channel.
     *
     * @param channel the channel
     * @param executorService an executor
     * @return the created client
     */
    public static ModelControllerClient createReceiving(final Channel channel, final ExecutorService executorService) {
        final ManagementClientChannelStrategy strategy = ManagementClientChannelStrategy.create(channel);
        final ManagementChannelHandler handler = new ManagementChannelHandler(strategy, executorService);
        final ExistingChannelModelControllerClient client = new ExistingChannelModelControllerClient(handler);
        handler.addHandlerFactory(client);
        channel.addCloseHandler(new CloseHandler<Channel>() {
            @Override
            public void handleClose(Channel closed, IOException exception) {
                handler.shutdown();
                try {
                    handler.awaitCompletion(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    handler.shutdownNow();
                }
            }
        });
        channel.receiveMessage(handler.getReceiver());
        return client;
    }

}
