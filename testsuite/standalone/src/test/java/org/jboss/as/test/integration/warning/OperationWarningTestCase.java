/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.warning;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LEVEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WARNING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WARNINGS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.base.AbstractMgmtTestBase;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

@ServerSetup(OperationWarningTestCase.SetupWorkers.class)
@RunWith(WildFlyRunner.class)
public class OperationWarningTestCase extends AbstractMgmtTestBase {
    @Inject
    private static ServerController serverController;

    protected static final String WORKER_SECOND = "puppet-master";
    protected static final String WORKER_DEFAULT = "default";
    protected static final String WORKER = "worker";
    protected static final PathAddress ADDRESS_IO_SUBSYSTEM = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "io"));
    protected static final PathAddress ADDRESS_WORKER_SECOND = ADDRESS_IO_SUBSYSTEM.append(PathElement.pathElement(WORKER, WORKER_SECOND));
    protected static final PathAddress ADDRESS_WORKER_DEFAULT = ADDRESS_IO_SUBSYSTEM.append(PathElement.pathElement(WORKER, WORKER_DEFAULT));
    protected static final PathAddress ADDRESS_REMOTING_SUBSYSTEM = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "remoting"));
    protected static final String BAD_LEVEL = "X_X";

    @Test
    public void testMe() throws Exception {

        ModelNode result = setRemotingWorkerTo(WORKER_SECOND, BAD_LEVEL);
        Assert.assertTrue(result.toString(), Operations.isSuccessfulOutcome(result));
        Assert.assertTrue(result.hasDefined(RESPONSE_HEADERS));
        ModelNode responseHeaders = result.get(RESPONSE_HEADERS);
        Assert.assertTrue(responseHeaders.hasDefined(WARNINGS));
        List<ModelNode> warnings = responseHeaders.get(WARNINGS).asList();
        Assert.assertTrue(warnings.size() == 2);
        ModelNode warningLoggerLevel = warnings.get(0);
        String message = warningLoggerLevel.get(WARNING).asString();
        Assert.assertEquals(ControllerLogger.ROOT_LOGGER.couldntConvertWarningLevel(BAD_LEVEL), message);
        Level level = Level.parse(warningLoggerLevel.get(LEVEL).asString());
        Assert.assertEquals(Level.ALL, level);
        ModelNode warningWorker = warnings.get(1);
        message = warningWorker.get(WARNING).asString();
        Assert.assertEquals(RemotingLogger.ROOT_LOGGER.warningOnWorkerChange(WORKER_SECOND), message);
        level = Level.parse(warningWorker.get(LEVEL).asString());
        Assert.assertEquals(Level.WARNING, level);
        // default level is "WARNING, set to severe and check if there are warnings
        result = setRemotingWorkerTo("default", "SEVERE");
        responseHeaders = result.get(RESPONSE_HEADERS);
        Assert.assertFalse(responseHeaders.hasDefined(WARNINGS));
        result = setRemotingWorkerTo("default", "OFF");
        responseHeaders = result.get(RESPONSE_HEADERS);
        Assert.assertFalse(responseHeaders.hasDefined(WARNINGS));
    }

    @Override
    protected ModelControllerClient getModelControllerClient() {
        return serverController.getClient().getControllerClient();
    }

    private static void add(final PathAddress address) throws Exception {
        add(address, Map.of());
    }

    private static void add(final PathAddress address, Map<String, ModelNode> parameters) throws Exception {
        ModelNode addOp = Util.createAddOperation(address, parameters);
        ModelNode resp = ManagementOperations.executeOperationRaw(serverController.getClient().getControllerClient(), addOp);
        assertEquals("Unexpected outcome " + resp + " of add operation: " + addOp, ModelDescriptionConstants.SUCCESS,
                resp.get("outcome").asString());
    }

    private static void remove(final PathAddress address) throws Exception {
        ModelNode removeOp = Util.createRemoveOperation(address);
        ModelNode resp = ManagementOperations.executeOperationRaw(serverController.getClient().getControllerClient(), removeOp);
        assertEquals("Unexpected outcome " + resp + " of remove operation: " + removeOp, ModelDescriptionConstants.SUCCESS,
                resp.get("outcome").asString());
    }

    private static ModelNode setRemotingWorkerTo(final String name, final String level) throws IOException {
        ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP_ADDR).set(ADDRESS_REMOTING_SUBSYSTEM.toModelNode());
        op.get(ModelDescriptionConstants.OP).set(WRITE_ATTRIBUTE_OPERATION);
        op.get(NAME).set(WORKER);
        op.get(VALUE).set(name);
        op.get(OPERATION_HEADERS).get(ModelDescriptionConstants.WARNING_LEVEL).set(level);
        final ModelControllerClient client = serverController.getClient().getControllerClient();
        ModelNode result = client.execute(op);
        return result;
    }

    static class SetupWorkers extends ServerReload.SetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            super.setup(managementClient);
            add(ADDRESS_WORKER_SECOND);
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            try {
                setRemotingWorkerTo(WORKER_DEFAULT, "OFF");
                remove(ADDRESS_WORKER_SECOND);
            } finally {
                super.tearDown(managementClient);
            }
        }
    }
}
