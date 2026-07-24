/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.wildfly.common.test.ServerHelper;

public class StandaloneDebugScriptTestCase extends ScriptTestCase {
    private static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = ServerHelper::isStandaloneRunning;

    public StandaloneDebugScriptTestCase() {
        super("standalone");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        final List<String> args = new ArrayList<>(Arrays.asList(ServerHelper.DEFAULT_SERVER_JAVA_OPTS));
        args.add("--debug");
        script.start(STANDALONE_CHECK, MAVEN_JAVA_OPTS, args.toArray(String[]::new));

        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        final var stdout = script.getStdoutAsString();
        Assert.assertFalse("Found -Xmx16m in JVM parameters, for a server started with " + script.getLastExecutedCmd() + "\nThe server output was:\n" + stdout, stdout.contains("-Xmx16m"));

        final Callable<ModelNode> callable = new Callable<>() {
            @Override
            public ModelNode call() throws Exception {
                try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                    return executeOperation(client, Operations.createOperation("shutdown"));
                }
            }
        };
        execute(callable);
        validateProcess(script);
    }
}
