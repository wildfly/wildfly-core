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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
abstract class AbstractCommandBuilder<T extends AbstractCommandBuilder<T>> implements CommandBuilder {

    private static final String MODULES_JAR_NAME = "jboss-modules.jar";
    private static final String JAVA_EXE = "java" + getExeSuffix();

    // TODO (jrp) should java.awt.headless=true be added?
    private static final List<String> DEFAULT_VM_ARGUMENTS = Arrays.asList(
            "-Xms64m",
            "-Xmx512m",
            "-XX:MaxPermSize=256m",
            "-Djava.net.preferIPv4Stack=true"
    );

    private final Path wildflyHome;
    private final List<String> modulesDirs;
    private final List<String> jvmArgs;
    private final List<String> systemPackages;
    private Path logDir;
    private Path configDir;
    private final List<String> serverArgs;
    private boolean addDefaultModuleDir;

    public AbstractCommandBuilder(final Path wildflyHome) {
        this.wildflyHome = validateWildFlyDir(wildflyHome);
        modulesDirs = new ArrayList<>();
        jvmArgs = new ArrayList<>();
        jvmArgs.addAll(DEFAULT_VM_ARGUMENTS);
        systemPackages = new ArrayList<>();
        systemPackages.add("org.jboss.byteman");
        serverArgs = new ArrayList<>();
        addDefaultModuleDir = true;
    }

