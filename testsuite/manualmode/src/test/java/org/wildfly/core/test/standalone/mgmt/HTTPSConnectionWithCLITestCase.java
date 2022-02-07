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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.HTTPS_CONTROLLER;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_NATIVE_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.net.InetAddress;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.integration.management.util.CustomCLIExecutor;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
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

    private static final File WORK_DIR = new File("native-if-workdir");
    public static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    public static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    public static final File CLIENT_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_KEYSTORE);
    public static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);
    public static final File UNTRUSTED_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.UNTRUSTED_KEYSTORE);

    private static final String ELYTRON_SSC = "elytronHttpsSSC";
    private static final String JBOSS_CLI_FILE = "jboss-cli.xml";
    private static final File TRUSTED_JBOSS_CLI_FILE = new File(WORK_DIR, "trusted-jboss-cli.xml");
    private static final File UNTRUSTED_JBOSS_CLI_FILE = new File(WORK_DIR, "untrusted-jboss-cli.xml");

    private static final String TESTING_OPERATION = "/core-service=management/management-interface=http-interface:read-resource";

    private static final ServerResourcesSetup serverResourcesSetup = new ServerResourcesSetup();

    @Inject
    private static ServerController containerController;

    @BeforeClass
    public static void prepareServer() throws Exception {

        containerController.startInAdminMode();

        serverResourcesSetup.setup(containerController.getClient());

        // To apply new security realm settings for http interface reload of  server is required
        reloadServer();
    }

    /**
     * Testing connection to server http interface with default CLI settings.
     * Client doesn't have servers certificate in truststore and therefore it rejects
     * connection.
     */
    @Test
    public void testDefaultCLIConfiguration() {
        String cliOutput = CustomCLIExecutor.execute(null, TESTING_OPERATION, HTTPS_CONTROLLER, true);
        assertThat("Untrusted client should not be authenticated.", cliOutput, not(containsString("\"outcome\" => \"success\"")));

    }

    /**
     * Testing connection to server http interface with CLI using wrong
     * certificate. Server doesn't have client certificate in truststore and
     * therefore it rejects connection.
     */
    @Test
    public void testUntrustedCLICertificate() {
        String cliOutput = CustomCLIExecutor.execute(UNTRUSTED_JBOSS_CLI_FILE, TESTING_OPERATION, HTTPS_CONTROLLER);
        assertThat("Untrusted client should not be authenticated.", cliOutput, not(containsString("\"outcome\" => \"success\"")));

    }

    /**
     * Testing connection to server http interface with CLI using trusted
     * certificate. Client has server certificate in truststore, and also
     * server has certificate from client, so client can successfully connect.
     */
    @Test
    public void testTrustedCLICertificate() {
        String cliOutput = CustomCLIExecutor.execute(TRUSTED_JBOSS_CLI_FILE, TESTING_OPERATION, HTTPS_CONTROLLER);
        assertThat("Client with valid certificate should be authenticated.", cliOutput, containsString("\"outcome\" => \"success\""));

    }

    @AfterClass
    public static void resetTestConfiguration() throws Exception {

        ModelControllerClient client = ModelControllerClient.Factory.create("remoting",
                InetAddress.getByName(TestSuiteEnvironment.getServerAddress()),
                MANAGEMENT_NATIVE_PORT, new org.wildfly.core.testrunner.Authentication.CallbackHandler());

        resetHttpInterfaceConfiguration(client);

        // reload to apply changes
        reloadServer();//reload using CLI

        serverResourcesSetup.tearDown(containerController.getClient());

        containerController.stop();
        FileUtils.deleteDirectory(WORK_DIR);
    }

    static void resetHttpInterfaceConfiguration(ModelControllerClient client) throws Exception {

        // change back security realm for http management interface
        ModelNode operation = createOpNode("core-service=management/management-interface=http-interface",
                ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("http-authentication-factory");
        operation.get(VALUE).set("management-http-authentication");
        CoreUtils.applyUpdate(operation, client);

        // Restore the http-upgrade setting
        ModelNode httpUpgrade = new ModelNode();
        httpUpgrade.get(ENABLED).set(true);
        httpUpgrade.get("sasl-authentication-factory").set("management-sasl-authentication");
        operation.get(NAME).set("http-upgrade");
        operation.get(VALUE).set(httpUpgrade);
        CoreUtils.applyUpdate(operation, client);

        // undefine secure socket binding from http interface
        operation = createOpNode("core-service=management/management-interface=http-interface",
                ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("secure-socket-binding");
        CoreUtils.applyUpdate(operation, client);

        // remove the ssl-context
        operation.get(NAME).set("ssl-context");
        CoreUtils.applyUpdate(operation, client);
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

            // create native interface to control server while http interface is secured

            final ModelControllerClient client = managementClient.getControllerClient();

            // add native socket binding
            ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native",ModelDescriptionConstants.ADD);
            operation.get("port").set(MANAGEMENT_NATIVE_PORT);
            operation.get("interface").set("management");
            CoreUtils.applyUpdate(operation, client);

            // Find the sasl-authentication-factory that's already known to be working so it can be reused
            ModelNode op = createOpNode("core-service=management/"
                    + "management-interface=http-interface/", "read-attribute");
            op.get("name").set("http-upgrade");
            ModelNode result = managementClient.executeForResult(op);
            String nativeSecurityValue = result.get("sasl-authentication-factory").asStringOrNull();
            assertNotNull("Invalid http-upgrade setting: " + result, nativeSecurityValue);

            operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);
            operation.get("sasl-authentication-factory").set(nativeSecurityValue);
            operation.get("socket-binding").set("management-native");
            CoreUtils.applyUpdate(operation, client);

            // secure http interface

            setupElytronSSL(managementClient);

            operation = createOpNode("core-service=management/management-interface=http-interface",
                    ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set("ssl-context");
            operation.get(VALUE).set(ELYTRON_SSC);
            CoreUtils.applyUpdate(operation, client);

            operation = createOpNode("core-service=management/management-interface=http-interface",
                    ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            operation.get(NAME).set("secure-socket-binding");
            operation.get(VALUE).set("management-https");
            CoreUtils.applyUpdate(operation, client);

            // reuse the existing address and op name, but different params
            operation.get(NAME).set("http-upgrade");
            ModelNode httpUpgrade = new ModelNode();
            httpUpgrade.get(ENABLED).set(true);
            httpUpgrade.get("sasl-authentication-factory").set("client-cert");
            operation.get(VALUE).set(httpUpgrade);
            CoreUtils.applyUpdate(operation, client);


            operation.get(NAME).set("http-authentication-factory");
            operation.get(VALUE).set("client-cert");
            CoreUtils.applyUpdate(operation, client);
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            final ModelControllerClient client = managementClient.getControllerClient();

            ModelNode operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.REMOVE);
            CoreUtils.applyUpdate(operation, client);

            operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.REMOVE);
            CoreUtils.applyUpdate(operation, client);

            removeElytronSsl(managementClient);
        }

        private static void setupElytronSSL(ManagementClient mgmtClient) throws Exception {
            ModelNode operation = createOpNode("subsystem=elytron/key-store=elytronHttpsKS", "add");
            operation.get("path").set(SERVER_KEYSTORE_FILE.getAbsolutePath());
            operation.get("credential-reference", "clear-text").set(SecurityTestConstants.KEYSTORE_PASSWORD);
            operation.get("type").set("JKS");
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/key-manager=elytronHttpsKM", "add");
            operation.get("key-store").set("elytronHttpsKS");
            operation.get("credential-reference", "clear-text").set(SecurityTestConstants.KEYSTORE_PASSWORD);
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/key-store=elytronHttpsTS", "add");
            operation.get("path").set(SERVER_TRUSTSTORE_FILE.getAbsolutePath());
            operation.get("credential-reference", "clear-text").set(SecurityTestConstants.KEYSTORE_PASSWORD);
            operation.get("type").set("JKS");
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/trust-manager=elytronHttpsTM", "add");
            operation.get("key-store").set("elytronHttpsTS");
            //operation.get("credential-reference", "clear-text").set(SecurityTestConstants.KEYSTORE_PASSWORD);
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/key-store-realm=client-cert-realm", "add");
            operation.get("key-store").set("elytronHttpsTS");
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/security-domain=client-cert-domain", "add");
            operation.get("realms").add("realm","client-cert-realm");
            operation.get("default-realm").set("client-cert-realm");
            operation.get("permission-mapper").set("default-permission-mapper");
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/server-ssl-context=elytronHttpsSSC", "add");
            operation.get("key-manager").set("elytronHttpsKM");
            operation.get("trust-manager").set("elytronHttpsTM");
            operation.get("protocols").add("TLSv1.2");
            operation.get("security-domain").set("client-cert-domain");
            operation.get("need-client-auth").set(true);
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/constant-realm-mapper=client-cert-mapper", "add");
            operation.get("realm-name").set("client-cert-realm");
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/http-authentication-factory=client-cert", "add");
            operation.get("http-server-mechanism-factory").set("global");
            operation.get("security-domain").set("client-cert-domain");
            ModelNode mechConfig = operation.get("mechanism-configurations").add();
            mechConfig.get("mechanism-name").set("CLIENT_CERT");
            mechConfig.get("realm-mapper").set("client-cert-mapper");
            mgmtClient.executeForResult(operation);

            operation = createOpNode("subsystem=elytron/sasl-authentication-factory=client-cert", "add");
            operation.get("security-domain").set("client-cert-domain");
            operation.get("sasl-server-factory").set("configured");
            ModelNode mechConfigs = operation.get("mechanism-configurations");
            mechConfig = mechConfigs.add();
            mechConfig.get("mechanism-name").set("EXTERNAL");
            mechConfig.get("realm-mapper").set("client-cert-mapper");
            mgmtClient.executeForResult(operation);
        }

        private static void removeElytronSsl(ManagementClient client) {
            remove(client, "subsystem=elytron/sasl-authentication-factory=client-cert");
            remove(client, "subsystem=elytron/http-authentication-factory=client-cert");
            remove(client, "subsystem=elytron/constant-realm-mapper=client-cert-mapper");
            remove(client, "subsystem=elytron/server-ssl-context=elytronHttpsSSC");
            remove(client, "subsystem=elytron/security-domain=client-cert-domain");
            remove(client, "subsystem=elytron/security-realm=client-cert-realm");
            remove(client, "subsystem=elytron/key-manager=elytronHttpsTM");
            remove(client, "subsystem=elytron/key-store=elytronHttpsTS");
            remove(client, "subsystem=elytron/key-manager=elytronHttpsKM");
            remove(client, "subsystem=elytron/key-store=elytronHttpsKS");
        }

        private static void remove(ManagementClient client, String addr) {
            try {
                ModelNode remove = createOpNode(addr, "remove");
                client.executeForResult(remove);
            } catch (UnsuccessfulOperationException uoe) {
                // It's ok if the resource doesn't exist due to failure in the test to create it
                try {
                    client.executeForResult(createOpNode(addr, "read-resource"));
                    // success means it wasn't a missing resource
                    throw uoe;
                } catch (UnsuccessfulOperationException ignored) {
                    // assume it's due to no such resource
                }
            }
        }
    }
}
