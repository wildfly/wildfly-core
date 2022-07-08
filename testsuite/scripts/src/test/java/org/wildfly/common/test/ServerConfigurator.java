/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.common.test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerConfigurator {
    private static final AtomicBoolean CONFIGURED = new AtomicBoolean(false);
    public static final Set<Path> PATHS = new LinkedHashSet<>(16);

    public static void configure() throws IOException, InterruptedException {
        if (CONFIGURED.compareAndSet(false, true)) {
            configureStandalone();
            configureDomain();

            // Always update the JBOSS_HOME/bin/*.conf.* files
            try {
                appendConf(ServerHelper.JBOSS_HOME, "domain", "PROCESS_CONTROLLER_JAVA_OPTS");
                appendConf(ServerHelper.JBOSS_HOME, "domain", "HOST_CONTROLLER_JAVA_OPTS");
                appendConf(ServerHelper.JBOSS_HOME, "standalone", "JAVA_OPTS");
                if (TestSuiteEnvironment.isWindows()) {
                    appendConf(ServerHelper.JBOSS_HOME, "common", "JAVA_OPTS");
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to update the script config files.", e);
            }

            // Always add the default path
            PATHS.add(ServerHelper.JBOSS_HOME);

            final String serverName = System.getProperty("server.name");

            // Create special characters in paths to test with assuming the -Dserver.name was not used
            if (serverName == null || serverName.isEmpty()) {
                PATHS.add(copy("wildfly core"));
                PATHS.add(copy("wildfly (core)"));
            }
        }
    }

    private static void configureStandalone() throws InterruptedException, IOException {
        final Path stdout = Paths.get(TestSuiteEnvironment.getTmpDir(), "config-standalone-stdout.txt");
        final StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(ServerHelper.JBOSS_HOME)
                .addJavaOptions(ServerHelper.DEFAULT_SERVER_JAVA_OPTS);

        final String localRepo = System.getProperty("maven.repo.local");
        if (localRepo != null) {
            builder.addJavaOption("-Dmaven.repo.local=" + localRepo);
        }

        Process process = null;
        try {
            process = Launcher.of(builder)
                    .setRedirectErrorStream(true)
                    .redirectOutput(stdout)
                    .launch();
            ServerHelper.waitForStandalone(process, () -> readStdout(stdout));

            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                final CompositeOperationBuilder opBuilder = CompositeOperationBuilder.create();
                ModelNode address = Operations.createAddress("subsystem", "logging", "custom-formatter", "json");
                ModelNode op = Operations.createAddOperation(address);
                op.get("class").set("org.jboss.logmanager.formatters.JsonFormatter");
                op.get("module").set("org.jboss.logmanager");
                final ModelNode properties = op.get("properties").setEmptyObject();
                properties.get("exceptionOutputType").set("FORMATTED");
                properties.get("prettyPrint").set(false);
                opBuilder.addStep(op);

                address = Operations.createAddress("subsystem", "logging", "file-handler", "json");
                op = Operations.createAddOperation(address);
                op.get("append").set(false);
                op.get("level").set("DEBUG");
                op.get("autoflush").set(true);
                op.get("named-formatter").set("json");
                final ModelNode file = op.get("file");
                file.get("relative-to").set("jboss.server.log.dir");
                file.get("path").set("json.log");
                opBuilder.addStep(op);

                address = Operations.createAddress("subsystem", "logging", "logger", "org.wildfly.common.test.LoggingAgent");
                op = Operations.createAddOperation(address);
                op.get("handlers").setEmptyList().add("json");
                op.get("level").set("DEBUG");
                opBuilder.addStep(op);

                executeOperation(client, opBuilder.build());
                ServerHelper.shutdownStandalone(client);
            }

            if (!process.waitFor(ServerHelper.TIMEOUT, TimeUnit.SECONDS)) {
                Assert.fail(readStdout(stdout));
            }

            if (process.exitValue() != 0) {
                Assert.fail(readStdout(stdout));
            }

        } finally {
            ProcessHelper.destroyProcess(process);
        }
    }

    private static void configureDomain() throws IOException, InterruptedException {
        final Path stdout = Paths.get(TestSuiteEnvironment.getTmpDir(), "config-domain-stdout.txt");
        final DomainCommandBuilder builder = DomainCommandBuilder.of(ServerHelper.JBOSS_HOME)
                .addHostControllerJavaOptions(ServerHelper.DEFAULT_SERVER_JAVA_OPTS);

        final String localRepo = System.getProperty("maven.repo.local");
        if (localRepo != null) {
            builder.addHostControllerJavaOption("-Dmaven.repo.local=" + localRepo)
                    .addProcessControllerJavaOption("-Dmaven.repo.local=" + localRepo);
        }

        Process process = null;
        try {
            process = Launcher.of(builder)
                    .setRedirectErrorStream(true)
                    .redirectOutput(stdout)
                    .launch();
            ServerHelper.waitForDomain(process, () -> readStdout(stdout));

            // Start server-three, configure it, then stop it
            try (DomainClient client = DomainClient.Factory.create(TestSuiteEnvironment.getModelControllerClient())) {
                final String hostName = ServerHelper.determineHostName(client);
                // Configure server-three to launch with an agent
                final CompositeOperationBuilder opBuilder = CompositeOperationBuilder.create();
                ModelNode address = Operations.createAddress("profile", "default");
                ModelNode op = Operations.createOperation("clone", address);
                op.get("to-profile").set("test");
                opBuilder.addStep(op);

                address = Operations.createAddress("server-group", "other-server-group");
                op = Operations.createWriteAttributeOperation(address, "profile", "test");
                opBuilder.addStep(op);

                address = Operations.createAddress("host", hostName, "server-config", "server-three", "jvm", "default");
                op = Operations.createAddOperation(address);
                op.get("module-options").setEmptyList().add("-javaagent:" + ServerHelper.JBOSS_HOME.resolve("logging-agent-tests.jar") + "=" + LoggingAgent.DEBUG_ARG);
                opBuilder.addStep(op);

                address = Operations.createAddress("profile", "test", "subsystem", "logging", "custom-formatter", "json");
                op = Operations.createAddOperation(address);
                op.get("class").set("org.jboss.logmanager.formatters.JsonFormatter");
                op.get("module").set("org.jboss.logmanager");
                final ModelNode properties = op.get("properties").setEmptyObject();
                properties.get("exceptionOutputType").set("FORMATTED");
                properties.get("prettyPrint").set(false);
                opBuilder.addStep(op);

                address = Operations.createAddress("profile", "test", "subsystem", "logging", "file-handler", "json");
                op = Operations.createAddOperation(address);
                op.get("append").set(false);
                op.get("level").set("DEBUG");
                op.get("autoflush").set(true);
                op.get("named-formatter").set("json");
                final ModelNode file = op.get("file");
                file.get("relative-to").set("jboss.server.log.dir");
                file.get("path").set("json.log");
                opBuilder.addStep(op);

                address = Operations.createAddress("profile", "test", "subsystem", "logging", "logger", "org.wildfly.common.test.LoggingAgent");
                op = Operations.createAddOperation(address);
                op.get("handlers").setEmptyList().add("json");
                op.get("level").set("DEBUG");
                opBuilder.addStep(op);

                executeOperation(client, opBuilder.build());


                client.startServer(hostName, "server-three");
                ServerHelper.waitForManagedServer(client, "server-three", () -> readStdout(stdout));
                ServerHelper.shutdownDomain(client);
            }
            if (!process.waitFor(ServerHelper.TIMEOUT, TimeUnit.SECONDS)) {
                Assert.fail(readStdout(stdout));
            }

            if (process.exitValue() != 0) {
                Assert.fail(readStdout(stdout));
            }
        } finally {
            ProcessHelper.destroyProcess(process);
        }
    }

    private static void executeOperation(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).asString());
        }
    }

    private static String readStdout(final Path stdout) {
        final StringBuilder error = new StringBuilder(10240)
                .append("Failed to boot the server: ").append(System.lineSeparator());
        try {
            for (String line : Files.readAllLines(stdout)) {
                error.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return error.toString();
    }

    private static void appendConf(final Path containerHome, final String baseName, final String envName) throws IOException {
        // Add the local maven repository to the conf file
        final String localRepo = System.getProperty("maven.repo.local");
        if (localRepo != null) {
            final Path binDir = containerHome.resolve("bin");
            if (TestSuiteEnvironment.isWindows()) {
                // Batch conf
                Path conf = binDir.resolve(baseName + ".conf.bat");
                OpenOption[] options;
                if (Files.notExists(conf)) {
                    options = new OpenOption[] {StandardOpenOption.CREATE_NEW};
                } else {
                    options = new OpenOption[] {StandardOpenOption.APPEND};
                }
                try (BufferedWriter writer = Files.newBufferedWriter(conf, options)) {
                    writer.newLine();
                    writer.write("set \"");
                    writer.write(envName);
                    writer.write("=-Dmaven.repo.local=");
                    writer.write(localRepo);
                    writer.write(" %");
                    writer.write(envName);
                    writer.write("%\"");
                    writer.newLine();
                }
                // Powershell conf
                conf = binDir.resolve(baseName + ".conf.ps1");
                if (Files.notExists(conf)) {
                    options = new OpenOption[] {StandardOpenOption.CREATE_NEW};
                } else {
                    options = new OpenOption[] {StandardOpenOption.APPEND};
                }
                try (BufferedWriter writer = Files.newBufferedWriter(conf, options)) {
                    writer.newLine();
                    writer.write('$');
                    writer.write(envName);
                    writer.write(" += '-Dmaven.repo.local=");
                    writer.write(localRepo);
                    writer.write("'");
                    writer.newLine();
                }
            } else {
                final Path conf = binDir.resolve(baseName + ".conf");
                try (BufferedWriter writer = Files.newBufferedWriter(conf, StandardOpenOption.APPEND)) {
                    writer.newLine();
                    writer.write(envName);
                    writer.write("=\"-Dmaven.repo.local=");
                    writer.write(localRepo);
                    writer.write(" $");
                    writer.write(envName);
                    writer.write('"');
                    writer.newLine();
                }
            }
        }
    }

    private static Path copy(final String targetName) {
        final Path source = ServerHelper.JBOSS_HOME;
        try {
            final Path target = source.getParent().resolve(targetName);
            deleteDirectory(target);
            copyDirectory(source, target);
            // Likely not needed in the PC, but also won't hurt
            appendConf(target, "domain", "PROCESS_CONTROLLER_JAVA_OPTS");
            appendConf(target, "domain", "HOST_CONTROLLER_JAVA_OPTS");
            appendConf(target, "standalone", "JAVA_OPTS");
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyDirectory(final Path source, final Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                final Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                final Path targetDir = target.resolve(source.relativize(dir));
                Files.copy(dir, targetDir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectory(final Path dir) throws IOException {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
