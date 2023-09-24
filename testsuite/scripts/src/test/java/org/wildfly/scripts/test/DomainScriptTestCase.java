/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.wildfly.common.test.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainScriptTestCase extends ScriptTestCase {

    private static final Function<ModelControllerClient, Boolean> HOST_CONTROLLER_CHECK = ServerHelper::isDomainRunning;

    public DomainScriptTestCase() {
        super("domain");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        script.start(HOST_CONTROLLER_CHECK, ServerHelper.DEFAULT_SERVER_JAVA_OPTS);

        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

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
