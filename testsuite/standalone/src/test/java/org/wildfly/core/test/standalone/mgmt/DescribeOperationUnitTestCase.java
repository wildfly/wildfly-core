/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.util.HashSet;
import java.util.Set;
import jakarta.inject.Inject;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test validating that subsystems register a "describe" operation in order to be able
 * to run in the domain mode.
 *
 * @author Emanuel Muckenhuber
 */
@RunWith(WildFlyRunner.class)
public class DescribeOperationUnitTestCase {

    private static final Set<String> ignored = new HashSet<String>();

    @Inject
    private static ManagementClient managementClient;

    static {
        // Only a few subsystems are NOT supposed to work in the domain mode
        ignored.add("deployment-scanner");
    }

    @Test
    public void testOperationNames() throws Exception {
        // Get a list of all registered subsystems
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(CHILD_TYPE).set(SUBSYSTEM);

        final ModelNode subsystemsResult = executeForResult(operation);
        for(final ModelNode subsystem : subsystemsResult.asList()) {
            final String name = subsystem.asString();
            if(ignored.contains(name)) {
                continue; // Only a few subsystems are not supposed to work in the domain mode
            }

            final ModelNode address = new ModelNode();
            address.add(SUBSYSTEM, name);

            // Check that the actual describe operation completes successfully
            final ModelNode describe = new ModelNode();
            describe.get(OP).set(DESCRIBE);
            describe.get(OP_ADDR).set(address);
            executeForResult(describe);

            // Check that the describe operation is registered a 'private' operation
            final ModelNode operationNames = new ModelNode();
            operationNames.get(OP).set(READ_OPERATION_NAMES_OPERATION);
            operationNames.get(OP_ADDR).set(address);

            final ModelNode operationNamesResult = executeForResult(operationNames);
            boolean found = false;
            for(final ModelNode operationName : operationNamesResult.asList()) {
                if(DESCRIBE.equals(operationName.asString())) {
                    found = true;
                    break;
                }
            }
            Assert.assertFalse(String.format("'describe' operation not registered as private in subsystem '%s'", name), found);
        }
    }

    private ModelNode executeForResult(final ModelNode operation) throws Exception {
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        checkSuccessful(result, operation);
        return result.get(RESULT);
    }

    static void checkSuccessful(final ModelNode result, final ModelNode operation) {
        if(! SUCCESS.equals(result.get(OUTCOME).asString())) {
            System.out.println("Failed result:\n" + result + "\n for operation:\n" + operation);
            Assert.fail("operation failed: " + result.get(FAILURE_DESCRIPTION));
        }
    }

}
