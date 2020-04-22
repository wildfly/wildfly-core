/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.CRC32;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.handlers.AbstractHandlerDefinition;
import org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition;
import org.jboss.as.logging.handlers.FileHandlerResourceDefinition;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.as.logging.loggers.LoggerResourceDefinition;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
// TODO (jrp) all these operations should be tested on loggers as well as root loggers
@SuppressWarnings("SameParameterValue")
public class LoggingOperationsSubsystemTestCase extends AbstractLoggingSubsystemTest {

    private static final String PROFILE = "testProfile";
    private static final String FQCN = LoggingOperationsSubsystemTestCase.class.getName();
    private static final Level[] LEVELS = {
            org.jboss.logmanager.Level.FATAL,
            org.jboss.logmanager.Level.ERROR,
            org.jboss.logmanager.Level.WARN,
            org.jboss.logmanager.Level.INFO,
            org.jboss.logmanager.Level.DEBUG,
            org.jboss.logmanager.Level.TRACE,

    };

    private static Path logDir;

    private KernelServices kernelServices;

    @BeforeClass
    public static void setupLoggingDir() throws Exception {
        logDir = LoggingTestEnvironment.get().getLogDir();
        clearDirectory(logDir);
    }

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

    @After
    @Override
    public void clearLogContext() throws Exception {
        super.clearLogContext();
        final LoggingProfileContextSelector contextSelector = LoggingProfileContextSelector.getInstance();
        if (contextSelector.exists(PROFILE)) {
            contextSelector.get(PROFILE).close();
            contextSelector.remove(PROFILE);
        }
    }

    @Override
    protected void standardSubsystemTest(final String configId) {
        // do nothing as this is not a subsystem parsing test
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/operations.xml");
    }

    @Test
    public void testChangeRootLogLevel() throws Exception {
        testChangeRootLogLevel(null);
        testChangeRootLogLevel(PROFILE);
    }

    @Test
    public void testSetRootLogger() throws Exception {
        testSetRootLogger(null);
        testSetRootLogger(PROFILE);
    }

    @Test
    public void testAddRemoveFileHandler() throws Exception {
        testAddRemoveFileHandler(null);
        testAddRemoveFileHandler(PROFILE);
    }

    @Test
    public void testDisableHandler() throws Exception {
        testDisableHandler(null, false);
        testDisableHandler(PROFILE, false);
    }

    @Test
    public void testLegacyDisableHandler() throws Exception {
        testDisableHandler(null, true);
        testDisableHandler(PROFILE, true);
    }

    @Test
    public void testPatternFormatter() throws Exception {
        testPatternFormatter(null);
        testPatternFormatter(PROFILE);
    }

    @Test
    public void testCompositeOperations() {
        testCompositeOperations(null);
        testCompositeOperations(PROFILE);
    }

