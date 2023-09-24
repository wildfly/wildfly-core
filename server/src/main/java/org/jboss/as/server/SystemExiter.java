/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;


import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.server.logging.ServerLogger;

/**
 * Used to override System.exit() calls. For our tests we don't
 * want System.exit to have any effect.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class SystemExiter {
    private static Exiter exiter;

    private static final AtomicBoolean logged = new AtomicBoolean(false);

    public static void initialize(Exiter exiter) {
        SystemExiter.exiter = exiter;
    }

    /**
     * Equivalent to {@link #logAndExit(ExitLogger, int)} but with an ExitLogger that
     * writes an "Aborting..." message to the log. So this method should only be
     * used for unexpected exits.
     *
     * @param status the status code to provide to the exiter
     */
    public static void abort(final int status) {
        logAndExit(new ExitLogger() {
            @Override
            public void logExit() {
                ServerLogger.ROOT_LOGGER.aborting(status);
            }
        }, status);
    }

    public static void safeAbort() {
        logAndExit(new ExitLogger() {
            @Override
            public void logExit() {
                // no-op
            }
        }, 0);
    }

    /**
     * Invokes the exit logger if and only if no ExitLogger was previously invoked.
     * @param logger the logger. Cannot be {@code null}
     */
    public static void logBeforeExit(ExitLogger logger) {
        try {
            if (logged.compareAndSet(false, true)) {
                logger.logExit();
            }
        } catch (Throwable ignored){
            // ignored
        }
    }

    /**
     * Calls {@link #logBeforeExit(ExitLogger)} and then invokes the {@link Exiter}.
     * @param logger logger the logger. Cannot be {@code null}
     * @param status the status code to provide to the exiter
     */
    public static void logAndExit(ExitLogger logger, int status) {
        logBeforeExit(logger);
        getExiter().exit(status);
        // If we get here, the exiter didn't really exit. So we
        // must be embedded or something, or perhaps in a test case
        // Reset the logged flag so we can log again the next time
        // someone "exits"
        logged.set(false);
    }

    private static Exiter getExiter() {
        return exiter == null ? Exiter.DEFAULT : exiter;
    }

    public interface Exiter {
        Exiter DEFAULT = new DefaultExiter();
        void exit(int status);
    }

    private static class DefaultExiter implements Exiter{
        public void exit(int status) {
            System.exit(status);
        }
    }

    public interface ExitLogger {
        void logExit();
    }
}
