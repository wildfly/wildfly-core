/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.logmanager;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jboss.logmanager.StandardOutputStreams;
import org.wildfly.core.logmanager.config.ErrorManagerConfiguration;
import org.wildfly.core.logmanager.config.FilterConfiguration;
import org.wildfly.core.logmanager.config.FormatterConfiguration;
import org.wildfly.core.logmanager.config.HandlerConfiguration;
import org.wildfly.core.logmanager.config.LogContextConfiguration;
import org.wildfly.core.logmanager.config.LoggerConfiguration;
import org.wildfly.core.logmanager.config.PojoConfiguration;
import org.wildfly.core.logmanager.config.PropertyConfigurable;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) this should be static and private
public class PropertyConfigurator {
    private static final String NEW_LINE = System.lineSeparator();

    private final LogContextConfiguration config;

    public PropertyConfigurator(final LogContextConfiguration config) {
        this.config = config;
    }

    /**
     * Writes the current configuration to the output stream.
     *
     * <b>Note:</b> the output stream will be closed.
     *
     * @param outputStream     the output stream to write to.
     * @param writeExpressions {@code true} if expressions should be written, {@code false} if the resolved value should
     *                         be written
     *
     * @throws IOException if an error occurs while writing the configuration.
     */
    public void writeConfiguration(final OutputStream outputStream, final boolean writeExpressions) throws IOException {
        try {
            final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            try {
                final Set<String> implicitHandlers = new HashSet<String>();
                final Set<String> implicitFilters = new HashSet<String>();
                final Set<String> implicitFormatters = new HashSet<String>();
                final Set<String> implicitErrorManagers = new HashSet<String>();
                final List<String> loggerNames = config.getLoggerNames();
                writePropertyComment(out, "Additional loggers to configure (the root logger is always configured)");
                writeProperty(out, "loggers", toCsvString(loggerNames));
                final LoggerConfiguration rootLogger = config.getLoggerConfiguration("");
                writeLoggerConfiguration(out, rootLogger, implicitHandlers, implicitFilters, writeExpressions);
                // Remove the root loggers
                loggerNames.remove("");
                for (String loggerName : loggerNames) {
                    writeLoggerConfiguration(out, config.getLoggerConfiguration(loggerName), implicitHandlers, implicitFilters, writeExpressions);
                }
                final List<String> allHandlerNames = config.getHandlerNames();
                final List<String> explicitHandlerNames = new ArrayList<String>(allHandlerNames);
                explicitHandlerNames.removeAll(implicitHandlers);
                if (!explicitHandlerNames.isEmpty()) {
                    writePropertyComment(out, "Additional handlers to configure");
                    writeProperty(out, "handlers", toCsvString(explicitHandlerNames));
                    out.write(NEW_LINE);
                }
                for (String handlerName : allHandlerNames) {
                    writeHandlerConfiguration(out, config.getHandlerConfiguration(handlerName), implicitHandlers, implicitFilters,
                            implicitFormatters, implicitErrorManagers, writeExpressions);
                }
                final List<String> allFilterNames = config.getFilterNames();
                final List<String> explicitFilterNames = new ArrayList<String>(allFilterNames);
                explicitFilterNames.removeAll(implicitFilters);
                if (!explicitFilterNames.isEmpty()) {
                    writePropertyComment(out, "Additional filters to configure");
                    writeProperty(out, "filters", toCsvString(explicitFilterNames));
                    out.write(NEW_LINE);
                }
                for (String filterName : allFilterNames) {
                    writeFilterConfiguration(out, config.getFilterConfiguration(filterName), writeExpressions);
                }
                final List<String> allFormatterNames = config.getFormatterNames();
                final ArrayList<String> explicitFormatterNames = new ArrayList<String>(allFormatterNames);
                explicitFormatterNames.removeAll(implicitFormatters);
                if (!explicitFormatterNames.isEmpty()) {
                    writePropertyComment(out, "Additional formatters to configure");
                    writeProperty(out, "formatters", toCsvString(explicitFormatterNames));
                    out.write(NEW_LINE);
                }
                for (String formatterName : allFormatterNames) {
                    writeFormatterConfiguration(out, config.getFormatterConfiguration(formatterName), writeExpressions);
                }
                final List<String> allErrorManagerNames = config.getErrorManagerNames();
                final ArrayList<String> explicitErrorManagerNames = new ArrayList<String>(allErrorManagerNames);
                explicitErrorManagerNames.removeAll(implicitErrorManagers);
                if (!explicitErrorManagerNames.isEmpty()) {
                    writePropertyComment(out, "Additional errorManagers to configure");
                    writeProperty(out, "errorManagers", toCsvString(explicitErrorManagerNames));
                    out.write(NEW_LINE);
                }
                for (String errorManagerName : allErrorManagerNames) {
                    writeErrorManagerConfiguration(out, config.getErrorManagerConfiguration(errorManagerName), writeExpressions);
                }

                // Write POJO configurations
                final List<String> pojoNames = config.getPojoNames();
                if (!pojoNames.isEmpty()) {
                    writePropertyComment(out, "POJOs to configure");
                    writeProperty(out, "pojos", toCsvString(pojoNames));
                    for (String pojoName : pojoNames) {
                        writePojoConfiguration(out, config.getPojoConfiguration(pojoName), writeExpressions);
                    }
                }

                out.flush();
                out.close();
            } finally {
                safeClose(out);
            }
            outputStream.close();
        } finally {
            safeClose(outputStream);
        }
    }

