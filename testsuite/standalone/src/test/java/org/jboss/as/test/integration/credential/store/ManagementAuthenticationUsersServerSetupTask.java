/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.jboss.as.test.integration.credential.store;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURITY_REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.Assert;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 *
 * @author Hynek Švábek <hsvabek@redhat.com>
 *
 */
public class ManagementAuthenticationUsersServerSetupTask implements ServerSetupTask {

    public static final String CREDENTIAL_STORE_NAME = "ManagementAuthenticationUsersCredentialStore";
    private static final Path CREDNETIAL_STORE_STORAGE_FILE = Paths.get("target/", CREDENTIAL_STORE_NAME + ".jceks");
    private ModelNode backupProperties;

    @Override
    public void setup(final ManagementClient managementClient) throws Exception {
        final ModelControllerClient client = managementClient.getControllerClient();

        ModelNode op = new ModelNode();
        op.get(OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        op.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(SECURITY_REALM, "ManagementRealm")
            .add(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.PROPERTIES);
        backupProperties = executeForSuccess(client, op);

        op = new ModelNode();
        op.get(OP).set(REMOVE);
        op.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(SECURITY_REALM, "ManagementRealm")
            .add(AUTHENTICATION, PROPERTIES);
        executeForSuccess(client, op);

        op = new ModelNode();
        op.get(OP).set(ModelDescriptionConstants.ADD);
        op.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(SECURITY_REALM, "ManagementRealm")
            .add(AUTHENTICATION, USERS);
        executeForSuccess(client, op);

        createCredentialStore(client);

        ServerReload.executeReloadAndWaitForCompletion(client, ServerReload.TIMEOUT);
    }

    @Override
    public void tearDown(final ManagementClient managementClient) throws Exception {
        final ModelControllerClient client = managementClient.getControllerClient();

        ModelNode op = new ModelNode();
        op.get(OP).set(REMOVE);
        op.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(SECURITY_REALM, "ManagementRealm")
            .add(AUTHENTICATION, USERS);
        executeForSuccess(client, op);

        op = new ModelNode();
        op.get(OP).set(ModelDescriptionConstants.ADD);
        op.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(SECURITY_REALM, "ManagementRealm")
            .add(AUTHENTICATION, PROPERTIES);

        for (Property property : backupProperties.asPropertyList()) {
            op.get(property.getName()).set(property.getValue());
        }

        executeForSuccess(client, op);

        removeCredentialStore(client);

        ServerReload.executeReloadAndWaitForCompletion(client, ServerReload.TIMEOUT);
    }

    private ModelNode executeForSuccess(final ModelControllerClient client, final ModelNode op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).asString());
        }
        return Operations.readResult(result);
    }

    private void createCredentialStore(ModelControllerClient client) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(SUBSYSTEM, "elytron").add("credential-store", CREDENTIAL_STORE_NAME);

        op.get("location").set(CREDNETIAL_STORE_STORAGE_FILE.toAbsolutePath().toString());
        op.get("create").set(true);

        ModelNode credentialRefParams = new ModelNode();
        credentialRefParams.get("clear-text").set("password123");
        op.get("credential-reference").set(credentialRefParams);
        executeForSuccess(client, op);
    }

    private void removeCredentialStore(ModelControllerClient client) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(REMOVE);
        op.get(OP_ADDR).add(SUBSYSTEM, "elytron").add("credential-store", CREDENTIAL_STORE_NAME);
        executeForSuccess(client, op);

        Files.deleteIfExists(CREDNETIAL_STORE_STORAGE_FILE);
    }
}
