/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.logging;

import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.client.helpers.Operations.createRemoveOperation;
import static org.jboss.as.subsystem.test.SubsystemOperations.OperationBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.handlers.AbstractHandlerDefinition;
import org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition;
import org.jboss.as.logging.handlers.ConsoleHandlerResourceDefinition;
import org.jboss.as.logging.handlers.FileHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SocketHandlerResourceDefinition;
import org.jboss.as.logging.handlers.Target;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.as.logging.loggers.LoggerResourceDefinition;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"MagicNumber", "SameParameterValue"})
public class HandlerOperationsTestCase extends AbstractOperationsTestCase {

    private static final String ENCODING = "UTF-8";

    private KernelServices kernelServices;

    @Before
    public void bootKernelServices() throws Exception {
        kernelServices = boot();
    }

    @After
    public void shutdown() {
        if (kernelServices != null) {
            kernelServices.shutdown();
        }
    }

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
        testConsoleHandler(kernelServices, null);
        testConsoleHandler(kernelServices, PROFILE);

        testFileHandler(kernelServices, null);
        testFileHandler(kernelServices, PROFILE);

        testPeriodicRotatingFileHandler(kernelServices, null);
        testPeriodicRotatingFileHandler(kernelServices, PROFILE);

        testPeriodicSizeRotatingFileHandler(kernelServices, null);
        testPeriodicSizeRotatingFileHandler(kernelServices, PROFILE);

        testSizeRotatingFileHandler(kernelServices, null);
        testSizeRotatingFileHandler(kernelServices, PROFILE);

        testSocketHandler(kernelServices, null);
        testSocketHandler(kernelServices, PROFILE);

