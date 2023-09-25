/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import static org.jboss.as.subsystem.test.SubsystemOperations.OperationBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.logging.handlers.AbstractHandlerDefinition;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.as.logging.loggers.LoggerResourceDefinition;
import org.jboss.as.logging.loggers.RootLoggerResourceDefinition;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggerOperationsTestCase extends AbstractOperationsTestCase {

    @Override
    protected void standardSubsystemTest(final String configId) {
        // do nothing as this is not a subsystem parsing test
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/empty-subsystem.xml");
    }

    @Test
    public void testOperations() throws Exception {
        final KernelServices kernelServices = boot();

        testRootLogger(kernelServices, null);
        testRootLogger(kernelServices, PROFILE);

        testLogger(kernelServices, null);
        testLogger(kernelServices, PROFILE);

        kernelServices.shutdown();
    }

    private void testRootLogger(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createRootLoggerAddress(profileName).toModelNode();
        final ModelNode handlers = new ModelNode().setEmptyList().add("CONSOLE");

        // Add the logger
        final ModelNode addOp = SubsystemOperations.createAddOperation(address);
        executeOperation(kernelServices, addOp);

        // Add a console handler for handlers tests
        final ModelNode consoleAddress = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();
        executeOperation(kernelServices, SubsystemOperations.createAddOperation(consoleAddress));

        // Write each attribute and check the value
        testWriteCommonAttributes(kernelServices, address, handlers);

        // Undefine attributes
        testUndefineCommonAttributes(kernelServices, address);

        // Test the add-handler operation
        ModelNode op = OperationBuilder.create(RootLoggerResourceDefinition.ADD_HANDLER_OPERATION, address)
                .addAttribute(CommonAttributes.HANDLER_NAME, "CONSOLE")
                .build();
        executeOperation(kernelServices, op);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, LoggerAttributes.HANDLERS.getName());
        ModelNode result = executeOperation(kernelServices, readOp);
        assertEquals(handlers, SubsystemOperations.readResult(result));

        // Test remove-handler operation
        op = SubsystemOperations.createOperation(RootLoggerResourceDefinition.REMOVE_HANDLER_OPERATION.getName(), address);
        op.get(CommonAttributes.HANDLER_NAME.getName()).set("CONSOLE");
        executeOperation(kernelServices, op);
        result = executeOperation(kernelServices, readOp);
        assertTrue("Handler CONSOLE should have been removed: " + result, SubsystemOperations.readResult(result)
                .asList()
                .isEmpty());

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(consoleAddress));
        verifyRemoved(kernelServices, consoleAddress);
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);

        // Add logger with the console-handler assigned, then attempt to remove the async-handler which should
        // result in a failure
        executeOperation(kernelServices, CompositeOperationBuilder.create()
                .addStep(SubsystemOperations.createAddOperation(consoleAddress))
                .addStep(addOp)
                .build().getOperation());

        // Attempt to remove the CONSOLE handler
        final ModelNode removeHandlerOp = SubsystemOperations.createOperation("remove-handler", address);
        removeHandlerOp.get("name").set("CONSOLE");
        executeOperationForFailure(kernelServices, removeHandlerOp);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(consoleAddress));
        verifyRemoved(kernelServices, consoleAddress);
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
    }

    private void testLogger(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createLoggerAddress(profileName, "org.jboss.as.logging").toModelNode();
        final ModelNode handlers = new ModelNode().setEmptyList().add("CONSOLE");

        // Add the logger
        final ModelNode addOp = SubsystemOperations.createAddOperation(address);
        executeOperation(kernelServices, addOp);

        // Add a console handler for handlers tests
        final ModelNode consoleAddress = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();
        executeOperation(kernelServices, SubsystemOperations.createAddOperation(consoleAddress));

        // Write each attribute and check the value
        testWriteCommonAttributes(kernelServices, address, handlers);
        testWrite(kernelServices, address, LoggerResourceDefinition.USE_PARENT_HANDLERS, false);

        // Undefine attributes
        testUndefineCommonAttributes(kernelServices, address);
        testUndefine(kernelServices, address, LoggerResourceDefinition.USE_PARENT_HANDLERS);

        // Test the add-handler operation
        ModelNode op = OperationBuilder.create(LoggerResourceDefinition.ADD_HANDLER_OPERATION, address)
                .addAttribute(CommonAttributes.HANDLER_NAME, "CONSOLE")
                .build();
        executeOperation(kernelServices, op);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, LoggerAttributes.HANDLERS.getName());
        ModelNode result = executeOperation(kernelServices, readOp);
        assertEquals(handlers, SubsystemOperations.readResult(result));

        // Test remove-handler operation
        op = SubsystemOperations.createOperation(LoggerResourceDefinition.REMOVE_HANDLER_OPERATION.getName(), address);
        op.get(CommonAttributes.HANDLER_NAME.getName()).set("CONSOLE");
        executeOperation(kernelServices, op);
        result = executeOperation(kernelServices, readOp);
        assertTrue("Handler CONSOLE should have been removed: " + result, SubsystemOperations.readResult(result)
                .asList()
                .isEmpty());

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(consoleAddress));
        verifyRemoved(kernelServices, consoleAddress);
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);

        // Add a logger with the console-handler assigned, then attempt to remove the async-handler which should
        // result in a failure
        executeOperation(kernelServices, CompositeOperationBuilder.create()
                .addStep(SubsystemOperations.createAddOperation(consoleAddress))
                .addStep(addOp)
                .build().getOperation());

        // Attempt to remove the CONSOLE handler
        final ModelNode removeHandlerOp = SubsystemOperations.createOperation("remove-handler", address);
        removeHandlerOp.get("name").set("CONSOLE");
        executeOperationForFailure(kernelServices, removeHandlerOp);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(consoleAddress));
        verifyRemoved(kernelServices, consoleAddress);
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
    }

    private void testWriteCommonAttributes(final KernelServices kernelServices, final ModelNode address, final ModelNode handlers) {
        testWrite(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC, "deny");
        testWrite(kernelServices, address, CommonAttributes.LEVEL, "INFO");
        testWrite(kernelServices, address, LoggerAttributes.HANDLERS, handlers);
    }

    private void testUndefineCommonAttributes(final KernelServices kernelServices, final ModelNode address) {
        testUndefine(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC);
        testUndefine(kernelServices, address, CommonAttributes.LEVEL);
        testUndefine(kernelServices, address, LoggerAttributes.HANDLERS);
    }
}