    private void writeLoggerConfiguration(final Writer out, final LoggerConfiguration logger,
                                          final Set<String> implicitHandlers, final Set<String> implicitFilters,
                                          final boolean writeExpressions) throws IOException {
        if (logger != null) {
            out.write(NEW_LINE);
            final String name = logger.getName();
            final String prefix = name.isEmpty() ? "logger." : "logger." + name + ".";
            final String level = (writeExpressions ? logger.getLevelValueExpression().getValue() : logger.getLevel());
            if (level != null) {
                writeProperty(out, prefix, "level", level);
            }
            final String filterName = (writeExpressions ? logger.getFilterValueExpression()
                    .getValue() : logger.getFilter());
            if (filterName != null) {
                writeProperty(out, prefix, "filter", filterName);
                implicitFilters.add(logger.getFilter());
            }
            final Boolean useParentHandlers = logger.getUseParentHandlers();
            final String useParentHandlersValue = (writeExpressions ? logger.getUseParentHandlersValueExpression()
                    .getValue() :
                    useParentHandlers == null ? null : useParentHandlers.toString());
            if (useParentHandlersValue != null) {
                writeProperty(out, prefix, "useParentHandlers", useParentHandlersValue);
            }
            final List<String> handlerNames = new ArrayList<String>();
            for (String handlerName : logger.getHandlerNames()) {
                if (config.getHandlerNames().contains(handlerName)) {
                    implicitHandlers.add(handlerName);
                    handlerNames.add(handlerName);
                } else {
                    printError("Handler %s is not defined and will not be written to the configuration for logger %s%n", handlerName, (name.isEmpty() ? "ROOT" : name));
                }
            }
            if (!handlerNames.isEmpty()) {
                writeProperty(out, prefix, "handlers", toCsvString(handlerNames));
            }
        }
    }

    private void writeHandlerConfiguration(final Writer out, final HandlerConfiguration handler,
                                           final Set<String> implicitHandlers, final Set<String> implicitFilters,
                                           final Set<String> implicitFormatters, final Set<String> implicitErrorManagers,
                                           final boolean writeExpressions) throws IOException {
        if (handler != null) {
            out.write(NEW_LINE);
            final String name = handler.getName();
            final String prefix = "handler." + name + ".";
            final String className = handler.getClassName();
            writeProperty(out, "handler.", name, className);
            final String moduleName = handler.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final String level = (writeExpressions ? handler.getLevelValueExpression().getValue() : handler.getLevel());
            if (level != null) {
                writeProperty(out, prefix, "level", level);
            }
            final String encoding = (writeExpressions ? handler.getEncodingValueExpression()
                    .getValue() : handler.getEncoding());
            if (encoding != null) {
                writeProperty(out, prefix, "encoding", encoding);
            }
            final String filter = (writeExpressions ? handler.getFilterValueExpression()
                    .getValue() : handler.getFilter());
            if (filter != null) {
                writeProperty(out, prefix, "filter", filter);
                implicitFilters.add(handler.getFilter());
            }
            final String formatterName = (writeExpressions ? handler.getFormatterNameValueExpression()
                    .getValue() : handler.getFormatterName());
            if (formatterName != null) {
                // Make sure the formatter exists
                if (config.getFormatterNames().contains(handler.getFormatterName())) {
                    writeProperty(out, prefix, "formatter", formatterName);
                    implicitFormatters.add(handler.getFormatterName());
                } else {
                    printError("Formatter %s is not defined and will not be written to the configuration for handler %s%n", formatterName, name);
                }
            }
            final String errorManagerName = (writeExpressions ? handler.getErrorManagerNameValueExpression()
                    .getValue() : handler.getErrorManagerName());
            if (errorManagerName != null) {
                // Make sure the error manager exists
                if (config.getErrorManagerNames().contains(handler.getErrorManagerName())) {
                    writeProperty(out, prefix, "errorManager", errorManagerName);
                    implicitErrorManagers.add(handler.getErrorManagerName());
                } else {
                    printError("Error manager %s is not defined and will not be written to the configuration for handler %s%n", errorManagerName, name);
                }
            }
            final List<String> handlerNames = new ArrayList<String>();
            for (String handlerName : handler.getHandlerNames()) {
                if (config.getHandlerNames().contains(handlerName)) {
                    implicitHandlers.add(handlerName);
                    handlerNames.add(handlerName);
                } else {
                    printError("Handler %s is not defined and will not be written to the configuration for handler %s%n", handlerName, name);
                }
            }
            if (!handlerNames.isEmpty()) {
                writeProperty(out, prefix, "handlers", toCsvString(handlerNames));
            }
            final List<String> postConfigurationMethods = handler.getPostConfigurationMethods();
            if (!postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, handler, writeExpressions);
        }
    }

