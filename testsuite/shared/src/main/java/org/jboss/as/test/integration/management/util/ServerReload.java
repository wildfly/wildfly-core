/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.util;

import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.xnio.IoUtils;

/**
 * Utilities for handling server reloads.
 *
 * @author Stuart Douglas
 */
public class ServerReload {

    /** Default time, in ms, to wait for reload to complete. */
    public static final int TIMEOUT = 100000;

    /**
     * Executes a {@code reload} operation and waits the {@link #TIMEOUT default timeout}
     * for the reload to complete.
     *
     * @param client the client to use for the request. Cannot be {@code null}
     *
     * @throws AssertionError if the reload does not complete within the timeout
     */
    public static void executeReloadAndWaitForCompletion(ModelControllerClient client) {
        executeReloadAndWaitForCompletion(client, TIMEOUT);
    }

    /**
     * Executes a {@code reload} operation, optionally putting the server into {@code admin-only}
     * running mode, and waits the {@link #TIMEOUT default timeout} for the reload to complete.
     *
     * @param client the client to use for the request. Cannot be {@code null}
     * @param adminOnly {@code true} if the server's running mode should be {@code admin-only} when
     *                              the reload completes
     *
     * @throws AssertionError if the reload does not complete within the timeout
     */
    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, boolean adminOnly) {
        executeReloadAndWaitForCompletion(client, TIMEOUT, adminOnly, null, -1);
    }

    /**
     * Executes a {@code reload} operation and waits a configurable maximum time for the reload to complete.
     *
     * @param client the client to use for the request. Cannot be {@code null}
     * @param timeout maximum time to wait for the reload to complete, in milliseconds
     *
     * @throws AssertionError if the reload does not complete within the specified timeout
     */
    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, int timeout) {
        executeReloadAndWaitForCompletion(client, timeout, false, null, -1);
    }

    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, ModelNode reloadOp) {
        executeReloadAndWaitForCompletion(client, reloadOp, TIMEOUT, null, -1);
    }

    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, ModelNode reloadOp, int timeout, String serverAddress, int serverPort) {
        executeReload(client, reloadOp);
        waitForLiveServerToReload(timeout, "remote+http",
                serverAddress != null ? serverAddress : TestSuiteEnvironment.getServerAddress(),
                serverPort != -1 ? serverPort : TestSuiteEnvironment.getServerPort());
    }

    /**
     * Executes a {@code reload} operation, optionally putting the server into {@code admin-only}
     * running mode, and waits a configurable maximum time for the reload to complete.
     *
     * @param client the client to use for the request. Cannot be {@code null}
     * @param timeout maximum time to wait for the reload to complete, in milliseconds
     * @param adminOnly     if {@code true}, the server will be reloaded in admin-only mode
     * @param serverAddress if {@code null}, use {@code TestSuiteEnvironment.getServerAddress()} to create the ModelControllerClient
     * @param serverPort    if {@code -1}, use {@code TestSuiteEnvironment.getServerPort()} to create the ModelControllerClient
     *
     * @throws AssertionError if the reload does not complete within the specified timeout
     */
    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, int timeout, boolean adminOnly, String serverAddress, int serverPort) {
        executeReload(client, adminOnly);
        waitForLiveServerToReload(timeout, "remote+http",
                serverAddress != null ? serverAddress : TestSuiteEnvironment.getServerAddress(),
                serverPort != -1 ? serverPort : TestSuiteEnvironment.getServerPort());
    }

    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, int timeout, boolean adminOnly, String protocol, String serverAddress, int serverPort) {
        executeReload(client, adminOnly);
        waitForLiveServerToReload(timeout, protocol,
                serverAddress != null ? serverAddress : TestSuiteEnvironment.getServerAddress(),
                serverPort != -1 ? serverPort : TestSuiteEnvironment.getServerPort());
    }

    public static void executeReload(ModelControllerClient client, boolean adminOnly) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set("reload");
        operation.get("admin-only").set(adminOnly);

        executeReload(client, operation);
    }

    public static void executeReload(ModelControllerClient client, ModelNode reloadOp) {
        try {
            ModelNode result = client.execute(reloadOp);
            Assert.assertEquals(SUCCESS, result.get(ClientConstants.OUTCOME).asString());
        } catch (IOException e) {
            final Throwable cause = e.getCause();
            if (!(cause instanceof ExecutionException) && !(cause instanceof CancellationException)) {
                throw new RuntimeException(e);
            } // else ignore, this might happen if the channel gets closed before we got the response
        }
    }


    public static String getContainerRunningState(ModelControllerClient modelControllerClient) throws IOException {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("server-state");
        ModelNode rsp = modelControllerClient.execute(operation);
        return SUCCESS.equals(rsp.get(OUTCOME).asString()) ? rsp.get(RESULT).asString() : "failed";
    }


    /**
     * Checks if the container status is "reload-required" and if it's the case executes reload and waits for completion.
     */
    public static void reloadIfRequired(final ModelControllerClient controllerClient) throws Exception {
        String runningState = getContainerRunningState(controllerClient);
        if ("reload-required".equalsIgnoreCase(runningState)) {
            executeReloadAndWaitForCompletion(controllerClient);
        } else {
            Assert.assertEquals("Server state 'running' is expected", "running", runningState);
        }
    }

    private static void waitForLiveServerToReload(int timeout, String protocol, String serverAddress, int serverPort) {
        long start = System.currentTimeMillis();
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("server-state");
        while (System.currentTimeMillis() - start < timeout) {
            //do the sleep before we check, as the attribute state may not change instantly
            //also reload generally takes longer than 100ms anyway
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            try {
                ModelControllerClient liveClient = ModelControllerClient.Factory.create(protocol,
                        serverAddress, serverPort);
                try {
                    ModelNode result = liveClient.execute(operation);
                    if ("running".equals(result.get(RESULT).asString())) {
                        return;
                    }
                } catch (IOException e) {
                } finally {
                    IoUtils.safeClose(liveClient);
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        fail("Live Server did not reload in the imparted time.");
    }

    /**
     * {@link ServerSetupTask} that calls {@link #executeReloadAndWaitForCompletion(ModelControllerClient)}
     * in the {@code tearDown} method
     */
    public static class SetupTask implements ServerSetupTask {

        public static final SetupTask INSTANCE = new SetupTask();

        /**
         * A no-op.
         *
         * {@inheritDoc}
         */
        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            // no-op;
        }

        /**
         * Calls {@link #executeReloadAndWaitForCompletion(ModelControllerClient)}.
         *
         * {@inheritDoc}
         */
        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        }
    }
}

