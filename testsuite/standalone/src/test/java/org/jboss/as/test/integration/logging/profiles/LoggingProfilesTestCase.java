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

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper.ServerDeploymentException;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Petr Křemenský <pkremens@redhat.com>
 */
@ServerSetup(LoggingProfilesTestCase.LoggingProfilesTestCaseSetup.class)
@RunWith(WildflyTestRunner.class)
public class LoggingProfilesTestCase extends AbstractLoggingTestCase {
    private static Logger log = Logger.getLogger(LoggingProfilesTestCase.class);

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

    static class LoggingProfilesTestCaseSetup implements ServerSetupTask {

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
            op.get("level").set("FATAL");
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
            Assert.assertTrue(
                    "\"LoggingServlet is logging fatal message\" should be presented in dummy-profile1.log",
                    line.contains("DummyProfile1: Test log message from 1"));
            Assert.assertTrue("Only one log should be found in dummy-profile1.log",
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
            // Change logging level of file handler on dummy-profile1 from FATAL to
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
                Assert.assertTrue(
                        "\"LoggingServlet is logging fatal message\" should be presented in dummy-profile1.log",
                        line.contains("DummyProfile1: Changed test message 1"));
                Assert.assertTrue("Only one log should be found in " + CHANGED_LOG_NAME, reader.readLine() == null);
            }
        } finally {
            undeploy(RUNTIME_NAME1);
        }
    }

    private static void deploy(final String name, final String profileName) throws ServerDeploymentException {
        deploy(name, profileName, true);
    }

    private static void deploy(final String name, final String profileName, final boolean useServiceActivator) throws ServerDeploymentException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        if (useServiceActivator) {
            archive.addClasses(LoggingServiceActivator.DEPENDENCIES);
            archive.addAsServiceProviderAndClasses(ServiceActivator.class, LoggingServiceActivator.class);
        }
        archive.addAsResource(new StringAsset("Dependencies: io.undertow.core\nLogging-Profile: " + profileName), "META-INF/MANIFEST.MF");
        deploy(archive, name);
    }

    private static void undeploy(final String... deployments) throws Exception {
        // Safely undeploy each deployment only logging deployment errors and throwing and exception at the end
        boolean error = false;
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter writer = new PrintWriter(stringWriter);
        for (String name : deployments) {
            try {
                undeploy(name);
            } catch (Exception e) {
                error = true;
                e.printStackTrace(writer);
            }
        }
        Assert.assertFalse(stringWriter.toString(), error);
    }
}
