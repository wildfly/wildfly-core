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
package org.wildfly.core.test.standalone.mgmt;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_HTTPS_PORT;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_HTTP_PORT;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_NATIVE_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.jboss.as.test.integration.security.common.CoreUtils.makeCallWithHttpClient;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.wildfly.core.test.standalone.mgmt.HTTPSConnectionWithCLITestCase.reloadServer;

import java.io.File;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.inject.Inject;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.security.common.AbstractBaseSecurityRealmsServerSetupTask;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SSLTruststoreUtil;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.integration.security.common.config.realm.Authentication;
import org.jboss.as.test.integration.security.common.config.realm.CredentialReference;
import org.jboss.as.test.integration.security.common.config.realm.RealmKeystore;
import org.jboss.as.test.integration.security.common.config.realm.SecurityRealm;
import org.jboss.as.test.integration.security.common.config.realm.ServerIdentity;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Testing https connection to HTTP Management interface with configured two-way SSL.
 * HTTP client has set client keystore with valid/invalid certificate, which is used for
 * authentication to management interface. Result of authentication depends on whether client
 * certificate is accepted in server truststore. HTTP client uses client truststore with accepted
 * server certificate to authenticate server identity.
 * <p/>
 * Keystores and truststores have valid certificates until 25 Octover 2033.
 *
 * @author Filip Bogyai
 * @author Josef Cacek
 */

@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
@Category(CommonCriteria.class)
public class HTTPSManagementInterfaceTestCase {

    public static Logger LOGGER = Logger.getLogger(HTTPSManagementInterfaceTestCase.class);

