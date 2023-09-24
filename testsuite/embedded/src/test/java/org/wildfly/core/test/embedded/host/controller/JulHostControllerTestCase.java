/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.embedded.host.controller;

import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.FileHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.test.embedded.Environment;
import org.wildfly.core.test.embedded.LoggingTestCase;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JulHostControllerTestCase extends LoggingTestCase {

    private Handler currentHandler;

    @Before
    public void configureLogManager() throws Exception {
        final PatternFormatter formatter = new PatternFormatter("[jul] %d{HH:mm:ss,SSS} %-5p [%c] %s%e%n");
        final Path logFile = Environment.LOG_DIR.resolve("test-hc-jul.log");
        // We will use a JBoss File handler here for convenience
        final FileHandler handler = new FileHandler(formatter, logFile.toFile(), false);
        handler.setAutoFlush(true);
        currentHandler = handler;
        Logger.getLogger("").addHandler(handler);
    }

    @After
    public void resetLogManager() {
        Logger.getLogger("").removeHandler(currentHandler);
    }

    @Test
    public void testLogManager() throws Exception {
        testHostController(Configuration.LoggerHint.JUL, "test-hc-jul.log", "[jul]", false);
    }
}
