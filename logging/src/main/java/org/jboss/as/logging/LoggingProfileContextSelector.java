/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.logmanager.LogContext;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class LoggingProfileContextSelector {
    private static final LoggingProfileContextSelector INSTANCE = new LoggingProfileContextSelector();

    private final ConcurrentMap<String, LogContext> profileContexts = new ConcurrentHashMap<>();

    private LoggingProfileContextSelector() {

    }

    public static LoggingProfileContextSelector getInstance() {
        return INSTANCE;
    }

    /**
     * Get or create the log context based on the logging profile.
     *
     * @param loggingProfile the logging profile to get or create the log context for
     *
     * @return the log context that was found or a new log context
     */
    protected LogContext getOrCreate(final String loggingProfile) {
        LogContext result = profileContexts.get(loggingProfile);
        if (result == null) {
            result = LogContext.create();
            final LogContext current = profileContexts.putIfAbsent(loggingProfile, result);
            if (current != null) {
                result = current;
            }
        }
        return result;
    }

    /**
     * Returns the log context associated with the logging profile or {@code null} if the logging profile does not have
     * an associated log context.
     *
     * @param loggingProfile the logging profile associated with the log context
     *
     * @return the log context or {@code null} if the logging profile is not associated with a log context
     */
    public LogContext get(final String loggingProfile) {
        return profileContexts.get(loggingProfile);
    }

    /**
     * Checks to see if the logging profile has a log context associated with it.
     *
     * @param loggingProfile the logging profile to check
     *
     * @return {@code true} if the logging profile has an associated log context, otherwise {@code false}
     */
    public boolean exists(final String loggingProfile) {
        return profileContexts.containsKey(loggingProfile);
    }

    /**
     * Removes the associated log context from the logging profile.
     *
     * @param loggingProfile the logging profile associated with the log context
     *
     * @return the log context that was removed or {@code null} if there was no log context associated
     */
    public LogContext remove(final String loggingProfile) {
        return profileContexts.remove(loggingProfile);
    }
}
