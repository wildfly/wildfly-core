/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.APP_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FACILITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.test.syslogserver.BlockedSyslogServerEventHandler;
import org.jboss.as.test.syslogserver.Rfc5424SyslogEvent;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Test that syslog-handler logs in Audit Log.
 *
 * @author: Ondrej Lukas
 * @author: Josef Cacek
 */
public abstract class AuditLogToSyslogTestCase {

    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);

    @Inject
    protected ManagementClient managementClient;

    protected static SyslogServerIF server;

    private List<Long> properties = new ArrayList<Long>();

    /**
     * Tests following steps in a test syslog server.
     * <ol>
     * <li>throw auditable event with auditlog disabled - check no message came to the syslog</li>
     * <li>enable the auditlog (it's auditable event itself) - check the message in syslog</li>
     * <li>throw auditable event with auditlog enabled - check the mesage in syslog</li>
     * <li>disable and check auditlog (it's auditable event itself) - check the message in syslog</li>
     * <li>check auditable event with auditlog disabled - check no message came to the syslog</li>
     * </ol>
     *
     * @throws Exception
     */
    @Test
    public void testAuditLoggingToSyslog() throws Exception {
        final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
        queue.clear();

        SyslogServerEventIF syslogEvent = null;
        makeOneLog();
        syslogEvent = queue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("No message was expected in the syslog, because syslog is disabled", syslogEvent);

        try {
            setAuditlogEnabled(true);
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Enabling audit log wasn't logged into the syslog", syslogEvent);
            Assert.assertEquals(1, syslogEvent.getFacility());
            assertAppName(AuditLogToSyslogSetup.DEFAULT_APPNAME, syslogEvent);

            makeOneLog();
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Auditable event was't logged into the syslog (adding system property)", syslogEvent);
            Assert.assertEquals(1, syslogEvent.getFacility());
            assertAppName(AuditLogToSyslogSetup.DEFAULT_APPNAME, syslogEvent);

            // disable audit log - auditable event
            setAuditlogEnabled(false);
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Disabling audit log wasn't logged into the syslog", syslogEvent);
            Assert.assertEquals(1, syslogEvent.getFacility());
            assertAppName(AuditLogToSyslogSetup.DEFAULT_APPNAME, syslogEvent);

            //remove handler
            CoreUtils.applyUpdate(Util.createRemoveOperation(AuditLogToSyslogSetup.AUDIT_LOG_LOGGER_SYSLOG_HANDLER_ADDR), managementClient.getControllerClient());
            syslogEvent = queue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNull("No message was expected in the syslog, because syslog is disabled", syslogEvent);

            //add other handler which has another appname and facility = LINE_PRINTER (6)
            CoreUtils.applyUpdate(Util.createAddOperation(AuditLogToSyslogSetup.AUDIT_LOG_LOGGER_SYSLOG_HANDLER_ADDR2), managementClient.getControllerClient());
            syslogEvent = queue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNull("No message was expected in the syslog, because syslog is disabled", syslogEvent);

            // enable audit log again
            setAuditlogEnabled(true);
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Enabling audit log wasn't logged into the syslog", syslogEvent);
            Assert.assertEquals(6, syslogEvent.getFacility());
            assertAppName("TestApp", syslogEvent);

            //Change other handler app name
            CoreUtils.applyUpdate(Util.getWriteAttributeOperation(AuditLogToSyslogSetup.AUDIT_SYSLOG_HANDLER_ADDR2, APP_NAME, new ModelNode("Stuff")), managementClient.getControllerClient());
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Auditable event was't logged into the syslog (setting new app-name in audit-log)", syslogEvent);
            Assert.assertEquals(6, syslogEvent.getFacility());
            assertAppName("Stuff", syslogEvent);

            //Reset other handler app name
            CoreUtils.applyUpdate(Util.getWriteAttributeOperation(AuditLogToSyslogSetup.AUDIT_SYSLOG_HANDLER_ADDR2, APP_NAME, new ModelNode()), managementClient.getControllerClient());
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Auditable event was't logged into the syslog (setting new app-name in audit-log)", syslogEvent);
            Assert.assertEquals(6, syslogEvent.getFacility());
            assertAppName(AuditLogToSyslogSetup.DEFAULT_APPNAME, syslogEvent);

            //Change other handler facility = LOCAL_USE_0 (16)
            CoreUtils.applyUpdate(Util.getWriteAttributeOperation(AuditLogToSyslogSetup.AUDIT_SYSLOG_HANDLER_ADDR2, FACILITY, new ModelNode("LOCAL_USE_0")), managementClient.getControllerClient());
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Auditable event was't logged into the syslog (setting new facility in audit-log)", syslogEvent);
            Assert.assertEquals(16, syslogEvent.getFacility());
            assertAppName(AuditLogToSyslogSetup.DEFAULT_APPNAME, syslogEvent);

            //Reset other handler facility
            CoreUtils.applyUpdate(Util.getWriteAttributeOperation(AuditLogToSyslogSetup.AUDIT_SYSLOG_HANDLER_ADDR2, FACILITY, new ModelNode()), managementClient.getControllerClient());
            syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            Assert.assertNotNull("Auditable event was't logged into the syslog (setting new facility in audit-log)", syslogEvent);
            Assert.assertEquals(1, syslogEvent.getFacility());
            assertAppName(AuditLogToSyslogSetup.DEFAULT_APPNAME, syslogEvent);

        } finally {
            setAuditlogEnabled(false);
        }
        syslogEvent = queue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("Disabling audit log wasn't logged into the syslog", syslogEvent);
        makeOneLog();
        syslogEvent = queue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("No message was expected in the syslog, because syslog is disabled", syslogEvent);

        for (Long property : properties) {
            CoreUtils.applyUpdate(
                    Util.createRemoveOperation(PathAddress.pathAddress().append(SYSTEM_PROPERTY, Long.toString(property))),
                    managementClient.getControllerClient());
        }
        properties.clear();
    }

    void assertAppName(String expected, SyslogServerEventIF syslogEvent) {
        Rfc5424SyslogEvent event = (Rfc5424SyslogEvent) syslogEvent;
        Assert.assertEquals(expected, event.getAppName());
    }

    /**
     * Throws auditable event. This implemetation writes a system-property to an AS configuration
     *
     * @throws Exception
     */
    protected void makeOneLog() throws Exception {
        long timeStamp = System.currentTimeMillis();
        properties.add(Long.valueOf(timeStamp));
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress().append(SYSTEM_PROPERTY, Long.toString(timeStamp)));
        op.get(NAME).set(NAME);
        op.get(VALUE).set("someValue");
        CoreUtils.applyUpdate(op, managementClient.getControllerClient());
    }

    /**
     * Enables/disables the auditlog.
     *
     * @throws Exception
     */
    private void setAuditlogEnabled(boolean value) throws Exception {
        ModelNode op = Util.getWriteAttributeOperation(AuditLogToSyslogSetup.AUDIT_LOG_LOGGER_ADDR, ENABLED, value);
        CoreUtils.applyUpdate(op, managementClient.getControllerClient());
    }

}