    private static final File WORK_DIR = new File("mgmt-if-workdir");
    public static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    public static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    public static final File CLIENT_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_KEYSTORE);
    public static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);
    public static final File UNTRUSTED_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.UNTRUSTED_KEYSTORE);

    private static final ManagementWebRealmSetup managementNativeRealmSetup = new ManagementWebRealmSetup();
    private static final String MANAGEMENT_WEB_REALM = "ManagementWebRealm";
    private static final String MANAGEMENT_WEB_REALM_CR = "ManagementWebRealmCr";
    private static final String MGMT_CTX = "/management";

    @Inject
    protected static ServerController controller;


    @BeforeClass
    public static void startAndSetupContainer() throws Exception {
        controller.startInAdminMode();

        try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()){
            ManagementClient managementClient = controller.getClient();

            serverSetup(managementClient);
            managementNativeRealmSetup.setup(client);
        }
        // To apply new security realm settings for http interface reload of
        // server is required
        reloadServer();
    }

    /**
     * @throws org.apache.http.client.ClientProtocolException, IOException, URISyntaxException
     * @test.tsfi tsfi.port.management.http
     * @test.tsfi tsfi.app.web.admin.console
     * @test.tsfi tsfi.keystore.file
     * @test.tsfi tsfi.truststore.file
     * @test.objective Testing authentication over management-http port. Test with user who has right/wrong certificate
     * to login into management web interface. Also provides check for web administration console
     * authentication, which goes through /management context.
     * @test.expectedResult Management web console page is successfully reached, and test finishes without exception.
     */
    @Test
    public void testHTTP() throws Exception, URISyntaxException {
        changeSecurityRealmForHttpMngmntInterface(MANAGEMENT_WEB_REALM);

        HttpClient httpClient = HttpClients.createDefault();
        URL mgmtURL = new URL("http", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_HTTP_PORT, MGMT_CTX);

        try {
            String responseBody = makeCallWithHttpClient(mgmtURL, httpClient, 401);
            assertThat("Management index page was reached", responseBody, not(containsString("management-major-version")));
            fail("Untrusted client should not be authenticated.");
        } catch (SSLHandshakeException e) {
            // OK
        }

        final HttpClient trustedHttpClient = getHttpClient(CLIENT_KEYSTORE_FILE);
        String responseBody = makeCallWithHttpClient(mgmtURL, trustedHttpClient, 200);
        assertTrue("Management index page was not reached", responseBody.contains("management-major-version"));
    }

    /**
     * @throws org.apache.http.client.ClientProtocolException, IOException, URISyntaxException
     * @test.tsfi tsfi.port.management.https
     * @test.tsfi tsfi.app.web.admin.console
     * @test.tsfi tsfi.keystore.file
     * @test.tsfi tsfi.truststore.file
     * @test.objective Testing authentication over management-https port. Test with user who has right/wrong certificate
     * to login into management web interface. Also provides check for web administration console
     * authentication, which goes through /management context.
     * @test.expectedResult Management web console page is successfully reached, and test finishes without exception.
     */
    @Test
    public void testHTTPS() throws Exception {
        changeSecurityRealmForHttpMngmntInterface(MANAGEMENT_WEB_REALM);

        final HttpClient httpClient = getHttpClient(CLIENT_KEYSTORE_FILE);
        final HttpClient httpClientUntrusted = getHttpClient(UNTRUSTED_KEYSTORE_FILE);

        URL mgmtURL = new URL("https", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_HTTPS_PORT, MGMT_CTX);
        try {
            String responseBody = makeCallWithHttpClient(mgmtURL, httpClientUntrusted, 401);
            assertThat("Management index page was reached", responseBody, not(containsString("management-major-version")));
        } catch (SSLHandshakeException | SSLPeerUnverifiedException | SocketException e) {
            //depending on the OS and the version of HTTP client in use any one of these exceptions may be thrown
            //in particular the SocketException gets thrown on Windows
            // OK
        } catch (SSLException e) {
            if (!(e.getCause() instanceof SocketException)) {
                throw e;
            }
        }

        String responseBody = makeCallWithHttpClient(mgmtURL, httpClient, 200);
        assertTrue("Management index page was not reached", responseBody.contains("management-major-version"));

    }

    /**
     * @throws org.apache.http.client.ClientProtocolException, IOException, URISyntaxException
     * @test.tsfi tsfi.port.management.https
     * @test.tsfi tsfi.app.web.admin.console
     * @test.tsfi tsfi.keystore.file
     * @test.tsfi tsfi.truststore.file
     * @test.objective Testing authentication over management-https port configured with credential reference.
     * Test with user who has right/wrong certificate to login into management web interface. Also provides check for
     * web administration console authentication, which goes through /management context.
     * @test.expectedResult Management web console page is successfully reached, and test finishes without exception.
     */
    @Test
    public void testHTTPSWithCredentialReference() throws Exception {
        changeSecurityRealmForHttpMngmntInterface(MANAGEMENT_WEB_REALM_CR);

        final HttpClient httpClient = getHttpClient(CLIENT_KEYSTORE_FILE);
        final HttpClient httpClientUntrusted = getHttpClient(UNTRUSTED_KEYSTORE_FILE);

        URL mgmtURL = new URL("https", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_HTTPS_PORT, MGMT_CTX);
        try {
            String responseBody = makeCallWithHttpClient(mgmtURL, httpClientUntrusted, 401);
            assertThat("Management index page was reached", responseBody, not(containsString("management-major-version")));
        } catch (SSLHandshakeException | SSLPeerUnverifiedException | SocketException e) {
            //depending on the OS and the version of HTTP client in use any one of these exceptions may be thrown
            //in particular the SocketException gets thrown on Windows
            // OK
        } catch (SSLException e) {
            if (!(e.getCause() instanceof SocketException)) {
                throw e;
            }
        }

        String responseBody = makeCallWithHttpClient(mgmtURL, httpClient, 200);
        assertTrue("Management index page was not reached", responseBody.contains("management-major-version"));

    }

    @AfterClass
    public static void stopContainer() throws Exception {

        try (ModelControllerClient client = getNativeModelControllerClient()) {
            resetHttpInterfaceConfiguration(client);

            // reload to apply changes
            reloadServer();

            serverTearDown(client);
            managementNativeRealmSetup.tearDown(client);
        }

        controller.stop();
    }

    private static HttpClient getHttpClient(File keystoreFile) {
        return SSLTruststoreUtil.getHttpClientWithSSL(keystoreFile, SecurityTestConstants.KEYSTORE_PASSWORD, CLIENT_TRUSTSTORE_FILE, SecurityTestConstants.KEYSTORE_PASSWORD);
    }

    static class ManagementWebRealmSetup extends AbstractBaseSecurityRealmsServerSetupTask {

        // Overridden just to expose locally
        @Override
        protected void setup(ModelControllerClient modelControllerClient) throws Exception {
            super.setup(modelControllerClient);
        }

        @Override
        protected void tearDown(ModelControllerClient modelControllerClient) throws Exception {
            super.tearDown(modelControllerClient);
        }

        @Override
        protected SecurityRealm[] getSecurityRealms() throws Exception {
            final ServerIdentity serverIdentity = new ServerIdentity.Builder().ssl(
                    new RealmKeystore.Builder().keystorePassword(SecurityTestConstants.KEYSTORE_PASSWORD)
                            .keystorePath(SERVER_KEYSTORE_FILE.getAbsolutePath()).build()).build();
            final Authentication authentication = new Authentication.Builder().truststore(
                    new RealmKeystore.Builder().keystorePassword(SecurityTestConstants.KEYSTORE_PASSWORD)
                            .keystorePath(SERVER_TRUSTSTORE_FILE.getAbsolutePath()).build()).build();
            final SecurityRealm realm = new SecurityRealm.Builder().name(MANAGEMENT_WEB_REALM).serverIdentity(serverIdentity)
                    .authentication(authentication).build();
            // Same using credential reference
            final CredentialReference credentialReference = new CredentialReference.Builder().clearText(SecurityTestConstants.KEYSTORE_PASSWORD).build();
            final ServerIdentity serverIdentityCR = new ServerIdentity.Builder().ssl(
                    new RealmKeystore.Builder()
                            .keystorePasswordCredentialReference(credentialReference)
                            .keyPasswordCredentialReference(credentialReference)
                            .keystorePath(SERVER_KEYSTORE_FILE.getAbsolutePath()).build()).build();
            final Authentication authenticationCR = new Authentication.Builder().truststore(
                    new RealmKeystore.Builder().keystorePasswordCredentialReference(credentialReference)
                            .keystorePath(SERVER_TRUSTSTORE_FILE.getAbsolutePath()).build()).build();
            final SecurityRealm realmCR = new SecurityRealm.Builder().name(MANAGEMENT_WEB_REALM_CR).serverIdentity(serverIdentityCR)
                    .authentication(authenticationCR).build();

            return new SecurityRealm[]{realm, realmCR};
        }
    }

    private static void serverSetup(ManagementClient managementClient) throws Exception {

        // create key and trust stores with imported certificates from opposing sides
        FileUtils.deleteDirectory(WORK_DIR);
        WORK_DIR.mkdirs();
        CoreUtils.createKeyMaterial(WORK_DIR);

        final ModelControllerClient client = managementClient.getControllerClient();

        // change security-realm for http management interface
        ModelNode operation = createOpNode("core-service=management/management-interface=http-interface",
                ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("security-realm");
        operation.get(VALUE).set(MANAGEMENT_WEB_REALM);
        CoreUtils.applyUpdate(operation, client);

        // add https connector to management interface
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

    /**
     * Change security realm and reload server if different security realm is requested.
     *
     * In case desired realm is already set do nothing.
     *
     * @param securityRealmName
     * @throws Exception
     */
    private static void changeSecurityRealmForHttpMngmntInterface(String securityRealmName) throws Exception {
        try (ModelControllerClient client = getNativeModelControllerClient()) {

            String originSecurityRealm = "";
            ModelNode operation = createOpNode("core-service=management/management-interface=http-interface", READ_ATTRIBUTE_OPERATION);
            operation.get(NAME).set("security-realm");
            final ModelNode result = client.execute(operation);
            if (SUCCESS.equals(result.get(OUTCOME).asString())) {
                originSecurityRealm = result.get(RESULT).asString();
            }

            if (!originSecurityRealm.equals(securityRealmName)) {
                operation = createOpNode("core-service=management/management-interface=http-interface",
                        ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
                operation.get(NAME).set("security-realm");
                operation.get(VALUE).set(securityRealmName);
                CoreUtils.applyUpdate(operation, client);

                reloadServer();
            }
        }
    }

    private static void serverTearDown(final ModelControllerClient client) throws Exception {

        ModelNode operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.REMOVE);
        CoreUtils.applyUpdate(operation, client);

        operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.REMOVE);
        CoreUtils.applyUpdate(operation, client);

        FileUtils.deleteDirectory(WORK_DIR);
    }

    static void resetHttpInterfaceConfiguration(ModelControllerClient client) throws Exception {

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

    static ModelControllerClient getNativeModelControllerClient() {

        ModelControllerClient client = null;
        try {
            client = ModelControllerClient.Factory.create("remoting", InetAddress.getByName(TestSuiteEnvironment.getServerAddress()),
                    MANAGEMENT_NATIVE_PORT, new org.wildfly.core.testrunner.Authentication.CallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return client;
    }
}
