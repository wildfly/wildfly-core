/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.embedded.standalone;

import java.nio.file.Files;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.StandaloneServer;
import org.wildfly.core.test.embedded.AbstractTestCase;
import org.wildfly.core.test.embedded.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SystemLoggerStandaloneTestCase extends AbstractTestCase {

    /**
     * Tests that a {@link System.Logger} logs messages. This is a regression test for WFCORE-6712.
     *
     * @throws Exception if an error occurs in the test
     */
    @Test
    public void standalone() throws Exception {
        // Configure the log manager settings
        final String logFileName = "test-standalone-system-logger.log";
        System.setProperty("test.log.file", logFileName);
        System.setProperty("logging.configuration", getClass().getResource("/jbl-logging.properties").toExternalForm());
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        // We need to explicitly override the JBoss Logging provider property as it's set in surefire
        final Configuration.LoggerHint loggerHint = Configuration.LoggerHint.JBOSS_LOG_MANAGER;
        System.setProperty("org.jboss.logging.provider", loggerHint.getProviderCode());

        // Create the embedded process
        final Configuration configuration = Environment.createConfigBuilder()
                .setLoggerHint(loggerHint)
                .build();
        final StandaloneServer server = EmbeddedProcessFactory.createStandaloneServer(configuration);
        startAndWaitFor(server, STANDALONE_CHECK);

        // Log a simple message with a System.Logger
        final System.Logger logger = System.getLogger(SystemLoggerStandaloneTestCase.class.getName());
        final String msg = "This is a test message from " + SystemLoggerStandaloneTestCase.class.getName() + " standalone.";
        logger.log(System.Logger.Level.INFO, msg);

        // Read all lines from the log file and look for the test message
        final List<String> logLines = Files.readAllLines(Environment.LOG_DIR.resolve(logFileName));
        Assert.assertTrue(String.format("Failed to find line \"%s\". Logs found%n: %s", msg, logLines), logLines.stream()
                .anyMatch(line -> line.endsWith(msg)));
    }
}
