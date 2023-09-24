/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.protocol.mgmt;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.ProtocolConnectionManager;
import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.xnio.OptionMap;

/**
 * Strategy management clients can use for controlling the lifecycle of the channel.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public abstract class ManagementClientChannelStrategy implements Closeable {

    /** The remoting channel service type. */
    private static final String DEFAULT_CHANNEL_SERVICE_TYPE = "management";

    /**
     * Get the channel.
     *
     * @return the channel
     * @throws IOException if an IO problem occurs getting the channel
     */
    public abstract Channel getChannel() throws IOException;

    /**
     * Create a new client channel strategy.
     *
     * @param channel the existing channel
     * @return the management client channel strategy
     */
    public static ManagementClientChannelStrategy create(final Channel channel) {
        return new Existing(channel);
    }

    /**
     * Create a new establishing management client channel-strategy
     *
     * @param baseConfig the base connection configuration
     * @param handler the {@code ManagementMessageHandler}
     * @param cbHandler a callback handler
     * @param saslOptions the sasl options
     * @param sslContext the ssl context
     * @param closeHandler a close handler
     * @return the management client channel strategy
     */
    public static ManagementClientChannelStrategy create(final ProtocolConnectionConfiguration baseConfig,
                                                         final ManagementMessageHandler handler,
                                                         final CallbackHandler cbHandler,
                                                         final Map<String, String> saslOptions,
                                                         final SSLContext sslContext,
                                                         final CloseHandler<Channel> closeHandler) {
        return create(createConfiguration(baseConfig, saslOptions, cbHandler, sslContext), ManagementChannelReceiver.createDelegating(handler), closeHandler);
    }

    /**
     * Create a new establishing management client channel-strategy
     *
     * @param configuration the connection configuration
     * @param receiver the channel receiver
     * @param closeHandler the close handler
     * @return the management client channel strategy
     */
    public static ManagementClientChannelStrategy create(final ProtocolConnectionConfiguration configuration, final Channel.Receiver receiver, final CloseHandler<Channel> closeHandler) {
        return new Establishing(configuration, receiver, closeHandler);
    }

    /**
     * The existing channel strategy.
     */
    private static class Existing extends ManagementClientChannelStrategy {
        // The underlying channel
        private final Channel channel;
        private volatile boolean closed = false;
        private Existing(final Channel channel) {
            this.channel = channel;
        }

        @Override
        public Channel getChannel() throws IOException {
            if(closed) {
                throw ProtocolLogger.ROOT_LOGGER.channelClosed();
            }
            return channel;
        }

        @Override
        public void close() {
            this.closed = true;
            // closing the channel is not our responsibility
        }
    }

    private static ProtocolConnectionConfiguration createConfiguration(final ProtocolConnectionConfiguration configuration,
                                                                       final Map<String, String> saslOptions, final CallbackHandler callbackHandler,
                                                                       final SSLContext sslContext) {
        final ProtocolConnectionConfiguration config = ProtocolConnectionConfiguration.copy(configuration);
        config.setCallbackHandler(callbackHandler);
        config.setSslContext(sslContext);
        config.setSaslOptions(saslOptions);
        return config;
    }

    /**
     * When getting the underlying channel this strategy is trying to automatically (re-)connect
     * when either the connection or channel was closed.
     */
    private static class Establishing extends FutureManagementChannel {

        private final String serviceType = DEFAULT_CHANNEL_SERVICE_TYPE;
        private final OptionMap channelOptions;
        private final Channel.Receiver receiver;
        private final ProtocolConnectionManager connectionManager;
        private final CloseHandler<Channel> closeHandler;
        private final long timeout;
        private Long deadline;

        private Establishing(final ProtocolConnectionConfiguration configuration, final Channel.Receiver receiver, final CloseHandler<Channel> closeHandler) {
            this.receiver = receiver;
            this.channelOptions = configuration.getOptionMap();
            this.connectionManager = ProtocolConnectionManager.create(configuration, this);
            this.closeHandler = closeHandler;
            this.timeout = configuration.getConnectionTimeout();
        }

        @Override
        public Channel getChannel() throws IOException {
            Channel channel = super.getChannel();
            if(channel != null) {
                return channel;
            }
            // Try to connect and wait for the channel
            synchronized (connectionManager) {
                deadline = System.currentTimeMillis() + timeout; // read in openChannel below
                connectionManager.connect();
                deadline = null;
            }
            // In case connect did not succeed the next getChannel() call needs to try to reconnect
            channel = super.getChannel();
            if(channel == null) {
                throw ProtocolLogger.ROOT_LOGGER.channelClosed();
            }
            return channel;
        }

        @Override
        public void connectionOpened(final Connection connection) throws IOException {
            final Channel channel = openChannel(connection, serviceType, channelOptions);
            if(setChannel(channel)) {
                channel.receiveMessage(receiver);
            } else {
                channel.closeAsync();
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connectionManager.shutdown();
            }
        }

        @Override
        protected Channel openChannel(final Connection connection, final String serviceType, final OptionMap options) throws IOException {
            // This is only called as part of the connectionManager.connect() handling in getChannel() above.
            // So, we should hold the connectionManager lock. We want to ensure that so we know we have the right deadline.
            // We could synchronize again on connectionManager to ensure that, but then if there is some corner case
            // the analysis missed where this gets called asynchronously during connectionManager.connect() handling
            // we would deadlock. Better to fail than to deadlock. Use an ISE instead of an assert because if this
            // fails, we don't want an Error; we want something that should eventually be caught and handled.
            if (!Thread.holdsLock(connectionManager)) {
                throw new IllegalStateException();
            }
            final Channel channel = openChannel(connection, serviceType, options, deadline);
            channel.addCloseHandler(closeHandler);
            return channel;
        }

    }

}
