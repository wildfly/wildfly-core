/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

import static org.wildfly.core.jar.runtime.Constants.DEPLOYMENT_ARG;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.jboss.as.controller.persistence.ConfigurationExtensionFactory;

import org.jboss.as.process.CommandLineConstants;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

/**
 *
 * @author jdenise
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class Arguments {
    private static final String JDK_SERIAL_FILTER = "jdk.serialFilter";
    private static final String DISABLE_JDK_SERIAL_FILTER_ENV = "DISABLE_JDK_SERIAL_FILTER";
    private static final String JDK_SERIAL_FILTER_ENV = "JDK_SERIAL_FILTER";
    // This default value comes from the standalone.conf file, in case it is changes, make sure to update this value.
    private static final String DEFAULT_SERIAL_FILTER = "maxbytes=10485760;maxdepth=128;maxarray=100000;maxrefs=300000";

    private Arguments(final BootableEnvironment environment) {
        this.environment = environment;
    }

    private Boolean isHelp;
    private Boolean isVersion;
    private final List<String> serverArguments = new ArrayList<>();
    private final BootableEnvironment environment;
    private Path deployment;
    private Path cliScript;
    private String serialFilter;
    private boolean logSerialFilterAlreadySet;

    static Arguments parseArguments(final List<String> args, final BootableEnvironment environment) throws Exception {
        Objects.requireNonNull(args);
        Arguments arguments = new Arguments(environment);
        arguments.handleArguments(args);
        return arguments;
    }

    private void handleArguments(List<String> args) throws Exception {
        final Map<String, String> systemProperties = new HashMap<>();
        final Iterator<String> iter = args.iterator();
        String jdkSerialFilterSet = null;
        while (iter.hasNext()) {
            final String a = iter.next();
            if (a.startsWith(DEPLOYMENT_ARG)) {
                deployment = checkPath(getValue(a));
            } else if (a.startsWith(CommandLineConstants.PUBLIC_BIND_ADDRESS)) {
                serverArguments.add(a);
            } else if (CommandLineConstants.PROPERTIES.equals(a)) {
                serverArguments.add(a);
                if (iter.hasNext()) {
                    final String urlSpec = iter.next();
                    serverArguments.add(urlSpec);
                    addSystemProperties(makeUrl(urlSpec), systemProperties);
                    jdkSerialFilterSet = systemProperties.get(JDK_SERIAL_FILTER);
                } else {
                    throw BootableJarLogger.ROOT_LOGGER.invalidArgument(a);
                }
            } else if (a.startsWith(CommandLineConstants.PROPERTIES)) {
                serverArguments.add(a);
                // We need these set as system properties early so the log manager can use them
                final String urlSpec = parseValue(a, CommandLineConstants.PROPERTIES);
                addSystemProperties(makeUrl(urlSpec), systemProperties);
                jdkSerialFilterSet = systemProperties.get(JDK_SERIAL_FILTER);
            } else if (a.startsWith(CommandLineConstants.SECURITY_PROP)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.SYS_PROP)) {
                // We want to set the server argument and add as a system property. The reason for this is when if the
                // property already exists on the system-property resource a warning is logged alerting to that. However
                // we also need to set it as a current system property for usage in the log manager.
                serverArguments.add(a);
                addSystemProperty(a, systemProperties);
                jdkSerialFilterSet = systemProperties.get(JDK_SERIAL_FILTER);
            } else if (a.startsWith(CommandLineConstants.START_MODE)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS)) {
                serverArguments.add(a);
            } else if (CommandLineConstants.VERSION.equals(a)) {
                isVersion = true;
                serverArguments.add(a);
            } else if (CommandLineConstants.HELP.equals(a)) {
                isHelp = true;
            } else if (a.startsWith("--cli-script")) {
                cliScript = Paths.get(getValue(a));
                if (!Files.exists(cliScript) || !Files.isReadable(cliScript)) {
                    throw new Exception("File doesn't exist or is not readable: " + cliScript);
                }
             } else if (ConfigurationExtensionFactory.isConfigurationExtensionSupported()
                    && ConfigurationExtensionFactory.commandLineContainsArgument(a)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.STABILITY)) {
                serverArguments.add(a);
            } else {
                throw BootableJarLogger.ROOT_LOGGER.unknownArgument(a);
            }
        }
        // Special handling of jdk.serialFilter
        // The filter could have been set by a system property not handled in the user arguments.
        // We will know after having check for the existence of a filter.
        ObjectInputFilter configuredFilter = ObjectInputFilter.Config.getSerialFilter();
        String filter = System.getenv(JDK_SERIAL_FILTER_ENV);
        String disabled = System.getenv(DISABLE_JDK_SERIAL_FILTER_ENV);
        boolean filterEnabled = disabled == null || !disabled.equalsIgnoreCase("true");
        if (configuredFilter == null) {
            if (jdkSerialFilterSet == null) {
                if (filterEnabled) {
                    if (filter == null) {
                        filter = DEFAULT_SERIAL_FILTER;
                    }
                    serialFilter = filter;
                }
            } else {
                // The filter has been explicitly set, don't keep the system property.
                // The filter will be set by the Bootable JAR runtime.
                serialFilter = jdkSerialFilterSet;
                systemProperties.remove(JDK_SERIAL_FILTER);
            }
        } else if (filter != null && filterEnabled) {
            logSerialFilterAlreadySet = true;
        }
        // Add the system properties to the environment
        environment.setSystemProperties(systemProperties);
    }

    void logArgumentsHandling(BootableJarLogger log) {
        if (logSerialFilterAlreadySet) {
            log.advertiseSerialFilterSet();
        }
    }
    private Path checkPath(String path) {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw BootableJarLogger.ROOT_LOGGER.notExistingFile(path);
        }
        return filePath;
    }

    private static String getValue(String arg) {
        int sep = arg.indexOf("=");
        if (sep == -1 || sep == arg.length() - 1) {
            throw BootableJarLogger.ROOT_LOGGER.invalidArgument(arg);
        }
        return arg.substring(sep + 1);
    }

    /**
     * @return the isHelp
     */
    public Boolean isHelp() {
        return isHelp == null ? false : isHelp;
    }

    /**
     * @return the isVersion
     */
    public Boolean isVersion() {
        return isVersion == null ? false : isVersion;
    }

    /**
     * @return the serverArguments
     */
    public List<String> getServerArguments() {
        return Collections.unmodifiableList(serverArguments);
    }

    /**
     * @return the deployment
     */
    public Path getDeployment() {
        return deployment;
    }

    public Path getCLIScript() {
        return cliScript;
    }

    /**
     * The serialization filter that must be explicitely set.
     * @return The filter, null if no filter to set.
     */
    public String getRequiredSerialFilter() {
        return serialFilter;
    }

    private static void addSystemProperty(final String arg, final Map<String, String> properties) {
        final int i = arg.indexOf('=');
        final String key;
        final String value;
        if (i > 2) {
            key = arg.substring(2, i);
        } else if (i == -1 && arg.length() > 2) {
            key = arg.substring(2);
        } else {
            throw BootableJarLogger.ROOT_LOGGER.invalidArgument(arg);
        }

        // We shouldn't be actually setting these properties
        if (i == -1 || (i + 1) == arg.length()) {
            value = "";
        } else {
            value = arg.substring(i + 1);
        }
        properties.put(key ,value);
    }

    private static void addSystemProperties(final URL url, final Map<String, String> properties) throws IOException {
        final Properties parsed = new Properties();
        try (InputStream in = url.openConnection().getInputStream()) {
            parsed.load(in);
        }
        for (String key : parsed.stringPropertyNames()) {
            properties.put(key, parsed.getProperty(key));
        }
    }

    private static String parseValue(final String arg, final String key) {
        final String value;
        int splitPos = key.length();
        if (arg.length() <= splitPos + 1 || arg.charAt(splitPos) != '=') {
            throw BootableJarLogger.ROOT_LOGGER.invalidArgument(arg);
        } else {
            value = arg.substring(splitPos + 1);
        }
        return value;
    }

    private static URL makeUrl(final String urlSpec) throws MalformedURLException {
        final String trimmed = urlSpec.trim();
        URL url;
        try {
            url = new URL(trimmed);
            if ("file".equals(url.getProtocol())) {
                // make sure the file is absolute & canonical file url
                url = Paths.get(url.toURI()).toRealPath().toUri().toURL();
            }
        } catch (Exception e) {
            // make sure we have an absolute & canonical file url
            try {
                url = Paths.get(trimmed).toRealPath().toUri().toURL();
            } catch (Exception n) {
                throw new MalformedURLException(n.toString());
            }
        }

        return url;
    }

}
