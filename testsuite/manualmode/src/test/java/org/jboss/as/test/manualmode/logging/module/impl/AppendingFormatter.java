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
