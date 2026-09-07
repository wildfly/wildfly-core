/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTION_STATUS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.integration.management.extension.blocker.BlockerExtension.BLOCK_POINT;
import static org.jboss.as.test.integration.management.extension.blocker.BlockerExtension.CALLER;
import static org.jboss.as.test.integration.management.extension.blocker.BlockerExtension.TARGET_HOST;
import static org.jboss.as.test.integration.management.extension.blocker.BlockerExtension.TARGET_SERVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.OperationCancellationTestCase;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
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
 * Tests that the domain responds appropriately to hang situations on remote processes.
 *
 * @author Brian Stansberry
 */
public class OperationTimeoutTestCase {

    private static final Logger log = Logger.getLogger(OperationCancellationTestCase.class);

    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(
            PathElement.pathElement(PROFILE, "default"),
            PathElement.pathElement(SUBSYSTEM, BlockerExtension.SUBSYSTEM_NAME));
    private static final ModelNode BLOCK_OP = Util.createEmptyOperation("block", SUBSYSTEM_ADDRESS);
    private static final ModelNode WRITE_FOO_OP = Util.getWriteAttributeOperation(SUBSYSTEM_ADDRESS, BlockerExtension.FOO.getName(), true);
    private static final PathAddress MGMT_CONTROLLER = PathAddress.pathAddress(
            PathElement.pathElement(CORE_SERVICE, MANAGEMENT),
            PathElement.pathElement(SERVICE, MANAGEMENT_OPERATIONS)
    );
    private static final PathAddress RUNTIME_MXBEAN = PathAddress.pathAddress(
            PathElement.pathElement(CORE_SERVICE, PLATFORM_MBEAN),
            PathElement.pathElement(TYPE, "runtime")
    );
    private static final String TIMEOUT_CONFIG = "-Djboss.as.management.blocking.timeout=1";
    private static final String TIMEOUT_ADDER_CONFIG = "-Dorg.wildfly.unsupported.test.domain-timeout-adder=1000";

    private static final long SAFE_TIMEOUT = TimeoutUtil.adjust(20);
    private static final long GET_TIMEOUT = TimeoutUtil.adjust(10000);

    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;

