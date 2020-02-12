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

package org.jboss.as.logging;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.logging.filters.FilterResourceDefinition;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FilterOperationsTestCase extends AbstractOperationsTestCase {

    private KernelServices kernelServices;

    @Before
    public void startTestContainer() throws Exception {
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
    public void testAddFilter() {
        testAddFilter(null);
        testAddFilter(PROFILE);
    }

    @Test
    public void testLoggerFilter() {
        testLoggerFilter(null);
        testLoggerFilter(PROFILE);
    }

    @Test
    public void testHandlerFilter() {
        testHandlerFilter(null);
        testHandlerFilter(PROFILE);
    }

    @Test
    public void testInvalidFilterNames() {
        executeOperationForFailure(kernelServices,
                createAddFilterOp(createAddress("filter", "test-filter").toModelNode()));
        executeOperationForFailure(kernelServices,
                createAddFilterOp(createAddress("filter", "0test").toModelNode()));
        executeOperationForFailure(kernelServices,
                createAddFilterOp(createAddress("filter", "levelRange").toModelNode()));
    }

    @Test
    public void testFilterRemoveFailure() {
        testFilterRemoveFailure(null);
        testFilterRemoveFailure(PROFILE);
    }

    private void testAddFilter(final String profileName) {
        final ModelNode address = createAddress(profileName, "filter", "test").toModelNode();
        executeOperation(kernelServices, createAddFilterOp(address));

        final ModelNode constructorProperties = new ModelNode().setEmptyObject();
        constructorProperties.get("constructorText").set(" | constructor property text");
        testWrite(kernelServices, address, FilterResourceDefinition.CONSTRUCTOR_PROPERTIES, constructorProperties);

        final ModelNode properties = new ModelNode().setEmptyObject();
        properties.get("propertyText").set(" | property text");
        testWrite(kernelServices, address, CommonAttributes.PROPERTIES, properties);

        // These should likely be tests last as they are not valid values
        testWrite(kernelServices, address, CommonAttributes.MODULE, "org.jboss.as.logging.changed");
        testWrite(kernelServices, address, CommonAttributes.CLASS, "org.jboss.as.logging.test.ChangedFilter");

        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
    }

    private void testLoggerFilter(final String profileName) {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode filterAddress = createAddress(profileName, "filter", "test").toModelNode();
        builder.addStep(createAddFilterOp(filterAddress));

        // Add to the root logger
        final ModelNode rootLoggerAddress = createRootLoggerAddress(profileName).toModelNode();
        ModelNode op = SubsystemOperations.createAddOperation(rootLoggerAddress);
        op.get("filter-spec").set("test");
        builder.addStep(op);

        // Add to a logger
        final ModelNode loggerAddress = createLoggerAddress(profileName, "org.jboss.as.logging").toModelNode();
        op = SubsystemOperations.createAddOperation(loggerAddress);
        op.get("filter-spec").set("any(test, accept, match(\".*\"))");
        builder.addStep(op);

        executeOperation(kernelServices, builder.build().getOperation());

        testWrite(kernelServices, rootLoggerAddress, LoggerAttributes.FILTER_SPEC, "all(accept, test)");
        testWrite(kernelServices, loggerAddress, LoggerAttributes.FILTER_SPEC, "test");

        executeOperationForFailure(kernelServices, SubsystemOperations.createRemoveOperation(filterAddress));

        // Remove the loggers, then the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createRemoveOperation(rootLoggerAddress));
        builder.addStep(Operations.createRemoveOperation(loggerAddress));
        builder.addStep(Operations.createRemoveOperation(filterAddress));
        executeOperation(kernelServices, builder.build().getOperation());
        verifyRemoved(kernelServices, filterAddress);
    }

    private void testHandlerFilter(final String profileName) {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode filterAddress = createAddress(profileName, "filter", "test").toModelNode();
        ModelNode op = SubsystemOperations.createAddOperation(filterAddress);
        op.get("module").set("org.jboss.as.logging.test");
        op.get("class").set(TestFilter.class.getName());
        builder.addStep(op);

        // Add to the root logger
        final ModelNode handlerAddress = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();
        op = SubsystemOperations.createAddOperation(handlerAddress);
        op.get("filter-spec").set("test");
        builder.addStep(op);

        executeOperation(kernelServices, builder.build().getOperation());

        testWrite(kernelServices, handlerAddress, LoggerAttributes.FILTER_SPEC, "all(levels(DEBUG, INFO, WARN, ERROR), test)");

        executeOperationForFailure(kernelServices, SubsystemOperations.createRemoveOperation(filterAddress));

        // Remove the loggers, then the filter
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createRemoveOperation(handlerAddress));
        builder.addStep(Operations.createRemoveOperation(filterAddress));
        executeOperation(kernelServices, builder.build().getOperation());
        verifyRemoved(kernelServices, filterAddress);
    }

    private void testFilterRemoveFailure(final String profileName) {
        CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode filterAddress = createAddress(profileName, "filter", "test").toModelNode();
        ModelNode op = SubsystemOperations.createAddOperation(filterAddress);
        op.get("module").set("org.jboss.as.logging.test");
        op.get("class").set(TestFilter.class.getName());
        builder.addStep(op);

        // Add to the root logger
        final ModelNode handlerAddress = createConsoleHandlerAddress(profileName, "CONSOLE").toModelNode();
        op = SubsystemOperations.createAddOperation(handlerAddress);
        op.get("filter-spec").set("test");
        builder.addStep(op);

        // Add to a logger
        final ModelNode loggerAddress = createLoggerAddress(profileName, "org.jboss.as.logging").toModelNode();
        op = SubsystemOperations.createAddOperation(loggerAddress);
        op.get("filter-spec").set("any(test, accept, match(\".*\"))");
        builder.addStep(op);

        executeOperation(kernelServices, builder.build().getOperation());

        // Remove the filter, then the filter from the handler which should fail
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createRemoveOperation(filterAddress));
        builder.addStep(Operations.createRemoveOperation(handlerAddress));
        executeOperationForFailure(kernelServices, builder.build().getOperation());

        // Remove the filter, then the filter from the logger which should fail
        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createRemoveOperation(filterAddress));
        builder.addStep(Operations.createRemoveOperation(loggerAddress));
        executeOperationForFailure(kernelServices, builder.build().getOperation());


        builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createRemoveOperation(loggerAddress));
        builder.addStep(Operations.createRemoveOperation(handlerAddress));
        builder.addStep(Operations.createRemoveOperation(filterAddress));
        executeOperation(kernelServices, builder.build().getOperation());
    }

    private static ModelNode createAddFilterOp(final ModelNode address) {
        final ModelNode op = SubsystemOperations.createAddOperation(address);
        op.get("module").set("org.jboss.as.logging.test");
        op.get("class").set(TestFilter.class.getName());
        return op;
    }
}
