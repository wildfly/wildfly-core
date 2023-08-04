/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.operations.common.ProcessEnvironment;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.jvm.JvmType;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.FeatureStream;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.Assert;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Encapsulates the runtime environment for a host controller.
 * This is parsed when the host controller is initially started, a process reload reuses the host controller environment.
 *
 * @author Brian Stansberry
 */
public class HostControllerEnvironment extends ProcessEnvironment {


    /////////////////////////////////////////////////////////////////////////
    //                   Configuration Value Identifiers                   //
    /////////////////////////////////////////////////////////////////////////

    /**
     * Constant that holds the name of the system property
     * for specifying the {@link #getHomeDir() home directory}.
     */
    public static final String HOME_DIR = "jboss.home.dir";

    /**
     * Constant that holds the name of the system property for specifying the directory returned from
     * {@link #getModulesDir()}.
     *
     * <p>
     * Defaults to <tt><em>HOME_DIR</em>/modules</tt>/
     * </p>
     *
     * <strong>This system property has no real meaning and should not be regarded as providing any sort of useful
     * information.</strong> The "modules" directory is the default location from which JBoss Modules looks to find
     * modules. However, this behavior is in no way controlled by this system property, nor is it guaranteed that
     * modules will be loaded from only one directory, nor is it guaranteed that the "modules" directory will be one
     * of the directories used. Finally, the structure and contents of any directories from which JBoss Modules loads
     * resources is not something available from this class. Users wishing to interact with the modular classloading
     * system should use the APIs provided by JBoss Modules
     *
     *
     * @deprecated  has no useful meaning
     */
    @Deprecated
    public static final String MODULES_DIR = "jboss.modules.dir";

    /**
     * Constant that holds the name of the system property
     * for specifying {@link #getDomainBaseDir()} the domain base directory}.
     *
     * <p>Defaults to <tt><em>HOME_DIR</em>/domain</tt>.
     */
    public static final String DOMAIN_BASE_DIR = "jboss.domain.base.dir";

    /**
     * Constant that holds the name of the system property
     * for specifying {@link #getDomainConfigurationDir()} the domain configuration directory}.
     *
     * <p>Defaults to <tt><em>DOMAIN_BASE_DIR</em>/configuration</tt> .
     */
    public static final String DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";

    /**
     * Constant that holds the name of the system property
     * for specifying {@link #getDomainDataDir()} the domain data directory}.
     *
     * <p>Defaults to <tt><em>DOMAIN_BASE_DIR</em>/data</tt>.
     */
    public static final String DOMAIN_DATA_DIR = "jboss.domain.data.dir";

    /**
     * Constant that holds the name of the system property
     * for specifying {@link #getDomainContentDir()} the domain content repository directory}.
     *
     * <p>Defaults to <tt><em>DOMAIN_DATA_DIR</em>/content</tt>.
     */
    public static final String DOMAIN_CONTENT_DIR = "jboss.domain.content.dir";

    /**
     * Deprecated variant of {@link #DOMAIN_CONTENT_DIR}.
     *
     * @deprecated use {@link #DOMAIN_CONTENT_DIR}
     */
    @Deprecated
    public static final String DOMAIN_DEPLOYMENT_DIR = "jboss.domain.deployment.dir";

    /**
     * Constant that holds the name of the system property
     * for specifying {@link #getDomainLogDir()} the domain log directory}.
     *
     * <p>Defaults to <tt><em>DOMAIN_BASE_DIR</em>/<em>log</em></tt>.
     */
    public static final String DOMAIN_LOG_DIR = "jboss.domain.log.dir";

    /**
     * Constant that holds the name of the system property
     * for specifying {@link #getDomainServersDir()} the managed domain server parent directory}.
     *
     * <p>Defaults to <tt><em>DOMAIN_BASE_DIR</em>/<em>servers</em></tt>.
     */
    public static final String DOMAIN_SERVERS_DIR = "jboss.domain.servers.dir";

    /**
     * Constant that holds the name of the system property
     * for specifying {@link #getDomainTempDir()} the domain temporary file storage directory}.
     *
     * <p>Defaults to <tt><em>DOMAIN_BASE_DIR</em>/tmp</tt> .
     */
    public static final String DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";

