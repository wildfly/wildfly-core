/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.profiles;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * @author Petr Křemenský <pkremens@redhat.com>
 */
abstract class AbstractLoggingProfilesTestCase extends AbstractLoggingTestCase {

    private static final String PROFILE1 = "dummy-profile1";
    private static final String PROFILE2 = "dummy-profile2";
    private static final String RUNTIME_NAME1 = "logging1.jar";
    private static final String RUNTIME_NAME2 = "logging2.jar";

    private static final String LOG_FILE_NAME = "profiles-test.log";
    private static final String PROFILE1_LOG_NAME = "dummy-profile1.log";
    private static final String PROFILE2_LOG_NAME = "dummy-profile2.log";
    private static final String CHANGED_LOG_NAME = "dummy-profile1-changed.log";
    private static final String LOG_DIR = resolveRelativePath("jboss.server.log.dir");
    private static final Path logFile = Paths.get(LOG_DIR, LOG_FILE_NAME);
    private static final Path dummyLog1 = Paths.get(LOG_DIR, PROFILE1_LOG_NAME);
    private static final Path dummyLog2 = Paths.get(LOG_DIR, PROFILE2_LOG_NAME);
    private static final Path dummyLog1Changed = Paths.get(LOG_DIR, CHANGED_LOG_NAME);

    private final Class<? extends ServiceActivator> serviceActivator;
    private final int profile1LogCount;

    protected AbstractLoggingProfilesTestCase(final Class<? extends ServiceActivator> serviceActivator, final int profile1LogCount) {
        this.serviceActivator = serviceActivator;
        this.profile1LogCount = profile1LogCount;
    }

    static class LoggingProfilesTestCaseSetup extends ServerReload.SetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            ModelNode op = Operations.createAddOperation(createAddress("periodic-rotating-file-handler", "LOGGING_TEST"));
            op.get("append").set("true");
            op.get("suffix").set(".yyyy-MM-dd");
            ModelNode file = new ModelNode();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(LOG_FILE_NAME);
            op.get("file").set(file);
            op.get("formatter").set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");
            builder.addStep(op);

            op = Operations.createOperation("add-handler", createAddress("root-logger", "ROOT"));
            op.get("name").set("LOGGING_TEST");
            builder.addStep(op);

            // create dummy-profile1
            builder.addStep(Operations.createAddOperation(createAddress("logging-profile", "dummy-profile1")));

            // add file handler
            op = Operations.createAddOperation(createAddress("logging-profile", "dummy-profile1", "periodic-rotating-file-handler", "DUMMY1"));
            op.get("level").set("ERROR");
            op.get("append").set("true");
            op.get("suffix").set(".yyyy-MM-dd");
            file = new ModelNode();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(PROFILE1_LOG_NAME);
            op.get("file").set(file);
            op.get("formatter").set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");
            builder.addStep(op);

            // add root logger
            op = Operations.createAddOperation(createAddress("logging-profile", "dummy-profile1", "root-logger", "ROOT"));
            op.get("level").set("INFO");
            ModelNode handlers = op.get("handlers");
            handlers.add("DUMMY1");
            op.get("handlers").set(handlers);
            builder.addStep(op);

            // create dummy-profile2
            builder.addStep(Operations.createAddOperation(createAddress("logging-profile", "dummy-profile2")));

            // add file handler
            op = Operations.createAddOperation(createAddress("logging-profile", "dummy-profile2", "periodic-rotating-file-handler", "DUMMY2"));
            op.get("level").set("INFO");
            op.get("append").set("true");
            op.get("suffix").set(".yyyy-MM-dd");
            file = new ModelNode();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(PROFILE2_LOG_NAME);
            op.get("file").set(file);
            op.get("formatter").set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");
            builder.addStep(op);

            // add root logger
            op = Operations.createAddOperation(createAddress("logging-profile", "dummy-profile2", "root-logger", "ROOT"));
            op.get("level").set("INFO");
            handlers = op.get("handlers");
            handlers.add("DUMMY2");
            op.get("handlers").set(handlers);
            builder.addStep(op);

            executeOperation(builder.build());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // remove LOGGING_TEST from root-logger
            ModelNode op = Operations.createOperation("remove-handler", createAddress("root-logger", "ROOT"));
            op.get("name").set("LOGGING_TEST");
            builder.addStep(op);

            // remove custom file handler
            builder.addStep(Operations.createRemoveOperation(createAddress("periodic-rotating-file-handler", "LOGGING_TEST")));

            // remove dummy-profile1
            builder.addStep(Operations.createRemoveOperation(createAddress("logging-profile", "dummy-profile1")));

            // remove dummy-profile2
            builder.addStep(Operations.createRemoveOperation(createAddress("logging-profile", "dummy-profile2")));

            executeOperation(builder.build());

            // delete log files only if this did not fail
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(dummyLog1);
            Files.deleteIfExists(dummyLog2);
            Files.deleteIfExists(dummyLog1Changed);

