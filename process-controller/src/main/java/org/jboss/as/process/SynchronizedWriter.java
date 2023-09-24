/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings({ "SynchronizeOnNonFinalField" })
final class SynchronizedWriter extends Writer {
    private final Writer delegate;

    SynchronizedWriter(final PrintStream target) {
        super(target);
        delegate = new OutputStreamWriter(target, StandardCharsets.UTF_8);
    }

    public void write(final int c) throws IOException {
        synchronized (lock) {
            delegate.write(c);
        }
    }

    public void write(final char[] cbuf) throws IOException {
        synchronized (lock) {
            delegate.write(cbuf);
        }
    }

    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        synchronized (lock) {
            delegate.write(cbuf, off, len);
        }
    }

    public void write(final String str) throws IOException {
        synchronized (lock) {
            delegate.write(str);
        }
    }

    public void write(final String str, final int off, final int len) throws IOException {
        synchronized (lock) {
            delegate.write(str, off, len);
        }
    }

    public Writer append(final CharSequence csq) throws IOException {
        synchronized (lock) {
            return delegate.append(csq);
        }
    }

    public Writer append(final CharSequence csq, final int start, final int end) throws IOException {
        synchronized (lock) {
            return delegate.append(csq, start, end);
        }
    }

    public Writer append(final char c) throws IOException {
        synchronized (lock) {
            return delegate.append(c);
        }
    }

    public void flush() throws IOException {
        synchronized (lock) {
            delegate.flush();
        }
    }

    public void close() throws IOException {
        // nada
    }
}
