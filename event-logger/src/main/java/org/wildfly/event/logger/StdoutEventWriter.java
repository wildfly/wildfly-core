/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.wildfly.event.logger;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * An event writer which writes directly to {@code stdout}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StdoutEventWriter implements EventWriter {

    private static final PrintStream STDOUT = new PrintStream(new FileOutputStream(FileDescriptor.out), true);

    private final EventFormatter formatter;

    private StdoutEventWriter(final EventFormatter formatter) {
        this.formatter = formatter;
    }

    /**
     * Creates a new {@code stdout} event writer with the provided formatter.
     *
     * @param formatter the formatter to use for formatting the event
     *
     * @return a new {@code stdout} event writer
     */
    public static StdoutEventWriter of(final EventFormatter formatter) {
        return new StdoutEventWriter(formatter);
    }

    @Override
    public void write(final Event event) {
        final EventFormatter formatter = this.formatter;
        STDOUT.println(formatter.format(event));
    }

    @Override
    public void close() {
        // Don't actually close, just flush
        STDOUT.flush();
    }
}
