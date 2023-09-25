/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.handlers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.as.test.integration.management.util.ServerReload;
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
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(RotatingFileHandlerTestCase.ConfigureSubsystem.class)
public class RotatingFileHandlerTestCase extends AbstractLoggingTestCase {

    private static final String PERIODIC_SIZE_FILE_NAME = "periodic-size-rotating.log";
    private static final String PERIODIC_SIZE_LOGGER_NAME = RotatingFileHandlerTestCase.class.getPackage().getName() + ".PeriodicSizeRotating";
    private static final String PERIODIC_SIZE_COMPRESSED_FILE_NAME = "periodic-size-rotating-compressed.log";
    private static final String PERIODIC_SIZE_COMPRESSED_LOGGER_NAME = RotatingFileHandlerTestCase.class.getPackage().getName() + ".PeriodicSizeRotating.compressed";
    private static final String SIZE_FILE_NAME = "size-rotating.log";
    private static final String SIZE_LOGGER_NAME = RotatingFileHandlerTestCase.class.getPackage().getName() + ".SizeRotating";
    private static final String SIZE_COMPRESSED_FILE_NAME = "size-rotating-compressed.log";
    private static final String SIZE_COMPRESSED_LOGGER_NAME = RotatingFileHandlerTestCase.class.getPackage().getName() + ".SizeRotating.compressed";
    private static final String FORMATTER_NAME = "json";

