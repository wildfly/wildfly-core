/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.logging.filters;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.as.test.integration.logging.TestFilter;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Tests user defined filters in the logging subsystem.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
public class FilterTestCase extends AbstractLoggingTestCase {

    private static final String FILE_NAME = "json-file-filter.log";
    private static final String JSON_HANDLER_NAME = "test-handler";
    private static final String JSON_FORMATTER_NAME = "json";
    private static final String FILTER_NAME = "testFilter";
    private static final ModelNode HANDLER_ADDRESS = createAddress("file-handler", JSON_HANDLER_NAME);
    private static final ModelNode FORMATTER_ADDRESS = createAddress("json-formatter", JSON_FORMATTER_NAME);
    private static final ModelNode LOGGER_ADDRESS = createAddress("logger", LoggingServiceActivator.LOGGER.getName());
    private static final ModelNode FILTER_ADDRESS = createAddress("filter", FILTER_NAME);

    private Path logFile = null;

    @BeforeClass
    public static void deploy() throws Exception {
        createModule();
        deploy(createDeployment(), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Before
    public void setLogFile() {
        if (logFile == null) {
            logFile = getAbsoluteLogFilePath(FILE_NAME);
        }
    }

    @After
    public void remove() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create()
                // Remove the logger
                .addStep(Operations.createRemoveOperation(LOGGER_ADDRESS))
                // Remove the file handler
                .addStep(Operations.createRemoveOperation(HANDLER_ADDRESS))
                // Remove the formatter
                .addStep(Operations.createRemoveOperation(FORMATTER_ADDRESS))
                // Remove the filter
                .addStep(Operations.createRemoveOperation(FILTER_ADDRESS));

        executeOperation(builder.build());
    }

    @Test
    public void testHandlerFilter() throws Exception {
        configure(null, FILTER_NAME);
        final String msg = "Test handler filter";
        final String expectedMsg = msg + " | constructor text | property text";
        test(msg, expectedMsg);
    }

    @Test
    public void testNestedHandlerFilter() throws Exception {
        configure(null, "any(deny, " + FILTER_NAME + ")");
        final String msg = "Test nested handler allowed filter";
        final String expectedMsg = msg + " | constructor text | property text";
        test(msg, expectedMsg);
    }

    @Test
    public void testNestedHandlerDenyFilter() throws Exception {
        configure(null, "all(" + FILTER_NAME + ", deny)");
        final String msg = "Test handler no log filter";
        test(msg, null);
    }

    @Test
    public void testLoggerFilter() throws Exception {
        configure(FILTER_NAME, null);
        final String msg = "Test logger filter";
        final String expectedMsg = msg + " | constructor text | property text";
        test(msg, expectedMsg);
    }

    @Test
    public void testNestedLoggerFilter() throws Exception {
        configure("any(deny, " + FILTER_NAME + ")", null);
        final String msg = "Test nexted logger logged filter";
        final String expectedMsg = msg + " | constructor text | property text";
        test(msg, expectedMsg);
    }

    @Test
    public void testNestedLoggerDenyFilter() throws Exception {
        configure("all(" + FILTER_NAME + ", deny)", null);
        final String msg = "Test logger no log filter";
        test(msg, null);
    }

    @Test
    public void testLoggerAndHandlerFilter() throws Exception {
        configure(FILTER_NAME, FILTER_NAME);
        final String msg = "Test handler filter";
        final String expectedMsg = msg + " | constructor text | property text | constructor text | property text";
        test(msg, expectedMsg);
    }

    @Test
    public void testNestedLoggerAndHandlerFilter() throws Exception {
        configure("any(deny, " + FILTER_NAME + ")", "any(" + FILTER_NAME + ", deny)");
        final String msg = "Test handler filter";
        final String expectedMsg = msg + " | constructor text | property text | constructor text | property text";
        test(msg, expectedMsg);
    }

    @Test
    public void testLoggerAndHandlerPropertyChangeFilter() throws Exception {
        configure(FILTER_NAME, FILTER_NAME);
        final String msg = "Test handler filter";
        String expectedMsg = msg + " | constructor text | property text | constructor text | property text";
        final int offset = test(msg, expectedMsg);

        final String changedPropertyMsg = " | changed text";
        final ModelNode properties = new ModelNode().setEmptyObject();
        properties.get("propertyText").set(changedPropertyMsg);
        final ModelNode op = Operations.createWriteAttributeOperation(FILTER_ADDRESS, "properties", properties);
        executeOperation(op);
        expectedMsg = msg + " | constructor text | changed text | constructor text | changed text";
        test(msg, expectedMsg, offset);
    }

    @Test
    public void testLoggerAndHandlerConstructorChangeFilter() throws Exception {
        configure(FILTER_NAME, FILTER_NAME);
        final String msg = "Test handler filter";
        String expectedMsg = msg + " | constructor text | property text | constructor text | property text";
        final int offset = test(msg, expectedMsg);

        final String changedPropertyMsg = " | changed text";
        final ModelNode properties = new ModelNode().setEmptyObject();
        properties.get("constructorText").set(changedPropertyMsg);
        final ModelNode op = Operations.createWriteAttributeOperation(FILTER_ADDRESS, "constructor-properties", properties);
        executeOperation(Operation.Factory.create(op), true);
        expectedMsg = msg + " | changed text | property text | changed text | property text";
        test(msg, expectedMsg, offset);
    }

    private int test(final String msg, final String expectedMsg) throws IOException {
        return test(msg, expectedMsg, 0);
    }

    private int test(final String msg, final String expectedMsg, final int offset) throws IOException {
        int linesRead = 0;
        final int statusCode = getResponse(msg);
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);

        // Validate each line
        final List<String> jsonLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        if (expectedMsg == null) {
            Assert.assertTrue("Expected no lines, but got: " + jsonLines, jsonLines.isEmpty());
        } else {
            for (String s : jsonLines.subList(offset, jsonLines.size())) {
                linesRead++;
                if (s.trim().isEmpty()) continue;
                try (JsonReader reader = Json.createReader(new StringReader(s))) {
                    final JsonObject json = reader.readObject();
                    Assert.assertEquals(expectedMsg, json.getString("message"));
                }
            }
        }
        return linesRead;
    }

