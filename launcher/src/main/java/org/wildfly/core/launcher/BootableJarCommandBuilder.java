/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.wildfly.core.launcher.AbstractCommandBuilder.DEFAULT_MODULAR_VM_ARGUMENTS;
import static org.wildfly.core.launcher.AbstractCommandBuilder.DEFAULT_VM_ARGUMENTS;

import org.wildfly.core.launcher.Arguments.Argument;
import static org.wildfly.core.launcher.StandaloneCommandBuilder.DEBUG_FORMAT;
import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * Builds a list of commands used to launch a bootable jar instance of WildFly.
 * <p/>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 *
 * @author <a href="mailto:jfdenise@redhat.com">JF Denise</a>
 */
@SuppressWarnings("unused")
public class BootableJarCommandBuilder implements CommandBuilder {

    private final Arguments javaOpts;
    private String debugArg;
    private String modulesLocklessArg;
    private String modulesMetricsArg;
    private final Map<String, String> securityProperties;
    private final Path bootableJar;
    private Jvm jvm;
    private final Arguments serverArgs;

    /**
     * Creates a new command builder for a bootable instance.
     * <p/>
     * Note the {@code bootableJar} and {@code javaHome} are not validated using
     * the constructor. The static {@link
     * #of(java.nio.file.Path)} is preferred.
     *
     * @param bootableJar the path to the bootable jar file.
     */
    private BootableJarCommandBuilder(final Path bootableJar) {
        this.bootableJar = bootableJar;
        javaOpts = new Arguments();
        javaOpts.addAll(DEFAULT_VM_ARGUMENTS);
        securityProperties = new LinkedHashMap<>();
        serverArgs = new Arguments();
        jvm = Jvm.current();
    }

