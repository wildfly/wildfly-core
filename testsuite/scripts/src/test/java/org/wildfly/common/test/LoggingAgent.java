/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.common.test;

import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingAgent {
    public static final String LOGGER_NAME = LoggingAgent.class.getName();
    public static final String MSG = "This is a message from the agent";
    public static final String DEBUG_ARG = "debug";

    public static void premain(final String args, final Instrumentation instrumentation) throws ClassNotFoundException {
        assert "org.jboss.logmanager.LogManager".equals(System.getProperty("java.util.logging.manager"));
        assert Logger.class.isAssignableFrom(Class.forName("org.jboss.logmanager.Logger"));
        final Logger logger = Logger.getLogger(LOGGER_NAME);
        if (args != null && args.equals(DEBUG_ARG)) {
            logger.fine(MSG);
        }
        logger.info(MSG);
    }
}
