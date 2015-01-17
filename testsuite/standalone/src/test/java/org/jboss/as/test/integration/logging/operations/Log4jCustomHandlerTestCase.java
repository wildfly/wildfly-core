/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.logging.operations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.Log4jServiceActivator;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@ServerSetup(Log4jCustomHandlerTestCase.Log4jCustomHandlerSetUp.class)
@RunWith(WildflyTestRunner.class)
public class Log4jCustomHandlerTestCase extends AbstractLoggingTestCase {

    private static final String FILE_NAME = "log4j-appender-file.log";
    private static final String CUSTOM_HANDLER_NAME = "customFileAppender";
    private static final ModelNode CUSTOM_HANDLER_ADDRESS = createAddress("custom-handler", CUSTOM_HANDLER_NAME);
    private final Path logFile = getAbsoluteLogFilePath(FILE_NAME);

    @BeforeClass
    public static void deploy() throws Exception {
        final JavaArchive archive = createDeployment(Log4jServiceActivator.class, Log4jServiceActivator.DEPENDENCIES);
        deploy(archive, DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void defaultLoggingTest() throws Exception {
        final String msg = "Logging test: Log4jCustomHandlerTestCase.defaultLoggingTest";
        searchLog(msg, true);
    }

    @Test
    public void disabledLoggerTest() throws Exception {
        setEnabled(CUSTOM_HANDLER_ADDRESS, false);

        try {
            final String msg = "Logging Test: Log4jCustomHandlerTestCase.disabledLoggerTest";
            searchLog(msg, false);
        } finally {
            setEnabled(CUSTOM_HANDLER_ADDRESS, true);
        }
    }

    private void searchLog(final String msg, final boolean expected) throws Exception {
        int statusCode = getResponse(msg);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        boolean logFound = false;
        for (String s : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (s.contains(msg)) {
                logFound = true;
                break;
            }
        }
        Assert.assertTrue(msg + " not found in " + logFile, logFound == expected);
    }

    static class Log4jCustomHandlerSetUp implements ServerSetupTask {

        private Path logFile;

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            logFile = getAbsoluteLogFilePath(FILE_NAME);

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // Create the custom handler
            ModelNode op = Operations.createAddOperation(CUSTOM_HANDLER_ADDRESS);
            op.get("class").set("org.apache.log4j.FileAppender");
            op.get("module").set("org.apache.log4j");
            ModelNode opProperties = op.get("properties").setEmptyObject();
            opProperties.get("file").set(logFile.normalize().toString());
            opProperties.get("immediateFlush").set(true);
            builder.addStep(op);

            // Add the handler to the root-logger
            op = Operations.createOperation("add-handler", createAddress("root-logger", "ROOT"));
            op.get(ModelDescriptionConstants.NAME).set(CUSTOM_HANDLER_NAME);
            builder.addStep(op);

            executeOperation(builder.build());
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // Remove the handler from the root-logger
            final ModelNode op = Operations.createOperation("remove-handler", createAddress("root-logger", "ROOT"));
            op.get(ModelDescriptionConstants.NAME).set(CUSTOM_HANDLER_NAME);
            builder.addStep(op);

            // Remove the custom handler
            builder.addStep(Operations.createRemoveOperation(CUSTOM_HANDLER_ADDRESS));

            executeOperation(builder.build());

            if (logFile != null) Files.deleteIfExists(logFile);

        }

    }
}