    /**
     * Set the directory to install the server.
     *
     * @param installDir Installation directory.
     * @return This builder.
     */
    public BootableJarCommandBuilder setInstallDir(Path installDir) {
        setSingleServerArg("--install-dir", installDir.toString());
        return this;
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
     * Adds the arguments to the collection of arguments that will be passed to
     * the server ignoring any {@code null} arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public BootableJarCommandBuilder addServerArguments(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addServerArgument(arg);
            }
        }
        return this;
    }

    /**
     * Adds the arguments to the collection of arguments that will be passed to
     * the server ignoring any {@code null} arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public BootableJarCommandBuilder addServerArguments(final Iterable<String> args) {
        if (args != null) {
            for (String arg : args) {
                addServerArgument(arg);
            }
        }
        return this;
    }

    /**
     * Adds an argument to be passed to the server ignore the argument if
     * {@code null}.
     *
     * @param arg the argument to pass
     *
     * @return the builder
     */
    public BootableJarCommandBuilder addServerArgument(final String arg) {
        if (arg != null) {
            serverArgs.add(Arguments.parse(arg));
        }
        return this;
    }

    /**
     * Sets the system property {@code jboss.bind.address} to the address given.
     * <p/>
     * This will override any previous value set via
     * {@link #addServerArgument(String)}.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has
     * not been removed from the interface. If the system property was removed
     * the address provided has no effect.
     *
     * @param address the address to set the bind address to
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setBindAddressHint(final String address) {
        setSingleServerArg("-b", address);
        return this;
    }

    /**
     * Sets the system property {@code jboss.bind.address.$INTERFACE} to the
     * address given where {@code $INTERFACE} is the {@code interfaceName}
     * parameter. For example in the default configuration passing
     * {@code management} for the {@code interfaceName} parameter would result
     * in the system property {@code jboss.bind.address.management} being set to
     * the address provided.
     * <p/>
     * This will override any previous value set via
     * {@link #addServerArgument(String)}.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has
     * not been removed from the interface. If the system property was removed
     * the address provided has no effect.
     *
     * @param interfaceName the name of the interface of the binding address
     * @param address the address to bind the management interface to
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setBindAddressHint(final String interfaceName, final String address) {
        if (interfaceName == null) {
            throw LauncherMessages.MESSAGES.nullParam("interfaceName");
        }
        setSingleServerArg("-b" + interfaceName, address);
        return this;
    }

    /**
     * Sets the system property {@code jboss.default.multicast.address} to the
     * address given.
     * <p/>
     * This will override any previous value set via
     * {@link #addServerArgument(String)}.
     * <p/>
     * <b>Note:</b> This option only works if the standard system property has
     * not been removed from the interface. If the system property was removed
     * the address provided has no effect.
     *
     * @param address the address to set the multicast system property to
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setMulticastAddressHint(final String address) {
        setSingleServerArg("-u", address);
        return this;
    }

    private void setSingleServerArg(final String key, final String value) {
        serverArgs.set(key, value);
    }

    /**
     * Creates a command builder for a bootable instance of WildFly.
     *
     * @param bootableJar the path to the bootable jar
     *
     * @return a new builder
     */
    public static BootableJarCommandBuilder of(final Path bootableJar) {
        return new BootableJarCommandBuilder(Environment.validateBootableJar(bootableJar));
    }

    /**
     * Creates a command builder for a bootable instance of WildFly.
     *
     * @param bootableJar the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static BootableJarCommandBuilder of(final String bootableJar) {
        return new BootableJarCommandBuilder(Environment.validateBootableJar(bootableJar));
    }

    /**
     * Adds a JVM argument to the command ignoring {@code null} arguments.
     *
     * @param jvmArg the JVM argument to add
     *
     * @return the builder
     */
    public BootableJarCommandBuilder addJavaOption(final String jvmArg) {
        if (jvmArg != null && !jvmArg.trim().isEmpty()) {
            final Argument argument = Arguments.parse(jvmArg);
            javaOpts.add(argument);
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
    public BootableJarCommandBuilder addJavaOptions(final String... javaOpts) {
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
    public BootableJarCommandBuilder addJavaOptions(final Iterable<String> javaOpts) {
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
    public BootableJarCommandBuilder setJavaOptions(final Iterable<String> javaOpts) {
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
    public BootableJarCommandBuilder setJavaOptions(final String... javaOpts) {
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
     * Sets the debug argument for the JVM with a default port of {@code 8787}.
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setDebug() {
        return setDebug(false, 8787);
    }

    /**
     * Sets the debug argument for the JVM.
     *
     * @param port the port to listen on
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setDebug(final int port) {
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
    public BootableJarCommandBuilder setDebug(final boolean suspend, final int port) {
        debugArg = String.format(DEBUG_FORMAT, (suspend ? "y" : "n"), port);
        return this;
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setJavaHome(final String javaHome) {
        jvm = Jvm.of(javaHome);
        return this;
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setJavaHome(final Path javaHome) {
        jvm = Jvm.of(javaHome);
        return this;
    }

    /**
     * Set to {@code true} to use JBoss Modules lockless mode.
     *
     * @param b {@code true} to use lockless mode
     *
     * @return the builder
     */
    public BootableJarCommandBuilder setModulesLockless(final boolean b) {
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
    public BootableJarCommandBuilder setModulesMetrics(final boolean b) {
        if (b) {
            modulesMetricsArg = "-Djboss.modules.metrics=true";
        } else {
            modulesMetricsArg = null;
        }
        return this;
    }

    /**
     * Adds a security property to be passed to the server with a {@code null} value.
     *
     * @param key the property key
     *
     * @return the builder
     */
    public BootableJarCommandBuilder addSecurityProperty(final String key) {
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
    public BootableJarCommandBuilder addSecurityProperty(final String key, final String value) {
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
    public BootableJarCommandBuilder addSecurityProperties(final Map<String, String> properties) {
        securityProperties.putAll(properties);
        return this;
    }

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();
        cmd.addAll(getJavaOptions());
        if (jvm.isModular()) {
            cmd.addAll(DEFAULT_MODULAR_VM_ARGUMENTS);
        }
        if (modulesLocklessArg != null) {
            cmd.add(modulesLocklessArg);
        }
        if (modulesMetricsArg != null) {
            cmd.add(modulesMetricsArg);
        }
        if (debugArg != null) {
            cmd.add(debugArg);
        }

        cmd.add("-jar");

        cmd.add(bootableJar.toString());

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
        cmd.add(jvm.getCommand());
        cmd.addAll(buildArguments());
        return cmd;
    }

    /**
     * Returns the Java home directory where the java executable command can be
     * found.
     * <p/>
     * If the directory was not set the system property value,
     * {@code java.home}, should be used.
     *
     * @return the path to the Java home directory
     */
    public Path getJavaHome() {
        return jvm.getPath();
    }
}
