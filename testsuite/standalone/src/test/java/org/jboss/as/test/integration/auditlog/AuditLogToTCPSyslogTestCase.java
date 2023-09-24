/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TCP;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.syslogserver.TCPSyslogServerConfig;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.jboss.dmr.ModelNode;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.server.SyslogServerConfigIF;

/**
 * Tests TCP protocol of auditlog-to-syslog handler.
 *
 * @author Ondrej Lukas
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
//@RunAsClient
@ServerSetup(AuditLogToTCPSyslogTestCase.AuditLogToTCPSyslogTestCaseSetup.class)
public class AuditLogToTCPSyslogTestCase extends AuditLogToSyslogTestCase {

    /**
     * {@link org.wildfly.core.testrunner.ServerSetupTask} implementation which configures syslog server and auditlog-to-syslog
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
            ModelNode op = Util.createAddOperation(syslogHandlerAddress.append(PROTOCOL, TCP));
            op.get("message-transfer").set("OCTET_COUNTING");
            return op;
        }

    }

}
