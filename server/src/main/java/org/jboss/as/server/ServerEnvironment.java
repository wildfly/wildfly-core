/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLoggerImpl;
import org.jboss.as.controller.interfaces.InetAddressUtil;
import org.jboss.as.controller.operations.common.ProcessEnvironment;
import org.jboss.as.controller.persistence.ConfigurationExtension;
import org.jboss.as.controller.persistence.ConfigurationExtensionFactory;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.server.controller.git.GitRepository;
import org.jboss.as.server.controller.git.GitRepositoryConfiguration;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.Stability;
import org.jboss.as.version.ProductConfig;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.wildfly.common.cpu.ProcessorInfo;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;

/**
 * Encapsulates the runtime environment for a server.
 * This is parsed when the server is initially started, a process reload reuses the server environment.
 *
 * @author Brian Stansberry
 * @author Mike M. Clark
 */
public class ServerEnvironment extends ProcessEnvironment implements Serializable {

    public static final NullaryServiceDescriptor<ServerEnvironment> SERVICE_DESCRIPTOR = NullaryServiceDescriptor.of("org.wildfly.server.environment", ServerEnvironment.class);

    private static final long serialVersionUID = 1725061010357265545L;

    /**
     * The manner in which a server can be launched
     */
    public enum LaunchType {
        /**
         * Launched by a Host Controller in a managed domain
         */
        DOMAIN(ProcessType.DOMAIN_SERVER),
        /**
         * Launched from the command line
         */
        STANDALONE(ProcessType.STANDALONE_SERVER),
        /**
         * Launched by another process in which the server is embedded
         */
        EMBEDDED(ProcessType.EMBEDDED_SERVER),
        /**
         * Launched as self-contained WildFly Swarm server
         */
        SELF_CONTAINED(ProcessType.SELF_CONTAINED),
        /**
         * Launched by a Java EE appclient
         */
        APPCLIENT(ProcessType.APPLICATION_CLIENT);

        private final ProcessType processType;

        LaunchType(final ProcessType processType) {
            this.processType = processType;
        }

        public ProcessType getProcessType() {
            return processType;
        }
    }

    // ///////////////////////////////////////////////////////////////////////
    // Configuration Value Identifiers //
    // ///////////////////////////////////////////////////////////////////////
    /**
     * Constant that holds the name of the system property for specifying the JDK extension directory paths.
     */
    public static final String JAVA_EXT_DIRS = "java.ext.dirs";

    /**
     * Constant that holds the name of the system property for specifying
     * {@link #getHomeDir() the JBoss home directory}.
     */
    public static final String HOME_DIR = "jboss.home.dir";

    /**
     * VFS module identifier.
     */
    public static final String VFS_MODULE_IDENTIFIER = "org.jboss.vfs";

    /**
     * Constant that holds the name of the system property for specifying
     * {@link #getServerBaseDir() the server base directory}.
     *
     * <p>
     * Defaults to <tt><em>HOME_DIR</em>/standalone</tt>.
     */
    public static final String SERVER_BASE_DIR = "jboss.server.base.dir";

    /**
     * Constant that holds the name of the system property for specifying
     * {@link #getServerConfigurationDir() the server configuration directory}.
     *
     * <p>
     * Defaults to <tt><em>SERVER_BASE_DIR</em>/configuration</tt> .
     */
    public static final String SERVER_CONFIG_DIR = "jboss.server.config.dir";

    /**
     * Constant that holds the name of the system property for specifying
     * {@link #getServerDataDir()} () the server data directory}.
     *
     * <p>
     * Defaults to <tt><em>SERVER_BASE_DIR</em>/data</tt>.
     */
    public static final String SERVER_DATA_DIR = "jboss.server.data.dir";

    /**
     * Constant that holds the name of the system property for specifying
     * {@link #getServerContentDir() the server managed content repository directory}.
     *
     * <p>
     * Defaults to <tt><em>SERVER_DATA_DIR</em>/content</tt>.
     */
    public static final String SERVER_CONTENT_DIR = "jboss.server.content.dir";

    /**
     * Constant that holds the name of the system property for specifying
     * {@link #getServerLogDir() the server log directory}.
     *
     * <p>
     * Defaults to <tt><em>SERVER_BASE_DIR</em>/<em>log</em></tt>.
     */
    public static final String SERVER_LOG_DIR = "jboss.server.log.dir";

    /**
     * Constant that holds the name of the system property for specifying t
     * {@link #getServerTempDir() the server temp directory}.
     *
     * <p>
     * Defaults to <tt><em>SERVER_BASE_DIR</em>/tmp</tt> .
     */
    public static final String SERVER_TEMP_DIR = "jboss.server.temp.dir";

    /**
     * Common alias between domain and standalone mode. Equivalent to jboss.domain.temp.dir in a managed domain,
     * and jboss.server.temp.dir on a standalone server.
     */
    public static final String CONTROLLER_TEMP_DIR = "jboss.controller.temp.dir";

    /**
     * Constant that holds the name of the system property for specifying the node name within a cluster.
     */
    public static final String NODE_NAME = "jboss.node.name";

    /**
     * Constant that holds the name of the system property for specifying the name of this server instance.
     */
    public static final String SERVER_NAME = "jboss.server.name";

    /**
     * Constant that holds the name of the system property for specifying the local part of the name
     * of the host machine that this server is running on.
     */
    public static final String HOST_NAME = "jboss.host.name";

    /**
     * Constant that holds the name of the system property for specifying the fully-qualified name of the host
     * machine that this server is running on.
     */
    public static final String QUALIFIED_HOST_NAME = "jboss.qualified.host.name";

    /**
     * Constant that holds the name of the system property for specifying the max threads used by the bootstrap
     * ServiceContainer.
     */
    public static final String BOOTSTRAP_MAX_THREADS = "org.jboss.server.bootstrap.maxThreads";

