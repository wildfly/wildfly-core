/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.wildfly.scripts.test;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class ScriptTestCase {

    private static final ModelNode EMPTY_ADDRESS = new ModelNode().addEmptyList();
    static final String[] DEFAULT_SERVER_JAVA_OPTS = {
            "-Djboss.management.http.port=" + TestSuiteEnvironment.getServerPort(),
            "-Djboss.bind.address.management=" + TestSuiteEnvironment.getServerAddress(),
    };

    static final String[] WIN_CMD_PREFIX = {
            "cmd",
            "/C"
    };

    static final String[] POWER_SHELL_PREFIX = {
            "cmd",
            "/C",
            "powershell",
            "-NonInteractive",
            "-File"
    };

    static {
        EMPTY_ADDRESS.protect();
    }

    private ExecutorService service;

    @Before
    public void startProcess() {
        service = Executors.newCachedThreadPool();
    }

    @After
    public void destroyProcess() {
        service.shutdownNow();
    }

    <T> Future<T> execute(final Callable<T> callable) {
        return service.submit(callable);
    }

    void validateProcess(final ScriptProcess script) throws InterruptedException, IOException {
        if (script.waitFor(TimeoutUtil.adjust(10), TimeUnit.SECONDS)) {
            // The script has exited, validate the exit code is valid
            final int exitValue = script.exitValue();
            if (exitValue != 0) {
                Assert.fail(getErrorMessage(script, String.format("Expected an exit value 0f 0 got %d", exitValue)));
            }
        } else {
            Assert.fail(getErrorMessage(script, "The script process did not exit within 10 seconds."));
        }
    }

    String getErrorMessage(final ScriptProcess script, final String msg) throws IOException {
        final StringBuilder errorMessage = new StringBuilder(msg)
                .append(System.lineSeparator())
                .append("Command Executed: ")
                .append(script.getLastExecutedCmd())
                .append(System.lineSeparator())
                .append("Output:");
        final List<String> stdoutLines = Files.readAllLines(script.getStdout());
        for (String line : stdoutLines) {
            errorMessage.append(System.lineSeparator())
                    .append(line);
        }

        return errorMessage.toString();
    }

    Path getExecutable(final String exe) {
        final Path fullExe = Environment.JBOSS_HOME.resolve("bin").resolve(exe).toAbsolutePath();
        Assert.assertTrue(String.format("Path %s does not exist", fullExe), Files.exists(fullExe));
        return fullExe;
    }

    static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = new Function<ModelControllerClient, Boolean>() {
        private final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state");

        @Override
        public Boolean apply(final ModelControllerClient client) {

            try {
                final ModelNode result = client.execute(op);
                if (Operations.isSuccessfulOutcome(result) &&
                        ClientConstants.CONTROLLER_PROCESS_STATE_RUNNING.equals(Operations.readResult(result).asString())) {
                    return true;
                }
            } catch (IllegalStateException | IOException ignore) {
            }
            return false;
        }
    };

    static final Function<ModelControllerClient, Boolean> HOST_CONTROLLER_CHECK = new Function<ModelControllerClient, Boolean>() {
        @Override
        public Boolean apply(final ModelControllerClient client) {
            final DomainClient domainClient = (client instanceof DomainClient ? (DomainClient) client : DomainClient.Factory.create(client));
            try {
                // Check for admin-only
                final ModelNode hostAddress = determineHostAddress(domainClient);
                final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create()
                        .addStep(Operations.createReadAttributeOperation(hostAddress, "running-mode"))
                        .addStep(Operations.createReadAttributeOperation(hostAddress, "host-state"));
                ModelNode response = domainClient.execute(builder.build());
                if (Operations.isSuccessfulOutcome(response)) {
                    response = Operations.readResult(response);
                    if ("ADMIN_ONLY".equals(Operations.readResult(response.get("step-1")).asString())) {
                        if (Operations.isSuccessfulOutcome(response.get("step-2"))) {
                            final String state = Operations.readResult(response).asString();
                            return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                                    && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
                        }
                    }
                }
                final Map<ServerIdentity, ServerStatus> servers = new HashMap<>();
                final Map<ServerIdentity, ServerStatus> statuses = domainClient.getServerStatuses();
                for (ServerIdentity id : statuses.keySet()) {
                    final ServerStatus status = statuses.get(id);
                    switch (status) {
                        case DISABLED:
                        case STARTED: {
                            servers.put(id, status);
                            break;
                        }
                    }
                }
                return statuses.size() == servers.size();
            } catch (IllegalStateException | IOException ignore) {
            }
            return false;
        }
    };

    static boolean isShellSupported(final String name, final String... args) throws IOException, InterruptedException {
        final List<String> cmd = new ArrayList<>();
        cmd.add(name);
        if (args != null && args.length > 0) {
            cmd.addAll(Arrays.asList(args));
        }
        final Path stdout = Files.createTempFile(name + "-supported", ".log");
        final ProcessBuilder builder = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(stdout.toFile());
        Process process = null;
        try {
            process = builder.start();
            if (!process.waitFor(Environment.TIMEOUT, TimeUnit.SECONDS)) {
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(stdout);
        }
    }

    static void appendConf(final String baseName, final String envName) throws IOException {
        // Add the local maven repository to the conf file
        final String localRepo = System.getProperty("maven.repo.local");
        if (localRepo != null) {
            final Path binDir = Environment.JBOSS_HOME.resolve("bin");
            if (Environment.isWindows()) {
                // Batch conf
                Path conf = binDir.resolve(baseName + ".conf.bat");
                try (BufferedWriter writer = Files.newBufferedWriter(conf, StandardOpenOption.APPEND)) {
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
                try (BufferedWriter writer = Files.newBufferedWriter(conf, StandardOpenOption.APPEND)) {
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

    static ModelNode executeOperation(final ModelControllerClient client, final ModelNode op) throws IOException {
        return executeOperation(client, OperationBuilder.create(op).build());
    }

    private static ModelNode executeOperation(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Failed to execute op: %s%nFailure Description: %s", op, Operations.getFailureDescription(result)));
        }
        return Operations.readResult(result);
    }

    static ModelNode determineHostAddress(final ModelControllerClient client) throws IOException {
        final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "local-host-name");
        ModelNode response = client.execute(op);
        if (Operations.isSuccessfulOutcome(response)) {
            return Operations.createAddress("host", Operations.readResult(response).asString());
        }
        throw new IOException("Failed to determine host name: " + Operations.readResult(response).asString());
    }
}
