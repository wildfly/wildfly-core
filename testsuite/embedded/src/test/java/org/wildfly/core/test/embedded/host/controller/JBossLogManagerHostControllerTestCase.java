/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.test.embedded.host.controller;

import org.junit.Assume;
import org.junit.Test;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.test.embedded.LoggingTestCase;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JBossLogManagerHostControllerTestCase extends LoggingTestCase {


    @Test
    public void testLogManager() throws Exception {
        // The IBM JDK creates a logger early on doesn't allow the JBoss Log Manager to be used. For now we will ignore
        // the test when the IBM JDK is being used.
        Assume.assumeFalse(isIbmJdk());
        System.setProperty("test.log.file", "test-standalone-jbl.log");
        System.setProperty("logging.configuration", getClass().getResource("/logging.properties").toExternalForm());
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        testHostController(Configuration.LoggerHint.JBOSS_LOG_MANAGER, "test-standalone-jbl.log", "[jboss-logmanager]");
    }
}
