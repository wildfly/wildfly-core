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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.BeforeClass;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainScriptTestCase extends ScriptTestCase {

    @SuppressWarnings("Convert2Lambda")
    private static final Function<ModelControllerClient, Boolean> HOST_CONTROLLER_CHECK = new Function<ModelControllerClient, Boolean>() {
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

    public DomainScriptTestCase() {
        super("domain", HOST_CONTROLLER_CHECK);
    }

    @BeforeClass
    public static void updateConf() throws Exception {
        // Likely not needed in the PC, but also won't hurt
        appendConf("domain", "PROCESS_CONTROLLER_JAVA_OPTS");
        appendConf("domain", "HOST_CONTROLLER_JAVA_OPTS");
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
                    return executeOperation(client, Operations.createOperation("shutdown", determineHostAddress(client)));
                }
            }
        };
        execute(callable);
        validateProcess(script);
    }

    private static ModelNode determineHostAddress(final ModelControllerClient client) throws IOException {
        final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "local-host-name");
        ModelNode response = client.execute(op);
        if (Operations.isSuccessfulOutcome(response)) {
            return Operations.createAddress("host", Operations.readResult(response).asString());
        }
        throw new IOException("Failed to determine host name: " + Operations.readResult(response).asString());
    }
}
