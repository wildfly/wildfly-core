/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl;

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;

import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ModelControllerClientFactory.ConnectionCloseHandler;
import org.jboss.as.controller.client.impl.AbstractModelControllerClient;
import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.ProtocolTimeoutHandler;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.HttpUpgradeConnectionProviderFactory;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.threads.JBossThreadFactory;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * @author Alexey Loubyansky
 *
 */
public class CLIModelControllerClient extends AbstractModelControllerClient
        implements AwaiterModelControllerClient {

    private static final OptionMap DEFAULT_OPTIONS = OptionMap.EMPTY;

    private static final ThreadPoolExecutor executorService;
    private static final Endpoint endpoint;
    static {
        final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
        final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
            public JBossThreadFactory run() {
                return new JBossThreadFactory(new ThreadGroup("cli-remoting"), Boolean.FALSE, null, "%G - %t", null, null);
            }
        });
        executorService = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, workQueue, threadFactory);
        // Allow the core threads to time out as well
        executorService.allowCoreThreadTimeOut(true);

        try {
            endpoint = Remoting.createEndpoint("cli-client", OptionMap.create(Options.THREAD_DAEMON, true));
            endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.EMPTY);
            endpoint.addConnectionProvider("remoting", new RemoteConnectionProviderFactory(), OptionMap.EMPTY);
            endpoint.addConnectionProvider("http-remoting", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            endpoint.addConnectionProvider("remote+http", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            endpoint.addConnectionProvider("https-remoting", new HttpUpgradeConnectionProviderFactory(),  OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
            endpoint.addConnectionProvider("remote+https", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create remoting endpoint", e);
        }

        CliShutdownHook.add(new CliShutdownHook.Handler() {
            @Override
            public void shutdown() {
                executorService.shutdown();
                try {
                    executorService.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {}
                try {
                    endpoint.close();
                } catch (IOException e) {
                }
            }
        });
    }

    private static final byte CLOSED = 0;
    private static final byte CONNECTING = 1;
    private static final byte CONNECTED = 2;
    private static final byte LOST_CONNECTION = 3;

    private final Object lock = new Object();

    private final CallbackHandler handler;
    private final Map<String, String> saslOptions;
    private final SSLContext sslContext;
    private final ConnectionCloseHandler closeHandler;

    private final ManagementChannelHandler channelAssociation;
    private ManagementClientChannelStrategy strategy;
    private final ProtocolConnectionConfiguration channelConfig;
    private final AtomicInteger state = new AtomicInteger(CLOSED);

    CLIModelControllerClient(final ControllerAddress address, CallbackHandler handler, int connectionTimeout,
            final ConnectionCloseHandler closeHandler, Map<String, String> saslOptions, SSLContext sslContext,
            ProtocolTimeoutHandler timeoutHandler, String clientBindAddress) throws IOException {
        this.handler = handler;
        this.sslContext = sslContext;
        this.closeHandler = closeHandler;

        this.channelAssociation = new ManagementChannelHandler(new ManagementClientChannelStrategy() {
            @Override
            public Channel getChannel() throws IOException {
                return getOrCreateChannel();
            }

            @Override
            public void close() throws IOException {
            }
        }, executorService, this);

        URI connURI;
        try {
            connURI = new URI(address.getProtocol(), null, address.getHost(), address.getPort(), null, null, null);
        } catch (URISyntaxException e) {
            throw new IOException("Failed to create URI" , e);
        }

        channelConfig = ProtocolConnectionConfiguration.create(endpoint, connURI, DEFAULT_OPTIONS);
        channelConfig.setClientBindAddress(clientBindAddress);
        this.saslOptions = saslOptions;
        channelConfig.setSaslOptions(saslOptions);
        if(connectionTimeout > 0) {
            channelConfig.setConnectionTimeout(connectionTimeout);
        }
        channelConfig.setTimeoutHandler(timeoutHandler);
    }

    @Override
    protected ManagementChannelAssociation getChannelAssociation() throws IOException {
        return channelAssociation;
    }

    protected Channel getOrCreateChannel() throws IOException {
        Channel ch = null;
        synchronized(lock) {
            if (strategy == null) {
                final ChannelCloseHandler channelCloseHandler = new ChannelCloseHandler();
                strategy = ManagementClientChannelStrategy.create(channelConfig, channelAssociation, handler, saslOptions, sslContext,
                        channelCloseHandler);
                channelCloseHandler.setOriginalStrategy(strategy);
            }
            state.set(CONNECTING);
            ch = strategy.getChannel();
            // it could happen that the connection has been lost already
            // in that case the channel close handler would change the state to LOST_CONNECTION
            if(state.get() == LOST_CONNECTION) {
                close(); // this will clean up things up here but the closed channel is still returned
            } else {
                state.set(CONNECTED);
            }
            lock.notifyAll();
        }
        return ch;
    }

    @Override
    public boolean isConnected() {
        return strategy != null;
    }

    @Override
    public void close() throws IOException {
        if(state.get() == CLOSED) {
            return;
        }
        synchronized (lock) {
            if(state.get() == CLOSED) {
                return;
            }
            state.set(CLOSED);
            // Don't allow any new request
            channelAssociation.shutdown();
            // First close the channel and connection
            if (strategy != null) {
                StreamUtils.safeClose(strategy);
                strategy = null;
            }
            // Cancel all still active operations
            channelAssociation.shutdownNow();
            try {
                channelAssociation.awaitCompletion(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
            lock.notifyAll();
        }
    }

    @Override
    public ModelNode execute(ModelNode operation, boolean awaitClose) throws IOException {
        final ModelNode response = super.execute(operation);
        if(!Util.isSuccess(response)) {
            return response;
        }
        awaitClose(awaitClose);

        return response;
    }

    @Override
    public void awaitClose(boolean awaitClose) throws IOException {
        if (awaitClose) {
            synchronized (lock) {
                if (strategy != null) {
                    try {
                        lock.wait(5000);
                    } catch (InterruptedException e) {
                    }
                    StreamUtils.safeClose(strategy);
                    strategy = null;
                }
            }
        }
    }

    @Override
    public void ensureConnected(long timeoutMillis) throws CommandLineException {
        boolean doTry = true;
        final long start = System.currentTimeMillis();
        IOException ioe = null;
        while (doTry) {
            synchronized (lock) {
                try {
                    getOrCreateChannel().getConnection();
                    doTry = false;
                } catch (IOException e) {
                    ioe = e;
                    if (strategy != null) {
                        StreamUtils.safeClose(strategy);
                        strategy = null;
                    }
                }
                lock.notifyAll();
            }

            if (ioe != null) {
                if (ioe.getCause() != null && ioe.getCause() instanceof SaslException) {
                    throw new CommandLineException("Failed to establish connection", ioe);
                }

                if (System.currentTimeMillis() - start > timeoutMillis) {
                    throw new CommandLineException("Failed to establish connection in " + (System.currentTimeMillis() - start)
                            + "ms", ioe);
                }
                ioe = null;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new CommandLineException("Interrupted while pausing before reconnecting.", e);
                }
            }
        }
    }

    private final class ChannelCloseHandler implements CloseHandler<Channel> {

        private ManagementClientChannelStrategy originalStrategy;

        void setOriginalStrategy(ManagementClientChannelStrategy strategy) {
            if(originalStrategy != null) {
                throw new IllegalArgumentException("The strategy has already been initialized.");
            }
            originalStrategy = strategy;
        }

        @Override
        public void handleClose(final Channel closed, final IOException exception) {
            if(CLIModelControllerClient.this.state.get() == CLOSED) {
                return;
            }
            if(CLIModelControllerClient.this.state.compareAndSet(CONNECTING, LOST_CONNECTION)) {
                return;
            }

            synchronized(lock) {
                if (strategy != null) {
                    if(strategy != originalStrategy) {
                        new Exception("Channel close handler " + strategy + " " + originalStrategy).printStackTrace();
                    }
                    strategy = null;
                    closeHandler.handleClose();
                }
                channelAssociation.handleChannelClosed(closed, exception);
                lock.notifyAll();
            }
            // Closing the strategy in this handler may result in race conditions
            // with connection closing and then deadlocks in remoting
            // it's safer to close the strategy from the connection close handler
            closed.getConnection().addCloseHandler(new CloseHandler<Connection>(){
                @Override
                public void handleClose(Connection closed, IOException exception) {
                    StreamUtils.safeClose(originalStrategy);
                }});
        }
    }
}
