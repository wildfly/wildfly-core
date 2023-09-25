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
public class LogbackStandaloneTestCase extends LoggingTestCase {

    @Test
    public void testLogback() throws Exception {
        System.setProperty("test.log.file", "test-standalone-logback.log");
        testStandalone(Configuration.LoggerHint.LOGBACK, "test-standalone-logback.log", "[logback]");
    }
}
