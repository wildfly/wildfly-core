/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.threads.AsyncFuture;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Generic management channel handler allowing to assemble multiple {@code ManagementRequestHandlerFactory} per channel.
 *
 * @author Emanuel Muckenhuber
 */
public final class ManagementChannelHandler extends AbstractMessageHandler implements ManagementChannelAssociation {

    /**
     * Optional attachment for a temp file directory.
     */
    public static final Attachments.Key<File> TEMP_DIR = new Attachments.Key<File>(File.class);

    private static final AtomicReferenceFieldUpdater<ManagementChannelHandler, ManagementRequestHandlerFactory[]> updater = AtomicReferenceFieldUpdater.newUpdater(ManagementChannelHandler.class, ManagementRequestHandlerFactory[].class, "handlers");
    private static final ManagementRequestHandlerFactory[] NO_HANDLERS = new ManagementRequestHandlerFactory[0];

    /** The management request handlers. */
    @SuppressWarnings("ALL")
    private volatile ManagementRequestHandlerFactory[] handlers;

    // A receiver for this handler
    private final ManagementChannelReceiver receiver;
    // The management client strategy
    private final ManagementClientChannelStrategy strategy;
    private final Attachments attachments = new Attachments();

    public ManagementChannelHandler(final ManagementClientChannelStrategy strategy, final ExecutorService executorService) {
        this(strategy, executorService, NO_HANDLERS);
    }

    public ManagementChannelHandler(final ManagementClientChannelStrategy strategy, final ExecutorService executorService, final ManagementRequestHandlerFactory... initial) {
        super(executorService);
        this.strategy = strategy;
        this.handlers = initial;
        this.receiver = ManagementChannelReceiver.createDelegating(this);
    }

    public long getLastMessageReceivedTime() {
        return receiver.getLastMessageTime();
    }

    /** {@inheritDoc} */
    @Override
    public Channel getChannel() throws IOException {
        return strategy.getChannel();
    }

    /**
     * Get the remote address.
     *
     * @return the remote address, {@code null} if not available
     */
    public InetAddress getRemoteAddress() {
        final Channel channel;
        try {
            channel = strategy.getChannel();
        } catch (IOException e) {
            return null;
        }
        final Connection connection = channel.getConnection();
        final InetSocketAddress peerAddress = connection.getPeerAddress(InetSocketAddress.class);
        return peerAddress == null ? null : peerAddress.getAddress();
    }

    @Override
    public <T, A> ActiveOperation<T, A> initializeOperation(A attachment, ActiveOperation.CompletedCallback<T> callback) throws IOException {
        return super.registerActiveOperation(attachment, callback);
    }

    /** {@inheritDoc} */
    @Override
    public <T, A> ActiveOperation<T, A> executeRequest(ManagementRequest<T, A> request, A attachment, ActiveOperation.CompletedCallback<T> callback) throws IOException {
        final ActiveOperation<T, A> operation = super.registerActiveOperation(attachment, callback);
        executeRequest(operation, request);
        return operation;
    }

    /** {@inheritDoc} */
    @Override
    public <T, A> ActiveOperation<T, A> executeRequest(final ManagementRequest<T, A> request, final A attachment) throws IOException {
        final ActiveOperation<T, A> operation = super.registerActiveOperation(attachment);
        executeRequest(operation, request);
        return operation;
    }

    /** {@inheritDoc} */
    @Override
    public <T, A> AsyncFuture<T> executeRequest(final Integer operationId, final ManagementRequest<T, A> request) throws IOException {
        final ActiveOperation<T, A> operation = super.getActiveOperation(operationId);
        if(operation == null) {
            throw ProtocolLogger.ROOT_LOGGER.responseHandlerNotFound(operationId);
        }
        return executeRequest(operation, request);
    }

    /** {@inheritDoc} */
    @Override
    public <T, A> AsyncFuture<T> executeRequest(final ActiveOperation<T, A> support, final ManagementRequest<T, A> request) throws IOException {
        return super.executeRequest(request, strategy.getChannel(), support);
    }

    /** {@inheritDoc} */
    @Override
    protected ManagementRequestHandler<?, ?> getRequestHandler(final ManagementRequestHeader header) {
        final ManagementRequestHandlerFactory[] snapshot = updater.get(this);
        // Iterate through the registered handlers
        return new ManagementRequestHandlerFactory.RequestHandlerChain() {
            final int length = snapshot.length;
            private int index = -1;

            @Override
            public ManagementRequestHandler<?, ?> resolveNext() {
                if(index++ == length) {
                    return getFallbackHandler(header);
                }
                final ManagementRequestHandlerFactory factory = snapshot[index];
                if(factory == null) {
                    // return getFallbackHandler(header);
                    return resolveNext();
                }
                return factory.resolveHandler(this, header);
            }

            @Override
            public <T, A> ActiveOperation<T, A> createActiveOperation(A attachment) {
                return ManagementChannelHandler.this.registerActiveOperation(attachment);
            }

            @Override
            public <T, A> ActiveOperation<T, A> createActiveOperation(A attachment, ActiveOperation.CompletedCallback<T> completedCallback) {
                return ManagementChannelHandler.this.registerActiveOperation(attachment, completedCallback);
            }

            @Override
            public <T, A> ActiveOperation<T, A> registerActiveOperation(Integer id, A attachment) {
                return ManagementChannelHandler.this.registerActiveOperation(id, attachment);
            }

            @Override
            public <T, A> ActiveOperation<T, A> registerActiveOperation(Integer id, A attachment, ActiveOperation.CompletedCallback<T> completedCallback) {
                return ManagementChannelHandler.this.registerActiveOperation(id, attachment, completedCallback);
            }

        }.resolveNext();
    }

    /**
     * Get a receiver instance for this context.
     *
     * @return the receiver
     */
    public Channel.Receiver getReceiver() {
        return receiver;
    }

    @Override
    public Attachments getAttachments() {
        return attachments;
    }

    /**
     * Add a management request handler factory to this context.
     *
     * @param factory the request handler to add
     */
    public void addHandlerFactory(ManagementRequestHandlerFactory factory) {
        for (;;) {
            final ManagementRequestHandlerFactory[] snapshot = updater.get(this);
            final int length = snapshot.length;
            final ManagementRequestHandlerFactory[] newVal = new ManagementRequestHandlerFactory[length + 1];
            System.arraycopy(snapshot, 0, newVal, 0, length);
            newVal[length] = factory;
            if (updater.compareAndSet(this, snapshot, newVal)) {
                return;
            }
        }
    }

    /**
     * Remove a management request handler factory from this context.
     *
     * @param instance the request handler factory
     * @return {@code true} if the instance was removed, {@code false} otherwise
     */
    public boolean removeHandlerFactory(ManagementRequestHandlerFactory instance) {
        for(;;) {
            final ManagementRequestHandlerFactory[] snapshot = updater.get(this);
            final int length = snapshot.length;
            int index = -1;
            for(int i = 0; i < length; i++) {
                if(snapshot[i] == instance) {
                    index = i;
                    break;
                }
            }
            if(index == -1) {
                return false;
            }
            final ManagementRequestHandlerFactory[] newVal = new ManagementRequestHandlerFactory[length - 1];
            System.arraycopy(snapshot, 0, newVal, 0, index);
            System.arraycopy(snapshot, index + 1, newVal, index, length - index - 1);
            if (updater.compareAndSet(this, snapshot, newVal)) {
                return true;
            }
        }
    }

}