    /**
     * The default system property used to store bind address information from the command-line (-b).
     */
    public static final String JBOSS_BIND_ADDRESS = "jboss.bind.address";

    /**
     * Prefix for the system property used to store qualified bind address information from the command-line (-bxxx).
     */
    public static final String JBOSS_BIND_ADDRESS_PREFIX = JBOSS_BIND_ADDRESS + ".";

    /**
     * The default system property used to store bind address information from the command-line (-b).
     */
    public static final String JBOSS_DEFAULT_MULTICAST_ADDRESS = "jboss.default.multicast.address";

    /**
     * The system property used to store the name of the default server configuration file. If not set,
     * the default domain configuration file is "standalone.xml". The default domain configuration file is only
     * relevant if the user does not use the {@code -c} or {@code --server-config} command line switches
     * to explicitly set the server configuration file.
     */
    public static final String JBOSS_SERVER_DEFAULT_CONFIG = "jboss.server.default.config";

    /**
     * The system property used to set the unique identifier for this server exposed via the
     * {@code uuid} attribute of the server's root management resource. If the property is not
     * set any previously persisted UUID will be used; otherwise a random UUID will be generated.
     */
    public static final String JBOSS_SERVER_MANAGEMENT_UUID = "jboss.server.management.uuid";

    /**
     * The system property used to indicate whether the server was configured to persist changes to the configuration
     * files.
     *
     * @deprecated for internal us only, may change or be removed at any time without notice
     */
    @Deprecated
    public static final String JBOSS_PERSIST_SERVER_CONFIG = "jboss.server.persist.config";

    public static final String DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    public static final String DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";

    /**
     * Properties that cannot be set via {@link #systemPropertyUpdated(String, String)}
     */
    private static final Set<String> ILLEGAL_PROPERTIES = new HashSet<>(Arrays.asList(DOMAIN_BASE_DIR,
            DOMAIN_CONFIG_DIR, JAVA_EXT_DIRS, HOME_DIR, "modules.path", SERVER_BASE_DIR, SERVER_CONFIG_DIR,
            SERVER_DATA_DIR, SERVER_LOG_DIR, BOOTSTRAP_MAX_THREADS, CONTROLLER_TEMP_DIR,
            JBOSS_SERVER_DEFAULT_CONFIG, JBOSS_PERSIST_SERVER_CONFIG, JBOSS_SERVER_MANAGEMENT_UUID, STABILITY));
    /**
     * Properties that can only be set via {@link #systemPropertyUpdated(String, String)} during server boot.
     */
    private static final Set<String> BOOT_PROPERTIES = new HashSet<>(Arrays.asList(SERVER_TEMP_DIR,
            NODE_NAME, SERVER_NAME, HOST_NAME, QUALIFIED_HOST_NAME, STABILITY));

    /**
     * Properties that we care about that were provided to the constructor (i.e. by the user via cmd line)
     */
    private final Properties primordialProperties;
    /**
     * Properties that we care about that have been provided by the user either via cmd line or
     * via {@link #systemPropertyUpdated(String, String)}
     */
    private final Properties providedProperties;
    /**
     * Whether the server name has been provided via {@link #setProcessName(String)}
     */
    private volatile boolean processNameSet;

    private final LaunchType launchType;
    private final String hostControllerName;
    private volatile String qualifiedHostName;
    private volatile String hostName;
    private volatile String serverName;
    private volatile String nodeName;

    private final File[] javaExtDirs;

    private final File homeDir;
    private final File serverBaseDir;
    private final File serverConfigurationDir;
    private final ConfigurationFile serverConfigurationFile;
    private final File serverLogDir;
    private final File controllerTempDir;
    private final File serverDataDir;
    private volatile File serverContentDir;
    private volatile File serverTempDir;
    private final File domainBaseDir;
    private final File domainConfigurationDir;

    private final boolean standalone;
    private final boolean allowModelControllerExecutor;
    private final RunningMode initialRunningMode;
    private final ProductConfig productConfig;
    private final RunningModeControl runningModeControl;
    private final UUID serverUUID;
    private final long startTime;
    private final boolean startSuspended;
    private final boolean startGracefully;
    private final GitRepository repository;
    private volatile Stability stability;

    public ServerEnvironment(final String hostControllerName, final Properties props, final Map<String, String> env, final String serverConfig,
            final ConfigurationFile.InteractionPolicy configInteractionPolicy, final LaunchType launchType,
            final RunningMode initialRunningMode, ProductConfig productConfig, boolean startSuspended) {
        this(hostControllerName, props, env, serverConfig, configInteractionPolicy, launchType, initialRunningMode, productConfig,
                System.currentTimeMillis(), startSuspended, false, null, null, null, null);
    }

