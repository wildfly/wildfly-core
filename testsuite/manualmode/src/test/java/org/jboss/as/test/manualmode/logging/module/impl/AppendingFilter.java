/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module.impl;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

import org.jboss.as.test.manualmode.logging.module.api.PropertyResolver;
import org.jboss.as.test.manualmode.logging.module.api.PropertyResolverFactory;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AppendingFilter implements Filter {

    private final PropertyResolver resolver;
    private final String text;

    public AppendingFilter(final String text) {
        this.text = text;
        resolver = PropertyResolverFactory.newResolver();
    }

    @Override
    public boolean isLoggable(final LogRecord record) {
        if (resolver != null && text != null) {
            final String currentMsg = record.getMessage();
            record.setMessage(currentMsg + " " + text);
            return true;
        }
        return false;
    }

    public String getText() {
        return text;
    }
}