    @Test
    public void testLegacyFilters() throws Exception {
        final String fileHandlerName = "test-file-handler";

        // add new file logger so we can track logged messages
        final Path logFile = createLogFile();
        final ModelNode handlerAddress = createFileHandlerAddress(fileHandlerName).toModelNode();
        addFileHandler(kernelServices, null, fileHandlerName, org.jboss.logmanager.Level.TRACE, logFile, true);
        // Write legacy filters
        for (Map.Entry<String, ModelNode> entry : FilterConversionTestCase.MAP.entrySet()) {
            // Validate the write-attribute operation
            ModelNode op = SubsystemOperations.createWriteAttributeOperation(handlerAddress, CommonAttributes.FILTER, entry.getValue());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(handlerAddress, LoggerAttributes.FILTER_SPEC);
            String filterSpecResult = SubsystemOperations.readResultAsString(executeOperation(kernelServices, op));
            assertEquals(entry.getKey(), filterSpecResult);

            // Validate an add operation
            final ModelNode tempHandlerAddress = createConsoleHandlerAddress("temp").toModelNode();
            op = SubsystemOperations.createAddOperation(tempHandlerAddress);
            op.get(CommonAttributes.FILTER.getName()).set(entry.getValue());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(tempHandlerAddress, LoggerAttributes.FILTER_SPEC);
            filterSpecResult = SubsystemOperations.readResultAsString(executeOperation(kernelServices, op));
            assertEquals(entry.getKey(), filterSpecResult);
            // Remove the temp handler
            op = SubsystemOperations.createRemoveOperation(tempHandlerAddress, true);
            executeOperation(kernelServices, op);

            // Add to a logger
            final ModelNode loggerAddress = createLoggerAddress("test-logger").toModelNode();
            op = SubsystemOperations.createAddOperation(loggerAddress);
            op.get(CommonAttributes.FILTER.getName()).set(entry.getValue());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(loggerAddress, LoggerAttributes.FILTER_SPEC);
            filterSpecResult = SubsystemOperations.readResultAsString(executeOperation(kernelServices, op));
            assertEquals(entry.getKey(), filterSpecResult);

            // Remove the attribute
            op = SubsystemOperations.createUndefineAttributeOperation(loggerAddress, LoggerAttributes.FILTER_SPEC);
            executeOperation(kernelServices, op);
            op = SubsystemOperations.createReadAttributeOperation(loggerAddress, CommonAttributes.FILTER);
            // Filter and filter spec should be undefined
            assertEquals("Filter was not undefined", SubsystemOperations.UNDEFINED, SubsystemOperations.readResult(executeOperation(kernelServices, op)));
            op = SubsystemOperations.createReadAttributeOperation(loggerAddress, LoggerAttributes.FILTER_SPEC);
            assertEquals("Filter was not undefined", SubsystemOperations.UNDEFINED, SubsystemOperations.readResult(executeOperation(kernelServices, op)));

            // Test writing the attribute to the logger
            op = SubsystemOperations.createWriteAttributeOperation(loggerAddress, CommonAttributes.FILTER, entry.getValue());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(loggerAddress, LoggerAttributes.FILTER_SPEC);
            filterSpecResult = SubsystemOperations.readResultAsString(executeOperation(kernelServices, op));
            assertEquals(entry.getKey(), filterSpecResult);

            // Remove the logger
            op = SubsystemOperations.createRemoveOperation(loggerAddress, true);
            executeOperation(kernelServices, op);
        }

        // Write new filters
        for (Map.Entry<String, ModelNode> entry : FilterConversionTestCase.MAP.entrySet()) {
            final ModelNode filterModel = FilterConversionTestCase.removeUndefined(entry.getValue());
            // Write to a handler
            ModelNode op = SubsystemOperations.createWriteAttributeOperation(handlerAddress, LoggerAttributes.FILTER_SPEC, entry.getKey());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(handlerAddress, CommonAttributes.FILTER);
            ModelNode filterResult = SubsystemOperations.readResult(executeOperation(kernelServices, op));
            ModelTestUtils.compare(filterModel, filterResult);

            // Validate an add operation
            final ModelNode tempHandlerAddress = createConsoleHandlerAddress("temp").toModelNode();
            op = SubsystemOperations.createAddOperation(tempHandlerAddress);
            op.get(LoggerAttributes.FILTER_SPEC.getName()).set(entry.getKey());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(tempHandlerAddress, CommonAttributes.FILTER);
            filterResult = SubsystemOperations.readResult(executeOperation(kernelServices, op));
            ModelTestUtils.compare(filterModel, filterResult);
            // Remove the temp handler
            op = SubsystemOperations.createRemoveOperation(tempHandlerAddress, true);
            executeOperation(kernelServices, op);

            // Add to a logger
            final ModelNode loggerAddress = createLoggerAddress("test-logger").toModelNode();
            op = SubsystemOperations.createAddOperation(loggerAddress);
            op.get(LoggerAttributes.FILTER_SPEC.getName()).set(entry.getKey());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(loggerAddress, CommonAttributes.FILTER);
            filterResult = SubsystemOperations.readResult(executeOperation(kernelServices, op));
            ModelTestUtils.compare(filterModel, filterResult);

            // Test writing the attribute to the logger
            op = SubsystemOperations.createWriteAttributeOperation(loggerAddress, LoggerAttributes.FILTER_SPEC, entry.getKey());
            executeOperation(kernelServices, op);
            // Read the current value
            op = SubsystemOperations.createReadAttributeOperation(loggerAddress, CommonAttributes.FILTER);
            filterResult = SubsystemOperations.readResult(executeOperation(kernelServices, op));
            ModelTestUtils.compare(filterModel, filterResult);

            // Remove the logger
            op = SubsystemOperations.createRemoveOperation(loggerAddress, true);
            executeOperation(kernelServices, op);
        }
        removeFileHandler(kernelServices, null, fileHandlerName, true);
    }

