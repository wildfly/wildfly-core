/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.impl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;

/**
 * {@link java.io.PrintStream} variant used to abstract direct use of {@link java.lang.System#out} away
 * from CLI uses and allow the CLI to have better control over writes to the console.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
final class CLIPrintStream extends PrintStream {

    private static final ThreadLocal<Boolean> entered = new ThreadLocal<Boolean>();

    private final PrintStream baseDelegate;
    private volatile PrintStream delegate;

    CLIPrintStream() {
        super(new ByteArrayOutputStream(), true);
        this.delegate = this.baseDelegate = System.out;
    }

    CLIPrintStream(OutputStream consoleOutput) {
        super(new ByteArrayOutputStream(), true);
        assert consoleOutput != null;
        this.delegate = this.baseDelegate = new PrintStream(consoleOutput);
    }

    void captureOutput(PrintStream delegate) {
        if (this.delegate != this.baseDelegate) {
            throw new IllegalStateException("Output is already being captured");
        }
        this.delegate.flush();
        this.delegate = delegate == null ? baseDelegate : delegate;
    }

    void releaseOutput() {
        if (this.delegate == this.baseDelegate) {
            throw new IllegalStateException("Output is not being captured");
        }
        this.delegate.flush();
        this.delegate = baseDelegate;
    }

    public void flush() {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.flush();
        } finally {
            entered.remove();
        }
    }

    public void close() {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.close();
        } finally {
            entered.remove();
        }
    }

    public boolean checkError() {
        if (entered.get() != null) {
            return false;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.checkError();
        } finally {
            entered.remove();
        }
    }

    public void write(final int b) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.write(b);
        } finally {
            entered.remove();
        }
    }

    public void write(final byte[] buf, final int off, final int len) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.write(buf, off, len);
        } finally {
            entered.remove();
        }
    }

    public void print(final boolean b) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(b);
        } finally {
            entered.remove();
        }
    }

    public void print(final char c) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(c);
        } finally {
            entered.remove();
        }
    }

    public void print(final int i) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(i);
        } finally {
            entered.remove();
        }
    }

    public void print(final long l) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(l);
        } finally {
            entered.remove();
        }
    }

    public void print(final float f) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(f);
        } finally {
            entered.remove();
        }
    }

    public void print(final double d) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(d);
        } finally {
            entered.remove();
        }
    }

    public void print(final char[] s) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(s);
        } finally {
            entered.remove();
        }
    }

    public void print(final String s) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(s);
        } finally {
            entered.remove();
        }
    }

    public void print(final Object obj) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.print(obj);
        } finally {
            entered.remove();
        }
    }

    public void println() {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println();
        } finally {
            entered.remove();
        }
    }

    public void println(final boolean x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final char x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final int x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final long x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final float x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final double x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final char[] x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final String x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public void println(final Object x) {
        if (entered.get() != null) {
            return;
        }
        try {
            entered.set(Boolean.TRUE);
            delegate.println(x);
        } finally {
            entered.remove();
        }
    }

    public PrintStream printf(final String format, final Object... args) {
        if (entered.get() != null) {
            return this;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.printf(format, args);
        } finally {
            entered.remove();
        }
    }

    public PrintStream printf(final Locale l, final String format, final Object... args) {
        if (entered.get() != null) {
            return this;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.printf(l, format, args);
        } finally {
            entered.remove();
        }
    }

    public PrintStream format(final String format, final Object... args) {
        if (entered.get() != null) {
            return this;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.format(format, args);
        } finally {
            entered.remove();
        }
    }

    public PrintStream format(final Locale l, final String format, final Object... args) {
        if (entered.get() != null) {
            return this;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.format(l, format, args);
        } finally {
            entered.remove();
        }
    }

    public PrintStream append(final CharSequence csq) {
        if (entered.get() != null) {
            return this;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.append(csq);
        } finally {
            entered.remove();
        }
    }

    public PrintStream append(final CharSequence csq, final int start, final int end) {
        if (entered.get() != null) {
            return this;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.append(csq, start, end);
        } finally {
            entered.remove();
        }
    }

    public PrintStream append(final char c) {
        if (entered.get() != null) {
            return this;
        }
        try {
            entered.set(Boolean.TRUE);
            return delegate.append(c);
        } finally {
            entered.remove();
        }
    }
}