    private static void writeFilterConfiguration(final Writer out, final FilterConfiguration filter, final boolean writeExpressions) throws IOException {
        if (filter != null) {
            out.write(NEW_LINE);
            final String name = filter.getName();
            final String prefix = "filter." + name + ".";
            final String className = filter.getClassName();
            writeProperty(out, "filter.", name, className);
            final String moduleName = filter.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = filter.getPostConfigurationMethods();
            if (!postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, filter, writeExpressions);
        }
    }

    private static void writeFormatterConfiguration(final Writer out, final FormatterConfiguration formatter, final boolean writeExpressions) throws IOException {
        if (formatter != null) {
            out.write(NEW_LINE);
            final String name = formatter.getName();
            final String prefix = "formatter." + name + ".";
            final String className = formatter.getClassName();
            writeProperty(out, "formatter.", name, className);
            final String moduleName = formatter.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = formatter.getPostConfigurationMethods();
            if (!postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, formatter, writeExpressions);
        }
    }

    private static void writeErrorManagerConfiguration(final Writer out, final ErrorManagerConfiguration errorManager, final boolean writeExpressions) throws IOException {
        if (errorManager != null) {
            out.write(NEW_LINE);
            final String name = errorManager.getName();
            final String prefix = "errorManager." + name + ".";
            final String className = errorManager.getClassName();
            writeProperty(out, "errorManager.", name, className);
            final String moduleName = errorManager.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = errorManager.getPostConfigurationMethods();
            if (!postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, errorManager, writeExpressions);
        }
    }

    private static void writePojoConfiguration(final Writer out, final PojoConfiguration pojo, final boolean writeExpressions) throws IOException {
        if (pojo != null) {
            out.write(NEW_LINE);
            final String name = pojo.getName();
            final String prefix = "pojo." + name + ".";
            final String className = pojo.getClassName();
            writeProperty(out, "pojo.", name, className);
            final String moduleName = pojo.getModuleName();
            if (moduleName != null) {
                writeProperty(out, prefix, "module", moduleName);
            }
            final List<String> postConfigurationMethods = pojo.getPostConfigurationMethods();
            if (!postConfigurationMethods.isEmpty()) {
                writeProperty(out, prefix, "postConfiguration", toCsvString(postConfigurationMethods));
            }
            writeProperties(out, prefix, pojo, writeExpressions);
        }
    }

    /**
     * Writes a comment to the print stream. Prepends the comment with a {@code #}.
     *
     * @param out     the print stream to write to.
     * @param comment the comment to write.
     */
    private static void writePropertyComment(final Writer out, final String comment) throws IOException {
        out.write(NEW_LINE);
        out.write("# ");
        out.write(comment);
        out.write(NEW_LINE);
    }

    /**
     * Writes a property to the print stream.
     *
     * @param out   the print stream to write to.
     * @param name  the name of the property.
     * @param value the value of the property.
     */
    private static void writeProperty(final Writer out, final String name, final String value) throws IOException {
        writeProperty(out, null, name, value);
    }

