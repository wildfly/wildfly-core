/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.impl;

import java.io.BufferedInputStream;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.protocol.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Emanuel Muckenhuber
 */
public interface InputStreamEntry extends Closeable {

    /**
     * Initialize the input stream entry.
     *
     * @return the size of the underlying stream
     * @throws java.io.IOException
     */
    int initialize() throws IOException;

    /**
     * Copy the stream.
     *
     * @param output the data output
     * @throws IOException for any error
     */
    void copyStream(DataOutput output) throws IOException;

    /**
     * Copy the data in-memory.
     */
    class InMemoryEntry implements InputStreamEntry {

        private final boolean autoClose;
        private final InputStream original;

        private byte[] data;

        public InMemoryEntry(InputStream original, boolean autoClose) {
            this.original = original;
            this.autoClose = autoClose;
        }

        @Override
        public synchronized int initialize() throws IOException {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                StreamUtils.copyStream(original, os);
            } finally {
                if(autoClose) {
                    StreamUtils.safeClose(original);
                }
            }
            data = os.toByteArray();
            return data.length;
        }

        @Override
        public synchronized void copyStream(final DataOutput output) throws IOException {
            try {
                output.write(data);
            } finally {
                data = null;
            }
        }

        @Override
        public void close() throws IOException {
            //
        }
    }

    /**
     * Cache the data on disk.
     */
    class CachingStreamEntry implements InputStreamEntry {

        private final boolean autoClose;
        private final InputStream original;

        private File temp;

        public CachingStreamEntry(final InputStream original, final boolean autoClose) {
            this.original = original;
            this.autoClose = autoClose;
        }

        @Override
        public synchronized int initialize() throws IOException {
            if(temp == null) {
                temp = File.createTempFile("client", "stream");
                try {
                    return (int) Files.copy(original, temp.toPath());
                } finally {
                    if(autoClose) {
                        StreamUtils.safeClose(original);
                    }
                }
            }
            return (int) temp.length();
        }

        @Override
        public synchronized void copyStream(final DataOutput output) throws IOException {
            final FileInputStream is = new FileInputStream(temp);
            try {
                StreamUtils.copyStream(is, output);
            } finally {
                StreamUtils.safeClose(is);
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (!temp.delete()) {
                ControllerClientLogger.ROOT_LOGGER.cannotDeleteTempFile(temp.getName());
                temp.deleteOnExit();
            }
            temp = null;
        }
    }

    InputStreamEntry EMPTY = new InputStreamEntry() {
        @Override
        public int initialize() throws IOException {
            return 0;
        }

        @Override
        public void copyStream(final DataOutput output) throws IOException {
            output.write(new byte[0]);
        }

        @Override
        public void close() throws IOException {
            //
        }
    };

    // Wrap the FIS in a streamEntry so that the controller-client has access to the underlying File
    class FileStreamEntry extends FilterInputStream implements InputStreamEntry {

        private final Path file;

        public FileStreamEntry(final File file) throws IOException {
            this(file.toPath());
        }

        public FileStreamEntry(final Path file) throws IOException {
            super(Files.newInputStream(file)); // This stream will get closed regardless of autoClose
            this.file = file;
        }

        @Override
        public int initialize() throws IOException {
            return (int) Files.size(file);
        }

        @Override
        public void copyStream(final DataOutput output) throws IOException {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                StreamUtils.copyStream(in, output);
            }
        }

    }
}
