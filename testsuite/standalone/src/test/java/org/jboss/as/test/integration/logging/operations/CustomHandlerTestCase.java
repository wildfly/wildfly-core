/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.operations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.handlers.FileHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@ServerSetup(CustomHandlerTestCase.CustomHandlerSetUp.class)
@RunWith(WildFlyRunner.class)
public class CustomHandlerTestCase extends AbstractLoggingOperationsTestCase {

    private static final String FILE_NAME = "custom-handler-file.log";
    private static final String CUSTOM_HANDLER_NAME = "customFileHandler";
    private static final ModelNode CUSTOM_HANDLER_ADDRESS = createCustomHandlerAddress(CUSTOM_HANDLER_NAME);
    private Path logFile = null;

    @Before
    public void setLogFile() throws Exception {
        if (logFile == null) {
            logFile = getAbsoluteLogFilePath(FILE_NAME);
        }
    }

    @Test
    public void defaultLoggingTest() throws Exception {
        final String msg = "Logging test: CustomHandlerTestCase.defaultLoggingTest";
        searchLog(msg, true);
    }

    @Test
    public void disabledLoggerTest() throws Exception {
        setEnabled(CUSTOM_HANDLER_ADDRESS, false);

        try {
            final String msg = "Logging Test: CustomHandlerTestCase.disabledLoggerTest";
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

    static class CustomHandlerSetUp implements ServerSetupTask {

        private Path logFile;

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            logFile = getAbsoluteLogFilePath(FILE_NAME);

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // Create the custom handler
            ModelNode op = Operations.createAddOperation(CUSTOM_HANDLER_ADDRESS);
            op.get("class").set(FileHandler.class.getName());
            op.get("module").set("org.jboss.logmanager");
            ModelNode opProperties = op.get("properties").setEmptyObject();
            opProperties.get("fileName").set(logFile.normalize().toString());
            opProperties.get("autoFlush").set(true);
            builder.addStep(op);

            // Add the handler to the root-logger
            op = Operations.createOperation("add-handler", createRootLoggerAddress());
            op.get(ModelDescriptionConstants.NAME).set(CUSTOM_HANDLER_NAME);
            builder.addStep(op);

            executeOperation(builder.build());
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // Remove the handler from the root-logger
            ModelNode op = Operations.createOperation("remove-handler", createRootLoggerAddress());
            op.get(ModelDescriptionConstants.NAME).set(CUSTOM_HANDLER_NAME);
            builder.addStep(op);

            // Remove the custom handler
            op = Operations.createRemoveOperation(CUSTOM_HANDLER_ADDRESS);
            builder.addStep(op);

            executeOperation(builder.build());

            if (logFile != null) Files.deleteIfExists(logFile);

        }
    }
}
