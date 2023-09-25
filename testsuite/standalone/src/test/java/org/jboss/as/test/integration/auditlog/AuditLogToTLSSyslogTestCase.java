/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.auditlog;

import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.junit.runner.RunWith;

/**
 * Tests TLS protocol of auditlog-to-syslog handler.
 *
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
//@RunAsClient
@ServerSetup(AuditLogToTLSSyslogSetup.class)
public class AuditLogToTLSSyslogTestCase extends AuditLogToSyslogTestCase {
}
