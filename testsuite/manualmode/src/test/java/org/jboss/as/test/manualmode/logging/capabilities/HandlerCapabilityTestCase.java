/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.capabilities;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class HandlerCapabilityTestCase extends CapabilityTestCase {

    private static final int QUEUE_LENGTH = 5;

    @Test
    public void testAsyncAddInvalidSubhandler() throws Exception {
        final ModelNode address = createSubsystemAddress("async-handler", "async");
        final ModelNode op = Operations.createAddOperation(address);
        op.get("queue-length").set(QUEUE_LENGTH);
        op.get("subhandlers").setEmptyList().add("non-existing");
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testAsyncAddHandlerInvalidSubhandler() throws Exception {
        final ModelNode address = createSubsystemAddress("async-handler", "async");
        ModelNode op = Operations.createAddOperation(address);
        op.get("queue-length").set(QUEUE_LENGTH);

        executeOperation(op);

        op = Operations.createOperation("add-handler", address);
        op.get("name").set("non-existing");
        executeOperationForFailure(op, NOT_FOUND);

        executeOperation(Operations.createRemoveOperation(address));
    }

    @Test
    public void testAsyncWriteInvalidSubhandler() throws Exception {
        final ModelNode address = createSubsystemAddress("async-handler", "async");
        final ModelNode op = Operations.createAddOperation(address);
        op.get("queue-length").set(QUEUE_LENGTH);

        executeOperation(op);

        final ModelNode handlers = new ModelNode().setEmptyList().add("non-existing");
        executeOperationForFailure(Operations.createWriteAttributeOperation(address, "subhandlers", handlers), NOT_FOUND);

        executeOperation(Operations.createRemoveOperation(address));
    }

    @Test
    public void testRemoveAttachedHandler() throws Exception {
        final ModelNode address = createSubsystemAddress("console-handler", HANDLER_NAME);
        executeOperationForFailure(Operations.createRemoveOperation(address), CANNOT_REMOVE);
    }

    @Test
    public void testAddInvalidFormatter() throws Exception {
        final ModelNode address = createSubsystemAddress("console-handler", "failed");
        final ModelNode op = Operations.createAddOperation(address);
        op.get("named-formatter").set("non-existing");
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testAsyncWriteInvalidFormatter() throws Exception {
        final ModelNode address = createSubsystemAddress("console-handler", "failed");
        final ModelNode op = Operations.createAddOperation(address);

        executeOperation(op);
        executeOperationForFailure(
                Operations.createWriteAttributeOperation(address, "named-formatter", "non-existing"), NOT_FOUND);

        executeOperation(Operations.createRemoveOperation(address));
    }

    @Test
    public void testRemoveAttachedFormatter() throws Exception {
        final ModelNode address = createSubsystemAddress("pattern-formatter", FORMATTER_NAME);
        executeOperationForFailure(Operations.createRemoveOperation(address), CANNOT_REMOVE);
    }
}
