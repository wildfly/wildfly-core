/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.capabilities;

import java.io.IOException;

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
public class LoggerCapabilityTestCase extends CapabilityTestCase {
    private static final String LOGGER_NAME = LoggerCapabilityTestCase.class.getName();

    @Test
    public void testAddInvalidHandlerToRootLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("root-logger", "ROOT");
        final ModelNode op = Operations.createOperation("add-handler", address);
        op.get("name").set("non-existing");
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testAddInvalidHandlerToLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("logger", LOGGER_NAME);
        final ModelNode op = Operations.createAddOperation(address);
        op.get("handlers").setEmptyList().add("non-existing");
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testWriteInvalidHandlerToRootLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("root-logger", "ROOT");
        final ModelNode handlers = new ModelNode().setEmptyList().add("non-existing");
        final ModelNode op = Operations.createWriteAttributeOperation(address, "handlers", handlers);
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testWriteInvalidHandlerToLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("logger", LOGGER_NAME);
        executeOperation(Operations.createAddOperation(address));
        final ModelNode handlers = new ModelNode().setEmptyList().add("non-existing");
        final ModelNode op = Operations.createWriteAttributeOperation(address, "handlers", handlers);
        executeOperationForFailure(op, NOT_FOUND);
        executeOperation(Operations.createRemoveOperation(address));
    }
}
