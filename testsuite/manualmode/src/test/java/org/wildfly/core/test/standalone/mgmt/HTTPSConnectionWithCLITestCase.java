/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.HTTPS_CONTROLLER;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_NATIVE_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.CLITestUtil;
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

/**
 * Testing https connection to the http management interface with cli console
 * with configured two-way SSL. CLI client uses client truststore with accepted
 * server's certificate and vice-versa. Keystores and truststores have valid
 * certificates until 25 Octover 2033.
 *
 * @author Filip Bogyai
 */

@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class HTTPSConnectionWithCLITestCase {

    private static Logger LOGGER = Logger.getLogger(HTTPSConnectionWithCLITestCase.class);

    private static final File WORK_DIR = new File("native-if-workdir");
    public static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    public static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    public static final File CLIENT_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_KEYSTORE);
    public static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);
    public static final File UNTRUSTED_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.UNTRUSTED_KEYSTORE);

    private static final String MANAGEMENT_NATIVE_REALM = "ManagementNativeRealm";
    private static final String JBOSS_CLI_FILE = "jboss-cli.xml";
    private static final File TRUSTED_JBOSS_CLI_FILE = new File(WORK_DIR, "trusted-jboss-cli.xml");
    private static final File UNTRUSTED_JBOSS_CLI_FILE = new File(WORK_DIR, "untrusted-jboss-cli.xml");

    private static final String TESTING_OPERATION = "/core-service=management/management-interface=http-interface:read-resource";

    private static final ServerResourcesSetup keystoreFilesSetup = new ServerResourcesSetup();
    private static final ManagementNativeRealmSetup managementNativeRealmSetup = new ManagementNativeRealmSetup();

    @Inject
    private static ServerController containerController;

    @BeforeClass
    public static void prepareServer() throws Exception {
        containerController.startInAdminMode();
        ManagementClient mgmtClient = containerController.getClient();
        //final ModelControllerClient client = mgmtClient.getControllerClient();
        keystoreFilesSetup.setup(mgmtClient);
        managementNativeRealmSetup.setup(mgmtClient);


        // To apply new security realm settings for http interface reload of  server is required
        reloadServer();
    }

    /**
     * Testing connection to server http interface with default CLI settings.
     * Client doesn't have servers certificate in truststore and therefore it rejects
     * connection.
     */
    @Test
    public void testDefaultCLIConfiguration() throws InterruptedException, IOException {
        String cliOutput = CustomCLIExecutor.execute(null, TESTING_OPERATION, HTTPS_CONTROLLER, true);
        assertThat("Untrusted client should not be authenticated.", cliOutput, not(containsString("\"outcome\" => \"success\"")));

    }

    /**
     * Testing connection to server http interface with CLI using wrong
     * certificate. Server doesn't have client certificate in truststore and
     * therefore it rejects connection.
     */
    @Test
    public void testUntrustedCLICertificate() throws InterruptedException, IOException {
        String cliOutput = CustomCLIExecutor.execute(UNTRUSTED_JBOSS_CLI_FILE, TESTING_OPERATION, HTTPS_CONTROLLER);
        assertThat("Untrusted client should not be authenticated.", cliOutput, not(containsString("\"outcome\" => \"success\"")));

    }

    /**
     * Testing connection to server http interface with CLI using trusted
     * certificate. Client has server certificate in truststore, and also
     * server has certificate from client, so client can successfully connect.
     */
    @Test
    public void testTrustedCLICertificate() throws InterruptedException, IOException {
        String cliOutput = CustomCLIExecutor.execute(TRUSTED_JBOSS_CLI_FILE, TESTING_OPERATION, HTTPS_CONTROLLER);
        assertThat("Client with valid certificate should be authenticated.", cliOutput, containsString("\"outcome\" => \"success\""));

    }

    @AfterClass
    public static void resetTestConfiguration() throws Exception {
        ModelControllerClient client = HTTPSManagementInterfaceTestCase.getNativeModelControllerClient();
        ManagementClient managementClient = new ManagementClient(client, TestSuiteEnvironment.getServerAddress(),
                MANAGEMENT_NATIVE_PORT, "remoting");

        HTTPSManagementInterfaceTestCase.resetHttpInterfaceConfiguration(client);

        // reload to apply changes
        reloadServer();//reload using CLI

        keystoreFilesSetup.tearDown(managementClient);
        managementNativeRealmSetup.tearDown(managementClient);

        containerController.stop();
        FileUtils.deleteDirectory(WORK_DIR);
    }

    public static void reloadServer() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext("remoting", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT);
        try {
            ctx.connectController();
            ctx.handle("reload");
        } finally {
            ctx.terminateSession();
        }
    }

    static class ManagementNativeRealmSetup extends AbstractBaseSecurityRealmsServerSetupTask {

        @Override
        protected SecurityRealm[] getSecurityRealms() throws Exception {
            final ServerIdentity serverIdentity = new ServerIdentity.Builder().ssl(
                    new RealmKeystore.Builder().keystorePassword(SecurityTestConstants.KEYSTORE_PASSWORD)
                            .keystorePath(SERVER_KEYSTORE_FILE.getAbsolutePath()).build()).build();
            final Authentication authentication = new Authentication.Builder().truststore(
                    new RealmKeystore.Builder().keystorePassword(SecurityTestConstants.KEYSTORE_PASSWORD)
                            .keystorePath(SERVER_TRUSTSTORE_FILE.getAbsolutePath()).build()).build();
            final SecurityRealm realm = new SecurityRealm.Builder().name(MANAGEMENT_NATIVE_REALM).serverIdentity(serverIdentity)
                    .authentication(authentication).build();
            return new SecurityRealm[]{realm};
        }
    }

    static class ServerResourcesSetup implements ServerSetupTask {

        public void setup(ManagementClient managementClient) throws Exception {

            // create key and trust stores with imported certificates from opposing sides
            FileUtils.deleteDirectory(WORK_DIR);
            WORK_DIR.mkdirs();
            CoreUtils.createKeyMaterial(WORK_DIR);

            // create jboss-cli.xml files with valid/invalid keystore certificates
            FileUtils.write(TRUSTED_JBOSS_CLI_FILE, CoreUtils.propertiesReplacer(JBOSS_CLI_FILE, CLIENT_KEYSTORE_FILE, CLIENT_TRUSTSTORE_FILE,
                    SecurityTestConstants.KEYSTORE_PASSWORD));
            FileUtils.write(UNTRUSTED_JBOSS_CLI_FILE, CoreUtils.propertiesReplacer(JBOSS_CLI_FILE, UNTRUSTED_KEYSTORE_FILE,
                    CLIENT_TRUSTSTORE_FILE, SecurityTestConstants.KEYSTORE_PASSWORD));

            final ModelControllerClient client = managementClient.getControllerClient();

            // secure http interface
            ModelNode operation = createOpNode("core-service=management/management-interface=http-interface",
                    ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set("security-realm");
            operation.get(VALUE).set(MANAGEMENT_NATIVE_REALM);
            CoreUtils.applyUpdate(operation, client);

            operation = createOpNode("core-service=management/management-interface=http-interface",
                    ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set("secure-socket-binding");
            operation.get(VALUE).set("management-https");
            CoreUtils.applyUpdate(operation, client);

            // add native socket binding
            operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native",ModelDescriptionConstants.ADD);
            operation.get("port").set(MANAGEMENT_NATIVE_PORT);
            operation.get("interface").set("management");
            CoreUtils.applyUpdate(operation, client);

            // create native interface to control server while http interface
            // will be secured
            operation = org.jboss.as.controller.operations.common.Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
            operation.get("steps").add(createOpNode("core-service=management/security-realm=native-realm", ModelDescriptionConstants.ADD));
            ModelNode localAuth = createOpNode("core-service=management/security-realm=native-realm/authentication=local", ModelDescriptionConstants.ADD);
            localAuth.get("default-user").set("$local");
            operation.get("steps").add(localAuth);
            CoreUtils.applyUpdate(operation, client);

            operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);
            operation.get("security-realm").set("native-realm");
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

            operation = createOpNode("core-service=management/security-realm=native-realm", ModelDescriptionConstants.REMOVE);
            CoreUtils.applyUpdate(operation, client);

            FileUtils.deleteDirectory(WORK_DIR);
        }
    }
}
