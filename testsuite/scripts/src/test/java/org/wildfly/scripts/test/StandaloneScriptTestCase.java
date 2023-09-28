/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import jakarta.json.JsonObject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.common.test.ServerConfigurator;
import org.wildfly.common.test.ServerHelper;
import org.wildfly.common.test.LoggingAgent;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Parameterized.class)
public class StandaloneScriptTestCase extends ScriptTestCase {
    private static final String STANDALONE_BASE_NAME = "standalone";

    @Parameter
    public Map<String, String> env;

    private static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = ServerHelper::isStandaloneRunning;

    public StandaloneScriptTestCase() {
        super(STANDALONE_BASE_NAME);
    }

    @Parameters
    public static Collection<Object> data() {
        final Collection<Object> result = new ArrayList<>(2);
        result.add(Collections.emptyMap());
        result.add(Collections.singletonMap("GC_LOG", "true"));
        result.add(Collections.singletonMap("MODULE_OPTS", "-javaagent:logging-agent-tests.jar=" + LoggingAgent.DEBUG_ARG));
        return result;
    }

    @Test
    public void testBatchScriptJavaOptsToEscape() throws Exception {
        Assume.assumeTrue(TestSuiteEnvironment.isWindows());
        final String variableToEscape = "-Dhttp.nonProxyHosts=localhost|127.0.0.1|10.10.10.*";
        ServerConfigurator.appendJavaOpts(ServerHelper.JBOSS_HOME, "standalone", variableToEscape);
        try (ScriptProcess script = new ScriptProcess(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, Shell.BATCH, ServerHelper.TIMEOUT)) {
            testScript(script);
        }
        ServerConfigurator.removeJavaOpts(ServerHelper.JBOSS_HOME, "standalone", variableToEscape);
    }

    @Test
    public void testBatchScriptJavaOptsEscaped() throws Exception {
        Assume.assumeTrue(TestSuiteEnvironment.isWindows());
        final String escapedVariable = "-Dhttp.nonProxyHosts=localhost^|127.0.0.1^|10.10.10.*";
        ServerConfigurator.appendJavaOpts(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, escapedVariable);
        try (ScriptProcess script = new ScriptProcess(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, Shell.BATCH, ServerHelper.TIMEOUT)) {
            testScript(script);
        }
        ServerConfigurator.removeJavaOpts(ServerHelper.JBOSS_HOME, STANDALONE_BASE_NAME, escapedVariable);
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        // This is an odd case for Windows where with the -Xlog:gc* where the file argument does not seem to work with
        // a directory that contains a space. It seems similar to https://bugs.openjdk.java.net/browse/JDK-8215398
        // however the workaround is to do something like file=`\`"C:\wildfly\standalong\logs\gc.log`\`". This does not
        // seem to work when a directory has a space. An error indicating the trailing quote cannot be found. Removing
        // the `\ parts and just keeping quotes ends in the error shown in JDK-8215398.
        Assume.assumeFalse(TestSuiteEnvironment.isWindows() && env.containsKey("GC_LOG") && script.getScript().toString().contains(" "));
        script.start(STANDALONE_CHECK, env, ServerHelper.DEFAULT_SERVER_JAVA_OPTS);
        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        if (env.containsKey("MODULE_OPTS")) {
            final List<JsonObject> lines = ServerHelper.readLogFileFromModel("json.log");
            Assert.assertEquals("Expected 2 lines found " + lines.size(), 2, lines.size());
            JsonObject msg = lines.get(0);
            Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
            Assert.assertEquals("FINE", msg.getString("level"));
            msg = lines.get(1);
            Assert.assertEquals(LoggingAgent.MSG, msg.getString("message"));
            Assert.assertEquals("INFO", msg.getString("level"));
        }

        // Shutdown the server
        @SuppressWarnings("Convert2Lambda")
        final Callable<ModelNode> callable = new Callable<ModelNode>() {
            @Override
            public ModelNode call() throws Exception {
                try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                    return executeOperation(client, Operations.createOperation("shutdown"));
                }
            }
        };
        execute(callable);
        validateProcess(script);

        // Check if the gc.log exists assuming we have the GC_LOG environment variable added
        if ("true".equals(env.get("GC_LOG"))) {
            final Path logDir = script.getContainerHome().resolve("standalone").resolve("log");
            Assert.assertTrue(Files.exists(logDir));
            final String fileName = "gc.log";
            Assert.assertTrue(String.format("Missing %s file in %s", fileName, logDir), Files.exists(logDir.resolve(fileName)));
        }
    }
}
