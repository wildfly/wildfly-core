/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
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
