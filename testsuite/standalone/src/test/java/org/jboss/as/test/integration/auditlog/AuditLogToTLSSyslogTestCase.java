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

import org.junit.Assume;
import org.junit.BeforeClass;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.junit.runner.RunWith;

/**
 * Tests TLS protocol of auditlog-to-syslog handler.
 *
 * @author Josef Cacek
 */
@RunWith(WildflyTestRunner.class)
//@RunAsClient
@ServerSetup(AuditLogToTLSSyslogSetup.class)
public class AuditLogToTLSSyslogTestCase extends AuditLogToSyslogTestCase {

    @BeforeClass
    public static void noJDK12Plus() {
        Assume.assumeFalse("Avoiding JDK 12 due to https://bugs.openjdk.java.net/browse/JDK-8219658", "12".equals(System.getProperty("java.specification.version")));
    }

}