    /**
     * Adds a directory to the collection of module paths.
     *
     * @param moduleDir the module directory to add
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the path is invalid or {@code null}
     */
    public T addModuleDir(final String moduleDir) {
        // Validate the path
        final Path path = validateAndNormalizeDir(moduleDir, false);
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
    public T addModuleDirs(final Collection<String> moduleDirs) {
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
    public T setModuleDirs(final Collection<String> moduleDirs) {
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
            int index = 0;
            final int len = modulesDirs.size();
            for (String dir : modulesDirs) {
                result.append(dir);
                if (++index < len) {
                    result.append(File.pathSeparator);
                }
            }
        }
        return result.toString();
    }

    /**
     * Adds a JVM argument to the command ignoring {@code null} arguments.
     *
     * @param jvmArg the JVM argument to add
     *
     * @return the builder
     */
    public T addJvmArg(final String jvmArg) {
        if (jvmArg != null) {
            jvmArgs.add(jvmArg);
        }
        return getThis();
    }

    /**
     * Adds the array of JVM arguments to the command.
     *
     * @param jvmArgs the array of JVM arguments to add, {@code null} arguments are ignored
     *
     * @return the builder
     */
    public T addJvmArgs(final String... jvmArgs) {
        if (jvmArgs != null) {
            for (String jvmArg : jvmArgs) {
                addJvmArg(jvmArg);
            }
        }
        return getThis();
    }

    /**
     * Adds the collection of JVM arguments to the command.
     *
     * @param jvmArgs the collection of JVM arguments to add, {@code null} arguments are ignored
     *
     * @return the builder
     */
    public T addJvmArgs(final Collection<String> jvmArgs) {
        if (jvmArgs != null) {
            for (String jvmArg : jvmArgs) {
                addJvmArg(jvmArg);
            }
        }
        return getThis();
    }

    /**
     * Sets the JVM arguments to use. This overrides any default JVM arguments that would normally be added and ignores
     * {@code null} values in the collection.
     * <p/>
     * If the collection is {@code null} the JVM arguments will be cleared and no new arguments will be added.
     *
     * @param jvmArgs the JVM arguments to use
     *
     * @return the builder
     */
    public T setJvmArgs(final Collection<String> jvmArgs) {
        this.jvmArgs.clear();
        return addJvmArgs(jvmArgs);
    }


    /**
     * Sets the JVM arguments to use. This overrides any default JVM arguments that would normally be added and ignores
     * {@code null} values in the array.
     * <p/>
     * If the array is {@code null} the JVM arguments will be cleared and no new arguments will be added.
     *
     * @param jvmArgs the JVM arguments to use
     *
     * @return the builder
     */
    public T setJvmArgs(final String... jvmArgs) {
        this.jvmArgs.clear();
        return addJvmArgs(jvmArgs);
    }

    /**
     * Returns the JVM arguments.
     *
     * @return the JVM arguments
     */
    public List<String> getJvmArgs() {
        return new ArrayList<>(this.jvmArgs);
    }

    /**
     * Returns the log directory for the server.
     *
     * @return the log directory
     */
    public Path getLogDirectory() {
        if (logDir == null) {
            return normalizePath(getBaseDirectory(), "log");
        }
        return logDir;
    }

    /**
     * Sets the log directory to be used for log files.
     * <p/>
     * If set to {@code null}, the default, the log directory will be resolved.
     *
     * @param path the path to the log directory or {@code null} to have the log directory resolved
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the directory is not a valid directory
     */
    public T setLogDirectory(final String path) {
        logDir = validateAndNormalizeDir(path, true);
        return getThis();
    }

    /**
     * Sets the log directory to be used for log files.
     * <p/>
     * If set to {@code null}, the default, the log directory will be resolved.
     *
     * @param path the path to the log directory or {@code null} to have the log directory resolved
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the directory is not a valid directory
     */
    public T setLogDirectory(final Path path) {
        logDir = validateAndNormalizeDir(path, true);
        return getThis();
    }

    /**
     * Returns the configuration directory for the server.
     *
     * @return the configuration directory
     */
    public Path getConfigurationDirectory() {
        if (configDir == null) {
            return normalizePath(getBaseDirectory(), "configuration");
        }
        return configDir;
    }

    /**
     * Sets the configuration directory to be used for configuration files.
     * <p/>
     * If set to {@code null}, the default, the configuration directory will be resolved.
     *
     * @param path the path to the configuration directory or {@code null} to have the configuration directory resolved
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the directory is not a valid directory
     */
    public T setConfigurationDirectory(final String path) {
        configDir = validateAndNormalizeDir(path, true);
        return getThis();
    }

    /**
     * Sets the configuration directory to be used for configuration files.
     * <p/>
     * If set to {@code null}, the default, the configuration directory will be resolved.
     *
     * @param path the path to the configuration directory or {@code null} to have the configuration directory resolved
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the directory is not a valid directory
     */
    public T setConfigurationDirectory(final Path path) {
        configDir = validateAndNormalizeDir(path, true);
        return getThis();
    }

    /**
     * Adds a system package name to pass to the module loader ignoring {@code null} values.
     *
     * @param packageName the package name
     *
     * @return the builder
     */
    public T addSystemPackage(final String packageName) {
        if (packageName != null) {
            systemPackages.add(packageName);
        }
        return getThis();
    }

    /**
     * Adds a system package name to pass to the module loader ignoring {@code null} values.
     *
     * @param pkg the package
     *
     * @return the builder
     */
    public T addSystemPackage(final Package pkg) {
        if (pkg != null) {
            addSystemPackage(pkg.getName());
        }
        return getThis();
    }

    /**
     * Adds the package names to the system packages for the module loader ignoring {@code null} values.
     *
     * @param packageNames the package names to add
     *
     * @return the builder
     */
    public T addSystemPackages(final String... packageNames) {
        if (packageNames != null) {
            for (String name : packageNames) {
                addSystemPackage(name);
            }
        }
        return getThis();
    }

    /**
     * Adds the package names to the system packages for the module loader ignoring {@code null} values.
     *
     * @param packages the packages to add
     *
     * @return the builder
     */
    public T addSystemPackages(final Package... packages) {
        if (packages != null) {
            for (Package pkg : packages) {
                addSystemPackage(pkg);
            }
        }
        return getThis();
    }

    /**
     * Adds the package names to the system packages for the module loader ignoring {@code null} values.
     *
     * @param packageNames the package names to add
     *
     * @return the builder
     */
    public T addSystemPackages(final Collection<String> packageNames) {
        if (packageNames != null) {
            for (String name : packageNames) {
                addSystemPackage(name);
            }
        }
        return getThis();
    }

    /**
     * Sets the system packages for the module loader ignoring {@code null} values.
     * <p/>
     * If the array is {@code null} any previous packages added are cleared and no new packages are added.
     *
     * @param packageNames the package names to add
     *
     * @return the builder
     */
    public T setSystemPackages(final String... packageNames) {
        systemPackages.clear();
        if (packageNames != null) {
            for (String name : packageNames) {
                addSystemPackage(name);
            }
        }
        return getThis();
    }

    /**
     * Sets system packages for the module loader ignoring {@code null} values.
     * <p/>
     * If the array is {@code null} any previous packages added are cleared and no new packages are added.
     *
     * @param packages the packages to add
     *
     * @return the builder
     */
    public T setSystemPackages(final Package... packages) {
        systemPackages.clear();
        if (packages != null) {
            for (Package pkg : packages) {
                addSystemPackage(pkg);
            }
        }
        return getThis();
    }

    /**
     * Returns the command line argument for the system module packages.
     *
     * @return the command line argument
     */
    public String getSystemPackages() {
        int index = 0;
        final int len = systemPackages.size();
        final StringBuilder result = new StringBuilder("-Djboss.modules.system.pkgs=");
        for (String name : systemPackages) {
            result.append(name);
            if (++index < len) {
                result.append(',');
            }
        }
        return result.toString();
    }

    /**
     * Adds an argument to be passed to the server ignore the argument if {@code null}.
     *
     * @param arg the argument to pass
     *
     * @return the builder
     */
    public T addServerArgument(final String arg) {
        if (arg != null) {
            serverArgs.add(arg);
        }
        return getThis();
    }

    /**
     * Adds the arguments to the collection of arguments that will be passed to the server ignoring any {@code null}
     * arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public T addServerArguments(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addServerArgument(arg);
            }
        }
        return getThis();
    }

    /**
     * Adds the arguments to the collection of arguments that will be passed to the server ignoring any {@code null}
     * arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public T addServerArguments(final Collection<String> args) {
        if (args != null) {
            for (String arg : args) {
                addServerArgument(arg);
            }
        }
        return getThis();
    }

    /**
     * Sets the server argument {@code --admin-only}.
     *
     * @return the builder
     */
    public T setAdminOnly() {
        if (!serverArgs.contains("--admin-only")) {
            addServerArgument("--admin-only");
        }
        return getThis();
    }

    /**
     * Sets the address to bind the server to or {@code null} to remove the address and use the default.
     * <p/>
     * This will override any previous value set.
     *
     * @param address the address to bind the server to or {@code null} to remove the value
     *
     * @return the builder
     */
    public T setBindAddress(final String address) {
        addOrRemoveServerArg("-b", address);
        return getThis();
    }

    /**
     * Sets the address to bind the management interface to or {@code null} to remove the address and use the default.
     * <p/>
     * This will override any previous value set.
     *
     * @param address the address to bind the management interface to or {@code null} to remove the value
     *
     * @return the builder
     */
    public T setManagementBindAddress(final String address) {
        addOrRemoveServerArg("-bmanagement", address);
        return getThis();
    }

    /**
     * Sets the configuration file for the server. The file must be in the {@link #setConfigurationDirectory(String)
     * configuration} directory. A value of {@code null} will remove the configuration file.
     * <p/>
     * This will override any previous value set.
     *
     * @param configFile the configuration file name or {@code null} to remove the configuration file
     *
     * @return the builder
     */
    public T setServerConfiguration(final String configFile) {
        addOrRemoveServerArg("-c", configFile);
        return getThis();
    }

    /**
     * Sets the properties file to use for the server or {@code null} to remove the file. The file must exist.
     * <p/>
     * This will override any previous value set.
     *
     * @param file the properties file to use or {@code null}
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the file does not exist or is not a regular file
     */
    public T setPropertiesFile(final String file) {
        final Path path;
        if (file == null) {
            path = null;
        } else {
            path = Paths.get(file);
        }
        return setPropertiesFile(path);
    }

    /**
     * Sets the properties file to use for the server or {@code null} to remove the file. The file must exist.
     * <p/>
     * This will override any previous value set.
     *
     * @param file the properties file to use or {@code null}
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the file does not exist or is not a regular file
     */
    public T setPropertiesFile(final Path file) {
        if (file == null) {
            addOrRemoveServerArg("-P", null);
        } else {
            if (Files.notExists(file)) {
                throw LauncherMessages.MESSAGES.pathDoesNotExist(file);
            }
            if (!Files.isRegularFile(file)) {
                throw LauncherMessages.MESSAGES.pathNotAFile(file);
            }
            addOrRemoveServerArg("-P", file.normalize().toString());
        }
        return getThis();
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
     * Returns the command line argument that specifies the logging configuration.
     *
     * @param fileName the name of the configuration file
     *
     * @return the command line argument
     */
    protected String getLoggingPropertiesArgument(final String fileName) {
        return "-Dlogging.configuration=file:" + normalizePath(getConfigurationDirectory(), fileName);
    }

    /**
     * Returns the command line argument that specifies the path the log file that should be used.
     * <p/>
     * This is generally only used on the initial boot of the server in standalone. The server will rewrite the
     * configuration file with hard-coded values.
     *
     * @param fileName the name of the file
     *
     * @return the command line argument
     */
    protected String getBootLogArgument(final String fileName) {
        return "-Dorg.jboss.boot.log.file=" + normalizePath(getLogDirectory(), fileName);
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
        return parent.resolve(path).normalize();
    }

    /**
     * Returns the base directory for the server.
     * <p/>
     * Example:
     * For standalone the base directory would be {@code $JBOSS_HOME/standalone}.
     *
     * @return the base directory
     */
    public abstract Path getBaseDirectory();

    /**
     * A collection of server command line arguments.
     *
     * @return the server arguments
     */
    public List<String> getServerArguments() {
        return new ArrayList<>(serverArgs);
    }

    /**
     * Returns the concrete builder.
     *
     * @return the concrete builder
     */
    protected abstract T getThis();

    protected void addOrRemoveServerArg(final String key, final String value) {
        if (value == null) {
            if (serverArgs.contains(key)) {
                final int index = serverArgs.indexOf(key) + 1;
                serverArgs.remove(index);
                serverArgs.remove(key);
            }
        } else {
            if (serverArgs.contains(key)) {
                final int index = serverArgs.indexOf(key) + 1;
                serverArgs.remove(index);
                serverArgs.add(index, value);
            } else {
                serverArgs.add(key);
                serverArgs.add(value);
            }
        }
    }

    protected void addOrRemoveServerArg(final String key, final Object value) {
        addOrRemoveServerArg(key, (value == null ? null : value.toString()));
    }

    protected static Path resolveJavaHome(final Path javaHome) {
        if (javaHome == null) {
            return validateJavaHome(System.getProperty("java.home"));
        }
        return validateJavaHome(javaHome);
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
        return path.normalize();
    }

    private static String getExeSuffix() {
        final String os = System.getProperty("os.name");
        if (os != null && os.toLowerCase(Locale.ROOT).contains("win")) {
            return ".exe";
        }
        return "";
    }
}
