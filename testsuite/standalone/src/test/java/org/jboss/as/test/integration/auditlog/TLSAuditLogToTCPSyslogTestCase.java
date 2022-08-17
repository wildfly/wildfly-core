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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TLS;

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
import org.jboss.as.test.syslogserver.TCPSyslogServerConfig;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.server.SyslogServerConfigIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests that plain TCP messages are not sent when TLS syslog-handler is selected in Audit Log settings. <br>
 * Regression test for WFCORE-190.
 *
 * @author: Josef Cacek
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(TLSAuditLogToTCPSyslogTestCase.AuditLogToTCPSyslogTestCaseSetup.class)
public class TLSAuditLogToTCPSyslogTestCase {

    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);

    @Inject
    private ManagementClient managementClient;

    protected static SyslogServerIF server;

    private List<Long> properties = new ArrayList<Long>();

    @Test
    public void testAuditLoggingToSyslog() throws Exception {
        final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
        queue.clear();

        SyslogServerEventIF syslogEvent = null;
        try {
            setAuditlogEnabled(true);
            // enabling audit-log is auditable event
            syslogEvent = queue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
            // but we don't expect a message in TCP syslog server
            Assert.assertNull("No message was expected in the syslog, because TCP syslog server is used", syslogEvent);
        } finally {
            setAuditlogEnabled(false);
        }

        for (Long property : properties) {
            CoreUtils.applyUpdate(
                    Util.createRemoveOperation(PathAddress.pathAddress().append(SYSTEM_PROPERTY, Long.toString(property))),
                    managementClient.getControllerClient());
        }
        properties.clear();
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

    /**
     * {@link org.jboss.as.arquillian.api.ServerSetupTask} implementation which configures syslog server and auditlog-to-syslog
     * handler for this test.
     */
    static class AuditLogToTCPSyslogTestCaseSetup extends AuditLogToSyslogSetup {

        @Override
        protected String getSyslogProtocol() {
            return SyslogConstants.TCP;
        }

        @Override
        protected SyslogServerConfigIF getSyslogConfig() {
            return new TCPSyslogServerConfig();
        }

        @Override
        protected ModelNode addAuditlogSyslogProtocol(PathAddress syslogHandlerAddress) {
            ModelNode op = Util.createAddOperation(syslogHandlerAddress.append(PROTOCOL, TLS));
            op.get("message-transfer").set("OCTET_COUNTING");
            return op;
        }

    }

}
