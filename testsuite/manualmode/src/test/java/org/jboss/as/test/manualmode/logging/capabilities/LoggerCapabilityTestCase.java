/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging.capabilities;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class LoggerCapabilityTestCase extends CapabilityTestCase {
    private static final String LOGGER_NAME = LoggerCapabilityTestCase.class.getName();

    @Test
    public void testAddInvalidHandlerToRootLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("root-logger", "ROOT");
        final ModelNode op = Operations.createOperation("add-handler", address);
        op.get("name").set("non-existing");
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testAddInvalidHandlerToLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("logger", LOGGER_NAME);
        final ModelNode op = Operations.createAddOperation(address);
        op.get("handlers").setEmptyList().add("non-existing");
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testWriteInvalidHandlerToRootLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("root-logger", "ROOT");
        final ModelNode handlers = new ModelNode().setEmptyList().add("non-existing");
        final ModelNode op = Operations.createWriteAttributeOperation(address, "handlers", handlers);
        executeOperationForFailure(op, NOT_FOUND);
    }

    @Test
    public void testWriteInvalidHandlerToLogger() throws IOException {
        final ModelNode address = createSubsystemAddress("logger", LOGGER_NAME);
        executeOperation(Operations.createAddOperation(address));
        final ModelNode handlers = new ModelNode().setEmptyList().add("non-existing");
        final ModelNode op = Operations.createWriteAttributeOperation(address, "handlers", handlers);
        executeOperationForFailure(op, NOT_FOUND);
        executeOperation(Operations.createRemoveOperation(address));
    }
}