    @Test
    public void testLoggingProfile() throws Exception {
        final String handlerName = "test-file-handler";

        final Path logFile = createLogFile();
        final Path profileLogFile = createLogFile("profile.log");
        final ModelNode handlerAddress = createFileHandlerAddress(handlerName).toModelNode();
        final ModelNode profileHandlerAddress = createFileHandlerAddress(PROFILE, handlerName).toModelNode();

        // Add handlers
        addFileHandler(kernelServices, null, handlerName, org.jboss.logmanager.Level.INFO, logFile, true);
        addFileHandler(kernelServices, PROFILE, handlerName, org.jboss.logmanager.Level.INFO, profileLogFile, true);

        // Change the format
        ModelNode op = SubsystemOperations.createReadAttributeOperation(handlerAddress, AbstractHandlerDefinition.FORMATTER);
        final String defaultHandlerFormat = SubsystemOperations.readResultAsString(executeOperation(kernelServices, op));
        op = SubsystemOperations.createReadAttributeOperation(profileHandlerAddress, AbstractHandlerDefinition.FORMATTER);
        final String defaultProfileHandlerFormat = SubsystemOperations.readResultAsString(executeOperation(kernelServices, op));
        op = SubsystemOperations.createWriteAttributeOperation(handlerAddress, AbstractHandlerDefinition.FORMATTER, "%m%n");
        executeOperation(kernelServices, op);
        op = SubsystemOperations.createWriteAttributeOperation(profileHandlerAddress, AbstractHandlerDefinition.FORMATTER, "%m%n");
        executeOperation(kernelServices, op);

        // Log with and without profile
        final String msg = "This is a test message";
        doLog(null, LEVELS, msg);
        doLog(PROFILE, LEVELS, msg);

        // Reset the formatters
        op = SubsystemOperations.createWriteAttributeOperation(handlerAddress, AbstractHandlerDefinition.FORMATTER, defaultHandlerFormat);
        executeOperation(kernelServices, op);
        op = SubsystemOperations.createWriteAttributeOperation(profileHandlerAddress, AbstractHandlerDefinition.FORMATTER, defaultProfileHandlerFormat);
        executeOperation(kernelServices, op);

        // Remove the handler
        removeFileHandler(kernelServices, null, handlerName, true);
        removeFileHandler(kernelServices, PROFILE, handlerName, true);

        // Read the files to a string
        final List<String> result = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        final List<String> profileResult = Files.readAllLines(profileLogFile, StandardCharsets.UTF_8);

        // Check generated log file
        assertTrue(result.contains(msg));
        assertTrue(profileResult.contains(msg));

        // The contents of the files should match
        assertEquals(String.format("Contents don't match: %nResult:%n%s%nProfileResult%n%s", result, profileResult), result, profileResult);
    }

    @Test
    public void testRemoveReAdd() {
        // Get all add ops for the current subsystem
        final ModelNode addOps = SubsystemOperations.readResult(executeOperation(kernelServices, createDescribeOperation()));

        // Remove the subsystem
        executeOperation(kernelServices, SubsystemOperations
                .createRemoveOperation(SubsystemOperations.createAddress(ClientConstants.SUBSYSTEM, "logging")));

        // Create a composite operation to re-add the subsystem
        final SubsystemOperations.CompositeOperationBuilder builder = SubsystemOperations.CompositeOperationBuilder.create();
        for (ModelNode addOp : addOps.asList()) {
            builder.addStep(addOp);
        }
        executeOperation(kernelServices, builder.build().getOperation());
    }

