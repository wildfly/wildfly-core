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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"SameParameterValue", "MagicNumber"})
public class RootSubsystemOperationsTestCase extends AbstractOperationsTestCase {
    private final String msg = "Test message ";

    @Before
    public void clearLogDir() throws Exception {
        clearDirectory(LoggingTestEnvironment.get().getLogDir());
    }

    @Override
    protected void standardSubsystemTest(final String configId) {
        // do nothing as this is not a subsystem parsing test
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return LoggingTestEnvironment.get();
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/simple-subsystem.xml");
    }

    @Test
    public void testAttributes() throws Exception {
        final KernelServices kernelServices = boot();
        final ModelNode address = SUBSYSTEM_ADDRESS.toModelNode();
        testWrite(kernelServices, address, LoggingResourceDefinition.ADD_LOGGING_API_DEPENDENCIES, true);
        testWrite(kernelServices, address, LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG, false);
        testUndefine(kernelServices, address, LoggingResourceDefinition.ADD_LOGGING_API_DEPENDENCIES);
        testUndefine(kernelServices, address, LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG);

        kernelServices.shutdown();
    }

    @Test
    public void testLogFileResource() throws Exception {
        final KernelServices kernelServices = boot();

        // Subsystem address
        final ModelNode address = SUBSYSTEM_ADDRESS.append("log-file").toModelNode();
        ModelNode op = SubsystemOperations.createReadResourceOperation(address);
        //op.get("include-runtime").set(true);
        ModelNode result = executeOperation(kernelServices, op);
        List<ModelNode> resources = SubsystemOperations.readResult(result).asList();
        assertFalse("No Resources were found: " + result, resources.isEmpty());

        int expectedSize = resources.size();

        // Add a new file not in the jboss.server.log.dir directory
        final Path logFile = LoggingTestEnvironment.get().getLogDir().resolve("fh.log");
        final ModelNode fhAddress = createFileHandlerAddress("fh").toModelNode();
        op = SubsystemOperations.createAddOperation(fhAddress);
        op.get("file").set(createFileValue(null, logFile.toAbsolutePath().toString()));
        executeOperation(kernelServices, op);

        // Re-read the log-file resource, the size should be the same
        result = executeOperation(kernelServices, SubsystemOperations.createReadResourceOperation(address));
        resources = SubsystemOperations.readResult(result).asList();
        assertEquals("Log file " + logFile + " should not be a resource", expectedSize, resources.size());

        // Change the file path of the file handler which should make it a log-file resource
        op = SubsystemOperations.createWriteAttributeOperation(fhAddress, "file", createFileValue("jboss.server.log.dir", "fh-2.log"));
        executeOperation(kernelServices, op);
        // Should be an additional resource
        result = executeOperation(kernelServices, SubsystemOperations.createReadResourceOperation(address));
        resources = SubsystemOperations.readResult(result).asList();
        assertEquals("Additional log-file resource failed to dynamically get added", ++expectedSize, resources.size());

        // Test the read-log-file on the
        final ModelNode simpleLogAddress = SUBSYSTEM_ADDRESS.append("log-file", "simple.log").toModelNode();
        op = SubsystemOperations.createOperation("read-log-file", simpleLogAddress);
        testReadLogFile(kernelServices, op, getLogger());

        // Test on the logging-profile
        final ModelNode profileAddress = SUBSYSTEM_ADDRESS.append("logging-profile", "testProfile").append("log-file", "profile-simple.log").toModelNode();
        op = SubsystemOperations.createOperation("read-log-file", profileAddress);
        testReadLogFile(kernelServices, op, getLogger("testProfile"));

        // Test file in subdirectory
        final ModelNode subFhAddress = createFileHandlerAddress("sub-fh").toModelNode();
        op = SubsystemOperations.createAddOperation(subFhAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "subdir" + File.separator + "sub-fh.log"));
        executeOperation(kernelServices, op);
        result = executeOperation(kernelServices, SubsystemOperations.createReadResourceOperation(address));
        resources = SubsystemOperations.readResult(result).asList();
        assertEquals("Log file " + logFile + " should not be a resource", ++expectedSize, resources.size());

