/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module.impl;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.as.test.manualmode.logging.module.api.PropertyResolver;
import org.jboss.as.test.manualmode.logging.module.api.Reflection;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class AppendingFileHandler extends Handler {
    public static final String PROPERTY_KEY = "test.prepend.text";

    private final Object lock = new Object();
    private PropertyResolver resolver;
    private Path file;
    private BufferedWriter writer;

    @Override
    public void publish(final LogRecord record) {
        if (isLoggable(record)) {
            synchronized (lock) {
                final String text = checkNotNullParamWithNullPointerException("resolver cannot be null", resolver).resolve(PROPERTY_KEY);
                record.setMessage(record.getMessage() + text);
                final String line = getFormatter().format(record);
                try {
                    writer.write(line);
                    flush();
                } catch (IOException e) {
                    getErrorManager().error("Failed to write log message: " + line, e, ErrorManager.WRITE_FAILURE);
                }
            }
        }
    }

    @Override
    public void flush() {
        synchronized (lock) {
            try {
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                flush();
                writer.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                writer = null;
            }
        }
    }

    public void setFileName(final String fileName) {
        final Path file;
        if (fileName == null) {
            file = null;
        } else {
            file = Paths.get(fileName);
        }
        synchronized (lock) {
            this.file = file;
            if (file == null) {
                close();
            } else {
                activate();
            }
        }
    }

    public void setPropertyResolver(final String value) {
        synchronized (lock) {
            resolver = Reflection.newInstance(value, PropertyResolver.class);
        }
    }

    private void activate() {
        synchronized (lock) {
            if (writer == null) {
                try {
                    writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