    /**
     * Constant that holds the name of the system property for specifying the local part of the name of the host that this
     * server is running on.
     */
    public static final String HOST_NAME = "jboss.host.name";

    /**
     * Constant that holds the name of the system property for specifying the fully-qualified name of the host that this server
     * is running on.
     */
    public static final String QUALIFIED_HOST_NAME = "jboss.qualified.host.name";

    /**
     * Common alias between domain and standalone mode. Uses jboss.domain.temp.dir on domain mode,
     * and jboss.server.temp.dir on standalone server mode.
     */
    public static final String CONTROLLER_TEMP_DIR = "jboss.controller.temp.dir";

    /**
     * The default system property used to store bind address information from the command-line (-b).
     */
    public static final String JBOSS_BIND_ADDRESS = "jboss.bind.address";

    /**
     * Prefix for the system property used to store qualified bind address information from the command-line (-bxxx).
     */
    public static final String JBOSS_BIND_ADDRESS_PREFIX = JBOSS_BIND_ADDRESS + ".";

    /**
     * The default system property used to store multicast address information from the command-line (-u).
     */
    public static final String JBOSS_DEFAULT_MULTICAST_ADDRESS = "jboss.default.multicast.address";

    /**
     * The default system property used to store the primary Host Controller's native management interface address
     * from the command line.
     */
    public static final String JBOSS_DOMAIN_PRIMARY_ADDRESS = "jboss.domain.primary.address";

    /**
     * The default system property used to store the primary Host Controller's native of the primary port from the command line.
     */
    public static final String JBOSS_DOMAIN_PRIMARY_PORT = "jboss.domain.primary.port";

    /**
     * The system property used to store the name of the default domain configuration file. If not set,
     * the default domain configuration file is "domain.xml". The default domain configuration file is only
     * relevant if the user does not use the {@code -c} or {@code --domain-config} command line switches
     * to explicitly set the domain configuration file.
     */
    public static final String JBOSS_DOMAIN_DEFAULT_CONFIG = "jboss.domain.default.config";

    /**
     * The system property used to store the name of the default host configuration file. If not set,
     * the default domain configuration file is "host.xml". The default domain configuration file is only
     * relevant if the user does not use the {@code --host-config} command line switch
     * to explicitly set the host configuration file.
     */
    public static final String JBOSS_HOST_DEFAULT_CONFIG = "jboss.host.default.config";

    /**
     * The system property used to set the unique identifier for this host exposed via the
     * {@code uuid} attribute of the root resource of the host's portion of the management resource tree.
     * If the property is not set any previously persisted UUID will be used; otherwise a random UUID will be generated.
     */
    public static final String JBOSS_HOST_MANAGEMENT_UUID = "jboss.host.management.uuid";

    private final Map<String, String> hostSystemProperties;
    private final InetAddress processControllerAddress;
    private final Integer processControllerPort;
    private final InetAddress hostControllerAddress;
    private final Integer hostControllerPort;
    private final File homeDir;
    private final File modulesDir;
    private final File domainBaseDir;
    private final File domainConfigurationDir;
    private final ConfigurationFile hostConfigurationFile;
    private final String domainConfig;
    private final String initialDomainConfig;
    private ConfigurationFile domainConfigurationFile;
    private final ConfigurationFile.InteractionPolicy domainConfigInteractionPolicy;
    private final File domainContentDir;
    private final File domainDataDir;
    private final File domainLogDir;
    private final File domainServersDir;
    private final File domainTempDir;
    private final JvmType defaultJvm;
    private final boolean isRestart;
    private final boolean backupDomainFiles;
    private final boolean useCachedDc;

    private final RunningMode initialRunningMode;
    private final FeatureStream stream;
    private final ProductConfig productConfig;
    private final String qualifiedHostName;
    private final String hostName;
    private final String modulePath;

    private volatile String hostControllerName;
    private final HostRunningModeControl runningModeControl;
    private final boolean securityManagerEnabled;
    private final UUID hostControllerUUID;
    private final long startTime;
    private final ProcessType processType;

