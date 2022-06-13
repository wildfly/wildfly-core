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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.wildfly.core.launcher.Arguments.Argument;
import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
abstract class AbstractCommandBuilder<T extends AbstractCommandBuilder<T>> implements CommandBuilder {

    private static final String ALLOW_VALUE = "allow";
    private static final String DISALLOW_VALUE = "disallow";
    static final String HOME_DIR = Environment.HOME_DIR;
    static final String SECURITY_MANAGER_ARG = "-secmgr";
    static final String SECURITY_MANAGER_PROP = "java.security.manager";
    static final String SECURITY_MANAGER_PROP_WITH_ALLOW_VALUE = "-D" + SECURITY_MANAGER_PROP + "=" + ALLOW_VALUE;
    static final String[] DEFAULT_VM_ARGUMENTS;
    static final Collection<String> DEFAULT_MODULAR_VM_ARGUMENTS;

    static {
        // Default JVM parameters for all versions
        final Collection<String> javaOpts = new ArrayList<>();
        javaOpts.add("-Xms64m");
        javaOpts.add("-Xmx512m");
        javaOpts.add("-Djava.net.preferIPv4Stack=true");
        javaOpts.add("-Djava.awt.headless=true");
        javaOpts.add("-Djboss.modules.system.pkgs=org.jboss.byteman");
        DEFAULT_VM_ARGUMENTS = javaOpts.toArray(new String[javaOpts.size()]);

        // Default JVM parameters for all modular JDKs
        // Additions to these should include good explanations why in the relevant JIRA
        // Keep them alphabetical to avoid the code history getting confused by reordering commits
        final ArrayList<String> modularJavaOpts = new ArrayList<>();
        if (!Boolean.parseBoolean(System.getProperty("launcher.skip.jpms.properties", "false"))) {
            modularJavaOpts.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED");
            modularJavaOpts.add("--add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED");
            modularJavaOpts.add("--add-exports=java.naming/com.sun.jndi.url.ldap=ALL-UNNAMED");
            modularJavaOpts.add("--add-exports=java.naming/com.sun.jndi.url.ldaps=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.base/java.io=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.base/java.security=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.base/java.util=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.management/javax.management=ALL-UNNAMED");
            modularJavaOpts.add("--add-opens=java.naming/javax.naming=ALL-UNNAMED");
            // As of jboss-modules 1.9.1.Final the java.se module is no longer required to be added. However as this API is
            // designed to work with older versions of the server we still need to add this argument of modular JVM's.
            modularJavaOpts.add("--add-modules=java.se");
        }
        DEFAULT_MODULAR_VM_ARGUMENTS = Collections.unmodifiableList(modularJavaOpts);
    }

    protected final Environment environment;
    private boolean useSecMgr;
    private Path logDir;
    private Path configDir;
    private final Arguments serverArgs;

    protected AbstractCommandBuilder(final Path wildflyHome) {
        this(wildflyHome, null);
    }

    protected AbstractCommandBuilder(final Path wildflyHome, final Jvm jvm) {
        environment = new Environment(wildflyHome).setJvm(jvm);
        useSecMgr = false;
        serverArgs = new Arguments();
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
        environment.addModuleDir(moduleDir);
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
        environment.addModuleDirs(moduleDirs);
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
        environment.addModuleDirs(moduleDirs);
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
        environment.setModuleDirs(moduleDirs);
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
        environment.setModuleDirs(moduleDirs);
        return getThis();
    }

    /**
     * Returns the modules paths used on the command line.
     *
     * @return the paths separated by the {@link java.io.File#pathSeparator path separator}
     */
    public String getModulePaths() {
        return environment.getModulePaths();
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
     * Adds an argument to be passed to the server ignore the argument if {@code null}.
     *
     * @param arg the argument to pass
     *
     * @return the builder
     */
    public T addServerArgument(final String arg) {
        if (arg != null) {
            final Argument argument = Arguments.parse(arg);
            if (addServerArgument(argument)) {
                if (SECURITY_MANAGER_ARG.equals(arg)) {
                    setUseSecurityManager(true);
                } else {
                    serverArgs.add(argument);
                }
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
     * Sets the server argument {@code --start-mode=suspend}.
     *
     * @return the builder
     */
    public T setStartSuspended() {
        return addServerArgument("--start-mode=suspend");
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
        setSingleServerArg("-b", address);
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
        setSingleServerArg("-b" + interfaceName, address);
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
        setSingleServerArg("-u", address);
        return getThis();
    }

    /**
     * Adds a properties file to be passed to the server. Note that the file must exist.
     *
     * @param file the file to add
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the file does not exist or is not a regular file
     */
    public T addPropertiesFile(final String file) {
        if (file != null) {
            addPropertiesFile(Paths.get(file));
        }
        return getThis();
    }

    /**
     * Adds a properties file to be passed to the server. Note that the file must exist.
     *
     * @param file the file to add
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the file does not exist or is not a regular file
     */
    public T addPropertiesFile(final Path file) {
        if (file != null) {
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
     * Sets the properties file to use for the server or {@code null} to remove the file. Note that the file must exist.
     * <p>
     * This will override any previous values set.
     * </p>
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
     * Sets the properties file to use for the server or {@code null} to remove the file. Note that the file must exist.
     * <p>
     * This will override any previous values set.
     * </p>
     *
     * @param file the properties file to use or {@code null}
     *
     * @return the builder
     *
     * @throws java.lang.IllegalArgumentException if the file does not exist or is not a regular file
     */
    public T setPropertiesFile(final Path file) {
        serverArgs.remove("-P");
        return addPropertiesFile(file);
    }

    /**
     * Returns the home directory used.
     *
     * @return the home directory
     */
    public Path getWildFlyHome() {
        return environment.getWildflyHome();
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
        return environment.getModuleJar().toString();
    }


    /**
     * Resolves the path to the path relative to the WildFly home directory.
     *
     * @param path the name of the relative path
     *
     * @return the normalized path
     */
    protected Path normalizePath(final String path) {
        return environment.resolvePath(path).toAbsolutePath().normalize();
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

    protected void setSingleServerArg(final String key, final String value) {
        serverArgs.set(key, value);
    }

    protected void addServerArg(final String key, final String value) {
        serverArgs.add(key, value);
    }

    /**
     * Returns the first argument from the server arguments.
     *
     * @param key the key to the argument
     *
     * @return the first argument or {@code null}
     */
    protected String getServerArg(final String key) {
        return serverArgs.get(key);
    }

    /**
     * Allows the subclass to do additional checking on whether the server argument should be added or not. In some
     * cases it may be desirable for the argument passed to be added or processed elsewhere.
     *
     * @param argument the argument to test
     *
     * @return {@code true} if the argument should be added to the server arguments, {@code false} if the argument is
     * handled by the subclass
     */
    abstract boolean addServerArgument(final Argument argument);

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

    protected static boolean isJavaSecurityManagerConfigured(final Argument argument) {
        return !ALLOW_VALUE.equals(argument.getValue()) && !DISALLOW_VALUE.equals(argument.getValue());
    }
}