    private void testChangeRootLogLevel(final String loggingProfile) throws Exception {
        final String fileHandlerName = "test-file-handler";

        // add new file logger so we can track logged messages
        final Path logFile = createLogFile();
        addFileHandler(kernelServices, loggingProfile, fileHandlerName, org.jboss.logmanager.Level.TRACE, logFile, true);

        final Level[] levels = {
                org.jboss.logmanager.Level.FATAL,
                org.jboss.logmanager.Level.ERROR,
                org.jboss.logmanager.Level.WARN,
                org.jboss.logmanager.Level.INFO,
                org.jboss.logmanager.Level.DEBUG,
                org.jboss.logmanager.Level.TRACE
        };
        final Map<Level, Integer> levelOrd = new HashMap<>();
        levelOrd.put(org.jboss.logmanager.Level.FATAL, 0);
        levelOrd.put(org.jboss.logmanager.Level.ERROR, 1);
        levelOrd.put(org.jboss.logmanager.Level.WARN, 2);
        levelOrd.put(org.jboss.logmanager.Level.INFO, 3);
        levelOrd.put(org.jboss.logmanager.Level.DEBUG, 4);
        levelOrd.put(org.jboss.logmanager.Level.TRACE, 5);

        // log messages on all levels with different root logger level settings
        final ModelNode address = createRootLoggerAddress(loggingProfile).toModelNode();
        for (Level level : levels) {
            // change root log level
            final ModelNode op = SubsystemOperations.createWriteAttributeOperation(address, CommonAttributes.LEVEL, level.getName());
            executeOperation(kernelServices, op);
            doLog(loggingProfile, levels, "RootLoggerTestCaseTST %s", level);
        }

        // Remove the handler
        removeFileHandler(kernelServices, loggingProfile, fileHandlerName, true);

        // go through logged messages - test that with each root logger level settings
        // message with equal priority and also messages with all higher
        // priorities were logged

        final boolean[][] logFound = new boolean[levelOrd.size()][levelOrd.size()];

        final List<String> logLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        for (String line : logLines) {
            if (!line.contains("RootLoggerTestCaseTST")) continue; // not our log
            final String[] words = line.split("\\s+");
            try {
                final Level lineLogLevel = Level.parse(words[1]);
                final Level rootLogLevel = Level.parse(words[5]);
                final int producedLevel = levelOrd.get(lineLogLevel);
                final int loggedLevel = levelOrd.get(rootLogLevel);
                assertTrue(String.format("Produced level(%s) greater than logged level (%s)", lineLogLevel, rootLogLevel), producedLevel <= loggedLevel);
                logFound[producedLevel][loggedLevel] = true;
            } catch (Exception e) {
                throw new Exception("Unexpected log:" + line);
            }
        }
        for (Level level : levels) {
            final int rl = levelOrd.get(level);
            for (int ll = 0; ll <= rl; ll++) assertTrue(logFound[ll][rl]);
        }

    }

