/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import org.jboss.as.process.logging.ProcessLogger;
import org.jboss.as.process.protocol.Connection.ClosedCallback;
import org.wildfly.common.Assert;

import javax.net.SocketFactory;

/**
 * A protocol client for management commands, which can also asynchronously receive protocol messages.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProtocolClient {

    private final ThreadFactory threadFactory;
    private final SocketFactory socketFactory;
    private final InetSocketAddress serverAddress;
    private final MessageHandler messageHandler;
    private final InetSocketAddress bindAddress;
    private final int connectTimeout;
    private final int readTimeout;
    private final Executor readExecutor;
    private final ClosedCallback callback;

    public ProtocolClient(final Configuration configuration) {
        threadFactory = configuration.getThreadFactory();
        bindAddress = configuration.getBindAddress();
        connectTimeout = configuration.getConnectTimeout();
        socketFactory = configuration.getSocketFactory();
        messageHandler = configuration.getMessageHandler();
        serverAddress = configuration.getServerAddress();
        readTimeout = configuration.getReadTimeout();
        readExecutor = configuration.getReadExecutor();
        callback = configuration.getClosedCallback();
        Assert.checkNotNullParam("threadFactory", threadFactory);
        Assert.checkNotNullParam("socketFactory", socketFactory);
        Assert.checkNotNullParam("serverAddress", serverAddress);
        Assert.checkNotNullParam("messageHandler", messageHandler);
        Assert.checkNotNullParam("readExecutor", readExecutor);
    }

    public Connection connect() throws IOException {
        ProcessLogger.PROTOCOL_CLIENT_LOGGER.tracef("Creating connection to %s", serverAddress);
        final Socket socket = socketFactory.createSocket();
        final ConnectionImpl connection = new ConnectionImpl(socket, messageHandler, readExecutor, callback);
        final Thread thread = threadFactory.newThread(connection.getReadTask());
        if (thread == null) {
            throw ProcessLogger.ROOT_LOGGER.threadCreationRefused();
        }
        if (bindAddress != null) socket.bind(bindAddress);
        if (readTimeout != 0) socket.setSoTimeout(readTimeout);
        socket.connect(serverAddress, connectTimeout);
        thread.setName("Read thread for " + serverAddress);
        thread.start();
        ProcessLogger.PROTOCOL_CLIENT_LOGGER.tracef("Connected to %s", serverAddress);
        return connection;
    }

    public static final class Configuration {
        private ThreadFactory threadFactory;
        private SocketFactory socketFactory;
        private InetSocketAddress serverAddress;
        private MessageHandler messageHandler;
        private InetSocketAddress bindAddress;
        private Executor readExecutor;
        private int connectTimeout = 0;
        private int readTimeout = 0;
        private ClosedCallback closedCallback;

        public Configuration() {
        }

        public ThreadFactory getThreadFactory() {
            return threadFactory;
        }

        public void setThreadFactory(final ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
        }

        public SocketFactory getSocketFactory() {
            return socketFactory;
        }

        public void setSocketFactory(final SocketFactory socketFactory) {
            this.socketFactory = socketFactory;
        }

        public InetSocketAddress getServerAddress() {
            return serverAddress;
        }

        public void setServerAddress(final InetSocketAddress serverAddress) {
            this.serverAddress = serverAddress;
        }

        public MessageHandler getMessageHandler() {
            return messageHandler;
        }

        public void setMessageHandler(final MessageHandler messageHandler) {
            this.messageHandler = messageHandler;
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        public void setBindAddress(final InetSocketAddress bindAddress) {
            this.bindAddress = bindAddress;
        }

        public Executor getReadExecutor() {
            return readExecutor;
        }

        public void setReadExecutor(final Executor readExecutor) {
            this.readExecutor = readExecutor;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(final int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(final int readTimeout) {
            this.readTimeout = readTimeout;
        }

        public ClosedCallback getClosedCallback() {
            return closedCallback;
        }

        public void setClosedCallback(ClosedCallback closedCallback) {
            this.closedCallback = closedCallback;
        }
    }
}
