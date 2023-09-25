/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol;

import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.wildfly.common.Assert;

import java.io.IOException;

/**
 * A basic connection manager, notifying clients when the connection is closed or shutdown. The
 * {@code ProtocolConnectionManager.ConnectTask} can be used to implement different (re-)connection strategies.
 *
 * @author Emanuel Muckenhuber
 */
public final class ProtocolConnectionManager {

    private ConnectTask connectTask;
    private volatile boolean shutdown;
    private volatile Connection connection;

    private ProtocolConnectionManager(final ConnectTask initial) {
        Assert.checkNotNullParam("initial", initial);
        this.connectTask = initial;
    }

    /**
     * Check if connected.
     *
     * @return {@code true} if the connection is open, {@code false} otherwise
     */
    public boolean isConnected() {
        return connection != null && !shutdown;
    }

    /**
     * Get the connection. If not connected, the {@code ConnectTask} will be used to establish a connection.
     *
     * @return the connection
     * @throws IOException
     */
    public Connection connect() throws IOException {
        Connection connection;
        synchronized (this) {
            if(shutdown) throw ProtocolLogger.ROOT_LOGGER.channelClosed();
            connection = this.connection;
            if(connection == null) {
                connection = connectTask.connect();
                if(connection == null) {
                    throw ProtocolLogger.ROOT_LOGGER.channelClosed();
                }
                boolean ok = false;
                try {
                    // Connection opened notification
                    final ConnectionOpenHandler openHandler = connectTask.getConnectionOpenedHandler();
                    openHandler.connectionOpened(connection);
                    ok = true;
                    this.connection = connection;
                    connection.addCloseHandler(new CloseHandler<Connection>() {
                        @Override
                        public void handleClose(Connection closed, IOException exception) {
                            onConnectionClose(closed);
                        }
                    });
                } finally {
                    if(!ok) {
                        StreamUtils.safeClose(connection);
                    }
                }
            }
        }
        return connection;
    }

    /**
     * Get the connection.
     *
     * @return the connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Shutdown the connection manager.
     */
    public void shutdown() {
        final Connection connection;
        synchronized (this) {
            if(shutdown) return;
            shutdown = true;
            connection = this.connection;
            if(connectTask != null) {
                connectTask.shutdown();
            }
        }
        if (connection != null) {
            connection.closeAsync();
        }
    }

    /**
     * Notification that a connection was closed.
     *
     * @param closed the closed connection
     */
    private void onConnectionClose(final Connection closed) {
        synchronized (this) {
            if(connection == closed) {
                connection = null;
                if(shutdown) {
                    connectTask = DISCONNECTED;
                    return;
                }
                final ConnectTask previous = connectTask;
                connectTask = previous.connectionClosed();
            }
        }
    }

    /** Handler for notifications that a connection has been opened */
    public interface ConnectionOpenHandler {

        /**
         * Connection opened notification
         *
         * @param connection the connection
         * @throws IOException
         */
        void connectionOpened(final Connection connection) throws IOException;

    }

    /**
     * Task used to establish the connection.
     */
    public interface ConnectTask {

        /**
         * Get the connection opened handler.
         *
         * @return the connection opened handler
         */
        ConnectionOpenHandler getConnectionOpenedHandler();

        /**
         * Create a new connection
         *
         * @return the connection
         * @throws IOException
         */
        Connection connect() throws IOException;

        /**
         * Notification when the channel is closed, but the manager not shutdown.
         *
         * @return the next connect connectTask
         */
        ConnectTask connectionClosed();

        /**
         * Notification when the connection manager gets shutdown.
         */
        void shutdown();

    }

    /**
     * Create a new connection manager, based on an existing connection.
     *
     * @param connection the existing connection
     * @param openHandler a connection open handler
     * @return the connected manager
     */
    public static ProtocolConnectionManager create(final Connection connection, final ConnectionOpenHandler openHandler) {
        return create(new EstablishedConnection(connection, openHandler));
    }

    /**
     * Create a new connection manager, which will try to connect using the protocol connection configuration.
     *
     * @param configuration the connection configuration
     * @param openHandler the connection open handler
     * @return the connection manager
     */
    public static ProtocolConnectionManager create(final ProtocolConnectionConfiguration configuration, final ConnectionOpenHandler openHandler) {
        return create(new EstablishingConnection(configuration, openHandler));
    }

    /**
     * Create a new connection manager, which will try to connect using the protocol connection configuration.
     *
     * @param configuration the connection configuration
     * @param openHandler the connection open handler
     * @param next the next connect connectTask used once disconnected
     * @return the connection manager
     */
    public static ProtocolConnectionManager create(final ProtocolConnectionConfiguration configuration, final ConnectionOpenHandler openHandler, final ConnectTask next) {
        return create(new EstablishingConnection(configuration, openHandler, next));
    }

    /**
     * Create a new connection manager.
     *
     * @param connectTask the connect connectTask
     * @return the connection manager
     */
    public static ProtocolConnectionManager create(final ConnectTask connectTask) {
        return new ProtocolConnectionManager(connectTask);
    }

    private static class EstablishingConnection implements ConnectTask {

        private final ConnectTask next;
        private final ConnectionOpenHandler openHandler;
        private final ProtocolConnectionConfiguration configuration;

        protected EstablishingConnection(final ProtocolConnectionConfiguration configuration, final ConnectionOpenHandler openHandler) {
            this.configuration = configuration;
            this.openHandler = openHandler;
            this.next = this;
        }

        protected EstablishingConnection(final ProtocolConnectionConfiguration configuration, final ConnectionOpenHandler openHandler, final ConnectTask next) {
            this.configuration = configuration;
            this.openHandler = openHandler;
            this.next = next;
        }

        @Override
        public ConnectionOpenHandler getConnectionOpenedHandler() {
            return openHandler;
        }

        @Override
        public Connection connect() throws IOException {
            return ProtocolConnectionUtils.connectSync(configuration);
        }

        @Override
        public ConnectTask connectionClosed() {
            return next;
        }

        @Override
        public void shutdown() {
            //
        }
    }

    private static class EstablishedConnection implements ConnectTask {

        private final Connection connection;
        private final ConnectionOpenHandler openHandler;
        private EstablishedConnection(final Connection connection, final ConnectionOpenHandler openHandler) {
            this.connection = connection;
            this.openHandler = openHandler;
        }

        @Override
        public ConnectionOpenHandler getConnectionOpenedHandler() {
            return openHandler;
        }

        @Override
        public Connection connect() throws IOException {
            return connection;
        }

        @Override
        public ConnectTask connectionClosed() {
            return DISCONNECTED;
        }

        @Override
        public void shutdown() {
            //
        }
    }

    /**
     * A {@code ConnectTask} that can be returned from {@link ConnectTask#connectionClosed()}
     * to terminate further attempts to connect.
     */
    public static final ConnectTask DISCONNECTED = new ConnectTask() {

        @Override
        public ConnectionOpenHandler getConnectionOpenedHandler() {
            return new ConnectionOpenHandler() {
                @Override
                public void connectionOpened(final Connection connection) throws IOException {
                    //
                }
            };
        }

        @Override
        public Connection connect() throws IOException {
            throw ProtocolLogger.ROOT_LOGGER.channelClosed();
        }

        @Override
        public ConnectTask connectionClosed() {
            return this;
        }

        @Override
        public void shutdown() {
            //
        }
    };

}
