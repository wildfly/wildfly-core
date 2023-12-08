/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.launcher;

import static org.wildfly.core.launcher.logger.LauncherMessages.MESSAGES;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.wildfly.core.launcher.Arguments.Argument;

/**
 * Builds a list of commands used to launch a standalone instance of WildFly.
 * <p/>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "MagicNumber", "UnusedReturnValue"})
public class StandaloneCommandBuilder extends AbstractCommandBuilder<StandaloneCommandBuilder> implements CommandBuilder {

    // JPDA remote socket debugging
    static final String DEBUG_FORMAT = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%d";

    private static final String SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String SERVER_LOG_DIR = "jboss.server.log.dir";

    private Path baseDir;
    private final Arguments javaOpts;
    private String debugArg;
    private String modulesLocklessArg;
    private String modulesMetricsArg;
    private final Map<String, String> securityProperties;
    private boolean addModuleAgent;
    private final Collection<String> moduleOpts;

    /**
     * Creates a new command builder for a standalone instance.
     * <p/>
     * Note the {@code wildflyHome} and {@code javaHome} are not validated using the constructor. The static {@link
     * #of(java.nio.file.Path)} is preferred.
     *
     * @param wildflyHome the path to WildFly
     */
    private StandaloneCommandBuilder(final Path wildflyHome) {
        super(wildflyHome);
        javaOpts = new Arguments();
        javaOpts.addAll(DEFAULT_VM_ARGUMENTS);
        securityProperties = new LinkedHashMap<>();
        moduleOpts = new ArrayList<>();
        addModuleAgent = false;
    }

    /**
     * Creates a command builder for a standalone instance of WildFly.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static StandaloneCommandBuilder of(final Path wildflyHome) {
        return new StandaloneCommandBuilder(Environment.validateWildFlyDir(wildflyHome));
    }

    /**
     * Creates a command builder for a standalone instance of WildFly.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static StandaloneCommandBuilder of(final String wildflyHome) {
        return new StandaloneCommandBuilder(Environment.validateWildFlyDir(wildflyHome));
    }

    /**
     * Adds a JVM argument to the command ignoring {@code null} arguments.
     *
     * @param jvmArg the JVM argument to add
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addJavaOption(final String jvmArg) {
        if (jvmArg != null && !jvmArg.trim().isEmpty()) {
            final Argument argument = Arguments.parse(jvmArg);
            switch (argument.getKey()) {
                case SERVER_BASE_DIR:
                    if (argument.getValue() != null) {
                        setBaseDirectory(argument.getValue());
                    }
                    break;
                case SERVER_CONFIG_DIR:
                    if (argument.getValue() != null) {
                        setConfigurationDirectory(argument.getValue());
                    }
                    break;
                case SERVER_LOG_DIR:
                    if (argument.getValue() != null) {
                        setLogDirectory(argument.getValue());
                    }
                    break;
                case SECURITY_MANAGER_PROP:
                    // [WFCORE-5778] java.security.manager system property with value "allow" detected.
                    // It doesn't mean SM is going to be installed but it indicates SM can be installed dynamically.
                    setUseSecurityManager(isJavaSecurityManagerConfigured(argument));
                    break;
                default:
                    javaOpts.add(argument);
                    break;
            }
        }
        return this;
    }

    /**
     * Adds the array of JVM arguments to the command.
     *
     * @param javaOpts the array of JVM arguments to add, {@code null} arguments are ignored
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addJavaOptions(final String... javaOpts) {
        if (javaOpts != null) {
            for (String javaOpt : javaOpts) {
                addJavaOption(javaOpt);
            }
        }
        return this;
    }

    /**
     * Adds the collection of JVM arguments to the command.
     *
     * @param javaOpts the collection of JVM arguments to add, {@code null} arguments are ignored
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addJavaOptions(final Iterable<String> javaOpts) {
        if (javaOpts != null) {
            for (String javaOpt : javaOpts) {
                addJavaOption(javaOpt);
            }
        }
        return this;
    }

    /**
     * Sets the JVM arguments to use. This overrides any default JVM arguments that would normally be added and ignores
     * {@code null} values in the collection.
     * <p/>
     * If the collection is {@code null} the JVM arguments will be cleared and no new arguments will be added.
     *
     * @param javaOpts the JVM arguments to use
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setJavaOptions(final Iterable<String> javaOpts) {
        this.javaOpts.clear();
        return addJavaOptions(javaOpts);
    }

    /**
     * Sets the JVM arguments to use. This overrides any default JVM arguments that would normally be added and ignores
     * {@code null} values in the array.
     * <p/>
     * If the array is {@code null} the JVM arguments will be cleared and no new arguments will be added.
     *
     * @param javaOpts the JVM arguments to use
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setJavaOptions(final String... javaOpts) {
        this.javaOpts.clear();
        return addJavaOptions(javaOpts);
    }

    /**
     * Returns the JVM arguments.
     *
     * @return the JVM arguments
     */
    public List<String> getJavaOptions() {
        return javaOpts.asList();
    }

    /**
     * Adds an option which will be passed to JBoss Modules.
     * <p>
     * Note that {@code -mp} or {@code --modulepath} is not supported. Use {@link #addModuleDir(String)} to add a module
     * directory.
     * </p>
     *
     * @param arg the argument to add
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addModuleOption(final String arg) {
        if (arg != null) {
            if (arg.startsWith("-javaagent:")) {
                addModuleAgent = true;
            } else if ("-mp".equals(arg) || "--modulepath".equals(arg)) {
                throw MESSAGES.invalidArgument(arg, "addModuleOption");
            } else if ("-secmgr".equals(arg)) {
                throw MESSAGES.invalidArgument(arg, "setUseSecurityManager");
            }
            moduleOpts.add(arg);
        }
        return this;
    }

    /**
     * Adds the options which will be passed to JBoss Modules.
     * <p>
     * Note that {@code -mp} or {@code --modulepath} is not supported. Use {@link #addModuleDirs(String...)} to add a
     * module directory.
     * </p>
     *
     * @param args the argument to add
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addModuleOptions(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addModuleOption(arg);
            }
        }
        return this;
    }

    /**
     * Adds the options which will be passed to JBoss Modules.
     * <p>
     * Note that {@code -mp} or {@code --modulepath} is not supported. Use {@link #addModuleDirs(Iterable)} to add a
     * module directory.
     * </p>
     *
     * @param args the argument to add
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addModuleOptions(final Iterable<String> args) {
        if (args != null) {
            for (String arg : args) {
                addModuleOption(arg);
            }
        }
        return this;
    }

    /**
     * Clears the current module options and adds the options which will be passed to JBoss Modules.
     * <p>
     * Note that {@code -mp} or {@code --modulepath} is not supported. Use {@link #addModuleDirs(String...)} to add a
     * module directory.
     * </p>
     *
     * @param args the argument to use
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setModuleOptions(final String... args) {
        moduleOpts.clear();
        addModuleOptions(args);
        return this;
    }

    /**
     * Clears the current module options and adds the options which will be passed to JBoss Modules.
     * <p>
     * Note that {@code -mp} or {@code --modulepath} is not supported. Use {@link #addModuleDirs(Iterable)} to add a
     * module directory.
     * </p>
     *
     * @param args the argument to use
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setModuleOptions(final Iterable<String> args) {
        moduleOpts.clear();
        addModuleOptions(args);
        return this;
    }

    /**
     * Sets the debug argument for the JVM with a default port of {@code 8787}.
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setDebug() {
        return setDebug(false, 8787);
    }

    /**
     * Sets the debug argument for the JVM.
     *
     * @param port the port to listen on
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setDebug(final int port) {
        return setDebug(false, port);
    }

    /**
     * Sets the debug JPDA remote socket debugging argument.
     *
     * @param suspend {@code true} to suspend otherwise {@code false}
     * @param port    the port to listen on
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setDebug(final boolean suspend, final int port) {
        debugArg = String.format(DEBUG_FORMAT, (suspend ? "y" : "n"), port);
        return this;
    }

    /**
     * Sets the base directory to use.
     * <p/>
     * The default is {@code $JBOSS_HOME/standalone}.
     *
     * @param baseDir the base directory or {@code null} to resolve the base directory
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setBaseDirectory(final String baseDir) {
        this.baseDir = validateAndNormalizeDir(baseDir, true);
        return this;
    }

    /**
     * Sets the base directory to use.
     * <p/>
     * The default is {@code $JBOSS_HOME/standalone}.
     *
     * @param baseDir the base directory or {@code null} to resolve the base directory
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setBaseDirectory(final Path baseDir) {
        this.baseDir = validateAndNormalizeDir(baseDir, true);
        return this;
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setJavaHome(final String javaHome) {
        environment.setJvm(Jvm.of(javaHome));
        return this;
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setJavaHome(final Path javaHome) {
        environment.setJvm(Jvm.of(javaHome));
        return this;
    }

    /**
     * Set to {@code true} to use JBoss Modules lockless mode.
     *
     * @param b {@code true} to use lockless mode
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setModulesLockless(final boolean b) {
        if (b) {
            modulesLocklessArg = "-Djboss.modules.lockless=true";
        } else {
            modulesLocklessArg = null;
        }
        return this;
    }

    /**
     * Set to {@code true} to gather metrics for JBoss Modules.
     *
     * @param b {@code true} to gather metrics for JBoss Modules.
     *
     * @return this builder
     */
    public StandaloneCommandBuilder setModulesMetrics(final boolean b) {
        if (b) {
            modulesMetricsArg = "-Djboss.modules.metrics=true";
        } else {
            modulesMetricsArg = null;
        }
        return this;
    }

    /**
     * Sets the configuration file for the server. The file must be in the {@link #setConfigurationDirectory(String)
     * configuration} directory. A value of {@code null} will remove the configuration file.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}.
     *
     * @param configFile the configuration file name or {@code null} to remove the configuration file
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setServerConfiguration(final String configFile) {
        setSingleServerArg("-c", configFile);
        return this;
    }

    /**
     * Returns the configuration file {@link #setServerConfiguration(String) set} or {@code null} if one was not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getServerConfiguration() {
        return getServerArg("-c");
    }

    /**
     * Sets the configuration file for the server. The file must be in the {@link #setConfigurationDirectory(String)
     * configuration} directory. A value of {@code null} will remove the configuration file.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}.
     *
     * @param configFile the configuration file name or {@code null} to remove the configuration file
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setServerReadOnlyConfiguration(final String configFile) {
        setSingleServerArg("--read-only-server-config", configFile);
        return this;
    }

    /**
     * Returns the configuration file {@link #setServerConfiguration(String) set} or {@code null} if one was not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getReadOnlyServerConfiguration() {
        return getServerArg("--read-only-server-config");
    }

    /**
     * Adds a security property to be passed to the server with a {@code null} value.
     *
     * @param key the property key
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addSecurityProperty(final String key) {
        securityProperties.put(key, null);
        return this;
    }

    /**
     * Adds a security property to be passed to the server.
     *
     * @param key   the property key
     * @param value the property value
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addSecurityProperty(final String key, final String value) {
        securityProperties.put(key, value);
        return this;
    }

    /**
     * Adds all the security properties to be passed to the server.
     *
     * @param properties a map of the properties to add, {@code null} values are allowed in the map
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addSecurityProperties(final Map<String, String> properties) {
        securityProperties.putAll(properties);
        return this;
    }

    /**
     * Configures the git repository for the standalone server.
     *
     * @param gitRepository     the git repository to clone to get the server configuration from
     * @param gitBranch         the branch to use to get the server configuration
     * @param gitAuthentication the Elytron configuration file for managing git credentials
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setGitRepository(final String gitRepository, final String gitBranch, final String gitAuthentication) {
        if (gitRepository == null) {
            throw MESSAGES.nullParam("git-repo");
        }
        addServerArg("--git-repo", gitRepository);
        if (gitBranch != null) {
            addServerArg("--git-branch", gitBranch);
        }
        if (gitAuthentication != null) {
            addServerArg("--git-auth", gitAuthentication);
        }

        return this;
    }

    /**
     * Adds the YAML configuration file argument with the given YAML configuration files.
     *
     * @param yamlFiles the files to add
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setYamlFiles(final Collection<Path> yamlFiles) {
        if (yamlFiles == null || yamlFiles.isEmpty()) {
            return this;
        }
        StringJoiner joiner = new StringJoiner(File.pathSeparator);
        for (Path yamlFile : yamlFiles) {
            joiner.add(yamlFile.toAbsolutePath().toString());
        }
        setSingleServerArg("--yaml", joiner.toString());
        return this;
    }

    /**
     * Adds the YAML configuration file argument with the given YAML configuration files.
     *
     * @param yamlFiles the files to add
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setYamlFiles(final Path... yamlFiles) {
        if (yamlFiles == null || yamlFiles.length == 0) {
            return this;
        }
        return setYamlFiles(List.of(yamlFiles));
    }

    /**
     * Sets the stability level of the domain controller process.
     * @param stability a stability level
     * @return a reference to this builder
     */
    public StandaloneCommandBuilder setStability(String stability) {
        if (stability != null) {
            this.setSingleServerArg("--stability", stability);
        }
        return this;
    }

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();
        cmd.add("-D[Standalone]");
        // Check to see if an agent was added as a module option, if so we want to add JBoss Modules as an agent.
        if (addModuleAgent) {
            cmd.add("-javaagent:" + getModulesJarName());
        }
        cmd.addAll(getJavaOptions());
        if (environment.getJvm().isModular()) {
            cmd.addAll(DEFAULT_MODULAR_VM_ARGUMENTS);
            for (final String optionalModularArgument : OPTIONAL_DEFAULT_MODULAR_VM_ARGUMENTS) {
                if (environment.getJvm().isPackageAvailable(environment.getJvm().getPath(), optionalModularArgument)) {
                    cmd.add(optionalModularArgument);
                }
            }
        }
        if (environment.getJvm().enhancedSecurityManagerAvailable()) {
            cmd.add(SECURITY_MANAGER_PROP_WITH_ALLOW_VALUE);
        }
        // Add these to JVM level system properties
        addSystemPropertyArg(cmd, HOME_DIR, getWildFlyHome());
        addSystemPropertyArg(cmd, SERVER_BASE_DIR, getBaseDirectory());
        addSystemPropertyArg(cmd, SERVER_LOG_DIR, getLogDirectory());
        addSystemPropertyArg(cmd, SERVER_CONFIG_DIR, getConfigurationDirectory());
        if (modulesLocklessArg != null) {
            cmd.add(modulesLocklessArg);
        }
        if (modulesMetricsArg != null) {
            cmd.add(modulesMetricsArg);
        }
        if (debugArg != null) {
            cmd.add(debugArg);
        }
        cmd.add(getBootLogArgument("server.log"));
        cmd.add(getLoggingPropertiesArgument("logging.properties"));
        cmd.add("-jar");
        cmd.add(getModulesJarName());
        if (useSecurityManager()) {
            cmd.add(SECURITY_MANAGER_ARG);
        }
        // Add the agent argument for jboss-modules
        cmd.addAll(moduleOpts);
        cmd.add("-mp");
        cmd.add(getModulePaths());
        cmd.add("org.jboss.as.standalone");

        // Add the security properties
        StringBuilder sb = new StringBuilder(64);
        for (Map.Entry<String, String> entry : securityProperties.entrySet()) {
            sb.append("-S").append(entry.getKey());
            if (entry.getValue() != null) {
                sb.append('=').append(entry.getValue());
            }
            cmd.add(sb.toString());
            sb.setLength(0);
        }

        cmd.addAll(getServerArguments());
        return cmd;
    }

    @Override
    public List<String> build() {
        final List<String> cmd = new ArrayList<>();
        cmd.add(environment.getJvm().getCommand());
        cmd.addAll(buildArguments());
        return cmd;
    }

    @Override
    public Path getJavaHome() {
        return environment.getJvm().getPath();
    }

    @Override
    public Path getBaseDirectory() {
        if (baseDir == null) {
            return normalizePath("standalone");
        }
        return baseDir;
    }

    @Override
    protected StandaloneCommandBuilder getThis() {
        return this;
    }

    @Override
    boolean addServerArgument(final Argument argument) {
        switch (argument.getKey()) {
            case SERVER_BASE_DIR:
                if (argument.getValue() != null) {
                    setBaseDirectory(argument.getValue());
                    return false;
                }
                break;
            case SERVER_CONFIG_DIR:
                if (argument.getValue() != null) {
                    setConfigurationDirectory(argument.getValue());
                    return false;
                }
                break;
            case SERVER_LOG_DIR:
                if (argument.getValue() != null) {
                    setLogDirectory(argument.getValue());
                    return false;
                }
                break;
        }
        return true;
    }
}
