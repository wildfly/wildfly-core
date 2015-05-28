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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.wildfly.core.launcher.Arguments.Argument;
import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * Builds a list of commands to create a new process for a CLI instance.
 * <p>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class CliCommandBuilder extends AbstractCommandBuilder<CliCommandBuilder> implements CommandBuilder {

    enum CliArgument {
        CONNECT("--connect", "-c"),
        CONTROLLER("--controller", "controller"),
        GUI("--gui"),
        FILE("--file"),
        COMMAND("--command"),
        COMMANDS("--commands"),
        USER("--user", "-u"),
        PASSWORD("--password", "-p"),
        TIMEOUT("--timeout"),;
        private static final Map<String, CliArgument> ENTRIES;

        static {
            final Map<String, CliArgument> map = new HashMap<>();
            for (CliArgument arg : values()) {
                map.put(arg.key, arg);
                if (arg.altKey != null) {
                    map.put(arg.altKey, arg);
                }
            }
            ENTRIES = Collections.unmodifiableMap(map);
        }

        public static CliArgument find(final String key) {
            return ENTRIES.get(key);
        }

        public static CliArgument find(final Argument argument) {
            return ENTRIES.get(argument.getKey());
        }

        private final String key;
        private final String altKey;

        CliArgument(final String key) {
            this(key, null);
        }

        CliArgument(final String key, final String altKey) {
            this.key = key;
            this.altKey = altKey;
        }
    }

    private Path javaHome;
    private final Arguments javaOpts;
    private final Arguments cliArgs;

    private CliCommandBuilder(final Path wildflyHome) {
        super(wildflyHome);
        javaOpts = new Arguments();
        cliArgs = new Arguments();
        // Add the default logging.properties file
        javaOpts.add("-Dlogging.configuration=file:" + wildflyHome.resolve("bin").resolve("jboss-cli-logging.properties"));
    }

    /**
     * Creates a command builder for a CLI instance.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static CliCommandBuilder of(final Path wildflyHome) {
        return new CliCommandBuilder(validateWildFlyDir(wildflyHome));
    }

    /**
     * Creates a command builder for a CLI instance.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static CliCommandBuilder of(final String wildflyHome) {
        return new CliCommandBuilder(validateWildFlyDir(wildflyHome));
    }

    /**
     * Sets the hostname and port to connect to.
     * <p>
     * This sets both the {@code --connect} and {@code --controller} arguments.
     * </p>
     *
     * @param controller the controller argument to use
     *
     * @return the builder
     */
    public CliCommandBuilder setConnection(final String controller) {
        addCliArgument(CliArgument.CONNECT);
        addCliArgument(CliArgument.CONTROLLER, controller);
        return this;
    }

    /**
     * Sets the hostname and port to connect to.
     * <p>
     * This sets both the {@code --connect} and {@code --controller} arguments.
     * </p>
     *
     * @param hostname the host name
     * @param port     the port
     *
     * @return the builder
     */
    public CliCommandBuilder setConnection(final String hostname, final int port) {
        addCliArgument(CliArgument.CONNECT);
        setController(hostname, port);
        return this;
    }

    /**
     * Sets the protocol, hostname and port to connect to.
     * <p>
     * This sets both the {@code --connect} and {@code --controller} arguments.
     * </p>
     *
     * @param protocol the protocol to use
     * @param hostname the host name
     * @param port     the port
     *
     * @return the builder
     */
    public CliCommandBuilder setConnection(final String protocol, final String hostname, final int port) {
        addCliArgument(CliArgument.CONNECT);
        setController(protocol, hostname, port);
        return this;
    }

    /**
     * Sets the hostname and port to connect to.
     *
     * @param controller the controller argument to use
     *
     * @return the builder
     */
    public CliCommandBuilder setController(final String controller) {
        addCliArgument(CliArgument.CONTROLLER, controller);
        return this;
    }

    /**
     * Sets the hostname and port to connect to.
     *
     * @param hostname the host name
     * @param port     the port
     *
     * @return the builder
     */
    public CliCommandBuilder setController(final String hostname, final int port) {
        setController(String.format("%s:%d", hostname, port));
        return this;
    }

    /**
     * Sets the protocol, hostname and port to connect to.
     *
     * @param protocol the protocol to use
     * @param hostname the host name
     * @param port     the port
     *
     * @return the builder
     */
    public CliCommandBuilder setController(final String protocol, final String hostname, final int port) {
        setController(String.format("%s://%s:%d", protocol, hostname, port));
        return this;
    }

    /**
     * Sets the user to use when establishing a connection.
     *
     * @param user the user to use
     *
     * @return the builder
     */
    public CliCommandBuilder setUser(final String user) {
        addCliArgument(CliArgument.USER, user);
        return this;
    }

    /**
     * Sets the password to use when establishing a connection.
     *
     * @param password the password to use
     *
     * @return the builder
     */
    public CliCommandBuilder setPassword(final String password) {
        addCliArgument(CliArgument.PASSWORD, password);
        return this;
    }

    /**
     * Sets the path to the script file to execute.
     *
     * @param path the path to the script file to execute
     *
     * @return the builder
     */
    public CliCommandBuilder setScriptFile(final String path) {
        if (path == null) {
            addCliArgument(CliArgument.FILE, null);
            return this;
        }
        return setScriptFile(Paths.get(path));
    }

    /**
     * Sets the path to the script file to execute.
     *
     * @param path the path to the script file to execute
     *
     * @return the builder
     */
    public CliCommandBuilder setScriptFile(final Path path) {
        if (path == null) {
            addCliArgument(CliArgument.FILE, null);
        } else {
            // Make sure the path exists
            if (Files.notExists(path)) {
                throw LauncherMessages.MESSAGES.pathDoesNotExist(path);
            }
            addCliArgument(CliArgument.FILE, path.toString());
        }
        return this;
    }

    /**
     * Sets the command to execute.
     *
     * @param command the command to execute
     *
     * @return the builder
     */
    public CliCommandBuilder setCommand(final String command) {
        addCliArgument(CliArgument.COMMAND, command);
        return this;
    }

    /**
     * Sets the commands to execute.
     *
     * @param commands the commands to execute
     *
     * @return the builder
     */
    public CliCommandBuilder setCommands(final String... commands) {
        if (commands == null || commands.length == 0) {
            addCliArgument(CliArgument.COMMANDS, null);
            return this;
        }
        return setCommands(Arrays.asList(commands));
    }

    /**
     * Sets the commands to execute.
     *
     * @param commands the commands to execute
     *
     * @return the builder
     */
    public CliCommandBuilder setCommands(final Iterable<String> commands) {
        if (commands == null) {
            addCliArgument(CliArgument.COMMANDS, null);
            return this;
        }
        final StringBuilder cmds = new StringBuilder();
        for (final Iterator<String> iterator = commands.iterator(); iterator.hasNext(); ) {
            cmds.append(iterator.next());
            if (iterator.hasNext()) cmds.append(',');
        }
        addCliArgument(CliArgument.COMMANDS, cmds.toString());
        return this;
    }

    /**
     * Sets the timeout used when connecting to the server.
     *
     * @param timeout the time out to use
     *
     * @return the builder
     */
    public CliCommandBuilder setTimeout(final int timeout) {
        if (timeout > 0) {
            addCliArgument(CliArgument.TIMEOUT, Integer.toString(timeout));
        } else {
            addCliArgument(CliArgument.TIMEOUT, null);
        }
        return this;
    }

    /**
     * Sets the command argument to use the GUI CLI client.
     *
     * @return the builder
     */
    public CliCommandBuilder setUseGui() {
        if (IS_MAC) {
            addJavaOption("-Djboss.modules.system.pkgs=com.apple.laf,com.apple.laf.resources");
        } else {
            addJavaOption("-Djboss.modules.system.pkgs=com.sun.java.swing");
        }
        addCliArgument(CliArgument.GUI);
        return this;
    }

    /**
     * Adds a JVM argument to the command ignoring {@code null} arguments.
     *
     * @param jvmArg the JVM argument to add
     *
     * @return the builder
     */
    public CliCommandBuilder addJavaOption(final String jvmArg) {
        if (jvmArg != null && !jvmArg.trim().isEmpty()) {
            javaOpts.add(jvmArg);
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
    public CliCommandBuilder addJavaOptions(final String... javaOpts) {
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
    public CliCommandBuilder addJavaOptions(final Iterable<String> javaOpts) {
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
    public CliCommandBuilder setJavaOptions(final Iterable<String> javaOpts) {
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
    public CliCommandBuilder setJavaOptions(final String... javaOpts) {
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
     * Adds an argument to be passed to the CLI command ignore the argument if {@code null}.
     *
     * @param arg the argument to pass
     *
     * @return the builder
     */
    public CliCommandBuilder addCliArgument(final String arg) {
        if (arg != null) {
            final Argument argument = Arguments.parse(arg);
            final CliArgument cliArgument = CliArgument.find(argument.getKey());
            if (cliArgument != null) {
                cliArgs.remove(cliArgument.key);
                if (cliArgument.altKey != null) {
                    cliArgs.remove(cliArgument.altKey);
                }
            }
            cliArgs.add(argument);
        }
        return this;
    }

    /**
     * Adds the arguments to the collection of arguments that will be passed to the CLI command ignoring any {@code
     * null} arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public CliCommandBuilder addCliArguments(final String... args) {
        if (args != null) {
            for (String arg : args) {
                addCliArgument(arg);
            }
        }
        return this;
    }

    /**
     * Adds the arguments to the collection of arguments that will be passed to the CLI command ignoring any {@code
     * null} arguments.
     *
     * @param args the arguments to add
     *
     * @return the builder
     */
    public CliCommandBuilder addCliArguments(final Iterable<String> args) {
        if (args != null) {
            for (String arg : args) {
                addCliArgument(arg);
            }
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
    public CliCommandBuilder setJavaHome(final String javaHome) {
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
    public CliCommandBuilder setJavaHome(final Path javaHome) {
        if (javaHome == null) {
            this.javaHome = null;
        } else {
            this.javaHome = validateJavaHome(javaHome);
        }
        return this;
    }

    @Override
    public Path getJavaHome() {
        final Path path;
        if (javaHome == null) {
            path = getDefaultJavaHome();
        } else {
            path = javaHome;
        }
        return path;
    }

    @Override
    protected CliCommandBuilder getThis() {
        return this;
    }

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();
        cmd.addAll(getJavaOptions());
        cmd.add("-jar");
        cmd.add(getModulesJarName());
        cmd.add("-mp");
        cmd.add(getModulePaths());
        cmd.add("org.jboss.as.cli");
        addSystemPropertyArg(cmd, HOME_DIR, getWildFlyHome());

        cmd.addAll(cliArgs.asList());
        return cmd;
    }

    @Override
    public List<String> build() {
        final List<String> cmd = new ArrayList<>();
        cmd.add(getJavaCommand());
        cmd.addAll(buildArguments());
        return cmd;
    }

    private CliCommandBuilder addCliArgument(final CliArgument cliArgument) {
        cliArgs.remove(cliArgument.key);
        if (cliArgument.altKey != null) {
            cliArgs.remove(cliArgument.altKey);
        }
        cliArgs.add(cliArgument.key);
        return this;
    }

    private CliCommandBuilder addCliArgument(final CliArgument cliArgument, final String value) {
        cliArgs.remove(cliArgument.key);
        if (cliArgument.altKey != null) {
            cliArgs.remove(cliArgument.altKey);
        }
        if (value != null && !value.isEmpty()) {
            cliArgs.add(cliArgument.key, value);
        }
        return this;
    }
}
