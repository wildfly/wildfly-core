/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.auditlog;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.auditlog.AuditLogToUDPSyslogSetup;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.test.syslogserver.BlockedSyslogServerEventHandler;
import org.jboss.as.test.syslogserver.Rfc5424SyslogEvent;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.xnio.IoUtils;

/**
 * Tests that fields of Audit log have right content.
 *
 * @author Ondrej Lukas
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
@Category(CommonCriteria.class)
public class AuditLogFieldsOfLogTestCase extends AbstractLogFieldsOfLogTestCase {

    private final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);
    private static final AuditLogToUDPSyslogSetup SYSLOG_SETUP = new AuditLogToUDPSyslogSetup();
    private final PathAddress auditLogConfigAddress = PathAddress.pathAddress(CoreManagementResourceDefinition.PATH_ELEMENT,
            AccessAuditResourceDefinition.PATH_ELEMENT, AuditLogLoggerResourceDefinition.PATH_ELEMENT);

    @Inject
    private ServerController container;

    @Before
    public void beforeTest() throws Exception {
        Files.deleteIfExists(FILE);

        Assert.assertNotNull(container);
        container.start();
        final ModelControllerClient client = container.getClient().getControllerClient();

        SYSLOG_SETUP.setup(container.getClient());

        final CompositeOperationBuilder compositeOp = CompositeOperationBuilder.create();

        configureUser(client, compositeOp);

        // Don't log boot operations by default
        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_BOOT.getName(), ModelNode.FALSE));
        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress, AuditLogLoggerResourceDefinition.ENABLED.getName(),
                ModelNode.TRUE));

        executeForSuccess(client, compositeOp.build());

        ServerReload.executeReloadAndWaitForCompletion(client);
    }

    @After
    public void afterTest() throws Exception {
        Assert.assertNotNull(container);
        Assert.assertTrue(container.isStarted()); // if container is not started, we get a NPE in container.getClient()
        SYSLOG_SETUP.tearDown(container.getClient());
        final ModelControllerClient client = container.getClient().getControllerClient();
        final CompositeOperationBuilder compositeOp = CompositeOperationBuilder.create();

        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.ENABLED.getName(), ModelNode.FALSE));

        resetUser(compositeOp);

        try {
            executeForSuccess(client, compositeOp.build());
        } finally {
            try {
                // Stop the container
                container.stop();
            } finally {
                IoUtils.safeClose(client);
                Files.deleteIfExists(FILE);
            }
        }
    }

    /**
     * @test.objective Test whether fields in Audit Log have right content
     * @test.expectedResult All asserts are correct and test finishes without any exception.
     */
    @Test
    public void testAuditLoggingFields() throws Exception {
        queue.clear();
        SyslogServerEventIF syslogEvent = null;

        Assert.assertTrue(makeOneLog());
        syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("Event wasn't logged into the syslog", syslogEvent);

        Rfc5424SyslogEvent event = (Rfc5424SyslogEvent) syslogEvent;
        String message = event.getMessage();
        Assert.assertNotNull("Message in the syslog event is empty", message);
        message = DATE_STAMP_PATTERN.matcher(message).replaceFirst("{");
        ModelNode syslogNode = ModelNode.fromJSONString(message);
        checkLog("Syslog", syslogNode);
        //Since JMX audit logging is not enabled for this test, we should not need to trim the records for WFCORE-2997
        List<ModelNode> logs = readFile(1, false);
        ModelNode log = logs.get(0);
        checkLog("File", log);
    }

    private void checkLog(String handler, ModelNode log) {
        final String failMsg = "Unexpected value in " + handler;
        Assert.assertEquals(failMsg, "core", log.get("type").asString());
        Assert.assertEquals(failMsg, "false", log.get("r/o").asString());
        Assert.assertEquals(failMsg, "false", log.get("booting").asString());
        Assert.assertTrue(failMsg, log.get("version").isDefined());
        Assert.assertEquals(failMsg, "IAmAdmin", log.get("user").asString());
        Assert.assertFalse(failMsg, log.get("domainUUID").isDefined());
        Assert.assertEquals(failMsg, "NATIVE", log.get("access").asString());
        Assert.assertTrue(failMsg, log.get("remote-address").isDefined());
        Assert.assertEquals(failMsg, "true", log.get("success").asString());
        List<ModelNode> operations = log.get("ops").asList();
        Assert.assertEquals(failMsg, 1, operations.size());
    }

    private boolean makeOneLog() throws IOException {
        ModelNode op = Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_BOOT.getName(), ModelNode.TRUE);
        ModelNode result = container.getClient().getControllerClient().execute(op);
        return Operations.isSuccessfulOutcome(result);
    }
}
