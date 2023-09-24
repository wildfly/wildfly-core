/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtLogRecord;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TestFilter implements Filter {
    private final String constructorText;
    private final boolean isLoggable;
    private String propertyText;

    public TestFilter() {
        this(null, true);
    }

    public TestFilter(final boolean isLoggable) {
        this(null, isLoggable);
    }

    public TestFilter(final String constructorText) {
        this(constructorText, true);
    }

    public TestFilter(final String constructorText, final boolean isLoggable) {
        this.constructorText = constructorText;
        this.isLoggable = isLoggable;
    }

    @Override
    public boolean isLoggable(final LogRecord record) {
        if (isLoggable) {
            final StringBuilder newMsg = new StringBuilder(ExtLogRecord.wrap(record).getFormattedMessage());
            if (constructorText != null) {
                newMsg.append(constructorText);
            }
            if (propertyText != null) {
                newMsg.append(propertyText);
            }
            record.setMessage(newMsg.toString());
        }
        return isLoggable;
    }

    public String getPropertyText() {
        return propertyText;
    }

    public void setPropertyText(final String propertyText) {
        this.propertyText = propertyText;
    }

    public String getConstructorText() {
        return constructorText;
    }

    @Override
    public String toString() {
        return TestFilter.class.getName() +
                "[constructorText=" + constructorText +
                ", isLoggable=" + isLoggable +
                ", propertyText=" + propertyText +
                "]";
    }
}
