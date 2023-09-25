/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.embedded.standalone;

import org.junit.Test;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.test.embedded.LoggingTestCase;

/**
 * @author <a href="mailto:nziakova@redhat.com">Nikoleta Ziakova</a>
 */
public class Log4j2StandaloneTestCase extends LoggingTestCase {

    @Test
    public void testLog4j2() throws Exception {
        System.setProperty("test.log.file", "test-standalone-log4j2.log");
        testStandalone(Configuration.LoggerHint.LOG4J2, "test-standalone-log4j2.log", "[log4j2]");
    }
}
