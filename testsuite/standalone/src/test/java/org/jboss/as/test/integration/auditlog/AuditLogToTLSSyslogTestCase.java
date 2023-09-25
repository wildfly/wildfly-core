/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.auditlog;

import org.junit.Assume;
import org.junit.BeforeClass;
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

    @BeforeClass
    public static void noJDK12Plus() {
        Assume.assumeFalse("Avoiding JDK 12 due to https://bugs.openjdk.java.net/browse/JDK-8219658", "12".equals(System.getProperty("java.specification.version")));
    }

}
