/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.test;

import java.io.IOException;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ProxyOperationAddressTranslator;
import org.jboss.as.controller.remote.RemoteProxyController;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.controller.remote.TransactionalProtocolOperationHandler;
import org.jboss.as.controller.support.RemoteChannelPairSetup;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.junit.After;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class RemoteChannelProxyControllerTestCase extends AbstractProxyControllerTest {

    RemoteChannelPairSetup channels;

    @After
    public void stopChannels() throws Exception {
        channels.stopChannels();
        channels.shutdownRemoting();
        channels = null;
    }

    @Override
    protected ProxyController createProxyController(final ModelController proxiedController, final PathAddress proxyNodeAddress) {
        try {
            channels = new RemoteChannelPairSetup();
            channels.setupRemoting(new ManagementChannelInitialization() {
                @Override
                public ManagementChannelHandler startReceiving(Channel channel) {
                    final ManagementClientChannelStrategy strategy = ManagementClientChannelStrategy.create(channel);
                    final ManagementChannelHandler support = new ManagementChannelHandler(strategy, channels.getExecutorService());
                    support.addHandlerFactory(new TransactionalProtocolOperationHandler(proxiedController, support, new ResponseAttachmentInputStreamSupport()));
                    channel.addCloseHandler(new CloseHandler<Channel>() {
                        @Override
                        public void handleClose(Channel closed, IOException exception) {
                            support.shutdownNow();
                        }
                    });
                    channel.receiveMessage(support.getReceiver());
                    return support;
                }
            });
            channels.startClientConnetion();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final Channel clientChannel = channels.getClientChannel();
        final ManagementClientChannelStrategy strategy = ManagementClientChannelStrategy.create(clientChannel);
        final ManagementChannelHandler support = new ManagementChannelHandler(strategy, channels.getExecutorService());
        final RemoteProxyController proxyController = RemoteProxyController.create(support, proxyNodeAddress, ProxyOperationAddressTranslator.SERVER);
        clientChannel.addCloseHandler(new CloseHandler<Channel>() {
            @Override
            public void handleClose(Channel closed, IOException exception) {
                support.shutdownNow();
            }
        });
        clientChannel.receiveMessage(support.getReceiver());
        return proxyController;
    }
}
