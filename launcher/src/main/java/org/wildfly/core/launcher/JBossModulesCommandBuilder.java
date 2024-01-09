/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.launcher;

import static org.wildfly.core.launcher.logger.LauncherMessages.MESSAGES;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.wildfly.core.launcher.Arguments.Argument;

/**
 * Builds a list of commands used to launch JBoss Modules module.
 * <p/>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class JBossModulesCommandBuilder implements CommandBuilder {

    static final String[] DEFAULT_VM_ARGUMENTS = {
            "-Xms64m",
            "-Xmx512m",
            "-Djava.net.preferIPv4Stack=true",
            "-Djava.awt.headless=true",
            "-Djboss.modules.system.pkgs=org.jboss.byteman"
    };
    /**
     * Packages being exported or opened are available on all JVMs
     */
    static final Collection<String> DEFAULT_MODULAR_VM_ARGUMENTS;
    /**
     * Packages being exported or opened may not be available on all JVMs
     */
    static final Collection<String> OPTIONAL_DEFAULT_MODULAR_VM_ARGUMENTS;
    private static final String ALLOW_VALUE = "allow";
    private static final String DISALLOW_VALUE = "disallow";
    static final String SECURITY_MANAGER_ARG = "-secmgr";
    static final String SECURITY_MANAGER_PROP = "java.security.manager";
    static final String SECURITY_MANAGER_PROP_WITH_ALLOW_VALUE = "-D" + SECURITY_MANAGER_PROP + "=" + ALLOW_VALUE;

    static {
        // Default JVM parameters for all modular JDKs
        // Additions to these should include good explanations why in the relevant JIRA
        // Keep them alphabetical to avoid the code history getting confused by reordering commits
        final List<String> modularJavaOpts;
        final boolean skipJPMSOptions = Boolean.getBoolean("launcher.skip.jpms.properties");
        if (!skipJPMSOptions) {
            modularJavaOpts = List.of(
                    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
                    "--add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED",
                    "--add-exports=java.naming/com.sun.jndi.url.ldap=ALL-UNNAMED",
                    "--add-exports=java.naming/com.sun.jndi.url.ldaps=ALL-UNNAMED",
                    "--add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                    "--add-opens=java.management/javax.management=ALL-UNNAMED",
                    "--add-opens=java.naming/javax.naming=ALL-UNNAMED",
                    // As of jboss-modules 1.9.1.Final the java.se module is no longer required to be added. However as this API is
                    // designed to work with older versions of the server we still need to add this argument of modular JVM's.
                    "--add-modules=java.se"
            );
        } else {
            modularJavaOpts = List.of();
        }
        DEFAULT_MODULAR_VM_ARGUMENTS = modularJavaOpts;

        final List<String> optionalModularJavaOpts;
        if (!skipJPMSOptions) {
            optionalModularJavaOpts = List.of(("--add-opens=java.base/com.sun.net.ssl.internal.ssl=ALL-UNNAMED"));
        } else {
            optionalModularJavaOpts = List.of();
        }
        OPTIONAL_DEFAULT_MODULAR_VM_ARGUMENTS = optionalModularJavaOpts;
    }

    final Environment environment;
    final Arguments serverArgs;
    final Arguments javaOpts;
    private final String moduleName;
    private String modulesLocklessArg;
    private String modulesMetricsArg;
    private boolean useSecMgr;
    private boolean addModuleAgent;
    private final Collection<String> moduleOpts;

    /**
     * Creates a command builder for a launching JBoss Modules module.
     * <p/>
     * Note the {@code wildflyHome} and {@code javaHome} are not validated using the constructor. The static {@link
     * #of(Path, String)} is preferred.
     *
     * @param wildflyHome the path to WildFly, cannot be {@code null}
     * @param moduleName  the name of the entry module, cannot be {@code null}
     */
    protected JBossModulesCommandBuilder(final Path wildflyHome, final String moduleName) {
        this(wildflyHome, null, moduleName);
    }

    /**
     * Creates a command builder for a launching JBoss Modules module.
     * <p/>
     * Note the {@code wildflyHome} and {@code javaHome} are not validated using the constructor. The static {@link
     * #of(Path, String)} is preferred.
     *
     * @param wildflyHome the path to WildFly, cannot be {@code null}
     * @param jvm         the JVM used for the command builder, {@code null} will use the current JVM
     * @param moduleName  the name of the entry module, cannot be {@code null}
     */
    JBossModulesCommandBuilder(final Path wildflyHome, final Jvm jvm, final String moduleName) {
        if (wildflyHome == null) {
            throw MESSAGES.nullParam("wildflyHome");
        }
        if (moduleName == null) {
            throw MESSAGES.nullParam("moduleName");
        }
        this.environment = new Environment(wildflyHome).setJvm(jvm);
        this.serverArgs = new Arguments();
        this.moduleName = moduleName;
        javaOpts = new Arguments();
        moduleOpts = new ArrayList<>();
        addModuleAgent = false;
    }

    /**
     * Creates a command builder for a launching JBoss Modules module.
     *
     * @param wildflyHome the path to the WildFly home directory, cannot be {@code null}
     * @param moduleName  the name of the entry module, cannot be {@code null}
     *
     * @return a new builder
     */
    public static JBossModulesCommandBuilder of(final Path wildflyHome, final String moduleName) {
        if (moduleName == null) {
            throw MESSAGES.nullParam("moduleName");
        }
        return new JBossModulesCommandBuilder(Environment.validateWildFlyDir(wildflyHome), moduleName);
    }

    /**
     * Creates a command builder for a launching JBoss Modules module.
     *
     * @param wildflyHome the path to the WildFly home directory, cannot be {@code null}
     * @param moduleName  the name of the entry module, cannot be {@code null}
     *
     * @return a new builder
     */
    public static JBossModulesCommandBuilder of(final String wildflyHome, final String moduleName) {
        if (moduleName == null) {
            throw MESSAGES.nullParam("moduleName");
        }
        return new JBossModulesCommandBuilder(Environment.validateWildFlyDir(wildflyHome), moduleName);
    }


    /**
     * Sets whether or not the security manager option, {@code -secmgr}, should be used.
     *
     * @param useSecMgr {@code true} to use the a security manager, otherwise {@code false}
     *
     * @return the builder
     */
    public JBossModulesCommandBuilder setUseSecurityManager(final boolean useSecMgr) {
        this.useSecMgr = useSecMgr;
        return this;
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
    public JBossModulesCommandBuilder addModuleDir(final String moduleDir) {
        environment.addModuleDir(moduleDir);
        return this;
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
    public JBossModulesCommandBuilder addModuleDirs(final String... moduleDirs) {
        environment.addModuleDirs(moduleDirs);
        return this;
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
    public JBossModulesCommandBuilder addModuleDirs(final Iterable<String> moduleDirs) {
        environment.addModuleDirs(moduleDirs);
        return this;
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
    public JBossModulesCommandBuilder setModuleDirs(final Iterable<String> moduleDirs) {
        environment.setModuleDirs(moduleDirs);
        return this;
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
    public JBossModulesCommandBuilder setModuleDirs(final String... moduleDirs) {
        environment.setModuleDirs(moduleDirs);
        return this;
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
     * Adds an argument to be passed to the server ignore the argument if {@code null}.
     *
     * @param arg the argument to pass
     *
     * @return the builder
     */
    public JBossModulesCommandBuilder addServerArgument(final String arg) {
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
        return this;
    }

    /**
     * Adds the arguments to the collection of arguments that will be passed to the server ignoring any {@code null}
     * arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public JBossModulesCommandBuilder addServerArguments(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addServerArgument(arg);
            }
        }
        return this;
    }

    /**
     * Adds the arguments to the collection of arguments that will be passed to the server ignoring any {@code null}
     * arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public JBossModulesCommandBuilder addServerArguments(final Iterable<String> args) {
        if (args != null) {
            for (String arg : args) {
                addServerArgument(arg);
            }
        }
        return this;
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
    public Path getJavaHome() {
        return environment.getWildflyHome();
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
     * A collection of server command line arguments.
     *
     * @return the server arguments
     */
    public List<String> getServerArguments() {
        return serverArgs.asList();
    }

    /**
     * Adds a JVM argument to the command ignoring {@code null} arguments.
     *
     * @param jvmArg the JVM argument to add
     *
     * @return the builder
     */
    public JBossModulesCommandBuilder addJavaOption(final String jvmArg) {
        if (jvmArg != null && !jvmArg.isBlank()) {
            final Argument argument = Arguments.parse(jvmArg);
            if (argument.getKey().equals(SECURITY_MANAGER_PROP)) {
                // [WFCORE-5778] java.security.manager system property with value "allow" detected.
                // It doesn't mean SM is going to be installed but it indicates SM can be installed dynamically.
                setUseSecurityManager(isJavaSecurityManagerConfigured(argument));
            } else {
                javaOpts.add(argument);
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
    public JBossModulesCommandBuilder addJavaOptions(final String... javaOpts) {
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
    public JBossModulesCommandBuilder addJavaOptions(final Iterable<String> javaOpts) {
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
    public JBossModulesCommandBuilder setJavaOptions(final Iterable<String> javaOpts) {
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
    public JBossModulesCommandBuilder setJavaOptions(final String... javaOpts) {
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
    public JBossModulesCommandBuilder addModuleOption(final String arg) {
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
    public JBossModulesCommandBuilder addModuleOptions(final String... args) {
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
    public JBossModulesCommandBuilder addModuleOptions(final Iterable<String> args) {
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
    public JBossModulesCommandBuilder setModuleOptions(final String... args) {
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
    public JBossModulesCommandBuilder setModuleOptions(final Iterable<String> args) {
        moduleOpts.clear();
        addModuleOptions(args);
        return this;
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     *
     * @return the builder
     */
    public JBossModulesCommandBuilder setJavaHome(final String javaHome) {
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
    public JBossModulesCommandBuilder setJavaHome(final Path javaHome) {
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
    public JBossModulesCommandBuilder setModulesLockless(final boolean b) {
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
    public JBossModulesCommandBuilder setModulesMetrics(final boolean b) {
        if (b) {
            modulesMetricsArg = "-Djboss.modules.metrics=true";
        } else {
            modulesMetricsArg = null;
        }
        return this;
    }

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();
        // Check to see if an agent was added as a module option, if so we want to add JBoss Modules as an agent.
        if (addModuleAgent) {
            cmd.add("-javaagent:" + getModulesJarName());
        }
        cmd.addAll(getJavaOptions());
        if (environment.getJvm().isModular()) {
            cmd.addAll(DEFAULT_MODULAR_VM_ARGUMENTS);
            for (final String optionalModularArgument : OPTIONAL_DEFAULT_MODULAR_VM_ARGUMENTS) {
                if (Jvm.isPackageAvailable(environment.getJvm().getPath(), optionalModularArgument)) {
                    cmd.add(optionalModularArgument);
                }
            }
        }
        if (environment.getJvm().enhancedSecurityManagerAvailable()) {
            cmd.add(SECURITY_MANAGER_PROP_WITH_ALLOW_VALUE);
        }
        // Add these to JVM level system properties
        if (modulesLocklessArg != null) {
            cmd.add(modulesLocklessArg);
        }
        if (modulesMetricsArg != null) {
            cmd.add(modulesMetricsArg);
        }
        cmd.add("-jar");
        cmd.add(getModulesJarName());
        if (useSecurityManager()) {
            cmd.add(SECURITY_MANAGER_ARG);
        }
        // Add the agent argument for jboss-modules
        cmd.addAll(moduleOpts);
        cmd.add("-mp");
        cmd.add(getModulePaths());
        cmd.add(moduleName);

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
    boolean addServerArgument(final Argument argument) {
        return true;
    }

    static boolean isJavaSecurityManagerConfigured(final Argument argument) {
        return !ALLOW_VALUE.equals(argument.getValue()) && !DISALLOW_VALUE.equals(argument.getValue());
    }
}
