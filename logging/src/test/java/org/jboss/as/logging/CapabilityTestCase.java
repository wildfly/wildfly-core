/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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
package org.jboss.as.logging;

import java.io.IOException;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CapabilityTestCase extends AbstractOperationsTestCase {

    private KernelServices kernelServices;

    @Before
    public void setup() throws Exception {
        kernelServices = boot();
    }

    @After
    public void shutdown() {
        if (kernelServices != null) {
            kernelServices.shutdown();
        }
    }

    @Override
    protected void standardSubsystemTest(final String configId) {
        // do nothing as this is not a subsystem parsing test
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/empty-subsystem.xml");
    }

    @Test
    public void testRemoveAssignedFormatter() {
        testRemoveAssignedFormatter(null);
        testRemoveAssignedFormatter(PROFILE);
    }

    @Test
    public void testRemoveAssignedHandler() {
        testRemoveAssignedHandler(null);
        testRemoveAssignedHandler(PROFILE);
    }

    @Test
    public void testAddMissingFormatter() {
        testAddMissingFormatter(null);
        testAddMissingFormatter(PROFILE);
    }

    @Test
    public void testWriteNamedFormatter() {
        testWriteNamedFormatter(null);
        testWriteNamedFormatter(PROFILE);
    }

    @Test
    public void testAsyncAddHandler() {
        testAsyncAddHandler(null);
        testAsyncAddHandler(PROFILE);
    }

    @Test
    public void testAsyncRemoveHandler() {
        testAsyncRemoveHandler(null);
        testAsyncRemoveHandler(PROFILE);
    }

    @Test
    public void testLoggerAddHandler() {
        testLoggerAddHandler(null);
        testLoggerAddHandler(PROFILE);
    }

    @Test
    public void testLoggerRemoveHandler() {
        testLoggerRemoveHandler(null);
        testLoggerRemoveHandler(PROFILE);
    }

    private void testRemoveAssignedFormatter(final String profileName) {
        final ModelNode formatterAddress = createAddress(profileName, "json-formatter", "json").toModelNode();

        ModelNode op = Operations.createAddOperation(formatterAddress);
        executeOperation(kernelServices, op);

        // Add a file handler
        final ModelNode handlerAddress = createFileHandlerAddress(profileName, "json-handler").toModelNode();
        op = Operations.createAddOperation(handlerAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "log.json"));
        op.get("autoflush").set(true);
        op.get("append").set(false);
        op.get("named-formatter").set("json");
        executeOperation(kernelServices, op);

        // Attempt to remove the formatter which should fail as it's assigned to a handler
        op = Operations.createRemoveOperation(formatterAddress);
        executeOperationForFailure(kernelServices, op);
    }

    private void testRemoveAssignedHandler(final String profileName) {
        final ModelNode formatterAddress = createAddress(profileName, "json-formatter", "json").toModelNode();

        ModelNode op = Operations.createAddOperation(formatterAddress);
        executeOperation(kernelServices, op);

        // Add a file handler
        final ModelNode handlerAddress = createFileHandlerAddress(profileName, "json-handler").toModelNode();
        op = Operations.createAddOperation(handlerAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "log.json"));
        op.get("autoflush").set(true);
        op.get("append").set(false);
        op.get("named-formatter").set("json");
        executeOperation(kernelServices, op);

        final ModelNode rootLoggerAddress = createRootLoggerAddress(profileName).toModelNode();
        op = Operations.createAddOperation(rootLoggerAddress);
        op.get("level").set("INFO");
        op.get("handlers").setEmptyList().add("json-handler");
        executeOperation(kernelServices, op);

        // Attempt to remove the handler which should fail as it's assigned to a logger
        op = Operations.createRemoveOperation(handlerAddress);
        executeOperationForFailure(kernelServices, op);
    }

    private void testAddMissingFormatter(final String profileName) {
        // Add a file handler
        final ModelNode handlerAddress = createFileHandlerAddress(profileName, "json-handler").toModelNode();
        ModelNode handlerAddOp = Operations.createAddOperation(handlerAddress);
        handlerAddOp.get("file").set(createFileValue("jboss.server.log.dir", "log.json"));
        handlerAddOp.get("autoflush").set(true);
        handlerAddOp.get("append").set(false);
        handlerAddOp.get("named-formatter").set("json");
        // Attempt to add the handler which should fail since the json formatter does not exist
        executeOperationForFailure(kernelServices, handlerAddOp);
        final ModelNode formatterAddress = createAddress(profileName, "json-formatter", "json").toModelNode();

        // Now add both the formatter and the handler which should be successful
        final ModelNode formatterAddOp = Operations.createAddOperation(formatterAddress);
        executeOperation(kernelServices, formatterAddOp);
        executeOperation(kernelServices, handlerAddOp);
    }

    private void testWriteNamedFormatter(final String profileName) {
        // Create a known formatter
        final ModelNode jsonFormatterAddress = createAddress(profileName, "json-formatter", "json").toModelNode();
        ModelNode op = Operations.createAddOperation(jsonFormatterAddress);
        executeOperation(kernelServices, op);

        // Add a file handler
        final ModelNode handlerAddress = createFileHandlerAddress(profileName, "json-handler").toModelNode();
        op = Operations.createAddOperation(handlerAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "formatted.log"));
        op.get("autoflush").set(true);
        op.get("append").set(false);
        op.get("named-formatter").set("json");
        executeOperation(kernelServices, op);

        // Attempt to write an invalid formatter
        executeOperationForFailure(kernelServices, Operations.createWriteAttributeOperation(handlerAddress, "named-formatter", "xml"));

        // Add an XML formatter, then change the named-formatter, then remove the JSON formatter
        final ModelNode xmlFormatterAddress = createAddress(profileName, "xml-formatter", "xml").toModelNode();
        executeOperation(kernelServices, Operations.createAddOperation(xmlFormatterAddress));
        executeOperation(kernelServices, Operations.createWriteAttributeOperation(handlerAddress, "named-formatter", "xml"));
        executeOperation(kernelServices, Operations.createRemoveOperation(jsonFormatterAddress));

    }

    private void testAsyncAddHandler(final String profileName) {
        // Add a handler to later be added to the async-handler
        final ModelNode fileHandlerAddress = createPeriodicRotatingFileHandlerAddress(profileName, "FILE").toModelNode();
        ModelNode op = Operations.createAddOperation(fileHandlerAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "server.log"));
        op.get("autoflush").set(true);
        op.get("append").set(true);
        op.get("suffix").set(".yyyy-MM-dd");
        executeOperation(kernelServices, op);

        // Add the async-handler
        final ModelNode asyncHandlerAddress = createAsyncHandlerAddress(profileName, "async").toModelNode();
        op = Operations.createAddOperation(asyncHandlerAddress);
        op.get("queue-length").set(100);
        executeOperation(kernelServices, op);

        // Attempt to add a non-existing handler
        op = Operations.createOperation("add-handler", asyncHandlerAddress);
        op.get("name").set("invalid");
        executeOperationForFailure(kernelServices, op);

        // Add an existing handler to ensure the add-handler works with capabilities
        op.get("name").set("FILE");
        executeOperation(kernelServices, op);
    }

    private void testAsyncRemoveHandler(final String profileName) {
        // Add a handler to later be added to the async-handler
        final ModelNode fileHandlerAddress = createPeriodicRotatingFileHandlerAddress(profileName, "FILE").toModelNode();
        ModelNode op = Operations.createAddOperation(fileHandlerAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "server.log"));
        op.get("autoflush").set(true);
        op.get("append").set(true);
        op.get("suffix").set(".yyyy-MM-dd");
        executeOperation(kernelServices, op);

        // Add the async-handler
        final ModelNode asyncHandlerAddress = createAsyncHandlerAddress(profileName, "async").toModelNode();
        op = Operations.createAddOperation(asyncHandlerAddress);
        op.get("queue-length").set(100);
        op.get("subhandlers").setEmptyList().add("FILE");
        executeOperation(kernelServices, op);

        // Should not be able to remove the sub-handler
        executeOperationForFailure(kernelServices, Operations.createRemoveOperation(fileHandlerAddress));

        // Remove the handler, then remove the file-handler
        op = Operations.createOperation("remove-handler", asyncHandlerAddress);
        op.get("name").set("FILE");
        final Operation operation = CompositeOperationBuilder.create()
                .addStep(op)
                .addStep(Operations.createRemoveOperation(fileHandlerAddress))
                .build();
        executeOperation(kernelServices, operation.getOperation());
    }

    private void testLoggerAddHandler(final String profileName) {
        // Add a handler to later be added to the async-handler
        final ModelNode handlerAddress = createPeriodicRotatingFileHandlerAddress(profileName, "FILE").toModelNode();
        ModelNode op = Operations.createAddOperation(handlerAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "server.log"));
        op.get("autoflush").set(true);
        op.get("append").set(true);
        op.get("suffix").set(".yyyy-MM-dd");
        executeOperation(kernelServices, op);

        CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add a logger
        final ModelNode loggerAddress = createLoggerAddress(profileName, "org.jboss.as").toModelNode();
        builder.addStep(Operations.createAddOperation(loggerAddress));
        // Add the root logger
        final ModelNode rootLoggerAddress = createRootLoggerAddress(profileName).toModelNode();
        builder.addStep(Operations.createAddOperation(rootLoggerAddress));
        executeOperation(kernelServices, builder.build().getOperation());

        // Add the handler the logger and root logger
        builder = CompositeOperationBuilder.create();
        op = Operations.createOperation("add-handler", loggerAddress);
        op.get("name").set("FILE");
        builder.addStep(op.clone());
        op = Operations.createOperation("add-handler", rootLoggerAddress);
        op.get("name").set("FILE");
        builder.addStep(op.clone());

        executeOperation(kernelServices, builder.build().getOperation());

        // Attempt to remove the handler which should fail
        executeOperationForFailure(kernelServices, Operations.createRemoveOperation(handlerAddress));
    }

    private void testLoggerRemoveHandler(final String profileName) {
        // Add a handler to later be added to the async-handler
        final ModelNode handlerAddress = createPeriodicRotatingFileHandlerAddress(profileName, "FILE").toModelNode();
        ModelNode op = Operations.createAddOperation(handlerAddress);
        op.get("file").set(createFileValue("jboss.server.log.dir", "server.log"));
        op.get("autoflush").set(true);
        op.get("append").set(true);
        op.get("suffix").set(".yyyy-MM-dd");
        executeOperation(kernelServices, op);

        CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add a logger
        final ModelNode loggerAddress = createLoggerAddress(profileName, "org.jboss.as").toModelNode();
        op = Operations.createAddOperation(loggerAddress);
        op.get("handlers").setEmptyList().add("FILE");
        builder.addStep(op.clone());

        // Add the root logger
        final ModelNode rootLoggerAddress = createRootLoggerAddress(profileName).toModelNode();
        op = Operations.createAddOperation(rootLoggerAddress);
        op.get("handlers").setEmptyList().add("FILE");
        builder.addStep(op.clone());

        executeOperation(kernelServices, builder.build().getOperation());

        // Attempt to remove the handler which should fail
        executeOperationForFailure(kernelServices, Operations.createRemoveOperation(handlerAddress));

        // Remove the handler from the loggers, then attempt to successfully remove
        builder = CompositeOperationBuilder.create();
        op = Operations.createOperation("remove-handler", loggerAddress);
        op.get("name").set("FILE");
        builder.addStep(op.clone());
        op = Operations.createOperation("remove-handler", rootLoggerAddress);
        op.get("name").set("FILE");
        builder.addStep(op.clone());
        builder.addStep(Operations.createRemoveOperation(handlerAddress));

        executeOperation(kernelServices, builder.build().getOperation());
    }
}