    private void testSetRootLogger(final String loggingProfile) throws Exception {
        final String fileHandlerName = "test-file-handler";

        // Add new file logger so we can test root logger change
        final Path logFile = createLogFile();
        addFileHandler(kernelServices, loggingProfile, fileHandlerName, org.jboss.logmanager.Level.INFO, logFile, false);

        // Read root logger
        final ModelNode rootLoggerAddress = createRootLoggerAddress(loggingProfile).toModelNode();
        ModelNode op = SubsystemOperations.createOperation(ClientConstants.READ_RESOURCE_OPERATION, rootLoggerAddress);
        final ModelNode rootLoggerResult = executeOperation(kernelServices, op);
        final List<String> handlers = modelNodeAsStringList(rootLoggerResult.get(LoggerAttributes.HANDLERS.getName()));

        // Remove the root logger
        op = SubsystemOperations.createRemoveOperation(rootLoggerAddress);
        executeOperation(kernelServices, op);

        // Set a new root logger
        op = SubsystemOperations.createOperation(ModelDescriptionConstants.ADD, rootLoggerAddress);
        op.get(CommonAttributes.LEVEL.getName()).set(rootLoggerResult.get(CommonAttributes.LEVEL.getName()));
        for (String handler : handlers) op.get(LoggerAttributes.HANDLERS.getName()).add(handler);
        op.get(LoggerAttributes.HANDLERS.getName()).add(fileHandlerName);
        executeOperation(kernelServices, op);
        doLog(loggingProfile, LEVELS, "Test123");

        // Remove the root logger
        op = SubsystemOperations.createRemoveOperation(rootLoggerAddress);
        executeOperation(kernelServices, op);


        // Revert root logger
        op = SubsystemOperations.createOperation(ModelDescriptionConstants.ADD, rootLoggerAddress);
        op.get(CommonAttributes.LEVEL.getName()).set(rootLoggerResult.get(CommonAttributes.LEVEL.getName()));
        op.get(LoggerAttributes.HANDLERS.getName()).set(rootLoggerResult.get(LoggerAttributes.HANDLERS.getName()));
        executeOperation(kernelServices, op);

        // remove file handler
        removeFileHandler(kernelServices, loggingProfile, fileHandlerName, false);

        // check that root logger were changed - file logger was registered
        String log = readFileToString(logFile);
        assertTrue(log.contains("Test123"));
    }

    private void testAddRemoveFileHandler(final String loggingProfile) throws Exception {
        final String fileHandlerName = "test-file-handler";

        Path logFile = createLogFile();

        // Add file handler
        addFileHandler(kernelServices, loggingProfile, fileHandlerName, org.jboss.logmanager.Level.INFO, logFile, true);

        final ModelNode rootLoggerAddress = createRootLoggerAddress(loggingProfile).toModelNode();
        // Ensure the model doesn't contain any erroneous attributes
        ModelNode op = SubsystemOperations.createReadResourceOperation(rootLoggerAddress);
        ModelNode result = executeOperation(kernelServices, op);
        final ModelNode rootLoggerResource = SubsystemOperations.readResult(result);
        validateResourceAttributes(rootLoggerResource, Arrays.asList("filter", "filter-spec", "level", "handlers"));


        // Ensure the handler is listed
        op = SubsystemOperations.createReadAttributeOperation(rootLoggerAddress, LoggerAttributes.HANDLERS);
        ModelNode handlerResult = executeOperation(kernelServices, op);
        List<String> handlerList = SubsystemOperations.readResultAsList(handlerResult);
        assertTrue(String.format("Handler '%s' was not found. Result: %s", fileHandlerName, handlerResult), handlerList.contains(fileHandlerName));
        doLog(loggingProfile, LEVELS, "Test123");

        // Remove handler from logger
        op = SubsystemOperations.createOperation("root-logger-unassign-handler", rootLoggerAddress);
        op.get(CommonAttributes.NAME.getName()).set(fileHandlerName);
        executeOperation(kernelServices, op);

        // Ensure the handler is not listed
        op = SubsystemOperations.createReadAttributeOperation(rootLoggerAddress, LoggerAttributes.HANDLERS);
        handlerResult = executeOperation(kernelServices, op);
        handlerList = SubsystemOperations.readResultAsList(handlerResult);
        assertFalse(String.format("Handler '%s' was not removed. Result: %s", fileHandlerName, handlerResult), handlerList.contains(fileHandlerName));

        // Remove the handler
        removeFileHandler(kernelServices, loggingProfile, fileHandlerName, false);

        // check generated log file
        assertTrue(readFileToString(logFile).contains("Test123"));

        // verify that the logger is stopped, no more logs are coming to the file
        long checksum = checksumCRC32(logFile);
        doLog(loggingProfile, LEVELS, "Test123");
        assertEquals(checksum, checksumCRC32(logFile));
    }

