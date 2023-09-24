/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.LogManager;

import org.jboss.as.cli.impl.CliLauncher;
import org.jboss.logging.Logger;
import org.jboss.logmanager.Configurator;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;

/**
*
* @author Alexey Loubyansky
*/
public class CommandLineMain {

    public static void main(String[] args) throws Exception {
        configureLogManager(args);
        createClientMarker();
        CliLauncher.main(args);
    }

    private static void configureLogManager(final String[] args) {
        // Note that we can't use the WildFlySecurityManager here as it's possible it could use a logger before we
        // setup the log manager. This is the reason for the getSystemProperty() and setSystemProperty() methods.

        // If the property is already set, we don't want to replace it
        if (getSystemProperty("java.util.logging.manager") == null) {
            try {
                setSystemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
                String logLevel = parseValue(getSystemProperty("jboss.cli.log.level"));
                for (String arg : args) {
                    if (arg.startsWith("-Djboss.cli.log.level")) {
                        logLevel = parseValue(arg);
                    } else if (arg.startsWith("-Dlogging.configuration")) {
                        setSystemProperty("logging.configuration", parseValue(arg));
                    }
                }
                // The log level has not been set, no need to continue
                if (logLevel == null) return;
                // Configure the log manager
                final LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof org.jboss.logmanager.LogManager) {
                    // Attempt to configure based on defaults
                    logManager.readConfiguration();
                    // If configured a Configurator will be on the root logger
                    if (LogContext.getSystemLogContext().getAttachment("", Configurator.ATTACHMENT_KEY) == null) {
                        if (!"OFF".equalsIgnoreCase(logLevel)) {
                            try {
                                final PropertyConfigurator configurator = new PropertyConfigurator();
                                // Get the root logger and attach the configurator, note we don't need to be concerned with security exceptions
                                // as the logManager.readConfiguration() will have already failed the check
                                final Configurator appearing = LogContext.getSystemLogContext().getLogger("").attachIfAbsent(Configurator.ATTACHMENT_KEY, configurator);
                                if (appearing == null) {
                                    configurator.configure(createLogManagerConfig(logLevel));
                                }
                            } catch (IOException e) {
                                System.err.println("ERROR: Could not configure LogManager");
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (SecurityException e) {
                System.err.println("ERROR: Could not configure LogManager");
                e.printStackTrace();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String getSystemProperty(final String key) {
        if (System.getSecurityManager() == null) {
            return System.getProperty(key);
        }
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getProperty(key);
            }
        });
    }

    private static void setSystemProperty(final String key, final String value) {
        if (System.getSecurityManager() == null) {
            System.setProperty(key, value);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    System.setProperty(key, value);
                    return null;
                }
            });
        }
    }

    private static String parseValue(final String prop) {
        if (prop != null) {
            final int index = prop.indexOf('=');
            if (index > 0 && prop.length() > (index + 1)) {
                return prop.substring(index + 1, prop.length());
            }
        }
        return null;
    }

    private static Properties createLogManagerConfig(final String level) {
        final Properties properties = new Properties();
        // Root log level
        properties.setProperty("logger.level", level.toUpperCase(Locale.ENGLISH));
        properties.setProperty("logger.handlers", "FILE");

        // Configure the handler
        properties.setProperty("handler.FILE", "org.jboss.logmanager.handlers.FileHandler");
        properties.setProperty("handler.FILE.properties", "autoFlush,append,fileName,enabled");
        properties.setProperty("handler.FILE.constructorProperties", "fileName,append");
        properties.setProperty("handler.FILE.append", "true");
        properties.setProperty("handler.FILE.autoFlush", "true");
        properties.setProperty("handler.FILE.fileName", "${jboss.cli.log.file:jboss-cli.log}");
        properties.setProperty("handler.FILE.formatter", "PATTERN");

        // Configure the formatter
        properties.setProperty("formatter.PATTERN", "org.jboss.logmanager.formatters.PatternFormatter");
        properties.setProperty("formatter.PATTERN.pattern", "%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n");
        properties.setProperty("formatter.PATTERN.properties", "pattern");
        return properties;
    }

    /**
     * Creates a file marker under $JBOSS_HOME/bin directory. This is used by the shutdown operation to understand from where we have
     * launched CLI instance when we are performing an update of a remote installation.
     */
    private static void createClientMarker() {
        try {
            final String jbossHome = System.getenv("JBOSS_HOME");
            if (jbossHome != null) {
                final Path cliMarkerPath = Paths.get(jbossHome).resolve("bin").resolve(Util.CLI_MARKER);
                Files.deleteIfExists(cliMarkerPath);
                Files.createFile(cliMarkerPath);

                final File cliMarkerFile = cliMarkerPath.toFile();
                cliMarkerFile.deleteOnExit();
                final String data = String.valueOf(System.currentTimeMillis());
                try (FileWriter writer = new FileWriter(cliMarkerFile, true)) {
                    writer.write(data);
                }
                System.setProperty(Util.CLI_MARKER_VALUE, data);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Logger.getLogger(CommandLineMain.class).debug("Creation of cli marker failed and will be ignored.", e);
        }
    }
}
