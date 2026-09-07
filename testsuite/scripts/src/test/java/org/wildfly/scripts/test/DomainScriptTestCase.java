/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.wildfly.common.test.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Parameterized.class)
public class DomainScriptTestCase extends ScriptTestCase {

    private static final Function<ModelControllerClient, Boolean> HOST_CONTROLLER_CHECK = ServerHelper::isDomainRunning;

    @Parameterized.Parameter
    public Map<String, String> env;
    public DomainScriptTestCase() {
        super("domain");
    }

    @Parameterized.Parameters
    public static Collection<Object> data() {
        return List.of(Map.of(), Map.of("SECMGR", SECMGR_VALUE));
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        script.start(HOST_CONTROLLER_CHECK, env, ServerHelper.DEFAULT_SERVER_JAVA_OPTS);

        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        final var stdout = script.getStdoutAsString();
        if (supportsEnhancedSecurityManager() && env.containsKey("SECMGR")) {
            Assert.assertTrue("Expected to find -Djava.security.manager=allow in the JVM parameters.", stdout.contains("-Djava.security.manager=allow"));
        } else {
            Assert.assertFalse("Did not expect to find -Djava.security.manager=allow in the JVM parameters.", stdout.contains("-Djava.security.manager=allow"));
        }

        // Shutdown the server
        @SuppressWarnings("Convert2Lambda")
        final Callable<ModelNode> callable = new Callable<ModelNode>() {
            @Override
            public ModelNode call() throws Exception {
                try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                    return executeOperation(client, Operations.createOperation("shutdown", ServerHelper.determineHostAddress(client)));
                }
            }
        };
        execute(callable);
        validateProcess(script);
    }

}
