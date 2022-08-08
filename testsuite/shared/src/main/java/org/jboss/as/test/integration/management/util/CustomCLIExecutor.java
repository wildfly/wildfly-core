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

package org.jboss.as.test.integration.management.util;

import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.Assert;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.logging.Logger;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;

/**
 * CLI executor with custom configuration file jboss-cli.xml used for testing
 * two-way SSL connection
 *
 * @author Filip Bogyai
 */
public class CustomCLIExecutor {

    public static final int MANAGEMENT_NATIVE_PORT = 9999;
    public static final int MANAGEMENT_HTTP_PORT = 9990;
    public static final int MANAGEMENT_HTTPS_PORT = 9993;
    public static final String NATIVE_CONTROLLER = "remote://" + TestSuiteEnvironment.getServerAddress() + ":"
            + MANAGEMENT_NATIVE_PORT;
    public static final String HTTP_CONTROLLER = "remote+http://" + TestSuiteEnvironment.getServerAddress() + ":"
            + MANAGEMENT_HTTP_PORT;
    public static final String HTTPS_CONTROLLER = "remote+https://" + TestSuiteEnvironment.getServerAddress() + ":"
            + MANAGEMENT_HTTPS_PORT;

    private static Logger LOGGER = Logger.getLogger(CustomCLIExecutor.class);

    private static final int CLI_PROC_TIMEOUT = TimeoutUtil.adjust(30000);

    public static String execute(File cliConfigFile, String operation) {

        String defaultController = TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();
        return execute(cliConfigFile, operation, defaultController, true);
    }

    public static String execute(File cliConfigFile, String operation, String controller) {
        return execute(cliConfigFile, operation, controller, true);
    }

    /**
     * Externally executes CLI operation with cliConfigFile settings via defined
     * controller
     *
     * @param cliConfigFile   the the configuration file to use or {@code null} to use the default
     * @param operation       the CLI operation to execute
     * @param controller      the controller to use
     * @param logFailure      {@code true} to log failures otherwise {@code false}
     *
     * @return the stdout response from the process
     */
    public static String execute(File cliConfigFile, String operation, String controller, boolean logFailure) {

        String jbossDist = System.getProperty("jboss.dist");
        if (jbossDist == null) {
            fail("jboss.dist system property is not set");
        }
        final String modulePath = System.getProperty("module.path");
        if (modulePath == null) {
            fail("module.path system property is not set");
        }


        final CliCommandBuilder commandBuilder = CliCommandBuilder.asModularLauncher(jbossDist)
                .setModuleDirs(modulePath.split(Pattern.quote(File.pathSeparator)));

        final List<String> ipv6Args = new ArrayList<>();
        TestSuiteEnvironment.getIpv6Args(ipv6Args);
        if (!ipv6Args.isEmpty()) {
            commandBuilder.addJavaOptions(ipv6Args);
        }

        final Path cliConfigPath;
        if (cliConfigFile != null) {
            cliConfigPath = cliConfigFile.toPath().toAbsolutePath();
            commandBuilder.addJavaOption("-Djboss.cli.config=" + cliConfigFile.getAbsolutePath());
        } else {
            cliConfigPath = Paths.get(jbossDist, "bin", "jboss-cli.xml");
        }
        commandBuilder.addJavaOption("-Djboss.cli.config=" + cliConfigPath);
        commandBuilder.addCliArgument("--timeout="+CLI_PROC_TIMEOUT);
        commandBuilder.addCliArgument("--error-on-interact"); // if server prompt for certificate to accept
        commandBuilder.addCliArgument("--no-color-output");

        // propagate JVM args to the CLI
        String cliJvmArgs = System.getProperty("cli.jvm.args");
        if (cliJvmArgs != null) {
            commandBuilder.addJavaOptions(cliJvmArgs.split("\\s+"));
        }

        // Note that this only allows for a single system property
        if (System.getProperty("cli.args") != null) {
            commandBuilder.addJavaOption(System.getProperty("cli.args"));
        }
        // Set the connection command
        commandBuilder.setConnection(controller);
        commandBuilder.addCliArgument(operation);
        return executeProcess(commandBuilder, logFailure, Collections.emptyMap());
    }

    /**
     * Execute the commands in a new CLI process offline. This is useful when using an {@code embed-server} or
     * {@code embed-host-controller} command is being used.
     *
     * @param commands the commands to execute
     *
     * @return the stdout response from the process
     */
    public static String executeOffline(final CharSequence... commands) {
        return executeOffline(false, Arrays.asList(commands));
    }

