/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import static org.jboss.as.subsystem.test.SubsystemOperations.OperationBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
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
public class LoggerLegacyOperationsTestCase extends AbstractOperationsTestCase {

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

        // Add a console handler for handlers tests
        final ModelNode consoleAddress = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();
        executeOperation(kernelServices, SubsystemOperations.createAddOperation(consoleAddress));

        // Add the handler
        final ModelNode addOp = OperationBuilder.create(RootLoggerResourceDefinition.ADD_ROOT_LOGGER_DEFINITION, address)
                .addAttribute(CommonAttributes.LEVEL, "WARN")
                .addAttribute(AbstractHandlerDefinition.FILTER_SPEC, "accept")
                .addAttribute(LoggerAttributes.HANDLERS, handlers)
                .build();
        executeOperation(kernelServices, addOp);

        // Test common legacy attributes
        testWriteCommonAttributes(kernelServices, address);
        testUndefineCommonAttributes(kernelServices, address);

        // Change the level
        ModelNode op = OperationBuilder.create(RootLoggerResourceDefinition.CHANGE_LEVEL_OPERATION, address)
                .addAttribute(CommonAttributes.LEVEL, "DEBUG")
                .build();
        executeOperation(kernelServices, op);
        // Check the level
        ModelNode result = executeOperation(kernelServices, SubsystemOperations.createReadAttributeOperation(address, CommonAttributes.LEVEL));
        assertEquals("DEBUG", SubsystemOperations.readResultAsString(result));

        // Test remove-handler operation
        op = SubsystemOperations.createOperation(RootLoggerResourceDefinition.LEGACY_REMOVE_HANDLER_OPERATION
                .getName(), address);
        op.get(CommonAttributes.HANDLER_NAME.getName()).set("CONSOLE");
        executeOperation(kernelServices, op);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, LoggerAttributes.HANDLERS
                .getName());
        result = executeOperation(kernelServices, readOp);
        assertTrue("Handler CONSOLE should have been removed: " + result, SubsystemOperations.readResult(result)
                .asList()
                .isEmpty());

        // Test the add-handler operation
        op = OperationBuilder.create(RootLoggerResourceDefinition.LEGACY_ADD_HANDLER_OPERATION, address)
                .addAttribute(CommonAttributes.HANDLER_NAME, "CONSOLE")
                .build();
        executeOperation(kernelServices, op);
        result = executeOperation(kernelServices, readOp);
        assertEquals(handlers, SubsystemOperations.readResult(result));

        // Clean-up

        // Remove the console handler from the root logger before the handler is removed
        final ModelNode removeHandlerOp = SubsystemOperations.createOperation("remove-handler", address);
        removeHandlerOp.get("name").set("CONSOLE");
        executeOperation(kernelServices, removeHandlerOp);

        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(consoleAddress));
        verifyRemoved(kernelServices, consoleAddress);
        executeOperation(kernelServices, SubsystemOperations.createOperation(
                RootLoggerResourceDefinition.ROOT_LOGGER_REMOVE_OPERATION.getName(), address));
        verifyRemoved(kernelServices, address);
    }

    private void testLogger(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createLoggerAddress(profileName, "org.jboss.as.logging").toModelNode();
        final ModelNode handlers = new ModelNode().setEmptyList().add("CONSOLE");

        // Add a console handler for handlers tests
        final ModelNode consoleAddress = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();
        executeOperation(kernelServices, SubsystemOperations.createAddOperation(consoleAddress));

        // Add the logger
        final ModelNode addOp = SubsystemOperations.createAddOperation(address);
        executeOperation(kernelServices, addOp);

        // Test common legacy attributes
        testWriteCommonAttributes(kernelServices, address);
        testUndefineCommonAttributes(kernelServices, address);

        // Change the level
        ModelNode op = OperationBuilder.create(LoggerResourceDefinition.CHANGE_LEVEL_OPERATION, address)
                .addAttribute(CommonAttributes.LEVEL, "DEBUG")
                .build();
        executeOperation(kernelServices, op);
        // Check the level
        ModelNode result = executeOperation(kernelServices, SubsystemOperations.createReadAttributeOperation(address, CommonAttributes.LEVEL));
        assertEquals("DEBUG", SubsystemOperations.readResultAsString(result));

        // Test the add-handler operation
        op = OperationBuilder.create(LoggerResourceDefinition.LEGACY_ADD_HANDLER_OPERATION, address)
                .addAttribute(CommonAttributes.HANDLER_NAME, "CONSOLE")
                .build();
        executeOperation(kernelServices, op);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, LoggerAttributes.HANDLERS
                .getName());
        result = executeOperation(kernelServices, readOp);
        assertEquals(handlers, SubsystemOperations.readResult(result));

        // Test remove-handler operation
        op = SubsystemOperations.createOperation(LoggerResourceDefinition.LEGACY_REMOVE_HANDLER_OPERATION
                .getName(), address);
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
    }

    private void testWriteCommonAttributes(final KernelServices kernelServices, final ModelNode address) {
        // filter attribute not on logging profiles
        if (!LoggingProfileOperations.isLoggingProfileAddress(PathAddress.pathAddress(address))) {
            final ModelNode filter = new ModelNode().setEmptyObject();
            filter.get(CommonAttributes.ACCEPT.getName()).set(true);
            testWrite(kernelServices, address, CommonAttributes.FILTER, filter);
            // filter-spec should be "accept"
            final ModelNode op = SubsystemOperations.createReadAttributeOperation(address, AbstractHandlerDefinition.FILTER_SPEC);
            final ModelNode result = executeOperation(kernelServices, op);
            assertEquals("accept", SubsystemOperations.readResultAsString(result));
        }
    }

    private void testUndefineCommonAttributes(final KernelServices kernelServices, final ModelNode address) {
        if (!LoggingProfileOperations.isLoggingProfileAddress(PathAddress.pathAddress(address))) {
            testUndefine(kernelServices, address, CommonAttributes.FILTER);
            // filter-spec should be undefined
            final ModelNode op = SubsystemOperations.createReadAttributeOperation(address, AbstractHandlerDefinition.FILTER_SPEC);
            final ModelNode result = executeOperation(kernelServices, op);
            assertFalse(SubsystemOperations.readResult(result).isDefined());
        }
    }
}
