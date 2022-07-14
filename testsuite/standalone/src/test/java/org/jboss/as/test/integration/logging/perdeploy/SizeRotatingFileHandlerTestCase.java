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

package org.jboss.as.test.integration.logging.perdeploy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.logmanager.handlers.SizeRotatingFileHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
public class SizeRotatingFileHandlerTestCase extends AbstractRotatingFileHandlerTestCase {

    private static final String FILE_NAME = "config-size-rotating.log";
    private static final String LOGGER_NAME = SizeRotatingFileHandlerTestCase.class.getName();

    @BeforeClass
    public static void setup() throws Exception {
        deploy(createDeployment(
                createLoggingConfiguration(SizeRotatingFileHandler.class, FILE_NAME,
                        Collections.singletonMap("rotateSize", "5120"))), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testSizeRotate() throws Exception {
        final String msg = "This is a size rotate test";
        final Map<String, String> params = new HashMap<>();
        params.put(LoggingServiceActivator.LOG_COUNT_KEY, "100");
        params.put(LoggingServiceActivator.LOG_NAME_KEY, LOGGER_NAME);
        executeRequest(FILE_NAME, msg, params);
    }
}
