/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.auditlog;

import java.io.File;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.audit.FileAuditLogHandlerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * The tests specific to the audit log file handler.
 *
 * Don't use core-model test for this. It does not support runtime, and more importantly for backwards compatibility the audit logger cannot be used
 *
 * @author <a href="mailto:thofman@redhat.com">Tomas Hofman</a>
 */
public class AuditLogFileHandlerTestCase extends AbstractAuditLogHandlerTestCase {

    private static final String LOG_FILE_NAME_PREFIX = "test-audit-log";
    private static final String LOG_FILE_NAME = LOG_FILE_NAME_PREFIX + ".log";
    private static final String FILE_HANDLER_NAME = "testfile";

    public AuditLogFileHandlerTestCase() {
        super(true, false);
    }

    @Before
    public void init() {
        // clean up log files
        for (File logFile: logDir.listFiles(createLogFilenameFilter(LOG_FILE_NAME_PREFIX))) {
            logFile.delete();
        }
    }

    @Test
    public void testAddRemoveFileAuditLogHandlerRotationOn() throws Exception {
        checkNumberOfLogFiles(0);
        File logFile = new File(logDir, LOG_FILE_NAME);

        // add file handler
        ModelNode op = createAddFileHandlerOperation(FILE_HANDLER_NAME, "test-formatter", LOG_FILE_NAME);
        op.get(ModelDescriptionConstants.ROTATE_AT_STARTUP).set(true);
        executeForResult(op);
        op = createAddHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        checkNumberOfLogFiles(1);
        readFile(logFile, 1);

        // remove handler
        op = createRemoveHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        op = createRemoveFileHandlerOperation(FILE_HANDLER_NAME);
        executeForResult(op);

        // and new file handler, log file should be rotated
        op = createAddFileHandlerOperation(FILE_HANDLER_NAME, "test-formatter", LOG_FILE_NAME);
        op.get(ModelDescriptionConstants.ROTATE_AT_STARTUP).set(true);
        executeForResult(op);
        op = createAddHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        checkNumberOfLogFiles(2);
        readFile(logFile, 1);

        // remove handler
        op = createRemoveHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        op = createRemoveFileHandlerOperation(FILE_HANDLER_NAME);
        executeForResult(op);
    }

    @Test
    public void testAddRemoveFileAuditLogHandlerRotationOff() throws Exception {
        checkNumberOfLogFiles(0);
        File logFile = new File(logDir, LOG_FILE_NAME);

        // add file handler
        ModelNode op = createAddFileHandlerOperation(FILE_HANDLER_NAME, "test-formatter", LOG_FILE_NAME);
        op.get(ModelDescriptionConstants.ROTATE_AT_STARTUP).set(false);
        executeForResult(op);
        op = createAddHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        checkNumberOfLogFiles(1);
        readFile(logFile, 1);

        // remove handler
        op = createRemoveHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        op = createRemoveFileHandlerOperation(FILE_HANDLER_NAME);
        executeForResult(op);

        // and new file handler, log file should not be rotated
        op = createAddFileHandlerOperation(FILE_HANDLER_NAME, "test-formatter", LOG_FILE_NAME);
        op.get(ModelDescriptionConstants.ROTATE_AT_STARTUP).set(false);
        executeForResult(op);
        op = createAddHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        checkNumberOfLogFiles(1);
        readFile(logFile, 3);

        // remove handler
        op = createRemoveHandlerReferenceOperation(FILE_HANDLER_NAME);
        executeForResult(op);
        op = createRemoveFileHandlerOperation(FILE_HANDLER_NAME);
        executeForResult(op);
    }

    @Test
    public void testRuntimeFailureMetricsAndRecycle() throws Exception {
        final String handlerName1 = "test-file";
        final String handlerName2 = "test-file2";

        File file1 = new File(logDir, LOG_FILE_NAME);
        File file2 = new File(logDir, LOG_FILE_NAME_PREFIX + "2.log");

        ModelNode op = createAddFileHandlerOperation(handlerName1, "test-formatter", file1.getName());
        op.get(FileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName()).set(2);
        executeForResult(op);
        op = createAddHandlerReferenceOperation(handlerName1);
        executeForResult(op);

        final ModelNode readResource = Util.createOperation(ModelDescriptionConstants.READ_RESOURCE_OPERATION, AUDIT_ADDR);
        readResource.get(ModelDescriptionConstants.RECURSIVE).set(true);
        readResource.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);

        ModelNode result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName1), 2, 0, false);

        //Delete the log directory so we start seeing failures in the file handler
        for (File file : logDir.listFiles()) {
            file.delete();
        }
        logDir.delete();

        Assert.assertFalse(file1.exists());
        Assert.assertFalse(file2.exists());

        op = createAddFileHandlerOperation(handlerName2, "test-formatter", file2.getName());
        op.get(FileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName()).set(1);
        executeForResult(op);

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName1), 2, 1, false);

        // this will re-create the log directory
        op = createAddHandlerReferenceOperation(handlerName2);
        executeForResult(op);

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName1), 2, 2, true);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName2), 1, 0, false);

        //Recycle the file handler so it resets the failure count and starts logging again
        executeForResult(Util.createOperation(ModelDescriptionConstants.RECYCLE, createFileHandlerAddress(handlerName1)));

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(file2.exists());

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName1), 2, 0, false);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName2), 1, 0, false);

        //Finally just update the max failure counts and see that works
        op = Util.getWriteAttributeOperation(createFileHandlerAddress(handlerName1), FileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName(), new ModelNode(7));
        executeForResult(op);
        op = Util.getWriteAttributeOperation(createFileHandlerAddress(handlerName2), FileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName(), new ModelNode(4));
        executeForResult(op);

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName1), 7, 0, false);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.FILE_HANDLER, handlerName2), 4, 0, false);

        // remove handlers
        op = createRemoveHandlerReferenceOperation(handlerName1);
        executeForResult(op);
        op = createRemoveFileHandlerOperation(handlerName1);
        executeForResult(op);
        op = createRemoveHandlerReferenceOperation(handlerName2);
        executeForResult(op);
        op = createRemoveFileHandlerOperation(handlerName2);
        executeForResult(op);
    }

    private void checkNumberOfLogFiles(int expected) {
        Assert.assertEquals("Unexpected number of log files", expected, logDir.list().length);
    }
}
