/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.snapshot;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;

import java.io.File;

import static org.junit.Assert.fail;

public class ServerSnapshot {
    public static AutoCloseable takeSnapshot(ManagementClient client) {
        return takeSnapshot(client, null);
    }
    /**
     * Takes a snapshot of the current state of the server.
     *
     * Returns a AutoCloseable that can be used to restore the server state
     * @param client The client
     * @param reloadToStability the stability the AutoCloseable should reload to
     * @return A closeable that can be used to restore the server
     */
    public static AutoCloseable takeSnapshot(ManagementClient client, Stability reloadToStability) {
        try {
            ModelNode node = new ModelNode();
            node.get(ModelDescriptionConstants.OP).set("take-snapshot");
            ModelNode result = client.getControllerClient().execute(node);
            if (!"success".equals(result.get(ClientConstants.OUTCOME).asString())) {
                fail("Reload operation didn't finish successfully: " + result.asString());
            }
            String snapshot = result.get(ModelDescriptionConstants.RESULT).asString();
            final String fileName = snapshot.contains(File.separator) ? snapshot.substring(snapshot.lastIndexOf(File.separator) + 1) : snapshot;
            return new AutoCloseable() {
                @Override
                public void close() throws Exception {
                    ServerReload.Parameters parameters = new ServerReload.Parameters();
                    parameters.setServerConfig(fileName);
                    if (reloadToStability != null) {
                        parameters.setStability(reloadToStability);
                    }
                    ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient(), parameters);

                    ModelNode node = new ModelNode();
                    node.get(ModelDescriptionConstants.OP).set("write-config");
                    ModelNode result = client.getControllerClient().execute(node);
                    if (!"success".equals(result.get(ClientConstants.OUTCOME).asString())) {
                        fail("Failed to write config after restoring from snapshot " + result.asString());
                    }
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to take snapshot", e);
        }
    }

}