    private void configure(final String loggerFilter, final String handlerFilter) throws IOException {

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create a JSON formatter
        ModelNode op = Operations.createAddOperation(FORMATTER_ADDRESS);
        // This should always be false so that the each line will be a separate entry since we're writing to a file
        op.get("pretty-print").set(false);
        builder.addStep(op);

        // Create the filter
        op = Operations.createAddOperation(FILTER_ADDRESS);
        op.get("module").set("org.jboss.as.logging.test");
        op.get("class").set(TestFilter.class.getName());
        ModelNode constructorProperties = op.get("constructor-properties").setEmptyObject();
        constructorProperties.get("constructorText").set(" | constructor text");
        ModelNode properties = op.get("properties").setEmptyObject();
        properties.get("propertyText").set(" | property text");
        builder.addStep(op);

        // Create the handler
        op = Operations.createAddOperation(HANDLER_ADDRESS);
        final ModelNode fileNode = op.get("file").setEmptyObject();
        fileNode.get("relative-to").set("jboss.server.log.dir");
        fileNode.get("path").set(logFile.getFileName().toString());
        op.get("autoFlush").set(true);
        op.get("append").set(false);
        op.get("named-formatter").set(JSON_FORMATTER_NAME);
        if (handlerFilter != null) {
            op.get("filter-spec").set(handlerFilter);
        }
        builder.addStep(op);

        // Add the handler to the root-logger
        op = Operations.createAddOperation(LOGGER_ADDRESS);
        op.get("handlers").setEmptyList().add(JSON_HANDLER_NAME);
        if (loggerFilter != null) {
            op.get("filter-spec").set(loggerFilter);
        }
        builder.addStep(op);

        executeOperation(builder.build());
    }

    private static void createModule() {
        final String jbossHome = WildFlySecurityManager.getPropertyPrivileged("jboss.home", ".");
        final Path modulesDir = Paths.get(jbossHome, "modules");
        Assert.assertTrue("Could not find modules directory: " + modulesDir, Files.exists(modulesDir));
        // Create an archive for the module
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "logging-test.jar")
                .addClasses(TestFilter.class);

        final Path testModule = Paths.get(modulesDir.toString(), "org", "jboss", "as", "logging", "test", "main");
        try {
            Files.createDirectories(testModule);
            try (
                    BufferedInputStream in = new BufferedInputStream(AbstractLoggingTestCase.class.getResourceAsStream("module.xml"));
                    BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(testModule.resolve("module.xml")))
            ) {
                final byte[] buffer = new byte[512];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }

            // Copy the JAR
            try (OutputStream out = Files.newOutputStream(testModule.resolve("logging-test.jar"), StandardOpenOption.CREATE_NEW)) {
                jar.as(ZipExporter.class).exportTo(out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
