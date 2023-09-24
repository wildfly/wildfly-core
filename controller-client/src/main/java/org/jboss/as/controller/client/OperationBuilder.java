/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client;

import java.io.BufferedInputStream;
import java.io.DataOutput;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.Assert;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jboss.as.controller.client.impl.InputStreamEntry;
import org.jboss.as.protocol.StreamUtils;

/**
 * Builder for a {@link Operation}.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class OperationBuilder {

    private final ModelNode operation;
    private volatile List<InputStream> inputStreams;
    private boolean autoCloseStreams = false;

    public OperationBuilder(final ModelNode operation) {
        this(operation, false);
    }

    public OperationBuilder(final ModelNode operation, boolean autoCloseStreams) {
        Assert.checkNotNullParam("operation", operation);
        this.operation = operation;
        this.autoCloseStreams = autoCloseStreams;
    }

    /**
     * Associate a file with the operation. This will create a {@code FileInputStream}
     * and add it as attachment.
     *
     * @param file the file
     * @return the operation builder
     */
    public OperationBuilder addFileAsAttachment(final File file) {
        Assert.checkNotNullParam("file", file);
        try {
            FileStreamEntry entry = new FileStreamEntry(file);
            if (inputStreams == null) {
                inputStreams = new ArrayList<InputStream>();
            }
            inputStreams.add(entry);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * Associate a file with the operation. This will create a {@code FileInputStream}
     * and add it as attachment.
     *
     * @param file the file
     * @return the operation builder
     */
    public OperationBuilder addFileAsAttachment(final Path file) {
        Assert.checkNotNullParam("file", file);
        try {
            FileStreamEntry entry = new FileStreamEntry(file);
            if (inputStreams == null) {
                inputStreams = new ArrayList<InputStream>();
            }
            inputStreams.add(entry);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * Associate an input stream with the operation. Closing the input stream
     * is the responsibility of the caller.
     *
     * @param in  the input stream. Cannot be {@code null}
     * @return a builder than can be used to continue building the operation
     */
    public OperationBuilder addInputStream(final InputStream in) {
        Assert.checkNotNullParam("in", in);
        if (inputStreams == null) {
            inputStreams = new ArrayList<InputStream>();
        }
        inputStreams.add(in);
        return this;
    }

    /**
     * Gets the number of input streams currently associated with the operation,
     *
     * @return  the number of input streams
     */
    public int getInputStreamCount() {
        List<InputStream> list = inputStreams;
        return list == null ? 0 : list.size();
    }

    /**
     * Automatically try to close the stream, once the operation finished executing.
     *
     * @param autoCloseStreams whether to close the streams or not
     */
    public void setAutoCloseStreams(boolean autoCloseStreams) {
        this.autoCloseStreams = autoCloseStreams;
    }

    /**
     * Builds the operation.
     *
     * @return the operation
     */
    public Operation build() {
        return new OperationImpl(operation, inputStreams, autoCloseStreams);
    }

    /**
     * Create an operation builder.
     *
     * @param operation the operation
     * @return the builder
     */
    public static OperationBuilder create(final ModelNode operation) {
        return new OperationBuilder(operation);
    }

    /**
     * Create an operation builder.
     *
     * @param operation the operation
     * @param autoCloseStreams whether streams should be automatically closed
     * @return the builder
     */
    public static OperationBuilder create(final ModelNode operation, final boolean autoCloseStreams) {
        return new OperationBuilder(operation, autoCloseStreams);
    }

    // Wrap the FIS in a streamEntry so that the controller-client has access to the underlying File
    private static class FileStreamEntry extends FilterInputStream implements InputStreamEntry {

        private final Path file;
        private FileStreamEntry(final File file) throws IOException {
            this(file.toPath());
        }

        private FileStreamEntry(final Path file) throws IOException {
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
