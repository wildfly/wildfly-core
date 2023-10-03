/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager;

import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.LogContextInitializer;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class WildFlyLogContextInitializer implements LogContextInitializer {
    @Override
    public Level getInitialLevel(final String loggerName) {
        // TODO (jrp) we might need a system property for this
        return LogContextInitializer.super.getInitialLevel(loggerName);
    }

    @Override
    public Level getMinimumLevel(final String loggerName) {
        // TODO (jrp) we might need a system property for this
        return LogContextInitializer.super.getMinimumLevel(loggerName);
    }

    @Override
    public Handler[] getInitialHandlers(final String loggerName) {
        // TODO (jrp) here we put the DelayedHandler. Possibly a static one so we have access to it
        return LogContextInitializer.super.getInitialHandlers(loggerName);
    }

    @Override
    public boolean useStrongReferences() {
        return true;
    }
}
