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

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.BeforeClass;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneScriptTestCase extends ScriptTestCase {

    private static final Function<ModelControllerClient, Boolean> STANDALONE_CHECK = new Function<ModelControllerClient, Boolean>() {
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

    public StandaloneScriptTestCase() {
        super("standalone", STANDALONE_CHECK);
    }

    @BeforeClass
    public static void updateConf() throws Exception {
        appendConf("standalone", "JAVA_OPTS");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        script.start(DEFAULT_SERVER_JAVA_OPTS);
        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

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
    }
}