    private void testDisableHandler(final String profileName, boolean legacy) throws Exception {
        final String fileHandlerName = "test-file-handler";

        final Path logFile = createLogFile();

        // Add file handler
        addFileHandler(kernelServices, profileName, fileHandlerName, org.jboss.logmanager.Level.INFO, logFile, true);

        // Ensure the handler is listed
        final ModelNode rootLoggerAddress = createRootLoggerAddress(profileName).toModelNode();
        ModelNode op = SubsystemOperations.createReadAttributeOperation(rootLoggerAddress, LoggerAttributes.HANDLERS);
        ModelNode handlerResult = executeOperation(kernelServices, op);
        List<String> handlerList = SubsystemOperations.readResultAsList(handlerResult);
        assertTrue(String.format("Handler '%s' was not found. Result: %s", fileHandlerName, handlerResult), handlerList.contains(fileHandlerName));

        // Get the logger
        final Logger logger = getLogger(profileName);

        // Log 3 lines
        logger.info("Test message 1");
        logger.info("Test message 2");
        logger.info("Test message 3");

        // Disable the handler
        final ModelNode handlerAddress = createFileHandlerAddress(profileName, fileHandlerName).toModelNode();
        ModelNode disableOp = legacy ? Util.getEmptyOperation("disable", handlerAddress)
                : SubsystemOperations.createWriteAttributeOperation(handlerAddress, CommonAttributes.ENABLED, false);
        executeOperation(kernelServices, disableOp);

        // The operation should set the enabled attribute to false
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(handlerAddress, CommonAttributes.ENABLED);
        ModelNode result = executeOperation(kernelServices, readOp);
        assertFalse("enabled attribute should be false when the disable operation is invoked", SubsystemOperations.readResult(result).asBoolean());

        // Log 3 more lines
        logger.info("Test message 4");
        logger.info("Test message 5");
        logger.info("Test message 6");

        // Check the file, should only contain 3 lines
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals("Handler was not disable.", 3, lines.size());

        // Re-enable the handler
        ModelNode enableOp = legacy ? Util.getEmptyOperation("enable", handlerAddress)
                : SubsystemOperations.createWriteAttributeOperation(handlerAddress, CommonAttributes.ENABLED, true);
        executeOperation(kernelServices, enableOp);

        // The operation should set the enabled attribute to true
        result = executeOperation(kernelServices, readOp);
        assertTrue("enabled attribute should be true when the enable operation is invoked", SubsystemOperations.readResult(result).asBoolean());

        // Log 3 more lines
        logger.info("Test message 7");
        logger.info("Test message 8");
        logger.info("Test message 9");

        // Check the file, should contain 6 lines
        lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals("Handler was not disable.", 6, lines.size());

    }

    private void testPatternFormatter(final String profileName) throws Exception {
        final String fileHandlerName = "test-file-handler";

        final Path logFile = createLogFile();

        // Add file handler
        final ModelNode handlerAddress = addFileHandler(kernelServices, profileName, fileHandlerName, org.jboss.logmanager.Level.INFO, logFile, false);

        // Get the logger
        final Logger logger = getLogger(profileName);

        // Create the logger
        final ModelNode loggerAddress = createLoggerAddress(profileName, logger.getName()).toModelNode();
        ModelNode op = SubsystemOperations.createAddOperation(loggerAddress);
        op.get(LoggerResourceDefinition.USE_PARENT_HANDLERS.getName()).set(false);
        op.get(LoggerAttributes.HANDLERS.getName()).setEmptyList().add(fileHandlerName);
        executeOperation(kernelServices, op);

        // Create a pattern formatter
        final ModelNode patternFormatterAddress = createPatternFormatterAddress(profileName, "PATTERN").toModelNode();
        op = SubsystemOperations.createAddOperation(patternFormatterAddress);
        // Add a format that can be read back to make sure it matches the pattern used in the handler
        op.get(PatternFormatterResourceDefinition.PATTERN.getName()).set("[NAMED-PATTERN] %s%n");
        executeOperation(kernelServices, op);

        // Add the named formatter to the handler
        op = SubsystemOperations.createWriteAttributeOperation(handlerAddress, FileHandlerResourceDefinition.NAMED_FORMATTER, "PATTERN");
        executeOperation(kernelServices, op);

        // Log 3 lines
        logger.info("Test message 1");
        logger.info("Test message 2");
        logger.info("Test message 3");

        // Check the file, should only contain 3 lines
        final List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals("Additional messages written to handler that should not be there.", 3, lines.size());

        // Check each line
        assertEquals("Line patterns don't match.", "[NAMED-PATTERN] Test message 1", lines.get(0));
        assertEquals("Line patterns don't match.", "[NAMED-PATTERN] Test message 2", lines.get(1));
        assertEquals("Line patterns don't match.", "[NAMED-PATTERN] Test message 3", lines.get(2));

        // Clean up
        op = SubsystemOperations.CompositeOperationBuilder.create()
                .addStep(SubsystemOperations.createRemoveOperation(loggerAddress))
                .addStep(SubsystemOperations.createRemoveOperation(handlerAddress))
                .addStep(SubsystemOperations.createRemoveOperation(patternFormatterAddress))
                .build().getOperation();
        executeOperation(kernelServices, op);

    }

