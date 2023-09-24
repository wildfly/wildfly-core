/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Create a deployment with per-deploy logging configuration file.
 * Verify that per-deploy logging has preference to logging subsystem (use-deployment-logging-config=true).
 * Set use-deployment-logging-config=false and verify that per-deploy configuration is no longer used.
 *
 * @author <a href="mailto:pkremens@redhat.com">Petr Kremensky</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class PerDeployLoggingTestCase extends AbstractLoggingTestCase {

    private static final String DEPLOYMENT_LOG_MESSAGE = "Deployment logging configuration message.";
    private static final String LOGGING_SUBSYSTEM_MESSAGE = "Per deploy logging disabled message.";
    private static final String PER_DEPLOY_ATTRIBUTE = "use-deployment-logging-config";
    private static final String FILE_HANDLER_NAME = "customFileAppender";
    private static final String FILE_HANDLER_FILE_NAME = "custom-file-logger.log";
    private static final String PER_DEPLOY_FILE_NAME = "per-deploy-logging.log";

    private static final ModelNode ROOT_LOGGER_ADDRESS = createAddress("root-logger", "ROOT");
    private static final ModelNode FILE_HANDLER_ADDRESS = createAddress(FILE_HANDLER, FILE_HANDLER_NAME);

    private Path customLog;
    private Path perDeployLog;

    @Before
    public void prepareContainer() throws Exception {
        // Start the container
        Assert.assertNotNull(container);
        container.start();

        customLog = getAbsoluteLogFilePath(FILE_HANDLER_FILE_NAME);
        perDeployLog = getAbsoluteLogFilePath(PER_DEPLOY_FILE_NAME);

        // Create the custom file handler
        ModelNode op = Operations.createAddOperation(FILE_HANDLER_ADDRESS);
        ModelNode file = new ModelNode();
        file.get(PATH).set(customLog.normalize().toString());
        op.get(FILE).set(file);
        executeOperation(op);

        // Add the handler to the root-logger
        op = Operations.createOperation("add-handler", ROOT_LOGGER_ADDRESS);
        op.get(NAME).set(FILE_HANDLER_NAME);
        executeOperation(op);

        final JavaArchive archive = createDeployment()
                .addAsResource(PerDeployLoggingTestCase.class.getPackage(), "per-deploy-logging.properties", "META-INF/logging.properties");
        deploy(archive);
    }

    @After
    public void stopContainer() throws Exception {
        Assert.assertNotNull(container);
        Assert.assertTrue(container.isStarted()); // if container is not started, we get a NPE in container.getClient() within undeploy()
        // Remove the servlet
        undeploy();

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Set use-deployment-logging-config to true
        builder.addStep(Operations.createWriteAttributeOperation(createAddress(), PER_DEPLOY_ATTRIBUTE, true));

        // Remove the custom handler from the root-logger
        final ModelNode op = Operations.createOperation("remove-handler", ROOT_LOGGER_ADDRESS);
        op.get(NAME).set(FILE_HANDLER_NAME);
        builder.addStep(op);

        // Remove the custom file handler
        builder.addStep(Operations.createRemoveOperation(FILE_HANDLER_ADDRESS));

        executeOperation(builder.build());

        // Stop the container
        container.stop();
        clearLogFiles();
    }

    @Test
    public void disablePerDeployLogging() throws Exception {
        // Per-deploy logging test
        int statusCode = getResponse(DEPLOYMENT_LOG_MESSAGE);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        checkLogs(DEPLOYMENT_LOG_MESSAGE, perDeployLog, true);
        checkLogs(DEPLOYMENT_LOG_MESSAGE, customLog, false);

        // Set use-deployment-logging-config to false
        executeOperation(Operations.createWriteAttributeOperation(createAddress(), PER_DEPLOY_ATTRIBUTE, false));

        // Restart the container and clean the logs
        container.stop();
        clearLogFiles();
        container.start();

        // Per-deploy logging disabled test
        statusCode = getResponse(LOGGING_SUBSYSTEM_MESSAGE);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        checkLogs(LOGGING_SUBSYSTEM_MESSAGE, perDeployLog, false);
        checkLogs(LOGGING_SUBSYSTEM_MESSAGE, customLog, true);
    }

    private void clearLogFiles() throws IOException {
        Files.deleteIfExists(customLog);
        Files.deleteIfExists(perDeployLog);
    }
}
