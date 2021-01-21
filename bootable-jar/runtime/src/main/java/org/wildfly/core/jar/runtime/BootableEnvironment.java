/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.jar.runtime;

import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a bootable JAR environment.
 * <p>
 * The environment initializes required system properties. System properties should also be
 * {@linkplain #setSystemProperty(String, String) set} via the environment and not directly.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class BootableEnvironment {

    private static final AtomicBoolean DEBUG = new AtomicBoolean();
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    private final Path jbossHome;
    private final Path serverDir;
    private final Path tmpDir;
    private final Collection<String> ignoredProperties;
    private final PropertyUpdater propertyUpdater;
    private final String pidFileName;
    private final long timeout;

    private BootableEnvironment(final Path jbossHome, final Collection<String> ignoredProperties,
                                final PropertyUpdater propertyUpdater) {
        this.jbossHome = jbossHome;
        serverDir = jbossHome.resolve("standalone");
        tmpDir = resolvePath(serverDir, "tmp");
        this.ignoredProperties = ignoredProperties;
        this.propertyUpdater = propertyUpdater;
        String pidFileName = System.getProperty("org.wildfly.core.bootable.jar.pidFile");
        if (pidFileName == null) {
            pidFileName = System.getenv("JBOSS_PIDFILE");
            if (pidFileName == null) {
                pidFileName = "wildfly.pid";
            }
        }
        this.pidFileName = pidFileName;
        long timeout;
        try {
            timeout = Long.parseLong(System.getProperty("org.wildfly.core.bootable.jar.timeout", "10"));
        } catch (NumberFormatException ignore) {
            //noinspection MagicNumber
            timeout = 10L;
        }
        this.timeout = timeout;
    }

    /**
     * Creates a new environment initializing required system properties based on the JBoss Home directory passed in.
     *
     * @param jbossHome the base JBoss Home directory
     *
     * @return the newly create environment
     */
    static BootableEnvironment of(final Path jbossHome) {
        final PropertyUpdater propertyUpdater;
        if (System.getSecurityManager() == null) {
            propertyUpdater = System::setProperty;
        } else {
            propertyUpdater = (name, value) ->
                    AccessController.doPrivileged((PrivilegedAction<String>) () -> System.setProperty(name, value));
        }
        return of(jbossHome, propertyUpdater);
    }

    /**
     * Creates a new environment initializing required system properties based on the JBoss Home directory passed in.
     *
     * @param jbossHome       the base JBoss Home directory
     * @param propertyUpdater the updater used to set system properties
     *
     * @return the newly create environment
     */
    static BootableEnvironment of(final Path jbossHome, final PropertyUpdater propertyUpdater) {
        return new BootableEnvironment(jbossHome, init(jbossHome, propertyUpdater), propertyUpdater);
    }

    /**
     * Returns the base JBoss Home directory.
     *
     * @return the JBoss Home directory
     */
    Path getJBossHome() {
        return jbossHome;
    }

    /**
     * Returns the server tmp dir.
     *
     * @return the server tmp dir.
     */
    Path getTmpDir() {
        return tmpDir;
    }

    /**
     * Returns the PID file.
     *
     * @return the PID file
     */
    Path getPidFile() {
        return resolvePath(jbossHome, pidFileName);
    }

    /**
     * Returns the timeout value for the bootable JAR in seconds.
     *
     * @return the timeout in seconds
     */
    long getTimeout() {
        return timeout;
    }

    /**
     * Indicates the OS is a Windows based OS.
     *
     * @return {@code true} if this is a Windows based OS, otherwise {@code false}
     */
    boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Results the servers configuration directory appending any optional paths.
     *
     * @param paths the optional paths to append to the configuration directory
     *
     * @return the resolved path
     */
    Path resolveConfigurationDir(final String... paths) {
        return resolvePath(serverDir, "configuration", paths);
    }

    /**
     * Results the servers content directory appending any optional paths.
     *
     * @param paths the optional paths to append to the content directory
     *
     * @return the resolved path
     */
    Path resolveContentDir(final String... paths) {
        return resolvePath(resolveDataDir(), "content", paths);
    }

    /**
     * Results the servers data directory appending any optional paths.
     *
     * @param paths the optional paths to append to the data directory
     *
     * @return the resolved path
     */
    Path resolveDataDir(final String... paths) {
        return resolvePath(serverDir, "data", paths);
    }

    /**
     * Results the servers log directory appending any optional paths.
     *
     * @param paths the optional paths to append to the log directory
     *
     * @return the resolved path
     */
    Path resolveLogDir(final String... paths) {
        return resolvePath(serverDir, "log", paths);
    }

    /**
     * Sets the system properties represented by the properties.
     * <p>
     * Note there are a set of system properties that will not be set and are determined based on the JBoss Home
     * directory.
     * </p>
     *
     * @param properties the properties to set
     */
    void setSystemProperties(final Map<String, String> properties) {
        final Map<String, String> local = new HashMap<>(properties);
        final String debugValue = local.remove(Constants.DEBUG_PROPERTY);
        if (debugValue != null) {
            DEBUG.set(debugValue.isEmpty() || "true".equalsIgnoreCase(debugValue));
        }
        for (Map.Entry<String, String> entry : local.entrySet()) {
            setSystemProperty(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Sets the system property.
     * <p>
     * Note there are a set of system properties that will not be set and are determined based on the JBoss Home
     * directory.
     * </p>
     *
     * @param key   the key for the property
     * @param value the property value
     */
    void setSystemProperty(final String key, final String value) {
        if (ignoredProperties.contains(key)) {
            logDebug("Ignoring system property %s.", key);
        } else {
            propertyUpdater.setProperty(key, value);
        }
    }

    private static Collection<String> init(final Path jbossHome, final PropertyUpdater propertyUpdater) {
        final Collection<String> propertyNames = new ArrayList<>();
        propertyNames.add("java.ext.dirs");
        propertyNames.add("java.home");
        propertyNames.add("java.io.tmpdir");
        propertyNames.add("jboss.server.persist.config");
        propertyNames.add("jboss.server.management.uuid");
        propertyNames.add("modules.path");
        propertyNames.add("user.dir");
        propertyNames.add("user.home");

        // Configure known paths
        setSystemProperty(propertyUpdater, "jboss.home.dir", jbossHome, propertyNames);
        final Path serverBaseDir = resolvePath(jbossHome, "standalone");
        setSystemProperty(propertyUpdater, "jboss.server.base.dir", serverBaseDir, propertyNames);
        setSystemProperty(propertyUpdater, "jboss.controller.temp.dir", resolvePath(serverBaseDir, "tmp"), propertyNames);
        final Path dataDir = resolvePath(serverBaseDir, "data");
        setSystemProperty(propertyUpdater, "jboss.server.data.dir", dataDir, propertyNames);
        setSystemProperty(propertyUpdater, "jboss.server.config.dir", resolvePath(serverBaseDir, "configuration"), propertyNames);
        setSystemProperty(propertyUpdater, "jboss.server.deploy.dir", resolvePath(dataDir, "content"), propertyNames);
        setSystemProperty(propertyUpdater, "jboss.server.log.dir", resolvePath(serverBaseDir, "log"), propertyNames);
        setSystemProperty(propertyUpdater, "jboss.server.temp.dir", resolvePath(serverBaseDir, "tmp"), propertyNames);
        return propertyNames;
    }

    private static Path resolvePath(final Path base, final String path1, final String... paths) {
        Path result = base.resolve(path1);
        for (String path : paths) {
            result = result.resolve(path);
        }
        return result.toAbsolutePath().normalize();
    }

    private static void setSystemProperty(final PropertyUpdater propertyUpdater, final String key, final Path path, final Collection<String> names) {
        names.add(key);
        final String previousValue = propertyUpdater.setProperty(key, path.toString());
        if (previousValue == null) {
            logDebug("Setting system property %s to %s", key, path);
        } else {
            logDebug("Replacing system property %s with a value of %s. The previous value was %s.", key, path, previousValue);
        }
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static void logDebug(final String format, final Object... args) {
        if (DEBUG.get()) {
            System.out.printf("[DEBUG] " + format, args);
        }
    }
}
