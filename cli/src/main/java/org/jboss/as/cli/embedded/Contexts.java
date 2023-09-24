/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import org.jboss.logmanager.LogContext;
import org.jboss.stdio.StdioContext;

/**
 * Wrapper for {@link org.jboss.stdio.StdioContext} and {@link org.jboss.logmanager.LogContext} to use
 * in an embedded server.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
class Contexts {

    private final LogContext logContext;
    private final StdioContext stdioContext;

    Contexts(LogContext logContext, StdioContext stdioContext) {
        this.logContext = logContext;
        this.stdioContext = stdioContext;
    }

    LogContext getLogContext() {
        return logContext;
    }

    StdioContext getStdioContext() {
        return stdioContext;
    }
}
