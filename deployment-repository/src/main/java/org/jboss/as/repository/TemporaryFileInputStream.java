/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.as.repository.logging.DeploymentRepositoryLogger;

/**
 * An inputstream over a temporary file that will be deleted on closing the stream.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class TemporaryFileInputStream extends TypedInputStream {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private final InputStream delegate;
    private final Path file;

    TemporaryFileInputStream(Path file) throws IOException {
        this.file = file;
        this.delegate = Files.newInputStream(file);
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        Files.deleteIfExists(file);
    }

    @Override
    public synchronized void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    public Path getFile() {
        return file;
    }

    @Override
    public String getContentType() {
        String contentType;
        try {
            contentType = Files.probeContentType(file);
            if(contentType == null) {
                contentType = DEFAULT_CONTENT_TYPE;
            }
        } catch (IOException ex) {
            DeploymentRepositoryLogger.ROOT_LOGGER.debugf(ex, "Error obtaining content-type for %s", file.toString());
            contentType = DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }
}
