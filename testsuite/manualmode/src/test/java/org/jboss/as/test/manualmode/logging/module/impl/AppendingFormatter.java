/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module.impl;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.jboss.as.test.manualmode.logging.module.api.Reflection;

/**
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AppendingFormatter extends Formatter {

    private volatile String text;
    private volatile Formatter delegate;

    @Override
    public String format(final LogRecord record) {
        final Formatter delegate = this.delegate;
        final String text = this.text;
        record.setMessage(record.getMessage() + text);
        return delegate == null ? record.getMessage() : delegate.format(record);
    }

    public String getDelegate() {
        final Formatter delegate = this.delegate;
        return delegate == null ? null : delegate.getClass().getName();
    }

    public void setDelegate(final String name) {
        delegate = Reflection.newInstance(name, Formatter.class);
    }

    public String getText() {
        return text;
    }

    public void setText(final String text) {
        this.text = text;
    }
}