    @BeforeClass
    public static void setup() throws Exception {
        deploy(createDeployment(), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testSizeRotate() throws Exception {
        final String msg = "This is a size rotate test";
        final Map<String, String> params = new HashMap<>();
        params.put(LoggingServiceActivator.LOG_COUNT_KEY, "100");
        params.put(LoggingServiceActivator.LOG_NAME_KEY, SIZE_LOGGER_NAME);
        executeRequest(SIZE_FILE_NAME, msg, params);
    }

    @Test
    public void testSizeRotateCompressed() throws Exception {
        // Change the suffix to use compress when rotated
        final String msg = "This is a size rotate test";
        final Map<String, String> params = new HashMap<>();
        params.put(LoggingServiceActivator.LOG_COUNT_KEY, "100");
        params.put(LoggingServiceActivator.LOG_NAME_KEY, SIZE_COMPRESSED_LOGGER_NAME);
        executeRequest(SIZE_COMPRESSED_FILE_NAME, msg, params);
    }

    @Test
    public void testPeriodicSizeRotate() throws Exception {
        final String msg = "This is a periodic size rotate test";
        final Map<String, String> params = new HashMap<>();
        params.put(LoggingServiceActivator.LOG_COUNT_KEY, "100");
        params.put(LoggingServiceActivator.LOG_NAME_KEY, PERIODIC_SIZE_LOGGER_NAME);
        executeRequest(PERIODIC_SIZE_FILE_NAME, msg, params);
    }

    @Test
    public void testPeriodicSizeRotateCompressed() throws Exception {
        final String msg = "This is a periodic size rotate test";
        final Map<String, String> params = new HashMap<>();
        params.put(LoggingServiceActivator.LOG_COUNT_KEY, "100");
        params.put(LoggingServiceActivator.LOG_NAME_KEY, PERIODIC_SIZE_COMPRESSED_LOGGER_NAME);
        executeRequest(PERIODIC_SIZE_COMPRESSED_FILE_NAME, msg, params);
    }

    private static void executeRequest(final String fileName, final String msg, final Map<String, String> params) throws IOException {
        final int statusCode = getResponse(msg, params);
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);
        final Path logDir = Paths.get(resolveRelativePath("jboss.server.log.dir"));
        // Walk the path and we should have the file name plus one ending in .1 as we should have logged enough to cause
        // at least one rotation.
        final Pattern pattern = Pattern.compile(parseFileName(fileName) + "(\\.log|\\.log\\.1|\\.log[0-9]{2}(\\.1)?)(\\.zip)?");
        final Set<String> foundFiles = Files.list(logDir)
                .map(path -> path.getFileName().toString())
                .filter((name) -> pattern.matcher(name).matches())
                .collect(Collectors.toSet());
        // We should have at least two files and no more than four
        final int size = foundFiles.size();
        Assert.assertTrue("Expected to have between 2 and 4 files, found " + size, 2 <= size && size <= 4);
    }

    private static String parseFileName(final String fileName) {
        final int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }


    public static class ConfigureSubsystem implements ServerSetupTask {
        private final Deque<ModelNode> removeOps = new ArrayDeque<>();

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create();

            final ModelNode formatterAddress = createAddress("json-formatter", FORMATTER_NAME);
            builder.addStep(Operations.createAddOperation(formatterAddress));
            removeOps.addLast(Operations.createRemoveOperation(formatterAddress));

            ModelNode address = createAddress("size-rotating-file-handler", SIZE_FILE_NAME);
            ModelNode op = Operations.createAddOperation(address);
            ModelNode file = op.get("file").setEmptyObject();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(SIZE_FILE_NAME);
            op.get("max-backup-index").set(1);
            op.get("rotate-size").set("5k");
            op.get("named-formatter").set(FORMATTER_NAME);
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            address = createAddress("size-rotating-file-handler", SIZE_COMPRESSED_FILE_NAME);
            op = Operations.createAddOperation(address);
            file = op.get("file").setEmptyObject();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(SIZE_COMPRESSED_FILE_NAME);
            op.get("max-backup-index").set(1);
            op.get("rotate-size").set("5k");
            op.get("named-formatter").set(FORMATTER_NAME);
            op.get("suffix").set(".zip");
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            address = createAddress("periodic-size-rotating-file-handler", PERIODIC_SIZE_FILE_NAME);
            op = Operations.createAddOperation(address);
            file = op.get("file").setEmptyObject();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(PERIODIC_SIZE_FILE_NAME);
            op.get("max-backup-index").set(1);
            op.get("rotate-size").set("5k");
            op.get("named-formatter").set(FORMATTER_NAME);
            op.get("suffix").set("mm");
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            address = createAddress("periodic-size-rotating-file-handler", PERIODIC_SIZE_COMPRESSED_FILE_NAME);
            op = Operations.createAddOperation(address);
            file = op.get("file").setEmptyObject();
            file.get("relative-to").set("jboss.server.log.dir");
            file.get("path").set(PERIODIC_SIZE_COMPRESSED_FILE_NAME);
            op.get("max-backup-index").set(1);
            op.get("rotate-size").set("5k");
            op.get("named-formatter").set(FORMATTER_NAME);
            op.get("suffix").set("mm.zip");
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            address = createAddress("logger", SIZE_LOGGER_NAME);
            op = Operations.createAddOperation(address);
            op.get("handlers").setEmptyList().add(SIZE_FILE_NAME);
            op.get("use-parent-handlers").set(false);
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            address = createAddress("logger", SIZE_COMPRESSED_LOGGER_NAME);
            op = Operations.createAddOperation(address);
            op.get("handlers").setEmptyList().add(SIZE_COMPRESSED_FILE_NAME);
            op.get("use-parent-handlers").set(false);
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            address = createAddress("logger", PERIODIC_SIZE_LOGGER_NAME);
            op = Operations.createAddOperation(address);
            op.get("handlers").setEmptyList().add(PERIODIC_SIZE_FILE_NAME);
            op.get("use-parent-handlers").set(false);
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            address = createAddress("logger", PERIODIC_SIZE_COMPRESSED_LOGGER_NAME);
            op = Operations.createAddOperation(address);
            op.get("handlers").setEmptyList().add(PERIODIC_SIZE_COMPRESSED_FILE_NAME);
            op.get("use-parent-handlers").set(false);
            builder.addStep(op);
            removeOps.addFirst(Operations.createRemoveOperation(address));

            executeOperation(managementClient, builder.build());
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {
            final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create();
            ModelNode op;
            while ((op = removeOps.pollFirst()) != null) {
                builder.addStep(op);
            }
            executeOperation(managementClient, builder.build());
        }

        private void executeOperation(final ManagementClient managementClient, final Operation op) throws IOException {
            final ModelNode result = managementClient.getControllerClient().execute(op);
            if (!Operations.isSuccessfulOutcome(result)) {
                throw new RuntimeException(Operations.getFailureDescription(result).toString());
            }
            // Reload if required
            if (result.hasDefined(ClientConstants.RESPONSE_HEADERS)) {
                final ModelNode responseHeaders = result.get(ClientConstants.RESPONSE_HEADERS);
                if (responseHeaders.hasDefined("process-state")) {
                    if (ClientConstants.CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED.equals(responseHeaders.get("process-state").asString())) {
                        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
                    }
                }
            }
        }
    }
}
