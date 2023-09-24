/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.embedded;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.embedded.EmbeddedManagedProcess;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.HostController;
import org.wildfly.core.embedded.StandaloneServer;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class EmbeddedTestCase extends AbstractTestCase {

    @Test
    public void testStartAndStopStandalone() throws Exception {
        final StandaloneServer server = EmbeddedProcessFactory.createStandaloneServer(Environment.createConfigBuilder().build());

        try {
            server.start();
            testRunning(server, STANDALONE_CHECK);
        } finally {
            server.stop();
        }

        try {
            server.start();
            testRunning(server, STANDALONE_CHECK);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testStartAndStopHostController() throws Exception {
        final HostController server = EmbeddedProcessFactory.createHostController(Environment.createConfigBuilder().build());

        try {
            server.start();
            testRunning(server, HOST_CONTROLLER_CHECK);
        } finally {
            server.stop();
        }

        try {
            server.start();
            testRunning(server, HOST_CONTROLLER_CHECK);
        } finally {
            server.stop();
        }
    }

    private void testRunning(final EmbeddedManagedProcess server, final Function<EmbeddedManagedProcess, Boolean> check)
            throws IOException, TimeoutException, InterruptedException {
        waitFor(server, check);
        // Ensure the server has started
        try (ModelControllerClient client = server.getModelControllerClient()) {
            final ModelNode op = Operations.createReadAttributeOperation(AbstractTestCase.EMPTY_ADDRESS, "launch-type");
            final ModelNode result = executeOperation(client, op);
            Assert.assertEquals("EMBEDDED", result.asString());
        }
    }
}
