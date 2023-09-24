/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.operations;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.logmanager.handlers.QueueHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@ServerSetup(ServerReload.SetupTask.class)
@RunWith(WildFlyRunner.class)
public class CustomHandlerOperationsTestCase extends AbstractLoggingOperationsTestCase {

    @Test
    public void testCustomHandler() throws Exception {

        testCustomHandler(null);

        // Create the profile
        final String profileName = "test-profile";
        final ModelNode profileAddress = createAddress("logging-profile", profileName);
        final ModelNode addOp = Operations.createAddOperation(profileAddress);
        executeOperation(addOp);

        testCustomHandler(profileName);

        // Remove the profile
        executeOperation(Operations.createRemoveOperation(profileAddress));
        verifyRemoved(profileAddress);
    }

    private void testCustomHandler(final String profileName) throws Exception {
        final ModelNode address = createCustomHandlerAddress(profileName, "CONSOLE2");

        // Add the handler
        final ModelNode addOp = Operations.createAddOperation(address);
        addOp.get("module").set("org.jboss.logmanager");
        addOp.get("class").set(ConsoleHandler.class.getName());
        executeOperation(addOp);

        // Write each attribute and check the value
        testWrite(address, "level", "INFO");
        testWrite(address, "enabled", true);
        testWrite(address, "encoding", "utf-8");
        testWrite(address, "formatter", "[test] %d{HH:mm:ss,SSS} %-5p [%c] %s%E%n");
        testWrite(address, "filter-spec", "deny");
        // Create a properties value
        final ModelNode properties = new ModelNode().setEmptyObject();
        properties.get("autoFlush").set(true);
        properties.get("target").set("SYSTEM_OUT");
        testWrite(address, "properties", properties);

        // Undefine attributes
        testUndefine(address, "level");
        testUndefine(address, "enabled");
        testUndefine(address, "encoding");
        testUndefine(address, "formatter");
        testUndefine(address, "filter-spec");
        testUndefine(address, "properties");

        // Writing either of these requires a reload, but we want to skip the reload
        testWrite(address, "class", QueueHandler.class.getName(), false);
        testWrite(address, "module", "org.jboss.logmanager", false);

        // Clean-up
        executeOperation(Operations.createRemoveOperation(address));
        verifyRemoved(address);
    }

    private static ModelNode createCustomHandlerAddress(final String profileName, final String name) {
        if (profileName == null) {
            return createAddress("custom-handler", name);
        }
        return createAddress("logging-profile", profileName, "custom-handler", name);
    }

    static class CustomHandlerSetUp implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            //no-op
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            //TODO implement tearDown
            throw new UnsupportedOperationException();
        }
    }
}