    public ServerEnvironment(final String hostControllerName, final Properties props, final Map<String, String> env, final String serverConfig,
            final ConfigurationFile.InteractionPolicy configurationInteractionPolicy, final LaunchType launchType,
            final RunningMode initialRunningMode, ProductConfig productConfig, long startTime, boolean startSuspended,
            final boolean startGracefully, final String gitRepository, final String gitBranch, final String gitAuthConfiguration,
            final String supplementalConfiguration) {
        assert props != null;
        assert productConfig != null;
        ConfigurationFile.InteractionPolicy configInteractionPolicy = configurationInteractionPolicy;
        this.startSuspended = startSuspended;
        this.startGracefully = startGracefully;

        this.launchType = launchType;
        this.standalone = launchType != LaunchType.DOMAIN;

        this.productConfig = productConfig;

        this.initialRunningMode = initialRunningMode == null ? RunningMode.NORMAL : initialRunningMode;
        this.runningModeControl = new RunningModeControl(this.initialRunningMode);
        this.startTime = startTime;

        this.hostControllerName = hostControllerName;
        if (standalone && hostControllerName != null) {
            throw ServerLogger.ROOT_LOGGER.hostControllerNameNonNullInStandalone();
        }
        if (!standalone && hostControllerName == null) {
            throw ServerLogger.ROOT_LOGGER.hostControllerNameNullInDomain();
        }

        // Calculate qualified and unqualified host names, default server name, cluster node name
        configureQualifiedHostName(props.getProperty(QUALIFIED_HOST_NAME), props.getProperty(HOST_NAME), props, env);

        // Java system-wide extension dirs
        javaExtDirs = getFilesFromProperty(JAVA_EXT_DIRS, props);

        if (launchType.equals(LaunchType.SELF_CONTAINED)) {
            Path[] supplementalConfigurationFiles = findSupplementalConfigurationFiles(null, supplementalConfiguration);
            ConfigurationExtension configurationExtension = ConfigurationExtensionFactory.createConfigurationExtension(supplementalConfigurationFiles);
            if (configurationExtension != null) {
                configInteractionPolicy = configurationExtension.shouldProcessOperations(initialRunningMode) ? ConfigurationFile.InteractionPolicy.READ_ONLY : configInteractionPolicy;
            }
            homeDir = new File(WildFlySecurityManager.getPropertyPrivileged("user.dir", "."));
            serverBaseDir = new File(WildFlySecurityManager.getPropertyPrivileged("user.dir", "."));
            serverLogDir = new File(WildFlySecurityManager.getPropertyPrivileged("user.dir", "."));

            String serverDirProp = props.getProperty(SERVER_TEMP_DIR);
            if (null == serverDirProp) {
                throw ServerLogger.ROOT_LOGGER.requiredSystemPropertyMissing(SERVER_TEMP_DIR);
            }
            serverTempDir = new File(serverDirProp);

            serverDataDir = serverTempDir;
            serverConfigurationDir = null;
            serverConfigurationFile = null;
            controllerTempDir = serverTempDir;
            domainBaseDir = null;
            domainConfigurationDir = null;
            repository = null;
            this.stability = this.productConfig.getDefaultStability();
            WildFlySecurityManager.setPropertyPrivileged(ServerEnvironment.JBOSS_PERSIST_SERVER_CONFIG, "false");
        } else {

            // Must have HOME_DIR
            homeDir = getFileFromProperty(HOME_DIR, props);
            if (homeDir == null) {
                throw ServerLogger.ROOT_LOGGER.missingHomeDirConfiguration(HOME_DIR);
            }
            if (!homeDir.exists() || !homeDir.isDirectory()) {
                throw ServerLogger.ROOT_LOGGER.homeDirectoryDoesNotExist(homeDir);
            }

            File tmp = getFileFromProperty(SERVER_BASE_DIR, props);
            if (tmp == null) {
                tmp = new File(homeDir, standalone ? "standalone" : "domain/servers/" + serverName);
            }
            if (standalone) {
                if (!tmp.exists()) {
                    throw ServerLogger.ROOT_LOGGER.serverBaseDirectoryDoesNotExist(tmp);
                } else if (!tmp.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.serverBaseDirectoryIsNotADirectory(tmp);
                }
            } else {
                if (tmp.exists()) {
                    if (!tmp.isDirectory()) {
                        throw ServerLogger.ROOT_LOGGER.serverBaseDirectoryIsNotADirectory(tmp);
                    }
                } else if (!tmp.mkdirs()) {
                    throw ServerLogger.ROOT_LOGGER.couldNotCreateServerBaseDirectory(tmp);
                }
            }
            serverBaseDir = tmp;

            tmp = getFileFromProperty(SERVER_CONFIG_DIR, props);
            if (tmp == null) {
                tmp = new File(serverBaseDir, "configuration");
            }
            serverConfigurationDir = tmp;
            if (standalone && (!serverConfigurationDir.exists() || !serverConfigurationDir.isDirectory())) {
                throw ServerLogger.ROOT_LOGGER.configDirectoryDoesNotExist(serverConfigurationDir);
            }

            Path[] supplementalConfigurationFiles = findSupplementalConfigurationFiles(serverConfigurationDir.toPath(), supplementalConfiguration);
            ConfigurationExtension configurationExtension = ConfigurationExtensionFactory.createConfigurationExtension(supplementalConfigurationFiles);
            if (configurationExtension != null) {
                configInteractionPolicy = configurationExtension.shouldProcessOperations(initialRunningMode) ? ConfigurationFile.InteractionPolicy.READ_ONLY : configInteractionPolicy;
            }

            tmp = getFileFromProperty(SERVER_DATA_DIR, props);
            if (tmp == null) {
                tmp = new File(serverBaseDir, "data");
            }
            serverDataDir = tmp;
            if (serverDataDir.exists()) {
                if (!serverDataDir.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.serverDataDirectoryIsNotDirectory(serverDataDir);
                }
            } else {
                if (!serverDataDir.mkdirs()) {
                    throw ServerLogger.ROOT_LOGGER.couldNotCreateServerDataDirectory(serverDataDir);
                }
            }

            tmp = getFileFromProperty(SERVER_CONTENT_DIR, props);
            if (tmp == null) {
                tmp = new File(serverDataDir, "content");
            }
            serverContentDir = tmp;
            if (serverContentDir.exists()) {
                if (!serverContentDir.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.serverContentDirectoryIsNotDirectory(serverContentDir);
                }
            } else if (!serverContentDir.mkdirs()) {
                throw ServerLogger.ROOT_LOGGER.couldNotCreateServerContentDirectory(serverContentDir);
            }

            tmp = getFileFromProperty(SERVER_LOG_DIR, props);
            if (tmp == null) {
                tmp = new File(serverBaseDir, "log");
            }
            if (tmp.exists()) {
                if (!tmp.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.logDirectoryIsNotADirectory(tmp);
                }
            } else if (!tmp.mkdirs()) {
                throw ServerLogger.ROOT_LOGGER.couldNotCreateLogDirectory(tmp);
            }
            serverLogDir = tmp;

            tmp = configureServerTempDir(props.getProperty(SERVER_TEMP_DIR), props);
            if (tmp.exists()) {
                if (!tmp.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.serverTempDirectoryIsNotADirectory(tmp);
                }
            } else if (!tmp.mkdirs()) {
                throw ServerLogger.ROOT_LOGGER.couldNotCreateServerTempDirectory(tmp);
            }
            createAuthDir(tmp);

            tmp = getFileFromProperty(CONTROLLER_TEMP_DIR, props);
            if (tmp == null) {
                tmp = serverTempDir;
            }
            if (tmp.exists()) {
                if (!tmp.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.controllerTempDirectoryIsNotADirectory(tmp);
                }
            } else if (!tmp.mkdirs()) {
                throw ServerLogger.ROOT_LOGGER.couldNotCreateControllerTempDirectory(tmp);
            }
            controllerTempDir = tmp;
            String defaultServerConfig = WildFlySecurityManager.getPropertyPrivileged(JBOSS_SERVER_DEFAULT_CONFIG, "standalone.xml");
            GitRepositoryConfiguration gitConfiguration = GitRepositoryConfiguration.Builder.getInstance()
                    .setBasePath(serverBaseDir.toPath())
                    .setAuthenticationConfig(gitAuthConfiguration)
                    .setBranch(gitBranch)
                    .setRepository(gitRepository)
                    .setIgnored(listIgnoredFiles(defaultServerConfig))
                    .build();
            if (gitConfiguration != null) {
                try {
                    repository = new GitRepository(gitConfiguration);
                } catch (Exception ex) {
                    ServerLogger.ROOT_LOGGER.errorUsingGit(ex, ex.getMessage());
                    throw ServerLogger.ROOT_LOGGER.unableToInitialiseGitRepository(ex);
                }
            } else {
                repository = null;
            }

            this.stability = getEnumProperty(props, ProcessEnvironment.STABILITY, productConfig.getDefaultStability());
            final String translatedConfig = translateFileAlias(serverConfig, stability);
            serverConfigurationFile = standalone ? new ConfigurationFile(serverConfigurationDir, defaultServerConfig, translatedConfig, configInteractionPolicy, repository != null, serverTempDir, configurationExtension) : null;
            // Adds a system property to indicate whether or not the server configuration should be persisted
            @SuppressWarnings("deprecation")
            final String propertyKey = JBOSS_PERSIST_SERVER_CONFIG;
            WildFlySecurityManager.setPropertyPrivileged(propertyKey, Boolean.toString(configInteractionPolicy == null || !configInteractionPolicy.isReadOnly()));

            // Optional paths for the domain mode
            tmp = getFileFromProperty(DOMAIN_BASE_DIR, props);
            if (tmp != null) {
                if (!tmp.exists() || !tmp.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.domainBaseDirDoesNotExist(tmp);
                }
                this.domainBaseDir = tmp;
            } else {
                this.domainBaseDir = null;
            }
            tmp = getFileFromProperty(DOMAIN_CONFIG_DIR, props);
            if (tmp != null) {
                if (!tmp.exists() || !tmp.isDirectory()) {
                    throw ServerLogger.ROOT_LOGGER.domainConfigDirDoesNotExist(tmp);
                }
                this.domainConfigurationDir = tmp;
            } else {
                this.domainConfigurationDir = null;
            }

            if (!productConfig.getStabilitySet().contains(this.stability)) {
                throw ServerLogger.ROOT_LOGGER.unsupportedStability(this.stability, productConfig.getProductName());
            }
        }
        boolean allowExecutor = true;
        String maxThreads = WildFlySecurityManager.getPropertyPrivileged(BOOTSTRAP_MAX_THREADS, null);
        if (maxThreads != null && !maxThreads.isEmpty()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Integer.decode(maxThreads);
                // Property was set to a valid value; user wishes to control core service threads
                allowExecutor = false;
            } catch (NumberFormatException ex) {
                ServerLogger.ROOT_LOGGER.failedToParseCommandLineInteger(BOOTSTRAP_MAX_THREADS, maxThreads);
            }
        }
        allowModelControllerExecutor = allowExecutor;
        final Path filePath = this.serverDataDir.toPath().resolve(KERNEL_DIR).resolve(UUID_FILE);
        UUID uuid;
        try {
            String sysPropUUID = props.getProperty(JBOSS_SERVER_MANAGEMENT_UUID);
            uuid = obtainProcessUUID(filePath, sysPropUUID);
        } catch (IOException ex) {
            throw ServerLogger.ROOT_LOGGER.couldNotObtainServerUuidFile(ex, filePath);
        }
        this.serverUUID = uuid;

