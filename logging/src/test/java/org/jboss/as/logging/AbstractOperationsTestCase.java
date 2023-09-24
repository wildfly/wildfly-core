/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.BeforeClass;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractOperationsTestCase extends AbstractLoggingSubsystemTest {

    protected static final String PROFILE = "testProfile";

    private static Path logDir;

    @BeforeClass
    public static void setupLoggingDir() throws Exception {
        logDir = LoggingTestEnvironment.get().getLogDir();
        clearDirectory(logDir);
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

    protected void testWrite(final KernelServices kernelServices, final ModelNode address, final AttributeDefinition attribute, final String value) {
        final ModelNode writeOp = SubsystemOperations.createWriteAttributeOperation(address, attribute, value);
        executeOperation(kernelServices, writeOp);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, attribute);
        final ModelNode result = executeOperation(kernelServices, readOp);
        assertEquals(value, SubsystemOperations.readResultAsString(result));
    }

    protected void testWrite(final KernelServices kernelServices, final ModelNode address, final AttributeDefinition attribute, final boolean value) {
        final ModelNode writeOp = SubsystemOperations.createWriteAttributeOperation(address, attribute, value);
        executeOperation(kernelServices, writeOp);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, attribute);
        final ModelNode result = executeOperation(kernelServices, readOp);
        assertEquals(value, SubsystemOperations.readResult(result).asBoolean());
    }

    protected void testWrite(final KernelServices kernelServices, final ModelNode address, final AttributeDefinition attribute, final int value) {
        final ModelNode writeOp = SubsystemOperations.createWriteAttributeOperation(address, attribute, value);
        executeOperation(kernelServices, writeOp);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, attribute);
        final ModelNode result = executeOperation(kernelServices, readOp);
        assertEquals(value, SubsystemOperations.readResult(result).asInt());
    }

    protected void testWrite(final KernelServices kernelServices, final ModelNode address, final AttributeDefinition attribute, final ModelNode value) {
        final ModelNode writeOp = SubsystemOperations.createWriteAttributeOperation(address, attribute, value);
        executeOperation(kernelServices, writeOp);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, attribute);
        final ModelNode result = executeOperation(kernelServices, readOp);
        assertEquals(value, SubsystemOperations.readResult(result));
    }

    void testUndefine(final KernelServices kernelServices, final ModelNode address, final String attribute) {
        final ModelNode undefineOp = SubsystemOperations.createUndefineAttributeOperation(address, attribute);
        executeOperation(kernelServices, undefineOp);
        // Create the read operation
        final ModelNode readOp = SubsystemOperations.createReadAttributeOperation(address, attribute);
        readOp.get("include-defaults").set(false);
        final ModelNode result = executeOperation(kernelServices, readOp);
        assertFalse("Attribute '" + attribute + "' was not undefined.", SubsystemOperations.readResult(result)
                .isDefined());
    }

    protected void testUndefine(final KernelServices kernelServices, final ModelNode address, final AttributeDefinition attribute) {
        testUndefine(kernelServices, address, attribute.getName());
    }

    protected void verifyRemoved(final KernelServices kernelServices, final ModelNode address) {
        final ModelNode op = SubsystemOperations.createReadResourceOperation(address);
        final ModelNode result = kernelServices.executeOperation(op);
        assertFalse("Resource not removed: " + address, SubsystemOperations.isSuccessfulOutcome(result));
    }

    protected ModelNode executeOperation(final KernelServices kernelServices, final ModelNode op) {
        final ModelNode result = kernelServices.executeOperation(op);
        assertTrue(SubsystemOperations.getFailureDescriptionAsString(result), SubsystemOperations.isSuccessfulOutcome(result));
        return result;
    }

    protected ModelNode executeOperationForFailure(final KernelServices kernelServices, final ModelNode op) {
        final ModelNode result = kernelServices.executeOperation(op);
        assertFalse("Operation was expected to fail: " + op, SubsystemOperations.isSuccessfulOutcome(result));
        return result;
    }

    protected ModelNode createFileValue(final String relativeTo, final String path) {
        final ModelNode file = new ModelNode().setEmptyObject();
        if (relativeTo != null) {
            file.get(PathResourceDefinition.RELATIVE_TO.getName()).set(relativeTo);
        }
        file.get(PathResourceDefinition.PATH.getName()).set(path);
        return file;
    }

    static void verifyFile(final String filename) {
        assertTrue("Log file was not found", Files.exists(logDir.resolve(filename)));
    }

    static void removeFile(final String filename) throws IOException {
        Files.delete(logDir.resolve(filename));
    }
}
