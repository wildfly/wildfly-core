/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security.auditlog;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.audit.AuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.FileAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.JsonAuditLogFormatterResourceDefinition;
import org.jboss.as.domain.management.audit.PeriodicRotatingFileAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.validators.SuffixValidator;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;

/**
 * The tests specific to the periodic rotating file handler.
 *
 * Don't use core-model test for this. It does not support runtime, and more importantly for backwards compatibility the audit logger cannot be used
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class AuditLogPeriodicRotatingFileHandlerTestCase extends AbstractAuditLogHandlerTestCase {

    private static final String LOG_FILE_NAME_PREFIX = "test-periodic-rotating-file";
    private static final String LOG_FILE_NAME = LOG_FILE_NAME_PREFIX + ".log";
    private static final String SUFFIX = ".yyyy-MM-dd-hh-mm";

    public AuditLogPeriodicRotatingFileHandlerTestCase() {
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
    public void testAddRemovePeriodicRotatingFileAuditLogHandler() throws Exception {
        final String fileHandlerName = "file2";
        final String rotatingHandlerName = "test-periodic-rotating-file";

        File file1 = new File(logDir, "test-file.log");     // simple file handler
        File file2 = new File(logDir, LOG_FILE_NAME);
        Assert.assertFalse(file2.exists());

        // add simple file handler
        ModelNode op = createAddFileHandlerOperation(fileHandlerName, "test-formatter", "test-file.log");
        executeForResult(op);
        Assert.assertFalse(file1.exists());
        op = createAddHandlerReferenceOperation(fileHandlerName);
        executeForResult(op);
        List<ModelNode> records1 = readFile(file1, 1);
        List<ModelNode> ops = checkBootRecordHeader(records1.get(0), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        // add periodic rotating file handler
        op = createAddPeriodicRotatingFileHandlerOperation(rotatingHandlerName, "test-formatter", LOG_FILE_NAME, SUFFIX);
        executeForResult(op);
        Assert.assertFalse(file2.exists());
        records1 = readFile(file1, 2);
        ops = checkBootRecordHeader(records1.get(1), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        op = createAddHandlerReferenceOperation(rotatingHandlerName);
        executeForResult(op);
        records1 = readFile(file1, 3);
        List<ModelNode> records2 = readFile(file2, 1);
        Assert.assertEquals(records1.get(2), records2.get(0));
        ops = checkBootRecordHeader(records1.get(2), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        // close the rotating handler
        op = createRemoveHandlerReferenceOperation(rotatingHandlerName);
        executeForResult(op);
        records1 = readFile(file1, 4);
        records2 = readFile(file2, 2);
        Assert.assertEquals(records1.get(3), records2.get(1));
        ops = checkBootRecordHeader(records1.get(3), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        // the rotating handler was closed but it should not rotate
        op = createAddHandlerReferenceOperation(rotatingHandlerName);
        executeForResult(op);
        records1 = readFile(file1, 5);
        records2 = readFile(file2, 3);
        Assert.assertEquals(records1.get(4), records2.get(2));
        ops = checkBootRecordHeader(records1.get(4), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        // clean handlers
        op = createRemoveHandlerReferenceOperation(rotatingHandlerName);
        executeForResult(op);
        records1 = readFile(file1, 6);
        records2 = readFile(file2, 4);
        Assert.assertEquals(records1.get(5), records2.get(3));
        ops = checkBootRecordHeader(records1.get(5), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        op = createRemovePeriodicRotatingFileHandlerOperation(rotatingHandlerName);
        executeForResult(op);
        records1 = readFile(file1, 7);
        records2 = readFile(file2, 4);
        ops = checkBootRecordHeader(records1.get(6), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        op = createRemoveHandlerReferenceOperation(fileHandlerName);
        executeForResult(op);
        op = createRemoveFileHandlerOperation(fileHandlerName);
        executeForResult(op);
    }

    @Test
    @Ignore("time consuming test, it takes more than 1 minute")
    public void testRotation() throws Exception {
        final String handlerName1 = "test-periodic-rotating-file";
        final String handlerName2 = "test-periodic-rotating-file2";

        File file1 = new File(logDir, LOG_FILE_NAME);
        File file2 = new File(logDir, LOG_FILE_NAME_PREFIX + "2.log");
        Assert.assertFalse(file1.exists());
        Assert.assertFalse(file2.exists());

        // add a handler rotating at 1k which should be small enough to rotate after one record which is about 600 B
        ModelNode op = createAddPeriodicRotatingFileHandlerOperation(handlerName2, "test-formatter", file2.getName(), SUFFIX);
        executeForResult(op);
        Assert.assertFalse(file2.exists());
        op = createAddHandlerReferenceOperation(handlerName2);
        executeForResult(op);
        List<ModelNode> records2 = readFile(file2, 1);
        List<ModelNode> ops = checkBootRecordHeader(records2.get(0), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        // check that handler2 did not rotate yet
        Assert.assertEquals(1, logDir.listFiles(createLogFilenameFilter(file2.getName())).length);

        // wait a bit more than one minute
        Thread.sleep(61 * 1000);

        // add another handler rotating by an hour
        op = createAddPeriodicRotatingFileHandlerOperation(handlerName1, "test-formatter", file1.getName(), ".yyyy-MM-dd-hh");
        executeForResult(op);
        Assert.assertFalse(file1.exists());
        op = createAddHandlerReferenceOperation(handlerName1);
        executeForResult(op);
        List<ModelNode> records1 = readFile(file1, 1);
        ops = checkBootRecordHeader(records1.get(0), 1, "core", false, false, true);
        checkOpsEqual(op, ops.get(0));

        // check that handler2 did rotate
        Assert.assertEquals(2, logDir.listFiles(createLogFilenameFilter(file2.getName())).length);

        // clean handlers
        op = createRemoveHandlerReferenceOperation(handlerName1);
        executeForResult(op);
        op = createRemovePeriodicRotatingFileHandlerOperation(handlerName1);
        executeForResult(op);

        op = createRemoveHandlerReferenceOperation(handlerName2);
        executeForResult(op);
        op = createRemovePeriodicRotatingFileHandlerOperation(handlerName2);
        executeForResult(op);
    }

    @Test
    public void testUpdateFileHandlerFormatter() throws Exception {
        final String rotatingHandlerName = "test-periodic-rotating-file";
        File file = new File(logDir, LOG_FILE_NAME);

        ModelNode op = createAddPeriodicRotatingFileHandlerOperation(rotatingHandlerName, "test-formatter", LOG_FILE_NAME, SUFFIX);
        executeForResult(op);
        op = createAddHandlerReferenceOperation(rotatingHandlerName);
        executeForResult(op);

        String fullRecord = readFullFileRecord(file);
        Assert.assertTrue(Pattern.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d - \\{[\\s\\S]*", fullRecord)); //This regexp allows for new lines

        file.delete();
        op = Util.getWriteAttributeOperation(createPeriodicRotatingFileHandlerAddress(rotatingHandlerName),
                FileAuditLogHandlerResourceDefinition.FORMATTER.getName(),
                new ModelNode("non-existent"));
        executeForFailure(op);
        fullRecord = readFullFileRecord(file);
        Assert.assertTrue(Pattern.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d - \\{[\\s\\S]*", fullRecord)); //This regexp allows for new lines
        ModelNode record = ModelNode.fromJSONString(fullRecord.substring(fullRecord.indexOf('{')));
        ModelNode loggedOp = checkBootRecordHeader(record, 1, "core", false, false, false).get(0);
        checkOpsEqual(op, loggedOp);

        //Add some new formatters
        op = Util.createAddOperation(createJsonFormatterAddress("compact-formatter"));
        op.get(JsonAuditLogFormatterResourceDefinition.COMPACT.getName()).set(true);
        op.get(JsonAuditLogFormatterResourceDefinition.DATE_FORMAT.getName()).set("yyyy/MM/dd HH-mm-ss");
        op.get(JsonAuditLogFormatterResourceDefinition.DATE_SEPARATOR.getName()).set(" xxx ");
        executeForResult(op);

        op = Util.createAddOperation(createJsonFormatterAddress("escaped-formatter"));
        op.get(JsonAuditLogFormatterResourceDefinition.INCLUDE_DATE.getName()).set(false);
        op.get(JsonAuditLogFormatterResourceDefinition.ESCAPE_NEW_LINE.getName()).set(true);
        executeForResult(op);

        //Update the handler formatter to the compact version and check the logged format
        file.delete();
        op = Util.getWriteAttributeOperation(createPeriodicRotatingFileHandlerAddress(rotatingHandlerName), FileAuditLogHandlerResourceDefinition.FORMATTER.getName(), new ModelNode("compact-formatter"));
        executeForResult(op);
        fullRecord = readFullFileRecord(file);
        Assert.assertTrue(Pattern.matches("\\d\\d\\d\\d/\\d\\d/\\d\\d \\d\\d-\\d\\d-\\d\\d xxx \\{.*", fullRecord)); //This regexp checks for no new lines
        record = ModelNode.fromJSONString(fullRecord.substring(fullRecord.indexOf('{')));
        loggedOp = checkBootRecordHeader(record, 1, "core", false, false, true).get(0);
        checkOpsEqual(op, loggedOp);

        //Update the handler formatter to the escaped version and check the logged format
        file.delete();
        op = Util.getWriteAttributeOperation(createPeriodicRotatingFileHandlerAddress(rotatingHandlerName), FileAuditLogHandlerResourceDefinition.FORMATTER.getName(), new ModelNode("escaped-formatter"));
        executeForResult(op);
        fullRecord = readFullFileRecord(file);
        Assert.assertTrue(Pattern.matches("\\{.*", fullRecord)); //This regexp checks for no new lines
        Assert.assertTrue(fullRecord.indexOf("#012") > 0);
        record = ModelNode.fromJSONString(fullRecord.substring(fullRecord.indexOf('{')).replace("#012", ""));
        loggedOp = checkBootRecordHeader(record, 1, "core", false, false, true).get(0);
        checkOpsEqual(op, loggedOp);

        //Check removing formatter in use fails
        file.delete();
        op = Util.createRemoveOperation(createJsonFormatterAddress("escaped-formatter"));
        executeForFailure(op);

        //Check can remove unused formatter
        op = Util.createRemoveOperation(createJsonFormatterAddress("compact-formatter"));
        executeForResult(op);

        //Now try changing the used formatter at runtime
        file.delete();
        op = Util.getWriteAttributeOperation(createJsonFormatterAddress("escaped-formatter"), JsonAuditLogFormatterResourceDefinition.ESCAPE_NEW_LINE.getName(), ModelNode.FALSE);
        executeForResult(op);
        fullRecord = readFullFileRecord(file);
        Assert.assertTrue(Pattern.matches("\\{[\\s\\S]*", fullRecord)); //This regexp allows for new lines
        Assert.assertTrue(fullRecord.indexOf("#012") == -1);
        record = ModelNode.fromJSONString(fullRecord.substring(fullRecord.indexOf('{')));
        loggedOp = checkBootRecordHeader(record, 1, "core", false, false, true).get(0);
        checkOpsEqual(op, loggedOp);

        file.delete();
        op = Util.getWriteAttributeOperation(createJsonFormatterAddress("escaped-formatter"), JsonAuditLogFormatterResourceDefinition.COMPACT.getName(), ModelNode.TRUE);
        executeForResult(op);
        fullRecord = readFullFileRecord(file);
        Assert.assertTrue(Pattern.matches("\\{.*", fullRecord)); //This regexp allows for new lines
        Assert.assertTrue(fullRecord.indexOf("#012") == -1);
        record = ModelNode.fromJSONString(fullRecord.substring(fullRecord.indexOf('{')));
        loggedOp = checkBootRecordHeader(record, 1, "core", false, false, true).get(0);
        checkOpsEqual(op, loggedOp);

        op = Util.getWriteAttributeOperation(createJsonFormatterAddress("escaped-formatter"), JsonAuditLogFormatterResourceDefinition.INCLUDE_DATE.getName(), ModelNode.TRUE);
        executeForResult(op);
        op = Util.getWriteAttributeOperation(createJsonFormatterAddress("escaped-formatter"), JsonAuditLogFormatterResourceDefinition.DATE_FORMAT.getName(), new ModelNode("yyyy/MM/dd HH-mm-ss"));
        executeForResult(op);
        file.delete();
        op = Util.getWriteAttributeOperation(createJsonFormatterAddress("escaped-formatter"), JsonAuditLogFormatterResourceDefinition.DATE_SEPARATOR.getName(), new ModelNode(" xxx "));
        executeForResult(op);
        fullRecord = readFullFileRecord(file);
        Assert.assertTrue(Pattern.matches("\\d\\d\\d\\d/\\d\\d/\\d\\d \\d\\d-\\d\\d-\\d\\d xxx \\{.*", fullRecord)); //This regexp checks for no new lines
        record = ModelNode.fromJSONString(fullRecord.substring(fullRecord.indexOf('{')));
        loggedOp = checkBootRecordHeader(record, 1, "core", false, false, true).get(0);
        checkOpsEqual(op, loggedOp);

        // remove the handler
        op = createRemoveHandlerReferenceOperation(rotatingHandlerName);
        executeForResult(op);
        op = createRemovePeriodicRotatingFileHandlerOperation(rotatingHandlerName);
        executeForResult(op);
    }

    @Test
    public void testRuntimeFailureMetricsAndRecycle() throws Exception {
        final String handlerName1 = "test-periodic-rotating-file";
        final String handlerName2 = "test-periodic-rotating-file2";

        File file1 = new File(logDir, LOG_FILE_NAME);
        File file2 = new File(logDir, LOG_FILE_NAME_PREFIX + "2.log");

        ModelNode op = createAddPeriodicRotatingFileHandlerOperation(handlerName1, "test-formatter", file1.getName(), SUFFIX);
        op.get(PeriodicRotatingFileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName()).set(2);
        executeForResult(op);
        op = createAddHandlerReferenceOperation(handlerName1);
        executeForResult(op);

        final ModelNode readResource = Util.createOperation(READ_RESOURCE_OPERATION, AUDIT_ADDR);
        readResource.get(ModelDescriptionConstants.RECURSIVE).set(true);
        readResource.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);

        ModelNode result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName1), 2, 0, false);

        //Delete the log directory so we start seeing failures in the file handler
        for (File file : logDir.listFiles()) {
            file.delete();
        }
        logDir.delete();

        Assert.assertFalse(file1.exists());
        Assert.assertFalse(file2.exists());

        op = createAddPeriodicRotatingFileHandlerOperation(handlerName2, "test-formatter", file2.getName(), SUFFIX);
        op.get(PeriodicRotatingFileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName()).set(1);
        executeForResult(op);

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName1), 2, 1, false);

        // this will re-create the log directory
        op = createAddHandlerReferenceOperation(handlerName2);
        executeForResult(op);

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName1), 2, 2, true);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName2), 1, 0, false);

        //Recycle the file handler so it resets the failure count and starts logging again
        executeForResult(Util.createOperation(ModelDescriptionConstants.RECYCLE, createPeriodicRotatingFileHandlerAddress(handlerName1)));

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(file2.exists());

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName1), 2, 0, false);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName2), 1, 0, false);

        //Finally just update the max failure counts and see that works
        op = Util.getWriteAttributeOperation(createPeriodicRotatingFileHandlerAddress(handlerName1), AuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName(), new ModelNode(7));
        executeForResult(op);
        op = Util.getWriteAttributeOperation(createPeriodicRotatingFileHandlerAddress(handlerName2), AuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName(), new ModelNode(4));
        executeForResult(op);

        result = executeForResult(readResource);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName1), 7, 0, false);
        checkHandlerRuntimeFailureMetrics(result.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName2), 4, 0, false);

        // remove handlers
        op = createRemoveHandlerReferenceOperation(handlerName1);
        executeForResult(op);
        op = createRemovePeriodicRotatingFileHandlerOperation(handlerName1);
        executeForResult(op);
        op = createRemoveHandlerReferenceOperation(handlerName2);
        executeForResult(op);
        op = createRemovePeriodicRotatingFileHandlerOperation(handlerName2);
        executeForResult(op);
    }

    @Test
    public void testSuffixValidator() throws Exception {
        final SuffixValidator validator = new SuffixValidator();
        try {
            validator.validateParameter("suffix", new ModelNode("s"));
            Assert.assertTrue("The model should be invalid", false);
        } catch (OperationFailedException e) {
            // no-op
        }
        try {
            //invalid pattern with one single quote
            validator.validateParameter("suffix", new ModelNode(".yyyy-MM-dd'custom suffix"));
            Assert.assertTrue("The model should be invalid", false);
        } catch (OperationFailedException e) {
            // no-op
        }
        //valid pattern with custom suffix
        validator.validateParameter("suffix", new ModelNode(".yyyy-MM-dd'custom suffix'"));
    }
}
