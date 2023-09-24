/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
