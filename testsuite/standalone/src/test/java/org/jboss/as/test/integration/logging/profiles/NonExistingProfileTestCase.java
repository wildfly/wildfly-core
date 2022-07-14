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
package org.jboss.as.test.integration.logging.profiles;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Petr Křemenský <pkremens@redhat.com>
 */
@ServerSetup(NonExistingProfileTestCase.NonExistingProfileTestCaseSetup.class)
@RunWith(WildFlyRunner.class)
public class NonExistingProfileTestCase extends AbstractLoggingTestCase {

    private static final String LOG_FILE_NAME = "non-existing-profile-test.log";
    private static final Path loggingTestLog = Paths.get(resolveRelativePath("jboss.server.log.dir"), LOG_FILE_NAME);

    static class NonExistingProfileTestCaseSetup implements ServerSetupTask {
        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // add custom file-handler
            ModelNode op = Operations.createAddOperation(createAddress("periodic-rotating-file-handler", "LOGGING_TEST"));
            op.get("append").set("true");
            op.get("suffix").set(".yyyy-MM-dd");
            ModelNode file = new ModelNode();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(LOG_FILE_NAME);
            op.get("file").set(file);
            op.get("formatter").set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");
            builder.addStep(op);

            // add handler to root-logger
            op = Operations.createOperation("add-handler", createAddress("root-logger", "ROOT"));
            op.get("name").set("LOGGING_TEST");
            builder.addStep(op);

            executeOperation(builder.build());
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // remove LOGGING_TEST from root-logger
            ModelNode op = Operations.createOperation("remove-handler", createAddress("root-logger", "ROOT"));
            op.get("name").set("LOGGING_TEST");
            builder.addStep(op);

            // remove custom file handler
            builder.addStep(Operations.createRemoveOperation(createAddress("periodic-rotating-file-handler", "LOGGING_TEST")));

            executeOperation(builder.build());

            Files.deleteIfExists(loggingTestLog);
        }
    }

    @BeforeClass
    public static void deploy() throws Exception {
        deploy(createDeployment(Collections.singletonMap("Logging-Profile", "non-existing-profile")), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void warningMessageTest() throws IOException {
        boolean warningFound = false;
        try (final BufferedReader reader = Files.newBufferedReader(loggingTestLog, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Look for profile id
                if (line.contains("non-existing-profile")) {
                    warningFound = true;
                    break;
                }
            }
        }
        Assert.assertTrue(warningFound);
    }

    @Test
    public void defaultLoggingTest() throws IOException {
        final String msg = "defaultLoggingTest: This is a test message";
        final int statusCode = getResponse(msg);
        assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        // check logs
        boolean logFound = false;
        try (final BufferedReader br = Files.newBufferedReader(loggingTestLog, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(msg)) {
                    logFound = true;
                    break;
                }
            }
        }
        Assert.assertTrue(logFound);
    }

    @Test
    public void testDeploymentConfigurationResource() throws Exception {
        // Get the resulting model
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME, "default");

        Assert.assertTrue("No logging subsystem resource found on the deployment", loggingConfiguration.isDefined());

        // Check the handler exists on the configuration
        final ModelNode handler = loggingConfiguration.get("handler", "CONSOLE");
        Assert.assertTrue("The CONSOLE handler was not found effective configuration", handler.isDefined());
    }
}
