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
import jakarta.json.JsonObject;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.manualmode.logging.module.impl.AppendingFilter;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CustomModuleFilterTestCase extends AbstractCustomModuleTestCase {

    private static final String FILE_NAME = "filter-log-file.log";

    @BeforeClass
    public static void setup() throws Exception {

        // Add the custom-handler and the logger
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final String formatterName = "json";
        final ModelNode formatterAddress = createAddress("json-formatter", formatterName);
        ModelNode op = Operations.createAddOperation(formatterAddress);
        op.get("pretty-print").set(false);
        builder.addStep(op);
        CLEANUP_OPS.addLast(Operations.createRemoveOperation(formatterAddress));

        // Add a filter
        final ModelNode filterAddress = createAddress("filter", "testFilter");
        op = Operations.createAddOperation(filterAddress);
        op.get("class").set(AppendingFilter.class.getCanonicalName());
        op.get("module").set(TestEnvironment.IMPL_MODULE_NAME);
        final ModelNode constructorProperties = op.get("constructor-properties").setEmptyObject();
        constructorProperties.get("text").set("filtered");
        builder.addStep(op);
        CLEANUP_OPS.addLast(Operations.createRemoveOperation(filterAddress));


        final String handlerName = "filter-handler";
        final ModelNode handlerAddress = createAddress("file-handler", handlerName);
        op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set(formatterName);
        op.get("filter-spec").set("testFilter");
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
    public void testFilter() throws IOException {
        final Path logFile = getAbsoluteLogFilePath(FILE_NAME);
        final List<JsonObject> lines = readLogFile(logFile);
        Assert.assertFalse("No log messages in file " + logFile, lines.isEmpty());
        for (JsonObject json : lines) {
            Assert.assertTrue(json.getString("message").endsWith("filtered"));
        }
    }
}
