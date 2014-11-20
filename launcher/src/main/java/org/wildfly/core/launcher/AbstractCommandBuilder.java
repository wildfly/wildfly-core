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

    static final String HOME_DIR = "jboss.home.dir";
    static final String SECURITY_MANAGER_ARG = "-secmgr";
    static final String SECURITY_MANAGER_PROP = "java.security.manager";
    // TODO (jrp) should java.awt.headless=true be added?
    static final String[] DEFAULT_VM_ARGUMENTS = {
            "-Xms64m",
            "-Xmx512m",
            "-XX:MaxPermSize=256m",
            "-Djava.net.preferIPv4Stack=true"
    };

    private final Path wildflyHome;
    private boolean useSecMgr;
    private final List<String> modulesDirs;
    private final List<String> systemPackages;
    private Path logDir;
    private Path configDir;
    private final Arguments serverArgs;
    private boolean addDefaultModuleDir;

    protected AbstractCommandBuilder(final Path wildflyHome) {
        this.wildflyHome = wildflyHome;
        useSecMgr = false;
        modulesDirs = new ArrayList<>();
        systemPackages = new ArrayList<>();
        systemPackages.add("org.jboss.byteman");
        serverArgs = new Arguments();
        addDefaultModuleDir = true;
    }

    /**
     * Sets whether or not the security manager option, {@code -secmgr}, should be used.
     *
     * @param useSecMgr {@code true} to use the a security manager, otherwise {@code false}
     *
     * @return the builder
     */
    public T setUseSecurityManager(final boolean useSecMgr) {
        this.useSecMgr = useSecMgr;
        return getThis();
    }

    /**
     * Indicates whether or no a security manager should be used for the server launched.
     *
     * @return {@code true} if a security manager should be used, otherwise {@code false}
     */
    public boolean useSecurityManager() {
        return useSecMgr;
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
        if (path == null) {
            logDir = null;
            return getThis();
        }
        return setLogDirectory(Paths.get(path));
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
        if (path == null) {
            logDir = null;
        } else {
            if (Files.exists(path) && !Files.isDirectory(path)) {
                throw LauncherMessages.MESSAGES.invalidDirectory(path);
            }
            logDir = path.toAbsolutePath().normalize();
        }
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
    public T addSystemPackages(final Iterable<String> packageNames) {
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
            if (SECURITY_MANAGER_ARG.equals(arg)) {
                setUseSecurityManager(true);
            } else {
                serverArgs.add(arg);
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
    public T addServerArguments(final Iterable<String> args) {
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
        return addServerArgument("--admin-only");
    }

    /**
     * Sets the system property {@code jboss.bind.address} to the address given.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has not been removed from the interface. If
     * the system property was removed the address provided has no effect.
     *
     * @param address the address to set the bind address to
     *
     * @return the builder
     */
    public T setBindAddressHint(final String address) {
        addServerArg("-b", address);
        return getThis();
    }

    /**
     * Sets the system property {@code jboss.bind.address.$INTERFACE} to the address given where {@code $INTERFACE} is
     * the {@code interfaceName} parameter. For example in the default configuration passing {@code management} for the
     * {@code interfaceName} parameter would result in the system property {@code jboss.bind.address.management} being
     * set to the address provided.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has not been removed from the interface. If
     * the system property was removed the address provided has no effect.
     *
     * @param interfaceName the name of the interface of the binding address
     * @param address       the address to bind the management interface to
     *
     * @return the builder
     */
    public T setBindAddressHint(final String interfaceName, final String address) {
        if (interfaceName == null) {
            throw LauncherMessages.MESSAGES.nullParam("interfaceName");
        }
        addServerArg("-b" + interfaceName, address);
        return getThis();
    }

    /**
     * Sets the system property {@code jboss.default.multicast.address} to the address given.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has not been removed from the interface. If
     * the system property was removed the address provided has no effect.
     *
     * @param address the address to set the multicast system property to
     *
     * @return the builder
     */
    public T setMulticastAddressHint(final String address) {
        addServerArg("-u", address);
        return getThis();
    }

    /**
     * Sets the properties file to use for the server or {@code null} to remove the file. The file must exist.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}..
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
     * This will override any previous value set via {@link #addServerArgument(String)}..
     *
     * @param file the properties file to use or {@code null}
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the file does not exist or is not a regular file
     */
    public T setPropertiesFile(final Path file) {
        if (file == null) {
            addServerArg("-P", null);
        } else {
            if (Files.notExists(file)) {
                throw LauncherMessages.MESSAGES.pathDoesNotExist(file);
            }
            if (!Files.isRegularFile(file)) {
                throw LauncherMessages.MESSAGES.pathNotAFile(file);
            }
            addServerArg("-P", file.toAbsolutePath().normalize().toString());
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
        return parent.resolve(path).toAbsolutePath().normalize();
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
        return serverArgs.asList();
    }

    /**
     * Returns the concrete builder.
     *
     * @return the concrete builder
     */
    protected abstract T getThis();

    protected void addServerArg(final String key, final String value) {
        serverArgs.add(key, value);
    }

    protected String getServerArg(final String key) {
        return serverArgs.get(key);
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
        return path.toAbsolutePath().normalize();
    }

    protected static void addSystemPropertyArg(final List<String> cmd, final String key, final Object value) {
        if (value != null) {
            cmd.add("-D" + key + "=" + value);
        }
    }

    private static String getExeSuffix() {
        final String os = System.getProperty("os.name");
        if (os != null && os.toLowerCase(Locale.ROOT).contains("win")) {
            return ".exe";
        }
        return "";
    }
}
