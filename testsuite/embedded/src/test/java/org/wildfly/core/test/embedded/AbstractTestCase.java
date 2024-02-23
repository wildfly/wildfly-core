/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.embedded;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.wildfly.core.embedded.EmbeddedManagedProcess;
import org.wildfly.core.embedded.EmbeddedProcessStartException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("Convert2Lambda")
public class AbstractTestCase {

    static final ModelNode EMPTY_ADDRESS = new ModelNode().addEmptyList();

    static {
        EMPTY_ADDRESS.protect();
    }

    private ExecutorService service;

    @Before
    public void configureExecutor() {
        service = Executors.newSingleThreadExecutor();
    }

    @After
    public void shutdownExecutor() {
        service.shutdownNow();
    }

    protected void startAndWaitFor(final EmbeddedManagedProcess server, final Function<EmbeddedManagedProcess, Boolean> check) throws TimeoutException, InterruptedException, EmbeddedProcessStartException {
        server.start();
        waitFor(server, check);
    }

    protected void waitFor(final EmbeddedManagedProcess server, final Function<EmbeddedManagedProcess, Boolean> check) throws TimeoutException, InterruptedException {
        final Callable<Boolean> callable = new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException {
                long timeout = Environment.TIMEOUT * 1000;
                final long sleep = 100L;
                while (timeout > 0) {
                    long before = System.currentTimeMillis();
                    if (check.apply(server)) {
                        return true;
                    }
                    timeout -= (System.currentTimeMillis() - before);
                    TimeUnit.MILLISECONDS.sleep(sleep);
                    timeout -= sleep;
                }
                return false;
            }
        };
        try {
            final Future<Boolean> future = service.submit(callable);
            if (!future.get()) {
                throw new TimeoutException(String.format("The embedded server did not start within %s seconds.", Environment.TIMEOUT));
            }
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to determine if the embedded server is running.", e);
        }
    }

    protected static final Function<EmbeddedManagedProcess, Boolean> STANDALONE_CHECK = new Function<EmbeddedManagedProcess, Boolean>() {
        private final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state");

        @Override
        public Boolean apply(final EmbeddedManagedProcess server) {

            try {
                final ModelControllerClient client = server.getModelControllerClient();
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

    protected static final Function<EmbeddedManagedProcess, Boolean> HOST_CONTROLLER_CHECK = new Function<EmbeddedManagedProcess, Boolean>() {
        @Override
        public Boolean apply(final EmbeddedManagedProcess server) {

            try {
                final ModelControllerClient client = server.getModelControllerClient();
                final ModelNode hostAddress = determineHostAddress(client);
                final ModelNode op = Operations.createReadAttributeOperation(hostAddress, "host-state");
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

    protected static ModelNode executeOperation(final ModelControllerClient client, final ModelNode op) throws IOException {
        return executeOperation(client, OperationBuilder.create(op).build());
    }

    private static ModelNode executeOperation(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Failed to execute op: %s%nFailure Description: %s", op, Operations.getFailureDescription(result)));
        }
        return Operations.readResult(result);
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
