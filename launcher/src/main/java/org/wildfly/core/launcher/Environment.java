/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.launcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Environment {
    private static final boolean MAC;
    private static final boolean WINDOWS;

    static final String HOME_DIR = "jboss.home.dir";
    static final String MODULES_JAR_NAME = "jboss-modules.jar";

    static {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        MAC = os.startsWith("mac");
        WINDOWS = os.contains("win");
    }

    private final Path wildflyHome;
    private Jvm jvm;
    private final List<String> modulesDirs;
    private boolean addDefaultModuleDir;

    Environment(final String wildflyHome) {
        this(validateWildFlyDir(wildflyHome));
    }

    Environment(final Path wildflyHome) {
        this.wildflyHome = validateWildFlyDir(wildflyHome);
        modulesDirs = new ArrayList<>();
        addDefaultModuleDir = true;
        jvm = Jvm.current();
    }

    /**
     * Returns the WildFly Home directory.
     *
     * @return the WildFly home directory
     */
    public Path getWildflyHome() {
        return wildflyHome;
    }

    /**
     * Returns the full path to the {@code jboss-modules.jar}.
     *
     * @return the path to {@code jboss-modules.jar}
     */
    public Path getModuleJar() {
        return resolvePath(MODULES_JAR_NAME);
    }

    /**
     * Adds a directory to the collection of module paths.
     *
     * @param moduleDir the module directory to add
     *
     * @throws java.lang.IllegalArgumentException if the path is {@code null}
     */
    public void addModuleDir(final String moduleDir) {
        if (moduleDir == null) {
            throw LauncherMessages.MESSAGES.nullParam("moduleDir");
        }
        // Validate the path
        final Path path = Paths.get(moduleDir).normalize();
        modulesDirs.add(path.toString());
    }

    /**
     * Adds all the module directories to the collection of module paths.
     *
     * @param moduleDirs an array of module paths to add
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public void addModuleDirs(final String... moduleDirs) {
        // Validate and add each path
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
    }

    /**
     * Adds all the module directories to the collection of module paths.
     *
     * @param moduleDirs a collection of module paths to add
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public void addModuleDirs(final Iterable<String> moduleDirs) {
        // Validate and add each path
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
    }

    /**
     * Replaces any previously set module directories with the collection of module directories.
     * <p/>
     * The default module directory will <i>NOT</i> be used if this method is invoked.
     *
     * @param moduleDirs the collection of module directories to use
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public void setModuleDirs(final Iterable<String> moduleDirs) {
        this.modulesDirs.clear();
        // Process each module directory
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        addDefaultModuleDir = false;
    }

    /**
     * Replaces any previously set module directories with the array of module directories.
     * <p/>
     * The default module directory will <i>NOT</i> be used if this method is invoked.
     *
     * @param moduleDirs the array of module directories to use
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public void setModuleDirs(final String... moduleDirs) {
        this.modulesDirs.clear();
        // Process each module directory
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        addDefaultModuleDir = false;
    }

    /**
     * Returns the modules paths used on the command line.
     *
     * @return the paths separated by the {@link File#pathSeparator path separator}
     */
    public String getModulePaths() {
        final StringBuilder result = new StringBuilder();
        if (addDefaultModuleDir) {
            result.append(wildflyHome.resolve("modules").toString());
        }
        if (!modulesDirs.isEmpty()) {
            if (addDefaultModuleDir) result.append(File.pathSeparator);
            for (Iterator<String> iterator = modulesDirs.iterator(); iterator.hasNext(); ) {
                result.append(iterator.next());
                if (iterator.hasNext()) {
                    result.append(File.pathSeparator);
                }
            }
        }
        return result.toString();
    }

    Jvm getJvm() {
        return jvm;
    }

    Environment setJvm(final Jvm jvm) {
        this.jvm = jvm == null ? Jvm.current() : jvm;
        return this;
    }

    /**
     * Resolves a path relative to the WildFly home directory.
     * <p>
     * Note this does not validate whether or not the path is valid or exists.
     * </p>
     *
     * @param paths the paths to resolve
     *
     * @return the path
     */
    public Path resolvePath(final String... paths) {
        Path result = wildflyHome;
        for (String path : paths) {
            result = result.resolve(path);
        }
        return result;
    }

    public static boolean isMac() {
        return MAC;
    }

    public static boolean isWindows() {
        return WINDOWS;
    }

    static Path validateWildFlyDir(final String wildflyHome) {
        if (wildflyHome == null) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(null);
        }
        return validateWildFlyDir(Paths.get(wildflyHome));
    }

    static Path validateWildFlyDir(final Path wildflyHome) {
        if (wildflyHome == null || Files.notExists(wildflyHome)) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(wildflyHome);
        }
        if (!Files.isDirectory(wildflyHome)) {
            throw LauncherMessages.MESSAGES.invalidDirectory(wildflyHome);
        }
        final Path result = wildflyHome.toAbsolutePath().normalize();
        if (Files.notExists(result.resolve(MODULES_JAR_NAME))) {
            throw LauncherMessages.MESSAGES.invalidDirectory(MODULES_JAR_NAME, wildflyHome);
        }
        return result;
    }

    static Path validateJar(final Path jarPath) {
        if (jarPath == null || Files.notExists(jarPath)) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(jarPath);
        }
        if (Files.isDirectory(jarPath)) {
            throw LauncherMessages.MESSAGES.pathNotAFile(jarPath);
        }
        return jarPath.toAbsolutePath().normalize();
    }

    static Path validateJar(final String jarPath) {
        if (jarPath == null) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(null);
        }
        return validateJar(Paths.get(jarPath));
    }

    @SuppressWarnings("SameParameterValue")
    static Path validateAndNormalizeDir(final String path, final boolean allowNull) {
        if (path == null) {
            if (allowNull) return null;
            throw LauncherMessages.MESSAGES.pathDoesNotExist(null);
        }
        return validateAndNormalizeDir(Paths.get(path), allowNull);
    }

    static Path validateAndNormalizeDir(final Path path, final boolean allowNull) {
        if (allowNull && path == null) return null;
        if (path == null || Files.notExists(path)) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(path);
        }
        if (!Files.isDirectory(path)) {
            throw LauncherMessages.MESSAGES.invalidDirectory(path);
        }
        return path.toAbsolutePath().normalize();
    }
}
