/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import org.jboss.as.cli.AwaiterModelControllerClient;
import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Map;
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
import org.wildfly.security.SecurityFactory;
import org.jboss.remoting3.EndpointBuilder;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.MatchRule;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.http.RedirectException;

/**
 * @author Alexey Loubyansky
 *
 */
public class CLIModelControllerClient extends AbstractModelControllerClient
        implements AwaiterModelControllerClient {

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);
    private static final OptionMap DEFAULT_OPTIONS = OptionMap.EMPTY;

    private static final Endpoint endpoint;
    static {
        try {
            // Making the XNIO Executor to only used 4 threads + unbounded queue.
            // This Executor is used by management requests and remoting protocol.
            EndpointBuilder endpointBuilder = Endpoint.builder();
            XnioWorker.Builder workerBuilder = endpointBuilder.buildXnioWorker(Xnio.getInstance());
            workerBuilder.setMaxWorkerPoolSize(4);
            endpoint = endpointBuilder.setEndpointName("cli-client").build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create remoting endpoint");
        }

        CliShutdownHook.add(new CliShutdownHook.Handler() {
            @Override
            public void shutdown() {
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
    private static final byte MUST_CLOSE = 4;

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
            final ConnectionCloseHandler closeHandler, Map<String, String> saslOptions, SecurityFactory<SSLContext> sslContextFactory,
            boolean fallbackSslContext, ProtocolTimeoutHandler timeoutHandler, String clientBindAddress) throws IOException {
        this.handler = handler;
        this.closeHandler = closeHandler;

        this.channelAssociation = new ManagementChannelHandler(new ManagementClientChannelStrategy() {
            @Override
            public Channel getChannel() throws IOException {
                return getOrCreateChannel();
            }

            @Override
            public void close() throws IOException {
            }
        }, endpoint.getXnioWorker(), this);

        URI connURI;
        try {
            connURI = new URI(address.getProtocol(), null, address.getHost(), address.getPort(), null, null, null);
        } catch (URISyntaxException e) {
            throw new IOException("Failed to create URI" , e);
        }

        try {
            if (sslContextFactory != null && fallbackSslContext == false) {
                // If the SSLContext was defined in the CLI configuration it should take priority.
                this.sslContext = sslContextFactory.create();
            } else {
                AuthenticationContext authenticationContext = AuthenticationContext.captureCurrent();
                if (sslContextFactory != null) {
                    // If not we add the default SSLContext created by the CLI to the end of the list.
                    // This will match if a suitable match is not already available on the AC.
                    authenticationContext = authenticationContext.withSsl(MatchRule.ALL, sslContextFactory);
                }

                this.sslContext = AUTH_CONFIGURATION_CLIENT.getSSLContext(connURI, authenticationContext);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to obtain SSLContext", e);
        }

        channelConfig = ProtocolConnectionConfiguration.create(endpoint, connURI, DEFAULT_OPTIONS);
        channelConfig.setClientBindAddress(clientBindAddress);
        this.saslOptions = saslOptions;
        channelConfig.setSaslOptions(saslOptions);
        if(connectionTimeout > 0) {
            channelConfig.setConnectionTimeout(connectionTimeout);
        }
        channelConfig.setTimeoutHandler(timeoutHandler);
        channelConfig.setCallbackHandlerPreferred(false);
    }

    @Override
    protected ManagementChannelAssociation getChannelAssociation() throws IOException {
        return channelAssociation;
    }

    protected Channel getOrCreateChannel() throws IOException {
        Channel ch = null;
        // Strategy is checked against null by mutiple methods in locked blocks.
        // Make it non null only at the end of connection process to advertise
        // that connection is done.
        ManagementClientChannelStrategy localStrategy;
        synchronized(lock) {
            if (strategy == null) {
                final ChannelCloseHandler channelCloseHandler = new ChannelCloseHandler();
                localStrategy = ManagementClientChannelStrategy.create(channelConfig, channelAssociation, handler, saslOptions, sslContext,
                        channelCloseHandler);
                channelCloseHandler.setOriginalStrategy(localStrategy);
            } else {
                localStrategy = strategy;
            }
            state.set(CONNECTING);
        }
        // Can't be called locked, can create dead-lock in case close occurs.
        ch = localStrategy.getChannel();
        synchronized(lock) {
            strategy = localStrategy;
            // In case this client has been closed (e.g.: Ctrl-C during a reload)
            // the state is switched to MUST_CLOSE.
            if (state.get() == MUST_CLOSE) {
                close();
                lock.notifyAll();
                throw new IOException("Connection closed");
            }
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
            // Do this check in locked block. The connection could be in progress
            // and state could have been changed to CONNECTED. There is is a small
            // window but still possible.
            if (state.get() == CONNECTING) {
                state.set(MUST_CLOSE);
                // We can't go any further at the risk to deadlock when shuting down
                // the channelAssociation. If close is required, will be closed
                // in connecting thread.
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
    public void ensureConnected(long timeoutMillis) throws CommandLineException,
            IOException {
        boolean doTry = true;
        final long start = System.currentTimeMillis();
        IOException ioe = null;

        boolean timeoutOccured = false;
        while (doTry) {
            try {
                // Can't be called locked, could create dead lock if close occured.
                getOrCreateChannel().getConnection();
                doTry = false;
            } catch (IOException e) {
                ioe = e;
                synchronized (lock) {
                    if (strategy != null) {
                        StreamUtils.safeClose(strategy);
                        strategy = null;
                    }
                    lock.notifyAll();
                }
            }

            if (ioe != null) {
                if (ioe.getCause() != null && ioe.getCause() instanceof SaslException) {
                    throw new CommandLineException("Failed to establish connection", ioe);
                }

                if (System.currentTimeMillis() - start > timeoutMillis) {
                    if (timeoutOccured) {
                        throw new CommandLineException("Failed to establish connection in " + (System.currentTimeMillis() - start)
                                + "ms", ioe);
                    } else {
                        timeoutOccured = true;
                    }
                }
                // Only propagate RedirectException at the end of timeout.
                if (timeoutOccured) {
                    Throwable ex = ioe;
                    while (ex != null) {
                        if (ex instanceof RedirectException) {
                            // Transient RedirectException are not propagated,
                            // only http to https redirect.
                            try {
                                if (Util.isHttpsRedirect((RedirectException) ex,
                                        channelConfig.getUri().getScheme())) {
                                    throw (RedirectException) ex;
                                }
                            } catch (URISyntaxException uriex) {
                                // XXX OK, would fail later.
                            }
                        }
                        ex = ex.getCause();
                    }
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