        kernelServices.shutdown();

    }

    @Test
    @Deprecated
    public void testListLogFiles() throws Exception {
        final KernelServices kernelServices = boot();

        // Subsystem address
        final ModelNode address = SUBSYSTEM_ADDRESS.toModelNode();
        final ModelNode op = SubsystemOperations.createOperation("list-log-files", address);
        ModelNode result = executeOperation(kernelServices, op);
        List<ModelNode> logFiles = SubsystemOperations.readResult(result).asList();

        // Should only be one file
        // TODO (jrp) can be tested when LOGMGR-83 is committed and the logmanager is updated
        // assertEquals("Found: " + logFiles, 2, logFiles.size());

        // Should contain simple.log and simple-profile.log
        boolean found = false;
        boolean foundProfile = false;
        for (ModelNode fileInfo : logFiles) {
            final String fileName = fileInfo.get("file-name").asString();
            if ("simple.log".equals(fileName)) {
                found = true;
            }
            if ("profile-simple.log".equals(fileName)) {
                foundProfile = true;
            }
            if ("ignore.log".equals(fileName)) {
                fail("Found ignore.log, but the file should not be listed.");
            }
            if ("profile-ignore.log".equals(fileName)) {
                fail("Found profile-ignore.log, but the file should not be listed.");
            }
        }
        assertTrue("simple.log file was not found", found);
        assertTrue("profile-simple.log file was not found", foundProfile);

        // Change the permissions on the file so read is not allowed
        final Path file = LoggingTestEnvironment.get().getLogDir().resolve("simple.log");
        // The file should exist
        assertTrue("File does not exist", Files.exists(file));

        // Only test if successfully set
        if (setReadable(file, false)) {
            result = executeOperation(kernelServices, op);
            logFiles = SubsystemOperations.readResult(result).asList();
            // The simple.log should not be in the list
            assertEquals("Read permission was found to be true on the file.", 1, logFiles.size());
            // Reset the file permissions
            assertTrue("Could not reset file permissions", setReadable(file, true));
        }

        kernelServices.shutdown();
    }

    @Test
    @Deprecated
    public void testReadLogFile() throws Exception {
        final KernelServices kernelServices = boot();

        // Subsystem address
        final ModelNode address = SUBSYSTEM_ADDRESS.toModelNode();
        final ModelNode op = SubsystemOperations.createOperation("read-log-file", address);
        op.get("name").set("simple.log");
        testReadLogFile(kernelServices, op, getLogger());

        // Change the permissions on the file so read is not allowed
        final Path file = LoggingTestEnvironment.get().getLogDir().resolve(op.get("name").asString());
        // The file should exist
        assertTrue("File does not exist", Files.exists(file));


        ModelNode result = null;

        // Only test if successfully set
        if (setReadable(file, false)) {
            result = kernelServices.executeOperation(op);
            assertFalse("Should have failed due to denial of read permissions on the file.", SubsystemOperations.isSuccessfulOutcome(result));
            // Reset the file permissions
            assertTrue("Could not reset file permissions", setReadable(file, true));
        }

        // Should be able to read profile-simple.log, but it should be empty
        op.get("name").set("profile-simple.log");
        result = executeOperation(kernelServices, op);
        final List<String> logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(0, logLines.size());

        // Should not be able to read ignore.log even though the file exists
        op.get("name").set("ignore.log");
        result = kernelServices.executeOperation(op);
        assertFalse("Should have failed due to file not be readable.", SubsystemOperations.isSuccessfulOutcome(result));

        // Should not be able to read ignore.log even though the file exists
        op.get("name").set("profile-ignore.log");
        result = kernelServices.executeOperation(op);
        assertFalse("Should have failed due to file not be readable.", SubsystemOperations.isSuccessfulOutcome(result));

        // Test an invalid file
        op.get("name").set("invalid");
        result = kernelServices.executeOperation(op);
        assertFalse("Should have failed due to invalid file.", SubsystemOperations.isSuccessfulOutcome(result));

        kernelServices.shutdown();
    }

    @Test
    public void testFailedLogFile() throws Exception {
        final Path configDir = LoggingTestEnvironment.get().getConfigDir();
        final Path logDir = LoggingTestEnvironment.get().getLogDir();

        // Create the test log file
        final Path logFile = configDir.resolve("test-config.log");

        Files.deleteIfExists(logFile);
        Files.createFile(logFile);
        final Path relativeLogFile = logDir.relativize(logFile);
        assertTrue("Expected the log file log file to exist", Files.exists(logFile));

        final KernelServices kernelServices = boot();

        // Attempt to read an attribute on a valid file, but a non-existing resource
        final ModelNode address = SUBSYSTEM_ADDRESS.append("log-file", relativeLogFile.toString()).toModelNode();
        ModelNode op = SubsystemOperations.createReadAttributeOperation(address, "file-size");
        executeOperationForFailure(kernelServices, op);

        // Attempt to read a valid file on a non-existing resource
        op = SubsystemOperations.createOperation("read-log-file", address);
        executeOperationForFailure(kernelServices, op);

        // Add a valid file-handler
        final ModelNode handlerAddress = createFileHandlerAddress(relativeLogFile.toString()).toModelNode();
        op = SubsystemOperations.createAddOperation(handlerAddress);
        op.get("append").set(true);
        final ModelNode fileModel = op.get("file").setEmptyObject();
        fileModel.get("relative-to").set("jboss.server.log.dir");
        fileModel.get("path").set(relativeLogFile.toString());
        executeOperation(kernelServices, op);

        // Attempt to read an attribute on a valid file handler. The file handler was created in the jboss.server.log.dir
        // however it used a relative path to attempt to allow any file to be read
        op = SubsystemOperations.createReadAttributeOperation(address, "file-size");
        executeOperationForFailure(kernelServices, op);

        // Attempt to read the file on a valid file handler. The file handler was created in the jboss.server.log.dir
        // however it used a relative path to attempt to allow any file to be read
        op = SubsystemOperations.createOperation("read-log-file", address);
        executeOperationForFailure(kernelServices, op);

        kernelServices.shutdown();
    }

    private void testReadLogFile(final KernelServices kernelServices, final ModelNode op, final Logger logger) {
        // Log some messages
        for (int i = 0; i < 50; i++) {
            logger.info(msg + i);
        }

        ModelNode result = executeOperation(kernelServices, op);
        List<String> logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(10, logLines.size());
        checkLogLines(logLines, 40);

        // Read from top
        op.get("tail").set(false);
        result = executeOperation(kernelServices, op);
        logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(10, logLines.size());
        checkLogLines(logLines, 0);

        // Read more lines from top
        op.get("lines").set(20);
        result = executeOperation(kernelServices, op);
        logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(20, logLines.size());
        checkLogLines(logLines, 0);

        // Read from bottom
        op.get("tail").set(true);
        result = executeOperation(kernelServices, op);
        logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(20, logLines.size());
        checkLogLines(logLines, 30);

        // Skip lines from bottom
        op.get("tail").set(true);
        op.get("skip").set(5);
        result = executeOperation(kernelServices, op);
        logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(20, logLines.size());
        checkLogLines(logLines, 25);

        // Skip lines from top
        op.get("tail").set(false);
        op.get("skip").set(5);
        result = executeOperation(kernelServices, op);
        logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(20, logLines.size());
        checkLogLines(logLines, 5);

        // Read all lines
        op.get("tail").set(false);
        op.get("lines").set(-1);
        op.remove("skip");
        result = executeOperation(kernelServices, op);
        logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(50, logLines.size());
        checkLogLines(logLines, 0);

        // Read all lines, but 5 lines
        op.get("tail").set(false);
        op.get("lines").set(-1);
        op.get("skip").set(5);
        result = executeOperation(kernelServices, op);
        logLines = SubsystemOperations.readResultAsList(result);
        assertEquals(45, logLines.size());
        checkLogLines(logLines, 5);
    }

    private void checkLogLines(final List<String> logLines, final int start) {
        int index = start;
        for (String line : logLines) {
            final String lineMsg = msg + index;
            assertTrue(String.format("Expected line containing '%s', found '%s", lineMsg, line), line.contains(msg + index));
            index++;
        }
    }

    private Logger getLogger() {
        return LogContext.getSystemLogContext().getLogger("org.jboss.as.logging.test");
    }

    private Logger getLogger(final String loggingProfile) {
        return LoggingProfileContextSelector.getInstance().get(loggingProfile).getLogger("org.jboss.as.logging.test");
    }

    private static boolean setReadable(final Path path, final boolean readable) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class)) {
            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (readable) {
                permissions.add(PosixFilePermission.OWNER_READ);
                permissions.add(PosixFilePermission.GROUP_READ);
                Files.setPosixFilePermissions(path, EnumSet.copyOf(permissions));
            } else {
                permissions.remove(PosixFilePermission.OWNER_READ);
                permissions.remove(PosixFilePermission.GROUP_READ);
                permissions.remove(PosixFilePermission.OTHERS_READ);
                Files.setPosixFilePermissions(path, EnumSet.copyOf(permissions));
            }
            return true;
        }
        return false;
    }
}
