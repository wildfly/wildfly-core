/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.protocol.mgmt;

import static org.xnio.IoUtils.safeClose;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;

/**
 * Base receiver class for the management protocol support.
 *
 * @author Emanuel Muckenhuber
 */
public final class ManagementChannelReceiver implements Channel.Receiver {

    /**
     * Create a {@code ManagementChannelReceiver} which delegates protocol messages to
     * a {@code ManagementMessageHandler}.
     *
     * @param handler the handler
     * @return the receiver
     */
    public static ManagementChannelReceiver createDelegating(final ManagementMessageHandler handler) {
        assert handler != null;
        return new ManagementChannelReceiver(handler);
    }

    private final ManagementMessageHandler handler;
    private volatile long lastMessageTime;

    private ManagementChannelReceiver(final ManagementMessageHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handleMessage(final Channel channel, final MessageInputStream message) {
        MessageInputStream messageInputStream = message;
        try {
            ProtocolLogger.ROOT_LOGGER.tracef("%s handling incoming data", this);
            lastMessageTime = System.currentTimeMillis();
            final DataInput input = new DataInputStream(messageInputStream);
            final ManagementProtocolHeader header = ManagementProtocolHeader.parse(input);
            final byte type = header.getType();
            try {
                if (type == ManagementProtocol.TYPE_PING) {
                    // Handle legacy ping/pong directly
                    ProtocolLogger.ROOT_LOGGER.tracef("Received ping on %s", this);
                    handlePing(channel, header);
                } else if (type == ManagementProtocol.TYPE_PONG) {
                    // Nothing to do here
                    ProtocolLogger.ROOT_LOGGER.tracef("Received pong on %s", this);
                } else if (type == ManagementProtocol.TYPE_BYE_BYE) {
                    // This signal has been a no-op for years and years, maybe since AS 7.0.0.Final!
                    // JBoss Remoting deals with channel close itself; we don't do it at the
                    // management protocol level
                    ProtocolLogger.ROOT_LOGGER.tracef("Received bye bye on %s, ignoring", this);
                } else {
                    // Handle a message
                    handler.handleMessage(channel, input, header);
                }
            } finally {
                try {
                    //noinspection StatementWithEmptyBody
                    while (messageInputStream.read() != -1) {
                        // drain the message to workaround a potential remoting buffer leak
                    }
                } catch (IOException ignore) {
                    //
                }
            }
            messageInputStream.close();
            messageInputStream = null; //avoid double close
        } catch(IOException e) {
            handleError(channel, e);
        } catch (Exception e) {
            handleError(channel, new IOException(e));
        } finally {
            safeClose(messageInputStream);
            ProtocolLogger.ROOT_LOGGER.tracef("%s done handling incoming data", this);
        }
        channel.receiveMessage(this);
    }

    /**
     * @return the time of the most recent invocation of {@link #handleMessage(Channel, MessageInputStream)}
     */
    public long getLastMessageTime() {
        return lastMessageTime;
    }

    @Override
    public void handleError(final Channel channel, final IOException error) {
        ProtocolLogger.ROOT_LOGGER.tracef(error, "%s error handling incoming data", this);
        safeClose(channel);
    }

    @Override
    public void handleEnd(final Channel channel) {
        safeClose(channel);
    }

    /**
     * Handle a simple ping request.
     *
     * @param channel the channel
     * @param header the protocol header
     * @throws IOException for any error
     */
    private static void handlePing(final Channel channel, final ManagementProtocolHeader header) throws IOException {
        final ManagementProtocolHeader response = new ManagementPongHeader(header.getVersion());
        MessageOutputStream output = channel.writeMessage();
        try {
            writeHeader(response, output);
            output.close();
            output = null; //avoid double close
        } finally {
            safeClose(output);
        }
    }

    /**
     * Write the management protocol header.
     *
     * @param header the mgmt protocol header
     * @param os the output stream
     * @throws IOException
     */
    private static void writeHeader(final ManagementProtocolHeader header, final OutputStream os) throws IOException {
        final FlushableDataOutput output = FlushableDataOutputImpl.create(os);
        header.write(output);
    }

}