    /** Only for test cases */
    public HostControllerEnvironment(Map<String, String> hostSystemProperties, boolean isRestart, String modulePath,
                                     InetAddress processControllerAddress, Integer processControllerPort, InetAddress hostControllerAddress,
                                     Integer hostControllerPort, String defaultJVM, String domainConfig, String initialDomainConfig, String hostConfig,
                                     String initialHostConfig, RunningMode initialRunningMode, boolean backupDomainFiles, boolean useCachedDc, ProductConfig productConfig) {
        this(hostSystemProperties, isRestart, modulePath, processControllerAddress, processControllerPort, hostControllerAddress, hostControllerPort, defaultJVM,
                domainConfig, initialDomainConfig, hostConfig, initialHostConfig, initialRunningMode, backupDomainFiles, useCachedDc, productConfig, false,
                System.currentTimeMillis(), ProcessType.HOST_CONTROLLER, ConfigurationFile.InteractionPolicy.STANDARD, ConfigurationFile.InteractionPolicy.STANDARD);
    }

    public HostControllerEnvironment(Map<String, String> hostSystemProperties, boolean isRestart, String modulePath,
                                     InetAddress processControllerAddress, Integer processControllerPort, InetAddress hostControllerAddress,
                                     Integer hostControllerPort, String defaultJVM, String domainConfig, String initialDomainConfig, String hostConfig,
                                     String initialHostConfig, RunningMode initialRunningMode, boolean backupDomainFiles, boolean useCachedDc,
                                     ProductConfig productConfig, boolean securityManagerEnabled, long startTime, ProcessType processType,
                                     ConfigurationFile.InteractionPolicy hostConfigInteractionPolicy, ConfigurationFile.InteractionPolicy domainConfigInteractionPolicy) {

        this.hostSystemProperties = Collections.unmodifiableMap(Assert.checkNotNullParam("hostSystemProperties", hostSystemProperties));
        Assert.checkNotNullParam("modulePath", modulePath);
        Assert.checkNotNullParam("processControllerAddress", processControllerAddress);
        Assert.checkNotNullParam("processControllerPort", processControllerPort);
        Assert.checkNotNullParam("hostControllerAddress", hostControllerAddress);
        Assert.checkNotNullParam("hostControllerPort", hostControllerPort);
        this.processControllerPort = processControllerPort;
        this.processControllerAddress = processControllerAddress;
        this.hostControllerAddress = hostControllerAddress;
        this.hostControllerPort = hostControllerPort;
        this.isRestart = isRestart;
        this.modulePath = modulePath;
        this.startTime = startTime;
        this.initialRunningMode = initialRunningMode;
        this.runningModeControl = new HostRunningModeControl(initialRunningMode, RestartMode.SERVERS);
        this.domainConfigInteractionPolicy = domainConfigInteractionPolicy;

        // Calculate host and default server name
        String hostName = hostSystemProperties.get(HOST_NAME);
        String qualifiedHostName = hostSystemProperties.get(QUALIFIED_HOST_NAME);
        if (qualifiedHostName == null) {
            Map<String, String> env = null;
            // if host name is specified, don't pick a qualified host name that isn't related to it
            qualifiedHostName = hostName;
            if (qualifiedHostName == null) {
                env = WildFlySecurityManager.getSystemEnvironmentPrivileged();
                // POSIX-like OSes including Mac should have this set
                qualifiedHostName = env.get("HOSTNAME");
            }
            if (qualifiedHostName == null) {
                // Certain versions of Windows
                qualifiedHostName = env.get("COMPUTERNAME");
            }
            if (qualifiedHostName == null) {
                try {
                    qualifiedHostName = NetworkUtils.canonize(InetAddress.getLocalHost().getHostName());
                } catch (UnknownHostException e) {
                    qualifiedHostName = null;
                }
            }
            if (qualifiedHostName != null && qualifiedHostName.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$|:")) {
                // IP address is not acceptable
                qualifiedHostName = null;
            }
            if (qualifiedHostName == null) {
                // Give up
                qualifiedHostName = "unknown-host.unknown-domain";
            } else {
                qualifiedHostName = qualifiedHostName.trim().toLowerCase(Locale.getDefault());
            }
        }
        this.qualifiedHostName = qualifiedHostName;
        this.hostControllerName = qualifiedHostName;

        if (hostName == null) {
            // Use the host part of the qualified host name
            final int idx = qualifiedHostName.indexOf('.');
            hostName = idx == -1 ? qualifiedHostName : qualifiedHostName.substring(0, idx);
        }
        this.hostName = hostName;

        File home = getFileFromProperty(HOME_DIR);
        if (home == null) {
            if (processType != ProcessType.EMBEDDED_HOST_CONTROLLER) {
                throw HostControllerLogger.ROOT_LOGGER.missingHomeDirConfiguration(HOME_DIR);
            } else {
                // only for embedded HC
                String homeStr = WildFlySecurityManager.getPropertyPrivileged(HOME_DIR, null);
                if (homeStr == null)
                    throw HostControllerLogger.ROOT_LOGGER.missingHomeDirConfiguration(HOME_DIR);
                home = new File(homeStr);
            }
        }
        this.homeDir = home;

        if (!homeDir.exists() || !homeDir.isDirectory()) {
            throw HostControllerLogger.ROOT_LOGGER.homeDirectoryDoesNotExist(homeDir);
        }
        WildFlySecurityManager.setPropertyPrivileged(HOME_DIR, this.homeDir.getAbsolutePath());

        @SuppressWarnings("deprecation")
        File tmp = getFileFromProperty(MODULES_DIR);
        if (tmp == null) {
            tmp = new File(this.homeDir, "modules");
        }
        this.modulesDir = tmp;
        @SuppressWarnings("deprecation")
        String deprecatedModDir = MODULES_DIR;
        WildFlySecurityManager.setPropertyPrivileged(deprecatedModDir, this.modulesDir.getAbsolutePath());

        tmp = getFileFromProperty(DOMAIN_BASE_DIR);
        if (tmp == null) {
            tmp = new File(this.homeDir, "domain");
        }
        if (!tmp.exists()) {
            throw HostControllerLogger.ROOT_LOGGER.domainBaseDirectoryDoesNotExist(tmp);
        } else if (!tmp.isDirectory()) {
            throw HostControllerLogger.ROOT_LOGGER.domainBaseDirectoryIsNotADirectory(tmp);
        }
        this.domainBaseDir = tmp;
        WildFlySecurityManager.setPropertyPrivileged(DOMAIN_BASE_DIR, this.domainBaseDir.getAbsolutePath());

        tmp = getFileFromProperty(DOMAIN_CONFIG_DIR);
        if (tmp == null) {
            tmp = new File(this.domainBaseDir, "configuration");
        }
        if (!tmp.exists() || !tmp.isDirectory()) {
            throw HostControllerLogger.ROOT_LOGGER.configDirectoryDoesNotExist(tmp);
        }
        this.domainConfigurationDir = tmp;
        WildFlySecurityManager.setPropertyPrivileged(DOMAIN_CONFIG_DIR, this.domainConfigurationDir.getAbsolutePath());

        this.domainConfig = domainConfig;
        this.initialDomainConfig = initialDomainConfig;

        tmp = getFileFromProperty(DOMAIN_DATA_DIR);
        if (tmp == null) {
            tmp = new File(this.domainBaseDir, "data");
        }
        this.domainDataDir = tmp;
        if (domainDataDir.exists()) {
            if (!domainDataDir.isDirectory()) {
                throw HostControllerLogger.ROOT_LOGGER.domainDataDirectoryIsNotDirectory(domainDataDir);
            }
        } else {
            if (!domainDataDir.mkdirs()) {
                throw HostControllerLogger.ROOT_LOGGER.couldNotCreateDomainDataDirectory(domainDataDir);
            }
        }
        WildFlySecurityManager.setPropertyPrivileged(DOMAIN_DATA_DIR, this.domainDataDir.getAbsolutePath());

        @SuppressWarnings("deprecation")
        String deprecatedDepDir = DOMAIN_DEPLOYMENT_DIR;
        tmp = getFileFromProperty(DOMAIN_CONTENT_DIR);
        if (tmp == null) {
            tmp = getFileFromProperty(deprecatedDepDir);
        }
        if (tmp == null) {
            tmp = new File(this.domainDataDir, "content");
        }
        this.domainContentDir = tmp;
        if (domainContentDir.exists()) {
            if (!domainContentDir.isDirectory()) {
                throw HostControllerLogger.ROOT_LOGGER.domainContentDirectoryIsNotDirectory(domainContentDir);
            }
        } else if (!domainContentDir.mkdirs()) {
            throw HostControllerLogger.ROOT_LOGGER.couldNotCreateDomainContentDirectory(domainContentDir);
        }

        WildFlySecurityManager.setPropertyPrivileged(DOMAIN_CONTENT_DIR, this.domainContentDir.getAbsolutePath());
        WildFlySecurityManager.setPropertyPrivileged(deprecatedDepDir, this.domainContentDir.getAbsolutePath());

        tmp = getFileFromProperty(DOMAIN_LOG_DIR);
        if (tmp == null) {
            tmp = new File(this.domainBaseDir, "log");
        }
        if (tmp.exists()) {
            if (!tmp.isDirectory()) {
                throw HostControllerLogger.ROOT_LOGGER.logDirectoryIsNotADirectory(tmp);
            }
        } else if (!tmp.mkdirs()) {
            throw HostControllerLogger.ROOT_LOGGER.couldNotCreateLogDirectory(tmp);
        }
        this.domainLogDir = tmp;
        WildFlySecurityManager.setPropertyPrivileged(DOMAIN_LOG_DIR, this.domainLogDir.getAbsolutePath());

        tmp = getFileFromProperty(DOMAIN_SERVERS_DIR);
        if (tmp == null) {
            tmp = new File(this.domainBaseDir, "servers");
        }
        if (tmp.exists()) {
            if (!tmp.isDirectory()) {
                throw HostControllerLogger.ROOT_LOGGER.serversDirectoryIsNotADirectory(tmp);
            }
        } else if (!tmp.mkdirs()) {
            throw HostControllerLogger.ROOT_LOGGER.couldNotCreateServersDirectory(tmp);
        }
        this.domainServersDir = tmp;
        WildFlySecurityManager.setPropertyPrivileged(DOMAIN_SERVERS_DIR, this.domainServersDir.getAbsolutePath());

        tmp = getFileFromProperty(DOMAIN_TEMP_DIR);
        if (tmp == null) {
            tmp = new File(this.domainBaseDir, "tmp");
        }
        if (tmp.exists()) {
            if (!tmp.isDirectory()) {
                throw HostControllerLogger.ROOT_LOGGER.domainTempDirectoryIsNotADirectory(tmp);
            }
        } else if (!tmp.mkdirs()){
            throw HostControllerLogger.ROOT_LOGGER.couldNotCreateDomainTempDirectory(tmp);
        }
        this.domainTempDir = tmp;
        WildFlySecurityManager.setPropertyPrivileged(DOMAIN_TEMP_DIR, this.domainTempDir.getAbsolutePath());
        createAuthDir(tmp);

        if (defaultJVM != null) {
            defaultJvm = JvmType.createFromJavaExecutable(defaultJVM, false);
        } else {
            defaultJvm = null;
        }

        final String defaultHostConfig = WildFlySecurityManager.getPropertyPrivileged(JBOSS_HOST_DEFAULT_CONFIG, "host.xml");

        hostConfigurationFile = new ConfigurationFile(domainConfigurationDir, defaultHostConfig, initialHostConfig == null ? hostConfig : initialHostConfig, hostConfigInteractionPolicy, false, null);

        final Path filePath = this.domainDataDir.toPath().resolve(KERNEL_DIR).resolve(UUID_FILE);
        UUID uuid;
        try {
            String sysPropUUID = hostSystemProperties.get(JBOSS_HOST_MANAGEMENT_UUID);
            uuid = obtainProcessUUID(filePath, sysPropUUID);
        } catch(IOException ex) {
            throw HostControllerLogger.ROOT_LOGGER.couldNotObtainDomainUuid(ex, filePath);
        }
        this.hostControllerUUID = uuid;
        this.backupDomainFiles = backupDomainFiles;
        this.useCachedDc = useCachedDc;
        this.productConfig = productConfig;
        // Note the java.security.manager property shouldn't be set, but we'll check to ensure the security manager should be enabled
        this.securityManagerEnabled = securityManagerEnabled || isJavaSecurityManagerConfigured(hostSystemProperties);
        this.processType = processType;

        this.stream = getEnumProperty(hostSystemProperties, FEATURE_STREAM, FeatureStream.PROCESS_DEFAULT);
        if (!hostSystemProperties.containsKey(FEATURE_STREAM)) {
            WildFlySecurityManager.setPropertyPrivileged(FEATURE_STREAM, this.stream.toString());
        }
    }

