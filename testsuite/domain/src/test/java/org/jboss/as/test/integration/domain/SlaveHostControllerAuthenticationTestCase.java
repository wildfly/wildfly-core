/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT_OPTIONS;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.test.integration.domain.management.util.Authentication;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.test.security.VaultHandler;

/**
 * Test a slave HC connecting to the domain using all 3 valid ways of configuring the slave HC's credential:
 * Base64 encoded password, system-property-backed expression, and vault expression.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SlaveHostControllerAuthenticationTestCase extends AbstractSlaveHCAuthenticationTestCase {

    private static final String VAULT_BLOCK = "ds_TestDS";
    private static final String RIGHT_PASSWORD = DomainLifecycleUtil.SLAVE_HOST_PASSWORD;

    private static ModelControllerClient domainMasterClient;
    private static ModelControllerClient domainSlaveClient;
    private static DomainTestSupport testSupport;

    static final String RESOURCE_LOCATION = SlaveHostControllerAuthenticationTestCase.class.getProtectionDomain().getCodeSource().getLocation().getFile()
            + "vault-shcatc/";

    @BeforeClass
    public static void setupDomain() throws Exception {

        // Set up a domain with a master that doesn't support local auth so slaves have to use configured credentials
        testSupport = DomainTestSupport.create(
                DomainTestSupport.Configuration.create(SlaveHostControllerAuthenticationTestCase.class.getSimpleName(),
                        "domain-configs/domain-standard.xml",
                        "host-configs/host-master-no-local.xml", "host-configs/host-secrets.xml"));

        // Tweak the callback handler so the master test driver client can authenticate
        // To keep setup simple it uses the same credentials as the slave host
        WildFlyManagedConfiguration masterConfig = testSupport.getDomainMasterConfiguration();
        CallbackHandler callbackHandler = Authentication.getCallbackHandler("slave", RIGHT_PASSWORD, "ManagementRealm");
        masterConfig.setCallbackHandler(callbackHandler);

        testSupport.start();

        domainMasterClient = testSupport.getDomainMasterLifecycleUtil().getDomainClient();
        domainSlaveClient = testSupport.getDomainSlaveLifecycleUtil().getDomainClient();

    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.stop();
        testSupport = null;
        domainMasterClient = null;
        domainSlaveClient = null;
    }

    @Test
    public void testSlaveRegistration() throws Exception {
        slaveWithBase64PasswordTest();
        slaveWithSystemPropertyPasswordTest();
        slaveWithVaultPasswordTest();
    }

    private void slaveWithBase64PasswordTest() throws Exception {
        // Simply check that the initial startup produced a registered slave
        readHostControllerStatus(domainMasterClient);
    }

    private void slaveWithSystemPropertyPasswordTest() throws Exception {

        // Set the slave secret to a system-property-backed expression
        setSlaveSecret("${slave.secret:" + RIGHT_PASSWORD + "}");

        reloadSlave();
        testSupport.getDomainSlaveLifecycleUtil().awaitHostController(System.currentTimeMillis());
        // Validate that it joined the master
        readHostControllerStatus(domainMasterClient);
    }

    private void slaveWithVaultPasswordTest() throws Exception {

        VaultHandler.cleanFilesystem(RESOURCE_LOCATION, true);

        // create new vault
        VaultHandler vaultHandler = new VaultHandler(RESOURCE_LOCATION);

        try {

            // create security attributes
            String attributeName = "value";
            String vaultPasswordString = vaultHandler.addSecuredAttribute(VAULT_BLOCK, attributeName,
                    RIGHT_PASSWORD.toCharArray());

            // create new vault setting in host
            ModelNode op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(HOST, "slave").add(CORE_SERVICE, VAULT);
            ModelNode vaultOption = op.get(VAULT_OPTIONS);
            vaultOption.get("KEYSTORE_URL").set(vaultHandler.getKeyStore());
            vaultOption.get("KEYSTORE_PASSWORD").set(vaultHandler.getMaskedKeyStorePassword());
            vaultOption.get("KEYSTORE_ALIAS").set(vaultHandler.getAlias());
            vaultOption.get("SALT").set(vaultHandler.getSalt());
            vaultOption.get("ITERATION_COUNT").set(vaultHandler.getIterationCountAsString());
            vaultOption.get("ENC_FILE_DIR").set(vaultHandler.getEncodedVaultFileDirectory());
            domainSlaveClient.execute(new OperationBuilder(op).build());

            setSlaveSecret("${" + vaultPasswordString + "}");

            reloadSlave();

            testSupport.getDomainSlaveLifecycleUtil().awaitHostController(System.currentTimeMillis());
            // Validate that it joined the master
            readHostControllerStatus(domainMasterClient);
        } finally {
            // remove temporary files
            vaultHandler.cleanUp();
        }
    }

    @Override
    protected ModelControllerClient getDomainMasterClient() {
        return domainMasterClient;
    }

    @Override
    protected ModelControllerClient getDomainSlaveClient() {
        return domainSlaveClient;
    }
}
