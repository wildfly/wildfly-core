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

package org.jboss.as.logging;

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
