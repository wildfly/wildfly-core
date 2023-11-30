/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
abstract class AbstractCommandBuilder<T extends AbstractCommandBuilder<T>> extends JBossModulesCommandBuilder implements CommandBuilder {

    static final String HOME_DIR = Environment.HOME_DIR;

    private Path logDir;
    private Path configDir;

    protected AbstractCommandBuilder(final Path wildflyHome, final String moduleName) {
        this(wildflyHome, null, moduleName);
    }

    protected AbstractCommandBuilder(final Path wildflyHome, final Jvm jvm, final String moduleName) {
        super(wildflyHome, jvm, moduleName);
    }

    @Override
    public T setUseSecurityManager(final boolean useSecMgr) {
        super.setUseSecurityManager(useSecMgr);
        return getThis();
    }

    @Override
    public T addModuleDir(final String moduleDir) {
        super.addModuleDir(moduleDir);
        return getThis();
    }

    @Override
    public T addModuleDirs(final String... moduleDirs) {
        super.addModuleDirs(moduleDirs);
        return getThis();
    }

    @Override
    public T addModuleDirs(final Iterable<String> moduleDirs) {
        super.addModuleDirs(moduleDirs);
        return getThis();
    }

    @Override
    public T setModuleDirs(final Iterable<String> moduleDirs) {
        super.setModuleDirs(moduleDirs);
        return getThis();
    }

    @Override
    public T setModuleDirs(final String... moduleDirs) {
        super.setModuleDirs(moduleDirs);
        return getThis();
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
        configDir = Environment.validateAndNormalizeDir(path, true);
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
        configDir = Environment.validateAndNormalizeDir(path, true);
        return getThis();
    }

    @Override
    public T addServerArgument(final String arg) {
        super.addServerArgument(arg);
        return getThis();
    }

    @Override
    public T addServerArguments(final String... args) {
        super.addServerArguments(args);
        return getThis();
    }

    @Override
    public T addServerArguments(final Iterable<String> args) {
        super.addServerArguments(args);
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
    @SuppressWarnings("SameParameterValue")
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
     * Returns the concrete builder.
     *
     * @return the concrete builder
     */
    protected abstract T getThis();

    protected static void addSystemPropertyArg(final List<String> cmd, final String key, final Object value) {
        if (value != null) {
            cmd.add("-D" + key + "=" + value);
        }
    }
}
