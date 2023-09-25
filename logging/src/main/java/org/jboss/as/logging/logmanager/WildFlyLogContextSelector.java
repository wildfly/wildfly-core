/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.logmanager;

import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;

import org.jboss.logmanager.Configurator;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.logmanager.config.LogContextConfiguration;

/**
 * The log context selector to use for the WildFly logging extension.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface WildFlyLogContextSelector extends LogContextSelector {

    /**
     * Get and set the log context.
     *
     * @param newValue the new log context value, or {@code null} to clear
     *
     * @return the previous log context value, or {@code null} if none was set
     *
     * @see org.jboss.logmanager.ThreadLocalLogContextSelector#getAndSet(Object, org.jboss.logmanager.LogContext)
     */
    LogContext setLocalContext(LogContext newValue);

    /**
     * Register a class loader with a log context.
     *
     * @param classLoader the class loader
     * @param logContext  the log context
     *
     * @throws IllegalArgumentException if the class loader is already associated with a log context
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#registerLogContext(ClassLoader,
     * org.jboss.logmanager.LogContext)
     */
    void registerLogContext(ClassLoader classLoader, LogContext logContext);

    /**
     * Unregister a class loader/log context association.
     *
     * @param classLoader the class loader
     * @param logContext  the log context
     *
     * @return {@code true} if the association exists and was removed, {@code false} otherwise
     *
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#unregisterLogContext(ClassLoader,
     * org.jboss.logmanager.LogContext)
     */
    boolean unregisterLogContext(ClassLoader classLoader, LogContext logContext);

    /**
     * Register a class loader which is a known log API, and thus should be skipped over when searching for the
     * log context to use for the caller class.
     *
     * @param apiClassLoader the API class loader
     *
     * @return {@code true} if this class loader was previously unknown, or {@code false} if it was already
     * registered
     *
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#addLogApiClassLoader(ClassLoader)
     */
    boolean addLogApiClassLoader(ClassLoader apiClassLoader);

    /**
     * Remove a class loader from the known log APIs set.
     *
     * @param apiClassLoader the API class loader
     *
     * @return {@code true} if the class loader was removed, or {@code false} if it was not known to this selector
     *
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#removeLogApiClassLoader(ClassLoader)
     */
    boolean removeLogApiClassLoader(ClassLoader apiClassLoader);

    /**
     * Returns the number of registered {@link org.jboss.logmanager.LogContext log contexts}.
     *
     * @return the number of registered log contexts
     */
    int registeredCount();

    class Factory {
        private static final LogContext EMBEDDED_LOG_CONTEXT = LogContext.create();

        /**
         * Creates a new selector which wraps the current {@linkplain LogContext#getLogContextSelector() selector}.
         *
         * @return a new selector that wraps the current selector
         */
        public static WildFlyLogContextSelector create() {
            // Wrap the current LogContextSelector. This will be used as the default in the cases where this selector
            // does not find a log context.
            return new WildFlyLogContextSelectorImpl(LogContext.getLogContextSelector());
        }

        /**
         * Creates a new selector which by default returns a static embedded context which can be used.
         *
         * @return a new selector
         */
        public static WildFlyLogContextSelector createEmbedded() {
            clearLogContext();
            return new WildFlyLogContextSelectorImpl(EMBEDDED_LOG_CONTEXT);
        }

        private static void clearLogContext() {
            // Remove the configurator and clear the log context
            final Configurator configurator = EMBEDDED_LOG_CONTEXT.getLogger("").detach(Configurator.ATTACHMENT_KEY);
            // If this was a PropertyConfigurator we can use the LogContextConfiguration API to tear down the LogContext
            if (configurator instanceof PropertyConfigurator) {
                final LogContextConfiguration logContextConfiguration = ((PropertyConfigurator) configurator).getLogContextConfiguration();
                clearLogContext(logContextConfiguration);
            } else if (configurator instanceof LogContextConfiguration) {
                clearLogContext((LogContextConfiguration) configurator);
            } else {
                // Remove all the handlers and close them as well as reset the loggers
                final List<String> loggerNames = Collections.list(EMBEDDED_LOG_CONTEXT.getLoggerNames());
                for (String name : loggerNames) {
                    final Logger logger = EMBEDDED_LOG_CONTEXT.getLoggerIfExists(name);
                    if (logger != null) {
                        final Handler[] handlers = logger.clearHandlers();
                        if (handlers != null) {
                            for (Handler handler : handlers) {
                                handler.close();
                            }
                        }
                        logger.setFilter(null);
                        logger.setUseParentFilters(false);
                        logger.setUseParentHandlers(true);
                        logger.setLevel(Level.INFO);
                    }
                }
            }
        }

        private static void clearLogContext(final LogContextConfiguration logContextConfiguration) {
            try {
                // Remove all the loggers
                for (String name : logContextConfiguration.getLoggerNames()) {
                    logContextConfiguration.removeLoggerConfiguration(name);
                }
                // Remove all the handlers
                for (String name : logContextConfiguration.getHandlerNames()) {
                    logContextConfiguration.removeHandlerConfiguration(name);
                }
                // Remove all the formatters
                for (String name : logContextConfiguration.getFormatterNames()) {
                    logContextConfiguration.removeFormatterConfiguration(name);
                }
                // Remove all the error managers
                for (String name : logContextConfiguration.getErrorManagerNames()) {
                    logContextConfiguration.removeErrorManagerConfiguration(name);
                }
                // Remove all the POJO's
                for (String name : logContextConfiguration.getPojoNames()) {
                    logContextConfiguration.removePojoConfiguration(name);
                }
                // Remove all the filters
                for (String name : logContextConfiguration.getFilterNames()) {
                    logContextConfiguration.removeFilterConfiguration(name);
                }
                logContextConfiguration.commit();
            } finally {
                logContextConfiguration.forget();
            }
        }
    }

}
