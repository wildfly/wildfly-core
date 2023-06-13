/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.StandardOutputStreams;
import org.jboss.logmanager.configuration.PropertyLogContextConfigurator;

/**
 * A utility to parse a {@code logging.properties} file and configure a {@link LogContext}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) rename this and make it private access as only the LogContextConfiguration should use it.
@SuppressWarnings("WeakerAccess")
public class PropertyLogContextConfiguration {

    private static final String[] EMPTY_STRINGS = new String[0];
    private final LogContextConfiguration configuration;
    private final Properties properties;

    private PropertyLogContextConfiguration(final LogContextConfiguration configuration, final Properties properties) {
        this.configuration = configuration;
        this.properties = properties;
    }

    /**
     * Configures the {@link LogContext} based on the properties.
     *
     * @param configuration the configuration to configure the properties on
     *
     * @return the context configuration for the properties
     */
    public static LogContextConfiguration configure(final LogContextConfiguration configuration, final InputStream inputStream) {
        final InputStream configIn = inputStream != null ? inputStream : findConfiguration();
        if (configIn != null) {
            final Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(configIn, StandardCharsets.UTF_8)) {
                properties.load(reader);
                return configure(configuration, properties);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return configuration;
    }

    /**
     * Configures the {@link LogContext} based on the properties.
     *
     * @param configuration the configuration to configure the properties on
     * @param properties    the properties used to configure the log context
     *
     * @return the context configuration for the properties
     */
    public static LogContextConfiguration configure(final LogContextConfiguration configuration, final Properties properties) {
        final PropertyLogContextConfiguration config = new PropertyLogContextConfiguration(
                Objects.requireNonNull(configuration),
                Objects.requireNonNull(properties));
        config.doConfigure();
        return configuration;
    }

    private void doConfigure() {
        try {
            // POJO's must be configured first so other
            for (String pojoName : getStringCsvArray("pojos")) {
                configurePojos(pojoName);
            }
            // Start with the list of loggers to configure.  The root logger is always on the list.
            configureLogger("");
            // And, for each logger name, configure any filters, handlers, etc.
            for (String loggerName : getStringCsvArray("loggers")) {
                configureLogger(loggerName);
            }
            // Configure any declared handlers.
            for (String handlerName : getStringCsvArray("handlers")) {
                configureHandler(handlerName);
            }
            // Configure any declared filters.
            for (String filterName : getStringCsvArray("filters")) {
                configureFilter(filterName);
            }
            // Configure any declared formatters.
            for (String formatterName : getStringCsvArray("formatters")) {
                configureFormatter(formatterName);
            }
            // Configure any declared error managers.
            for (String errorManagerName : getStringCsvArray("errorManagers")) {
                configureErrorManager(errorManagerName);
            }
            configuration.commit();
        } finally {
            configuration.forget();
        }
    }

    @SuppressWarnings({"ConstantConditions"})
    private void configureLogger(final String loggerName) {
        if (configuration.getLoggerConfiguration(loggerName) != null) {
            // already configured!
            return;
        }

        final LoggerConfiguration loggerConfig = configuration.addLoggerConfiguration(loggerName);
        // Get logger level
        final String levelName = getStringProperty(getKey("logger", loggerName, "level"));
        if (levelName != null) {
            loggerConfig.setLevel(levelName);
        }

        // Get logger filters
        final String filterName = getStringProperty(getKey("logger", loggerName, "filter"));
        if (filterName != null) {
            // TODO (jrp) this is not really the best way to handle filters -
            // the trouble is the filter could be an expression, match("value"), or a defined filter
            loggerConfig.setFilter(filterName);
            final String resolvedFilter = loggerConfig.getFilterValueExpression().getResolvedValue();
            // Check for a filter class
            final String filterClassName = getStringProperty(getKey("filter", resolvedFilter));
            // If the filter class is null, assume it's a filter expression
            if (filterClassName != null) {
                configureFilter(resolvedFilter);
            }
        }

        // Get logger handlers
        final String[] handlerNames = getStringCsvArray(getKey("logger", loggerName, "handlers"));
        for (String name : handlerNames) {
            if (configureHandler(name)) {
                loggerConfig.addHandlerName(name);
            }
        }

        // Get logger properties
        final String useParentHandlersString = getStringProperty(getKey("logger", loggerName, "useParentHandlers"));
        if (useParentHandlersString != null) {
            loggerConfig.setUseParentHandlers(useParentHandlersString);
        }
        final String useParentFiltersString = getStringProperty(getKey("logger", loggerName, "useParentFilters"));
        if (useParentFiltersString != null) {
            loggerConfig.setUseParentFilters(useParentFiltersString);
        }
    }

    private boolean configureHandler(final String handlerName) {
        if (configuration.getHandlerConfiguration(handlerName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(getKey("handler", handlerName), true);
        if (className == null) {
            StandardOutputStreams.printError("Handler %s is not defined%n", handlerName);
            return false;
        }
        final HandlerConfiguration handlerConfig = configuration.addHandlerConfiguration(
                getStringProperty(getKey("handler", handlerName, "module")),
                className,
                handlerName,
                getStringCsvArray(getKey("handler", handlerName, "constructorProperties"))
        );

        // Configure the properties
        final String encoding = getStringProperty(getKey("handler", handlerName, "encoding"));
        if (encoding != null) {
            handlerConfig.setEncoding(encoding);
        }

        final String filter = getStringProperty(getKey("handler", handlerName, "filter"));
        if (filter != null) {
            // TODO (jrp) this is not really the best way to handle filters -
            // the trouble is the filter could be an expression, match("value"), or a defined filter
            handlerConfig.setFilter(filter);
            final String resolvedFilter = handlerConfig.getFilterValueExpression().getResolvedValue();
            // Check for a filter class
            final String filterClassName = getStringProperty(getKey("filter", resolvedFilter));
            // If the filter class is null, assume it's a filter expression
            if (filterClassName != null) {
                configureFilter(resolvedFilter);
            }
        }
        final String levelName = getStringProperty(getKey("handler", handlerName, "level"));
        if (levelName != null) {
            handlerConfig.setLevel(levelName);
        }
        final String formatterName = getStringProperty(getKey("handler", handlerName, "formatter"));
        if (formatterName != null) {
            if (configureFormatter(formatterName)) {
                handlerConfig.setFormatterName(formatterName);
            }
        }
        final String errorManagerName = getStringProperty(getKey("handler", handlerName, "errorManager"));
        if (errorManagerName != null) {
            if (configureErrorManager(errorManagerName)) {
                handlerConfig.setErrorManagerName(errorManagerName);
            }
        }

        final String[] handlerNames = getStringCsvArray(getKey("handler", handlerName, "handlers"));
        for (String name : handlerNames) {
            if (configureHandler(name)) {
                handlerConfig.addHandlerName(name);
            }
        }
        final String[] postConfigurationMethods = getStringCsvArray(getKey("handler", handlerName, "postConfiguration"));
        handlerConfig.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(handlerConfig, "handler", handlerName);
        return true;
    }

    private boolean configureFormatter(final String formatterName) {
        if (configuration.getFormatterConfiguration(formatterName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(getKey("formatter", formatterName), true);
        if (className == null) {
            StandardOutputStreams.printError("Formatter %s is not defined%n", formatterName);
            return false;
        }
        final FormatterConfiguration formatterConfig = configuration.addFormatterConfiguration(
                getStringProperty(getKey("formatter", formatterName, "module")),
                className,
                formatterName,
                getStringCsvArray(getKey("formatter", formatterName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(getKey("formatter", formatterName, "postConfiguration"));
        formatterConfig.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(formatterConfig, "formatter", formatterName);
        return true;
    }

    private boolean configureErrorManager(final String errorManagerName) {
        if (configuration.getErrorManagerConfiguration(errorManagerName) != null) {
            // already configured!
            return true;
        }
        final String className = getStringProperty(getKey("errorManager", errorManagerName), true);
        if (className == null) {
            StandardOutputStreams.printError("Error manager %s is not defined%n", errorManagerName);
            return false;
        }
        final ErrorManagerConfiguration errorManagerConfig = configuration.addErrorManagerConfiguration(
                getStringProperty(getKey("errorManager", errorManagerName, "module")),
                className,
                errorManagerName,
                getStringCsvArray(getKey("errorManager", errorManagerName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(getKey("errorManager", errorManagerName, "postConfiguration"));
        errorManagerConfig.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(errorManagerConfig, "errorManager", errorManagerName);
        return true;
    }

    private boolean configureFilter(final String filterName) {
        if (configuration.getFilterConfiguration(filterName) != null) {
            return true;
        }
        final String className = getStringProperty(getKey("filter", filterName));
        if (className == null) {
            StandardOutputStreams.printError("Filter %s is not defined%n", filterName);
            return false;
        }
        final FilterConfiguration filterConfig = configuration.addFilterConfiguration(
                getStringProperty(getKey("filter", filterName, "module")),
                className,
                filterName,
                getStringCsvArray(getKey("filter", filterName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(getKey("filter", filterName, "postConfiguration"));
        filterConfig.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(filterConfig, "filter", filterName);
        return true;
    }

    private void configurePojos(final String pojoName) {
        if (configuration.getPojoConfiguration(pojoName) != null) {
            // already configured!
            return;
        }
        final String className = getStringProperty(getKey("pojo", pojoName), true);
        if (className == null) {
            StandardOutputStreams.printError("POJO %s is not defined%n", pojoName);
            return;
        }
        final PojoConfiguration pojoConfig = configuration.addPojoConfiguration(
                getStringProperty(getKey("pojo", pojoName, "module")),
                getStringProperty(getKey("pojo", pojoName)),
                pojoName,
                getStringCsvArray(getKey("pojo", pojoName, "constructorProperties")));
        final String[] postConfigurationMethods = getStringCsvArray(getKey("pojo", pojoName, "postConfiguration"));
        pojoConfig.setPostConfigurationMethods(postConfigurationMethods);
        configureProperties(pojoConfig, "pojo", pojoName);
    }

    private String getStringProperty(final String key) {
        return getStringProperty(key, true);
    }

    private String getStringProperty(final String key, final boolean trim) {
        String value = properties.getProperty(key);
        if (value != null && trim) {
            value = value.trim();
        }
        return value;
    }

    private String[] getStringCsvArray(final String key) {
        final String property = properties.getProperty(key, "");
        if (property == null) {
            return EMPTY_STRINGS;
        }
        final String value = property.trim();
        if (value.isEmpty()) {
            return EMPTY_STRINGS;
        }
        return value.split("\\s*,\\s*");
    }

    private void configureProperties(final PropertyConfigurable config, final String prefix, final String name) {
        // Next configure setter properties
        final String[] propertyNames = getStringCsvArray(getKey(prefix, name, "properties"));
        for (String propertyName : propertyNames) {
            final String valueString = getStringProperty(getKey(prefix, name, propertyName), false);
            if (valueString != null)
                config.setPropertyValueExpression(propertyName, valueString);
        }
    }

    private static String getKey(final String prefix, final String objectName) {
        return !objectName.isEmpty() ? prefix + "." + objectName : prefix;
    }

    private static String getKey(final String prefix, final String objectName, final String key) {
        return !objectName.isEmpty() ? prefix + "." + objectName + "." + key : prefix + "." + key;
    }

    private static InputStream findConfiguration() {
        final String propLoc = System.getProperty("logging.configuration");
        if (propLoc != null)
            try {
                return new URL(propLoc).openStream();
            } catch (IOException e) {
                StandardOutputStreams.printError("Unable to read the logging configuration from '%s' (%s)%n", propLoc, e);
            }
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null)
            try {
                final InputStream stream = tccl.getResourceAsStream("logging.properties");
                if (stream != null)
                    return stream;
            } catch (Exception ignore) {
            }
        return PropertyLogContextConfigurator.class.getResourceAsStream("logging.properties");
    }
}
