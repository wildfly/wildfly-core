/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.operations;

import java.io.BufferedReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@ServerSetup(ServerReload.SetupTask.class)
@RunWith(WildFlyRunner.class)
public class CustomFormattersTestCase extends AbstractLoggingOperationsTestCase {

    private static final String CUSTOM_FORMATTER_NAME = "customFormatter";
    private static final String FILE_NAME = "cf-log.xml";
    private static final String HANDLER_NAME = "xmlFile";
    private static final ModelNode CUSTOM_FORMATTER_ADDRESS = createAddress("custom-formatter", CUSTOM_FORMATTER_NAME);
    private static final ModelNode HANDLER_ADDRESS = createAddress("file-handler", HANDLER_NAME);
    private static final ModelNode ROOT_LOGGER_ADDRESS = createAddress("root-logger", "ROOT");

    @Test
    public void testOperations() throws Exception {
        // Create the custom formatter
        ModelNode op = Operations.createAddOperation(CUSTOM_FORMATTER_ADDRESS);
        op.get("class").set("org.jboss.logmanager.formatters.PatternFormatter");
        op.get("module").set("org.jboss.logmanager");
        executeOperation(op);

        // Write some properties
        final ModelNode properties = new ModelNode().setEmptyObject();
        properties.get("pattern").set("%s%E%n");
        testWrite(CUSTOM_FORMATTER_ADDRESS, "properties", properties);

        // Undefine the properties
        testUndefine(CUSTOM_FORMATTER_ADDRESS, "properties");

        // Write a new class attribute, should leave in restart state
        ModelNode result = testWrite(CUSTOM_FORMATTER_ADDRESS, "class", "java.util.logging.XMLFormatter");
        // Check the state
        ModelNode step1 = Operations.readResult(result).get("step-1");
        Assert.assertTrue("Should be in reload-required state: " + result, step1.get("response-headers").get("operation-requires-reload").asBoolean());

        // Undefining the class should fail
        testUndefine(CUSTOM_FORMATTER_ADDRESS, "class", true);

        // Change the module which should require a restart
        result = testWrite(CUSTOM_FORMATTER_ADDRESS, "module", "org.slf4j.impl");
        // Check the state
        step1 = Operations.readResult(result).get("step-1");
        Assert.assertTrue("Should be in reload-required state: " + result, step1.get("response-headers").get("operation-requires-reload").asBoolean());

        // Undefining the module should fail
        testUndefine(CUSTOM_FORMATTER_ADDRESS, "module", true);

        // Remove the custom formatter
        op = Operations.createRemoveOperation(CUSTOM_FORMATTER_ADDRESS);
        executeOperation(op);

        // Verify it's been removed
        verifyRemoved(CUSTOM_FORMATTER_ADDRESS);
    }

    @Test
    public void testUsage() throws Exception {

        // Create the custom formatter
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        ModelNode op = Operations.createAddOperation(CUSTOM_FORMATTER_ADDRESS);
        op.get("class").set("java.util.logging.XMLFormatter");
        // the module doesn't really matter since it's a JDK, so we'll just use the jboss-logmanager.
        op.get("module").set("org.jboss.logmanager");
        builder.addStep(op);

        // Create the handler
        op = Operations.createAddOperation(HANDLER_ADDRESS);
        final ModelNode file = op.get("file");
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set(FILE_NAME);
        op.get("append").set(false);
        op.get("autoflush").set(true);
        op.get("named-formatter").set(CUSTOM_FORMATTER_NAME);
        builder.addStep(op);

        // Add the handler to the root logger
        op = Operations.createOperation("add-handler", ROOT_LOGGER_ADDRESS);
        op.get(ModelDescriptionConstants.NAME).set(HANDLER_NAME);
        builder.addStep(op);

        executeOperation(builder.build());

        // Get the log file
        op = Operations.createOperation("resolve-path", HANDLER_ADDRESS);
        ModelNode result = executeOperation(op);
        final Path logFile = Paths.get(Operations.readResult(result).asString());

        // The file should exist
        Assert.assertTrue("The log file was not created.", Files.exists(logFile));

        // Log 5 records
        doLog("Test message: ", 5);

        // Read the log file
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            final Pattern pattern = Pattern.compile("^(<message>)+(Test message: \\d)+(</message>)$");
            final List<String> messages = new ArrayList<>(5);
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmedLine = line.trim();
                final Matcher m = pattern.matcher(trimmedLine);
                // Very simple xml parsing
                if (m.matches()) {
                    messages.add(m.group(2));
                }
            }

            // Should be 5 messages
            Assert.assertEquals(5, messages.size());
            // Check each message
            int count = 0;
            for (String msg : messages) {
                Assert.assertEquals("Test message: " + count++, msg);
            }
        }

        builder = CompositeOperationBuilder.create();
        // Remove the handler from the root-logger
        op = Operations.createOperation("remove-handler", ROOT_LOGGER_ADDRESS);
        op.get(ModelDescriptionConstants.NAME).set(HANDLER_NAME);
        builder.addStep(op);


        // Remove the custom formatter
        op = Operations.createRemoveOperation(CUSTOM_FORMATTER_ADDRESS);
        builder.addStep(op);

        // Remove the handler
        op = Operations.createRemoveOperation(HANDLER_ADDRESS);
        builder.addStep(op);

        executeOperation(builder.build());

        // So we don't pollute other, verify the formatter and handler have been removed
        op = Operations.createReadAttributeOperation(ROOT_LOGGER_ADDRESS, "handlers");
        result = executeOperation(op);
        // Should be a list type
        final List<ModelNode> handlers = Operations.readResult(result).asList();
        for (ModelNode handler : handlers) {
            Assert.assertNotEquals(CUSTOM_FORMATTER_NAME, handler.asString());
        }
        verifyRemoved(CUSTOM_FORMATTER_ADDRESS);
        verifyRemoved(HANDLER_ADDRESS);

        // Delete the log file
        Files.delete(logFile);
        // Ensure it's been deleted
        Assert.assertFalse(Files.exists(logFile));
    }

    private void doLog(final String msg, final int count) throws Exception {
        final URL url = TestSuiteEnvironment.getHttpUrl();
        for (int i = 0; i < count; i++) {
            final String s = msg + i;
            final int statusCode = getResponse(s, Collections.singletonMap(LoggingServiceActivator.LOG_INFO_ONLY_KEY, "true"));
            Assert.assertTrue("Invalid response statusCode: " + statusCode + " URL: " + url, statusCode == HttpStatus.SC_OK);
        }
    }
}