            super.tearDown(client);
        }
    }

    @Test
    public void noWarningTest() throws Exception {
        try {
            deploy(RUNTIME_NAME1, PROFILE1, false);
            deploy(RUNTIME_NAME2, PROFILE2, false);
            try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Look for message id in order to support all languages.
                    if (line.contains(PROFILE1) || line.contains(PROFILE2)) {
                        Assert.fail("Every deployment should have defined its own logging profile. But found this line in logs: "
                                + line);
                    }
                }
            }
        } finally {
            undeploy(RUNTIME_NAME1, RUNTIME_NAME2);
        }
    }

    @Test
    public void testProfiles() throws Exception {
        // Test the first profile
        try {
            deploy(RUNTIME_NAME1, PROFILE1);
            // make some logs
            int statusCode = getResponse("DummyProfile1: Test log message from 1");
            assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        } finally {
            undeploy(RUNTIME_NAME1);
        }

        // Test the next profile
        try {
            deploy(RUNTIME_NAME2, PROFILE2);
            // make some logs
            int statusCode = getResponse("DummyProfile2: Test log message from 2");
            assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        } finally {
            undeploy(RUNTIME_NAME2);
        }

        // Check that only one log record is in the first file
        Assert.assertTrue("dummy-profile1.log was not created", Files.exists(dummyLog1));
        try (final BufferedReader reader = Files.newBufferedReader(dummyLog1, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            Assert.assertNotNull("Log file dummy-profile1.log is empty and should not be.", line);
            Assert.assertTrue(
                    "\"LoggingServlet is logging error message\" should be presented in dummy-profile1.log",
                    line.contains("DummyProfile1: Test log message from 1"));
            // Read lines until we expect no more
            for (int i = 1; i < profile1LogCount; i++) {
                Assert.assertNotNull(String.format("Expected %d log records but only got %d", profile1LogCount, i)
                        , reader.readLine());
            }
            Assert.assertTrue("Only " + profile1LogCount + " log should be found in dummy-profile1.log",
                    reader.readLine() == null);
        }

        // Check that only one log record is in the second file
        Assert.assertTrue("dummy-profile2.log was not created", Files.exists(dummyLog2));
        try (final BufferedReader reader = Files.newBufferedReader(dummyLog2, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // The UndertowService also logs one line, this line should be ignored
                if (line.contains("UndertowService")) continue;
                if (!line.contains("DummyProfile2: Test log message from 2")) {
                    Assert.fail("dummy-profile2 should not contains this line: "
                            + line);
                }
            }
        }

        // Check a file that should not have been written to
        try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Test log message from")) {
                    Assert.fail("LoggingServlet messages should be presented only in files specified in profiles, but found: "
                            + line);
                }
            }
        }

        // Change the file for the first profile
        try {
            deploy(RUNTIME_NAME1, PROFILE1);
            // Change logging level of file handler on dummy-profile1 from ERROR to
            // INFO
            final ModelNode address = createAddress("logging-profile", PROFILE1, "periodic-rotating-file-handler", "DUMMY1");
            final ModelNode file = new ModelNode();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(CHANGED_LOG_NAME);
            ModelNode op = Operations.createWriteAttributeOperation(address, "file", file);
            executeOperation(op);

            // make some logs
            int statusCode = getResponse("DummyProfile1: Changed test message 1");
            assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);

            // check logs, after logging level change we should see also INFO and
            // ... messages
            try (final BufferedReader reader = Files.newBufferedReader(dummyLog1Changed, StandardCharsets.UTF_8)) {
                String line = reader.readLine();
                Assert.assertNotNull("Log file " + CHANGED_LOG_NAME + " is empty and should not be.", line);
                Assert.assertTrue(
                        "\"LoggingServlet is logging error message\" should be presented in " + CHANGED_LOG_NAME,
                        line.contains("DummyProfile1: Changed test message 1"));
                // Read lines until we expect no more
                for (int i = 1; i < profile1LogCount; i++) {
                    Assert.assertNotNull(String.format("Expected %d log records but only got %d", profile1LogCount, i)
                            , reader.readLine());
                }
                Assert.assertNull("Only " + profile1LogCount + " log should be found in " + CHANGED_LOG_NAME, reader.readLine());
            }
        } finally {
            undeploy(RUNTIME_NAME1);
        }
    }

    @Test
    public void testDeploymentConfigurationResource() throws Exception {
        try {
            deploy(RUNTIME_NAME1, PROFILE1);
            // Get the resulting model
            final ModelNode loggingConfiguration = readDeploymentResource(RUNTIME_NAME1, "profile-" + PROFILE1);

            Assert.assertTrue("No logging subsystem resource found on the deployment", loggingConfiguration.isDefined());

            // Check the handler exists on the configuration
            final ModelNode handler = loggingConfiguration.get("handler", "DUMMY1");
            Assert.assertTrue("The DUMMY1 handler was not found effective configuration", handler.isDefined());
            Assert.assertEquals("The level should be ERROR", "ERROR", handler.get("level").asString());
        } finally {
            undeploy(RUNTIME_NAME1);
        }
    }

    protected void processDeployment(final JavaArchive deployment) {
        addPermissions(deployment);
    }

    private void deploy(final String name, final String profileName) throws IOException {
        deploy(name, profileName, true);
    }

    private void deploy(final String name, final String profileName, final boolean useServiceActivator) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        if (useServiceActivator) {
            archive.addClasses(LoggingServiceActivator.DEPENDENCIES);
            archive.addAsServiceProviderAndClasses(ServiceActivator.class, serviceActivator);
        }
        archive.addAsResource(new StringAsset("Dependencies: io.undertow.core\nLogging-Profile: " + profileName), "META-INF/MANIFEST.MF");
        processDeployment(archive);
        deploy(archive, name);
    }
}
