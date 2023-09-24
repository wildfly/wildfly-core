/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.auditlog;

import org.jboss.as.test.categories.CommonCriteria;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests UDP protocol of auditlog-to-syslog handler.
 *
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(AuditLogToUDPSyslogSetup.class)
@Category(CommonCriteria.class)
public class AuditLogToUDPSyslogTestCase extends AuditLogToSyslogTestCase {

    // nothing to do here
}
