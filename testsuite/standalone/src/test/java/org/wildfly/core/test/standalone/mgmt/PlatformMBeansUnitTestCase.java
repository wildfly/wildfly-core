/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;

import java.util.HashSet;
import java.util.Set;
import jakarta.inject.Inject;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test validating that the platform mbean resources exist and are reachable.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(WildFlyRunner.class)
public class PlatformMBeansUnitTestCase {

    private static final Set<String> ignored = new HashSet<String>();

    static {
        // Only a few subsystems are NOT supposed to work in the domain mode
        ignored.add("deployment-scanner");
    }

    @Inject
    private ManagementClient managementClient;

    @Test
    public void testReadClassLoadingMXBean() throws Exception {
        // Get a list of all registered subsystems
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        ModelNode address = new ModelNode();
        address.add(CORE_SERVICE, PLATFORM_MBEAN);
        address.add(TYPE, "class-loading");
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set("loaded-class-count");

        final ModelNode result = executeForResult(operation);
        org.junit.Assert.assertEquals(ModelType.INT, result.getType());
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
