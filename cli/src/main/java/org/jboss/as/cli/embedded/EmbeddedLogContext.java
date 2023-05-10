/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import org.jboss.as.cli.CommandContext;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.configuration.PropertyContextConfiguration;
import org.wildfly.core.embedded.logging.EmbeddedLogger;
import org.wildfly.security.manager.WildFlySecurityManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Used to control the {@linkplain LogContext log context} for embedded servers.
 * <p>
 * A single global log context is used for all embedded servers launched from a session. This is needed as static
 * loggers will be created on a single log context. Reusing the same log context allows these loggers to still work and
 * the log context to be reconfigured based on the embedded server that's booting.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class EmbeddedLogContext {

    private static class Holder {
        static final LogContext LOG_CONTEXT = LogContext.create();
    }


    /**
     * Configures the log context for the server and returns the configured log context.
     *
     * @param logDir             the logging directory, from jboss.server|domain.log.dir standalone default {@code $JBOSS_HOME/standalone/log}
     * @param configDir          the configuration directory from jboss.server|domain.config.dir, standalone default {@code $JBOSS_HOME/standalone/configuration}
     * @param defaultLogFileName the name of the log file to pass to {@code org.jboss.boot.log.file}
     * @param ctx                the command context used to report errors to
     * @return the configured log context
     */
    static synchronized LogContext configureLogContext(final File logDir, final File configDir, final String defaultLogFileName, final CommandContext ctx) {
        final LogContext embeddedLogContext = Holder.LOG_CONTEXT;
        final Path bootLog = logDir.toPath().resolve(Paths.get(defaultLogFileName));
        final Path loggingProperties = configDir.toPath().resolve(Paths.get("logging.properties"));
        if (Files.exists(loggingProperties)) {
            WildFlySecurityManager.setPropertyPrivileged("org.jboss.boot.log.file", bootLog.toAbsolutePath().toString());

            try (final BufferedReader reader = Files.newBufferedReader(loggingProperties, StandardCharsets.UTF_8)) {
                final Properties properties = new Properties();
                properties.load(reader);
                embeddedLogContext.attach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, PropertyContextConfiguration.configure(embeddedLogContext, properties));
            } catch (IOException e) {
                ctx.printLine(String.format("Unable to configure logging from configuration file %s. Reason: %s", loggingProperties, e.getLocalizedMessage()));
            }
        }
        return embeddedLogContext;
    }

    /**
     * Attempts to clear the global log context used for embedded servers.
     */
    static synchronized void clearLogContext() {
        final LogContext embeddedLogContext = Holder.LOG_CONTEXT;
        // Remove the configurator and clear the log context
        final ContextConfiguration configurator = embeddedLogContext.detach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
        if (configurator != null) {
            try {
                configurator.close();
            } catch (Exception e) {
                EmbeddedLogger.ROOT_LOGGER.failedToCloseLogContext(e);
            }
        }
    }
}
