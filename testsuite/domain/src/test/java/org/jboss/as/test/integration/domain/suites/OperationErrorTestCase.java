/*
Copyright 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_FAILURE_DESCRIPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRIMARY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILED_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.management.extension.blocker.BlockerExtension.CALLER;
import static org.jboss.as.test.integration.management.extension.blocker.BlockerExtension.TARGET_HOST;
import static org.jboss.as.test.integration.management.extension.blocker.BlockerExtension.TARGET_SERVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.extension.error.ErrorExtension;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of handling of java.lang.Error thrown while running operations in a domain.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class OperationErrorTestCase {

    private static final Logger log = Logger.getLogger(OperationErrorTestCase.class);

    private static final PathElement SUBSYSTEM_ELEMENT = PathElement.pathElement(SUBSYSTEM, ErrorExtension.SUBSYSTEM_NAME);
    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(
            PathElement.pathElement(PROFILE, "default"),
            SUBSYSTEM_ELEMENT);
    private static final ModelNode ERROR_OP = Util.createEmptyOperation("error", SUBSYSTEM_ADDRESS);
    private static final PathAddress MGMT_CONTROLLER = PathAddress.pathAddress(
            PathElement.pathElement(CORE_SERVICE, MANAGEMENT),
            PathElement.pathElement(SERVICE, MANAGEMENT_OPERATIONS)
    );
    private static final PathAddress MASTER_ADDRESS = PathAddress.pathAddress(HOST, PRIMARY);
    private static final PathAddress SLAVE_ADDRESS = PathAddress.pathAddress(HOST, "secondary");


    private static final EnumSet<ErrorExtension.ErrorPoint> RUNTIME_POINTS =
            EnumSet.of(ErrorExtension.ErrorPoint.RUNTIME, ErrorExtension.ErrorPoint.SERVICE_START, ErrorExtension.ErrorPoint.SERVICE_STOP);

    private static final long GET_TIMEOUT = TimeoutUtil.adjust(10000);

    private static final ModelNode ROLLOUT_HEADER;

    static {
        ROLLOUT_HEADER = new ModelNode();
        ModelNode groupHeader = ROLLOUT_HEADER.get(ROLLOUT_PLAN, IN_SERIES).add();
        groupHeader.get(SERVER_GROUP, "main-server-group", MAX_FAILED_SERVERS).set(1);
        ROLLOUT_HEADER.protect();
    }

    private static DomainTestSupport testSupport;
    private static DomainClient masterClient;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(OperationCancellationTestCase.class.getSimpleName());
        masterClient = testSupport.getDomainMasterLifecycleUtil().getDomainClient();

        // Initialize the test extension
        ExtensionSetup.initializeErrorExtension(testSupport);

        PathAddress extensionAddress = PathAddress.pathAddress(EXTENSION, ErrorExtension.MODULE_NAME);
        ModelNode addExtension = Util.createAddOperation(extensionAddress);

        executeForResponse(addExtension, masterClient);

        ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(
                PathElement.pathElement(PROFILE, "default"),
                SUBSYSTEM_ELEMENT));
        executeForResponse(addSubsystem, masterClient);

        ModelNode addMasterExtension = Util.createAddOperation(MASTER_ADDRESS.append(extensionAddress));
        executeForResponse(addMasterExtension, masterClient);

        ModelNode addMasterSubsystem = Util.createAddOperation(MASTER_ADDRESS.append(SUBSYSTEM_ELEMENT));
        executeForResponse(addMasterSubsystem, masterClient);

        ModelNode addSlaveExtension = Util.createAddOperation(SLAVE_ADDRESS.append(extensionAddress));
        executeForResponse(addSlaveExtension, masterClient);

        ModelNode addSlaveSubsystem = Util.createAddOperation(SLAVE_ADDRESS.append(SUBSYSTEM_ELEMENT));
        executeForResponse(addSlaveSubsystem, masterClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        ModelNode removeSubsystem = Util.createEmptyOperation(REMOVE, PathAddress.pathAddress(
                PathElement.pathElement(PROFILE, "default"),
                SUBSYSTEM_ELEMENT));
        executeForResponse(removeSubsystem, masterClient);

        PathAddress extensionAddress = PathAddress.pathAddress(EXTENSION, ErrorExtension.MODULE_NAME);
        ModelNode removeExtension = Util.createEmptyOperation(REMOVE, extensionAddress);
        executeForResponse(removeExtension, masterClient);

        ModelNode removeSlaveSubsystem = Util.createEmptyOperation(REMOVE, SLAVE_ADDRESS.append(SUBSYSTEM_ELEMENT));
        executeForResponse(removeSlaveSubsystem, masterClient);

        ModelNode removeSlaveExtension = Util.createEmptyOperation(REMOVE, SLAVE_ADDRESS.append(extensionAddress));
        executeForResponse(removeSlaveExtension, masterClient);

        ModelNode removeMasterSubsystem = Util.createEmptyOperation(REMOVE, MASTER_ADDRESS.append(SUBSYSTEM_ELEMENT));
        executeForResponse(removeMasterSubsystem, masterClient);

        ModelNode removeMasterExtension = Util.createEmptyOperation(REMOVE, MASTER_ADDRESS.append(extensionAddress));
        executeForResponse(removeMasterExtension, masterClient);

        testSupport = null;
        masterClient = null;
        DomainTestSuite.stopSupport();
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @After
    public void awaitCompletion() throws Exception {
        // Start from the leaves of the domain process tree and work inward validating
        // that all error ops are cleared locally. This ensures that a later test doesn't
        // mistakenly cancel a completing op from an earlier test
        validateNoActiveOperation(masterClient, "primary", "main-one");
        validateNoActiveOperation(masterClient, "secondary", "main-three");
        validateNoActiveOperation(masterClient, "secondary", null);
        MgmtOperationException moe = validateNoActiveOperation(masterClient, "primary", null);
        if (moe != null) {
            throw moe;
        }
    }

    @Test
    public void testMasterServerStageModel() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.MODEL, false, true);
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.MODEL, true, false);
    }

    @Test
    public void testSlaveServerStageModel() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.MODEL, false, true);
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.MODEL, true, false);
    }

    @Test
    public void testMasterHCStageModel() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("primary", null, ErrorExtension.ErrorPoint.MODEL, false, true);
        errorTest("primary", null, ErrorExtension.ErrorPoint.MODEL, true, true);
    }

    @Test
    public void testSlaveHCStageModel() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("secondary", null, ErrorExtension.ErrorPoint.MODEL, false, true);
        errorTest("secondary", null, ErrorExtension.ErrorPoint.MODEL, true, true);
    }

    @Test
    public void testMasterServerStageRuntime() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.RUNTIME, false, true);
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.RUNTIME, true, false);
    }

    @Test
    public void testSlaveServerStageRuntime() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.RUNTIME, false, true);
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.RUNTIME, true, false);
    }

    @Test
    public void testMasterHCStageRuntime() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("primary", null, ErrorExtension.ErrorPoint.RUNTIME, false, true);
        errorTest("primary", null, ErrorExtension.ErrorPoint.RUNTIME, true, true);
    }

    @Test
    public void testSlaveHCStageRuntime() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("secondary", null, ErrorExtension.ErrorPoint.RUNTIME, false, true);
        errorTest("secondary", null, ErrorExtension.ErrorPoint.RUNTIME, true, true);
    }

    @Test
    public void testMasterServerServiceStart() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.SERVICE_START, false, true);
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.SERVICE_START, true, false);
    }

    @Test
    public void testSlaveServerServiceStart() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.SERVICE_START, false, true);
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.SERVICE_START, true, false);
    }

    @Test
    public void testMasterHCServiceStart() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("primary", null, ErrorExtension.ErrorPoint.SERVICE_START, false, true);
        errorTest("primary", null, ErrorExtension.ErrorPoint.SERVICE_START, true, true);
    }

    @Test
    public void testSlaveHCServiceStart() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("secondary", null, ErrorExtension.ErrorPoint.SERVICE_START, false, true);
        errorTest("secondary", null, ErrorExtension.ErrorPoint.SERVICE_START, true, true);
    }

    @Test
    public void testMasterServerServiceStop() throws Exception {
        // MSC only logs a WARN for service.stop() errors and stops the service regardless.
        // So the op should succeed.
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.SERVICE_STOP, false, false);
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.SERVICE_STOP, true, false);
    }

    @Test
    public void testSlaveServerServiceStop() throws Exception {
        // MSC only logs a WARN for service.stop() errors and stops the service regardless.
        // So the op should succeed.
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.SERVICE_STOP, false, false);
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.SERVICE_STOP, true, false);
    }

    @Test
    public void testMasterHCServiceStop() throws Exception {
        // MSC only logs a WARN for service.stop() errors and stops the service regardless.
        // So the op should succeed.
        errorTest("primary", null, ErrorExtension.ErrorPoint.SERVICE_STOP, false, false);
        errorTest("primary", null, ErrorExtension.ErrorPoint.SERVICE_STOP, true, false);
    }

    @Test
    public void testSlaveHCServiceStop() throws Exception {
        // MSC only logs a WARN for service.stop() errors and stops the service regardless.
        // So the op should succeed.
        errorTest("secondary", null, ErrorExtension.ErrorPoint.SERVICE_STOP, false, false);
        errorTest("secondary", null, ErrorExtension.ErrorPoint.SERVICE_STOP, true, false);
    }

    @Test
    public void testMasterServerStageVerify() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.VERIFY, false, true);
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.VERIFY, true, false);
    }

    @Test
    public void testSlaveServerStageVerify() throws Exception {
        // The server fails in pre-prepare so whether overall outcome=failed depends on the rollout plan
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.VERIFY, false, true);
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.VERIFY, true, false);
    }

    @Test
    public void testMasterHCStageVerify() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("primary", null, ErrorExtension.ErrorPoint.VERIFY, false, true);
        errorTest("primary", null, ErrorExtension.ErrorPoint.VERIFY, true, true);
    }

    @Test
    public void testSlaveHCStageVerify() throws Exception {
        // A pre-prepare failure on an HC always means outcome=failed
        errorTest("secondary", null, ErrorExtension.ErrorPoint.VERIFY, false, true);
        errorTest("secondary", null, ErrorExtension.ErrorPoint.VERIFY, true, true);
    }

    @Test
    public void testMasterServerStageCommit() throws Exception {
        // Post commit failure does not result in outcome=failed
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.COMMIT, false, false);
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.COMMIT, true, false);
    }

    @Test
    public void testSlaveServerStageCommit() throws Exception {
        // Post commit failure does not result in outcome=failed
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.COMMIT, false, false);
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.COMMIT, true, false);
    }

    @Test
    public void testMasterHCStageCommit() throws Exception {
        // Here the failure blows away the normal domain response and results in reporting a failure
        // TODO this isn't ideal as the DC's model, MSC and the rest of the domain are changed
        errorTest("primary", null, ErrorExtension.ErrorPoint.COMMIT, false, true);
        errorTest("primary", null, ErrorExtension.ErrorPoint.COMMIT, true, true);
    }

    @Test
    public void testSlaveHCStageCommit() throws Exception {
        // Post commit failure does not result in outcome=failed
        errorTest("secondary", null, ErrorExtension.ErrorPoint.COMMIT, false, false);
        errorTest("secondary", null, ErrorExtension.ErrorPoint.COMMIT, true, false);
    }

    @Test
    public void testMasterServerStageRollback() throws Exception {
        // The server fails in pre-prepare (to trigger the rollback that throws Error),
        // so whether overall outcome=failed depends on the rollout plan
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.ROLLBACK, false, true);
        errorTest("primary", "main-one", ErrorExtension.ErrorPoint.ROLLBACK, true, false);
    }

    @Test
    public void testSlaveServerStageRollback() throws Exception {
        // The server fails in pre-prepare (to trigger the rollback that throws Error),
        // so whether overall outcome=failed depends on the rollout plan
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.ROLLBACK, false, true);
        errorTest("secondary", "main-three", ErrorExtension.ErrorPoint.ROLLBACK, true, false);
    }

    @Test
    public void testMasterHCStageRollback() throws Exception {
        // A pre-prepare failure (used to trigger the rollback that throws Error) on an HC always means outcome=failed
        errorTest("primary", null, ErrorExtension.ErrorPoint.ROLLBACK, false, true);
        errorTest("primary", null, ErrorExtension.ErrorPoint.ROLLBACK, true, true);
    }

    @Test
    public void testSlaveHCStageRollback() throws Exception {
        // A pre-prepare failure (used to trigger the rollback that throws Error) on an HC always means outcome=failed
        errorTest("secondary", null, ErrorExtension.ErrorPoint.ROLLBACK, false, true);
        errorTest("secondary", null, ErrorExtension.ErrorPoint.ROLLBACK, true, true);
    }

    private void errorTest(String host, String server, ErrorExtension.ErrorPoint errorPoint, boolean addRolloutPlan, boolean expectFailure) throws Exception {
        boolean multiphase = true;
        ModelNode op = ERROR_OP.clone();
        op.get(TARGET_HOST.getName()).set(host);
        if (server != null) {
            op.get(TARGET_SERVER.getName()).set(server);
        }
        op.get(ErrorExtension.ERROR_POINT.getName()).set(errorPoint.toString());
        op.get(CALLER.getName()).set(getTestMethod());
        if (addRolloutPlan) {
            op.get(OPERATION_HEADERS).set(ROLLOUT_HEADER);
        }
        if (server == null && RUNTIME_POINTS.contains(errorPoint)) {
            // retarget the op to the HC subsystem
            PathAddress addr = host.equals("primary") ? MASTER_ADDRESS : SLAVE_ADDRESS;
            op.get(OP_ADDR).set(addr.append(SUBSYSTEM_ELEMENT).toModelNode());
            multiphase = false;
        }

        ModelNode response;
        if (expectFailure) {
            response = executeForFailure(op, masterClient);
        } else {
            response = executeForResponse(op, masterClient);
        }
        if (errorPoint != ErrorExtension.ErrorPoint.SERVICE_STOP) {
            validateResponseDetails(response, host, server, errorPoint, multiphase);
        } // else it's a success and I'm too lazy to write the code to verify the response
    }

    private void validateResponseDetails(ModelNode response, String host, String server,
                                         ErrorExtension.ErrorPoint errorPoint, boolean multiphase) {
        ModelNode unmodified = response.clone();
        ModelNode fd;
        if (server != null) {
            ModelNode serverResp = response.get(SERVER_GROUPS, "main-server-group", HOST, host, server, RESPONSE);
            assertEquals(unmodified.toString(), FAILED, serverResp.get(OUTCOME).asString());
            fd = serverResp.get(FAILURE_DESCRIPTION);
        } else if ("primary".equals(host)) {
            if (errorPoint == ErrorExtension.ErrorPoint.COMMIT || errorPoint == ErrorExtension.ErrorPoint.ROLLBACK) {
                // In this case the late error destroys the normal response structure and we
                // get a simple failure. This isn't ideal. I'm writing the test to specifically assert
                // this simple failure structure mostly so it can assert the more complex
                // structure in the other cases, not because I'm explicitly ruling out any change to this
                // simple structure and want this test to enforce that. OTOH it shouldn't be changed lightly
                // as it would be easy to mess up.
                fd = response.get(FAILURE_DESCRIPTION);
            } else if (!multiphase) {
                fd = response.get(FAILURE_DESCRIPTION);
            } else if (RUNTIME_POINTS.contains(errorPoint)) {
                fd = response.get(FAILURE_DESCRIPTION, HOST_FAILURE_DESCRIPTIONS, host);
            } else {
                fd = response.get(FAILURE_DESCRIPTION, DOMAIN_FAILURE_DESCRIPTION);
            }
        } else {
            if (errorPoint == ErrorExtension.ErrorPoint.COMMIT) {
                // post-commit failure currently does not produce a failure-description
                assertFalse(unmodified.toString(), response.hasDefined(FAILURE_DESCRIPTION));
                return;
            }
            fd = multiphase ? response.get(FAILURE_DESCRIPTION, HOST_FAILURE_DESCRIPTIONS, host) : response.get(FAILURE_DESCRIPTION);
        }
        ModelType errorType = errorPoint == ErrorExtension.ErrorPoint.SERVICE_START ? ModelType.OBJECT : ModelType.STRING;
        assertEquals(unmodified.toString(), errorType, fd.getType());
        assertTrue(unmodified.toString(), fd.asString().contains(ErrorExtension.ERROR_MESSAGE));
    }

    private MgmtOperationException validateNoActiveOperation(DomainClient client, String host, String server) throws Exception {

        PathAddress baseAddress = getManagementControllerAddress(host, server);

        // The op should clear w/in a few ms but we'll wait up to 5 secs just in case
        // something strange is happening on the machine is overloaded
        long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(5000);
        MgmtOperationException failure;
        String id;
        do {
            id = findActiveOperation(client, baseAddress, "error");
            if (id == null) {
                return null;
            }
            failure = null;
            PathAddress address = baseAddress.append(PathElement.pathElement(ACTIVE_OPERATION, id));
            ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
            op.get(NAME).set(OP);
            try {
                executeForFailure(op, client);
            } catch (MgmtOperationException moe) {
                failure = moe;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < timeout);

        if (failure != null) {
            PathAddress address = baseAddress.append(PathElement.pathElement(ACTIVE_OPERATION, id));

            if (!executeForResponse(Util.createEmptyOperation("cancel", address), client).get(RESULT).asBoolean()) {
                return failure;
            }
        }
        return null;
    }

    private String findActiveOperation(DomainClient client, PathAddress address, String opName) throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, address);
        op.get(CHILD_TYPE).set(ACTIVE_OPERATION);
        ModelNode result = executeForResponse(op, client).get(RESULT);
        if (result.isDefined()) {
            assertEquals(result.asString(), ModelType.OBJECT, result.getType());
            for (Property prop : result.asPropertyList()) {
                if (prop.getValue().get(OP).asString().equals(opName)) {
                    return prop.getName();
                }
            }
        }
        return null;
    }

    private static ModelNode execute(final ModelNode op, final ModelControllerClient modelControllerClient) throws Exception {
        Future<ModelNode> future =  modelControllerClient.executeAsync(op, OperationMessageHandler.DISCARD);
        try {
            return future.get(GET_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw e;
        } catch (Exception e) {
            future.cancel(true);
            throw e;
        }
    }

    private static ModelNode executeForResponse(final ModelNode op, final ModelControllerClient modelControllerClient) throws Exception {
        final ModelNode ret = execute(op, modelControllerClient);

        if (! SUCCESS.equals(ret.get(OUTCOME).asString())) {
            System.out.println("Failed operation:");
            System.out.println(op);
            System.out.println("Response:");
            System.out.println(ret);
            throw new MgmtOperationException("Management operation failed.", op, ret);
        }
        return ret;
    }

    private static ModelNode executeForFailure(final ModelNode op, final ModelControllerClient modelControllerClient) throws Exception {
        final ModelNode ret = execute(op, modelControllerClient);

        if (! FAILED.equals(ret.get(OUTCOME).asString())) {
            System.out.println("Unexpectedly successful operation:");
            System.out.println(op);
            System.out.println("Response:");
            System.out.println(ret);
            throw new MgmtOperationException("Management operation succeeded.", op, ret);
        }

        return ret;
    }

    private static String getTestMethod() {
        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : stack) {
            String method = ste.getMethodName();
            if (method.startsWith("test")) {
                return method;
            }
        }
        return "unknown";
    }

    private static PathAddress getManagementControllerAddress(String host, String server) {
        PathAddress address = PathAddress.pathAddress(PathElement.pathElement(HOST, host));
        if (server != null) {
            address = address.append(PathElement.pathElement(SERVER, server));
        }
        address = address.append(MGMT_CONTROLLER);
        return address;
    }
}
