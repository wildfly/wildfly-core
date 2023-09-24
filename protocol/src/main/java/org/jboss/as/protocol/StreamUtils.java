/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.protocol.logging.ProtocolLogger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StreamUtils {

    private static final int BUFFER_SIZE = 8192;

    private StreamUtils() {
        //
    }

    public static void copyStream(final InputStream in, final OutputStream out) throws IOException {
        final byte[] bytes = new byte[BUFFER_SIZE];
        int cnt;
        while ((cnt = in.read(bytes)) != -1) {
            out.write(bytes, 0, cnt);
        }
    }

    public static void copyStream(final InputStream in, final DataOutput out) throws IOException {
        final byte[] bytes = new byte[BUFFER_SIZE];
        int cnt;
        while ((cnt = in.read(bytes)) != -1) {
            out.write(bytes, 0, cnt);
        }
    }

    public static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Throwable t) {
            ProtocolLogger.ROOT_LOGGER.failedToCloseResource(t, closeable);
        }
    }

    public static void safeClose(final Socket socket) {
        if (socket != null) try {
            socket.close();
        } catch (Throwable t) {
            ProtocolLogger.ROOT_LOGGER.failedToCloseResource(t, socket);
        }
    }

    public static void safeClose(final ServerSocket serverSocket) {
        if (serverSocket != null) try {
            serverSocket.close();
        } catch (IOException e) {
            ProtocolLogger.ROOT_LOGGER.failedToCloseServerSocket(e, serverSocket);
        }
    }

    public static void safeClose(final XMLStreamWriter writer) {
        if (writer != null) try {
            writer.close();
        } catch (Throwable t) {
            ProtocolLogger.ROOT_LOGGER.failedToCloseResource(t, writer);
        }
    }
}
