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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURITY_REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.test.integration.credential.store.ManagementAuthenticationUsersServerSetupTask.CREDENTIAL_STORE_NAME;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Hynek Švábek <hsvabek@redhat.com>
 *
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup(ManagementAuthenticationUsersServerSetupTask.class)
public class ManagementAuthenticationUsersTestCase {

    @Inject
    protected ManagementClient managementClient;

    private static final String USERNAME_1 = "user_1";
    private static final String USERNAME_2 = "user_2";
    private static final String PASSWORD_1 = "password1!";
    private static final String PASSWORD_2 = "password2!";

    @Test
    public void testCredentialReference() throws Exception {
        addAliasOperation(getCredentialStoreAddress(CREDENTIAL_STORE_NAME), USERNAME_1, PASSWORD_1);
        addAliasOperation(getCredentialStoreAddress(CREDENTIAL_STORE_NAME), USERNAME_2, PASSWORD_2);

        authenticationUsersCredentialReferenceClearTextTest(USERNAME_1, PASSWORD_1);
        authenticationUsersCredentialReferenceStoreAliasTest(CREDENTIAL_STORE_NAME, USERNAME_2, PASSWORD_2);
        invalidCredentials();
    }

    private void invalidCredentials() throws IOException {
        processWhoamiOperationFail(USERNAME_1, PASSWORD_2);
        processWhoamiOperationFail(USERNAME_2, PASSWORD_1);
    }

    private void authenticationUsersCredentialReferenceClearTextTest(String username, String pwd) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(ModelDescriptionConstants.ADD);
        op.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(SECURITY_REALM, "ManagementRealm")
            .add(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.USERS)
            .add(ModelDescriptionConstants.USER, username);
        op.get("credential-reference").set(prepareCredentialReference(pwd));
        getClient().execute(new OperationBuilder(op).build());

        reload();
        processWhoamiOperationSuccess(USERNAME_1, pwd);
    }

    private void authenticationUsersCredentialReferenceStoreAliasTest(String storeName, String username, String pwd)
        throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(ModelDescriptionConstants.ADD);
        op.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(SECURITY_REALM, "ManagementRealm")
            .add(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.USERS)
            .add(ModelDescriptionConstants.USER, username);
        op.get("credential-reference").set(prepareCredentialReference(storeName, username));
        getClient().execute(new OperationBuilder(op).build());

        reload();
        processWhoamiOperationSuccess(USERNAME_2, pwd);
    }

    private ModelNode getCredentialStoreAddress(String storeName) {
        return Operations.createAddress(SUBSYSTEM, "elytron", "credential-store", storeName);
    }

    protected void addAliasOperation(ModelNode credentialStoreAddress, String aliasName, String secretValue)
        throws IOException {
        ModelNode op = Operations.createOperation("add-alias", credentialStoreAddress);
        op.get("secret-value").set(secretValue);
        op.get("alias").set(aliasName);
        getClient().execute(new OperationBuilder(op).build());
    }

    private ModelNode prepareCredentialReference(String clearText) {
        return prepareCredentialReference(clearText, null, null);
    }

    private ModelNode prepareCredentialReference(String store, String alias) {
        return prepareCredentialReference(null, store, alias);
    }

    private ModelNode prepareCredentialReference(String clearText, String store, String alias) {
        ModelNode credentialRefParams = new ModelNode();
        if (isNotBlank(clearText)) {
            credentialRefParams.get("clear-text").set(clearText);
        }
        if (isNotBlank(alias)) {
            credentialRefParams.get("alias").set(alias);
        }
        if (isNotBlank(store)) {
            credentialRefParams.get("store").set(store);
        }
        return credentialRefParams;
    }

    protected ModelControllerClient getClient() {
        return managementClient.getControllerClient();
    }

    private void reload() {
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
    }

    private boolean isNotBlank(String string) {
        return string != null && !string.isEmpty();
    }

    private void processWhoamiOperationSuccess(String username, String pwd) throws IOException {
        CliProcessWrapper cli = processWhoamiCommand(username, pwd);

        assertTrue("Output: '" + cli.getOutput() + "'",
            cli.getOutput().trim().contains(String.format("\"username\" => \"%s\"", username)));
    }

    private void processWhoamiOperationFail(String username, String pwd) throws IOException {
        CliProcessWrapper cli = processWhoamiCommand(username, pwd);

        assertTrue("Output: '" + cli.getOutput() + "'",
            cli.getOutput().trim().contains("uthentication failed: all available authentication mechanisms failed"));
    }

    private CliProcessWrapper processWhoamiCommand(String username, String pwd) throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
            .addCliArgument("--connect")
            .addCliArgument(String.format("--user=%s", username))
            .addCliArgument(String.format("--password=%s", pwd))
            .addCliArgument("--controller=" + managementClient.getMgmtAddress() + ":" + managementClient.getMgmtPort())
            .addCliArgument("--command=:whoami");
        cli.executeNonInteractive();
        return cli;
    }
}
