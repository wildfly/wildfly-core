/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.wildfly.launcher.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.json.JsonObject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.test.LoggingAgent;
import org.wildfly.common.test.ServerHelper;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * This tests launching WildFly with JBoss Modules as an agent. For details see
 * <a href="https://issues.jboss.org/browse/WFCORE-4674">WFCORE-4677</a>.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LauncherTestCase {
    private static final Path STDOUT_PATH = Paths.get(TestSuiteEnvironment.getTmpDir(), "stdout.txt");

    @Test
    public void testStandaloneWithAgent() throws Exception {
        final StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(ServerHelper.JBOSS_HOME)
                .addJavaOptions(ServerHelper.DEFAULT_SERVER_JAVA_OPTS)
                .setUseSecurityManager(parseProperty("security.manager"))
                // TODO (jrp) maybe test with agent arguments too, however this won't work until the 1.10.1.Final jboss-modules upgrade
                // Add the test logging agent to the jboss-modules arguments
                .addModuleOption("-javaagent:" + ServerHelper.JBOSS_HOME.resolve("logging-agent-tests.jar").toString());

        final String localRepo = System.getProperty("maven.repo.local");
        if (localRepo != null) {
            builder.addJavaOption("-Dmaven.repo.local=" + localRepo);
        }

        Process process = null;
        try {
            process = Launcher.of(builder)
                    .setRedirectErrorStream(true)
                    .redirectOutput(STDOUT_PATH)
                    .launch();
            waitForStandalone(process);
            final List<JsonObject> lines = ServerHelper.readLogFileFromModel("json.log");
            Assert.assertEquals("Expected 1 line found " + lines.size(), 1, lines.size());
            final JsonObject msg = lines.get(0);
            Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
            shutdownStandalone();
            process.waitFor();
            if (process.exitValue() != 0) {
                Assert.fail(readStdout());
            }
        } finally {
            ProcessHelper.destroyProcess(process);
        }
    }

    /**
     * Waits the given amount of time in seconds for a standalone server to start.
     * <p>
     * If the {@code process} is not {@code null} and a timeout occurs the process will be
     * {@linkplain Process#destroy() destroyed}.
     * </p>
     *
     * @param process the Java process can be {@code null} if no process is available
     *
     * @throws InterruptedException if interrupted while waiting for the server to start
     * @throws RuntimeException     if the process has died
     */
    private static void waitForStandalone(final Process process)
            throws InterruptedException, IOException {
        try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
            long timeout = ServerHelper.TIMEOUT * 1000;
            final long sleep = 100L;
            while (timeout > 0) {
                long before = System.currentTimeMillis();
                if (ServerHelper.isStandaloneRunning(client))
                    break;
                timeout -= (System.currentTimeMillis() - before);
                if (process != null && !process.isAlive()) {
                    Assert.fail(readStdout());
                }
                TimeUnit.MILLISECONDS.sleep(sleep);
                timeout -= sleep;
            }
            if (timeout <= 0) {
                if (process != null) {
                    process.destroy();
                }
                Assert.fail(String.format("The server did not start within %s seconds: %s", ServerHelper.TIMEOUT, readStdout()));
            }
        }
    }

    /**
     * Shuts down a standalone server.
     *
     * @throws IOException if an error occurs communicating with the server
     */
    @SuppressWarnings("MagicNumber")
    private static void shutdownStandalone() throws IOException {
        try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
            final ModelNode op = Operations.createOperation("shutdown");
            op.get("timeout").set(0);
            final ModelNode response = client.execute(op);
            if (Operations.isSuccessfulOutcome(response)) {
                while (true) {
                    if (ServerHelper.isStandaloneRunning(client)) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(20L);
                        } catch (InterruptedException ignore) {
                        }
                    } else {
                        break;
                    }
                }
            } else {
                Assert.fail("Failed to shutdown server: " + Operations.getFailureDescription(response).asString());
            }
        }
    }

    private static String readStdout() throws IOException {
        final StringBuilder error = new StringBuilder(10240)
                .append("Failed to boot the server: ").append(System.lineSeparator());
        for (String line : Files.readAllLines(STDOUT_PATH)) {
            error.append(line).append(System.lineSeparator());
        }
        return error.toString();
    }

    private static boolean parseProperty(@SuppressWarnings("SameParameterValue") final String key) {
        final String value = System.getProperty(key);
        return value != null && (value.isEmpty() || Boolean.parseBoolean(value));
    }
}