    private static boolean isJavaSecurityManagerConfigured(final Map<String, String> props) {
        final String value = props.get("java.security.manager");
        return value != null && !"allow".equals(value) && !"disallow".equals(value);
    }

    private static <E extends Enum<E>> E getEnumProperty(Map<String, String> properties, String key, E defaultValue) {
        String value = properties.get(key);
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
     * Gets the address the process controller passed to this process
     * to use in communicating with it.
     *
     * @return the process controller's address
     */
    public InetAddress getProcessControllerAddress() {
        return processControllerAddress;
    }

    /**
     * Gets the port number the process controller passed to this process
     * to use in communicating with it.
     *
     * @return the process controller's port
     */
    public Integer getProcessControllerPort() {
        return processControllerPort;
    }

    /**
     * Gets the address the process controller told this Host Controller to use for communication with it.
     * Not related to communication with management clients.
     *
     * @return the address used for inter-process communication with the Process Controller
     */
    public InetAddress getHostControllerAddress() {
        return hostControllerAddress;
    }

    /**
     * Gets the port the process controller told this Host Controller to use for communication with it.
     * Not related to communication with management clients.
     *
     * @return the port used for inter-process communication with the Process Controller
     */
    public Integer getHostControllerPort() {
        return hostControllerPort;
    }

    /**
     * Gets whether this was a restarted host controller.
     *
     * @return if it was restarted
     */
    public boolean isRestart() {
        return isRestart;
    }

    /**
     * Whether we should maintain a copy of the domain configuration file even though we are not the
     * master host controller for the domain. This is only relevant if we are not the master host controller.
     *
     * @return <code>true</code> if we should grab the files
     */
    public boolean isBackupDomainFiles() {
        return backupDomainFiles;
    }

    /**
     * Whether we should try to start up with a locally cached copy of the domain configuration file rather than
     * trying to connect to a master host controller. This only has an effect if we are not configured to
     * act as the master host controller for the domain.
     *
     * @return <code>true</code> if we start with a locally cached copy of the domain configuration file
     */
    public boolean isUseCachedDc() {
        return useCachedDc;
    }

    /**
     * Gets the {@link RunningMode} that was in effect when this Host Controller was launched.
     *
     * @return  the initial running mode
     */
    public RunningMode getInitialRunningMode() {
        return initialRunningMode;
    }

    /**
     * Get the {@link HostRunningModeControl} containing the current running mode of the host controller
     *
     * @return the running mode control
     */
    @Override
    public HostRunningModeControl getRunningModeControl() {
        return runningModeControl;
    }

    /**
     * Gets the {@link ProductConfig} detected at startup.
     *
     * @return the product config. Will not be {@code null}
     */
    public ProductConfig getProductConfig() {
        return productConfig;
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
     * <strong>A filesystem location that has no real meaning and should not be regarded as providing any sort of useful
     * information.</strong> The "modules" directory is the default location from which JBoss Modules looks to find
     * modules. However, this behavior is in no way controlled by the value returned by this method, nor is it guaranteed that
     * modules will be loaded from only one directory, nor is it guaranteed that the "modules" directory will be one
     * of the directories used. Finally, the structure and contents of any directories from which JBoss Modules loads
     * resources is not something available from this class. Users wishing to interact with the modular classloading
     * system should use the APIs provided by JBoss Modules.
     *
     * @return a file
     *
     * @deprecated has no reliable meaning
     */
    @Deprecated
    public File getModulesDir() {
        return modulesDir;
    }

    /**
     * Gets the base directory in which managed domain files are stored.
     * <p>Defaults to {@link #getHomeDir() JBOSS_HOME}/domain</p>
     *
     * @return the domain base directory.
     */
    public File getDomainBaseDir() {
        return domainBaseDir;
    }

    /**
     * Gets the directory in which managed domain configuration files are stored.
     * <p>Defaults to {@link #getDomainBaseDir()}  domainBaseDir}/configuration</p>
     *
     * @return the domain configuration directory.
     */
    public File getDomainConfigurationDir() {
        return domainConfigurationDir;
    }

    /**
     * Gets the directory in which a Host Controller or Process Controller can store private internal state that
     * should survive a process restart.
     * <p>Defaults to {@link #getDomainBaseDir()}  domainBaseDir}/data</p>
     *
     * @return the internal state persistent storage directory for the Host Controller and Process Controller.
     */
    public File getDomainDataDir() {
        return domainDataDir;
    }

    /**
     * Gets the directory in which a Host Controller will store domain-managed user content (e.g. deployments or
     * rollout plans.)
     *
     * <p>Defaults to {@link #getDomainDataDir()}  domainDataDir}/content</p>
     *
     * @return the domain managed content storage directory
     */
    public File getDomainContentDir() {
        return domainContentDir;
    }

    /**
     * Deprecated previous name for {@link #getDomainContentDir()}.
     * @return the domain managed content storage directory.
     *
     * @deprecated use {@link #getDomainContentDir()}
     */
    @Deprecated
    public File getDomainDeploymentDir() {
        return domainContentDir;
    }

    /**
     * Gets the directory in which a Host Controller or Process Controller can write log files.
     * <p>Defaults to {@link #getDomainBaseDir()}  domainBaseDir}/log</p>
     *
     * @return the log file directory for the Host Controller and Process Controller.
     */
    public File getDomainLogDir() {
        return domainLogDir;
    }

    /**
     * Gets the directory under domain managed servers will write any persistent data. Each server will
     * have its own subdirectory.
     * <p>Defaults to {@link #getDomainBaseDir()}  domainBaseDir}/servers</p>
     *
     * @return the root directory for domain managed servers
     */
    public File getDomainServersDir() {
        return domainServersDir;
    }

    /**
     * Gets the directory in which a Host Controller or Process Controller can store private internal state that
     * does not need to survive a process restart.
     * <p>Defaults to {@link #getDomainBaseDir()}  domainBaseDir}/tmp</p>
     *
     * @return the internal state temporary storage directory for the Host Controller and Process Controller.
     */
    public File getDomainTempDir() {
        return domainTempDir;
    }

    /**
     * Gets the location of the default java executable to use when launch managed domain servers.
     *
     * @return the location of the default java executable
     */
    public File getDefaultJVM() {
        return defaultJvm != null ? new File(defaultJvm.getJavaExecutable()) : null;
    }

    /**
     * Initial set of system properties provided to this Host Controller at boot via the command line.
     * @return the properties
     */
    public Map<String, String> getHostSystemProperties() {
        return hostSystemProperties;
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

    /**
     * Get the local host name detected at server startup. Note that this is not the same
     * as the {@link #getHostControllerName() host controller name}. Defaults to the portion of
     * {@link #getQualifiedHostName() the qualified host name} following the first '.'.
     *
     * @return the local host name
     */
    @Override
    public String getHostName() {
        return hostName;
    }

    /**
     * Gets the name by which this host controller is known in the domain. Default to the
     * {@link #getQualifiedHostName() qualified host name} if the {@code name} attribute is not
     * specified on the root element of the host configuration file (e.g. host.xml).
     *
     * @return the name of the host controller
     */
    @Override
    public String getHostControllerName() {
        return hostControllerName;
    }

    /**
     * Gets the time when this process was started. Note that a process reload does not change this value.
     * @return the time, in ms since the epoch
     */
    public long getStartTime() {
        return startTime;
    }

    @Override
    protected String getProcessName() {
        return hostControllerName;
    }


    @Override
    protected void setProcessName(String processName) {
        if (processName != null) {
            this.hostControllerName = processName;
        }
    }

    /**
     * The process type for this host controller environment.
     *
     * @return the process type
     */
    public ProcessType getProcessType() {
        return processType;
    }

    @Override
    public FeatureStream getFeatureStream() {
        return this.stream;
    }

    @Override
    protected boolean isRuntimeSystemPropertyUpdateAllowed(String propertyName, String propertyValue, boolean bootTime) {
        // Currently any system-property in host.xml should not be applied to the HC runtime. This method
        // should not be invoked.
        throw HostControllerLogger.ROOT_LOGGER.hostControllerSystemPropertyUpdateNotSupported();
    }

    @Override
    protected void systemPropertyUpdated(String propertyName, String propertyValue) {
        // no-op
    }

    public ConfigurationFile getHostConfigurationFile() {
        return hostConfigurationFile;
    }

    public ConfigurationFile getDomainConfigurationFile() {
        return domainConfigurationFile;
    }

    public ConfigurationFile.InteractionPolicy getDomainConfigurationFileInteractionPolicy() {
        return domainConfigInteractionPolicy;
    }

    String getDomainConfig() {
        return domainConfig;
    }

    String getInitialDomainConfig() {
        return initialDomainConfig;
    }

    /**
     * This shouldn't really be a property of this class,
     * but we expose it in the management API resource for the HC environment,
     * so we store it here.
     *
     * @param domainConfigurationFile the config file, or {@code null} in a slave HC without --backup --cached-dc
     */
    void setDomainConfigurationFile(ConfigurationFile domainConfigurationFile) {
        this.domainConfigurationFile = domainConfigurationFile;
    }

    String getModulePath() {
        return modulePath;
    }

    boolean isSecurityManagerEnabled() {
        return securityManagerEnabled;
    }

    /**
     * Get a File from configuration.
     * @param name the name of the property
     * @return the CanonicalFile form for the given name.
     */
    private File getFileFromProperty(final String name) {
        String value = hostSystemProperties.get(name);
        File result = (value != null) ? new File(value) : null;
        // AS7-1752 see if a non-existent relative path exists relative to the home dir
        if (result != null && homeDir != null && !result.exists() && !result.isAbsolute()) {
            File relative = new File(homeDir, value);
            if (relative.exists()) {
                result = relative;
            }
        }
        return result;
    }

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

    @Override
    public UUID getInstanceUuid() {
        return this.hostControllerUUID;
    }

    /**
     * Gets an {@link OperationStepHandler} that can write the {@code name} attribute for a host controller.
     *
     * @return the handler
     */
    public OperationStepHandler getHostNameWriteHandler(LocalHostControllerInfoImpl hostControllerInfo) {
        return new HostNameWriteAttributeHandler(hostControllerInfo);
    }

    protected class HostNameWriteAttributeHandler extends ProcessNameWriteAttributeHandler {
        private final LocalHostControllerInfoImpl hostControllerInfo;

        private HostNameWriteAttributeHandler(LocalHostControllerInfoImpl hostControllerInfo) {
            this.hostControllerInfo = hostControllerInfo;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final boolean booting = context.isBooting();
            if (booting) {
                final ModelNode newValue = operation.hasDefined(VALUE) ? operation.get(VALUE) : new ModelNode();
                if (newValue.isDefined()) {
                    context.addStep(new OperationStepHandler() {
                        @Override
                        public void execute(OperationContext context, ModelNode operation) {
                            hostControllerInfo.clearOverrideLocalHostName();
                        }
                    }, OperationContext.Stage.RUNTIME);
                }
            }
            super.execute(context, operation);
        }
    }
}
