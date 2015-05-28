/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
import java.util.List;

import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
abstract class AbstractServerCommandBuilder<T extends AbstractServerCommandBuilder<T>> extends AbstractCommandBuilder<T> {
    static final String SECURITY_MANAGER_ARG = "-secmgr";
    static final String SECURITY_MANAGER_PROP = "java.security.manager";
    static final String[] DEFAULT_VM_ARGUMENTS;

    static {
        final String jvmVersion = System.getProperty("java.specification.version");
        final Collection<String> javaOpts = new ArrayList<>();
        // Default JVM parameters for all versions
        javaOpts.add("-Xms64m");
        javaOpts.add("-Xmx512m");
        javaOpts.add("-Djava.net.preferIPv4Stack=true");
        javaOpts.add("-Djava.awt.headless=true");
        javaOpts.add("-Djboss.modules.system.pkgs=org.jboss.byteman");

        // Versions below 8 should add a MaxPermSize
        if (VersionComparator.compareVersion(jvmVersion, "1.8") < 0) {
            javaOpts.add("-XX:MaxPermSize=256m");
        }
        DEFAULT_VM_ARGUMENTS = javaOpts.toArray(new String[javaOpts.size()]);
    }

    private boolean useSecMgr;
    private Path logDir;
    private Path configDir;
    private final Arguments serverArgs;

    protected AbstractServerCommandBuilder(final Path wildflyHome) {
        super(wildflyHome);
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
     * @throws IllegalArgumentException if the directory is not a valid directory
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
     * @throws IllegalArgumentException if the directory is not a valid directory
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
     * @throws IllegalArgumentException if the directory is not a valid directory
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
     * @throws IllegalArgumentException if the directory is not a valid directory
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
     * @throws IllegalArgumentException if the file does not exist or is not a regular file
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
     * @throws IllegalArgumentException if the file does not exist or is not a regular file
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
     * Returns the base directory for the server.
     * <p/>
     * Example:
     * For standalone the base directory would be {@code $JBOSS_HOME/standalone}.
     *
     * @return the base directory
     */
    public abstract Path getBaseDirectory();

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
     * A collection of server command line arguments.
     *
     * @return the server arguments
     */
    public List<String> getServerArguments() {
        return serverArgs.asList();
    }

    protected void addServerArg(final String key, final String value) {
        serverArgs.add(key, value);
    }

    protected String getServerArg(final String key) {
        return serverArgs.get(key);
    }
}