    @BeforeClass
    public static void setupDomain() throws Exception {

        // We can't use the standard config or make this part of a TestSuite because we need to
        // set TIMEOUT_ADDER_CONFIG on the HC processes. There's no management API to do this post-boot
        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(OperationTimeoutTestCase.class.getSimpleName(),
            "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml");
        configuration.getPrimaryConfiguration().addHostCommandLineProperty(TIMEOUT_CONFIG);
        configuration.getPrimaryConfiguration().addHostCommandLineProperty(TIMEOUT_ADDER_CONFIG);
        configuration.getSecondaryConfiguration().addHostCommandLineProperty(TIMEOUT_CONFIG);
        configuration.getSecondaryConfiguration().addHostCommandLineProperty(TIMEOUT_ADDER_CONFIG);

        testSupport = DomainTestSupport.create(configuration);

        testSupport.start();
        primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        // Initialize the test extension
        ExtensionSetup.initializeBlockerExtension(testSupport);

        ModelNode addExtension = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, BlockerExtension.MODULE_NAME)));

        executeForResult(safeTimeout(addExtension), primaryClient);

        ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(
                PathElement.pathElement(PROFILE, "default"),
                PathElement.pathElement(SUBSYSTEM, BlockerExtension.SUBSYSTEM_NAME)));
        executeForResult(safeTimeout(addSubsystem), primaryClient);

        restoreServerTimeouts("primary", "main-one");
        restoreServerTimeouts("secondary", "main-three");

        // Confirm that the timeout properties are what we expect on each process
        validateTimeoutProperties("primary", null, "1", "1000");
        validateTimeoutProperties("secondary", null, "1", "1000");
        validateTimeoutProperties("primary", "main-one", "300", "5000");
        validateTimeoutProperties("secondary", "main-three", "300", "5000");
    }

    private static void restoreServerTimeouts(String host, String server) throws IOException, MgmtOperationException {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, host), PathElement.pathElement(SERVER_CONFIG, server));
        ModelNode op = Util.createAddOperation(pa.append(PathAddress.pathAddress(SYSTEM_PROPERTY, "jboss.as.management.blocking.timeout")));
        op.get(VALUE).set("300");
        executeForResult(safeTimeout(op), primaryClient);

        op.get(OP_ADDR).set(pa.append(PathAddress.pathAddress(SYSTEM_PROPERTY, "org.wildfly.unsupported.test.domain-timeout-adder")).toModelNode());
        op.get(VALUE).set("5000");
        executeForResult(safeTimeout(op), primaryClient);
    }

    private static void validateTimeoutProperties(String host, String server, String baseTimeout, String domainAdder) throws IOException, MgmtOperationException {
        PathAddress pa = PathAddress.pathAddress(HOST, host);
        if (server != null) {
            pa = pa.append(RUNNING_SERVER, server);
        }
        ModelNode op = Util.getReadAttributeOperation(pa.append(RUNTIME_MXBEAN), "system-properties");
        ModelNode props = executeForResult(op, primaryClient);
        if (baseTimeout == null) {
            assertFalse(props.toString(), props.hasDefined("jboss.as.management.blocking.timeout"));
        } else {
            assertEquals(props.toString(), baseTimeout, props.get("jboss.as.management.blocking.timeout").asString());
        }
        if (domainAdder == null) {
            assertFalse(props.toString(), props.hasDefined("org.wildfly.unsupported.test.domain-timeout-adder"));
        } else {
            assertEquals(props.toString(), domainAdder, props.get("org.wildfly.unsupported.test.domain-timeout-adder").asString());
        }
    }

    /** Applies a more normal blocking-timeout to an op so it does not get tripped up by the low timeouts on the domain processes */
    private static ModelNode safeTimeout(ModelNode op) {
        op.get(OPERATION_HEADERS, BLOCKING_TIMEOUT).set(SAFE_TIMEOUT);
        return op;
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        ModelNode removeSubsystem = Util.createEmptyOperation(REMOVE, PathAddress.pathAddress(
                PathElement.pathElement(PROFILE, "default"),
                PathElement.pathElement(SUBSYSTEM, BlockerExtension.SUBSYSTEM_NAME)));
        try {
            assertNotNull(primaryClient);
            executeForResult(safeTimeout(removeSubsystem), primaryClient);

            ModelNode removeExtension = Util.createEmptyOperation(REMOVE, PathAddress.pathAddress(PathElement.pathElement(EXTENSION, BlockerExtension.MODULE_NAME)));
            executeForResult(safeTimeout(removeExtension), primaryClient);

            assertNotNull(testSupport);
            testSupport.close();
        } finally {
            testSupport = null;
            primaryClient = null;
        }
    }

    @After
    public void awaitCompletion() throws Exception {
        // Start from the leaves of the domain process tree and work inward validating
        // that all block ops are cleared locally. This ensures that a later test doesn't
        // mistakenly cancel a completing op from an earlier test
        validateNoActiveOperation(primaryClient, "primary", "main-one");
        validateNoActiveOperation(primaryClient, "secondary", "main-three");
        validateNoActiveOperation(primaryClient, "secondary", null);
        validateNoActiveOperation(primaryClient, "primary", null);
    }

    @Test
    public void testPrePrepareHangOnSecondaryHC() throws Exception {
        long start = System.currentTimeMillis();
        Future<ModelNode> blockFuture = block("secondary", null, BlockerExtension.BlockPoint.MODEL);
        String id = findActiveOperation(primaryClient, "secondary", null, "block", null, start);
        ModelNode response = blockFuture.get(GET_TIMEOUT, TimeUnit.MILLISECONDS);
        assertEquals(response.asString(), FAILED, response.get(OUTCOME).asString());
        System.out.println(response);
        assertTrue(response.asString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYDC0080"));
        validateNoActiveOperation(primaryClient, "secondary", null, id, true);
    }

    @Test
    public void testPrePrepareHangOnSecondaryServer() throws Exception {
        long start = System.currentTimeMillis();
        Future<ModelNode> blockFuture = block("secondary", "main-three", BlockerExtension.BlockPoint.RUNTIME);
        String id = findActiveOperation(primaryClient, "secondary", "main-three", "block", null, start);
        ModelNode response = blockFuture.get(GET_TIMEOUT, TimeUnit.MILLISECONDS);
        assertEquals(response.asString(), FAILED, response.get(OUTCOME).asString());
        assertTrue(response.asString(), response.get(FAILURE_DESCRIPTION).asString().contains("main-three"));
        assertTrue(response.asString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0409"));
        validateNoActiveOperation(primaryClient, "secondary", "main-three", id, true);
    }

    @Test
    public void testPrePrepareHangOnPrimaryServer() throws Exception {
        long start = System.currentTimeMillis();
        Future<ModelNode> blockFuture = block("primary", "main-one", BlockerExtension.BlockPoint.RUNTIME);
        String id = findActiveOperation(primaryClient, "primary", "main-one", "block", null, start);
        ModelNode response = blockFuture.get(GET_TIMEOUT, TimeUnit.MILLISECONDS);
        assertEquals(response.asString(), FAILED, response.get(OUTCOME).asString());
        assertTrue(response.asString(), response.get(FAILURE_DESCRIPTION).asString().contains("main-one"));
        assertTrue(response.asString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0409"));
        validateNoActiveOperation(primaryClient, "primary", "main-one", id, true);
    }

    @Test
    public void testPostCommitHangOnSecondaryHC() throws Exception {
        long start = System.currentTimeMillis();
        Future<ModelNode> blockFuture = block("secondary", null, BlockerExtension.BlockPoint.COMMIT);
        String id = findActiveOperation(primaryClient, "secondary", null, "block", OperationContext.ExecutionStatus.COMPLETING, start);
        ModelNode response = blockFuture.get(GET_TIMEOUT, TimeUnit.MILLISECONDS);
        // cancelling on the secondary during Stage.DONE should not result in a prepare-phase failure sent to primary,
        // so result should always be SUCCESS
        assertEquals(response.asString(), SUCCESS, response.get(OUTCOME).asString());
        validateNoActiveOperation(primaryClient, "secondary", null, id, true);
    }

    @Test
    public void testPostCommitHangOnSecondaryServer() throws Exception {
        long start = System.currentTimeMillis();
        Future<ModelNode> blockFuture = block("secondary", "main-three", BlockerExtension.BlockPoint.COMMIT);
        String id = findActiveOperation(primaryClient, "secondary", "main-three", "block", OperationContext.ExecutionStatus.COMPLETING, start);
        ModelNode response = blockFuture.get(GET_TIMEOUT, TimeUnit.MILLISECONDS);
        // cancelling on the server during Stage.DONE should not result in a prepare-phase failure sent to primary,
        // so result should always be SUCCESS
        assertEquals(response.asString(), SUCCESS, response.get(OUTCOME).asString());
        validateNoActiveOperation(primaryClient, "secondary", "main-three", id, true);
    }

    @Test
    public void testPostCommitHangOnPrimaryServer() throws Exception {
        long start = System.currentTimeMillis();
        Future<ModelNode> blockFuture = block("primary", "main-one", BlockerExtension.BlockPoint.COMMIT);
        String id = findActiveOperation(primaryClient, "primary", "main-one", "block", OperationContext.ExecutionStatus.COMPLETING, start);
        ModelNode response = blockFuture.get(GET_TIMEOUT, TimeUnit.MILLISECONDS);
        // cancelling on the server during Stage.DONE should not result in a prepare-phase failure sent to primary,
        // so result should always be SUCCESS
        assertEquals(response.asString(), SUCCESS, response.get(OUTCOME).asString());
        validateNoActiveOperation(primaryClient, "primary", "main-one", id, true);
    }

    private Future<ModelNode> block(String host, String server, BlockerExtension.BlockPoint blockPoint) {
        ModelNode op = BLOCK_OP.clone();
        op.get(TARGET_HOST.getName()).set(host);
        if (server != null) {
            op.get(TARGET_SERVER.getName()).set(server);
        }
        op.get(BLOCK_POINT.getName()).set(blockPoint.toString());
        op.get(CALLER.getName()).set(getTestMethod());
        return primaryClient.executeAsync(op, OperationMessageHandler.DISCARD);
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

    private static ModelNode executeForResult(final ModelNode op, final ModelControllerClient modelControllerClient) throws IOException, MgmtOperationException {
        try {
            return DomainTestUtils.executeForResult(op, modelControllerClient);
        } catch (MgmtOperationException e) {
            System.out.println(" Op failed:");
            System.out.println(e.getOperation());
            System.out.println("with result");
            System.out.println(e.getResult());
            throw e;
        }
    }

    private static ModelNode executeForFailure(final ModelNode op, final ModelControllerClient modelControllerClient) throws IOException, MgmtOperationException {
        try {
            return DomainTestUtils.executeForFailure(op, modelControllerClient);
        } catch (MgmtOperationException e) {
            System.out.println(" Op that incorrectly succeeded:");
            System.out.println(e.getOperation());
            throw e;
        }
    }

    private String findActiveOperation(DomainClient client, String host, String server, String opName,
                                       OperationContext.ExecutionStatus targetStatus, long executionStart) throws Exception {
        PathAddress address = getManagementControllerAddress(host, server);
        return findActiveOperation(client, address, opName, targetStatus, executionStart, false);
    }

    private String findActiveOperation(DomainClient client, PathAddress address, String opName, OperationContext.ExecutionStatus targetStatus, long executionStart, boolean serverOpOnly) throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, address);
        op.get(CHILD_TYPE).set(ACTIVE_OPERATION);
        long maxTime = TimeoutUtil.adjust(5000);
        long timeout = executionStart + maxTime;
        List<String> activeOps = new ArrayList<String>();
        String opToCancel = null;
        do {
            activeOps.clear();
            ModelNode result = executeForResult(op, client);
            if (result.isDefined()) {
                assertEquals(result.asString(), ModelType.OBJECT, result.getType());
                for (Property prop : result.asPropertyList()) {
                    if (prop.getValue().get(OP).asString().equals(opName)) {
                        PathAddress pa = PathAddress.pathAddress(prop.getValue().get(OP_ADDR));
                        if (!serverOpOnly || pa.size() > 2 && pa.getElement(1).getKey().equals(SERVER)) {
                            activeOps.add(prop.getName() + " -- " + prop.getValue().toString());
                            if (targetStatus == null || prop.getValue().get(EXECUTION_STATUS).asString().equals(targetStatus.toString())) {
                                opToCancel = prop.getName();
                            }
                        }
                    }
                }
            }
            if (opToCancel == null) {
                Thread.sleep(50);
            }

        } while ((opToCancel == null || activeOps.size() > 1) && System.currentTimeMillis() <= timeout);

        assertTrue(opName + " not present after " + maxTime + " ms", activeOps.size() > 0);
        assertEquals("Multiple instances of " + opName + " present: " + activeOps, 1, activeOps.size());
        assertNotNull(opName + " not in status " + targetStatus + " after " + maxTime + " ms", opToCancel);

        return opToCancel;
    }

    private String findActiveOperation(DomainClient client, PathAddress address, String opName) throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, address);
        op.get(CHILD_TYPE).set(ACTIVE_OPERATION);
        ModelNode result = executeForResult(op, client);
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

    private void validateNoActiveOperation(DomainClient client, String host, String server) throws Exception {

        PathAddress baseAddress = getManagementControllerAddress(host, server);

        // The op should clear w/in a few ms but we'll wait up to 5 secs just in case
        // something strange is happening on the machine is overloaded
        long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(5000);
        MgmtOperationException failure;
        do {
            String id = findActiveOperation(client, baseAddress, "block");
            if (id == null) {
                return;
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

        throw failure;
    }

    private void validateNoActiveOperation(DomainClient client, String host, String server, String id,
                                           boolean patient) throws Exception {
        PathAddress address = getManagementControllerAddress(host, server);
        address = address.append(PathElement.pathElement(ACTIVE_OPERATION, id));
        ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(OP);

        // The op should clear w/in a few ms but we'll wait up to 5 secs just in case
        // something strange is happening on the machine is overloaded
        long timeout = patient ? System.currentTimeMillis() + TimeoutUtil.adjust(5000) : 0;
        MgmtOperationException failure;
        do {
            try {
                executeForFailure(op, client);
                return;
            } catch (MgmtOperationException moe) {
                if (!patient) {
                    throw moe;
                }
                failure = moe;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < timeout);

        throw failure;
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
