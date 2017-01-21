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

package org.wildfly.core.test.standalone.mgmt.api.core;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT_OPTIONS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.security.VaultHandler;

/**
 * Test for WFCORE-2182
 *
 * @author Brian Stansberry
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup(MissingVaultExpressionTestCase.VaultSetupTask.class)
public class MissingVaultExpressionTestCase {

    private static final PathAddress LOGGER_ADDRESS = PathAddress.pathAddress("subsystem", "logging")
            .append("logger", MissingVaultExpressionTestCase.class.getSimpleName());

    private static VaultHandler vaultHandler;

    static class VaultSetupTask implements ServerSetupTask {

        private static final String RESOURCE_LOCATION = MissingVaultExpressionTestCase.class.getProtectionDomain().getCodeSource().getLocation().getFile()
                + "vault-masked/";

        @Override
        public void setup(ManagementClient managementClient) throws Exception {

            VaultHandler.cleanFilesystem(RESOURCE_LOCATION, true);

            // create new vault
            vaultHandler = new VaultHandler(RESOURCE_LOCATION);
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            // remove temporary files
            vaultHandler.cleanUp();
        }

    }

    @Inject
    private ManagementClient managementClient;

    @After
    public void after() throws IOException {
        try {
            // Remove the vault
            ModelNode op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).add(CORE_SERVICE, VAULT);
            managementClient.getControllerClient().execute(op);

        } finally {
            // Just in case the logger got added successfully try and remove it.
            ModelNode remove = Util.createRemoveOperation(LOGGER_ADDRESS);
            managementClient.getControllerClient().execute(remove);
            // If it fails I assume it was because the logger wasn't added.
        }
    }

    @Test
    public void testResolutionOfMissingVaultData() throws IOException {

        ModelControllerClient mcc = managementClient.getControllerClient();

        // Log with an invalid system property expression just to capture the message
        ModelNode add = Util.createAddOperation(LOGGER_ADDRESS);
        add.get("level").set("${foo." + getClass().getSimpleName() + "}");
        ModelNode response = mcc.execute(add);
        String propMissId = getFailureDescriptionId(response);

        // Now try a vault expression, but with no vault configured
        // If a VaultReader is present (not expected to be true at the time of writing this)
        // then this tests the handling by the VaultReader when no vault is configured.
        // If no VaultReader is present (expected in WildFly Core as the standard impl can't
        // be instantiated without the non-present org.picketbox module) then this tests
        // how resolution deals with vault expressions without any VaultReader
        add.get("level").set("${VAULT::logging::level::12345}");
        response = mcc.execute(add);
        String noVaultMissId = getFailureDescriptionId(response);
        assertEquals(response.toString(), propMissId, noVaultMissId);

        // Now add the vault. Should still fail

        // NOTE: at the time of writing this test, adding the vault shouldn't really
        // matter as WildFly Core does not include the org.picketbox module that
        // allows the vault to work. But we include this anyway so we have testing in
        // place in case that changes.

        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add(CORE_SERVICE, VAULT);
        ModelNode vaultOption = op.get(VAULT_OPTIONS);
        vaultOption.get("KEYSTORE_URL").set(vaultHandler.getKeyStore());
        vaultOption.get("KEYSTORE_PASSWORD").set(vaultHandler.getMaskedKeyStorePassword());
        vaultOption.get("KEYSTORE_ALIAS").set(vaultHandler.getAlias());
        vaultOption.get("SALT").set(vaultHandler.getSalt());
        vaultOption.get("ITERATION_COUNT").set(vaultHandler.getIterationCountAsString());
        vaultOption.get("ENC_FILE_DIR").set(vaultHandler.getEncodedVaultFileDirectory());
        mcc.execute(op);

        response = mcc.execute(add);
        String vaultMissId = getFailureDescriptionId(response);
        assertEquals(response.toString(), propMissId, vaultMissId);

    }

    private static String getFailureDescriptionId(ModelNode response) {
        assertEquals(response.toString(), "failed", response.get("outcome").asString());
        ModelNode failDesc = response.get("failure-description");
        assertTrue(response.toString(), failDesc.isDefined());
        assertEquals(response.toString(), ModelType.STRING, failDesc.getType());
        String str = failDesc.asString();
        int idx = str.indexOf(':');
        assertTrue(str, idx > 0);
        return str.substring(0, idx);
    }
}