    /**
     * Execute the commands in a new CLI process offline. This is useful when using an {@code embed-server} or
     * {@code embed-host-controller} command is being used.
     *
     * @param commands the commands to execute
     *
     * @return the stdout response from the process
     */
    public static String executeOffline(final Iterable<? extends CharSequence> commands) {
        return executeOffline(false, commands);
    }

    /**
     * Execute the commands in a new CLI process offline. This is useful when using an {@code embed-server} or
     * {@code embed-host-controller} command is being used.
     *
     * @param commands   the commands to execute
     * @param logFailure {@code true} to log failures otherwise {@code false}
     *
     * @return the stdout response from the process
     */
    public static String executeOffline(final boolean logFailure, final CharSequence... commands) {
        return executeOffline(logFailure, Arrays.asList(commands));
    }

    /**
     * Execute the commands in a new CLI process offline. This is useful when using an {@code embed-server} or
     * {@code embed-host-controller} command is being used.
     *
     * @param commands   the commands to execute
     * @param logFailure {@code true} to log failures otherwise {@code false}
     *
     * @return the stdout response from the process
     */
    public static String executeOffline(final boolean logFailure, final Iterable<? extends CharSequence> commands) {

        final String jbossDist = System.getProperty("jboss.dist");
        if (jbossDist == null) {
            fail("jboss.dist system property is not set");
        }
        final String modulePath = System.getProperty("module.path");
        if (modulePath == null) {
            fail("module.path system property is not set");
        }

        // Write the commands to a temporary file
        try {
            final Path cliScript = Files.createTempFile("offline-test-cli", ".cli");
            try {
                Files.write(cliScript, commands, StandardCharsets.UTF_8);

                // Build the CLI command
                final CliCommandBuilder commandBuilder = CliCommandBuilder.asModularLauncher(jbossDist)
                        .setModuleDirs(modulePath.split(Pattern.quote(File.pathSeparator)))
                        .addCliArgument("--file=" + cliScript.toAbsolutePath());

                final String cliJvmArgs = System.getProperty("cli.jvm.args");
                if (cliJvmArgs != null) {
                    commandBuilder.addJavaOptions(cliJvmArgs.split("\\s+"));
                }
                return executeProcess(commandBuilder, logFailure, Collections.singletonMap("JBOSS_HOME", jbossDist));
            } finally {
                try {
                    Files.deleteIfExists(cliScript);
                } catch (IOException e) {
                    LOGGER.debugf(e, "Failed to delete CLI script %s", cliScript);
                }
            }
        } catch (IOException e) {
            fail("Failed to execute offline CLI script: " + e.getLocalizedMessage());
            // Should never happen
            throw new RuntimeException("Failed to execute offline CLI script: " + e.getLocalizedMessage(), e);
        }
    }

    private static String executeProcess(final CliCommandBuilder commandBuilder, final boolean logFailure, final Map<String, String> env) {
        Process cliProc = null;
        try {
            cliProc = Launcher.of(commandBuilder)
                    .addEnvironmentVariables(env)
                    .launch();
        } catch (IOException e) {
            fail("Failed to start CLI process: " + e.getLocalizedMessage());
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        CustomCLIExecutor.ConsoleConsumer.start(cliProc.getInputStream(), out);
        CustomCLIExecutor.ConsoleConsumer.start(cliProc.getErrorStream(), err);
        int exitCode = Integer.MIN_VALUE;
        try {
            boolean finished = cliProc.waitFor(CLI_PROC_TIMEOUT, TimeUnit.MILLISECONDS);
            if (finished) {
                exitCode = cliProc.exitValue();
            } else {
                LOGGER.info("A timeout has occurred while invoking CLI command.");
            }
        } catch (InterruptedException e1) {
            Assert.fail("Interupted while waiting. Error Message" + e1.getMessage());
        } finally {
            cliProc.destroyForcibly();
        }

        final String cliOutput = out.toString();

        if (logFailure && exitCode != 0) {
            LOGGER.info("Command's output: '" + cliOutput + "'");
            if (err.size() > 0) {
                LOGGER.info("Command's error log: '" + err.toString() + "'");
            } else {
                LOGGER.info("No output data for the command.");
            }
        }
        return exitCode + ": " + cliOutput;
    }

    private static class ConsoleConsumer implements Runnable {

        static void start(final InputStream in, final OutputStream target) {
            final Thread t = new Thread(new ConsoleConsumer(in, target));
            t.start();
        }

        private final InputStream in;
        private final OutputStream target;

        private ConsoleConsumer(final InputStream in, final OutputStream target) {
            this.in = in;
            this.target = target;
        }

        @Override
        public void run() {
            final byte[] b = new byte[32];
            int len;
            try {
                while ((len = in.read(b)) != -1) {
                    target.write(b, 0, len);
                }
            } catch (IOException ignore) {
            }
        }
    }
}