        // Keep a copy of the original properties
        this.primordialProperties = new Properties();
        copyProperties(props, primordialProperties);
        // Create a separate copy for tracking later changes
        this.providedProperties = new Properties();
        copyProperties(primordialProperties, providedProperties);

        // Provide standard system properties for environment items
        WildFlySecurityManager.setPropertyPrivileged(QUALIFIED_HOST_NAME, qualifiedHostName);
        WildFlySecurityManager.setPropertyPrivileged(HOST_NAME, hostName);
        WildFlySecurityManager.setPropertyPrivileged(SERVER_NAME, serverName);
        WildFlySecurityManager.setPropertyPrivileged(NODE_NAME, nodeName);
        setPathProperty(HOME_DIR, homeDir);
        setPathProperty(SERVER_BASE_DIR, serverBaseDir);
        setPathProperty(SERVER_CONFIG_DIR, serverConfigurationDir);
        setPathProperty(SERVER_DATA_DIR, serverDataDir);
        setPathProperty(SERVER_LOG_DIR, serverLogDir);
        setPathProperty(SERVER_TEMP_DIR, serverTempDir);

        if (launchType.getProcessType() == ProcessType.DOMAIN_SERVER) {
            if (domainBaseDir != null) {
                WildFlySecurityManager.setPropertyPrivileged(DOMAIN_BASE_DIR, domainBaseDir.getAbsolutePath());
            }
            if (domainConfigurationDir != null) {
                WildFlySecurityManager.setPropertyPrivileged(DOMAIN_CONFIG_DIR, domainConfigurationDir.getAbsolutePath());
            }
        }

