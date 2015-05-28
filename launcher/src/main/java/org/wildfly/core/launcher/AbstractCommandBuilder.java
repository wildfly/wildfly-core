/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
@SuppressWarnings("unused")
abstract class AbstractCommandBuilder<T extends AbstractCommandBuilder<T>> implements CommandBuilder {

    private static final String MODULES_JAR_NAME = "jboss-modules.jar";
    private static final String JAVA_EXE;
    private static final String JAVA_HOME;

    static final boolean IS_MAC;
    static final String HOME_DIR = "jboss.home.dir";

    static {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String exe = "java";
        IS_MAC = os.startsWith("mac");
        if (os.toLowerCase(Locale.ROOT).contains("win")) {
            exe = "java.exe";
        }
        JAVA_EXE = exe;
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            javaHome = System.getProperty("java.home");
        }
        JAVA_HOME = javaHome;
    }

    private final Path wildflyHome;
    private final List<String> modulesDirs;
    private boolean addDefaultModuleDir;

    protected AbstractCommandBuilder(final Path wildflyHome) {
        this.wildflyHome = wildflyHome;
        modulesDirs = new ArrayList<>();
        addDefaultModuleDir = true;
    }

    /**
     * Adds a directory to the collection of module paths.
     *
     * @param moduleDir the module directory to add
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the path is {@code null}
     */
    public T addModuleDir(final String moduleDir) {
        if (moduleDir == null) {
            throw LauncherMessages.MESSAGES.nullParam("moduleDir");
        }
        // Validate the path
        final Path path = Paths.get(moduleDir).normalize();
        modulesDirs.add(path.toString());
        return getThis();
    }

    /**
     * Adds all the module directories to the collection of module paths.
     *
     * @param moduleDirs an array of module paths to add
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public T addModuleDirs(final String... moduleDirs) {
        // Validate and add each path
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        return getThis();
    }

    /**
     * Adds all the module directories to the collection of module paths.
     *
     * @param moduleDirs a collection of module paths to add
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public T addModuleDirs(final Iterable<String> moduleDirs) {
        // Validate and add each path
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        return getThis();
    }

    /**
     * Replaces any previously set module directories with the collection of module directories.
     * <p/>
     * The default module directory will <i>NOT</i> be used if this method is invoked.
     *
     * @param moduleDirs the collection of module directories to use
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public T setModuleDirs(final Iterable<String> moduleDirs) {
        this.modulesDirs.clear();
        // Process each module directory
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        addDefaultModuleDir = false;
        return getThis();
    }

    /**
     * Replaces any previously set module directories with the array of module directories.
     * <p/>
     * The default module directory will <i>NOT</i> be used if this method is invoked.
     *
     * @param moduleDirs the array of module directories to use
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    public T setModuleDirs(final String... moduleDirs) {
        this.modulesDirs.clear();
        // Process each module directory
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        addDefaultModuleDir = false;
        return getThis();
    }

    /**
     * Returns the modules paths used on the command line.
     *
     * @return the paths separated by the {@link File#pathSeparator path separator}
     */
    public String getModulePaths() {
        final StringBuilder result = new StringBuilder();
        if (addDefaultModuleDir) {
            result.append(normalizePath("modules").toString());
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

    /**
     * Returns the home directory used.
     *
     * @return the home directory
     */
    public Path getWildFlyHome() {
        return wildflyHome;
    }

    /**
     * Returns the Java home directory where the java executable command can be found.
     * <p/>
     * If the directory was not set the system property value, {@code java.home}, should be used.
     *
     * @return the path to the Java home directory
     */
    public abstract Path getJavaHome();

    /**
     * The java executable command found in the {@link #getJavaHome() Java home} directory.
     *
     * @return the java executable command
     */
    protected String getJavaCommand() {
        return getJavaCommand(getJavaHome());
    }

    /**
     * The java executable command found in the Java home directory.
     * <p/>
     * If the directory contains a space the command is returned with quotes surrounding it.
     *
     * @param javaHome the path to the Java home directory
     *
     * @return the java executable command
     */
    protected String getJavaCommand(final Path javaHome) {
        final String exe = javaHome.resolve("bin").resolve("java").toString();
        if (exe.contains(" ")) {
            return "\"" + exe + "\"";
        }
        return exe;
    }

    /**
     * Returns the normalized path to the {@code jboss-modules.jar} for launching the server.
     *
     * @return the path to {@code jboss-modules.jar}
     */
    public String getModulesJarName() {
        return normalizePath(MODULES_JAR_NAME).toString();
    }


    /**
     * Resolves the path to the path relative to the WildFly home directory.
     *
     * @param path the name of the relative path
     *
     * @return the normalized path
     */
    protected Path normalizePath(final String path) {
        return normalizePath(wildflyHome, path);
    }

    /**
     * Resolves the path relative to the parent and normalizes it.
     *
     * @param parent the parent path
     * @param path   the path
     *
     * @return the normalized path
     */
    protected Path normalizePath(final Path parent, final String path) {
        return parent.resolve(path).toAbsolutePath().normalize();
    }

    /**
     * Returns the concrete builder.
     *
     * @return the concrete builder
     */
    protected abstract T getThis();

    protected static Path getDefaultJavaHome() {
        return validateJavaHome(JAVA_HOME);
    }

    protected static Path validateWildFlyDir(final String wildflyHome) {
        final Path result = validateAndNormalizeDir(wildflyHome, false);
        if (Files.notExists(result.resolve(MODULES_JAR_NAME))) {
            throw LauncherMessages.MESSAGES.invalidDirectory(MODULES_JAR_NAME, result);
        }
        return result;
    }

    protected static Path validateWildFlyDir(final Path wildflyHome) {
        final Path result = validateAndNormalizeDir(wildflyHome, false);
        if (Files.notExists(result.resolve(MODULES_JAR_NAME))) {
            throw LauncherMessages.MESSAGES.invalidDirectory(MODULES_JAR_NAME, wildflyHome);
        }
        return result;
    }

    protected static Path validateJavaHome(final String javaHome) {
        if (javaHome == null) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(null);
        }
        return validateJavaHome(Paths.get(javaHome));
    }

    protected static Path validateJavaHome(final Path javaHome) {
        final Path result = validateAndNormalizeDir(javaHome, false);
        final Path exe = result.resolve("bin").resolve(JAVA_EXE);
        if (Files.notExists(exe)) {
            final int count = exe.getNameCount();
            throw LauncherMessages.MESSAGES.invalidDirectory(exe.subpath(count - 2, count).toString(), javaHome);
        }
        return result;
    }

    protected static Path validateAndNormalizeDir(final String path, final boolean allowNull) {
        if (path == null) {
            if (allowNull) return null;
            throw LauncherMessages.MESSAGES.pathDoesNotExist(null);
        }
        return validateAndNormalizeDir(Paths.get(path), allowNull);
    }

    protected static Path validateAndNormalizeDir(final Path path, final boolean allowNull) {
        if (allowNull && path == null) return null;
        if (path == null || Files.notExists(path)) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(path);
        }
        if (!Files.isDirectory(path)) {
            throw LauncherMessages.MESSAGES.invalidDirectory(path);
        }
        return path.toAbsolutePath().normalize();
    }

    protected static void addSystemPropertyArg(final List<String> cmd, final String key, final Object value) {
        if (value != null) {
            cmd.add("-D" + key + "=" + value);
        }
    }
}
