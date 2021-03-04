/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.jar.runtime;

import static org.wildfly.core.jar.runtime.Constants.DEPLOYMENT_ARG;

import java.io.IOException;
import java.io.InputStream;
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

import org.jboss.as.process.CommandLineConstants;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

/**
 *
 * @author jdenise
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class Arguments {

    private Arguments(final BootableEnvironment environment) {
        this.environment = environment;
    }

    private Boolean isHelp;
    private Boolean isVersion;
    private final List<String> serverArguments = new ArrayList<>();
    private final BootableEnvironment environment;
    private Path deployment;
    private Path cliScript;

    static Arguments parseArguments(final List<String> args, final BootableEnvironment environment) throws Exception {
        Objects.requireNonNull(args);
        Arguments arguments = new Arguments(environment);
        arguments.handleArguments(args);
        return arguments;
    }

    private void handleArguments(List<String> args) throws Exception {
        final Map<String, String> systemProperties = new HashMap<>();
        final Iterator<String> iter = args.iterator();
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
                } else {
                    throw BootableJarLogger.ROOT_LOGGER.invalidArgument(a);
                }
            } else if (a.startsWith(CommandLineConstants.PROPERTIES)) {
                serverArguments.add(a);
                // We need these set as system properties early so the log manager can use them
                final String urlSpec = parseValue(a, CommandLineConstants.PROPERTIES);
                addSystemProperties(makeUrl(urlSpec), systemProperties);
            } else if (a.startsWith(CommandLineConstants.SECURITY_PROP)) {
                serverArguments.add(a);
            } else if (a.startsWith(CommandLineConstants.SYS_PROP)) {
                // We want to set the server argument and add as a system property. The reason for this is when if the
                // property already exists on the system-property resource a warning is logged alerting to that. However
                // we also need to set it as a current system property for usage in the log manager.
                serverArguments.add(a);
                addSystemProperty(a, systemProperties);
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
            } else {
                throw BootableJarLogger.ROOT_LOGGER.unknownArgument(a);
            }
        }
        // Add the system properties to the environment
        environment.setSystemProperties(systemProperties);
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
