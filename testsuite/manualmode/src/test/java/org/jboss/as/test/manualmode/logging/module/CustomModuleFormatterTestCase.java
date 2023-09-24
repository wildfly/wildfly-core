/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import jakarta.json.JsonObject;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.manualmode.logging.module.impl.AppendingFormatter;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.formatters.JsonFormatter;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CustomModuleFormatterTestCase extends AbstractCustomModuleTestCase {

    private static final String FILE_NAME = "formatter-log-file.log";

    @BeforeClass
    public static void setup() throws Exception {

        // Add the custom-handler and the logger
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add a filter
        final ModelNode formatterAddress = createAddress("custom-formatter", "test-formatter");
        ModelNode op = Operations.createAddOperation(formatterAddress);
        op.get("class").set(AppendingFormatter.class.getCanonicalName());
        op.get("module").set(TestEnvironment.IMPL_MODULE_NAME);
        final ModelNode properties = op.get("properties").setEmptyObject();
        properties.get("delegate").set(JsonFormatter.class.getCanonicalName());
        properties.get("text").set(" formatted");
        builder.addStep(op);
        CLEANUP_OPS.addLast(Operations.createRemoveOperation(formatterAddress));


        final String handlerName = "formatter-handler";
        final ModelNode handlerAddress = createAddress("file-handler", handlerName);
        op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set("test-formatter");
        op.get("append").set(false);
        final ModelNode file = op.get("file").setEmptyObject();
        file.get("path").set(FILE_NAME);
        file.get("relative-to").set("jboss.server.log.dir");
        builder.addStep(op);
        CLEANUP_OPS.addFirst(Operations.createRemoveOperation(handlerAddress));

        final ModelNode loggerAddress = createAddress("logger", LoggingDeploymentServiceActivator.LOGGER.getName());
        op = Operations.createAddOperation(loggerAddress);
        op.get("handlers").setEmptyList().add(handlerName);
        builder.addStep(op);
        CLEANUP_OPS.addFirst(Operations.createRemoveOperation(loggerAddress));

        executeOperation(builder.build());

        deployApp();
    }

    @Test
    public void testFormatter() throws IOException {
        final Path logFile = getAbsoluteLogFilePath(FILE_NAME);
        final List<JsonObject> lines = readLogFile(logFile);
        Assert.assertFalse("No log messages in file " + logFile, lines.isEmpty());
        for (JsonObject json : lines) {
            Assert.assertTrue(json.getString("message").endsWith("formatted"));
        }
    }
}
