/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;


import org.jboss.as.host.controller.logging.HostControllerLogger;

/**
 * Used to override System.exit() calls. For our tests we don't
 * want System.exit to have any effect.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class SystemExiter extends org.jboss.as.server.SystemExiter {

    public static void abort(final int status) {
        logAndExit(new ExitLogger() {
            @Override
            public void logExit() {
                HostControllerLogger.ROOT_LOGGER.aborting(status);
            }
        }, status);
    }

    /** @deprecated use {@link org.jboss.as.server.SystemExiter.Exiter}*/
    @Deprecated
    public interface Exiter extends org.jboss.as.server.SystemExiter.Exiter {
    }
}
