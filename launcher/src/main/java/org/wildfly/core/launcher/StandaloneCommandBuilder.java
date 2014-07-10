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
import java.util.List;

/**
 * Builds a list of commands used to launch a standalone instance of WildFly Application Server.
 * <p/>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneCommandBuilder extends AbstractCommandBuilder<StandaloneCommandBuilder> implements CommandBuilder {

    // JPDA remote socket debugging
    static final String DEBUG_FORMAT = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%d";

    private static final String SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String SERVER_LOG_DIR = "jboss.server.log.dir";

    private Path baseDir;
    private Path javaHome;
    private String debugArg;
    private String modulesLocklessArg;
    private String modulesMetricsArg;

    private StandaloneCommandBuilder(final Path wildflyHome) {
        super(wildflyHome);
    }

    /**
     * Creates a command builder for a standalone instance of WildFly Application Server.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static StandaloneCommandBuilder of(final Path wildflyHome) {
        return new StandaloneCommandBuilder(validateWildFlyDir(wildflyHome));
    }

    /**
     * Creates a command builder for a standalone instance of WildFly Application Server.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static StandaloneCommandBuilder of(final String wildflyHome) {
        return new StandaloneCommandBuilder(validateWildFlyDir(wildflyHome));
    }

    @Override
    public StandaloneCommandBuilder addJvmArg(final String jvmArg) {
        if (jvmArg != null) {
            if (jvmArg.contains(SERVER_BASE_DIR)) {
                setBaseDirectory(getSystemPropertyValue(jvmArg));
            } else if (jvmArg.contains(SERVER_CONFIG_DIR)) {
                setServerConfiguration(getSystemPropertyValue(jvmArg));
            } else if (jvmArg.contains(SERVER_LOG_DIR)) {
                setLogDirectory(getSystemPropertyValue(jvmArg));
            } else {
                super.addJvmArg(jvmArg);
            }
        }
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
        if (javaHome == null) {
            this.javaHome = null;
        } else {
            this.javaHome = validateJavaHome(javaHome);
        }
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
        if (javaHome == null) {
            this.javaHome = null;
        } else {
            this.javaHome = validateJavaHome(javaHome);
        }
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

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();
        cmd.add("-D[Standalone]");
        cmd.addAll(getJvmArgs());
        cmd.add(getSystemPackages());
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
        cmd.add("-mp");
        cmd.add(getModulePaths());
        // TODO (jrp) Windows has -jaxpmodule "javax.xml.jax-provider", is this needed?
        cmd.add("org.jboss.as.standalone");
        addSystemPropertyArg(cmd, "jboss.home.dir", getWildFlyHome());
        addSystemPropertyArg(cmd, "jboss.server.base.dir", getBaseDirectory());
        addSystemPropertyArg(cmd, "jboss.server.log.dir", getLogDirectory());
        addSystemPropertyArg(cmd, "jboss.server.config.dir", getConfigurationDirectory());
        // TODO (jrp) FreeBSD may require -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider
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
        final Path path;
        if (javaHome == null) {
            path = validateJavaHome(System.getProperty("java.home"));
        } else {
            path = javaHome;
        }
        return path;
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

    private static String getSystemPropertyValue(final String arg) {
        if (arg.startsWith("-D")) {
            final int index = arg.indexOf('=') + 1;
            if (index > 0 && index <= arg.length()) {
                return arg.substring(index);
            }
        }
        return arg;
    }

    private static void addSystemPropertyArg(final List<String> cmd, final String key, final Object value) {
        if (value != null) {
            cmd.add(String.format("-D%s=%s", key, value));
        }
    }
}
