/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
