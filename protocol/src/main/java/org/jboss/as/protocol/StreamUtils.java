/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.protocol;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import javax.xml.stream.XMLStreamWriter;

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

    /**
     * Close a resource, logging an error if an error occurs. Relies on {@link org.xnio.IoUtils#safeClose}.
     * You can obtain the log with logger name {@code org.xnio.safe-close} with {@code TRACE} level.
     * @param closeable the resource to close
     *
     * @deprecated Use underlying {@link org.xnio.IoUtils#safeClose(Closeable)} directly instead.
     */
    public static void safeClose(final Closeable closeable) {
        org.xnio.IoUtils.safeClose(closeable);
    }

    /**
     * Close a resource, logging an error if an error occurs. Relies on {@link org.xnio.IoUtils#safeClose}.
     * You can obtain the log with logger name {@code org.xnio.safe-close} with {@code TRACE} level.
     * @param closeable the resource to close
     *
     * @deprecated Use underlying {@link org.xnio.IoUtils#safeClose(Socket)} directly instead.
     */
    public static void safeClose(final Socket socket) {
        org.xnio.IoUtils.safeClose(socket);
    }

    /**
     * Close a resource, logging an error if an error occurs. Relies on {@link org.xnio.IoUtils#safeClose}.
     * You can obtain the log with logger name {@code org.xnio.safe-close} with {@code TRACE} level.
     * @param closeable the resource to close
     *
     * @deprecated Use underlying {@link org.xnio.IoUtils#safeClose(ServerSocket)} directly instead.
     */
    public static void safeClose(final ServerSocket serverSocket) {
        org.xnio.IoUtils.safeClose(serverSocket);
    }

    /**
     * Close a resource, logging an error if an error occurs. Relies on {@link org.xnio.IoUtils#safeClose}.
     * You can obtain the log with logger name {@code org.xnio.safe-close} with {@code TRACE} level.
     * @param closeable the resource to close
     *
     * @deprecated Use underlying {@link org.xnio.IoUtils#safeClose(AutoCloseable)} with a lambda {@code writer::close} call instead.
     */
    public static void safeClose(final XMLStreamWriter writer) {
        if (writer != null) {
            org.xnio.IoUtils.safeClose((AutoCloseable) writer::close);
        }
    }
}
