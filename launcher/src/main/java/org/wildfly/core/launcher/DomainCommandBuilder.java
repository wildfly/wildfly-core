/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.launcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.wildfly.core.launcher.Arguments.Argument;

/**
 * Builds a list of commands used to launch a domain instance of WildFly.
 * <p/>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 * <p/>
 * The {@link #getJavaHome() default Java home} directory is used for the process controller and optionally for the
 * host
 * controller and the servers launched.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class DomainCommandBuilder extends AbstractCommandBuilder<DomainCommandBuilder> implements CommandBuilder {

    private static final String DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    private static final String DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    private static final String DOMAIN_LOG_DIR = "jboss.domain.log.dir";

    private Jvm hostControllerJvm;
    private Jvm serverJvm;
    private Path baseDir;
    private final Arguments hostControllerJavaOpts;
    private final Arguments processControllerJavaOpts;

    /**
     * Creates a new command builder for a domain instance of WildFly.
     * <p/>
     * Note the {@code wildflyHome} and {@code javaHome} are not validated using the constructor. The static {@link
     * #of(java.nio.file.Path)} is preferred.
     *
     * @param wildflyHome the WildFly home directory
     * @param jvm         the default JVM
     */
    private DomainCommandBuilder(final Path wildflyHome, final Jvm jvm) {
        super(wildflyHome, jvm);
        hostControllerJavaOpts = new Arguments();
        hostControllerJavaOpts.addAll(DEFAULT_VM_ARGUMENTS);
        processControllerJavaOpts = new Arguments();
        processControllerJavaOpts.addAll(DEFAULT_VM_ARGUMENTS);
        hostControllerJvm = serverJvm = environment.getJvm();
    }

    /**
     * Creates a command builder for a domain instance of WildFly.
     * <p/>
     * Uses the system property {@code java.home} to find the java executable required for the default Java home.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final Path wildflyHome) {
        return new DomainCommandBuilder(Environment.validateWildFlyDir(wildflyHome), Jvm.current());
    }

    /**
     * Creates a command builder for a domain instance of WildFly.
     * <p/>
     * Uses the system property {@code java.home} to find the java executable required for the default Java home.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final String wildflyHome) {
        return new DomainCommandBuilder(Environment.validateWildFlyDir(wildflyHome), Jvm.current());
    }

    /**
     * Creates a command builder for a domain instance of WildFly.
     *
     * @param wildflyHome the path to the WildFly home directory
     * @param javaHome    the path to the default Java home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final String wildflyHome, final String javaHome) {
        return new DomainCommandBuilder(Environment.validateWildFlyDir(wildflyHome), Jvm.of(javaHome));
    }

    /**
     * Creates a command builder for a domain instance of WildFly.
     *
     * @param wildflyHome the path to the WildFly home directory
     * @param javaHome    the path default to the Java home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final Path wildflyHome, final Path javaHome) {
        return new DomainCommandBuilder(Environment.validateWildFlyDir(wildflyHome), Jvm.of(javaHome));
    }

    /**
     * Sets the option ({@code --backup} to keep a copy of the persistent domain configuration even if this host is not
     * a domain controller.
     *
     * @return the builder
     */
    public DomainCommandBuilder setBackup() {
        addServerArgument("--backup");
        return this;
    }

    /**
     * Sets the option ({@code --cached-dc}) to boot using a locally cached copy of the domain configuration.
     *
     * @return the builder
     *
     * @see #setBackup()
     */
    public DomainCommandBuilder setCachedDomainController() {
        addServerArgument("--cached-dc");
        return this;
    }

    /**
     * Sets the address on which the host controller should listen for communication from the process controller
     * ({@code interprocess-hc-address}). Ignores {@code null} values.
     *
     * @param address the address
     *
     * @return the builder
     */
    public DomainCommandBuilder setInterProcessHostControllerAddress(final String address) {
        if (address != null) {
            setSingleServerArg("--interprocess-hc-address", address);
        }
        return this;
    }

    /**
     * Sets the port on which the host controller should listen for communication from the process controller ({@code
     * interprocess-hc-address}). Ignores {@code null} values or values less than 0.
     *
     * @param port the port
     *
     * @return the builder
     *
     * @throws java.lang.NumberFormatException if the port is not a valid integer
     */
    public DomainCommandBuilder setInterProcessHostControllerPort(final String port) {
        if (port != null) {
            setInterProcessHostControllerPort(Integer.parseInt(port));
        }
        return this;
    }


    /**
     * Sets the port on which the host controller should listen for communication from the process controller ({@code
     * interprocess-hc-address}). Ignores values less than 0.
     *
     * @param port the port
     *
     * @return the builder
     */
    public DomainCommandBuilder setInterProcessHostControllerPort(final int port) {
        if (port > -1) {
            setSingleServerArg("--interprocess-hc-port", Integer.toString(port));
        }
        return this;
    }

    /**
     * Sets the system property {@code jboss.domain.primary.address}. In a default slave host configuration this is used
     * to configure the address of the master host controller. Ignores {@code null} values.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has not been removed from the remote host.
     * If the system property was removed the address provided has no effect.
     *
     * @param address the address
     *
     * @return the builder
     */
    public DomainCommandBuilder setMasterAddressHint(final String address) {
        if (address != null) {
            setSingleServerArg("--primary-address", address);
        }
        return this;
    }

    /**
     * Sets the system property {@code jboss.domain.primary.port}. In a default slave host configuration this is used
     * to configure the port of the master host controller. Ignores {@code null} values or values less than 0.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has not been removed from the remote host.
     * If the system property was removed the port provided has no effect.
     *
     * @param port the port
     *
     * @return the builder
     *
     * @throws java.lang.NumberFormatException if the port is not a valid integer
     */
    public DomainCommandBuilder setMasterPortHint(final String port) {
        if (port != null) {
            setMasterPortHint(Integer.parseInt(port));
        }
        return this;
    }

    /**
     * Sets the system property {@code jboss.domain.primary.port}. In a default slave host configuration this is used
     * to configure the port of the master host controller. Ignores values less than 0.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has not been removed from the remote host.
     * If the system property was removed the port provided has no effect.
     *
     * @param port the port
     *
     * @return the builder
     */
    public DomainCommandBuilder setMasterPortHint(final int port) {
        if (port > -1) {
            setSingleServerArg("--primary-port", Integer.toString(port));
        }
        return this;
    }

    /**
     * Sets the address on which the process controller listens for communication from processes it controls. Ignores
     * {@code null} values.
     *
     * @param address the address
     *
     * @return the builder
     */
    public DomainCommandBuilder setProcessControllerAddress(final String address) {
        if (address != null) {
            setSingleServerArg("--pc-address", address);
        }
        return this;
    }

    /**
     * Sets the port on which the process controller listens for communication from processes it controls. Ignores
     * {@code null} values or values less than 0.
     *
     * @param port the port
     *
     * @return the builder
     *
     * @throws java.lang.NumberFormatException if the port is not a valid integer
     */
    public DomainCommandBuilder setProcessControllerPort(final String port) {
        if (port != null) {
            setProcessControllerPort(Integer.parseInt(port));
        }
        return this;
    }

    /**
     * Sets the port on which the process controller listens for communication from processes it controls. Ignores
     * values less than 0.
     *
     * @param port the port
     *
     * @return the builder
     */
    public DomainCommandBuilder setProcessControllerPort(final int port) {
        if (port > -1) {
            setSingleServerArg("--pc-port", Integer.toString(port));
        }
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
    public DomainCommandBuilder setBaseDirectory(final String baseDir) {
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
    public DomainCommandBuilder setBaseDirectory(final Path baseDir) {
        this.baseDir = validateAndNormalizeDir(baseDir, true);
        return this;
    }

    /**
     * Sets the Java home for the host controller.
     * <p/>
     * If the {@code javaHome} is not {@code null} then the java executable will be resolved and used to launch the
     * host processor. If the {@code javaHome} is {@code null} the same java executable will be used to launch the host
     * controller that launched the process controller.
     *
     * @param javaHome the java home to set, can be {@code null} to use the default
     *
     * @return the builder
     */
    public DomainCommandBuilder setHostControllerJavaHome(final String javaHome) {
        hostControllerJvm = Jvm.of(javaHome);
        return this;
    }

    /**
     * Sets the Java home for the host controller.
     * <p/>
     * If the {@code javaHome} is not {@code null} then the java executable will be resolved and used to launch the
     * host processor. If the {@code javaHome} is {@code null} the same java executable will be used to launch the host
     * controller that launched the process controller.
     *
     * @param javaHome the java home to set, can be {@code null} to use the default
     *
     * @return the builder
     */
    public DomainCommandBuilder setHostControllerJavaHome(final Path javaHome) {
        hostControllerJvm = Jvm.of(javaHome);
        return this;
    }

    /**
     * Returns the Java home path the host controller will use.
     *
     * @return the path to the Java home for the host controller
     */
    public Path getHostControllerJavaHome() {
        return hostControllerJvm.getPath();
    }

    /**
     * Sets the configuration file for the host controller (host.xml). The file must be in the {@link
     * #setConfigurationDirectory(String) configuration} directory. A value of {@code null} is ignored.
     *
     * @param configFile the configuration file name
     *
     * @return the builder
     */
    public DomainCommandBuilder setHostConfiguration(final String configFile) {
        if (configFile != null) {
            setSingleServerArg("--host-config", configFile);
        }
        return this;
    }

    /**
     * Returns the configuration file {@link #setHostConfiguration(String) set} or {@code null} if one was not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getHostConfiguration() {
        return getServerArg("--host-config");
    }

    /**
     * Sets the read only configuration file for the host controller (host.xml). The file must be in the {@link
     * #setConfigurationDirectory(String) configuration} directory. A value of {@code null} is ignored
     * <p/>
     * This will override any previous value set.
     *
     * @param configFile the configuration file name or {@code null}
     *
     * @return the builder
     */
    public DomainCommandBuilder setReadOnlyHostConfiguration(final String configFile) {
        if (configFile != null) {
            setSingleServerArg("--read-only-host-config", configFile);
        }
        return this;
    }


    /**
     * Returns the configuration file {@link #setReadOnlyHostConfiguration(String)} set} or {@code null} if one was
     * not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getReadOnlyHostConfiguration() {
        return getServerArg("--read-only-host-config");
    }

    /**
     * Sets the configuration file for the domain (domain.xml). The file must be in the {@link
     * #setConfigurationDirectory(String) configuration} directory. A value of {@code null} is ignored.
     * <p/>
     * This will override any previous value set.
     *
     * @param configFile the configuration file name
     *
     * @return the builder
     */
    public DomainCommandBuilder setDomainConfiguration(final String configFile) {
        setSingleServerArg("-c", configFile);
        return this;
    }

    /**
     * Returns the configuration file {@link #setDomainConfiguration(String) set} or {@code null} if one was not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getDomainConfiguration() {
        return getServerArg("-c");
    }

    /**
     * Sets the read only configuration file for the domain (domain.xml). The file must be in the {@link
     * #setConfigurationDirectory(String) configuration} directory. A value of {@code null} is ignored
     * <p/>
     * This will override any previous value set.
     *
     * @param configFile the configuration file name or {@code null}
     *
     * @return the builder
     */
    public DomainCommandBuilder setReadOnlyDomainConfiguration(final String configFile) {
        if (configFile != null) {
            setSingleServerArg("--read-only-domain-config", configFile);
        }
        return this;
    }


    /**
     * Returns the configuration file {@link #setReadOnlyDomainConfiguration(String)} set} or {@code null} if one was
     * not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getReadOnlyDomainConfiguration() {
        return getServerArg("--read-only-domain-config");
    }

    /**
     * Adds a JVM argument to the host controller ignoring {@code null} values.
     *
     * @param arg the argument to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addHostControllerJavaOption(final String arg) {
        if (arg != null && !arg.trim().isEmpty()) {
            final Argument argument = Arguments.parse(arg);
            switch (argument.getKey()) {
                case DOMAIN_BASE_DIR:
                    if (argument.getValue() != null) {
                        setBaseDirectory(argument.getValue());
                    }
                    break;
                case DOMAIN_CONFIG_DIR:
                    if (argument.getValue() != null) {
                        setConfigurationDirectory(argument.getValue());
                    }
                    break;
                case DOMAIN_LOG_DIR:
                    if (argument.getValue() != null) {
                        setLogDirectory(argument.getValue());
                    }
                    break;
                default:
                    hostControllerJavaOpts.add(argument);
                    break;
            }
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the host controller ignoring {@code null} values.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addHostControllerJavaOptions(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addHostControllerJavaOption(arg);
            }
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the host controller ignoring {@code null} values.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addHostControllerJavaOptions(final Iterable<String> args) {
        if (args != null) {
            for (String arg : args) {
                addHostControllerJavaOption(arg);
            }
        }
        return this;
    }

    /**
     * Sets the JVM arguments for the host controller ignoring {@code null} values in the array.
     * <p/>
     * If the array is {@code null} the host controller JVM arguments are cleared and no new ones are added
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder setHostControllerJavaOptions(final String... args) {
        hostControllerJavaOpts.clear();
        return addHostControllerJavaOptions(args);
    }

    /**
     * Sets the JVM arguments for the host controller ignoring {@code null} values in the collection.
     * <p/>
     * If the collection is {@code null} the host controller JVM arguments are cleared and no new ones are added
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder setHostControllerJavaOptions(final Iterable<String> args) {
        hostControllerJavaOpts.clear();
        return addHostControllerJavaOptions(args);
    }

    /**
     * Returns the JVM arguments for the host controller.
     *
     * @return the JVM arguments
     */
    public List<String> getHostControllerJavaOptions() {
        return hostControllerJavaOpts.asList();
    }

    /**
     * Adds a JVM argument to the process controller ignoring {@code null} values.
     *
     * @param arg the argument to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addProcessControllerJavaOption(final String arg) {
        if (arg != null && !arg.trim().isEmpty()) {
            final Argument argument = Arguments.parse(arg);
            if (argument.getKey().equals(SECURITY_MANAGER_PROP)) {
                // [WFCORE-5778] java.security.manager system property with value "allow" detected.
                // It doesn't mean SM is going to be installed but it indicates SM can be installed dynamically.
                setUseSecurityManager(isJavaSecurityManagerConfigured(argument));
            } else {
                processControllerJavaOpts.add(argument);
            }
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the process controller ignoring {@code null} values.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addProcessControllerJavaOptions(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addProcessControllerJavaOption(arg);
            }
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the process controller ignoring {@code null} values.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addProcessControllerJavaOptions(final Iterable<String> args) {
        if (args != null) {
            for (String arg : args) {
                addProcessControllerJavaOption(arg);
            }
        }
        return this;
    }

    /**
     * Sets the JVM arguments for the process controller ignoring {@code null} values in the array.
     * <p/>
     * If the array is {@code null} the process controller JVM arguments are cleared and no new ones are added
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder setProcessControllerJavaOptions(final String... args) {
        processControllerJavaOpts.clear();
        return addProcessControllerJavaOptions(args);
    }

    /**
     * Sets the JVM arguments for the process controller ignoring {@code null} values in the collection.
     * <p/>
     * If the collection is {@code null} the process controller JVM arguments are cleared and no new ones are added
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder setProcessControllerJavaOptions(final Iterable<String> args) {
        processControllerJavaOpts.clear();
        return addProcessControllerJavaOptions(args);
    }

    /**
     * Returns the JVM arguments used for the process controller.
     *
     * @return the JVM arguments
     */
    public List<String> getProcessControllerJavaOptions() {
        return processControllerJavaOpts.asList();
    }


    /**
     * Sets the Java home for the servers that are launched in the domain.
     * <p/>
     * If the {@code javaHome} is not {@code null} then the java executable will be resolved and used to launch the
     * servers in the domain. If the {@code javaHome} is {@code null} the same java executable will be used to launch
     * the servers in the domain that launched the process controller.
     *
     * @param javaHome the java home to set, can be {@code null} to use the default
     *
     * @return the builder
     */
    public DomainCommandBuilder setServerJavaHome(final String javaHome) {
        serverJvm = Jvm.of(javaHome);
        return this;
    }


    /**
     * Sets the Java home for the servers that are launched in the domain.
     * <p/>
     * If the {@code javaHome} is not {@code null} then the java executable will be resolved and used to launch the
     * servers in the domain. If the {@code javaHome} is {@code null} the same java executable will be used to launch
     * the servers in the domain that launched the process controller.
     *
     * @param javaHome the java home to set, can be {@code null} to use the default
     *
     * @return the builder
     */
    public DomainCommandBuilder setServerJavaHome(final Path javaHome) {
        serverJvm = Jvm.of(javaHome);
        return this;
    }

    /**
     * Sets the stability level of the standalone server process.
     * @param stability a stability level
     * @return a reference to this builder
     */
    public DomainCommandBuilder setStability(String stability) {
        if (stability != null) {
            this.setSingleServerArg("--stability", stability);
        }
        return this;
    }

    /**
     * Returns the Java home path the servers will use.
     *
     * @return the path to the Java home for the servers
     */
    public Path getServerJavaHome() {
        return serverJvm.getPath();
    }

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();

        // Process Controller
        cmd.add("-D[Process Controller]");
        addSystemPropertyArg(cmd, HOME_DIR, getWildFlyHome());

        // PROCESS_CONTROLLER_JAVA_OPTS
        cmd.addAll(processControllerJavaOpts.asList());
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

        cmd.add(getBootLogArgument("process-controller.log"));
        cmd.add(getLoggingPropertiesArgument("logging.properties"));
        cmd.add("-jar");
        cmd.add(getModulesJarName());
        if (useSecurityManager()) {
            cmd.add(SECURITY_MANAGER_ARG);
        }
        cmd.add("-mp");
        cmd.add(getModulePaths());
        cmd.add("org.jboss.as.process-controller");
        cmd.add("-jboss-home");
        cmd.add(getWildFlyHome().toString());
        cmd.add("-jvm");
        cmd.add(hostControllerJvm.getCommand());
        cmd.add("-mp");
        cmd.add(getModulePaths());

        // Host Controller
        cmd.add("--");
        cmd.add(getBootLogArgument("host-controller.log"));
        cmd.add(getLoggingPropertiesArgument("logging.properties"));

        // HOST_CONTROLLER_JAVA_OPTS
        cmd.addAll(hostControllerJavaOpts.asList());
        if (hostControllerJvm.isModular()) {
            cmd.addAll(DEFAULT_MODULAR_VM_ARGUMENTS);
            for (final String optionalModularArgument : OPTIONAL_DEFAULT_MODULAR_VM_ARGUMENTS) {
                if (environment.getJvm().isPackageAvailable(environment.getJvm().getPath(), optionalModularArgument)) {
                    cmd.add(optionalModularArgument);
                }
            }
        }
        if (hostControllerJvm.enhancedSecurityManagerAvailable()) {
            cmd.add(SECURITY_MANAGER_PROP_WITH_ALLOW_VALUE);
        }

        cmd.add("--");
        cmd.add("-default-jvm");
        cmd.add(serverJvm.getCommand());
        addSystemPropertyArg(cmd, DOMAIN_BASE_DIR, getBaseDirectory());
        addSystemPropertyArg(cmd, DOMAIN_LOG_DIR, getLogDirectory());
        addSystemPropertyArg(cmd, DOMAIN_CONFIG_DIR, getConfigurationDirectory());

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
            return normalizePath("domain");
        }
        return baseDir;
    }

    @Override
    protected DomainCommandBuilder getThis() {
        return this;
    }

    @Override
    boolean addServerArgument(final Argument argument) {
        switch (argument.getKey()) {
            case DOMAIN_BASE_DIR:
                if (argument.getValue() != null) {
                    setBaseDirectory(argument.getValue());
                    return false;
                }
                break;
            case DOMAIN_CONFIG_DIR:
                if (argument.getValue() != null) {
                    setConfigurationDirectory(argument.getValue());
                    return false;
                }
                break;
            case DOMAIN_LOG_DIR:
                if (argument.getValue() != null) {
                    setLogDirectory(argument.getValue());
                    return false;
                }
                break;
        }
        return true;
    }
}
