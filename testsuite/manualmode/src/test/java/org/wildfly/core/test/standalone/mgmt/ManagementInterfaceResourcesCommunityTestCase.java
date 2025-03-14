/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.client.helpers.Operations.createAddress;
import static org.jboss.as.controller.client.helpers.Operations.createWriteAttributeOperation;
import static org.jboss.as.test.integration.management.util.ServerReload.executeReloadAndWaitForCompletion;

import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.test.stability.StabilityServerSetupSnapshotRestoreTasks;

/**
 * Test case to test resource limits and clean up of management interface connections.
 *
 * This test case uses attributes defined directly on the HTTP management interface resource for configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ManagementInterfaceResourcesCommunityTestCase extends AbstractManagementInterfaceResourcesTestCase {

    /*
    * Attribute names
    */
    private static final String BACKLOG_ATTRIBUTE = "backlog";
    private static final String CONNECTION_HIGH_WATER_ATTRIBUTE = "connection-high-water";
    private static final String CONNECTION_LOW_WATER_ATTRIBUTE = "connection-low-water";
    private static final String NO_REQUEST_TIMEOUT_ATTRIBUTE = "no-request-timeout";

    private static final ModelNode HTTP_INTERFACE_ADDRESS = createAddress("core-service", "management", "management-interface", "http-interface");

    protected void runTest(int noRequestTimeout, ExceptionRunnable<Exception> test) throws Exception {
        controller.start();
        ManagementClient client = controller.getClient();

        ServerSetupTask task = new ManagementInterfaceSetUpTask(noRequestTimeout);

        try {
            task.setup(client);
            test.run();
            controller.reload();
        } finally {
            task.tearDown(client);
            controller.stop();
        }
    }

    class ManagementInterfaceSetUpTask extends StabilityServerSetupSnapshotRestoreTasks.Community {

        private final int noRequestTimeout;

        public ManagementInterfaceSetUpTask(int noRequestTimeout) {
            this.noRequestTimeout = noRequestTimeout;
        }

        protected void doSetup(ManagementClient managementClient) throws Exception {
            writeAttribute(managementClient, BACKLOG_ATTRIBUTE, 2);
            writeAttribute(managementClient, CONNECTION_HIGH_WATER_ATTRIBUTE, 6);
            writeAttribute(managementClient, CONNECTION_LOW_WATER_ATTRIBUTE, 3);
            writeAttribute(managementClient, NO_REQUEST_TIMEOUT_ATTRIBUTE, noRequestTimeout);

            // Execute the reload
            ServerReload.Parameters parameters = new ServerReload.Parameters()
                .setStability(Stability.COMMUNITY);
            executeReloadAndWaitForCompletion(managementClient.getControllerClient(), parameters);
        }

        private void writeAttribute(final ManagementClient managementClient, final String attributeName, final int value) throws Exception {
            ModelNode writeOp = createWriteAttributeOperation(HTTP_INTERFACE_ADDRESS, attributeName, value);
            managementClient.executeForResult(writeOp);
        }

    }
}
