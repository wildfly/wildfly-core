/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.logmanager;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.resolvers.FileResolver;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.config.ErrorManagerConfiguration;
import org.jboss.logmanager.config.FilterConfiguration;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.jboss.logmanager.config.PojoConfiguration;

/**
 * Persists the {@literal logging.properties} file.
 * <p/>
 * Commits any changes remaining on the {@link LogContextConfiguration} and writes out the
 * configuration to the configuration file.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) re-write all of this
public class ConfigurationPersistence extends LogContextConfiguration {

    private static final Object LOCK = new Object();
    private static final String PROPERTIES_FILE = "logging.properties";
    private static final byte[] NOTE_MESSAGE = String.format("# Note this file has been generated and will be overwritten if a%n" +
            "# logging subsystem has been defined in the XML configuration.%n%n").getBytes(StandardCharsets.UTF_8);
    private final LogContextConfiguration config;

    private ConfigurationPersistence(final LogContextConfiguration config) {
        super(config);
        this.config = config;
    }

    /**
     * Gets the property configurator. If the {@link ConfigurationPersistence} does not exist a new one is created.
     *
     * @return the property configurator
     */
    public static ConfigurationPersistence getOrCreateConfigurationPersistence() {
        return getOrCreateConfigurationPersistence(LogContext.getLogContext());
    }

    /**
     * Gets the property configurator. If the {@link ConfigurationPersistence} does not exist a new one is created.
     *
     * @param logContext the log context used to find the property configurator or to attach it to.
     *
     * @return the property configurator
     */
    public static ConfigurationPersistence getOrCreateConfigurationPersistence(final LogContext logContext) {
        // TODO (jrp) fix this
        return getConfigurationPersistence(logContext);
    }

    /**
     * Gets the property configurator. If the {@link ConfigurationPersistence} does not exist a {@code null} is
     * returned.
     *
     * @param logContext the log context used to find the property configurator or to attach it to.
     *
     * @return the property configurator or {@code null}
     */
    public static ConfigurationPersistence getConfigurationPersistence(final LogContext logContext) {
        if (logContext == null) return null;
        // TODO (jrp) fix this
        return new ConfigurationPersistence(LogContextConfiguration.getInstance(logContext));
    }

    private static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Throwable t) {
            LoggingLogger.ROOT_LOGGER.failedToCloseResource(t, closeable);
        }
    }

    @Override
    public LogContext getLogContext() {
        synchronized (LOCK) {
            return config.getLogContext();
        }
    }

    @Override
    public LoggerConfiguration addLoggerConfiguration(final String loggerName) {
        synchronized (LOCK) {
            return config.addLoggerConfiguration(loggerName);
        }
    }

    @Override
    public boolean removeLoggerConfiguration(final String loggerName) {
        synchronized (LOCK) {
            return config.removeLoggerConfiguration(loggerName);
        }
    }

    @Override
    public LoggerConfiguration getLoggerConfiguration(final String loggerName) {
        synchronized (LOCK) {
            return config.getLoggerConfiguration(loggerName);
        }
    }

    @Override
    public List<String> getLoggerNames() {
        synchronized (LOCK) {
            return config.getLoggerNames();
        }
    }

    @Override
    public HandlerConfiguration addHandlerConfiguration(final String moduleName, final String className, final String handlerName, final String... constructorProperties) {
        synchronized (LOCK) {
            return config.addHandlerConfiguration(moduleName, className, handlerName, constructorProperties);
        }
    }

    @Override
    public boolean removeHandlerConfiguration(final String handlerName) {
        synchronized (LOCK) {
            return config.removeHandlerConfiguration(handlerName);
        }
    }

    @Override
    public HandlerConfiguration getHandlerConfiguration(final String handlerName) {
        synchronized (LOCK) {
            return config.getHandlerConfiguration(handlerName);
        }
    }

    @Override
    public List<String> getHandlerNames() {
        synchronized (LOCK) {
            return config.getHandlerNames();
        }
    }

    @Override
    public FormatterConfiguration addFormatterConfiguration(final String moduleName, final String className, final String formatterName, final String... constructorProperties) {
        synchronized (LOCK) {
            return config.addFormatterConfiguration(moduleName, className, formatterName, constructorProperties);
        }
    }

    @Override
    public boolean removeFormatterConfiguration(final String formatterName) {
        synchronized (LOCK) {
            return config.removeFormatterConfiguration(formatterName);
        }
    }

    @Override
    public FormatterConfiguration getFormatterConfiguration(final String formatterName) {
        synchronized (LOCK) {
            return config.getFormatterConfiguration(formatterName);
        }
    }

    @Override
    public List<String> getFormatterNames() {
        synchronized (LOCK) {
            return config.getFormatterNames();
        }
    }

    @Override
    public FilterConfiguration addFilterConfiguration(final String moduleName, final String className, final String filterName, final String... constructorProperties) {
        synchronized (LOCK) {
            return config.addFilterConfiguration(moduleName, className, filterName, constructorProperties);
        }
    }

    @Override
    public boolean removeFilterConfiguration(final String filterName) {
        synchronized (LOCK) {
            return config.removeFilterConfiguration(filterName);
        }
    }

    @Override
    public FilterConfiguration getFilterConfiguration(final String filterName) {
        synchronized (LOCK) {
            return config.getFilterConfiguration(filterName);
        }
    }

    @Override
    public List<String> getFilterNames() {
        synchronized (LOCK) {
            return config.getFilterNames();
        }
    }

    @Override
    public ErrorManagerConfiguration addErrorManagerConfiguration(final String moduleName, final String className, final String errorManagerName, final String... constructorProperties) {
        synchronized (LOCK) {
            return config.addErrorManagerConfiguration(moduleName, className, errorManagerName, constructorProperties);
        }
    }

    @Override
    public boolean removeErrorManagerConfiguration(final String errorManagerName) {
        synchronized (LOCK) {
            return config.removeErrorManagerConfiguration(errorManagerName);
        }
    }

    @Override
    public ErrorManagerConfiguration getErrorManagerConfiguration(final String errorManagerName) {
        synchronized (LOCK) {
            return config.getErrorManagerConfiguration(errorManagerName);
        }
    }

    @Override
    public List<String> getErrorManagerNames() {
        synchronized (LOCK) {
            return config.getErrorManagerNames();
        }
    }

    @Override
    public void prepare() {
        synchronized (LOCK) {
            config.prepare();
        }
    }

    @Override
    public PojoConfiguration addPojoConfiguration(final String moduleName, final String className, final String pojoName, final String... constructorProperties) {
        synchronized (LOCK) {
            return config.addPojoConfiguration(moduleName, className, pojoName, constructorProperties);
        }
    }

    @Override
    public boolean removePojoConfiguration(final String pojoName) {
        synchronized (LOCK) {
            return config.removePojoConfiguration(pojoName);
        }
    }

    @Override
    public PojoConfiguration getPojoConfiguration(final String pojoName) {
        synchronized (LOCK) {
            return config.getPojoConfiguration(pojoName);
        }
    }

    @Override
    public List<String> getPojoNames() {
        synchronized (LOCK) {
            return config.getPojoNames();
        }
    }

    @Override
    public void commit() {
        synchronized (LOCK) {
            config.commit();
        }
    }

    @Override
    public void forget() {
        synchronized (LOCK) {
            config.forget();
        }
    }

    /**
     * Rolls back the runtime changes.
     */
    public void rollback() {
        forget();
    }

    /**
     * Get the log context configuration.
     * <p/>
     * <em>WARNING</em>: this instance is not thread safe in any way.  The returned object should never be used from
     * more than one thread at a time; furthermore the {@link #writeConfiguration(OperationContext)} method also
     * accesses this object directly.
     *
     * @return the log context configuration instance
     */
    public LogContextConfiguration getLogContextConfiguration() {
        return this;
    }

    /**
     * Write the logging configuration to the {@code logging.properties} file.
     *
     * @param context the context used to determine the file location.
     */
    public void writeConfiguration(final OperationContext context) {
        final String loggingConfig;
        switch (context.getProcessType()) {
            case DOMAIN_SERVER: {
                loggingConfig = FileResolver.resolvePath(context, "jboss.server.data.dir", PROPERTIES_FILE);
                break;
            }
            case STANDALONE_SERVER: {
                loggingConfig = FileResolver.resolvePath(context, "jboss.server.config.dir", PROPERTIES_FILE);
                break;
            }
            default: {
                /*
                For every case other than domain or standalone servers, (embedded, appclient etc.) this leave loggingConfig
                unset and returns. Allowing the config to be set here, will result in the respective {standalone|domain}/configuration/
                logging.properties being overwritten with a basic empty logging template, which may cause unexpected and missing logging
                output on the next startup. (This is caused in the embedded case by nothing being committed on the LogContext being used.)
                */
                return;
            }
        }
        if (loggingConfig == null) {
            LoggingLogger.ROOT_LOGGER.warn(LoggingLogger.ROOT_LOGGER.pathManagerServiceNotStarted());
        } else {
            final File configFile = new File(loggingConfig);
            synchronized (LOCK) {
                try {
                    // Commit the log context configuration
                    commit();
                    FileOutputStream out = null;
                    try {
                        out = new FileOutputStream(configFile);
                        out.write(NOTE_MESSAGE);
                        final PropertyConfigurator config = new PropertyConfigurator(this.config);
                        config.writeConfiguration(out, false);
                        LoggingLogger.ROOT_LOGGER.tracef("Logging configuration file '%s' successfully written.", configFile.getAbsolutePath());
                    } catch (IOException e) {
                        throw LoggingLogger.ROOT_LOGGER.failedToWriteConfigurationFile(e, configFile);
                    } finally {
                        safeClose(out);
                    }
                } finally {
                    forget();
                }
            }
        }
    }
}
