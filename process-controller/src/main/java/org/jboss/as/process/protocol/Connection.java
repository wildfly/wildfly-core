/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;

/**
 * A peer-to-peer connection with another participant in the management protocol.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Connection extends Closeable {

    /**
     * Write a protocol message.  Returns a stream which can be written to, to transmit the
     * data.  When the stream is closed, the message is concluded.
     *
     * @return the stream to which the message should be written
     * @throws IOException if an I/O error occurs
     */
    OutputStream writeMessage() throws IOException;

    /**
     * Shut down writes once all messages are sent.  This will cause the reading side's {@link MessageHandler#handleShutdown(Connection)}
     * method to be called.
     *
     * @throws IOException if an I/O error occurs
     */
    void shutdownWrites() throws IOException;

    /**
     * Close the connection.  This will interrupt both reads and writes and so should only be
     * done in the event of an unrecoverable failure of the connection.
     *
     * @throws IOException if the close fails
     */
    void close() throws IOException;

    /**
     * Change the current message handler.
     *
     * @param messageHandler the new message handler to use
     */
    void setMessageHandler(MessageHandler messageHandler);

    /**
     * Get the remote peer address.
     *
     * @return the peer address
     */
    InetAddress getPeerAddress();

    void attach(Object attachment);

    Object getAttachment();

    /**
     * Records the current message handler, which can be reset using
     * {@link #restoreMessageHandler()}
     */
    void backupMessageHandler();

    /**
     * Resets the message handler to any that was backed up using
     * {@link #backupMessageHandler()}. If no backup was done, {@link MessageHandler#NULL}
     * is used
     */
    void restoreMessageHandler();


    /**
     * A callback that will be triggered once the connection is closed
     */
    public interface ClosedCallback {
        void connectionClosed();
    }
}
