/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.logging.module.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;
import org.jboss.as.test.manualmode.logging.module.api.PropertyResolver;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AppendingAppender extends AppenderSkeleton {
    public static final String PROPERTY_KEY = "test.prepend.text";

    private final Object lock = new Object();
    private PropertyResolver resolver;
    private Path file;
    private BufferedWriter writer;

    @Override
    public void activateOptions() {
        super.activateOptions();
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

    @Override
    protected void append(final LoggingEvent event) {
        if (isAsSevereAsThreshold(event.getLevel())) {
            synchronized (lock) {
                final String text = Objects.requireNonNull(resolver).resolve(PROPERTY_KEY);
                final LoggingEvent changed = new LoggingEvent(event.getFQNOfLoggerClass(), event.getLogger(),
                        event.getTimeStamp(), event.getLevel(), event.getMessage() + text, event.getThreadName(),
                        event.getThrowableInformation(), event.getNDC(), event.getLocationInformation(), event.getProperties());
                final String line = getLayout().format(changed);
                try {
                    writer.write(line);
                    writer.flush();
                } catch (IOException e) {
                    LogLog.error("Failed to write log message: " + line, e);
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                writer = null;
            }
        }
    }

    @Override
    public boolean requiresLayout() {
        return true;
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
                activateOptions();
            }
        }
    }

    public void setPropertyResolver(final String value) {
        synchronized (lock) {
            resolver = (PropertyResolver) OptionConverter.instantiateByClassName(value, PropertyResolver.class, null);
        }
    }
}