    private void testCompositeOperations(final String profileName) {
        final String asyncHandlerName = "async";
        final String consoleHandlerName = "console";

        final ModelNode asyncHandlerAddress = createAsyncHandlerAddress(profileName, asyncHandlerName).toModelNode();
        final ModelNode consoleHandlerAddress = createConsoleHandlerAddress(profileName, consoleHandlerName).toModelNode();
        final ModelNode loggerAddress = createLoggerAddress(profileName, FQCN).toModelNode();

        final ModelNode addAsyncHandlerOp = SubsystemOperations.createAddOperation(asyncHandlerAddress);
        addAsyncHandlerOp.get(AsyncHandlerResourceDefinition.QUEUE_LENGTH.getName()).set(10);

        // Add the handlers
        ModelNode op = SubsystemOperations.CompositeOperationBuilder.create()
                .addStep(addAsyncHandlerOp)
                .addStep(SubsystemOperations.createAddOperation(consoleHandlerAddress))
                .addStep(SubsystemOperations.createAddOperation(loggerAddress))
                .build().getOperation();
        executeOperation(kernelServices, op);

        // Add the handlers to the logger and the console-handler to the async-handler
        op = SubsystemOperations.CompositeOperationBuilder.create()
                .addStep(createAddHandlerOperation(asyncHandlerAddress, consoleHandlerName))
                .addStep(createAddHandlerOperation(loggerAddress, asyncHandlerName))
                .addStep(createAddHandlerOperation(loggerAddress, consoleHandlerName))
                .build().getOperation();
        executeOperation(kernelServices, op);

        // Check each resource for erroneous attributes
        op = SubsystemOperations.createReadResourceOperation(asyncHandlerAddress);
        ModelNode result = executeOperation(kernelServices, op);
        ModelNode resource = SubsystemOperations.readResult(result);
        validateResourceAttributes(resource, Arrays.asList("enabled", "level", "filter-spec", "queue-length",
                "overflow-action", "subhandlers", "name", "filter"));
        op = SubsystemOperations.createReadResourceOperation(consoleHandlerAddress);
        result = executeOperation(kernelServices, op);
        resource = SubsystemOperations.readResult(result);
        validateResourceAttributes(resource, Arrays.asList("enabled", "encoding", "level", "filter-spec", "formatter",
                "autoflush", "target", "named-formatter", "name", "filter"));
        op = SubsystemOperations.createReadResourceOperation(loggerAddress);
        result = executeOperation(kernelServices, op);
        resource = SubsystemOperations.readResult(result);
        validateResourceAttributes(resource, Arrays.asList("category", "filter", "filter-spec", "level", "handlers",
                "use-parent-handlers"));

        // Remove all the resources
        op = SubsystemOperations.CompositeOperationBuilder.create()
                .addStep(SubsystemOperations.createRemoveOperation(loggerAddress))
                .addStep(SubsystemOperations.createRemoveOperation(asyncHandlerAddress))
                .addStep(SubsystemOperations.createRemoveOperation(consoleHandlerAddress))
                .build().getOperation();
        executeOperation(kernelServices, op);
    }


