/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import org.jboss.as.host.controller.logging.HostControllerLogger;

/**
 * Bootstrap of a non-embedded HostController process.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public final class HostControllerBootstrap extends AbstractHostControllerBootstrap {

    public HostControllerBootstrap(final HostControllerEnvironment environment, final String authCode) {
        super(environment, authCode,
                new ShutdownHook(HostControllerBootstrap::handleProcessExiting));
    }

    /**
     * Start the host controller services.
     */
    public void bootstrap() {
        bootstrap(false);
    }

    private static void handleProcessExiting() {
        SystemExiter.logBeforeExit(HostControllerLogger.ROOT_LOGGER::shutdownHookInvoked);
    }

}
