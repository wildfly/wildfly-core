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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds a list of commands used to launch a domain instance of WildFly Application Server.
 * <p/>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainCommandBuilder extends AbstractCommandBuilder<DomainCommandBuilder> implements CommandBuilder {

    private final Path javaHome;
    private Path hostControllerJavaHome;
    private Path serverJavaHome;
    private Path baseDir;
    private final List<String> hostControllerJvmArgs;
    private final List<String> processControllerJvmArgs;

    private DomainCommandBuilder(final Path wildflyHome, final Path javaHome) {
        super(wildflyHome);
        this.javaHome = javaHome.normalize();
        hostControllerJvmArgs = new ArrayList<>();
        processControllerJvmArgs = new ArrayList<>();
    }

    /**
     * Creates a command builder for a standalone instance of WildFly Application Server.
     * <p/>
     * Uses the system property {@code java.home} to find the java executable required for the host controller and
     * servers.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final Path wildflyHome) {
        return new DomainCommandBuilder(validateWildFlyDir(wildflyHome), validateJavaHome(System.getProperty("java.home")));
    }

    /**
     * Creates a command builder for a standalone instance of WildFly Application Server.
     * <p/>
     * Uses the system property {@code java.home} to find the java executable required for the host controller and
     * servers.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final String wildflyHome) {
        return new DomainCommandBuilder(validateWildFlyDir(wildflyHome), validateJavaHome(System.getProperty("java.home")));
    }

    /**
     * Creates a command builder for a standalone instance of WildFly Application Server.
     *
     * @param wildflyHome the path to the WildFly home directory
     * @param javaHome    the path to the Java home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final String wildflyHome, final String javaHome) {
        return new DomainCommandBuilder(validateWildFlyDir(wildflyHome), validateJavaHome(javaHome));
    }

    /**
     * Creates a command builder for a standalone instance of WildFly Application Server.
     *
     * @param wildflyHome the path to the WildFly home directory
     * @param javaHome    the path to the Java home directory
     *
     * @return a new builder
     */
    public static DomainCommandBuilder of(final Path wildflyHome, final Path javaHome) {
        return new DomainCommandBuilder(validateWildFlyDir(wildflyHome), validateJavaHome(javaHome));
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
        if (javaHome == null) {
            this.hostControllerJavaHome = null;
        } else {
            this.hostControllerJavaHome = validateJavaHome(javaHome);
        }
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
        if (javaHome == null) {
            this.hostControllerJavaHome = null;
        } else {
            this.hostControllerJavaHome = validateJavaHome(javaHome);
        }
        return this;
    }

    /**
     * Returns the Java home path the host controller will use.
     * <p/>
     * If the path was not previously set the default {@link #getJavaHome() Java home} will be used.
     *
     * @return the path to the Java home for the host controller
     */
    public Path getHostControllerJavaHome() {
        if (hostControllerJavaHome == null) {
            return getJavaHome();
        }
        return hostControllerJavaHome;
    }

    /**
     * Adds a JVM argument to the host controller ignoring {@code null} values.
     * <p/>
     * Host controller JVM arguments also include the main {@link #addJvmArg(String) JVM arguments}.
     *
     * @param arg the argument to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addHostControllerJvmArg(final String arg) {
        if (arg != null) {
            hostControllerJvmArgs.add(arg);
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the host controller ignoring {@code null} values.
     * <p/>
     * Host controller JVM arguments also include the main {@link #addJvmArg(String) JVM arguments}.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addHostControllerJvmArgs(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addHostControllerJvmArg(arg);
            }
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the host controller ignoring {@code null} values.
     * <p/>
     * Host controller JVM arguments also include the main {@link #addJvmArg(String) JVM arguments}.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addHostControllerJvmArgs(final Collection<String> args) {
        if (args != null) {
            for (String arg : args) {
                addHostControllerJvmArg(arg);
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
    public DomainCommandBuilder setHostControllerJvmArgs(final String... args) {
        hostControllerJvmArgs.clear();
        return addHostControllerJvmArgs(args);
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
    public DomainCommandBuilder setHostControllerJvmArgs(final Collection<String> args) {
        hostControllerJvmArgs.clear();
        return addHostControllerJvmArgs(args);
    }

    /**
     * Returns the JVM arguments for the host controller.
     *
     * @return the JVM arguments
     */
    public List<String> getHostControllerJvmArgs() {
        return new ArrayList<>(hostControllerJvmArgs);
    }

    /**
     * Adds a JVM argument to the process controller ignoring {@code null} values.
     * <p/>
     * Process controller JVM arguments also include the main {@link #addJvmArg(String) JVM arguments}.
     *
     * @param arg the argument to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addProcessControllerJvmArg(final String arg) {
        if (arg != null) {
            processControllerJvmArgs.add(arg);
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the process controller ignoring {@code null} values.
     * <p/>
     * Process controller JVM arguments also include the main {@link #addJvmArg(String) JVM arguments}.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addProcessControllerJvmArgs(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addProcessControllerJvmArg(arg);
            }
        }
        return this;
    }

    /**
     * Adds a JVM arguments to the process controller ignoring {@code null} values.
     * <p/>
     * Process controller JVM arguments also include the main {@link #addJvmArg(String) JVM arguments}.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public DomainCommandBuilder addProcessControllerJvmArgs(final Collection<String> args) {
        if (args != null) {
            for (String arg : args) {
                addProcessControllerJvmArg(arg);
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
    public DomainCommandBuilder setProcessControllerJvmArgs(final String... args) {
        processControllerJvmArgs.clear();
        return addProcessControllerJvmArgs(args);
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
    public DomainCommandBuilder setProcessControllerJvmArgs(final Collection<String> args) {
        processControllerJvmArgs.clear();
        return addProcessControllerJvmArgs(args);
    }

    /**
     * Returns the JVM arguments used for the process controller.
     *
     * @return the JVM arguments
     */
    public List<String> getProcessControllerJvmArgs() {
        return new ArrayList<>(processControllerJvmArgs);
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
        if (javaHome == null) {
            this.serverJavaHome = null;
        } else {
            this.serverJavaHome = validateJavaHome(javaHome);
        }
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
        if (javaHome == null) {
            this.serverJavaHome = null;
        } else {
            this.serverJavaHome = validateJavaHome(javaHome);
        }
        return this;
    }

    /**
     * Returns the Java home path the servers will use.
     * <p/>
     * If the path was not previously set the default {@link #getJavaHome() Java home} will be used.
     *
     * @return the path to the Java home for the servers
     */
    public Path getServerJavaHome() {
        if (serverJavaHome == null) {
            return getJavaHome();
        }
        return serverJavaHome;
    }

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();

        // Process Controller
        cmd.add("-D[Process Controller]");

        // PROCESS_CONTROLLER_JAVA_OPTS
        cmd.addAll(getJvmArgs());
        cmd.add(getSystemPackages());
        cmd.addAll(processControllerJvmArgs);

        cmd.add(getBootLogArgument("process-controller.log"));
        cmd.add(getLoggingPropertiesArgument("logging.properties"));
        cmd.add("-jar");
        cmd.add(getModulesJarName());
        cmd.add("-mp");
        cmd.add(getModulePaths());
        cmd.add("org.jboss.as.process-controller");
        cmd.add("-jboss-home");
        cmd.add(getWildFlyHome().toString());
        cmd.add("-jvm");
        cmd.add(getHostControllerJavaCommand());
        cmd.add("-mp");
        cmd.add(getModulePaths());

        // Host Controller
        cmd.add("--");
        cmd.add(getBootLogArgument("host-controller.log"));
        cmd.add(getLoggingPropertiesArgument("logging.properties"));

        // HOST_CONTROLLER_JAVA_OPTS
        cmd.addAll(getJvmArgs());
        cmd.add(getSystemPackages());
        cmd.addAll(hostControllerJvmArgs);

        cmd.add("--");
        cmd.add("-default-jvm");
        cmd.add(getServerJavaCommand());
        cmd.addAll(getServerArguments());
        return cmd;
    }

    @Override
    public List<String> build() {
        final List<String> cmd = new ArrayList<>();
        cmd.add(getJavaCommand());
        cmd.addAll(buildArguments());
        return cmd;
    }

    @Override
    public Path getJavaHome() {
        return javaHome;
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

    private String getHostControllerJavaCommand() {
        if (hostControllerJavaHome != null) {
            return getJavaCommand(hostControllerJavaHome);
        }
        return getJavaCommand();
    }

    private String getServerJavaCommand() {
        if (serverJavaHome != null) {
            return getJavaCommand(serverJavaHome);
        }
        return getJavaCommand();
    }
}
