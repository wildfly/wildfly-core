/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.optypes.OpTypesExtension;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests invocations of private and hidden operations.
 *
 * @author Brian Stansberry
 */
@RunWith(WildFlyRunner.class)
public class PrivateHiddenOperationsTestCase {

    private static final PathAddress EXT = PathAddress.pathAddress("extension", OpTypesExtension.EXTENSION_NAME);
    private static final PathAddress SUBSYSTEM = PathAddress.pathAddress("subsystem", OpTypesExtension.SUBSYSTEM_NAME);

    @Inject
    private static ManagementClient managementClient;

    @Before
    public void installExtensionModule() throws IOException {
        ExtensionUtils.createExtensionModule(OpTypesExtension.EXTENSION_NAME, OpTypesExtension.class,
                EmptySubsystemParser.class.getPackage());

        ModelNode addOp = Util.createAddOperation(EXT);
        executeOp(addOp, SUCCESS);
        addOp = Util.createAddOperation(SUBSYSTEM);
        executeOp(addOp, SUCCESS);
    }

    @After
    public void removeExtensionModule() throws IOException {

        try {
            executeOp(Util.createRemoveOperation(SUBSYSTEM), SUCCESS);
        } finally {
            try {
                executeOp(Util.createRemoveOperation(EXT), SUCCESS);
            } finally {
                ExtensionUtils.deleteExtensionModule(OpTypesExtension.EXTENSION_NAME);
            }
        }
    }

    @Test
    public void testPrivateHiddenOps() throws IOException {
        executeOp(Util.createEmptyOperation("hidden", SUBSYSTEM), SUCCESS);
        executeOp(Util.createEmptyOperation("private", SUBSYSTEM), FAILED);
    }

    private void executeOp(ModelNode op, String outcome) throws IOException {
        ModelNode response = managementClient.getControllerClient().execute(op);
        assertTrue(response.toString(), response.hasDefined(OUTCOME));
        assertEquals(response.toString(), outcome, response.get(OUTCOME).asString());
    }
}
