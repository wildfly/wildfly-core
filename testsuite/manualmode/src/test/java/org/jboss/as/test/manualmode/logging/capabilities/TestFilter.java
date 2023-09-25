/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.capabilities;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TestFilter implements Filter {
    @Override
    public boolean isLoggable(final LogRecord record) {
        return true;
    }
}
