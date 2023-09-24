/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.operations;

import static org.junit.Assert.*;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractLoggingOperationsTestCase extends AbstractLoggingTestCase {

    @BeforeClass
    public static void deploy() throws Exception {
        deploy(createDeployment(), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    static ModelNode createRootLoggerAddress() {
        return createAddress("root-logger", "ROOT");
    }

    static ModelNode createCustomHandlerAddress(final String name) {
        return createAddress("custom-handler", name);
    }

    protected ModelNode testWrite(final ModelNode address, final String attribute, final String value) throws IOException {
        return testWrite(address, attribute, value, true);
    }

    protected ModelNode testWrite(final ModelNode address, final String attribute, final String value, final boolean reloadIfRequired) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createWriteAttributeOperation(address, attribute, value));
        // Create the read operation
        builder.addStep(Operations.createReadAttributeOperation(address, attribute));
        final ModelNode result = executeOperation(builder.build(), reloadIfRequired);
        assertEquals(value, Operations.readResult(Operations.readResult(result).get("step-2")).asString());
        return result;
    }

    protected ModelNode testWrite(final ModelNode address, final String attribute, final boolean value) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createWriteAttributeOperation(address, attribute, value));
        // Create the read operation
        builder.addStep(Operations.createReadAttributeOperation(address, attribute));
        final ModelNode result = executeOperation(builder.build());
        assertEquals(value, Operations.readResult(Operations.readResult(result).get("step-2")).asBoolean());
        return result;
    }

    protected ModelNode testWrite(final ModelNode address, final String attribute, final ModelNode value) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createWriteAttributeOperation(address, attribute, value));
        // Create the read operation
        builder.addStep(Operations.createReadAttributeOperation(address, attribute));
        final ModelNode result = executeOperation(builder.build());
        assertEquals(value, Operations.readResult(Operations.readResult(result).get("step-2")));
        return result;
    }

    protected ModelNode testUndefine(final ModelNode address, final String attribute) throws IOException {
        return testUndefine(address, attribute, false);
    }

    protected ModelNode testUndefine(final ModelNode address, final String attribute, final boolean expectFailure) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createUndefineAttributeOperation(address, attribute));
        // Create the read operation
        final ModelNode readOp = Operations.createReadAttributeOperation(address, attribute);
        readOp.get("include-defaults").set(false);
        builder.addStep(readOp);
        final ModelNode result = client.getControllerClient().execute(builder.build());
        if (expectFailure) {
            assertFalse("Undefining attribute " + attribute + " should have failed.", Operations.isSuccessfulOutcome(result));
        } else {
            if (!Operations.isSuccessfulOutcome(result)) {
                Assert.fail(Operations.getFailureDescription(result).toString());
            }
            assertFalse("Attribute '" + attribute + "' was not undefined.", Operations.readResult(Operations.readResult(result).get("step-2"))
                    .isDefined());
        }
        return result;
    }

    protected void verifyRemoved(final ModelNode address) throws IOException {
        final ModelNode op = Operations.createReadResourceOperation(address);
        final ModelNode result = client.getControllerClient().execute(op);
        assertFalse("Resource not removed: " + address, Operations.isSuccessfulOutcome(result));
    }
}