        // Register the vfs module as URLStreamHandlerFactory
        try {
            ModuleLoader bootLoader = Module.getBootModuleLoader();
            Module vfsModule = bootLoader.loadModule(VFS_MODULE_IDENTIFIER);
            Module.registerURLStreamHandlerFactoryModule(vfsModule);
        } catch (Exception ex) {
            ServerLogger.ROOT_LOGGER.cannotAddURLStreamHandlerFactory(ex, VFS_MODULE_IDENTIFIER);
        }
    }

    private Set<String> listIgnoredFiles(String defaultServerConfig) {
        Set<String> ignored = new LinkedHashSet<>();
        setIgnored(ignored, serverDataDir.toPath(), true, false);
        setUnignored(ignored, serverDataDir.toPath(), false);
        setUnignored(ignored, serverContentDir.toPath(), true);
        setIgnored(ignored, serverConfigurationDir.toPath().resolve("logging.properties"), false, true);
        setIgnored(ignored, serverBaseDir.toPath().resolve("deployments"), false, false);
        setIgnored(ignored, serverLogDir.toPath(), false, false);
        setIgnored(ignored, serverTempDir.toPath(), false, false);
        setIgnored(ignored, serverConfigurationDir.toPath().resolve(defaultServerConfig.replace('.', '_') + "_history"), false, false);
        return ignored;
    }

    private void setIgnored(Set<String> ignored, Path path, boolean wilcard, boolean isFile) {
        final Path serverBasePath = serverBaseDir.toPath();
        if (path.startsWith(serverBasePath)) {
            String ignoredBasePath = serverBasePath.relativize(path).toString().replace('\\', '/');
            if (wilcard) {
                ignored.add(ignoredBasePath + "/*");
            } else {
                if (isFile) {
                    ignored.add(ignoredBasePath);
                } else {
                    ignored.add(ignoredBasePath + '/');
                }
            }
        }
    }

    private void setUnignored(Set<String> ignored, Path path, boolean dir) {
        final Path serverBasePath = serverBaseDir.toPath();
        if (path.startsWith(serverBasePath)) {
            ignored.add("!" + serverBasePath.relativize(path).toString().replace('\\', '/'));
        }
    }

    private void setPathProperty(String propertyName, File path) {
        if (this.launchType == LaunchType.SELF_CONTAINED && path == null) {
            return;
        }
        WildFlySecurityManager.setPropertyPrivileged(propertyName, path.getAbsolutePath());
    }

    private static void copyProperties(Properties src, Properties dest) {
        for (Map.Entry<Object, Object> entry : src.entrySet()) {
            Object key = entry.getKey();
            if (key instanceof String) {
                Object val = entry.getValue();
                if (val == null || val instanceof String) {
                    dest.setProperty((String) key, (String) val);
                }
            }
        }
    }

    private Path[] findSupplementalConfigurationFiles(final Path serverConfigurationDirPath, String yaml) {
        List<Path> yamlPaths = new ArrayList<>();
        StringJoiner joiner = new StringJoiner(", ");
        boolean error = false;
        if (yaml != null && !yaml.isEmpty()) {
            for (String yamlFile : yaml.split(File.pathSeparator)) {
                Path yamlPath = new File(yamlFile).toPath();
                if (!yamlPath.isAbsolute()) {
                    if (Files.exists(yamlPath) && Files.isRegularFile(yamlPath)) {
                        yamlPaths.add(yamlPath);
                    } else if (serverConfigurationDirPath != null) {
                        yamlPath = serverConfigurationDirPath.resolve(yamlFile);
                        if (Files.exists(yamlPath) && Files.isRegularFile(yamlPath)) {
                            yamlPaths.add(yamlPath);
                        } else {
                            error = true;
                            joiner.add('\'' + yamlFile + '\'');
                        }
                    } else {
                        error = true;
                        joiner.add('\'' + yamlFile + '\'');
                    }
                } else if (Files.exists(yamlPath) && Files.isRegularFile(yamlPath)) {
                    yamlPaths.add(yamlPath);
                } else {
                    error = true;
                    joiner.add('\'' + yamlFile + '\'');
                }
            }
        }
        if (error) {
            throw ServerLogger.ROOT_LOGGER.unableToFindYaml(joiner.toString());
        }
        return yamlPaths.toArray(new Path[0]);
    }

    void resetProvidedProperties() {
        // We're being reloaded, so restore state to where we were right after constructor was executed
        providedProperties.clear();
        copyProperties(primordialProperties, providedProperties);
        processNameSet = false;
    }

    /**
     * Get the name of this server's host controller. For domain-mode servers, this is the name given in the domain
     * configuration. For
     * standalone servers, which do not utilize a host controller, the value should be <code>null</code>.
     *
     * @return server's host controller name if the instance is running in domain mode, or <code>null</code> if running
     * in standalone
     * mode
     */
    @Override
    public String getHostControllerName() {
        return hostControllerName;
    }

    /**
     * Get the name of this server instance. For domain-mode servers, this is the name given in the domain
     * configuration. For
     * standalone servers, this is the name either provided in the server configuration, or, if not given, the name
     * specified
     * via {@link #SERVER_NAME system property}, or auto-detected based on {@link #getHostName() host name}.
     *
     * @return the server name
     */
    public String getServerName() {
        return serverName;
    }

    private void configureServerName(String serverName, Properties providedProperties) {
        if (serverName == null || serverName.isBlank()) {
            serverName = hostName;
        } else {
            providedProperties.setProperty(SERVER_NAME, serverName);
            // If necessary, convert jboss.domain.uuid into a UUID
            serverName = resolveGUID(serverName);
        }
        this.serverName = serverName;

        // Chain down to nodeName as serverName is the default for it
        configureNodeName(providedProperties.getProperty(NODE_NAME), providedProperties);
    }

    /**
     * Get the fully-qualified host name detected at server startup.
     *
     * @return the qualified host name
     */
    @Override
    public String getQualifiedHostName() {
        return qualifiedHostName;
    }

    private void configureQualifiedHostName(String qualifiedHostName, String providedHostName,
            Properties providedProperties,
            Map<String, String> env) {
        if (qualifiedHostName == null) {
            // if host name is specified, don't pick a qualified host name that isn't related to it
            qualifiedHostName = providedHostName;
            if (qualifiedHostName == null || qualifiedHostName.isBlank()) {
                // POSIX-like OSes including Mac should have this set
                qualifiedHostName = env.get("HOSTNAME");
            }
            if (qualifiedHostName == null || qualifiedHostName.isBlank()) {
                // Certain versions of Windows
                qualifiedHostName = env.get("COMPUTERNAME");
            }
            if (qualifiedHostName == null || qualifiedHostName.isBlank()) {
                try {
                    qualifiedHostName = NetworkUtils.canonize(InetAddressUtil.getLocalHost().getHostName());
                } catch (UnknownHostException e) {
                    qualifiedHostName = null;
                }
            }
            if (qualifiedHostName != null && qualifiedHostName.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$|:")) {
                // IP address is not acceptable
                qualifiedHostName = null;
            }
            if (qualifiedHostName == null || qualifiedHostName.isBlank()) {
                // Give up
                qualifiedHostName = "unknown-host.unknown-domain";
            } else {
                qualifiedHostName = qualifiedHostName.trim().toLowerCase(Locale.getDefault());
            }
        } else {
            providedProperties.setProperty(QUALIFIED_HOST_NAME, qualifiedHostName);
        }

        this.qualifiedHostName = qualifiedHostName;

        // Chain down to hostName as qualifiedHostName is the default for it
        configureHostName(providedProperties.getProperty(HOST_NAME), providedProperties);
    }

    /**
     * Get the local host name detected at server startup.
     *
     * @return the local host name
     */
    @Override
    public String getHostName() {
        return hostName;
    }

    private void configureHostName(String hostName, Properties providedProperties) {
        if (hostName == null || hostName.isBlank()) {
            providedProperties.remove(HOST_NAME);
            // Use the host part of the qualified host name
            final int idx = qualifiedHostName.indexOf('.');
            hostName = idx == -1 ? qualifiedHostName : qualifiedHostName.substring(0, idx);
        } else {
            providedProperties.setProperty(HOST_NAME, hostName);
        }
        this.hostName = hostName;

        // Chain down to serverName as hostName is the default for it
        configureServerName(providedProperties.getProperty(SERVER_NAME), providedProperties);
    }

    /**
     * Get the node name used for clustering purposes.
     *
     * @return the node name
     */
    public String getNodeName() {
        return nodeName;
    }

    private void configureNodeName(String nodeName, Properties providedProperties) {
        if (nodeName == null) {
            providedProperties.remove(NODE_NAME);
            nodeName = hostControllerName == null ? serverName : hostControllerName + ":" + serverName;
        } else {
            providedProperties.setProperty(NODE_NAME, nodeName);
        }
        this.nodeName = nodeName;
    }

    /**
     * Gets any Java extension directories.
     *
     * @return the java extension directories. Will not return {@code null}, but may be an empty array
     */
    public File[] getJavaExtDirs() {
        return javaExtDirs.clone();
    }

    /**
     * Gets the root directory for this JBoss installation.
     *
     * @return the root directory
     */
    public File getHomeDir() {
        return homeDir;
    }

    /**
     * Gets the based directory for this server.
     *
     * <p>
     * Defaults to <tt>{@link #getHomeDir() homeDir}/standalone</tt> for a standalone server or
     * <tt>domain/servers/<server-name></tt> for a managed domain server.</p>
     *
     * @return the base directory for the server
     */
    public File getServerBaseDir() {
        return serverBaseDir;
    }

    /**
     * Gets the directory in which server configuration files are stored.
     * <p>
     * Defaults to {@link #getServerBaseDir()} serverBaseDir}/configuration</p>
     *
     * @return the server configuration directory.
     */
    public File getServerConfigurationDir() {
        return serverConfigurationDir;
    }

    /**
     * Gets the {@link ConfigurationFile} that manages the server's configuration file.
     *
     * @return the configuration file
     */
    public ConfigurationFile getServerConfigurationFile() {
        return serverConfigurationFile;
    }

    /**
     * Gets the directory in which the server can store private internal state that
     * should survive a process restart.
     * <p>
     * Defaults to {@link #getServerBaseDir()} serverBaseDir}/data</p>
     *
     * @return the internal state persistent storage directory
     */
    public File getServerDataDir() {
        return serverDataDir;
    }

    /**
     * Gets the directory in which the server will store server-managed user content (e.g. deployments.)
     *
     * <p>
     * Defaults to {@link #getServerDataDir()} serverDataDir}/content</p>
     *
     * @return the domain managed content storage directory
     */
    public File getServerContentDir() {
        return serverContentDir;
    }

    /**
     * Gets the directory in which the server can write log files.
     * <p>
     * Defaults to {@link #getServerBaseDir()} serverBaseDir}/log</p>
     *
     * @return the log file directory for the server.
     */
    public File getServerLogDir() {
        return serverLogDir;
    }

    /**
     * Gets the directory in which athe server can store private internal state that
     * does not need to survive a process restart.
     * <p>
     * Defaults to {@link #getServerBaseDir()} serverBaseDir}/tmp</p>
     *
     * @return the internal state temporary storage directory for the server.
     */
    public File getServerTempDir() {
        return serverTempDir;
    }

    /**
     * If this is true then the server will start in suspended mode
     *
     * @return <code>true</code> if the server should start in suspended mode
     */
    public boolean isStartSuspended() {
        return startSuspended;
    }

    /**
     * If this is true then the server will start with graceful startup disabled
     *
     * @return <code>true</code> if the server should start with graceful startup disabled
     */
    public boolean isStartGracefully() {
        return startGracefully;
    }

    private File configureServerTempDir(String path, Properties providedProps) {
        File tmp = getFileFromPath(path);
        if (tmp == null) {
            providedProps.remove(SERVER_TEMP_DIR);
            tmp = new File(serverBaseDir, "tmp");
        } else {
            providedProps.setProperty(SERVER_TEMP_DIR, path);
        }
        serverTempDir = tmp;
        return tmp;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void createAuthDir(File tempDir) {
        File authDir = new File(tempDir, "auth");
        if (authDir.exists()) {
            if (!authDir.isDirectory()) {
                throw ServerLogger.ROOT_LOGGER.unableToCreateTempDirForAuthTokensFileExists();
            }
        } else if (!authDir.mkdirs()) {
            // there is a race if multiple services are starting for the same
            // security realm
            if (!authDir.isDirectory()) {
                throw ServerLogger.ROOT_LOGGER.unableToCreateAuthDir(authDir.getAbsolutePath());
            }
        } else {
            // As a precaution make perms user restricted for directories created (if the OS allows)
            authDir.setWritable(false, false);
            authDir.setWritable(true, true);
            authDir.setReadable(false, false);
            authDir.setReadable(true, true);
            authDir.setExecutable(false, false);
            authDir.setExecutable(true, true);
        }
    }

    // BES 2012/02/04 made package protected as I cannot find use for it other than to create a PathService
    // So, the integration hook is the name of the path service, not this method.
    File getControllerTempDir() {
        return controllerTempDir;
    }

    /**
     * Gets the base directory in which managed domain files are stored.
     * <p>
     * Defaults to {@link #getHomeDir() JBOSS_HOME}/domain</p>
     *
     * @return the domain base directory, or {@code null} if this server is not running in a managed domain.
     */
    public File getDomainBaseDir() {
        return domainBaseDir;
    }

    /**
     * Gets the directory in which managed domain configuration files are stored.
     * <p>
     * Defaults to {@link #getDomainBaseDir()} domainBaseDir}/configuration</p>
     *
     * @return the domain configuration directory, or {@code null} if this server is not running in a managed domain.
     */
    public File getDomainConfigurationDir() {
        return domainConfigurationDir;
    }

    /**
     * Gets the manner in which this server was launched
     *
     * @return the launch type
     */
    public LaunchType getLaunchType() {
        return launchType;
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }

    @Override
    public Set<Stability> getStabilities() {
        return this.productConfig.getStabilitySet();
    }

    /**
     * Gets whether this server is an independently managed server, not managed as part of a managed domain.
     *
     * @return {@code true} if this server is an independently managed server
     */
    public boolean isStandalone() {
        return standalone;
    }

    /**
     * Gets whether this server is a self-contained (no filesystem layout) or not.
     *
     * @return {@code true} if this server is self-contained
     */
    public boolean isSelfContained() {
        return this.launchType == LaunchType.SELF_CONTAINED;
    }

    /**
     * Gets the {@link RunningMode} that was in effect when this server was launched.
     *
     * @return the initial running mode
     */
    public RunningMode getInitialRunningMode() {
        return initialRunningMode;
    }

    /**
     * Get the {@link RunningModeControl} containing the current running mode of the server
     *
     * @return the running mode control
     */
    @Override
    public RunningModeControl getRunningModeControl() {
        return runningModeControl;
    }

    // package protected for now as this is not a stable API
    boolean isAllowModelControllerExecutor() {
        return allowModelControllerExecutor;
    }

    /**
     * Gets the {@link ProductConfig} detected at startup.
     *
     * @return the product config. Will not be {@code null}
     */
    public ProductConfig getProductConfig() {
        return productConfig;
    }

    @Override
    public UUID getInstanceUuid() {
        return this.serverUUID;
    }

    /**
     * Gets the time when this process was started. Note that a process reload does not change this value.
     *
     * @return the time, in ms since the epoch
     */
    public long getStartTime() {
        return startTime;
    }

    public boolean useGit() {
        return this.repository != null;
    }

    public GitRepository getGitRepository() {
        return repository;
    }

    public ConfigurationExtension getConfigurationExtension() {
        return this.getServerConfigurationFile() != null ? this.getServerConfigurationFile().getConfigurationExtension() : null;
    }

    /**
     * Determine the number of threads to use for the bootstrap service container. This reads
     * the {@link #BOOTSTRAP_MAX_THREADS} system property and if not set, defaults to 2*cpus.
     *
     * @see Runtime#availableProcessors()
     * @return the maximum number of threads to use for the bootstrap service container.
     */
    public static int getBootstrapMaxThreads() {
        // Base the bootstrap thread on proc count if not specified
        int cpuCount = ProcessorInfo.availableProcessors();
        int defaultThreads = cpuCount * 2;
        String maxThreads = WildFlySecurityManager.getPropertyPrivileged(BOOTSTRAP_MAX_THREADS, null);
        if (maxThreads != null && !maxThreads.isEmpty()) {
            try {
                int max = Integer.decode(maxThreads);
                defaultThreads = Math.max(max, 1);
            } catch (NumberFormatException ex) {
                ServerLogger.ROOT_LOGGER.failedToParseCommandLineInteger(BOOTSTRAP_MAX_THREADS, maxThreads);
            }
        }
        return defaultThreads;
    }

    @Override
    protected String getProcessName() {
        return serverName;
    }

    @Override
    protected void setProcessName(String processName) {
        if (processName != null) {
            if (primordialProperties.contains(SERVER_NAME)) {
                // User specified both -Djboss.server.name and a standalone.xml <server name="xxx"/> value.
                // Log a WARN
                String rawServerProp = WildFlySecurityManager.getPropertyPrivileged(SERVER_NAME, serverName);
                ServerLogger.AS_ROOT_LOGGER.duplicateServerNameConfiguration(SERVER_NAME, rawServerProp, processName);
            }
            serverName = processName;
            WildFlySecurityManager.setPropertyPrivileged(SERVER_NAME, serverName);
            processNameSet = true;
            if (!primordialProperties.contains(NODE_NAME)) {
                nodeName = serverName;
                WildFlySecurityManager.setPropertyPrivileged(NODE_NAME, nodeName);
            }
        }
    }

    @Override
    protected boolean isRuntimeSystemPropertyUpdateAllowed(String propertyName, String propertyValue, boolean bootTime) throws OperationFailedException {
        if (ILLEGAL_PROPERTIES.contains(propertyName)) {
            throw ServerLogger.ROOT_LOGGER.systemPropertyNotManageable(propertyName);
        }
        if (processNameSet && SERVER_NAME.equals(propertyName)) {
            throw ServerLogger.ROOT_LOGGER.systemPropertyCannotOverrideServerName(SERVER_NAME);
        }
        return bootTime || !BOOT_PROPERTIES.contains(propertyName);
    }

    @Override
    protected void systemPropertyUpdated(String propertyName, String propertyValue) {
        if (BOOT_PROPERTIES.contains(propertyName)) {
            if (SERVER_TEMP_DIR.equals(propertyName)) {
                configureServerTempDir(propertyValue, providedProperties);
            } else if (QUALIFIED_HOST_NAME.equals(propertyName)) {
                configureQualifiedHostName(propertyValue, providedProperties.getProperty(HOST_NAME),
                        providedProperties, WildFlySecurityManager.getSystemEnvironmentPrivileged());
            } else if (HOST_NAME.equals(propertyName)) {
                configureHostName(propertyValue, providedProperties);
            } else if (SERVER_NAME.equals(propertyName)) {
                configureServerName(propertyValue, providedProperties);
            } else if (NODE_NAME.equals(propertyName)) {
                configureNodeName(propertyValue, providedProperties);
            }
        }
    }


    public void checkStabilityIsValidForInstallation(Stability stability) {
        checkStabilityIsValidForInstallation(productConfig, stability);
    }

    private void checkStabilityIsValidForInstallation(ProductConfig productConfig, Stability stability) {
        if (!productConfig.getStabilitySet().contains(stability)) {
            throw ServerLogger.ROOT_LOGGER.unsupportedStability(this.stability, productConfig.getProductName());
        }
    }

    // TODO Figure out how to make this package protected
    public void setStability(Stability stability) {
        WildFlySecurityManager.setPropertyPrivileged(ProcessEnvironment.STABILITY, stability.toString());
        this.stability = stability;
    }

    /**
     * Get a File from configuration.
     *
     * @param name the name of the property
     * @param props the set of configuration properties
     *
     * @return the CanonicalFile form for the given name.
     */
    private File getFileFromProperty(final String name, final Properties props) {
        return getFileFromPath(props.getProperty(name));
    }

    private static <E extends Enum<E>> E getEnumProperty(Properties properties, String key, E defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        Class<E> enumClass = defaultValue.getDeclaringClass();
        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ServerLogger.ROOT_LOGGER.failedToParseEnumProperty(key, value, EnumSet.allOf(enumClass));
        }
    }

    /**
     * Get a File from configuration.
     *
     * @param path the file path
     *
     * @return the CanonicalFile form for the given name.
     */
    private File getFileFromPath(final String path) {
        File result = (path != null) ? new File(path) : null;
        // AS7-1752 see if a non-existent relative path exists relative to the home dir
        if (result != null && homeDir != null && !result.exists() && !result.isAbsolute()) {
            File relative = new File(homeDir, path);
            if (relative.exists()) {
                result = relative;
            }
        }
        return result;
    }

    private static final File[] NO_FILES = new File[0];

    /**
     * Get a File path list from configuration.
     *
     * @param name the name of the property
     * @param props the set of configuration properties
     *
     * @return the CanonicalFile form for the given name.
     */
    private File[] getFilesFromProperty(final String name, final Properties props) {
        String sep = WildFlySecurityManager.getPropertyPrivileged("path.separator", null);
        String value = props.getProperty(name, null);
        if (value != null) {
            final String[] paths = value.split(Pattern.quote(sep));
            final int len = paths.length;
            final File[] files = new File[len];
            for (int i = 0; i < len; i++) {
                files[i] = new File(paths[i]);
            }
            return files;
        }
        return NO_FILES;
    }

    ManagedAuditLogger createAuditLogger() {
        return new ManagedAuditLoggerImpl(getProductConfig().resolveVersion(), true);
    }

    public static String translateFileAlias(String alias, Stability stability) {
        if (!stability.enables(Stability.COMMUNITY) || alias == null) {
            return alias;
        }
        switch (alias) {
            case "full":
            case "ha":
            case "full-ha":
            case "load-balancer":
            case "microprofile":
            case "microprofile-ha":
                break;
            case "fha":   alias = "full-ha"; break;
            case "lb":    alias = "load-balancer"; break;
            case "mp":    alias = "microprofile"; break;
            case "mpha":  alias = "microprofile-ha"; break;
            default:      return alias;
        }
        return "standalone-" + alias + ".xml";
    }
}
