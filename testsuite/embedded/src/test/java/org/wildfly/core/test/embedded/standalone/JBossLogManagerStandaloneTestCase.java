/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.embedded.standalone;

import java.io.InputStream;
import java.util.logging.LogManager;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.test.embedded.LoggingTestCase;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JBossLogManagerStandaloneTestCase extends LoggingTestCase {


    @Test
    public void testLogManager() throws Exception {
        System.setProperty("test.log.file", "test-standalone-jbl.log");
        // The IBM JVM creates a logger early on and requires the log manager to be reconfigured
        if (isIbmJdk()) {
            // We need to reconfigure logging as the log manager is likely already setup
            try (InputStream in = getClass().getResourceAsStream("/jbl-logging.properties")) {
                Assert.assertNotNull("Failed to find the logging.properties file", in);
                LogManager.getLogManager().readConfiguration(in);
            }
        }
        System.setProperty("logging.configuration", getClass().getResource("/jbl-logging.properties").toExternalForm());
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        testStandalone(Configuration.LoggerHint.JBOSS_LOG_MANAGER, "test-standalone-jbl.log", "[jboss-logmanager]");
    }
}
