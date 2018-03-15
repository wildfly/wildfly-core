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

package org.wildfly.core.test.embedded.standalone;

import org.junit.Test;
import org.wildfly.core.test.embedded.LoggingTestCase;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JBossLogManagerStandaloneTestCase extends LoggingTestCase {


    @Test
    public void testLogManager() throws Exception {
        System.setProperty("test.log.file", "test-standalone-jbl.log");
        System.setProperty("org.jboss.logging.provider", "jboss");
        testStandalone("org.jboss.logmanager", "test-standalone-jbl.log");
    }
}