        // Run these last as they put the server in reload-required, and the later
        // ones will not update runtime once that is done
        testAsyncHandler(kernelServices, null);
        testAsyncHandler(kernelServices, PROFILE);
    }

    @Test
    public void testFormatsNoColor() throws Exception {
        final Path logFile = LoggingTestEnvironment.get().getLogDir().resolve("formatter.log");
        // Delete the file if it exists
        Files.deleteIfExists(logFile);

        // Create a file handler
        final String fileHandlerName = "formatter-handler";
        final ModelNode handlerAddress = createFileHandlerAddress(fileHandlerName).toModelNode();
        ModelNode op = SubsystemOperations.createAddOperation(handlerAddress);
        op.get(CommonAttributes.LEVEL.getName()).set("INFO");
        op.get(CommonAttributes.ENCODING.getName()).set(ENCODING);
        op.get(CommonAttributes.FILE.getName()).get(PathResourceDefinition.PATH.getName()).set(logFile.toAbsolutePath().toString());
        op.get(CommonAttributes.AUTOFLUSH.getName()).set(true);
        op.get(FileHandlerResourceDefinition.FORMATTER.getName()).set("%s%n");
        executeOperation(kernelServices, op);

        // Create a logger
        final Logger logger = LogContext.getSystemLogContext().getLogger(HandlerOperationsTestCase.class.getName());
        final ModelNode loggerAddress = createLoggerAddress(logger.getName()).toModelNode();
        op = SubsystemOperations.createAddOperation(loggerAddress);
        op.get(LoggerResourceDefinition.USE_PARENT_HANDLERS.getName()).set(false);
        op.get(LoggerAttributes.HANDLERS.getName()).setEmptyList().add(fileHandlerName);
        executeOperation(kernelServices, op);

        // Log a few records
        logger.log(Level.INFO, "Test message 1");
        logger.log(Level.INFO, "Test message 2");

        // Read the file
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals("Number of lines logged and found in the file do not match", 2, lines.size());

        // Check the lines
        assertEquals("Test message 1", lines.get(0));
        assertEquals("Test message 2", lines.get(1));

        // Create a pattern formatter
        final ModelNode patternFormatterAddress = createPatternFormatterAddress("PATTERN").toModelNode();
        op = SubsystemOperations.createAddOperation(patternFormatterAddress);
        op.get(PatternFormatterResourceDefinition.PATTERN.getName()).set("[changed-pattern] %s%n");
        executeOperation(kernelServices, op);

        // The formatter will need to be undefined before the named-formatter can be written
        executeOperation(kernelServices, SubsystemOperations.createUndefineAttributeOperation(handlerAddress, FileHandlerResourceDefinition.FORMATTER));
        // Assign the pattern to the handler
        executeOperation(kernelServices, SubsystemOperations.createWriteAttributeOperation(handlerAddress, FileHandlerResourceDefinition.NAMED_FORMATTER, "PATTERN"));

        // Check that the formatter attribute was undefined
        op = SubsystemOperations.createReadAttributeOperation(handlerAddress, FileHandlerResourceDefinition.FORMATTER);
        op.get("include-defaults").set(false);
        ModelNode result = executeOperation(kernelServices, op);
        assertFalse("formatter attribute was not undefined after the change to a named-formatter", SubsystemOperations.readResult(result).isDefined());

        // Log some more records
        logger.log(Level.INFO, "Test message 3");
        logger.log(Level.INFO, "Test message 4");

        // Read the file
        lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals("Number of lines logged and found in the file do not match", 4, lines.size());

        // Check the lines
        assertTrue("Line logged does not match expected: 3", Arrays.equals("[changed-pattern] Test message 3".getBytes(ENCODING), lines.get(2).getBytes(ENCODING)));
        // Second line will start with the clear string, followed by the color string
        assertTrue("Line logged does not match expected: 4", Arrays.equals("[changed-pattern] Test message 4".getBytes(ENCODING), lines.get(3).getBytes(ENCODING)));

        // Remove the handler operation
        final ModelNode removeHandlerOp = SubsystemOperations.createOperation("remove-handler", loggerAddress);
        removeHandlerOp.get("name").set(fileHandlerName);

        // Finally clean everything up
        op = SubsystemOperations.CompositeOperationBuilder.create()
                .addStep(removeHandlerOp)
                .addStep(SubsystemOperations.createRemoveOperation(handlerAddress))
                .addStep(SubsystemOperations.createRemoveOperation(patternFormatterAddress))
                .addStep(SubsystemOperations.createRemoveOperation(loggerAddress))
                .build().getOperation();
        executeOperation(kernelServices, op);

    }

    /**
     * Tests a composite operation of undefining a {@code formatter} attribute and defining a {@code named-formatter}
     * attribute in a composite operation. These two specific attributes have strange behavior. If the
     * {@code named-formatter} is defined it removes the formatter, named the same as the handler, which was created
     * as part of the {@code undefine-attribute} operation of the {@code formatter} attribute.
     *
     */
    @Test
    public void testCompositeOperations() {
        final ModelNode address = createFileHandlerAddress("FILE").toModelNode();
        final String filename = "test-file.log";
        final String defaultFormatterName = "FILE" + PatternFormatterResourceDefinition.DEFAULT_FORMATTER_SUFFIX;

        // Add the handler
        ModelNode addOp = OperationBuilder.createAddOperation(address)
                .addAttribute(CommonAttributes.FILE, createFileValue("jboss.server.log.dir", filename))
                .build();
        executeOperation(kernelServices, addOp);
        final ModelNode patternFormatterAddress = createPatternFormatterAddress("PATTERN").toModelNode();
        addOp = SubsystemOperations.createAddOperation(patternFormatterAddress);
        addOp.get(PatternFormatterResourceDefinition.PATTERN.getName()).set("%d{HH:mm:ss,SSS} %-5p [%c] %s%e%n");
        executeOperation(kernelServices, addOp);

        // Create a composite operation to undefine
        final Operation op = CompositeOperationBuilder.create()
                .addStep(SubsystemOperations.createUndefineAttributeOperation(address, "formatter"))
                .addStep(SubsystemOperations.createWriteAttributeOperation(address, "named-formatter", "PATTERN"))
                .build();
        executeOperation(kernelServices, op.getOperation());

        // Get the log context configuration to validate what has been configured
        final LogContextConfiguration configuration = ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext());
        assertNotNull("Expected to find the configuration", configuration);
        assertFalse("Expected the default formatter named " + defaultFormatterName + " to be removed for the handler FILE",
                configuration.getFormatterNames().contains(defaultFormatterName));
        final HandlerConfiguration handlerConfiguration = configuration.getHandlerConfiguration("FILE");
        assertNotNull("Expected to find the configuration for the FILE handler", configuration);
        assertEquals("Expected the handler named FILE to use the PATTERN formatter", "PATTERN",
                handlerConfiguration.getFormatterName());

        // Undefine the named-formatter to ensure a formatter is created
        executeOperation(kernelServices, SubsystemOperations.createUndefineAttributeOperation(address, "named-formatter"));
        assertTrue("Expected the default formatter named " + defaultFormatterName + " to be added",
                configuration.getFormatterNames().contains(defaultFormatterName));
        assertEquals("Expected the handler named FILE to use the FILE formatter", defaultFormatterName,
                handlerConfiguration.getFormatterName());
    }

    @Test
    public void testAddHandlerComposite() {
        final ModelNode handlerAddress = createFileHandlerAddress("FILE").toModelNode();
        final String filename = "test-file-2.log";

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add the handler
        builder.addStep(OperationBuilder.createAddOperation(handlerAddress)
                .addAttribute(CommonAttributes.FILE, createFileValue("jboss.server.log.dir", filename))
                .build());

        // Create a formatter and add it
        final ModelNode patternFormatterAddress = createPatternFormatterAddress("PATTERN").toModelNode();
        builder.addStep(OperationBuilder.createAddOperation(patternFormatterAddress)
                .addAttribute(PatternFormatterResourceDefinition.PATTERN, "%d{HH:mm:ss,SSS} %-5p [%c] %s%e%n")
                .build());

        // Write the named-formatter
        builder.addStep(SubsystemOperations.createWriteAttributeOperation(handlerAddress, "named-formatter", "PATTERN"));

        // Create an async-handler
        final ModelNode asyncHandlerAddress = createAsyncHandlerAddress(null, "ASYNC").toModelNode();
        builder.addStep(OperationBuilder.createAddOperation(asyncHandlerAddress)
                .addAttribute(AsyncHandlerResourceDefinition.QUEUE_LENGTH, 100)
                .build());

        // Add the file-handler to the async-handler
        ModelNode addHandlerOp = SubsystemOperations.createOperation("add-handler", asyncHandlerAddress);
        addHandlerOp.get("name").set("FILE");
        builder.addStep(addHandlerOp);

        // Create a logger
        final ModelNode loggerAddress = createLoggerAddress("org.jboss.as.logging").toModelNode();
        builder.addStep(SubsystemOperations.createAddOperation(loggerAddress));

        // Use the add-handler operation to add the handler to the logger
        addHandlerOp = SubsystemOperations.createOperation("add-handler", loggerAddress);
        addHandlerOp.get("name").set("ASYNC");
        builder.addStep(addHandlerOp);

        executeOperation(kernelServices, builder.build().getOperation());

        // Get the log context configuration to validate what has been configured
        final LogContextConfiguration configuration = ConfigurationPersistence.getConfigurationPersistence(LogContext.getLogContext());
        assertNotNull("Expected to find the configuration", configuration);
        final HandlerConfiguration handlerConfiguration = configuration.getHandlerConfiguration("FILE");
        assertNotNull("Expected to find the configuration for the FILE handler", configuration);
        assertEquals("Expected the handler named FILE to use the PATTERN formatter", "PATTERN",
                handlerConfiguration.getFormatterName());
        final LoggerConfiguration loggerConfiguration = configuration.getLoggerConfiguration("org.jboss.as.logging");
        assertNotNull("Expected the logger configuration for org.jboss.as.logging to exist", loggerConfiguration);
        assertTrue("Expected the FILE handler to be assigned", loggerConfiguration.getHandlerNames().contains("ASYNC"));
    }

    private void testAsyncHandler(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createAsyncHandlerAddress(profileName, "async").toModelNode();
        final ModelNode subhandlers = new ModelNode().setEmptyList().add("CONSOLE");

        // Add the handler
        final ModelNode addOp = OperationBuilder.createAddOperation(address)
                .addAttribute(AsyncHandlerResourceDefinition.QUEUE_LENGTH, 10)
                .build();
        executeOperation(kernelServices, addOp);

        // Add a console handler for subhandler tests
        final ModelNode consoleAddress = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();
        executeOperation(kernelServices, SubsystemOperations.createAddOperation(consoleAddress));

        // Write each attribute and check the value
        testWrite(kernelServices, address, CommonAttributes.LEVEL, "INFO");
        testWrite(kernelServices, address, CommonAttributes.ENABLED, true);
        testWrite(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC, "deny");
        testWrite(kernelServices, address, AsyncHandlerResourceDefinition.OVERFLOW_ACTION, "BLOCK");
        testWrite(kernelServices, address, AsyncHandlerResourceDefinition.SUBHANDLERS, subhandlers);

        // Undefine attributes
        testUndefine(kernelServices, address, CommonAttributes.LEVEL);
        testUndefine(kernelServices, address, CommonAttributes.ENABLED);
        testUndefine(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC);
        testUndefine(kernelServices, address, AsyncHandlerResourceDefinition.OVERFLOW_ACTION);
        testUndefine(kernelServices, address, AsyncHandlerResourceDefinition.SUBHANDLERS);

        // Test the add-handler operation
        ModelNode op = OperationBuilder.create("add-handler", address)
                .addAttribute(CommonAttributes.HANDLER_NAME, "CONSOLE")
                .build();
        executeOperation(kernelServices, op);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, AsyncHandlerResourceDefinition.SUBHANDLERS);
        ModelNode result = executeOperation(kernelServices, readOp);
        assertEquals(subhandlers, SubsystemOperations.readResult(result));

        // Test remove-handler operation
        op = SubsystemOperations.createOperation("remove-handler", address);
        op.get(CommonAttributes.HANDLER_NAME.getName()).set("CONSOLE");
        executeOperation(kernelServices, op);
        result = executeOperation(kernelServices, readOp);
        assertTrue("Subhandler CONSOLE should have been removed: " + result, SubsystemOperations.readResult(result)
                .asList()
                .isEmpty());

        // Ensure the model doesn't contain any erroneous attributes
        op = SubsystemOperations.createReadResourceOperation(address);
        result = executeOperation(kernelServices, op);
        final ModelNode asyncHandlerResource = SubsystemOperations.readResult(result);
        validateResourceAttributes(asyncHandlerResource, Arrays.asList("enabled", "level", "filter-spec", "queue-length",
                "overflow-action", "subhandlers", "name", "filter"));
        // The name attribute should be the same as the last path element of the address
        assertEquals(asyncHandlerResource.get(CommonAttributes.NAME.getName()).asString(), PathAddress.pathAddress(address).getLastElement().getValue());

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(consoleAddress));
        verifyRemoved(kernelServices, consoleAddress);

        // This needs to execute after all other operations on the async-handler as it will put the state into reload
        // required.
        testWrite(kernelServices, address, AsyncHandlerResourceDefinition.QUEUE_LENGTH, 20);
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);

        // Add an async-handler with the console-handler assigned, then attempt to remove the async-handler which should
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

    private void testConsoleHandler(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();

        // Add the handler
        final ModelNode addOp = SubsystemOperations.createAddOperation(address);
        executeOperation(kernelServices, addOp);

        // Write each attribute and check the value
        testWriteCommonAttributes(kernelServices, address);
        testWrite(kernelServices, address, CommonAttributes.AUTOFLUSH, false);
        for (Target target : Target.values()) {
            testWrite(kernelServices, address, ConsoleHandlerResourceDefinition.TARGET, target.toString());
        }

        // Undefine attributes
        testUndefineCommonAttributes(kernelServices, address);
        testUndefine(kernelServices, address, CommonAttributes.AUTOFLUSH);
        testUndefine(kernelServices, address, ConsoleHandlerResourceDefinition.TARGET);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
    }

    private void testFileHandler(final KernelServices kernelServices, final String profileName) throws Exception {
        final ModelNode address = createFileHandlerAddress(profileName, "FILE").toModelNode();
        final String filename = "test-file.log";
        final String newFilename = "new-test-file.log";

        // Add the handler
        final ModelNode addOp = OperationBuilder.createAddOperation(address)
                .addAttribute(CommonAttributes.FILE, createFileValue("jboss.server.log.dir", filename))
                .build();
        executeOperation(kernelServices, addOp);
        verifyFile(filename);

        // Write each attribute and check the value
        testWriteCommonAttributes(kernelServices, address);
        testWrite(kernelServices, address, CommonAttributes.APPEND, false);
        testWrite(kernelServices, address, CommonAttributes.AUTOFLUSH, false);

        final ModelNode newFile = createFileValue("jboss.server.log.dir", newFilename);
        testWrite(kernelServices, address, CommonAttributes.FILE, newFile);
        verifyFile(newFilename);

        // Undefine attributes
        testUndefineCommonAttributes(kernelServices, address);
        testUndefine(kernelServices, address, CommonAttributes.APPEND);
        testUndefine(kernelServices, address, CommonAttributes.AUTOFLUSH);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
        removeFile(filename);
        removeFile(newFilename);

        testCommonFileOperations(kernelServices, address);
    }

    private void testPeriodicRotatingFileHandler(final KernelServices kernelServices, final String profileName) throws Exception {
        final ModelNode address = createPeriodicRotatingFileHandlerAddress(profileName, "FILE").toModelNode();
        final String filename = "test-file.log";
        final String newFilename = "new-test-file.log";

        // Add the handler
        final ModelNode addOp = OperationBuilder.createAddOperation(address)
                .addAttribute(CommonAttributes.FILE, createFileValue("jboss.server.log.dir", filename))
                .addAttribute(PeriodicHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd")
                .build();
        executeOperation(kernelServices, addOp);
        verifyFile(filename);

        // Write each attribute and check the value
        testWriteCommonAttributes(kernelServices, address);
        testWrite(kernelServices, address, CommonAttributes.APPEND, false);
        testWrite(kernelServices, address, CommonAttributes.AUTOFLUSH, false);

        final ModelNode newFile = createFileValue("jboss.server.log.dir", newFilename);
        testWrite(kernelServices, address, CommonAttributes.FILE, newFile);
        verifyFile(newFilename);

        testWrite(kernelServices, address, PeriodicHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd-HH");
        testWrite(kernelServices, address, PeriodicHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd-HH.zip");
        testWrite(kernelServices, address, PeriodicHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd-HH.gz");

        // Undefine attributes
        testUndefineCommonAttributes(kernelServices, address);
        testUndefine(kernelServices, address, CommonAttributes.APPEND);
        testUndefine(kernelServices, address, CommonAttributes.AUTOFLUSH);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
        removeFile(filename);
        removeFile(newFilename);

        testCommonFileOperations(kernelServices, address);
    }

    private void testPeriodicSizeRotatingFileHandler(final KernelServices kernelServices, final String profileName) throws Exception {
        final ModelNode address = createPeriodicSizeRotatingFileHandlerAddress(profileName, "FILE").toModelNode();
        final String filename = "test-file.log";
        final String newFilename = "new-test-file.log";

        // Add the handler
        final ModelNode addOp = OperationBuilder.createAddOperation(address)
                .addAttribute(CommonAttributes.FILE, createFileValue("jboss.server.log.dir", filename))
                .addAttribute(PeriodicHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd")
                .build();
        executeOperation(kernelServices, addOp);
        verifyFile(filename);

        // Write each attribute and check the value
        testWriteCommonAttributes(kernelServices, address);
        testWrite(kernelServices, address, CommonAttributes.APPEND, false);
        testWrite(kernelServices, address, CommonAttributes.AUTOFLUSH, false);

        final ModelNode newFile = createFileValue("jboss.server.log.dir", newFilename);
        testWrite(kernelServices, address, CommonAttributes.FILE, newFile);
        verifyFile(newFilename);

        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX, 20);
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.ROTATE_SIZE, "50m");
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd-HH");
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd-HH.zip");
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd-HH.gz");

        // Undefine attributes
        testUndefineCommonAttributes(kernelServices, address);
        testUndefine(kernelServices, address, CommonAttributes.APPEND);
        testUndefine(kernelServices, address, CommonAttributes.AUTOFLUSH);
        testUndefine(kernelServices, address, SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
        removeFile(filename);
        removeFile(newFilename);

        testCommonFileOperations(kernelServices, address);
    }

    private void testSizeRotatingFileHandler(final KernelServices kernelServices, final String profileName) throws Exception {
        final ModelNode address = createSizeRotatingFileHandlerAddress(profileName, "FILE").toModelNode();
        final String filename = "test-file.log";
        final String newFilename = "new-test-file.log";

        // Add the handler
        final ModelNode addOp = OperationBuilder.createAddOperation(address)
                .addAttribute(CommonAttributes.FILE, createFileValue("jboss.server.log.dir", filename))
                .build();
        executeOperation(kernelServices, addOp);
        verifyFile(filename);

        // Write each attribute and check the value
        testWriteCommonAttributes(kernelServices, address);
        testWrite(kernelServices, address, CommonAttributes.APPEND, false);
        testWrite(kernelServices, address, CommonAttributes.AUTOFLUSH, false);

        final ModelNode newFile = createFileValue("jboss.server.log.dir", newFilename);
        testWrite(kernelServices, address, CommonAttributes.FILE, newFile);
        verifyFile(newFilename);

        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX, 20);
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.ROTATE_SIZE, "50m");
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.SUFFIX, ".yyyy-MM-dd'T'HH:mm:ssZ");
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.SUFFIX, ".zip");
        testWrite(kernelServices, address, SizeRotatingHandlerResourceDefinition.SUFFIX, ".gz");

        // Undefine attributes
        testUndefineCommonAttributes(kernelServices, address);
        testUndefine(kernelServices, address, CommonAttributes.APPEND);
        testUndefine(kernelServices, address, CommonAttributes.AUTOFLUSH);
        testUndefine(kernelServices, address, SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX);
        testUndefine(kernelServices, address, SizeRotatingHandlerResourceDefinition.SUFFIX);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
        removeFile(filename);
        removeFile(newFilename);

        testCommonFileOperations(kernelServices, address);
    }

    private void testSocketHandler(final KernelServices kernelServices, final String profileName) {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        final PathAddress formatterAddress = createPatternFormatterAddress(profileName, "log-pattern");
        final ModelNode formatterAddOp = SubsystemOperations.createAddOperation(formatterAddress.toModelNode());
        formatterAddOp.get("pattern").set("[log-server] %d{HH:mm:ss,SSS} %-5p [%c] %s%e%n");
        builder.addStep(formatterAddOp);
        final ModelNode jsonFormatterAddress = SUBSYSTEM_ADDRESS.append("json-formatter", "json").toModelNode();
        builder.addStep(Operations.createAddOperation(jsonFormatterAddress));

        final ModelNode address = createAddress(profileName, "socket-handler", "log-server-handler").toModelNode();

        // Add the handler
        final ModelNode addOp = SubsystemOperations.createAddOperation(address);
        addOp.get("named-formatter").set(formatterAddress.getLastElement().getValue());
        addOp.get("outbound-socket-binding-ref").set("log-server");
        builder.addStep(addOp);

        executeOperation(kernelServices, builder.build().getOperation());

        // Write each attribute and check the value
        testWrite(kernelServices, address, CommonAttributes.AUTOFLUSH, false);
        testWrite(kernelServices, address, SocketHandlerResourceDefinition.BLOCK_ON_RECONNECT, true);
        testWrite(kernelServices, address, CommonAttributes.ENABLED, true);
        testWrite(kernelServices, address, CommonAttributes.ENCODING, ENCODING);
        testWrite(kernelServices, address, CommonAttributes.LEVEL, "INFO");
        testWrite(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC, "deny");
        testWrite(kernelServices, address, SocketHandlerResourceDefinition.PROTOCOL, "UDP");

        // Undefine attributes
        testUndefine(kernelServices, address, CommonAttributes.AUTOFLUSH);
        testUndefine(kernelServices, address, SocketHandlerResourceDefinition.BLOCK_ON_RECONNECT);
        testUndefine(kernelServices, address, CommonAttributes.ENABLED);
        testUndefine(kernelServices, address, CommonAttributes.ENCODING);
        testUndefine(kernelServices, address, CommonAttributes.LEVEL);
        testUndefine(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC);
        testUndefine(kernelServices, address, SocketHandlerResourceDefinition.PROTOCOL);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(formatterAddress.toModelNode()));
        verifyRemoved(kernelServices, formatterAddress.toModelNode());
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(jsonFormatterAddress));
        verifyRemoved(kernelServices, jsonFormatterAddress);
    }

    // TODO (jrp) do syslog? only concern is will it active it

    private void testWriteCommonAttributes(final KernelServices kernelServices, final ModelNode address) {
        testWrite(kernelServices, address, CommonAttributes.LEVEL, "INFO");
        testWrite(kernelServices, address, CommonAttributes.ENABLED, true);
        testWrite(kernelServices, address, CommonAttributes.ENCODING, ENCODING);
        testWrite(kernelServices, address, AbstractHandlerDefinition.FORMATTER, "[test] %d{HH:mm:ss,SSS} %-5p [%c] %s%e%n");
        testWrite(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC, "deny");

        // Add a pattern-formatter
        addPatternFormatter(kernelServices, LoggingProfileOperations.getLoggingProfileName(PathAddress.pathAddress(address)), "PATTERN");
        // The formatter will need to be undefined before the named-formatter can be written
        testUndefine(kernelServices, address, AbstractHandlerDefinition.FORMATTER);
        testWrite(kernelServices, address, AbstractHandlerDefinition.NAMED_FORMATTER, "PATTERN");
    }

    private void testUndefineCommonAttributes(final KernelServices kernelServices, final ModelNode address) {
        testUndefine(kernelServices, address, CommonAttributes.LEVEL);
        testUndefine(kernelServices, address, CommonAttributes.ENABLED);
        testUndefine(kernelServices, address, CommonAttributes.ENCODING);
        testUndefine(kernelServices, address, AbstractHandlerDefinition.FORMATTER);
        testUndefine(kernelServices, address, AbstractHandlerDefinition.FILTER_SPEC);

        // Remove a pattern-formatter
        testUndefine(kernelServices, address, AbstractHandlerDefinition.NAMED_FORMATTER);
        removePatternFormatter(kernelServices, LoggingProfileOperations.getLoggingProfileName(PathAddress.pathAddress(address)), "PATTERN");
    }

    private void addPatternFormatter(final KernelServices kernelServices, final String profileName, final String name) {
        final ModelNode address = createPatternFormatterAddress(profileName, name).toModelNode();
        final ModelNode op = createAddOperation(address);
        op.get(PatternFormatterResourceDefinition.PATTERN.getName()).set("[test-pattern] %d{HH:mm:ss,SSS} %-5p [%c] %s%e%n");
        executeOperation(kernelServices, op);
    }

    private void removePatternFormatter(final KernelServices kernelServices, final String profileName, final String name) {
        final ModelNode address = createPatternFormatterAddress(profileName, name).toModelNode();
        final ModelNode op = createRemoveOperation(address);
        executeOperation(kernelServices, op);
    }

    private void testCommonFileOperations(final KernelServices kernelServices, final ModelNode address) throws Exception {
        // Create a directory a new directory
        final LoggingTestEnvironment env = LoggingTestEnvironment.get();
        final Path dir = env.getLogDir().toAbsolutePath().resolve("file-dir");
        Files.createDirectories(dir);
        // Attempt to add a file-handler with the dir for the path
        ModelNode op = OperationBuilder.createAddOperation(address)
                .addAttribute(CommonAttributes.FILE, createFileValue(null, dir.toString()))
                .build();
        executeOperationForFailure(kernelServices, op);

        // Clean-up
        Files.deleteIfExists(dir);
    }
}
