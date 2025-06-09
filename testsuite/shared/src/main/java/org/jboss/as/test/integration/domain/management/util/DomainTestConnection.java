/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.management.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.ProtocolConnectionUtils;
import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * Connection utility allowing the {@linkplain DomainLifecycleUtil} to share a remoting connection between potentially
 * multiple controller clients, each using it's own channel.
 *
 * @author Emanuel Muckenhuber
 */
class DomainTestConnection implements Closeable {

    private static final String DEFAULT_CHANNEL_SERVICE_TYPE = "management";

    private final ProtocolConnectionConfiguration clientConfiguration;
    private final CallbackHandler callbackHandler;
    private final ExecutorService executorService;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    volatile Connection connection;

    DomainTestConnection(final ProtocolConnectionConfiguration clientConfiguration, final CallbackHandler callbackHandler, final ExecutorService executorService) {
        this.clientConfiguration = clientConfiguration;
        this.callbackHandler = callbackHandler;
        this.executorService = executorService;
    }

    /**
     * Create a controller client instance.
     *
     * @return the controller client
     */
    protected DomainTestClient createClient() {
        return createClient(executorService);
    }

    /**
     * Create a controller client instance.
     *
     * @param executorService the executor service
     * @return the controller client
     */
    protected DomainTestClient createClient(final ExecutorService executorService) {
        final ChannelStrategy strategy = new ChannelStrategy(executorService);
        final ManagementChannelHandler handler = strategy.handler;
        final DomainTestClient client = new DomainTestClient() {
            @Override
            Connection getConnection() {
                return connection;
            }

            @Override
            protected ManagementChannelAssociation getChannelAssociation() {
                return handler;
            }

            @Override
            Channel getChannel() {
                return strategy.channel;
            }

            @Override
            public void close() throws IOException {
                strategy.close();
            }
        };
        handler.addHandlerFactory(client);
        return client;
    }

    /**
     * Try to connect to the remote controller.
     *
     * @return the underlying remoting connection
     * @throws IOException
     */
    protected Connection connect() throws IOException {
        return connect(callbackHandler);
    }

    /**
     * Try to connect to the remote host controller.
     *
     * @param callbackHandler the security callback handler
     * @return the underlying remoting connection
     * @throws IOException
     */
    protected Connection connect(final CallbackHandler callbackHandler) throws IOException {
        if(closed.get()) {
            throw new IllegalStateException();
        }
        synchronized (this) {
            if(isConnected()) {
                return connection;
            }
            connection = ProtocolConnectionUtils.connectSync(clientConfiguration, callbackHandler, Collections.emptyMap(), null);
            connection.addCloseHandler(new CloseHandler<Connection>() {
                @Override
                public void handleClose(Connection old, IOException exception) {
                    synchronized (DomainTestConnection.this) {
                        if(connection == old) {
                            connection = null;
                        }
                        DomainTestConnection.this.notifyAll();
                    }
                }
            });
            return connection;
        }
    }

    /**
     * Check if we are currently connected.
     *
     * @return {@code true} if we are connected
     */
    protected boolean isConnected() {
        return connection != null;
    }

    /**
     * Disconnect from the remote controller, allowing reconnection.
     *
     * @throws IOException
     */
    protected void disconnect() throws IOException {
        final Connection connection = this.connection;
        if(connection != null) {
            connection.close();
        }
    }

    /**
     * Await the connection the be closed.
     *
     * @param ref the referenced connection
     * @throws InterruptedException
     */
    protected void awaitConnectionClosed(final Connection ref) throws InterruptedException {
        synchronized (this) {
            for(;;) {
                final Connection connection = this.connection;
                if(connection == null) {
                    return;
                } else if (connection != ref) {
                    return;
                }
                wait();
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if(closed.compareAndSet(false, true)) {
                final Connection connection = this.connection;
                if(connection != null) {
                    connection.close();
                }
            }
        }
    }

    class ChannelStrategy extends ManagementClientChannelStrategy {

        volatile Channel channel;

        private final ManagementChannelHandler handler;
        ChannelStrategy(ExecutorService executorService) {
            this.handler = new ManagementChannelHandler(this, executorService);
        }

        @Override
        public Channel getChannel() throws IOException {
            if(channel == null) {
                synchronized (this) {
                    if(channel == null) {
                        final Connection connection = connect();
                        channel = openChannel(connection);
                        channel.receiveMessage(handler.getReceiver());
                    }
                }
            }
            return channel;
        }

        Channel openChannel(final Connection connection) throws IOException {
            final IoFuture<Channel> future = connection.openChannel(DEFAULT_CHANNEL_SERVICE_TYPE, OptionMap.EMPTY);
            future.await(10L, TimeUnit.SECONDS);
            if (future.getStatus() == IoFuture.Status.WAITING) {
                future.cancel();
                throw ProtocolLogger.ROOT_LOGGER.channelTimedOut();
            }
            final Channel channel = future.get();
            channel.addCloseHandler(new CloseHandler<Channel>() {
                @Override
                public void handleClose(final Channel old, final IOException e) {
                    synchronized (ChannelStrategy.this) {
                        if(ChannelStrategy.this.channel == old) {
                            ChannelStrategy.this.handler.handleClose(old, e);
                            ChannelStrategy.this.channel = null;
                        }
                    }
                    handler.handleChannelClosed(old, e);
                }
            });
            return channel;
        }

        @Override
        public void close() throws IOException {
            final Channel channel = this.channel;
            if(channel != null) {
                channel.close();
            }
        }
    }

}
