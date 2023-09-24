/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.launcher.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jakarta.json.JsonObject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.common.test.LoggingAgent;
import org.wildfly.common.test.ServerConfigurator;
import org.wildfly.common.test.ServerHelper;
import org.wildfly.core.launcher.DomainCommandBuilder;
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

    @BeforeClass
    public static void setup() throws Exception {
        ServerConfigurator.configure();
    }

    @Test
    public void testStandaloneWithAgent() throws Exception {
        final StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(ServerHelper.JBOSS_HOME)
                .addJavaOptions(ServerHelper.DEFAULT_SERVER_JAVA_OPTS)
                .setUseSecurityManager(parseProperty("security.manager"))
                // Add the test logging agent to the jboss-modules arguments
                .addModuleOption("-javaagent:" + ServerHelper.JBOSS_HOME.resolve("logging-agent-tests.jar") + "=" + LoggingAgent.DEBUG_ARG);

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
            ServerHelper.waitForStandalone(process, LauncherTestCase::readStdout);
            final List<JsonObject> lines = ServerHelper.readLogFileFromModel("json.log");
            Assert.assertEquals("Expected 2 lines found " + lines.size(), 2, lines.size());
            JsonObject msg = lines.get(0);
            Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
            Assert.assertEquals("FINE", msg.getString("level"));
            msg = lines.get(1);
            Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
            Assert.assertEquals("INFO", msg.getString("level"));
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                ServerHelper.shutdownStandalone(client);
            }
            process.waitFor(ServerHelper.TIMEOUT, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                Assert.fail(readStdout());
            }
        } finally {
            ProcessHelper.destroyProcess(process);
        }
    }

    @Test
    public void testDomainServerWithAgent() throws Exception {
        final DomainCommandBuilder builder = DomainCommandBuilder.of(ServerHelper.JBOSS_HOME)
                .addHostControllerJavaOptions(ServerHelper.DEFAULT_SERVER_JAVA_OPTS)
                .setUseSecurityManager(parseProperty("security.manager"));

        final String localRepo = System.getProperty("maven.repo.local");
        if (localRepo != null) {
            builder.addHostControllerJavaOption("-Dmaven.repo.local=" + localRepo)
                    .addProcessControllerJavaOption("-Dmaven.repo.local=" + localRepo);
        }

        Process process = null;
        try {
            process = Launcher.of(builder)
                    .setRedirectErrorStream(true)
                    .redirectOutput(STDOUT_PATH)
                    .launch();
            ServerHelper.waitForDomain(process, LauncherTestCase::readStdout);

            // Start server-three, configure it, then stop it
            try (DomainClient client = DomainClient.Factory.create(TestSuiteEnvironment.getModelControllerClient())) {
                final String hostName = ServerHelper.determineHostName(client);


                client.startServer(hostName, "server-three");
                ServerHelper.waitForManagedServer(client, "server-three", LauncherTestCase::readStdout);
                final List<JsonObject> lines = ServerHelper.readLogFileFromModel("json.log", "host", hostName, "server", "server-three");
                Assert.assertEquals("Expected 2 lines found " + lines.size(), 2, lines.size());
                JsonObject msg = lines.get(0);
                Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
                Assert.assertEquals("FINE", msg.getString("level"));
                msg = lines.get(1);
                Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
                Assert.assertEquals("INFO", msg.getString("level"));

                ServerHelper.shutdownDomain(client);
            }
            process.waitFor(ServerHelper.TIMEOUT, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                Assert.fail(readStdout());
            }
        } finally {
            ProcessHelper.destroyProcess(process);
        }
    }

    private static String readStdout() {
        final StringBuilder error = new StringBuilder(10240)
                .append("Failed to boot the server: ").append(System.lineSeparator());
        try {
            for (String line : Files.readAllLines(STDOUT_PATH)) {
                error.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return error.toString();
    }

    private static boolean parseProperty(@SuppressWarnings("SameParameterValue") final String key) {
        final String value = System.getProperty(key);
        return value != null && (value.isEmpty() || Boolean.parseBoolean(value));
    }
}
