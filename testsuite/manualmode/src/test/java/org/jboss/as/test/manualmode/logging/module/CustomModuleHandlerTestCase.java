/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging.module;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.json.JsonObject;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.manualmode.logging.module.impl.AppendingFileHandler;
import org.jboss.as.test.manualmode.logging.module.impl.SystemPropertyResolver;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CustomModuleHandlerTestCase extends AbstractCustomModuleTestCase {

    private static final String PROPERTY_VALUE = " handler";
    private static final String FILE_NAME = "custom-log-file.log";

    private static Path logFile;

    @BeforeClass
    public static void setup() throws Exception {

        logFile = getAbsoluteLogFilePath(FILE_NAME);

        // Add the custom-handler and the logger
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode address = Operations.createAddress("system-property", AppendingFileHandler.PROPERTY_KEY);
        ModelNode op = Operations.createAddOperation(address);
        op.get("value").set(PROPERTY_VALUE);
        builder.addStep(op);
        CLEANUP_OPS.add(Operations.createRemoveOperation(address));

        final String formatterName = "json";
        final ModelNode formatterAddress = createAddress("json-formatter", formatterName);
        op = Operations.createAddOperation(formatterAddress);
        op.get("pretty-print").set(false);
        builder.addStep(op);
        CLEANUP_OPS.addLast(Operations.createRemoveOperation(formatterAddress));


        final String handlerName = "custom-handler";
        final ModelNode handlerAddress = createAddress("custom-handler", handlerName);
        op = Operations.createAddOperation(handlerAddress);
        op.get("class").set(AppendingFileHandler.class.getCanonicalName());
        op.get("module").set(TestEnvironment.IMPL_MODULE_NAME);
        op.get("named-formatter").set(formatterName);
        ModelNode properties = op.get("properties");
        properties.get("fileName").set(logFile.toString());
        properties.get("propertyResolver").set(SystemPropertyResolver.class.getCanonicalName());
        builder.addStep(op);
        CLEANUP_OPS.addFirst(Operations.createRemoveOperation(handlerAddress));

        final ModelNode loggerAddress = createAddress("logger", LoggingDeploymentServiceActivator.LOGGER.getName());
        op = Operations.createAddOperation(loggerAddress);
        final ModelNode handlers = op.get("handlers").setEmptyList();
        handlers.add(handlerName);
        builder.addStep(op);
        CLEANUP_OPS.addFirst(Operations.createRemoveOperation(loggerAddress));

        executeOperation(builder.build());

        deployApp();
    }

    @Test
    public void testHandler() throws IOException {
        final List<JsonObject> lines = readLogFile(logFile);
        Assert.assertEquals("Expected six log entry", 6, lines.size());
        for (JsonObject json : lines) {
            Assert.assertTrue(json.getString("message").endsWith(PROPERTY_VALUE));
        }
    }
}
