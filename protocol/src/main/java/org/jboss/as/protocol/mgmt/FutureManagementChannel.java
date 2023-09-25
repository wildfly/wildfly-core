/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.jboss.as.protocol.ProtocolConnectionManager;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * Base class for a connecting {@code ManagementClientChannelStrategy}.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class FutureManagementChannel extends ManagementClientChannelStrategy implements ProtocolConnectionManager.ConnectionOpenHandler {

    private final Object lock = new Object();
    private volatile Channel channel;
    private volatile State state = State.OPEN;

    public enum State {
        OPEN,
        CLOSING,
        CLOSED,
        ;
    }

    @Override
    public Channel getChannel() throws IOException {
        final Channel channel = this.channel;
        if(channel == null && state != State.OPEN) {
            throw channelClosed();
        }
        return channel;
    }

    protected static IOException channelClosed() {
        return ProtocolLogger.ROOT_LOGGER.channelClosed();
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if(state == State.CLOSED) {
                return;
            }
            state = State.CLOSED;
            StreamUtils.safeClose(channel);
            lock.notifyAll();
        }
    }

    public State getState() {
        return this.state;
    }

    /**
     * Check if connected.
     *
     * @return {@code true} if the connection is open, {@code false} otherwise
     */
    protected boolean isConnected() {
        return channel != null && state != State.CLOSED;
    }

    /**
     * Get the underlying channel. This may block until the channel is set.
     *
     * @return the channel
     * @throws IOException for any error
     */
    protected Channel awaitChannel() throws IOException {
        Channel channel = this.channel;
        if(channel != null) {
            return channel;
        }
        synchronized (lock) {
            for(;;) {
                if(state == State.CLOSED) {
                    throw ProtocolLogger.ROOT_LOGGER.channelClosed();
                }
                channel = this.channel;
                if(channel != null) {
                    return channel;
                }
                if(state == State.CLOSING) {
                    throw ProtocolLogger.ROOT_LOGGER.channelClosed();
                }
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    /**
     * Signal that we are about to close the channel. This will not have any affect on the underlying channel, however
     * prevent setting a new channel.
     *
     * @return whether the closing state was set successfully
     */
    protected boolean prepareClose() {
        synchronized (lock) {
            final State state = this.state;
            if (state == State.OPEN) {
                this.state = State.CLOSING;
                lock.notifyAll();
                return true;
            }
        }
        return false;
    }

    /**
     * Open a channel.
     *
     * @param connection the connection
     * @param serviceType the service type
     * @param options the channel options
     * @return the opened channel
     * @throws IOException if there is a remoting problem opening the channel or it cannot be opened in a reasonable amount of time
     */
    protected Channel openChannel(final Connection connection, final String serviceType, final OptionMap options) throws IOException {
        return openChannel(connection, serviceType, options, null);
    }

    /**
     * Open a channel.
     *
     * @param connection the connection
     * @param serviceType the service type
     * @param options the channel options
     * @param deadline time, in ms since the epoch, by which the channel must be created,
     *                 or {@code null} if the caller is not imposing a specific deadline.
     *                 Ignored if less than 10s from the current time, with 10s used as the
     *                 default if this is {@code null}
     * @return the opened channel
     * @throws IOException if there is a remoting problem opening the channel or it cannot be opened in a reasonable amount of time
     */
    final Channel openChannel(final Connection connection, final String serviceType, final OptionMap options, final Long deadline) throws IOException {
        final IoFuture<Channel> futureChannel = connection.openChannel(serviceType, options);
        long waitTime = deadline == null ? 10000 : Math.max(10000, deadline - System.currentTimeMillis());
        futureChannel.await(waitTime, TimeUnit.MILLISECONDS);
        if (futureChannel.getStatus() == IoFuture.Status.WAITING) {
            futureChannel.cancel();
            throw ProtocolLogger.ROOT_LOGGER.channelTimedOut();
        }
        return futureChannel.get();
    }

    /**
     * Set the channel. This will return whether the channel could be set successfully or not.
     *
     * @param newChannel the channel
     * @return whether the operation succeeded or not
     */
    protected boolean setChannel(final Channel newChannel) {
        if(newChannel == null) {
            return false;
        }
        synchronized (lock) {
            if(state != State.OPEN || channel != null) {
                return false;
            }
            this.channel = newChannel;
            this.channel.addCloseHandler(new CloseHandler<Channel>() {
                @Override
                public void handleClose(final Channel closed, final IOException exception) {
                    synchronized (lock) {
                        if(FutureManagementChannel.this.channel == closed) {
                            FutureManagementChannel.this.channel = null;
                        }
                        lock.notifyAll();
                    }
                }
            });
            lock.notifyAll();
            return true;
        }
    }

}
