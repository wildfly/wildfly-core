/*
 * JBoss, Home of Professional Open Source
 * Copyright 2021 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.wildfly.test.integration.elytron.ssl;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.ReflectPermission;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.extension.elytron.ElytronExtension;
import org.wildfly.openssl.OpenSSLProvider;
import org.wildfly.security.ssl.CipherSuiteSelector;
import org.wildfly.security.ssl.ProtocolSelector;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.security.ssl.test.util.CAGenerationTool;
import org.wildfly.security.ssl.test.util.CAGenerationTool.Identity;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.CliPath;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.CredentialReference;
import org.wildfly.test.security.common.elytron.SimpleKeyManager;
import org.wildfly.test.security.common.elytron.SimpleKeyStore;
import org.wildfly.test.security.common.elytron.SimpleServerSslContext;
import org.wildfly.test.security.common.elytron.SimpleTrustManager;
import org.wildfly.test.undertow.UndertowSSLService;
import org.wildfly.test.undertow.UndertowSSLServiceActivator;
import org.wildfly.test.undertow.UndertowServiceActivator;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import io.undertow.protocols.ssl.UndertowXnioSsl;

@RunWith(WildFlyRunner.class)
@org.wildfly.core.testrunner.ServerSetup({ OpenSslTlsTestCase.KeyMaterialSetup.class, OpenSslTlsTestCase.ServerSetup.class })
public class OpenSslTlsTestCase {

    @Inject
    protected ManagementClient managementClient;

    private static final String javaSpecVersion = System.getProperty("java.specification.version");
    private static final String PASSWORD = "Elytron";

    private static final String SERVER_KEY_STORE_NAME = "serverKS";
    private static final String SERVER_TRUST_STORE_NAME = "serverTS";
    private static final String SERVER_KEY_MANAGER_NAME = "serverKM";
    private static final String SERVER_TRUST_MANAGER_NAME = "serverTM";
    private static final String SERVER_SSL_CONTEXT_NAME = "test-context";

    private static final PathAddress ROOT_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);
    private static final PathAddress SERVER_SSL_CONTEXT_ADDRESS = ROOT_ADDRESS.append("server-ssl-context", SERVER_SSL_CONTEXT_NAME);
    private static final Pattern OPENSSL_TLSv13_PATTERN = Pattern.compile("^(TLS_AES_128_GCM_SHA256|TLS_AES_256_GCM_SHA384|TLS_CHACHA20_POLY1305_SHA256|TLS_AES_128_CCM_SHA256|TLS_AES_128_CCM_8_SHA256)$");

    private static CAGenerationTool caGenerationTool;
    private static final File WORK_DIR;
    private static final OpenSSLProvider openSslProvider = new OpenSSLProvider();

    static {
        try {
            WORK_DIR = Files.createTempDirectory("jks-").toFile();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary folder", e);
        }
    }
    private static final File SERVER_KEY_STORE_FILE = new File(WORK_DIR, "scarab.keystore");
    private static final File CLIENT_KEY_STORE_FILE = new File(WORK_DIR, "ladybird.keystore");
    private static final File TRUST_STORE_FILE = new File(WORK_DIR, "ca.truststore");
    private static final String TEST_JAR = "test.jar";

    @BeforeClass
    public static void noJDK14Plus() {
        Assume.assumeFalse("Avoiding JDK 14+ due to https://issues.jboss.org/browse/WFCORE-4532", getJavaSpecVersion() >= 14);
    }

    @BeforeClass
    public static void isOpenSSL111OrHigher() {
        Assume.assumeTrue("OpenSSL version in use does not support TLS 1.3", isOpenSslTls13Enabled());
    }

    private static boolean isOpenSslTls13Enabled() {
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLSv1.3", openSslProvider);
            String[] OPENSSL_AVAILABLE_CIPHERSUITES = sslContext.getSupportedSSLParameters().getCipherSuites();
            for (String cipherSuite : OPENSSL_AVAILABLE_CIPHERSUITES) {
                if (OPENSSL_TLSv13_PATTERN.matcher(cipherSuite).matches()) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static int getJavaSpecVersion() {
        return Integer.parseInt(javaSpecVersion);
    }

    static class ServerSetup extends TestRunnerConfigSetupTask {

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            if (isOpenSslTls13Enabled()) {
                super.setup(managementClient);

                JavaArchive jar = ShrinkWrap.create(JavaArchive.class, TEST_JAR)
                        .addClasses(UndertowServiceActivator.DEPENDENCIES)
                        .addClasses(UndertowSSLService.class)
                        .addAsResource(new StringAsset("Dependencies: io.undertow.core"), "META-INF/MANIFEST.MF")
                        .addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(UndertowServiceActivator.appendPermissions(new FilePermission("<<ALL FILES>>", "read"),
                                new RuntimePermission("getClassLoader"),
                                new RuntimePermission("accessDeclaredMembers"),
                                new RuntimePermission("accessClassInPackage.sun.security.ssl"),
                                new ReflectPermission("suppressAccessChecks"))), "permissions.xml")
                        .addAsServiceProviderAndClasses(ServiceActivator.class, UndertowSSLServiceActivator.class);
                deploy(jar, managementClient);
                ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
            }
        }

        @Override
        protected ConfigurableElement[] getConfigurableElements() {
            List<ConfigurableElement> elements = new ArrayList<>();

            final CredentialReference credentialReference = CredentialReference.builder().withClearText(PASSWORD)
                    .build();

            // KeyStores
            final SimpleKeyStore.Builder ksCommon = SimpleKeyStore.builder()
                    .withType("JKS")
                    .withCredentialReference(credentialReference);
            elements.add(ksCommon.withName(SERVER_KEY_STORE_NAME)
                    .withPath(CliPath.builder()
                            .withPath(SERVER_KEY_STORE_FILE.getAbsolutePath())
                            .build())
                    .build());
            elements.add(ksCommon.withName(SERVER_TRUST_STORE_NAME)
                    .withPath(CliPath.builder()
                            .withPath(TRUST_STORE_FILE.getAbsolutePath())
                            .build())
                    .build());

            // Key and Trust Managers
            elements.add(SimpleKeyManager.builder()
                    .withName(SERVER_KEY_MANAGER_NAME)
                    .withCredentialReference(credentialReference)
                    .withKeyStore(SERVER_KEY_STORE_NAME)
                    .build());
            elements.add(
                    SimpleTrustManager.builder()
                            .withName(SERVER_TRUST_MANAGER_NAME)
                            .withKeyStore(SERVER_TRUST_STORE_NAME)
                            .build());

            // SSLContext with OpenSSL provider
            elements.add(SimpleServerSslContext.builder()
                    .withName(SERVER_SSL_CONTEXT_NAME)
                    .withKeyManagers(SERVER_KEY_MANAGER_NAME)
                    .withTrustManagers(SERVER_TRUST_MANAGER_NAME)
                    .withProviders("openssl")
                    .withProtocols("TLSv1.3", "TLSv1.2", "TLSv1.1")
                    .withCipherSuiteNames("TLS_AES_256_GCM_SHA384:TLS_AES_128_CCM_8_SHA256")
                    .build());

            return elements.toArray(new ConfigurableElement[elements.size()]);
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {
            undeploy(managementClient, TEST_JAR);
            super.tearDown(managementClient);
        }

        /**
         * Deploys the archive to the running server.
         *
         * @param archive the archive to deploy
         * @throws IOException if an error occurs deploying the archive
         */
        private static void deploy(final Archive<?> archive, ManagementClient managementClient) throws IOException {
            // Use an operation to allow overriding the runtime name
            final ModelNode address = Operations.createAddress(DEPLOYMENT, archive.getName());
            final ModelNode addOp = createAddOperation(address);
            addOp.get("enabled").set(true);
            // Create the content for the add operation
            final ModelNode contentNode = addOp.get(CONTENT);
            final ModelNode contentItem = contentNode.get(0);
            contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(0);

            // Create an operation and add the input archive
            final OperationBuilder builder = OperationBuilder.create(addOp);
            builder.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());

            // Deploy the content and check the results
            final ModelNode result = managementClient.getControllerClient().execute(builder.build());
            if (!Operations.isSuccessfulOutcome(result)) {
                Assert.fail(String.format("Failed to deploy %s: %s", archive, Operations.getFailureDescription(result).asString()));
            }
        }

        private static void undeploy(ManagementClient client, final String runtimeName) throws ServerDeploymentHelper.ServerDeploymentException {
            final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
            final Collection<Throwable> errors = new ArrayList<>();
            try {
                final ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("deployment", runtimeName));
                final ModelNode result = client.getControllerClient().execute(op);
                if (Operations.isSuccessfulOutcome(result))
                    helper.undeploy(runtimeName);
            } catch (Exception e) {
                errors.add(e);
            }
            if (!errors.isEmpty()) {
                final RuntimeException e = new RuntimeException("Error undeploying: " + runtimeName);
                for (Throwable error : errors) {
                    e.addSuppressed(error);
                }
                throw e;
            }
        }
    }

    public static class KeyMaterialSetup implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            if (WORK_DIR.exists()) {
                FileUtils.deleteQuietly(WORK_DIR);
            }
            caGenerationTool = CAGenerationTool.builder()
                    .setBaseDir(WORK_DIR.getAbsolutePath())
                    .setRequestIdentities(Identity.LADYBIRD, Identity.SCARAB)
                    .build();
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            FileUtils.deleteQuietly(WORK_DIR);
        }

    }

    @Test
    public void testTLS13OpenSsl() throws Throwable {
        configureServerCipherSuitesAndProtocols("TLS_AES_256_GCM_SHA384:TLS_AES_128_CCM_8_SHA256", "TLSv1.3", "TLSv1.2", "TLSv1.1");
        UndertowXnioSsl ssl = createClientSSL("TLS_AES_256_GCM_SHA384:TLS_AES_128_CCM_8_SHA256", "TLSv1.3", "TLSv1.2", "TLSv1.1");
        performSimpleTest(ssl, "TLS_AES_256_GCM_SHA384", "TLSv1.3");
    }

    @Test
    public void testNoExplicitTLS13CipherSuitesOpenSsl() throws Throwable {
        // WFCORE-5122: Update this test once we are ready to enable TLS 1.3 by default for the WildFly OpenSSL provider
        // For now, TLS 1.2 should be used by default in this test case since no TLS 1.3 cipher suites have been explicitly
        // configured on the server side
        configureServerCipherSuitesAndProtocols(null, "TLSv1.3", "TLSv1.2", "TLSv1.1");
        UndertowXnioSsl ssl = createClientSSL("TLS_AES_256_GCM_SHA384:TLS_AES_128_CCM_8_SHA256", "TLSv1.3", "TLSv1.2", "TLSv1.1");
        performSimpleTest(ssl, null, "TLSv1.2");
    }

    @Test(expected = SSLException.class)
    public void testProtocolMismatchOpenSsl() throws Throwable {
        configureServerCipherSuitesAndProtocols(null, "TLSv1.2");
        UndertowXnioSsl ssl = createClientSSL("TLS_AES_256_GCM_SHA384:TLS_AES_128_CCM_8_SHA256", "TLSv1.3");
        performSimpleTest(ssl, null, "");
    }

    @Test(expected = SSLException.class)
    public void testCipherSuiteMismatchOpenSsl() throws Throwable {
        configureServerCipherSuitesAndProtocols("TLS_AES_128_CCM_8_SHA256:TLS_AES_256_GCM_SHA384", "TLSv1.3");
        UndertowXnioSsl ssl = createClientSSL("TLS_AES_128_GCM_SHA256", "TLSv1.3");
        performSimpleTest(ssl, null, "");
    }

    private UndertowXnioSsl createClientSSL(String cipherSuiteNames, String... protocols) throws Exception {
        SSLContextBuilder clientContextBuilder = new SSLContextBuilder();
        clientContextBuilder.setProviderSupplier(() -> new Provider[] {new OpenSSLProvider()});
        if (cipherSuiteNames != null) {
            clientContextBuilder.setCipherSuiteSelector(CipherSuiteSelector.fromNamesString(cipherSuiteNames));
        }
        if (protocols != null) {
            ProtocolSelector protocolSelector = ProtocolSelector.empty();
            for (String protocol : protocols) {
                protocolSelector = protocolSelector.add(protocol);
            }
            clientContextBuilder.setProtocolSelector(protocolSelector);
        }
        clientContextBuilder.setKeyManager(getKeyManager(CLIENT_KEY_STORE_FILE));
        clientContextBuilder.setTrustManager(getTrustManager(TRUST_STORE_FILE));
        clientContextBuilder.setClientMode(true);
        SSLContext clientContext = clientContextBuilder.build().create();
        return new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, clientContext);
    }

    private static X509ExtendedKeyManager getKeyManager(final File ksFile) throws Exception {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(loadKeyStore(ksFile), PASSWORD.toCharArray());
        for (KeyManager current : keyManagerFactory.getKeyManagers()) {
            if (current instanceof X509ExtendedKeyManager) {
                return (X509ExtendedKeyManager) current;
            }
        }
        throw new IllegalStateException("Unable to obtain X509ExtendedKeyManager.");
    }

    private static X509TrustManager getTrustManager(File trustStoreFile) throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(loadKeyStore(trustStoreFile));
        for (TrustManager current : trustManagerFactory.getTrustManagers()) {
            if (current instanceof X509TrustManager) {
                return (X509TrustManager) current;
            }
        }
        throw new IllegalStateException("Unable to obtain X509TrustManager.");
    }

    private static KeyStore loadKeyStore(final File ksFile) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, PASSWORD.toCharArray());
        }
        return ks;
    }

    private void performSimpleTest(UndertowXnioSsl ssl, String expectedCipherSuite, String expectedProtocol) throws Exception {
        SSLSocket clientSocket = ((SSLSocket)(ssl.getSslContext().getSocketFactory().createSocket("localhost", TestSuiteEnvironment.getHttpPort())));
        clientSocket.getOutputStream().write(new byte[]{0x12, 0x34});
        SSLSession clientSession = clientSocket.getSession();
        if (expectedCipherSuite != null) {
            Assert.assertEquals(expectedCipherSuite, clientSession.getCipherSuite());
        }
        Assert.assertEquals(expectedProtocol, clientSession.getProtocol());
        Assert.assertEquals(Identity.SCARAB.getPrincipal().getName(), clientSession.getPeerPrincipal().getName());
    }

    private void configureServerCipherSuitesAndProtocols(String cipherSuiteNames, String... protocols) throws Exception {
        ModelNode writeOp = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, SERVER_SSL_CONTEXT_ADDRESS);
        if (cipherSuiteNames != null) {
            writeOp.get("name").set("cipher-suite-names");
            writeOp.get("value").set(cipherSuiteNames);
            managementClient.executeForResult(writeOp);
        } else {
            ModelNode undefineOp = Util.createEmptyOperation(UNDEFINE_ATTRIBUTE_OPERATION, SERVER_SSL_CONTEXT_ADDRESS);
            undefineOp.get("name").set("cipher-suite-names");
            managementClient.executeForResult(undefineOp);
        }
        writeOp.get("name").set("protocols");
        ModelNode protocolsList = new ModelNode();
        for (String protocol : protocols) {
            protocolsList.add(protocol);
        }
        writeOp.get("value").set(protocolsList);
        managementClient.executeForResult(writeOp);
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
    }
}
