/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.threads.AsyncFutureTask;
import org.jboss.threads.JBossExecutors;

/**
 * @author John Bailey
 */
public class FutureServiceContainer extends AsyncFutureTask<ServiceContainer> {
    public FutureServiceContainer() {
        super(JBossExecutors.directExecutor());
    }

    void done(final ServiceContainer container) {
        setResult(container);
    }

    void failed(final Throwable t) {
        Throwable cause = t != null ? t : ServerLogger.ROOT_LOGGER.throwableIsNull();
        setFailed(cause);
    }
}
