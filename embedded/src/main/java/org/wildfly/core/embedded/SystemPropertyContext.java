/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.embedded;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * An abstract instance of a context which sets and restores system properties.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class SystemPropertyContext implements Context {
    private static final String HOME_DIR = "jboss.home.dir";
    private static final String HOST_NAME = "jboss.host.name";
    private static final String NODE_NAME = "jboss.node.name";
    private static final String QUALIFIED_HOST_NAME = "jboss.qualified.host.name";
    private static final String SERVER_NAME = "jboss.server.name";

    private final Path jbossHomeDir;
    private final Map<String, String> propertiesToReset = new HashMap<>();
    private final Set<String> propertiesToClear = new HashSet<>();

    /**
     * Creates a new system property context.
     *
     * @param jbossHomeDir the JBoss home directory
     */
    SystemPropertyContext(final Path jbossHomeDir) {
        if (jbossHomeDir == null) {
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("jbossHomeDir");
        }
        this.jbossHomeDir = jbossHomeDir;
    }

    @Override
    public void activate() {
        addOrReplaceProperty(HOME_DIR, jbossHomeDir);
        checkProperty("jboss.server.persist.config");
        checkProperty(HOST_NAME);
        checkProperty(NODE_NAME);
        checkProperty(QUALIFIED_HOST_NAME);
        checkProperty(SERVER_NAME);
        checkProperty("org.jboss.resolver.warning");
        configureProperties();
    }

    @Override
    public void restore() {
        final Iterator<String> toClear = propertiesToClear.iterator();
        while (toClear.hasNext()) {
            SecurityActions.clearPropertyPrivileged(toClear.next());
            toClear.remove();
        }
        final Iterator<Map.Entry<String, String>> toReset = propertiesToReset.entrySet().iterator();
        while (toReset.hasNext()) {
            final Map.Entry<String, String> entry = toReset.next();
            SecurityActions.setPropertyPrivileged(entry.getKey(), entry.getValue());
            toReset.remove();
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "(propertiesToReset=" + propertiesToReset + ", propertiesToClear="
                + propertiesToClear + ")";
    }

    /**
     * Configures additional system properties.
     */
    abstract void configureProperties();

    /**
     * Adds or replaces the system property. If the system property already exists with a different value on
     * {@link #restore()} the system property will be set back to it's previous value.
     *
     * @param name  the name of the property
     * @param value the value to set
     */
    @SuppressWarnings("WeakerAccess")
    void addOrReplaceProperty(final String name, final Object value) {
        final String currentValue = SecurityActions.setPropertyPrivileged(name, value.toString());
        if (currentValue != null) {
            propertiesToReset.put(name, currentValue);
        } else {
            propertiesToClear.add(name);
        }
    }

    /**
     * Adds a system property only if the property does not currently exist. If the property does not exist on
     * {@link #restore()} the property will be cleared.
     *
     * @param name  the name of the property
     * @param value the value to set if the property is absent
     */
    void addPropertyIfAbsent(final String name, final Object value) {
        final String currentValue = SecurityActions.getPropertyPrivileged(name);
        if (currentValue == null) {
            SecurityActions.setPropertyPrivileged(name, value.toString());
            propertiesToClear.add(name);
        }
    }

    /**
     * If the property does not exist it will be added to the properties to be cleared when the context is
     * {@linkplain #restore() restored}.
     *
     * @param name the name of the property
     */
    void checkProperty(final String name) {
        if (SecurityActions.getPropertyPrivileged(name) == null) {
            propertiesToClear.add(name);
        }
    }

    /**
     * Resolves the base directory. If the system property is set that value will be used. Otherwise the path is
     * resolved from the home directory.
     *
     * @param name    the system property name
     * @param dirName the directory name relative to the base directory
     *
     * @return the resolved base directory
     */
    Path resolveBaseDir(final String name, final String dirName) {
        final String currentDir = SecurityActions.getPropertyPrivileged(name);
        if (currentDir == null) {
            return jbossHomeDir.resolve(dirName);
        }
        return Paths.get(currentDir);
    }

    /**
     * Resolves a path relative to the base path.
     *
     * @param base  the base path
     * @param paths paths relative to the base directory
     *
     * @return the resolved path
     */
    static Path resolvePath(final Path base, final String... paths) {
        return Paths.get(base.toString(), paths);
    }
}