    private ModelNode addFileHandler(final KernelServices kernelServices, final String loggingProfile, final String name,
                                     final Level level, final Path file, final boolean assign) {
        final ModelNode address = createFileHandlerAddress(loggingProfile, name).toModelNode();

        // add file handler
        ModelNode op = SubsystemOperations.createAddOperation(address);
        op.get(CommonAttributes.NAME.getName()).set(name);
        op.get(CommonAttributes.LEVEL.getName()).set(level.getName());
        op.get(CommonAttributes.FILE.getName()).get(PathResourceDefinition.PATH.getName()).set(file.toAbsolutePath().toString());
        op.get(CommonAttributes.AUTOFLUSH.getName()).set(true);
        executeOperation(kernelServices, op);

        // register it with root logger
        if (assign) {
            op = SubsystemOperations.createOperation("root-logger-assign-handler", createRootLoggerAddress(loggingProfile).toModelNode());
            op.get(CommonAttributes.NAME.getName()).set(name);
            executeOperation(kernelServices, op);
        }
        return address;
    }

    private void removeFileHandler(final KernelServices kernelServices, final String loggingProfile, final String name,
                                   final boolean unassign) {

        if (unassign) {
            // Remove the handler from the logger
            final ModelNode op = SubsystemOperations.createOperation("root-logger-unassign-handler", createRootLoggerAddress(loggingProfile).toModelNode());
            op.get(CommonAttributes.NAME.getName()).set(name);
            executeOperation(kernelServices, op);
        }

        // Remove the handler
        final ModelNode op = SubsystemOperations.createRemoveOperation(createFileHandlerAddress(loggingProfile, name).toModelNode());
        executeOperation(kernelServices, op);
    }

    private ModelNode executeOperation(final KernelServices kernelServices, final ModelNode op) {
        return executeOperation(kernelServices, op, true);
    }

    private ModelNode executeOperation(final KernelServices kernelServices, final ModelNode op, final boolean validateResult) {
        final ModelNode result = kernelServices.executeOperation(op);
        if (validateResult)
            assertTrue(SubsystemOperations.getFailureDescriptionAsString(result), SubsystemOperations.isSuccessfulOutcome(result));
        return result;
    }

    private void doLog(final String loggingProfile, final Level[] levels, final String format, final Object... params) {
        final Logger log = getLogger(loggingProfile);
        // log a message
        for (Level lvl : levels) {
            log.log(lvl, String.format(format, params));
        }
    }

    private Logger getLogger(final String profileName) {
        final LogContext logContext;
        if (profileName != null) {
            logContext = LoggingProfileContextSelector.getInstance().get(profileName);
        } else {
            logContext = LogContext.getSystemLogContext();
        }
        return logContext.getLogger(FQCN);
    }

    private ModelNode createAddHandlerOperation(final ModelNode address, final String handlerName) {
        final ModelNode op = SubsystemOperations.createOperation(CommonAttributes.ADD_HANDLER_OPERATION_NAME, address);
        op.get(CommonAttributes.HANDLER_NAME.getName()).set(handlerName);
        return op;
    }

    private static Path createLogFile() throws IOException {
        return createLogFile(UUID.randomUUID().toString() + ".log");
    }

    private static Path createLogFile(final String filename) throws IOException {
        final Path logFile = logDir.resolve(filename);
        Files.deleteIfExists(logFile);
        return logFile;
    }

    private static String readFileToString(final Path file) throws IOException {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InputStream in = Files.newInputStream(file)) {
            final byte[] buffer = new byte[512];
            int len;
            while ((len = in.read(buffer)) != -1) {
                result.write(buffer, 0, len);
            }
        }
        return result.toString("UTF-8");
    }

    private static long checksumCRC32(final Path file) throws IOException {
        final CRC32 checksum = new CRC32();
        checksum.update(Files.readAllBytes(file));
        return checksum.getValue();
    }
}