    /**
     * Writes a property to the print stream.
     *
     * @param out    the print stream to write to.
     * @param prefix the prefix for the name or {@code null} to use no prefix.
     * @param name   the name of the property.
     * @param value  the value of the property.
     */
    private static void writeProperty(final Writer out, final String prefix, final String name, final String value) throws IOException {
        if (prefix == null) {
            writeKey(out, name);
        } else {
            writeKey(out, String.format("%s%s", prefix, name));
        }
        writeValue(out, value);
        out.write(NEW_LINE);
    }

    /**
     * Writes a collection of properties to the print stream. Uses the {@link PropertyConfigurable#getPropertyValueString(String)}
     * to extract the value.
     *
     * @param out                  the print stream to write to.
     * @param prefix               the prefix for the name or {@code null} to use no prefix.
     * @param propertyConfigurable the configuration to extract the property value from.
     * @param writeExpression      {@code true} if expressions should be written, {@code false} if the resolved value
     *                             should be written
     */
    private static void writeProperties(final Writer out, final String prefix, final PropertyConfigurable propertyConfigurable, final boolean writeExpression) throws IOException {
        final List<String> names = propertyConfigurable.getPropertyNames();
        if (!names.isEmpty()) {
            final List<String> ctorProps = propertyConfigurable.getConstructorProperties();
            if (prefix == null) {
                writeProperty(out, "properties", toCsvString(names));
                if (!ctorProps.isEmpty()) {
                    writeProperty(out, "constructorProperties", toCsvString(ctorProps));
                }
                for (String name : names) {
                    if (writeExpression) {
                        writeProperty(out, name, propertyConfigurable.getPropertyValueExpression(name).getValue());
                    } else {
                        writeProperty(out, name, propertyConfigurable.getPropertyValueString(name));
                    }
                }
            } else {
                writeProperty(out, prefix, "properties", toCsvString(names));
                if (!ctorProps.isEmpty()) {
                    writeProperty(out, prefix, "constructorProperties", toCsvString(ctorProps));
                }
                for (String name : names) {
                    if (writeExpression) {
                        writeProperty(out, prefix, name, propertyConfigurable.getPropertyValueExpression(name)
                                .getValue());
                    } else {
                        writeProperty(out, prefix, name, propertyConfigurable.getPropertyValueString(name));
                    }
                }
            }
        }
    }

    /**
     * Parses the list and creates a comma delimited string of the names.
     * <p/>
     * <b>Notes:</b> empty names are ignored.
     *
     * @param names the names to process.
     *
     * @return a comma delimited list of the names.
     */
    private static String toCsvString(final List<String> names) {
        final StringBuilder result = new StringBuilder(1024);
        Iterator<String> iterator = names.iterator();
        while (iterator.hasNext()) {
            final String name = iterator.next();
            // No need to write empty names
            if (!name.isEmpty()) {
                result.append(name);
                if (iterator.hasNext()) {
                    result.append(",");
                }
            }
        }
        return result.toString();
    }

    private static void writeValue(final Appendable out, final String value) throws IOException {
        writeSanitized(out, value, false);
    }

    private static void writeKey(final Appendable out, final String key) throws IOException {
        writeSanitized(out, key, true);
        out.append('=');
    }

    private static void writeSanitized(final Appendable out, final String string, final boolean escapeSpaces) throws IOException {
        for (int x = 0; x < string.length(); x++) {
            final char c = string.charAt(x);
            switch (c) {
                case ' ':
                    if (x == 0 || escapeSpaces)
                        out.append('\\');
                    out.append(c);
                    break;
                case '\t':
                    out.append('\\').append('t');
                    break;
                case '\n':
                    out.append('\\').append('n');
                    break;
                case '\r':
                    out.append('\\').append('r');
                    break;
                case '\f':
                    out.append('\\').append('f');
                    break;
                case '\\':
                case '=':
                case ':':
                case '#':
                case '!':
                    out.append('\\').append(c);
                    break;
                default:
                    out.append(c);
            }
        }
    }

    /**
     * Prints the message to stderr.
     *
     * @param format the format of the message
     * @param args   the format arguments
     */
    static void printError(final String format, final Object... args) {
        StandardOutputStreams.printError(format, args);
    }


    private static void safeClose(final Closeable stream) {
        if (stream != null) try {
            stream.close();
        } catch (Exception e) {
            // can't do anything about it
        }
    }
}
