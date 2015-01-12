/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

    private final PrintStream baseDelegate;
    private volatile PrintStream delegate;

    CLIPrintStream() {
        super(new ByteArrayOutputStream(), false);
        this.delegate = this.baseDelegate = System.out;
    }

    CLIPrintStream(OutputStream consoleOutput) {
        super(new ByteArrayOutputStream(), false);
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

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean checkError() {
        return delegate.checkError();
    }

    @Override
    public void write(int b) {
        delegate.write(b);
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        delegate.write(buf, off, len);
    }

    @Override
    public void print(boolean b) {
        delegate.print(b);
    }

    @Override
    public void print(char c) {
        delegate.print(c);
    }

    @Override
    public void print(int i) {
        delegate.print(i);
    }

    @Override
    public void print(long l) {
        delegate.print(l);
    }

    @Override
    public void print(float f) {
        delegate.print(f);
    }

    @Override
    public void print(double d) {
        delegate.print(d);
    }

    @Override
    public void print(char[] s) {
        delegate.print(s);
    }

    @Override
    public void print(String s) {
        delegate.print(s);
    }

    @Override
    public void print(Object obj) {
        delegate.print(obj);
    }

    @Override
    public void println() {
        delegate.println();
    }

    @Override
    public void println(boolean x) {
        delegate.println(x);
    }

    @Override
    public void println(char x) {
        delegate.println(x);
    }

    @Override
    public void println(int x) {
        delegate.println(x);
    }

    @Override
    public void println(long x) {
        delegate.println(x);
    }

    @Override
    public void println(float x) {
        delegate.println(x);
    }

    @Override
    public void println(double x) {
        delegate.println(x);
    }

    @Override
    public void println(char[] x) {
        delegate.println(x);
    }

    @Override
    public void println(String x) {
        delegate.println(x);
    }

    @Override
    public void println(Object x) {
        delegate.println(x);
    }

    @Override
    public PrintStream printf(String format, Object... args) {
        return delegate.printf(format, args);
    }

    @Override
    public PrintStream printf(Locale l, String format, Object... args) {
        return delegate.printf(l, format, args);
    }

    @Override
    public PrintStream format(String format, Object... args) {
        return delegate.format(format, args);
    }

    @Override
    public PrintStream format(Locale l, String format, Object... args) {
        return delegate.format(l, format, args);
    }

    @Override
    public PrintStream append(CharSequence csq) {
        return delegate.append(csq);
    }

    @Override
    public PrintStream append(CharSequence csq, int start, int end) {
        return delegate.append(csq, start, end);
    }

    @Override
    public PrintStream append(char c) {
        return delegate.append(c);
    }
}
