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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

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
 * Create a deployment with both per-deploy logging configuration file and logging profile.
 * Verify that per-deploy logging has preference to logging profile.
 * Set use-deployment-logging-config=false and verify that profile is used.
 *
 * @author <a href="mailto:pkremens@redhat.com">Petr Kremensky</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class LoggingPreferencesTestCase extends AbstractLoggingTestCase {

    private static final String DEPLOYMENT_LOG_MESSAGE = "Deployment logging configuration message.";
    private static final String LOGGING_PROFILE_MESSAGE = "Logging subsystem profile message.";
    private static final String PER_DEPLOY_ATTRIBUTE = "use-deployment-logging-config";

    private static final String PROFILE_NAME = "logging-preferences-profile";
    private static final String FILE_HANDLER_NAME = "customFileAppender";
    private static final String FILE_HANDLER_FILE_NAME = "custom-file-logger.log";
    private static final String PER_DEPLOY_FILE_NAME = "per-deploy-logging.log";

    private static final ModelNode PROFILE_ADDRESS = createAddress("logging-profile", PROFILE_NAME);
    private static final ModelNode ROOT_LOGGER_ADDRESS = createAddress("logging-profile", PROFILE_NAME, "root-logger", "ROOT");
    private static final ModelNode FILE_HANDLER_ADDRESS = createAddress("logging-profile", PROFILE_NAME, FILE_HANDLER, FILE_HANDLER_NAME);

    private Path profileLog;
    private Path perDeployLog;

    @Before
    public void prepareContainer() throws Exception {
        // Start the container
        container.start();

        if (profileLog == null) {
            profileLog = getAbsoluteLogFilePath(FILE_HANDLER_FILE_NAME);
            perDeployLog = getAbsoluteLogFilePath(PER_DEPLOY_FILE_NAME);
        }

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Create a new logging profile
        builder.addStep(Operations.createAddOperation(PROFILE_ADDRESS));

        // Add root logger to the profile
        builder.addStep(Operations.createAddOperation(ROOT_LOGGER_ADDRESS));

        // Add file handler to the profile
        ModelNode op = Operations.createAddOperation(FILE_HANDLER_ADDRESS);
        ModelNode file = new ModelNode();
        file.get(PATH).set(profileLog.normalize().toString());
        op.get(FILE).set(file);
        builder.addStep(op);

        executeOperation(builder.build());

        // Register file logger to root
        op = Operations.createOperation("add-handler", ROOT_LOGGER_ADDRESS);
        op.get(NAME).set(FILE_HANDLER_NAME);
        executeOperation(op);

        final JavaArchive archive = createDeployment(Collections.singletonMap("Logging-Profile", PROFILE_NAME))
                .addAsResource(LoggingPreferencesTestCase.class.getPackage(), "per-deploy-logging.properties", "META-INF/logging.properties");
        deploy(archive);
    }

    @After
    public void stopContainer() throws Exception {
        // Remove the servlet
        undeploy();

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Set use-deployment-logging-config to true
        builder.addStep(Operations.createWriteAttributeOperation(createAddress(), PER_DEPLOY_ATTRIBUTE, true));

        // Remove the logging profile
        builder.addStep(Operations.createRemoveOperation(PROFILE_ADDRESS));

        executeOperation(builder.build());

        // Stop the container
        container.stop();
        clearLogFiles();
    }

    @Test
    public void loggingPreferences() throws Exception {
        // Per-deploy logging test
        int statusCode = getResponse(DEPLOYMENT_LOG_MESSAGE);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        checkLogs(DEPLOYMENT_LOG_MESSAGE, perDeployLog, true);
        checkLogs(DEPLOYMENT_LOG_MESSAGE, profileLog, false);

        // Set use-deployment-logging-config to false
        executeOperation(Operations.createWriteAttributeOperation(createAddress(), PER_DEPLOY_ATTRIBUTE, false));

        // Restart the container and clean the logs
        container.stop();
        clearLogFiles();
        container.start();

        // Per-deploy logging disabled test
        statusCode = getResponse(LOGGING_PROFILE_MESSAGE);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        checkLogs(LOGGING_PROFILE_MESSAGE, perDeployLog, false);
        checkLogs(LOGGING_PROFILE_MESSAGE, profileLog, true);
    }

    private void clearLogFiles() throws IOException {
        Files.deleteIfExists(profileLog);
        Files.deleteIfExists(perDeployLog);
    }
}
