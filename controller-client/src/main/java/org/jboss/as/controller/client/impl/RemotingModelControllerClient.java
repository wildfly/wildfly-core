/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.wildfly.common.ref.CleanerReference;
import org.wildfly.common.ref.Reaper;
import org.wildfly.common.ref.Reference;

/**
 * {@link ModelControllerClient} based on a Remoting {@link Endpoint}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class RemotingModelControllerClient extends AbstractModelControllerClient {

    private static final Reaper<RemotingModelControllerClient, ClientCloseable> REAPER = new Reaper<>() {
        @Override
        public void reap(Reference<RemotingModelControllerClient, ClientCloseable> reference) {
            ClientCloseable closeable = reference.getAttachment();
            if(! closeable.closed) {
                // Create the leak description
                final Throwable t = ControllerClientLogger.ROOT_LOGGER.controllerClientNotClosed();
                t.setStackTrace(closeable.allocationStackTrace);
                ControllerClientLogger.ROOT_LOGGER.leakedControllerClient(t);
                // Close
                StreamUtils.safeClose(closeable);
            }
        }
    };

    public static RemotingModelControllerClient create(final ModelControllerClientConfiguration configuration) {
        RemotingModelControllerClient client = new RemotingModelControllerClient(configuration);
        // Use a PhantomReference instead of overriding finalize() to ensure close gets called
        // CleanerReference handles ensuring there's a strong ref to itself so we can just construct it and move on
        new CleanerReference<>(client, client.closeable, REAPER);
        return client;
    }

    private final ClientCloseable closeable;

    private RemotingModelControllerClient(final ModelControllerClientConfiguration configuration) {

        ManagementChannelHandler handler = new ManagementChannelHandler(new ManagementClientChannelStrategy() {
            @Override
            public Channel getChannel() throws IOException {
                return getOrCreateChannel();
            }

            @Override
            public synchronized void close() throws IOException {
                //
            }
        }, configuration.getExecutor(), this);
        this.closeable = new ClientCloseable(handler, configuration, Thread.currentThread().getStackTrace());
    }

    @Override
    protected ManagementChannelAssociation getChannelAssociation() {
        return closeable.channelAssociation;
    }

    @Override
    public void close() throws IOException {
        closeable.close();
    }

    protected Channel getOrCreateChannel() throws IOException {
        synchronized (closeable) {
            if (closeable.closed) {
                throw ControllerClientLogger.ROOT_LOGGER.objectIsClosed(ModelControllerClient.class.getSimpleName());
            }
            if (closeable.strategy == null) {
                try {

                    closeable.endpoint = Endpoint.builder().setEndpointName("management-client").build();

                    final ProtocolConnectionConfiguration configuration = ProtocolConfigurationFactory.create(closeable.clientConfiguration, closeable.endpoint);

                    closeable.strategy = ManagementClientChannelStrategy.create(configuration, closeable.channelAssociation, closeable.clientConfiguration.getCallbackHandler(),
                            closeable.clientConfiguration.getSaslOptions(), closeable.clientConfiguration.getSSLContext(),
                            new CloseHandler<>() {
                                @Override
                                public void handleClose(final Channel closed, final IOException exception) {
                                    closeable.channelAssociation.handleChannelClosed(closed, exception);
                                }
                            });
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return closeable.strategy.getChannel();
        }
    }

    private static final class ClientCloseable implements Closeable {

        private Endpoint endpoint;
        private ManagementClientChannelStrategy strategy;
        private boolean closed;

        private final ManagementChannelHandler channelAssociation;
        private final ModelControllerClientConfiguration clientConfiguration;
        private final StackTraceElement[] allocationStackTrace;

        private ClientCloseable(ManagementChannelHandler channelAssociation,
                                ModelControllerClientConfiguration clientConfiguration,
                                StackTraceElement[] allocationStackTrace) {
            this.channelAssociation = channelAssociation;
            this.clientConfiguration = clientConfiguration;
            this.allocationStackTrace = allocationStackTrace;
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                // Don't allow any new request
                channelAssociation.shutdown();
                // First close the channel and connection
                if (strategy != null) {
                    StreamUtils.safeClose(strategy);
                    strategy = null;
                }
                // Then the endpoint
                final Endpoint endpoint = this.endpoint;
                if (endpoint != null) {
                    this.endpoint = null;
                    try {
                        endpoint.closeAsync();
                    } catch (UnsupportedOperationException ignored) {
                    }
                }
                // Cancel all still active operations
                channelAssociation.shutdownNow();
                try {
                    channelAssociation.awaitCompletion(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                } finally {
                    StreamUtils.safeClose(clientConfiguration);
                }
                // Per WFCORE-1573 remoting endpoints should be closed asynchronously, however consumers of this client
                // likely need to wait until the endpoints are fully shutdown.
                if (endpoint != null) try {
                    endpoint.awaitClosed();
                } catch (InterruptedException e) {
                    final InterruptedIOException cause = new InterruptedIOException(e.getLocalizedMessage());
                    cause.initCause(e);
                    throw cause;
                }
            }
        }
    }

}
