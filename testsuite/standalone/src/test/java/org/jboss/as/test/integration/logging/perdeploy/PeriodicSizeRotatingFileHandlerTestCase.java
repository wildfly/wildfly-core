/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.perdeploy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.logmanager.handlers.PeriodicSizeRotatingFileHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
public class PeriodicSizeRotatingFileHandlerTestCase extends AbstractRotatingFileHandlerTestCase {

    private static final String FILE_NAME = "config-periodic-size-rotating.log";
    private static final String LOGGER_NAME = PeriodicSizeRotatingFileHandlerTestCase.class.getName();

    @BeforeClass
    public static void setup() throws Exception {
        deploy(createDeployment(
                createLoggingConfiguration(PeriodicSizeRotatingFileHandler.class, FILE_NAME,
                        Collections.singletonMap("rotateSize", "5120"))), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testRotate() throws Exception {
        final String msg = "This is a periodic size rotate test";
        final Map<String, String> params = new HashMap<>();
        params.put(LoggingServiceActivator.LOG_COUNT_KEY, "100");
        params.put(LoggingServiceActivator.LOG_NAME_KEY, LOGGER_NAME);
        executeRequest(FILE_NAME, msg, params);
    }
}
