/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt.api;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALIDATE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.IOException;

import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.ModelUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests that the validate-operation operation works as it should
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildFlyRunner.class)
public class ValidateOperationOperationTestCase extends ContainerResourceMgmtTestBase {

    @Test
    public void testValidRootOperation() throws IOException, MgmtOperationException {
        ModelNode op = ModelUtil.createOpNode(null, READ_OPERATION_DESCRIPTION_OPERATION);
        op.get(NAME).set("Doesn't matter");
        executeOperation(createValidateOperation(op));
    }

    @Test
    public void testFailedRootOperation() throws IOException {
        ModelNode op = ModelUtil.createOpNode(null, READ_OPERATION_DESCRIPTION_OPERATION);
        executeInvalidOperation(op);
    }

    @Test()
    public void testValidChildOperation() throws IOException, MgmtOperationException {
        ModelNode op = ModelUtil.createOpNode("subsystem=jmx/remoting-connector=jmx", ADD);
        executeOperation(createValidateOperation(op));
    }

    @Test
    public void testInvalidChildOperation() throws IOException {
        ModelNode op = ModelUtil.createOpNode("subsystem=jmx/remoting-connector=jmx", ADD);
        op.get("nonexistent").set("stuff");
        executeInvalidOperation(op);
    }

    @Test
    public void testValidInheritedOperation() throws IOException, MgmtOperationException {
        ModelNode op = ModelUtil.createOpNode("subsystem=jmx/remoting-connector=jmx", READ_OPERATION_DESCRIPTION_OPERATION);
        op.get(NAME).set("Doesn't matter");
        executeOperation(createValidateOperation(op));
    }

    @Test
    public void testInvalidInheritedOperation() throws IOException {
        ModelNode op = ModelUtil.createOpNode("subsystem=jmx/remoting-connector=jmx", READ_OPERATION_DESCRIPTION_OPERATION);
        executeInvalidOperation(op);
    }

    private ModelNode createValidateOperation(ModelNode validatedOperation) throws IOException {
        ModelNode node = ModelUtil.createOpNode(null, VALIDATE_OPERATION);
        node.get(VALUE).set(validatedOperation);
        return node;
    }


    private void executeInvalidOperation(ModelNode operation) throws IOException {
        try {
            executeOperation(createValidateOperation(operation));
            Assert.fail("Should have failed on no required parameter included");
        } catch (MgmtOperationException expected) {
        }
    }

}
