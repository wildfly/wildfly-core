/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.vault;

import static org.hamcrest.CoreMatchers.containsString;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_NATIVE_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertThat;
import static org.wildfly.core.test.standalone.mgmt.HTTPSConnectionWithCLITestCase.reloadServer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.CustomCLIExecutor;
import org.jboss.as.test.integration.security.common.AbstractBaseSecurityRealmsServerSetupTask;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.integration.security.common.config.realm.Authentication;
import org.jboss.as.test.integration.security.common.config.realm.RealmKeystore;
import org.jboss.as.test.integration.security.common.config.realm.SecurityRealm;
import org.jboss.as.test.integration.security.common.config.realm.ServerIdentity;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.api.Authentication.CallbackHandler;

/**
 * Testing support for vault encrypted passwords in jboss-cli configuration
 * file. It tries to connect to native management interface with cli console
 * with configured SSL.
 *
 * @author Filip Bogyai
 */

@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class VaultPasswordsInCLITestCase {

    private static Logger LOGGER = Logger.getLogger(VaultPasswordsInCLITestCase.class);

    private static final File WORK_DIR = new File("cli-vault-workdir");
    public static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    public static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    public static final File CLIENT_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_KEYSTORE);
    public static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);

    // see genseckey-cli-vault-keystore in pom.xml for the keystore creation
    public static final File VAULT_KEYSTORE_FILE = new File("target/cli-vault.keystore");
    public static final File VAULT_CONFIG_FILE = new File(WORK_DIR, "vault-config-xml");
    public static final String VAULT_KEYSTORE_PASS = "secret_1";
    public static final String ALIAS_NAME = "vault";

    private static final String MANAGEMENT_CLI_REALM = "ManagementCLIRealm";
    private static final String JBOSS_CLI_FILE = "jboss-cli.xml";
    private static final File RIGHT_VAULT_PASSWORD_FILE = new File(WORK_DIR, "right-vault-pass.xml");
    private static final File WRONG_VAULT_PASSWORD_FILE = new File(WORK_DIR, "wrong-vault-pass.xml");

    private static final String TESTING_OPERATION = "/core-service=management/management-interface=http-interface:read-resource";

    private static final ManagementInterfacesSetup managementInterfacesSetup = new ManagementInterfacesSetup();
    private static final ManagemenCLIRealmSetup managementCLICliRealmSetup = new ManagemenCLIRealmSetup();

    @Inject
    private static ServerController containerController;


    @BeforeClass
    public static void prepareServer() throws Exception {

        LOGGER.trace("*** starting server");
        containerController.startInAdminMode();
        ManagementClient mgmtClient = containerController.getClient();
        managementInterfacesSetup.setup(mgmtClient);
        managementCLICliRealmSetup.setup(mgmtClient);

        createAndInitializeVault();

        // To apply new security realm settings for native interface reload of
        // server is required
        LOGGER.trace("*** reload server");
        reloadServer();
    }

    /**
     * Testing access to native interface with wrong password to keystore in
     * jboss-cli configuration. This password is masked and is loaded from
     * vault. Exception with message that password is incorrect should be
     * thrown.
     */
    @Test
    public void testWrongVaultPassword() throws InterruptedException, IOException {

        String cliOutput = CustomCLIExecutor.execute(WRONG_VAULT_PASSWORD_FILE, TESTING_OPERATION);
        assertThat("Password should be wrong", cliOutput, containsString("Keystore was tampered with, or password was incorrect"));

    }

    /**
     * Testing access to native interface with right password to keystore in
     * jboss-cli configuration. This password is masked and is loaded from
     * vault.
     */
    @Test
    public void testRightVaultPassword() throws InterruptedException, IOException {

        String cliOutput = CustomCLIExecutor.execute(RIGHT_VAULT_PASSWORD_FILE, TESTING_OPERATION);
        assertThat("Password should be right and authentication successful", cliOutput, containsString("\"outcome\" => \"success\""));

    }

    @AfterClass
    public static void resetConfigurationForNativeInterface() throws Exception {

        LOGGER.info("*** reseting test configuration");

        resetHttpInterfaceConfiguration(getNativeModelControllerClient());

        // reload to apply changes
        reloadServer();

        ManagementClient client = containerController.getClient();

        managementInterfacesSetup.tearDown(client);
        managementCLICliRealmSetup.tearDown(client);

        LOGGER.info("*** stopping container");
        containerController.stop();

        FileUtils.deleteDirectory(WORK_DIR);
    }

    static class ManagemenCLIRealmSetup extends AbstractBaseSecurityRealmsServerSetupTask {

        @Override
        protected SecurityRealm[] getSecurityRealms() throws Exception {
            final ServerIdentity serverIdentity = new ServerIdentity.Builder().ssl(
                    new RealmKeystore.Builder().keystorePassword(SecurityTestConstants.KEYSTORE_PASSWORD)
                            .keystorePath(SERVER_KEYSTORE_FILE.getAbsolutePath()).build()).build();
            final Authentication authentication = new Authentication.Builder().truststore(
                    new RealmKeystore.Builder().keystorePassword(SecurityTestConstants.KEYSTORE_PASSWORD)
                            .keystorePath(SERVER_TRUSTSTORE_FILE.getAbsolutePath()).build()).build();
            final SecurityRealm realm = new SecurityRealm.Builder().name(MANAGEMENT_CLI_REALM).serverIdentity(serverIdentity)
                    .authentication(authentication).build();
            return new SecurityRealm[]{realm};
        }
    }

    static class ManagementInterfacesSetup implements ServerSetupTask {

        public void setup(ManagementClient managementClient) throws Exception {
            FileUtils.deleteDirectory(WORK_DIR);
            WORK_DIR.mkdirs();
            CoreUtils.createKeyMaterial(WORK_DIR);

            final ModelControllerClient client = managementClient.getControllerClient();

            // secure http interface
            ModelNode operation = createOpNode("core-service=management/management-interface=http-interface",
                    ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set("security-realm");
            operation.get(VALUE).set(MANAGEMENT_CLI_REALM);
            CoreUtils.applyUpdate(operation, client);

            operation = createOpNode("core-service=management/management-interface=http-interface",
                    ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set("secure-socket-binding");
            operation.get(VALUE).set("management-https");
            CoreUtils.applyUpdate(operation, client);

            // add native socket binding
            operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.ADD);
            operation.get("port").set(MANAGEMENT_NATIVE_PORT);
            operation.get("interface").set("management");
            CoreUtils.applyUpdate(operation, client);


            // create native interface to control server while http interface will be secured
            operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);
            operation.get("security-realm").set("ManagementRealm");
            operation.get("socket-binding").set("management-native");
            CoreUtils.applyUpdate(operation, client);
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {

            final ModelControllerClient client = managementClient.getControllerClient();

            ModelNode operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.REMOVE);
            CoreUtils.applyUpdate(operation, client);

            operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.REMOVE);
            CoreUtils.applyUpdate(operation, client);

            FileUtils.deleteDirectory(WORK_DIR);

        }
    }

    private static void createAndInitializeVault() throws Exception {

        // see genseckey-cli-vault-keystore in pom.xml for the keystore creation
        String keystoreURL = VAULT_KEYSTORE_FILE.getAbsolutePath();
        String encryptionDirURL = WORK_DIR.getAbsolutePath();
        String salt = "87654321";
        int iterationCount = 20;
        TestVaultSession nonInteractiveSession = new TestVaultSession(keystoreURL, VAULT_KEYSTORE_PASS, encryptionDirURL, salt, iterationCount);
        nonInteractiveSession.startVaultSession(ALIAS_NAME);

        String rightPassword = SecurityTestConstants.KEYSTORE_PASSWORD;
        String wrongPassword = "blablabla";
        String rightBlock = "right";
        String wrongBlock = "wrong";
        String attributeName = "password";

        // write vault configuration to file
        FileUtils.write(VAULT_CONFIG_FILE, nonInteractiveSession.vaultConfiguration());

        // add right and wrong password for clients keystore into vault
        String vaultPasswordString = nonInteractiveSession.addSecuredAttribute(rightBlock, attributeName, rightPassword.toCharArray());
        String wrongVaultPasswordString = nonInteractiveSession.addSecuredAttribute(wrongBlock, attributeName, wrongPassword.toCharArray());

        String vaultConfiguration = "<vault file=\"" + VAULT_CONFIG_FILE.getAbsolutePath() + "\"/>";

        // create jboss-cli configuration file with ssl and vaulted passwords
        FileUtils.write(RIGHT_VAULT_PASSWORD_FILE, CoreUtils.propertiesReplacer(JBOSS_CLI_FILE, CLIENT_KEYSTORE_FILE, CLIENT_TRUSTSTORE_FILE,
                vaultPasswordString, vaultConfiguration));
        FileUtils.write(WRONG_VAULT_PASSWORD_FILE, CoreUtils.propertiesReplacer(JBOSS_CLI_FILE, CLIENT_KEYSTORE_FILE, CLIENT_TRUSTSTORE_FILE,
                wrongVaultPasswordString, vaultConfiguration));

    }

    private static void resetHttpInterfaceConfiguration(ModelControllerClient client) throws Exception {

        // change back security realm for http management interface
        ModelNode operation = createOpNode("core-service=management/management-interface=http-interface",
                ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("security-realm");
        operation.get(VALUE).set("ManagementRealm");
        CoreUtils.applyUpdate(operation, client);

        // undefine secure socket binding from http interface
        operation = createOpNode("core-service=management/management-interface=http-interface",
                ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("secure-socket-binding");
        CoreUtils.applyUpdate(operation, client);
    }

    private static ModelControllerClient getNativeModelControllerClient() {

        ModelControllerClient client = null;
        try {
            client = ModelControllerClient.Factory.create("remote", InetAddress.getByName(TestSuiteEnvironment.getServerAddress()),
                    MANAGEMENT_NATIVE_PORT, new CallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return client;
    }
}
