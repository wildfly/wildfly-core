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

package org.jboss.as.test.manualmode.logging;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * This test ensures that log4j appenders weren't replaced and still log correctly after the server has been restarted.
 * Confirms the logging.properties file was wrote log4j appenders properly as well. See WFLY-1379 for further details.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class Log4jAppenderTestCase extends AbstractLoggingTestCase {

    private static final String FILE_NAME = "log4j-appender-file.log";
    private static final String CUSTOM_HANDLER_NAME = "customFileAppender";
    private static final ModelNode CUSTOM_HANDLER_ADDRESS = createAddress("custom-handler", CUSTOM_HANDLER_NAME);
    private static final ModelNode ROOT_LOGGER_ADDRESS = createAddress("root-logger", "ROOT");

    private Path logFile;

    @Before
    public void startContainer() throws Exception {
        // Start the server
        container.start();

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
        op = Operations.createOperation("add-handler", ROOT_LOGGER_ADDRESS);
        op.get(ModelDescriptionConstants.NAME).set(CUSTOM_HANDLER_NAME);
        builder.addStep(op);

        executeOperation(builder.build());

        // Stop the container
        container.stop();
        // Start the server again
        container.start();

        Assert.assertTrue("Container is not started", container.isStarted());

        // Deploy the servlet
        deploy(createDeployment(Log4jServiceActivator.class, Log4jServiceActivator.DEPENDENCIES), DEPLOYMENT_NAME);
    }

    @After
    public void stopContainer() throws Exception {
        // Remove the servlet
        undeploy();

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Remove the handler from the root-logger
        ModelNode op = Operations.createOperation("remove-handler", ROOT_LOGGER_ADDRESS);
        op.get(ModelDescriptionConstants.NAME).set(CUSTOM_HANDLER_NAME);
        builder.addStep(op);

        // Remove the custom handler
        builder.addStep(Operations.createRemoveOperation(CUSTOM_HANDLER_ADDRESS));

        executeOperation(builder.build());

        Files.deleteIfExists(logFile);

        // Stop the container
        container.stop();
    }

    @Test
    public void logAfterReload() throws Exception {
        // Write the message to the server
        final String msg = "Logging test: Log4jCustomHandlerTestCase.logAfterReload";
        searchLog(msg, true);
    }

    private void searchLog(final String msg, final boolean expected) throws Exception {
        final int statusCode = getResponse(msg);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            boolean logFound = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains(msg)) {
                    logFound = true;
                    break;
                }
            }
            Assert.assertTrue(logFound == expected);
        }
    }
}
