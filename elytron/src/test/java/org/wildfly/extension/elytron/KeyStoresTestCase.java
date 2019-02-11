/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.integration.ClientAndServer;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.cert.BasicConstraintsExtension;
import org.wildfly.security.x500.cert.KeyUsage;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.security.x500.cert.X509CertificateBuilder;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.AcmeException;

import mockit.Mock;
import mockit.MockUp;
import mockit.integration.junit4.JMockit;


/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
@RunWith(JMockit.class)
public class KeyStoresTestCase extends AbstractSubsystemTest {

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility csUtil = null;
    private static final String CS_PASSWORD = "super_secret";
    private static final String KEYSTORE_NAME = "ModifiedKeystore";
    private static final String KEY_PASSWORD = "secret";
    private static final String CERTIFICATE_AUTHORITY_ACCOUNT_NAME = "CertAuthorityAccount";
    private static final String ACCOUNTS_KEYSTORE = "account.keystore";
    private static final String ACCOUNTS_KEYSTORE_NAME = "AccountsKeyStore";
    private static final String ACCOUNTS_KEYSTORE_PASSWORD = "elytron";
    private static String oldHomeDir;
    private static ClientAndServer server; // used to simulate a Let's Encrypt server instance
    private static final String WORKING_DIRECTORY_LOCATION = "./target/test-classes/org/wildfly/extension/elytron";
    private static final char[] KEYSTORE_PASSWORD = "Elytron".toCharArray();
    private static final X500Principal ROOT_DN = new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA");
    private static final X500Principal ANOTHER_ROOT_DN = new X500Principal("O=Another Root Certificate Authority, EMAILADDRESS=anotherca@wildfly.org, C=UK, ST=Elytron, CN=Another Elytron CA");
    private static final X500Principal SALLY_DN = new X500Principal("CN=sally smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
    private static final X500Principal SSMITH_DN = new X500Principal("CN=ssmith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
    private static final X500Principal FIREFLY_DN = new X500Principal("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly");
    private static final X500Principal INTERMEDIATE_DN = new X500Principal("O=Intermediate Certificate Authority, EMAILADDRESS=intermediateca@wildfly.org, C=UK, ST=Elytron, CN=Intermediate Elytron CA");
    private static final File FIREFLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "firefly.keystore");
    private static final File TEST_FILE = new File(WORKING_DIRECTORY_LOCATION, "test.keystore");
    private static final File TEST_SINGLE_CERT_REPLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-single-cert-reply.cert");
    private static final File TEST_CERT_CHAIN_REPLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-cert-chain-reply.cert");
    private static final File TEST_EXPORTED_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-exported.cert");
    private static final File TEST_TRUSTED_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-trusted.cert");
    private static final File TEST_UNTRUSTED_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-untrusted.cert");
    private static final File TEST_UNTRUSTED_CERT_CHAIN_REPLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-untrusted-cert-chain-reply.cert");

    private static void mockCertificateAuthorityUrl() {
        Class<?> classToMock;
        try {
            classToMock = Class.forName("org.wildfly.security.x500.cert.acme.AcmeAccount", true, CertificateAuthorityAccountDefinition.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(e.getMessage());
        }
        new MockUp<Object>(classToMock) {
            @Mock
            public String getServerUrl(boolean staging){
                return "http://localhost:4001/directory"; // use a simulated Let's Encrypt server instance for these tests
            }
        };
    }

    private static void mockRetryAfter() {
        Class<?> classToMock;
        try {
            classToMock = Class.forName("org.wildfly.security.x500.cert.acme.AcmeClientSpi", true, AcmeAccount.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(e.getMessage());
        }
        new MockUp<Object>(classToMock) {
            @Mock
            private long getRetryAfter(HttpURLConnection connection, boolean useDefaultIfHeaderNotPresent) throws AcmeException {
                return 0;
            }
        };
    }

    private static void createServerEnvironment() {
        File home = new File("target/wildfly");
        home.mkdir();
        File challengeDir = new File(home, ".well-known/acme-challenge");
        challengeDir.mkdirs();
        oldHomeDir = System.setProperty("jboss.home.dir", home.getAbsolutePath());
    }

    public KeyStoresTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private KernelServices services = null;

    private ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private ModelNode assertFailed(ModelNode response) {
        if (! response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    private static void createTemporaryKeyStoreFile(KeyStore keyStore, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            keyStore.store(fos, KEYSTORE_PASSWORD);
        }
    }

    private static void createTemporaryCertFile(X509Certificate certificate, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile, true)){
            fos.write(certificate.getEncoded());
        }
    }

    private static KeyStore createFireflyKeyStore() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair fireflyKeys = keyPairGenerator.generateKeyPair();
        PrivateKey fireflySigningKey = fireflyKeys.getPrivate();
        PublicKey fireflyPublicKey = fireflyKeys.getPublic();

        KeyStore fireflyKeyStore = loadKeyStore();

        SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(ROOT_DN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA1withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
        X509Certificate issuerCertificate = issuerSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        fireflyKeyStore.setCertificateEntry("ca", issuerCertificate);

        X509Certificate fireflyCertificate = new X509CertificateBuilder()
                .setIssuerDn(ROOT_DN)
                .setSubjectDn(FIREFLY_DN)
                .setSignatureAlgorithmName("SHA1withRSA")
                .setSigningKey(issuerSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(fireflyPublicKey)
                .setSerialNumber(new BigInteger("1"))
                .addExtension(new BasicConstraintsExtension(false, false, -1))
                .build();
        fireflyKeyStore.setKeyEntry("firefly", fireflySigningKey, KEY_PASSWORD.toCharArray(), new X509Certificate[]{fireflyCertificate,issuerCertificate});

        return fireflyKeyStore;
    }

    private static SelfSignedX509CertificateAndSigningKey createCertRoot(X500Principal DN) {
        return SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(DN)
                .setKeyAlgorithmName("DSA")
                .setSignatureAlgorithmName("SHA1withDSA")
                .setKeySize(1024)
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
    }

    private static SelfSignedX509CertificateAndSigningKey createSallyCertificate() {
        return SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(SALLY_DN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA256withRSA")
                .setKeySize(1024)
                .addExtension(false, "ExtendedKeyUsage", "clientAuth")
                .addExtension(true, "KeyUsage", "digitalSignature")
                .addExtension(false, "SubjectAlternativeName", "email:sallysmith@example.com,DNS:sallysmith.example.com")
                .build();
    }

    private static X509Certificate createSSmithCertificate(SelfSignedX509CertificateAndSigningKey rootSelfSignedX509CertificateAndSigningKey, PublicKey publicKey) throws Exception {
        return new X509CertificateBuilder()
                .setIssuerDn(ROOT_DN)
                .setSubjectDn(SSMITH_DN)
                .setSignatureAlgorithmName("SHA1withDSA")
                .setSigningKey(rootSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(publicKey)
                .build();
    }

    private static KeyStore createTestKeyStore(SelfSignedX509CertificateAndSigningKey rootSelfSignedX509CertificateAndSigningKey, SelfSignedX509CertificateAndSigningKey sallySelfSignedX509CertificateAndSigningKey) throws Exception {
        KeyStore testKeyStore = loadKeyStore();

        X509Certificate sallyCertificate = sallySelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        testKeyStore.setKeyEntry("ssmith", sallySelfSignedX509CertificateAndSigningKey.getSigningKey(), KEY_PASSWORD.toCharArray(), new X509Certificate[]{sallyCertificate});

        X509Certificate rootCertificate = rootSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        testKeyStore.setKeyEntry("ca", rootSelfSignedX509CertificateAndSigningKey.getSigningKey(), KEY_PASSWORD.toCharArray(), new X509Certificate[]{rootCertificate});

        return testKeyStore;
    }

    private static X509Certificate createTestTrustedCertificate(SelfSignedX509CertificateAndSigningKey rootSelfSignedX509CertificateAndSigningKey) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair keys = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keys.getPublic();

        return new X509CertificateBuilder()
                .setIssuerDn(ROOT_DN)
                .setSubjectDn(INTERMEDIATE_DN)
                .setSignatureAlgorithmName("SHA1withDSA")
                .setSigningKey(rootSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(publicKey)
                .build();
    }

    private static X509Certificate createTestUntrustedCertChainReplyCertificate(SelfSignedX509CertificateAndSigningKey selfSignedX509CertificateAndSigningKey, PublicKey publicKey) throws Exception {
        return new X509CertificateBuilder()
                .setIssuerDn(ANOTHER_ROOT_DN)
                .setSubjectDn(SSMITH_DN)
                .setSignatureAlgorithmName("SHA1withDSA")
                .setSigningKey(selfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(publicKey)
                .build();
    }

    private static void setUpFiles() throws Exception {
        File workingDir = new File(WORKING_DIRECTORY_LOCATION);
        if (workingDir.exists() == false) {
            workingDir.mkdirs();
        }

        SelfSignedX509CertificateAndSigningKey rootSelfSignedX509CertificateAndSigningKey = createCertRoot(ROOT_DN);
        SelfSignedX509CertificateAndSigningKey anotherRootSelfSignedX509CertificateAndSigningKey = createCertRoot(ANOTHER_ROOT_DN);
        SelfSignedX509CertificateAndSigningKey sallySelfSignedX509CertificateAndSigningKey = createSallyCertificate();

        PublicKey publicKey = sallySelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate().getPublicKey();

        // Key Stores
        KeyStore fireflyKeyStore = createFireflyKeyStore();
        KeyStore testKeyStore = createTestKeyStore(rootSelfSignedX509CertificateAndSigningKey, sallySelfSignedX509CertificateAndSigningKey);

        createTemporaryKeyStoreFile(fireflyKeyStore, FIREFLY_FILE);
        createTemporaryKeyStoreFile(testKeyStore, TEST_FILE);

        // Cert Files
        X509Certificate sSmithCertificate = createSSmithCertificate(rootSelfSignedX509CertificateAndSigningKey, publicKey);

        X509Certificate testTrustedCert = createTestTrustedCertificate(rootSelfSignedX509CertificateAndSigningKey);
        X509Certificate testUntrustedCert = anotherRootSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        X509Certificate testUntrustedCertChainReplyCert = createTestUntrustedCertChainReplyCertificate(anotherRootSelfSignedX509CertificateAndSigningKey, publicKey);

        createTemporaryCertFile(sSmithCertificate, TEST_SINGLE_CERT_REPLY_FILE);

        createTemporaryCertFile(rootSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate(), TEST_CERT_CHAIN_REPLY_FILE);
        createTemporaryCertFile(sSmithCertificate, TEST_CERT_CHAIN_REPLY_FILE);

        createTemporaryCertFile(sallySelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate(), TEST_EXPORTED_FILE);
        createTemporaryCertFile(testTrustedCert, TEST_TRUSTED_FILE);
        createTemporaryCertFile(testUntrustedCert, TEST_UNTRUSTED_FILE);

        createTemporaryCertFile(testUntrustedCertChainReplyCert, TEST_UNTRUSTED_CERT_CHAIN_REPLY_FILE);
        createTemporaryCertFile(anotherRootSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate(), TEST_UNTRUSTED_CERT_CHAIN_REPLY_FILE);
    }

    private static void removeTestFiles() {
        File[] testFiles = {
                FIREFLY_FILE,
                TEST_FILE,
                TEST_SINGLE_CERT_REPLY_FILE,
                TEST_CERT_CHAIN_REPLY_FILE,
                TEST_EXPORTED_FILE,
                TEST_TRUSTED_FILE,
                TEST_UNTRUSTED_FILE,
                TEST_UNTRUSTED_CERT_CHAIN_REPLY_FILE
        };
        for (File file : testFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @BeforeClass
    public static void initTests() throws Exception {
        server = new ClientAndServer(4001);
        setUpFiles();
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(wildFlyElytronProvider, 1);
            }
        });
        csUtil = new CredentialStoreUtility("target/tlstest.keystore", CS_PASSWORD);
        csUtil.addEntry("the-key-alias", "Elytron");
        csUtil.addEntry("master-password-alias", "Elytron");
        mockCertificateAuthorityUrl();
        mockRetryAfter(); // no need to sleep in between polling attempts during testing
        createServerEnvironment();
    }

    @AfterClass
    public static void cleanUpTests() {
        if (server != null) {
            server.stop();
        }
        removeTestFiles();
        csUtil.cleanUp();
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Security.removeProvider(wildFlyElytronProvider.getName());

                return null;
            }
        });
        if (oldHomeDir == null) {
            System.clearProperty("jboss.home.dir");
        } else {
            System.setProperty("jboss.home.dir", oldHomeDir);
        }
    }

    @Before
    public void init() throws Exception {
        String subsystemXml = System.getProperty("java.vendor").startsWith("IBM") ? "tls-ibm.xml" : "tls-sun.xml";
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(subsystemXml).build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }
    }

    @Test
    public void testKeystoreService() throws Exception {
        ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName("FireflyKeystore");
        KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
        assertNotNull(keyStore);

        assertTrue(keyStore.containsAlias("firefly"));
        assertTrue(keyStore.isKeyEntry("firefly"));
        X509Certificate cert = (X509Certificate) keyStore.getCertificate("firefly");
        assertEquals("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly", cert.getSubjectDN().getName());
        assertEquals("firefly", keyStore.getCertificateAlias(cert));

        Certificate[] chain = keyStore.getCertificateChain("firefly");
        assertEquals("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly", ((X509Certificate) chain[0]).getSubjectDN().getName());
        assertEquals("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA", ((X509Certificate) chain[1]).getSubjectDN().getName());

        assertTrue(keyStore.containsAlias("ca"));
        assertTrue(keyStore.isCertificateEntry("ca"));
        X509Certificate certCa = (X509Certificate) keyStore.getCertificate("ca");
        assertEquals("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA", certCa.getSubjectDN().getName());
        assertEquals("ca", keyStore.getCertificateAlias(certCa));
    }

    @Test
    public void testKeystoreCli() throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("firefly.keystore"), resources.resolve("firefly-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ModelNode operation = new ModelNode(); // add keystore
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", "ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set("firefly-copy.keystore");
        operation.get(ElytronDescriptionConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(2, nodes.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.REMOVE_ALIAS);
        operation.get(ElytronDescriptionConstants.NAME).set("ca");
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);
        nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(1, nodes.size());

        operation = new ModelNode(); // remove keystore
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testFilteringKeystoreService() throws Exception {
        ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName("FilteringKeyStore");
        KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
        assertNotNull(keyStore);

        assertTrue(keyStore.containsAlias("firefly"));
        assertTrue(keyStore.isKeyEntry("firefly"));
        assertEquals(2, keyStore.getCertificateChain("firefly").length); // has CA in chain
        Certificate cert = keyStore.getCertificate("firefly");
        assertNotNull(cert);
        assertEquals("firefly", keyStore.getCertificateAlias(cert));

        assertFalse(keyStore.containsAlias("ca"));
        assertFalse(keyStore.isCertificateEntry("ca"));
    }

    @Test
    public void testFilteringKeystoreCli() throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.FILTERING_KEY_STORE,"FilteringKeyStore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(1, nodes.size());
        assertEquals("firefly", nodes.get(0).asString());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.FILTERING_KEY_STORE,"FilteringKeyStore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIAS);
        operation.get(ElytronDescriptionConstants.NAME).set("firefly");
        ModelNode firefly = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals("firefly", firefly.get(ElytronDescriptionConstants.NAME).asString());
        assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), firefly.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
        assertTrue(firefly.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).isDefined());
    }

    @Test
    public void testAutomaticKeystoreService() throws Exception {
        ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName("AutomaticKeystore");
        KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
        assertNotNull(keyStore);

        assertTrue(keyStore.containsAlias("firefly"));
        assertTrue(keyStore.isKeyEntry("firefly"));
        X509Certificate cert = (X509Certificate) keyStore.getCertificate("firefly");
        assertEquals("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly", cert.getSubjectDN().getName());
        assertEquals("firefly", keyStore.getCertificateAlias(cert));

        Certificate[] chain = keyStore.getCertificateChain("firefly");
        assertEquals("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly", ((X509Certificate) chain[0]).getSubjectDN().getName());
        assertEquals("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA", ((X509Certificate) chain[1]).getSubjectDN().getName());

        assertTrue(keyStore.containsAlias("ca"));
        assertTrue(keyStore.isCertificateEntry("ca"));
        X509Certificate certCa = (X509Certificate) keyStore.getCertificate("ca");
        assertEquals("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA", certCa.getSubjectDN().getName());
        assertEquals("ca", keyStore.getCertificateAlias(certCa));
    }

    @Test
    public void testAutomaticKeystoreCli() throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron");
        operation.get(ClientConstants.OP).set("read-resource");
        System.out.println(services.executeOperation(operation).get(ClientConstants.RESULT).asString());
        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.KEY_STORE,"AutomaticKeystore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(2, nodes.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.KEY_STORE,"AutomaticKeystore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIAS);
        operation.get(ElytronDescriptionConstants.NAME).set("firefly");
        ModelNode firefly = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals("firefly", firefly.get(ElytronDescriptionConstants.NAME).asString());
        assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), firefly.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
        assertTrue(firefly.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).isDefined());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronDescriptionConstants.KEY_STORE,"AutomaticKeystore");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIAS);
        operation.get(ElytronDescriptionConstants.NAME).set("ca");
        ModelNode ca = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals("ca", ca.get(ElytronDescriptionConstants.NAME).asString());
        assertEquals(KeyStore.TrustedCertificateEntry.class.getSimpleName(), ca.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
    }

    @Test
    public void testGenerateKeyPair() throws Exception {
        addKeyStore();

        try {
            int numAliasesBefore = readAliases().size();

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GENERATE_KEY_PAIR);
            operation.get(ElytronDescriptionConstants.ALIAS).set("bsmith");
            operation.get(ElytronDescriptionConstants.ALGORITHM).set("RSA");
            operation.get(ElytronDescriptionConstants.KEY_SIZE).set(1024);
            operation.get(ElytronDescriptionConstants.VALIDITY).set(365);
            operation.get(ElytronDescriptionConstants.SIGNATURE_ALGORITHM).set("SHA256withRSA");
            operation.get(ElytronDescriptionConstants.DISTINGUISHED_NAME).set("CN=bob smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
            ModelNode extensions = new ModelNode();
            extensions.add(getExtension(false, "ExtendedKeyUsage", "clientAuth"));
            extensions.add(getExtension(true, "KeyUsage", "digitalSignature"));
            extensions.add(getExtension(false, "SubjectAlternativeName", "email:bobsmith@example.com,DNS:bobsmith.example.com"));
            operation.get(ElytronDescriptionConstants.EXTENSIONS).set(extensions);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            assertSuccess(services.executeOperation(operation));
            assertEquals(numAliasesBefore + 1, readAliases().size());

            ModelNode newAlias = readAlias("bsmith");
            assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), newAlias.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
            assertEquals(1, newAlias.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).asList().size());

            ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate("bsmith");
            assertEquals("RSA", certificate.getPublicKey().getAlgorithm());
            assertEquals(1024, ((RSAKey) certificate.getPublicKey()).getModulus().bitLength());
            Date notBefore = certificate.getNotBefore();
            Date notAfter = certificate.getNotAfter();
            assertEquals(365, (notAfter.getTime() - notBefore.getTime()) / (1000 * 60 * 60 * 24));
            assertEquals("SHA256withRSA", certificate.getSigAlgName());
            assertEquals(new X500Principal("CN=bob smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us"), certificate.getSubjectX500Principal());
            assertEquals(new X500Principal("CN=bob smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us"), certificate.getIssuerX500Principal());
            try {
                certificate.verify(certificate.getPublicKey());
            } catch (Exception e) {
                fail("Exception not expected");
            }
            assertEquals(1, certificate.getCriticalExtensionOIDs().size());
            assertEquals(3, certificate.getNonCriticalExtensionOIDs().size());
            assertEquals(Arrays.asList(X500.OID_KP_CLIENT_AUTH), certificate.getExtendedKeyUsage());
            boolean[] keyUsage = certificate.getKeyUsage();
            assertTrue(KeyUsage.digitalSignature.in(keyUsage));
            final Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            assertEquals(2, names.size());
            final Iterator<List<?>> iterator = names.iterator();
            List<?> item = iterator.next();
            assertEquals(2, item.size());
            assertEquals(Integer.valueOf(GeneralName.RFC_822_NAME), item.get(0));
            assertEquals("bobsmith@example.com", item.get(1));
            item = iterator.next();
            assertEquals(2, item.size());
            assertEquals(Integer.valueOf(GeneralName.DNS_NAME), item.get(0));
            assertEquals("bobsmith.example.com", item.get(1));
            assertNotNull(certificate.getExtensionValue(X500.OID_CE_SUBJECT_KEY_IDENTIFIER));

            assertNotNull(keyStore.getKey("bsmith", KEY_PASSWORD.toCharArray()));
        } finally {
            removeKeyStore();
        }
    }

    @Test
    public void testGenerateCertificateSigningRequest() throws Exception {
        String csrFileName = "/generated-csr.csr";
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        File csrFile = new File(resources + csrFileName);
        // Use the original KeyStore since this test depends on the encoding being identical but not the expiration date
        addOriginalKeyStore();

        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GENERATE_CERTIFICATE_SIGNING_REQUEST);
            operation.get(ElytronDescriptionConstants.ALIAS).set("ssmith");
            operation.get(ElytronDescriptionConstants.SIGNATURE_ALGORITHM).set("SHA512withRSA");
            operation.get(ElytronDescriptionConstants.DISTINGUISHED_NAME).set("CN=ssmith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
            ModelNode extensions = new ModelNode();
            extensions.add(getExtension(false, "ExtendedKeyUsage", "clientAuth"));
            extensions.add(getExtension(true, "KeyUsage", "digitalSignature"));
            operation.get(ElytronDescriptionConstants.EXTENSIONS).set(extensions);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            operation.get(ElytronDescriptionConstants.PATH).set(csrFileName);
            assertSuccess(services.executeOperation(operation));

            assertTrue(csrFile.exists());
            byte[] bytes = Files.readAllBytes(csrFile.toPath());
            String expectedCsr = "-----BEGIN CERTIFICATE REQUEST-----" + System.lineSeparator() +
                    "MIICADCCAWkCAQAwazELMAkGA1UEBhMCdXMxFzAVBgNVBAgTDm5vcnRoIGNhcm9s" + System.lineSeparator() +
                    "aW5hMRAwDgYDVQQHEwdyYWxlaWdoMRAwDgYDVQQKEwdyZWQgaGF0MQ4wDAYDVQQL" + System.lineSeparator() +
                    "EwVqYm9zczEPMA0GA1UEAxMGc3NtaXRoMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCB" + System.lineSeparator() +
                    "iQKBgQCLn3sYEbfkVOuFNUAXxNDhNBGecNcWPmqM5J3/eUlfmczSMFqLvSx4X5j8" + System.lineSeparator() +
                    "CGt5wZMI13KyBONS87STVqLeMC2FrnKtDkifPcfUt/ytoK3LMPyNNK35gBHix7ap" + System.lineSeparator() +
                    "Zv0z2+WbscfbLvNb32BabZaq6yIuLEJZvl8WzB8dm4GWaoGoqQIDAQABoFUwUwYJ" + System.lineSeparator() +
                    "KoZIhvcNAQkOMUYwRDATBgNVHSUEDDAKBggrBgEFBQcDAjAOBgNVHQ8BAf8EBAMC" + System.lineSeparator() +
                    "B4AwHQYDVR0OBBYEFHE6nKD0WLjThMTt8rA9ZpKPugdBMA0GCSqGSIb3DQEBDQUA" + System.lineSeparator() +
                    "A4GBAAFyEDq9pXLoEy6hcP2OiaTOIAxaBSxewGaVaF2n2mhGosc1BpNnY2EKASVm" + System.lineSeparator() +
                    "z9hA0m0z/AKy/H2bRNcdHavnHR9nK5xGbzbMm6nrLk+juAT4GIDxkR5S7QfHtKpb" + System.lineSeparator() +
                    "UDNvda3hKmtKFAcDdf5Cb/i8PL5yqdKfUcd4WOTE3TM3xton" + System.lineSeparator() +
                    "-----END CERTIFICATE REQUEST-----" + System.lineSeparator();
            assertEquals(expectedCsr, new String(bytes, StandardCharsets.UTF_8));
        } finally {
            removeKeyStore();
            if (csrFile.exists()) {
                csrFile.delete();
            }
        }
    }

    @Test
    public void testImportSingleCertificateReply() throws Exception {
        String replyFileName = "/test-single-cert-reply.cert";
        testImportCertificateReply(replyFileName);
    }

    @Test
    public void testImportCertificateChainReply() throws Exception {
        String replyFileName = "/test-cert-chain-reply.cert";
        testImportCertificateReply(replyFileName);
    }

    private void testImportCertificateReply(String replyFileName) throws Exception {
        addKeyStore();

        try {
            ModelNode alias = readAlias("ssmith");
            assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), alias.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
            assertEquals(1, alias.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).asList().size());

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set("ssmith");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            operation.get(ElytronDescriptionConstants.PATH).set(replyFileName);
            assertSuccess(services.executeOperation(operation));

            alias = readAlias("ssmith");
            assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), alias.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
            assertEquals(2, alias.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).asList().size());

            ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            Certificate[] chain = keyStore.getCertificateChain("ssmith");
            X509Certificate firstCertificate = (X509Certificate) chain[0];
            X509Certificate secondCertificate = (X509Certificate) chain[1];
            String expectedIssuerDn = "O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA";
            assertEquals(new X500Principal("CN=ssmith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us"), firstCertificate.getSubjectX500Principal());
            assertEquals(new X500Principal(expectedIssuerDn), firstCertificate.getIssuerX500Principal());
            assertEquals(new X500Principal(expectedIssuerDn), secondCertificate.getSubjectX500Principal());
            assertEquals(new X500Principal(expectedIssuerDn), secondCertificate.getIssuerX500Principal());
        } finally {
            removeKeyStore();
        }
    }

    @Test
    public void testImportUntrustedCertificateReplyWithValidation() throws Exception {
        testImportUntrustedCertificateReply(true);
    }

    @Test
    public void testImportUntrustedCertificateReplyWithoutValidation() throws Exception {
        testImportUntrustedCertificateReply(false);
    }

    private void testImportUntrustedCertificateReply(boolean validate) throws Exception {
        // top-most certificate in the reply is not present in the keystore or cacerts file
        String replyFileName = "/test-untrusted-cert-chain-reply.cert";

        addKeyStore();

        try {
            ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            KeyStore.PrivateKeyEntry aliasBefore = (KeyStore.PrivateKeyEntry) keyStore.getEntry("ssmith", new KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray()));
            assertEquals(1, aliasBefore.getCertificateChain().length);

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set("ssmith");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            operation.get(ElytronDescriptionConstants.PATH).set(replyFileName);
            operation.get(ElytronDescriptionConstants.VALIDATE).set(validate);
            operation.get(ElytronDescriptionConstants.TRUST_CACERTS).set(true);

            if (validate) {
                assertFailed(services.executeOperation(operation));
                keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
                assertNotNull(keyStore);
                KeyStore.PrivateKeyEntry aliasAfter = (KeyStore.PrivateKeyEntry) keyStore.getEntry("ssmith", new KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray()));
                assertEquals(aliasBefore.getCertificate(), aliasAfter.getCertificate());
                assertArrayEquals(aliasBefore.getCertificateChain(), aliasAfter.getCertificateChain());
            } else {
                assertSuccess(services.executeOperation(operation));

                ModelNode alias = readAlias("ssmith");
                assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), alias.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
                assertEquals(2, alias.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).asList().size());

                keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
                assertNotNull(keyStore);
                Certificate[] chain = keyStore.getCertificateChain("ssmith");
                X509Certificate firstCertificate = (X509Certificate) chain[0];
                X509Certificate secondCertificate = (X509Certificate) chain[1];
                String expectedIssuerDn = "O=Another Root Certificate Authority, EMAILADDRESS=anotherca@wildfly.org, C=UK, ST=Elytron, CN=Another Elytron CA";
                assertEquals(new X500Principal("CN=ssmith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us"), firstCertificate.getSubjectX500Principal());
                assertEquals(new X500Principal(expectedIssuerDn), firstCertificate.getIssuerX500Principal());
                assertEquals(new X500Principal(expectedIssuerDn), secondCertificate.getSubjectX500Principal());
                assertEquals(new X500Principal(expectedIssuerDn), secondCertificate.getIssuerX500Principal());
            }
        } finally {
            removeKeyStore();
        }
    }

    @Test
    public void testImportTrustedCertificate() throws Exception {
        String replyFileName = "/test-trusted.cert";
        addKeyStore();

        try {
            int numAliasesBefore = readAliases().size();

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set("intermediateCA");
            operation.get(ElytronDescriptionConstants.PATH).set(replyFileName);
            operation.get(ElytronDescriptionConstants.TRUST_CACERTS).set(true);
            assertSuccess(services.executeOperation(operation));
            assertEquals(numAliasesBefore + 1, readAliases().size());

            ModelNode alias = readAlias("intermediateCA");
            assertEquals(KeyStore.TrustedCertificateEntry.class.getSimpleName(), alias.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
            assertTrue(alias.get(ElytronDescriptionConstants.CERTIFICATE).isDefined());

            ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate("intermediateCA");
            assertEquals(new X500Principal("O=Intermediate Certificate Authority, EMAILADDRESS=intermediateca@wildfly.org, C=UK, ST=Elytron, CN=Intermediate Elytron CA"), certificate.getSubjectX500Principal());
            assertEquals(new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA"), certificate.getIssuerX500Principal());
        } finally {
            removeKeyStore();
        }
    }

    @Test
    public void testImportUntrustedCertificateWithValidation() throws Exception {
        testImportUntrustedCertificate(true);
    }

    @Test
    public void testImportUntrustedCertificateWithoutValidation() throws Exception {
        testImportUntrustedCertificate(false);
    }

    private void testImportUntrustedCertificate(boolean validate) throws Exception {
        // issuer certificate is not present in the keystore or cacerts file
        String replyFileName = "/test-untrusted.cert";

        addKeyStore();

        try {
            int numAliasesBefore = readAliases().size();

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set("anotherCA");
            operation.get(ElytronDescriptionConstants.PATH).set(replyFileName);
            operation.get(ElytronDescriptionConstants.VALIDATE).set(validate);
            operation.get(ElytronDescriptionConstants.TRUST_CACERTS).set(true);

            if (validate) {
                assertFailed(services.executeOperation(operation));
                assertEquals(numAliasesBefore, readAliases().size());
            } else {
                assertSuccess(services.executeOperation(operation));
                assertEquals(numAliasesBefore + 1, readAliases().size());

                ModelNode alias = readAlias("anotherCA");
                assertEquals(KeyStore.TrustedCertificateEntry.class.getSimpleName(), alias.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
                assertTrue(alias.get(ElytronDescriptionConstants.CERTIFICATE).isDefined());

                ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
                KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
                assertNotNull(keyStore);
                String expectedDn = "O=Another Root Certificate Authority, EMAILADDRESS=anotherca@wildfly.org, C=UK, ST=Elytron, CN=Another Elytron CA";
                X509Certificate certificate = (X509Certificate) keyStore.getCertificate("anotherCA");
                assertEquals(new X500Principal(expectedDn), certificate.getSubjectX500Principal());
                assertEquals(new X500Principal(expectedDn), certificate.getIssuerX500Principal());
            }
        } finally {
            removeKeyStore();
        }
    }

    @Test
    public void testExportCertificate() throws Exception {
        String expectedCertificateFileName = "/test-exported.cert";
        String certificateFileName = "/exported-cert.cert";
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        File certificateFile = new File(resources + certificateFileName);
        addKeyStore();

        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.EXPORT_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set("ssmith");
            operation.get(ElytronDescriptionConstants.PATH).set(certificateFileName);
            assertSuccess(services.executeOperation(operation));

            assertTrue(certificateFile.exists());
            File expectedCertificateFile = new File(resources + expectedCertificateFileName);
            byte[] expectedBytes = Files.readAllBytes(expectedCertificateFile.toPath());
            byte[] bytes = Files.readAllBytes(certificateFile.toPath());
            assertArrayEquals(expectedBytes, bytes);
        } finally {
            removeKeyStore();
            if (certificateFile.exists()) {
                certificateFile.delete();
            }
        }
    }

    @Test
    public void testExportCertificatePem() throws Exception {
        String certificateFileName = "/exported-cert.cert";
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        File certificateFile = new File(resources + certificateFileName);
        // Use the original KeyStore since this test depends on the encoding being identical but not the expiration date
        addOriginalKeyStore();

        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.EXPORT_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set("ssmith");
            operation.get(ElytronDescriptionConstants.PATH).set(certificateFileName);
            operation.get(ElytronDescriptionConstants.PEM).set(true);
            assertSuccess(services.executeOperation(operation));

            assertTrue(certificateFile.exists());
            byte[] bytes = Files.readAllBytes(certificateFile.toPath());
            String expectedCertificate = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                    "MIIC2zCCAkSgAwIBAgIEEjs64jANBgkqhkiG9w0BAQsFADBwMQswCQYDVQQGEwJ1" + System.lineSeparator() +
                    "czEXMBUGA1UECBMObm9ydGggY2Fyb2xpbmExEDAOBgNVBAcTB3JhbGVpZ2gxEDAO" + System.lineSeparator() +
                    "BgNVBAoTB3JlZCBoYXQxDjAMBgNVBAsTBWpib3NzMRQwEgYDVQQDEwtzYWxseSBz" + System.lineSeparator() +
                    "bWl0aDAeFw0xNzExMTUyMDU1MDlaFw0xODExMTUyMDU1MDlaMHAxCzAJBgNVBAYT" + System.lineSeparator() +
                    "AnVzMRcwFQYDVQQIEw5ub3J0aCBjYXJvbGluYTEQMA4GA1UEBxMHcmFsZWlnaDEQ" + System.lineSeparator() +
                    "MA4GA1UEChMHcmVkIGhhdDEOMAwGA1UECxMFamJvc3MxFDASBgNVBAMTC3NhbGx5" + System.lineSeparator() +
                    "IHNtaXRoMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCLn3sYEbfkVOuFNUAX" + System.lineSeparator() +
                    "xNDhNBGecNcWPmqM5J3/eUlfmczSMFqLvSx4X5j8CGt5wZMI13KyBONS87STVqLe" + System.lineSeparator() +
                    "MC2FrnKtDkifPcfUt/ytoK3LMPyNNK35gBHix7apZv0z2+WbscfbLvNb32BabZaq" + System.lineSeparator() +
                    "6yIuLEJZvl8WzB8dm4GWaoGoqQIDAQABo4GBMH8wEwYDVR0lBAwwCgYIKwYBBQUH" + System.lineSeparator() +
                    "AwIwDgYDVR0PAQH/BAQDAgeAMDkGA1UdEQQyMDCBFnNhbGx5c21pdGhAZXhhbXBs" + System.lineSeparator() +
                    "ZS5jb22CFnNhbGx5c21pdGguZXhhbXBsZS5jb20wHQYDVR0OBBYEFHE6nKD0WLjT" + System.lineSeparator() +
                    "hMTt8rA9ZpKPugdBMA0GCSqGSIb3DQEBCwUAA4GBABdkyo13j11OA9Oqfqaab68N" + System.lineSeparator() +
                    "xKCx4KSLoA1fOQW4F5BlnIZI3RbI4poNhByhba8Er6Q7qgQTLIHRI6G4HiL57v7B" + System.lineSeparator() +
                    "1DOLrs4IFGEGCDWGvaXFIrwItlJ02WTxuuu477oGbh9kdbe29gJxls2yJReNqGCq" + System.lineSeparator() +
                    "PnBUGLBQg9fol/JlZYco" + System.lineSeparator() +
                    "-----END CERTIFICATE-----" + System.lineSeparator();
            assertEquals(expectedCertificate, new String(bytes, StandardCharsets.UTF_8));
        } finally {
            removeKeyStore();
            if (certificateFile.exists()) {
                certificateFile.delete();
            }
        }
    }

    @Test
    public void testChangeAlias() throws Exception {
        addKeyStore();

        try {
            int numAliasesBefore = readAliases().size();

            ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            KeyStore.PrivateKeyEntry aliasBefore = (KeyStore.PrivateKeyEntry) keyStore.getEntry("ssmith", new KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray()));

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CHANGE_ALIAS);
            operation.get(ElytronDescriptionConstants.ALIAS).set("ssmith");
            operation.get(ElytronDescriptionConstants.NEW_ALIAS).set("sallysmith");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            assertSuccess(services.executeOperation(operation));
            assertEquals(numAliasesBefore, readAliases().size());

            keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            assertTrue(! keyStore.containsAlias("ssmith"));
            KeyStore.PrivateKeyEntry aliasAfter = (KeyStore.PrivateKeyEntry) keyStore.getEntry("sallysmith", new KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray()));
            assertEquals(aliasBefore.getCertificate(), aliasAfter.getCertificate());
            assertArrayEquals(aliasBefore.getCertificateChain(), aliasAfter.getCertificateChain());
            assertEquals(aliasBefore.getPrivateKey(), aliasAfter.getPrivateKey());
        } finally {
            removeKeyStore();
        }
    }

    @Test
    public void testStoreFileDoesNotExist() throws Exception {
        String nonExistentFileName = "/does-not-exist.keystore";
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        File file = new File(resources + nonExistentFileName);

        ModelNode operation = getAddKeyStoreUsingNonExistingFileOperation(false, nonExistentFileName);
        assertSuccess(services.executeOperation(operation));
        try {
            operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.STORE);
            assertSuccess(services.executeOperation(operation));
            assertTrue(file.exists());
        } finally {
            removeKeyStore();
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Test
    public void testFileDoesNotExistAndIsRequired() throws Exception {
        String nonExistentFileName = "/does-not-exist.keystore";

        ModelNode operation = getAddKeyStoreUsingNonExistingFileOperation(true, nonExistentFileName);
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testObtainCertificateWithoutAgreeingToTermsOfService() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityAccount("invalid");
        addKeyStore(); // to store the obtained certificate
        server = setupTestCreateAccountWithoutAgreeingToTermsOfService();
        String alias = "server";
        KeyStore keyStore = getKeyStore(KEYSTORE_NAME);
        assertFalse(keyStore.containsAlias(alias));
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.OBTAIN_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
            operation.get(ElytronDescriptionConstants.DOMAIN_NAMES).add("www.example.com");
            operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            assertFailed(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
            removeKeyStore(KEYSTORE_NAME);
        }
    }

    @Test
    public void testObtainCertificateWithKeySize() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityAccount("account6");
        addKeyStore(); // to store the obtained certificate
        server = setupTestObtainCertificateWithKeySize();
        String alias = "server";
        String keyAlgorithmName = "RSA";
        int keySize = 4096;
        KeyStore keyStore = getKeyStore(KEYSTORE_NAME);
        assertFalse(keyStore.containsAlias(alias));
        obtainCertificate(keyAlgorithmName, keySize, "inlneseppwkfwew.com", alias, keyStore);
    }

    @Test
    public void testObtainCertificateWithECPublicKey() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityAccount("account7");
        addKeyStore(); // to store the obtained certificate
        server = setupTestObtainCertificateWithECPublicKey();
        String alias = "server";
        String keyAlgorithmName = "EC";
        int keySize = 256;
        KeyStore keyStore = getKeyStore(KEYSTORE_NAME);
        assertFalse(keyStore.containsAlias(alias));
        obtainCertificate(keyAlgorithmName, keySize, "mndelkdnbcilohg.com", alias, keyStore);
    }

    @Test
    public void testObtainCertificateWithUnsupportedPublicKey() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityAccount("account7");
        addKeyStore(); // to store the obtained certificate
        server = setupTestObtainCertificateWithUnsupportedPublicKey();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.OBTAIN_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set("server");
            operation.get(ElytronDescriptionConstants.DOMAIN_NAMES).add("iraclzlcqgaymrc.com");
            operation.get(ElytronDescriptionConstants.ALGORITHM).set("DSA");
            operation.get(ElytronDescriptionConstants.KEY_SIZE).set(2048);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            assertFailed(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
            removeKeyStore(KEYSTORE_NAME);
        }
    }

    private void obtainCertificate(String keyAlgorithmName, int keySize, String domainName, String alias, KeyStore keyStore) throws Exception {
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.OBTAIN_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
            operation.get(ElytronDescriptionConstants.DOMAIN_NAMES).add(domainName);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            operation.get(ElytronDescriptionConstants.ALGORITHM).set(keyAlgorithmName);
            operation.get(ElytronDescriptionConstants.KEY_SIZE).set(keySize);
            operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            assertSuccess(services.executeOperation(operation));
            assertTrue(keyStore.containsAlias(alias));
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, KEY_PASSWORD.toCharArray());
            X509Certificate signedCert = (X509Certificate) keyStore.getCertificate(alias);
            assertEquals(keyAlgorithmName, privateKey.getAlgorithm());
            assertEquals(keyAlgorithmName, signedCert.getPublicKey().getAlgorithm());
            if (keyAlgorithmName.equals("EC")) {
                assertEquals(keySize, ((ECPublicKey) signedCert.getPublicKey()).getParams().getCurve().getField().getFieldSize());
            } else if (keyAlgorithmName.equals("RSA")) {
                assertEquals(keySize, ((RSAPublicKey) signedCert.getPublicKey()).getModulus().bitLength());
            }
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
            removeKeyStore(KEYSTORE_NAME);
        }
    }

    @Test
    public void testRevokeCertificateWithoutReason() throws Exception {
        revokeCertificate("revokealias", null);
    }

    @Test
    public void testRevokeCertificateWithReason() throws Exception {
        revokeCertificate("revokewithreasonalias", "AACompromise");
    }

    @Test
    public void testShouldRenewCertificateAlreadyExpired() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.of(2018, 03, 24, 23, 59, 59, 0, ZoneOffset.UTC);
        final ZonedDateTime notValidAfterDate = ZonedDateTime.of(2018, 04, 24, 23, 59, 59, 0, ZoneOffset.UTC);
        ModelNode result = shouldRenewCertificate(notValidBeforeDate, notValidAfterDate, 30);
        assertTrue(result.get(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE).asBoolean());
        assertEquals(0, result.get(ElytronDescriptionConstants.DAYS_TO_EXPIRY).asLong());
    }

    @Test
    public void testShouldRenewCertificateExpiresWithinGivenDays() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        final ZonedDateTime notValidAfterDate = notValidBeforeDate.plusDays(60).plusMinutes(1);
        ModelNode result = shouldRenewCertificate(notValidBeforeDate, notValidAfterDate, 90);
        assertTrue(result.get(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE).asBoolean());
        assertEquals(60, result.get(ElytronDescriptionConstants.DAYS_TO_EXPIRY).asLong());
    }

    @Test
    public void testShouldRenewCertificateDoesNotExpireWithinGivenDays() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        final ZonedDateTime notValidAfterDate = notValidBeforeDate.plusDays(30).plusMinutes(1);
        ModelNode result = shouldRenewCertificate(notValidBeforeDate, notValidAfterDate, 15);
        assertFalse(result.get(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE).asBoolean());
        assertEquals(30, result.get(ElytronDescriptionConstants.DAYS_TO_EXPIRY).asLong());
    }

    private ModelNode shouldRenewCertificate(ZonedDateTime notValidBeforeDate, ZonedDateTime notValidAfterDate, int expiration) throws Exception {
        String expiryKeyStoreFileName = "expiry.keystore";
        String alias = "expiry";
        File expiryKeyStoreFile = new File(WORKING_DIRECTORY_LOCATION, expiryKeyStoreFileName);
        KeyStore expiryKeyStore = KeyStore.getInstance("JKS");
        expiryKeyStore.load(null, null);
        addCertificate(expiryKeyStore, alias, notValidBeforeDate, notValidAfterDate);
        try (FileOutputStream fos = new FileOutputStream(expiryKeyStoreFile)){
            expiryKeyStore.store(fos, "Elytron".toCharArray());
        }
        addKeyStore(expiryKeyStoreFileName, KEYSTORE_NAME, "Elytron");

        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
            operation.get(ElytronDescriptionConstants.EXPIRATION).set(expiration);
            return assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        } finally {
            removeKeyStore(KEYSTORE_NAME);
        }
    }

    private void revokeCertificate(String alias, String reason) throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityAccount("account1");
        KeyStore keyStore = getKeyStore(ACCOUNTS_KEYSTORE_NAME);
        if (reason != null) {
            server = setupTestRevokeCertificateWithReason();
        } else {
            server = setupTestRevokeCertificateWithoutReason();
        }
        assertTrue(keyStore.containsAlias(alias));
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", ACCOUNTS_KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.REVOKE_CERTIFICATE);
            operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
            if (reason != null) {
                operation.get(ElytronDescriptionConstants.REASON).set(reason);
            }
            operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            assertSuccess(services.executeOperation(operation));
            assertFalse(keyStore.containsAlias(alias));
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    private void addCertificate(KeyStore keyStore, String alias, ZonedDateTime notValidBefore, ZonedDateTime notValidAfter) throws Exception {
        SelfSignedX509CertificateAndSigningKey issuerCertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(ROOT_DN)
                .build();

        X509Certificate issuerCertificate = issuerCertificateAndSigningKey.getSelfSignedCertificate();

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        X509Certificate signedCertificate = new X509CertificateBuilder()
                .setIssuerDn(ROOT_DN)
                .setSubjectDn(FIREFLY_DN)
                .setPublicKey(publicKey)
                .setSignatureAlgorithmName("SHA1withRSA")
                .setSigningKey(privateKey)
                .setNotValidBefore(notValidBefore)
                .setNotValidAfter(notValidAfter)
                .build();
        keyStore.setKeyEntry(alias, privateKey, KEY_PASSWORD.toCharArray(), new X509Certificate[]{ signedCertificate, issuerCertificate});
    }

    private void addKeyStore() throws Exception {
        addKeyStore("test.keystore", KEYSTORE_NAME, "Elytron");
    }

    private void addKeyStore(String keyStoreFile, String keyStoreName, String keyStorePassword) throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve(keyStoreFile), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set("test-copy.keystore");
        operation.get(ElytronDescriptionConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(keyStorePassword);
        assertSuccess(services.executeOperation(operation));
    }

    private KeyStore getKeyStore(String keyStoreName) {
        ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(keyStoreName);
        return (KeyStore) services.getContainer().getService(serviceName).getValue();
    }

    private void addOriginalKeyStore() throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("test-original.keystore"), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set("test-copy.keystore");
        operation.get(ElytronDescriptionConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        assertSuccess(services.executeOperation(operation));
    }

    private List<ModelNode> readAliases() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);
        return assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
    }

    private ModelNode readAlias(String aliasName) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIAS);
        operation.get(ElytronDescriptionConstants.NAME).set(aliasName);
        ModelNode alias = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals(aliasName, alias.get(ElytronDescriptionConstants.NAME).asString());
        return alias;
    }

    private ModelNode getExtension(boolean critical, String name, String value) {
        ModelNode extension = new ModelNode();
        extension.get(ElytronDescriptionConstants.CRITICAL).set(critical);
        extension.get(ElytronDescriptionConstants.NAME).set(name);
        extension.get(ElytronDescriptionConstants.VALUE).set(value);
        return extension;
    }

    private void removeKeyStore() {
        removeKeyStore(KEYSTORE_NAME);
    }

    private void removeKeyStore(String keyStoreName) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    private ModelNode getAddKeyStoreUsingNonExistingFileOperation(boolean required, String nonExistentFileName) throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        File file = new File(resources + nonExistentFileName);
        assertTrue (! file.exists());

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set(nonExistentFileName);
        operation.get(ElytronDescriptionConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        if (required) {
            operation.get(ElytronDescriptionConstants.REQUIRED).set(true);
        }
        return operation;
    }

    private void addCertificateAuthorityAccount(String alias) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).add("mailto:admin@myexample.com");
        operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).set("LetsEncrypt");
        operation.get(ElytronDescriptionConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(ACCOUNTS_KEYSTORE_PASSWORD);
        assertSuccess(services.executeOperation(operation));
    }

    private void removeCertificateAuthorityAccount() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    private ClientAndServer setupTestCreateAccountWithoutAgreeingToTermsOfService() {

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"TrOIFke5bdM\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator()  +
                "  \"meta\": {" + System.lineSeparator()  +
                "    \"caaIdentities\": [" + System.lineSeparator()  +
                "      \"happy-hacker-ca.invalid\"" + System.lineSeparator()  +
                "    ]," + System.lineSeparator()  +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator()  +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator()  +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator()  +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator()  +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator()  +
                "}";

        final String NEW_NONCE_RESPONSE = "1NeSlP3y1aEyWGJ143a1cn6yTW-SBwRcBl9YuDKzOAU";

        final String NEW_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJxVm5ycWVrTXRuLVExZmk2M1N1QzhqSHdmSXB4S25mVU9RcjM5YWcxZXQzMkoyd1pXbGY0VF9BOWFmWFp2S3dYNUx5VjE4OGxvUm1STXotaTV4eUJQbnVSQlZWdTFtX3d6bjBWOS0taVVvY0Y2WTMyRFpaek9qbVViRTNkSlZ4VDZsNWVidVNycDlNSkZCaFl1bHgtTGhVZVpLT3FfZmtHSG4wYllyUklVLWhyTy1rYmhCVGZjeUV4WmhTaERvSkZJeE5vLUpnaFZBcTlsUmFGcUh5TUIwaS1PQ3ZGLXoySW53THBFWVk3ZE1TZGlzMmJpYWJMMkFEWWQ1X2ZxNlBZRUpTSFJTSnpnaXphRUpLa3IzMmp5dHg0bVNzd2pfZVpHcmdyT3VRWEVQenVSOUoySHB2TVhjNThGSjBjSHptMG81S3JyZE9iMTBpZ3NQeGE0N3NrLVEifSwibm9uY2UiOiIxTmVTbFAzeTFhRXlXR0oxNDNhMWNuNnlUVy1TQndSY0JsOVl1REt6T0FVIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6ZmFsc2UsImNvbnRhY3QiOlsibWFpbHRvOmFkbWluQGV4YW1wbGUuY29tIl19\",\"signature\":\"OoR7FxGDECKfMCfDn0cggD97D1R65EQRG0rpd3ykpAQDZOXqYpRRmxuR6eWDQM_gNunE9KODZu5bdrq2zM0HAqCXBSNM-KReU6sitSNKTQfhOakJsW1VeJHms3nh7HOu67ZhqZgfbhLK-l9w2EL4IEn4bkjrs2VcrIqzMC-tStEGRFWaq2de--TfErDnxC_Ij0GfXKlZsWKbvd4bq9ar_Fo8uPRi0146NPS5jYDDgD0_sL2Bz7fIPAIHAfufyTw_Iui1wBbgxqHOSTEmqDSJ9b7veztqCztRG8J-wfVoVSZg-uUbBYBQ7bbaSulrvZNNK9flC2ivJUxBLlru4YPrug\"}";

        final String NEW_ACCT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"urn:ietf:params:acme:error:malformed\"," + System.lineSeparator() +
                "  \"detail\": \"must agree to terms of service\"," + System.lineSeparator() +
                "  \"status\": 400" + System.lineSeparator() +
                "}";

        final String NEW_ACCT_REPLAY_NONCE = "20y41JJoZ3Rn0VCEKDRa5AzT0kjcz6b6FushFyVS4zY";
        final String NEW_ACCT_LOCATION = "";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(NEW_ACCT_REQUEST_BODY, NEW_ACCT_RESPONSE_BODY, NEW_ACCT_REPLAY_NONCE, NEW_ACCT_LOCATION, 400, true)
                .build();
    }

    private ClientAndServer setupTestObtainCertificateWithKeySize() {

        // set up a mock Let's Encrypt server
        final String ACCT_PATH = "/acme/acct/2";
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"R0Qoi70t57s\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"meta\": {" + System.lineSeparator() +
                "    \"caaIdentities\": [" + System.lineSeparator() +
                "      \"happy-hacker-ca.invalid\"" + System.lineSeparator() +
                "    ]," + System.lineSeparator() +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator() +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String NEW_NONCE_RESPONSE = "xqvTn53klxJBCc4a3pNhijzpr4xqKPAOS-uVqH64y94";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoNWlULUY4UzZMczJLZlRMNUZpNV9hRzhpdWNZTl9yajJVXy16ck8yckpxczg2WHVHQnY1SDdMZm9vOWxqM3lsaXlxNVQ2ejdkY3RZOW1rZUZXUEIxaEk0Rjg3em16azFWR05PcnM5TV9KcDlPSVc4QVllNDFsMHBvWVpNQTllQkE0ZnV6YmZDTUdONTdXRjBfMjhRRmJuWTVXblhXR3VPa0N6QS04Uk5IQlRxX3Q1a1BWRV9jNFFVemRJcVoyZG54el9FZ05jdU1hMXVHZEs3YmNybEZIdmNrWjNxMkpsT0NEckxEdEJpYW96ZnlLR0lRUlpheGRYSlE2cl9tZVdHOWhmZUJuMTZKcG5nLTU4TFd6X0VIUVFtLTN1bl85UVl4d2pIY2RDdVBUQ1RXNEFwcFdnZ1FWdE00ZTd6U1ZzMkZYczdpaVZKVzhnMUF1dFFINU53Z1EifSwibm9uY2UiOiJ4cXZUbjUza2x4SkJDYzRhM3BOaGlqenByNHhxS1BBT1MtdVZxSDY0eTk0IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AbXlleGFtcGxlLmNvbSJdfQ\",\"signature\":\"bhB01ghPOvmxuw8pH5Vyl1bT7alCfY-I5cdG0HOexdjYApov1c54PhCozT2dn-AklH7O7OsBHgEimq9aS2n3kuEMA3dhC2osxx4xkSK4LtZwo7TLZHuxKCe9znQTCPni7FPfr3sJTyrLZR0vAeq7KDhxd7gvPxgzfgVtPhILXI8JsWwq6Kgy2SPJ9KOgg2xW0NQqPLtZzP23J84xpxmYHzWCxWcRNtaQQ5QtRhq6ucN_yznujH5j8535V76VJCrkAaObxUuCpZzHbRPuRm0V2QviNTHhDIomuXIQCJMzRUleBnxjezZrIxr4yCJtpJSCffG02lpsX3ytuMZeTysfiQ\"}";


        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "0bL9ah0ITjdvggt0P77_o8dspCfmnOen-rimw7E9qwM";
        final String ACCT_LOCATION = "http://localhost:4001" + ACCT_PATH;

        final String QUERY_ACCT_REQUEST_BODY_2 = "";

        final String QUERY_ACCT_RESPONSE_BODY_2= "{" + System.lineSeparator() +
                "  \"id\": 1," + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"h5iT-F8S6Ls2KfTL5Fi5_aG8iucYN_rj2U_-zrO2rJqs86XuGBv5H7Lfoo9lj3yliyq5T6z7dctY9mkeFWPB1hI4F87zmzk1VGNOrs9M_Jp9OIW8AYe41l0poYZMA9eBA4fuzbfCMGN57WF0_28QFbnY5WnXWGuOkCzA-8RNHBTq_t5kPVE_c4QUzdIqZ2dnxz_EgNcuMa1uGdK7bcrlFHvckZ3q2JlOCDrLDtBiaozfyKGIQRZaxdXJQ6r_meWG9hfeBn16Jpng-58LWz_EHQQm-3un_9QYxwjHcdCuPTCTW4AppWggQVtM4e7zSVs2FXs7iiVJW8g1AutQH5NwgQ\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@myexample.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2018-11-26T20:25:24Z\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "na_bjoXbpRlEFD8Bb2shGzT2Xiy6_ju4Gs6YJCPPs1E";

        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMiIsIm5vbmNlIjoibmFfYmpvWGJwUmxFRkQ4QmIyc2hHelQyWGl5Nl9qdTRHczZZSkNQUHMxRSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1vcmRlciJ9\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoiaW5sbmVzZXBwd2tmd2V3LmNvbSJ9XX0\",\"signature\":\"Xobxsl_guUz2T3bUMTAwA5A4MzZt4HBzcGPHlcaldvPm8nqh2HZ9BfRBh7pAqGJUxzFJkyPK4BhO8F4ekzEQsEOhhCsV42f9lelVp2lWFbxPdWJVIOIhfLrzMLgTfqkrfL2GIZqsWAT4B94VgbBw1dfB7NwAzujGv6kJo9USA86slStLYDE06q7lL7q0tWe63vKtPhzEJv5odgcLL8vBb9ANiM9ZeSlFprw6nzTGn3M7gVY3IlenkK8XHJjN_9Xw0aeYcOMqB5o14LowDpyKFlgPYeVuu-bhl1YcGMrDvUVj0lnZS-_YoW0vfMKyvWxWhZKbVf8UcH-e_eAVdx2cbA\"}";
        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:18:11.372756901Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/2/8\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "RvM3fgI2Z08HTzhbwKA-EnOrJtnGqV81tOlfErZIAK4";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/2/8";

        final String AUTHZ_URL = "/acme/authz/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk";
        final String AUTHZ_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMiIsIm5vbmNlIjoiUnZNM2ZnSTJaMDhIVHpoYndLQS1Fbk9ySnRuR3FWODF0T2xmRXJaSUFLNCIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2F1dGh6L1l6THF2azdHZWRMSVZmQWtyZUZnTmNydC1LY1Y1TW9LZE1XWmNPbHFKcGsifQ\",\"payload\":\"\",\"signature\":\"C5nBE_22rqq5LsqDGackn_v09Jltf09fg-aPIW_xdL9jKWu2cOlU_ktFTYGI1JEzYyVplzoLLzkXgfmOdQKlm9IrxMWB7FsY_JzfEl2bHGsacE3we-OzPXFMQjPblAyc--7Prk56_mMtVpGaJMJAYOu4Nr3ZkcdWkjTvkNyRFGj2dinKS2aFytngBG26zZbLVTgZpXXHuvSxAd8C0cgc5KxJbk8iI3E9r39k_7RcbMRQ-2_scmoiWMTyipav7kBqEj8LSPqHLNeUo7hbui0Jwh8vQ6VFc1kMURqTGioXfzGQytsm3C2A6wOYGLdPgKldVu1J9ruD_bGw2NjUmMp_kw\"}";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:18:11Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/17\"," + System.lineSeparator() +
                "      \"token\": \"vKGXiPTz4xRD23TLKdFKUflWK6DdEPIWOdChQxWBJTA\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/18\"," + System.lineSeparator() +
                "      \"token\": \"6BIn9ySZG5m9yweJX1KKkRsJa_B0alX4DrfQF1YtmJc\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/19\"," + System.lineSeparator() +
                "      \"token\": \"59uoXgFHuyYVwZDxIyXIhFe-OZkFlJhk_3iFiENmRZ4\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/20\"," + System.lineSeparator() +
                "      \"token\": \"DGdnia8PWJaVYXnFZOdQGOedbryAWa7AUEk9UjSxA0w\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String AUTHZ_REPLAY_NONCE = "KvD4oVF2ahe2w2RtqbjYP9nJH_xzVWeHJIlhDRNn-N4";

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMiIsIm5vbmNlIjoiS3ZENG9WRjJhaGUydzJSdHFiallQOW5KSF94elZXZUhKSWxoRFJObi1ONCIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2NoYWxsZW5nZS9Zekxxdms3R2VkTElWZkFrcmVGZ05jcnQtS2NWNU1vS2RNV1pjT2xxSnBrLzIwIn0\",\"payload\":\"e30\",\"signature\":\"FyKdGn_TlCbEg21RtgXSIZS8Js0YGMFHv5V1SJaefy1LDw_YeSx5_X1g_rEB1BLGuxUoIv96CMDeX-_GAb5PNYSVQfzc_kEIs8YLpVWrWCq_KVNbRx1NWBl5Vc4hYgwWa246wWMD2AjMBOtD46ncuYinJkueHX3sbW_CKBMEo-LG3SdupX-sNckcpuQqlRdNaEwfi1hxEZLjoHvlyzfg9kUH4m39wsoSXELQm2ZeYv8pUOqvXH3M02Ik4CjT_2_lhh0NzU6Kh_WXrHawK-2FPkSYN0xdqh4qK1i_YcUSG9_trtgxcHBVJLfn9jroqpmpy7Y4Li8M4C4J-M90nzPMXQ\"}";
        final String CHALLENGE_URL = "/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/20";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/20\"," + System.lineSeparator() +
                "  \"token\": \"DGdnia8PWJaVYXnFZOdQGOedbryAWa7AUEk9UjSxA0w\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "A-3-ge_TjcQwoYdlSJX4YtznB5fPVME627MwK-U_GkM";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/20";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/DGdnia8PWJaVYXnFZOdQGOedbryAWa7AUEk9UjSxA0w";
        final String CHALLENGE_FILE_CONTENTS = "DGdnia8PWJaVYXnFZOdQGOedbryAWa7AUEk9UjSxA0w.w2Peh-j-AQnRWPMr_Xjf-IdvQBZYnSj__5h29xxhwkk";

        final String UPDATED_AUTHZ_REPLAY_NONCE = "jBxAXwYy9_19Bue5Wcij8aiAegiC4nqGTFD_42k3HQQ";
        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-12-23T22:18:11Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/17\"," + System.lineSeparator() +
                "      \"token\": \"vKGXiPTz4xRD23TLKdFKUflWK6DdEPIWOdChQxWBJTA\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/18\"," + System.lineSeparator() +
                "      \"token\": \"6BIn9ySZG5m9yweJX1KKkRsJa_B0alX4DrfQF1YtmJc\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/19\"," + System.lineSeparator() +
                "      \"token\": \"59uoXgFHuyYVwZDxIyXIhFe-OZkFlJhk_3iFiENmRZ4\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk/20\"," + System.lineSeparator() +
                "      \"token\": \"DGdnia8PWJaVYXnFZOdQGOedbryAWa7AUEk9UjSxA0w\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://172.17.0.1:5002/.well-known/acme-challenge/DGdnia8PWJaVYXnFZOdQGOedbryAWa7AUEk9UjSxA0w\"," + System.lineSeparator() +
                "          \"hostname\": \"inlneseppwkfwew.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"172.17.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"172.17.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMiIsIm5vbmNlIjoiakJ4QVh3WXk5XzE5QnVlNVdjaWo4YWlBZWdpQzRucUdURkRfNDJrM0hRUSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2ZpbmFsaXplLzIvOCJ9\",\"payload\":\"eyJjc3IiOiJNSUlFc3pDQ0Fwc0NBUUF3SGpFY01Cb0dBMVVFQXd3VGFXNXNibVZ6WlhCd2QydG1kMlYzTG1OdmJUQ0NBaUl3RFFZSktvWklodmNOQVFFQkJRQURnZ0lQQURDQ0Fnb0NnZ0lCQUtaSU15U2NOczM0UHNPTUtXZjE5cy1ORTBMNDVaUGYxdDVVZjhGYllfYkV2UFpiSG1qbS1RalhTQ1dVZmowelVzdHo2N3h2RXZicS01d2h5Zk9tQ2VBOWw4djBrZHBacHRwdUtIdjN6T05URDBCd2N3c01adzhYTm9DQXdQWk1DTUNqMTZPNDIzYjRzbTE0RDBEUGg2bEkwanI3dXB1QVhTWXU4YkNJeUVISllJY2ZDd2pGOXNUeUctV3p6SjdwMWhwV2F1N0s5MmFNekRQTGZBandUSkFqdGNxNEhuNl9GX3NIQXNQM0RhWU5lMnp4QUxaWXRiYzBKSGFELWdreVNILUZ6TU9pWlNJR0FTNFJCV3E4S2pwUkE3YWFTaDNfWDBjNG9kbFVPVjVXYVJWbjF0eWlGaGItMlNFT2IzQW0tb19uanBqUjBTYlVrNlZlZ0xicWVyQ2V1WGdZUzNYcXF5MlU3cTZtY0FFQUF6TzktcFlpM1Fza01hQXB3XzFhUUFSUU5JWFo3NWRpcGlmN295UzdtS0hTM0RaZUtPTDRuUlY1dFRyMjY2NFc4VUxZdjlhRmJGcFp2ak5EYThlYUl4QVdGWnEwYlN1UFhISjBjVjFGOHlaMUItT2Y4bGRhZmFjWGQxTms4S0swamw3MXRoVzhraFdtekJmNTdjR1Q3bUQ0a1dmcXY3YVJ0cVVfVE1RWjM4cDdGbnp0ZFhFTUpMQTZLRVhkV04yMU5hZkxodU1uRVBqSW9Rc2d2UEVsOEJVTDdZMkwtMC1MX2JuMEc5aV9LVG4tWFdteFdDNHk4dmdZMVQ0VVV1RkptT0lYSnl3TVFmMllvWDVIZUZ1bW10S3hHRHhrMjltSURTdHotSWVQd2lUOUE4SGtlVG5hcXN5RG1CQXpyWE5KQWdNQkFBR2dVREJPQmdrcWhraUc5dzBCQ1E0eFFUQV9NQjRHQTFVZEVRUVhNQldDRTJsdWJHNWxjMlZ3Y0hkclpuZGxkeTVqYjIwd0hRWURWUjBPQkJZRUZNRmszanFiXzRQWFZnRzhPa1ZsUXMzSzZPZkRNQTBHQ1NxR1NJYjNEUUVCREFVQUE0SUNBUUFGaXpjQW5sbmYwNUxad0duR3pKZHhQTHNndGhtanp3Uy0xcWJ2dy1kNlpmNGEtSGVBdjZTakNoVXgtVUI5UklpTy1HWERranp5eVpMUFlkbE50SlAteGNCeGs0YXZDc1ZvR0x5TUdMMVE1ejItcURXOXdoakR1M210TndvVFQ5REx3Sk1UUEJLV1ROWU9Za25tQkk1WDF1alpJVkFfSjJmUHA3SzNVMlJJQXdDNE9FX01sMzVYOElJU0hsTmplMUtMOVlNN0F4a0thcm03Wl9ic1lYcFVkQmR1T2VZRGNsbkdrRU1hT0Q4WDAxVUhuS1FmRVRlLUlmb0lXYVBSZWZWNGh4SnU5MndDR3EySnUtWVY3X0k2VVRGU1B1cUZJM0JrWXBXcUVIUUdWV2Qtck1MQ194UVVSbHNxWHhfbG5KQjlPdGFmcHpYblNCU0lRRk1xdm81Z2J3VWVscENpTDA0T280ZTNfS0NfSGtLQ3c0ZTNZdXdnY2FFUVJ3YUVZS1Y0UEc5QmNsbE1Ia19McE5LVXM1SGE1NDh6V1JNQWhRc2c3d0NIZHEzb2xwZWUyWUowbUN4cjdDLUlqT2dmcFhrdUtwYWhITUs2UzlybU9zZjdLaHVLeXpWLVBHalAybXZmTXo1b2RfZVlRV3M1UHVlbDVjMEhnbkRjdlBhVGRwTDdrZ1V5TEpHRWVBUVQ3WkE5cGxscm1hSDA1aDZRREtwNHBCLUJieTBaaHVYWlFxV2hmbmZsak91dklHN0hWUzlXWXlhTl9mdlBJOFJJa1F0MTdXQ094cEZYNDJ5YmRtWC1IVUdqLWYwS3NfOExudUVsMjBhazZ3bjZFRldLOWRRZnBaOXZIcndybTVMV0xaenlhdG1zSmI4QW1KUGhjQ3lOUFVjcFJfZmg3ZyJ9\",\"signature\":\"Cs0G6_pY_ql1INkfziVjpTW2lzlNdnW7HAn1pKwlCcb2h5IFjUA-JAfCxUrWR_uMNnvlk7-KuwtTNN_AXSaocuRlc6uJd7ZsF2xpTrkGFrTDolVjfM8VxSQYZlLhN_TMKyFyH-Dxn0fAptM2Xm3PZBSkkXMuuiSuqjMUV4fGKGEik4WERuUJZsEgdiPUsZu61usfk3kyjZ1MetgU7VzMMXg0e3tTd5t490B4X6fad8sllmee1TpTFhdfNRFcf6GYDcPnkyApvRsI9sc5gB0WM_Q_zrFip5Rk2irA2QQZeVkYmdQ7E5wEucnoxbjoY-m3A4d-4y4mcBSSZ5OA1AOKHQ\"}";
        final String FINALIZE_URL = "/acme/finalize/2/8";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:18:11Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/2/8\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/fff0ba7aa54a2ce6597c0fa0dd6f7c8e87a7\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "l_OFFmSJQuq27CFQSG6N-2vbNCbyHG7E_RyF3J45wvg";
        final String FINALIZE_LOCATION = "http://localhost:4001/acme/order/2/8";

        final String CHECK_ORDER_URL = "/acme/order/2/8";
        final String CHECK_ORDER_REPLAY_NONCE = "yuXkl473reHRMcaVgTyTZ1AWO8Z_HbiHo9oj3RdoUog";

        final String CHECK_ORDER_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMiIsIm5vbmNlIjoibF9PRkZtU0pRdXEyN0NGUVNHNk4tMnZiTkNieUhHN0VfUnlGM0o0NXd2ZyIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL29yZGVyLzIvOCJ9\",\"payload\":\"\",\"signature\":\"drMLY2890JViRe2N8BZB2gfpveieZ0hzOUYHJYz9eUPOhAzYJyS658OAs27oil7LnRVFFVdLu6iIYKmeCjS3tRWNkFQLPba8EDRSaGaJQGshaVhHtvxfv-p3M_0pJ3Mu7lJDzDwzzbZ_cYeeqI0txp1qXNqp68Ac7aT946nRrsLPaefiff0n0tGtlYvnc3TXML3hohhLtz_4xXmWnr3f_-dT17BSAZNDPrp1d7wFaoD1LVEBwTG1X-NFNOPweQ0imAEUQCg8ZPDNSbBBxxO1iLqNjQXITPxBV3hz-fmLDzh82Pgfs4KkSBtUPPkxDAX4Re6LHzkW7J-Vqu_E2NH01Q\"}";
        final String CHECK_ORDER_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:18:11Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/YzLqvk7GedLIVfAkreFgNcrt-KcV5MoKdMWZcOlqJpk\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/2/8\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/fff0ba7aa54a2ce6597c0fa0dd6f7c8e87a7\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CERT_URL = "/acme/cert/fff0ba7aa54a2ce6597c0fa0dd6f7c8e87a7";
        final String CERT_REPLAY_NONCE = "9Ir87CU21P5mNNGfhBASf2dkD7QpJdZfB9BGMIzQW9Q";

        final String CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMiIsIm5vbmNlIjoieXVYa2w0NzNyZUhSTWNhVmdUeVRaMUFXTzhaX0hiaUhvOW9qM1Jkb1VvZyIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2NlcnQvZmZmMGJhN2FhNTRhMmNlNjU5N2MwZmEwZGQ2ZjdjOGU4N2E3In0\",\"payload\":\"\",\"signature\":\"NyRAnCigeTinK1pdkEhzO1ZKwCzuG70hNBbySxzkoS00SNq-KNA_eYu9Hk5FK7SA8HPWWadgJ4UA2GNEotqaQKzMpinPPonW2hX_SrLOcUTcRAYsggpoQl6jLRCT7O4bJ4Glve_IrAW1F2GEEqWHAhEnTSQDpZul9d5qrORjUxt7qu8A_5nAbssPDErplv2uXJH4BZAyLS2v4g-MG-Yf-Iun8kN7QC4-9uFNlIZMQyclqO1nYEUbVYanZnvxTv0WysabMZlsTmCJtElsfGdraJqBMnFvstd5E5dKqcGibq5uzleJgxYd2e5sFJfKe7cew7pbVTqvjl-nk1EwqUVizw\"}";
        final String CERT_RESPONSE_BODY = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIGRjCCBS6gAwIBAgITAP/wunqlSizmWXwPoN1vfI6HpzANBgkqhkiG9w0BAQsF" + System.lineSeparator() +
                "ADAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTAeFw0xODExMjMyMTE4" + System.lineSeparator() +
                "MTJaFw0xOTAyMjEyMTE4MTJaMB4xHDAaBgNVBAMTE2lubG5lc2VwcHdrZndldy5j" + System.lineSeparator() +
                "b20wggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQCmSDMknDbN+D7DjCln" + System.lineSeparator() +
                "9fbPjRNC+OWT39beVH/BW2P2xLz2Wx5o5vkI10gllH49M1LLc+u8bxL26vucIcnz" + System.lineSeparator() +
                "pgngPZfL9JHaWababih798zjUw9AcHMLDGcPFzaAgMD2TAjAo9ejuNt2+LJteA9A" + System.lineSeparator() +
                "z4epSNI6+7qbgF0mLvGwiMhByWCHHwsIxfbE8hvls8ye6dYaVmruyvdmjMwzy3wI" + System.lineSeparator() +
                "8EyQI7XKuB5+vxf7BwLD9w2mDXts8QC2WLW3NCR2g/oJMkh/hczDomUiBgEuEQVq" + System.lineSeparator() +
                "vCo6UQO2mkod/19HOKHZVDleVmkVZ9bcohYW/tkhDm9wJvqP546Y0dEm1JOlXoC2" + System.lineSeparator() +
                "6nqwnrl4GEt16qstlO6upnABAAMzvfqWIt0LJDGgKcP9WkAEUDSF2e+XYqYn+6Mk" + System.lineSeparator() +
                "u5ih0tw2Xiji+J0VebU69uuuFvFC2L/WhWxaWb4zQ2vHmiMQFhWatG0rj1xydHFd" + System.lineSeparator() +
                "RfMmdQfjn/JXWn2nF3dTZPCitI5e9bYVvJIVpswX+e3Bk+5g+JFn6r+2kbalP0zE" + System.lineSeparator() +
                "Gd/KexZ87XVxDCSwOihF3VjdtTWny4bjJxD4yKELILzxJfAVC+2Ni/tPi/259BvY" + System.lineSeparator() +
                "vyk5/l1psVguMvL4GNU+FFLhSZjiFycsDEH9mKF+R3hbpprSsRg8ZNvZiA0rc/iH" + System.lineSeparator() +
                "j8Ik/QPB5Hk52qrMg5gQM61zSQIDAQABo4ICejCCAnYwDgYDVR0PAQH/BAQDAgWg" + System.lineSeparator() +
                "MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAAMB0G" + System.lineSeparator() +
                "A1UdDgQWBBTBZN46m/+D11YBvDpFZULNyujnwzAfBgNVHSMEGDAWgBT7eE8S+WAV" + System.lineSeparator() +
                "gyyfF380GbMuNupBiTBkBggrBgEFBQcBAQRYMFYwIgYIKwYBBQUHMAGGFmh0dHA6" + System.lineSeparator() +
                "Ly8xMjcuMC4wLjE6NDAwMi8wMAYIKwYBBQUHMAKGJGh0dHA6Ly9ib3VsZGVyOjQ0" + System.lineSeparator() +
                "MzAvYWNtZS9pc3N1ZXItY2VydDAeBgNVHREEFzAVghNpbmxuZXNlcHB3a2Z3ZXcu" + System.lineSeparator() +
                "Y29tMCcGA1UdHwQgMB4wHKAaoBiGFmh0dHA6Ly9leGFtcGxlLmNvbS9jcmwwQAYD" + System.lineSeparator() +
                "VR0gBDkwNzAIBgZngQwBAgEwKwYDKgMEMCQwIgYIKwYBBQUHAgEWFmh0dHA6Ly9l" + System.lineSeparator() +
                "eGFtcGxlLmNvbS9jcHMwggEEBgorBgEEAdZ5AgQCBIH1BIHyAPAAdgAodhoYkCf7" + System.lineSeparator() +
                "7zzQ1hoBjXawUFcpx6dBG8y99gT0XUJhUwAAAWdCpuW/AAAEAwBHMEUCIQDSlIhR" + System.lineSeparator() +
                "AaD+KnEI3cUBIigDrbXJxXDYUIwoIcYErsHF7gIgXbY/rmJ6LCbYyt8PwZkDVStn" + System.lineSeparator() +
                "2Khogm0Tk5hK4FynyxYAdgAW6GnB0ZXq18P4lxrj8HYB94zhtp0xqFIYtoN/MagV" + System.lineSeparator() +
                "CAAAAWdCpuuZAAAEAwBHMEUCIQDmVp+En4lRjkqn23HuzJk2mEkGbuDOQvLcZ+XH" + System.lineSeparator() +
                "hj4DcgIgBpfHfTG7i3mtCTYz20hP72/9qbEyKI8I/0yt/bMMjlEwDQYJKoZIhvcN" + System.lineSeparator() +
                "AQELBQADggEBAEMZGO3pbTME1J97CDjpK8SX/0HUyOa8fyLXn8et6R6Q+LfhtZuE" + System.lineSeparator() +
                "Tb+RsKtx+QcEiqwFTQF5/tIqHh3T8QoXZvSvanUmn+/wAjgmhllRHbVuNe/8QB+f" + System.lineSeparator() +
                "NE+hhbpB5IPiQjBFPNuTyHSq5HZisrPKXr9hjKc+UhqHu6VC6kgQT7JrAlQ3YXcA" + System.lineSeparator() +
                "rIUGyi325G8mOUqs+vl24Lu6ll2BP9kHTBatYJyj0b1JnuVpIiCCXSS13v3VYg+b" + System.lineSeparator() +
                "ejRaEGe9QhHNHEola5ZxYb/Ryacvd/ZGZBAIRCy8zOV4zaOmP6WXk9yajUswhymx" + System.lineSeparator() +
                "uDS1f3V/hiCjfuDZ7ljN4FQYBF2eIMZT6Ks=" + System.lineSeparator() +
                "-----END CERTIFICATE-----" + System.lineSeparator() +
                "" + System.lineSeparator() +
                "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIERTCCAy2gAwIBAgICElowDQYJKoZIhvcNAQELBQAwKzEpMCcGA1UEAwwgY2Fj" + System.lineSeparator() +
                "a2xpbmcgY3J5cHRvZ3JhcGhlciBmYWtlIFJPT1QwHhcNMTYwMzIyMDI0NzUyWhcN" + System.lineSeparator() +
                "MjEwMzIxMDI0NzUyWjAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTCC" + System.lineSeparator() +
                "ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMIKR3maBcUSsncXYzQT13D5" + System.lineSeparator() +
                "Nr+Z3mLxMMh3TUdt6sACmqbJ0btRlgXfMtNLM2OU1I6a3Ju+tIZSdn2v21JBwvxU" + System.lineSeparator() +
                "zpZQ4zy2cimIiMQDZCQHJwzC9GZn8HaW091iz9H0Go3A7WDXwYNmsdLNRi00o14U" + System.lineSeparator() +
                "joaVqaPsYrZWvRKaIRqaU0hHmS0AWwQSvN/93iMIXuyiwywmkwKbWnnxCQ/gsctK" + System.lineSeparator() +
                "FUtcNrwEx9Wgj6KlhwDTyI1QWSBbxVYNyUgPFzKxrSmwMO0yNff7ho+QT9x5+Y/7" + System.lineSeparator() +
                "XE59S4Mc4ZXxcXKew/gSlN9U5mvT+D2BhDtkCupdfsZNCQWp27A+b/DmrFI9NqsC" + System.lineSeparator() +
                "AwEAAaOCAX0wggF5MBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgGG" + System.lineSeparator() +
                "MH8GCCsGAQUFBwEBBHMwcTAyBggrBgEFBQcwAYYmaHR0cDovL2lzcmcudHJ1c3Rp" + System.lineSeparator() +
                "ZC5vY3NwLmlkZW50cnVzdC5jb20wOwYIKwYBBQUHMAKGL2h0dHA6Ly9hcHBzLmlk" + System.lineSeparator() +
                "ZW50cnVzdC5jb20vcm9vdHMvZHN0cm9vdGNheDMucDdjMB8GA1UdIwQYMBaAFOmk" + System.lineSeparator() +
                "P+6epeby1dd5YDyTpi4kjpeqMFQGA1UdIARNMEswCAYGZ4EMAQIBMD8GCysGAQQB" + System.lineSeparator() +
                "gt8TAQEBMDAwLgYIKwYBBQUHAgEWImh0dHA6Ly9jcHMucm9vdC14MS5sZXRzZW5j" + System.lineSeparator() +
                "cnlwdC5vcmcwPAYDVR0fBDUwMzAxoC+gLYYraHR0cDovL2NybC5pZGVudHJ1c3Qu" + System.lineSeparator() +
                "Y29tL0RTVFJPT1RDQVgzQ1JMLmNybDAdBgNVHQ4EFgQU+3hPEvlgFYMsnxd/NBmz" + System.lineSeparator() +
                "LjbqQYkwDQYJKoZIhvcNAQELBQADggEBAKvePfYXBaAcYca2e0WwkswwJ7lLU/i3" + System.lineSeparator() +
                "GIFM8tErKThNf3gD3KdCtDZ45XomOsgdRv8oxYTvQpBGTclYRAqLsO9t/LgGxeSB" + System.lineSeparator() +
                "jzwY7Ytdwwj8lviEGtiun06sJxRvvBU+l9uTs3DKBxWKZ/YRf4+6wq/vERrShpEC" + System.lineSeparator() +
                "KuQ5+NgMcStQY7dywrsd6x1p3bkOvowbDlaRwru7QCIXTBSb8TepKqCqRzr6YREt" + System.lineSeparator() +
                "doIw2FE8MKMCGR2p+U3slhxfLTh13MuqIOvTuA145S/qf6xCkRc9I92GpjoQk87Z" + System.lineSeparator() +
                "v1uhpkgT9uwbRw0Cs5DMdxT/LgIUSfUTKU83GNrbrQNYinkJ77i6wG0=" + System.lineSeparator() +
                "-----END CERTIFICATE-----" + System.lineSeparator();

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_1, QUERY_ACCT_RESPONSE_BODY_1, QUERY_ACCT_REPLAY_NONCE_1, ACCT_LOCATION, 200)
                .updateAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_2, QUERY_ACCT_RESPONSE_BODY_2, QUERY_ACCT_REPLAY_NONCE_2, ACCT_PATH, 200)
                .orderCertificateRequestAndResponse(ORDER_CERT_REQUEST_BODY, ORDER_CERT_RESPONSE_BODY, ORDER_CERT_REPLAY_NONCE, ORDER_LOCATION, 201, false)
                .addAuthorizationResponseBody(AUTHZ_URL, AUTHZ_REQUEST_BODY, AUTHZ_RESPONSE_BODY, AUTHZ_REPLAY_NONCE)
                .addChallengeRequestAndResponse(CHALLENGE_REQUEST_BODY, CHALLENGE_URL, CHALLENGE_RESPONSE_BODY, CHALLENGE_REPLAY_NONCE, CHALLENGE_LOCATION, CHALLENGE_LINK, 200, false, VERIFY_CHALLENGE_URL, CHALLENGE_FILE_CONTENTS, AUTHZ_URL, UPDATED_AUTHZ_RESPONSE_BODY, UPDATED_AUTHZ_REPLAY_NONCE)
                .addFinalizeRequestAndResponse(FINALIZE_RESPONSE_BODY, FINALIZE_REPLAY_NONCE, FINALIZE_URL, FINALIZE_LOCATION, 200)
                .addCheckOrderRequestAndResponse(CHECK_ORDER_URL, CHECK_ORDER_REQUEST_BODY, CHECK_ORDER_RESPONSE_BODY, CHECK_ORDER_REPLAY_NONCE, 200)
                .addCertificateRequestAndResponse(CERT_URL, CERT_REQUEST_BODY, CERT_RESPONSE_BODY, CERT_REPLAY_NONCE, 200)
                .build();
    }

    private ClientAndServer setupTestObtainCertificateWithECPublicKey() {
        // set up a mock Let's Encrypt server
        final String ACCT_PATH = "/acme/acct/3";
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"R0Qoi70t57s\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"meta\": {" + System.lineSeparator() +
                "    \"caaIdentities\": [" + System.lineSeparator() +
                "      \"happy-hacker-ca.invalid\"" + System.lineSeparator() +
                "    ]," + System.lineSeparator() +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator() +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String NEW_NONCE_RESPONSE = "I_xE0lMrq7iwHG5BQKgdfET9PFg5WLwzkSofkQyzCMU";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJpVk5xSnVoM3VvWW5xdkNfZGtYQzRFMDN4R292eTdLUjAwd3M4QUwwcHJWcktzajhnZFdhWjBLZkZ1Q0NUaUtMU1BhNVQ0ZnRWNFdia2l0djFMa0JWU29Wd1hqSDE0bFpIMWFHYkptR1lCX3pSOV9uVzZJTzRVb1RGc2Vqb3paN05kNW8waVFpQWpyRjBmMDhGVC1xYS1TVVZiVk16dkNnQW16SjJFVlhzOXdOQ2pzSVRnNGh3eDdZRzl5eHRhZjFoT0hkV1dKVWtwZ0hnQkVfclpZT1B5YVNlb2JyeE5mMllxVmhFNWM2ZjhrZUhYdnU2dnprODctZVNLWXlndk9hSW1YOUhFbFZhQXRVcnI0S3hFV3VvUDdNRzZCV0s2TDVpam9Db0VMQjBqM0w2UHNuXzM1VnMxQi05OFR6SFZqYU1sU1NGV20xQjdtS0NzNGZMeE1pRXcifSwibm9uY2UiOiJJX3hFMGxNcnE3aXdIRzVCUUtnZGZFVDlQRmc1V0x3emtTb2ZrUXl6Q01VIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AbXlleGFtcGxlLmNvbSJdfQ\",\"signature\":\"eFH0rsYtNBCs6v_DD8uKbEYDfkIQoZdH7GOAz8TwBA37NDT6V1TT_VxY3Jqd02euHo71gqMrb4rYG1H-K38jtWaAJJZW98UuP6tuefw5qrso9POS27HGOzdVu7UtacT9B1hj3PsgsHQpJRZPzBVtgo3e3dBob57dMwxmeXQ5GbzL35OIBxWMI-NWjXdkug4Fm-v-p-km1VqzKACesD0Pu5UmKrn2kt3ZGaSwMX7hJnN7muXmJe9diG_QH_qIB-y1uR8MmTiWl-rhv8_0nOH0vYuf31BXeurqJerFjtWr1mXe22vISYVORjH-1eYMt-gOcOlWEO4CvceUFdQu8UbTWA\"}";


        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "MW2S_i5GD86wRqzEl1Y8g5rm5vwhypO52NAnRtzW-yg";

        final String QUERY_ACCT_REQUEST_BODY_2 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiTVcyU19pNUdEODZ3UnF6RWwxWThnNXJtNXZ3aHlwTzUyTkFuUnR6Vy15ZyIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyJ9\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AbXlleGFtcGxlLmNvbSJdfQ\",\"signature\":\"VPnJ5ySaCMrLlB2yU71MUZKA_eFptuLbYxnRXs1lRxtPbV79Ql9853ykwENN3Uy-jMqnmMph2Wi_yJRa30-TvvwthY5lxQQzJ-sTVP683JNKPJbjpj7lEMXhfsGhzpScynN4pcFGQtUgYRQ9Yi7e8VSucLorDnyU6_AecY4auELAfAdJy9dDfYTsJCs_u0hC25ob6RS2iIgJTQ3WcC5Y52i8_d8vWQweHD_G0tM-XZWw1foyt_nUpSDbbo9kfRBO4qAL00jfO2av35WF0p35A0iowosjFXtn7Pft9NKDbJzgK5_Ax83mDaKp5dJzcAh_QjQ2TuNU9EMosqOHvi0PbQ\"}";

        final String QUERY_ACCT_RESPONSE_BODY_2 = "";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "GLc_xR_n3Ytx8bDjmIODHL4tuHWul6phFmFVGASUI-s";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/3";


        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiR0xjX3hSX24zWXR4OGJEam1JT0RITDR0dUhXdWw2cGhGbUZWR0FTVUktcyIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1vcmRlciJ9\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoibW5kZWxrZG5iY2lsb2hnLmNvbSJ9XX0\",\"signature\":\"REO9CCtOq19V3T0tKTuwTlZZlRwCoe1Sy_-xYitF4dHADT63cocpyzaML0OAgnuc8BrqTnbLhP3KcsLAVPx3gmFpsYY4iWPL2EsB-tVJzEWGqjHd-X2WkX9i2uO9U615zWgM2k6shzduewV7GlF6yMBl4SAB3lg7wCYtS5-cVGF1SrVqKHuBDC9istsWYLC0AkfgJwO1gdK4fweQJ4WP-0OHHi9SX7WIjwKxgRrMDlWl-UJO9bc4lqMEcIKdMQsg_q_hYwxSUgIAhM_a3a1gEidutlfOgexwvtdFQY2mMPJtHiwwkRmO9Fmre6gdvQwoVVLryLuGRJv5I-W84ZG3bg\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:43:32.370907593Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"mndelkdnbcilohg.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/3/10\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "i4xRyKelZj5ScS7U7TcTU2PxO6Ri41YpDHPamcMbfN0";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/3/10";

        final String AUTHZ_URL = "/acme/authz/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g";
        final String AUTHZ_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiaTR4UnlLZWxaajVTY1M3VTdUY1RVMlB4TzZSaTQxWXBESFBhbWNNYmZOMCIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2F1dGh6L2c5bWJoaXRWS1lxMGVCWXhuaG00THgyYkNBYTJRVEtfWkJUOFhCeHlQMWcifQ\",\"payload\":\"\",\"signature\":\"YupyN8RQoenfotv3VHLvehnp2LBYtUTGJRQDcFS65kPog_bI7MojREpeIGYOcBCLrGpuLH-_LOowqdfd4aocfAv2Dk-skNg8Ma5FjLFdY51Eo2ULqPwTGXX1TY78B8cWiUpDr8se3NgFTcEDYEk6F_V-kUh8eylEwaQUsmexwTPPUL2fmT4hL-5R3CGCadWYTmsEHqB45BwqDtPvd-81CTbOQ18sTrbV1d-Xf5hQWdZfm_78FrSXjqqRl3dI-WP8K0CE7vqJ6euXJVUMa7K-Gwwkrp6CJ9yQfcOQv-eu-B9o-WIfUgQRioqjgLzOJ4z8dbojNE-gnOtId4uGn1O5xQ\"}";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"mndelkdnbcilohg.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:43:32Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/25\"," + System.lineSeparator() +
                "      \"token\": \"zjXs_VldJCiubFFW7Vvr1bctw7JAbO0PtIhjQc4Kb4U\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/26\"," + System.lineSeparator() +
                "      \"token\": \"vdUixEBpiDj0RuJKlJnplaSvpr4C_GfBOVh_zUUUulk\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/27\"," + System.lineSeparator() +
                "      \"token\": \"hGOq4xCmkDe4E7ZzqUbwT6PfjoT5VipE2PpyutL2RxQ\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/28\"," + System.lineSeparator() +
                "      \"token\": \"f-0Jro36un-NLfg-kCqPEdvsDWwPbX7-FZY1SSmZ9w8\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String AUTHZ_REPLAY_NONCE = "rqfaFFRiaabH3tAJQoW7R3J-AStDq-MmE7um5NluHSE";

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoicnFmYUZGUmlhYWJIM3RBSlFvVzdSM0otQVN0RHEtTW1FN3VtNU5sdUhTRSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2NoYWxsZW5nZS9nOW1iaGl0VktZcTBlQll4bmhtNEx4MmJDQWEyUVRLX1pCVDhYQnh5UDFnLzI4In0\",\"payload\":\"e30\",\"signature\":\"W777d69Y9TiV8GB2mZrkZd2AUcSvj6AJJtvCvNb7ILOIXpQuEdspA1_aFAhY5Pzilqocsd8RApJYgRyHULCmWeIwG_SOMKFfyMOXnPzUFnecUNJBRQhZzXiotrILhIUkBGU0bZBshemRmGZSdAe9bASVqcEWLqWlSaX3Idd0vJ6m31TuYqz6Po5ClUvrHWL0-1i4gjKHpNXnJ7bzqa6KRe6BCo9bVC_frMWEiSaE6Cq-YXB9pSmAXsJRkLNgbuFe8c8pRMfSFJluVkxE-yP1cTjChJGvHx5tRD6DmOKRIPXfwQ2zMEBxudjxaka8mdeKayOVpqTfXXlSp-aiS03N7w\"}";
        final String CHALLENGE_URL = "/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/28";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/28\"," + System.lineSeparator() +
                "  \"token\": \"f-0Jro36un-NLfg-kCqPEdvsDWwPbX7-FZY1SSmZ9w8\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "Luvt_xmHKnuIQuUWMpI1-HjWMsNuVMK7mSnbgciTNxw";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/28";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/f-0Jro36un-NLfg-kCqPEdvsDWwPbX7-FZY1SSmZ9w8";
        final String CHALLENGE_FILE_CONTENTS = "f-0Jro36un-NLfg-kCqPEdvsDWwPbX7-FZY1SSmZ9w8.952Xm_XyluK_IpyAn6NKkgOGuXbeWn8qoo0Bs9I8mFg";

        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"mndelkdnbcilohg.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-12-23T22:43:32Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/25\"," + System.lineSeparator() +
                "      \"token\": \"zjXs_VldJCiubFFW7Vvr1bctw7JAbO0PtIhjQc4Kb4U\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/26\"," + System.lineSeparator() +
                "      \"token\": \"vdUixEBpiDj0RuJKlJnplaSvpr4C_GfBOVh_zUUUulk\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/27\"," + System.lineSeparator() +
                "      \"token\": \"hGOq4xCmkDe4E7ZzqUbwT6PfjoT5VipE2PpyutL2RxQ\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g/28\"," + System.lineSeparator() +
                "      \"token\": \"f-0Jro36un-NLfg-kCqPEdvsDWwPbX7-FZY1SSmZ9w8\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://172.17.0.1:5002/.well-known/acme-challenge/f-0Jro36un-NLfg-kCqPEdvsDWwPbX7-FZY1SSmZ9w8\"," + System.lineSeparator() +
                "          \"hostname\": \"mndelkdnbcilohg.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"172.17.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"172.17.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String UPDATED_AUTHZ_REPLAY_NONCE = "z8JyOz3PXdq67aXf3rpXBS5LkAjSibX9xqFW4qaCvB8";

        final String FINALIZE_URL = "/acme/finalize/3/10";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:43:32Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"mndelkdnbcilohg.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/3/10\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff87f7830644eb0e60d43bf624d6d028bd89\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "9jSBsPqFmUC9D5kxAJ6g7XsN6LWGLCLp3Y6pDTWbHHM";
        final String FINALIZE_LOCATION = "http://localhost:4001/acme/order/3/10";

        final String CHECK_ORDER_URL = "/acme/order/3/10";
        final String CHECK_ORDER_REPLAY_NONCE = "WQ_v5SrNqsA_9v_xCmH3MyQgWqX2PZg59OKWG1EYIQU";

        final String CHECK_ORDER_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiOWpTQnNQcUZtVUM5RDVreEFKNmc3WHNONkxXR0xDTHAzWTZwRFRXYkhITSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL29yZGVyLzMvMTAifQ\",\"payload\":\"\",\"signature\":\"HHxbg4kXThDyJrw0lp1VNfTxVi1GsKKO2AUhCUpaxNK61pgv49eRlHZq6vQiG0do5F1JGJTN8gc_NtoMoovlEQl9Rp48G-9ZHdk5_XNaTr7AEW_TDKufX3vlOpElkVDr0pRZWPhgc5RdauxzFoCQDVQQN1ZK4CbuELwp-FHTgoGvvc_vWT9gN0pOTVYPA02N7sN1yy0XF6PrJrHJgZyDvNx2urWkrIgUtemKv9-6eyjLwY315YCTQ-DygeLwkjVw1DeC2O-yMXJ_rZPOS3I3Kvephvj3xleyJ3xLoboYhdIp8_GnK3rcuPrmYHvu097XD0YfWRbfaYGzp3Zhh_0-hA\"}";
        final String CHECK_ORDER_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T22:43:32Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"mndelkdnbcilohg.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/g9mbhitVKYq0eBYxnhm4Lx2bCAa2QTK_ZBT8XBxyP1g\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/3/10\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff87f7830644eb0e60d43bf624d6d028bd89\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CERT_URL = "/acme/cert/ff87f7830644eb0e60d43bf624d6d028bd89";
        final String CERT_REPLAY_NONCE = "by4s6iEQL4-fAf_ku09qljcbhlxL7sAftG8YdJLJfiE";

        final String CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiV1FfdjVTck5xc0FfOXZfeENtSDNNeVFnV3FYMlBaZzU5T0tXRzFFWUlRVSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2NlcnQvZmY4N2Y3ODMwNjQ0ZWIwZTYwZDQzYmY2MjRkNmQwMjhiZDg5In0\",\"payload\":\"\",\"signature\":\"VfoBhgP5mnEDf2_JeomN2xcDr4AuA58b3g0Q_NgWOvGC1egoyGA4PzaqZRJQC_dg14hFnZWUF6WNUUz0hAyD7pFOiA8YEbi0s42pse4H2X-xUnnhRRGmitxcjZYS-t7BjBYaMyHirT6dhpmpcZIEiROYqUGG3WrycDQDvV3s9WsGjdjOYHCTiLA5WWnm3okB8xLugmHHdsC8XXTnUuZjuNpqYJBIJM0fg60aqmkZOZup6BCQy83T4i-Obz65hndCFVG-zHIGV7V8zPUqkPYTQ3Zzztb0Bj_fvYoB6oxZoMw687NcPV_3Q3HQ34vKfu8K2DEJleTzgwueV-7dC2BJQw\"}";
        final String CERT_RESPONSE_BODY = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIEnzCCA4egAwIBAgITAP+H94MGROsOYNQ79iTW0Ci9iTANBgkqhkiG9w0BAQsF" + System.lineSeparator() +
                "ADAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTAeFw0xODExMjMyMTQz" + System.lineSeparator() +
                "MzJaFw0xOTAyMjEyMTQzMzJaMB4xHDAaBgNVBAMTE21uZGVsa2RuYmNpbG9oZy5j" + System.lineSeparator() +
                "b20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASkd8KOCVYYS/TzqUunlsoJX57r" + System.lineSeparator() +
                "iZbr5QuO+4vWHXpRd7kl9soPKttpDMVn6/lWgM8N/z4hyC0RxtJ9y5qJimmoo4IC" + System.lineSeparator() +
                "njCCApowDgYDVR0PAQH/BAQDAgeAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF" + System.lineSeparator() +
                "BQcDAjAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBSD8y6uXfvil+pta5vpFb1pO6IF" + System.lineSeparator() +
                "nDAfBgNVHSMEGDAWgBT7eE8S+WAVgyyfF380GbMuNupBiTBmBggrBgEFBQcBAQRa" + System.lineSeparator() +
                "MFgwIgYIKwYBBQUHMAGGFmh0dHA6Ly8xMjcuMC4wLjE6NDAwMi8wMgYIKwYBBQUH" + System.lineSeparator() +
                "MAKGJmh0dHA6Ly8xMjcuMC4wLjE6NDAwMC9hY21lL2lzc3Vlci1jZXJ0MB4GA1Ud" + System.lineSeparator() +
                "EQQXMBWCE21uZGVsa2RuYmNpbG9oZy5jb20wJwYDVR0fBCAwHjAcoBqgGIYWaHR0" + System.lineSeparator() +
                "cDovL2V4YW1wbGUuY29tL2NybDBhBgNVHSAEWjBYMAgGBmeBDAECATBMBgMqAwQw" + System.lineSeparator() +
                "RTAiBggrBgEFBQcCARYWaHR0cDovL2V4YW1wbGUuY29tL2NwczAfBggrBgEFBQcC" + System.lineSeparator() +
                "AjATDBFEbyBXaGF0IFRob3UgV2lsdDCCAQUGCisGAQQB1nkCBAIEgfYEgfMA8QB2" + System.lineSeparator() +
                "ABboacHRlerXw/iXGuPwdgH3jOG2nTGoUhi2g38xqBUIAAABZ0K+FRsAAAQDAEcw" + System.lineSeparator() +
                "RQIgcDtvDgILEVlsLLOkfVFeFbOdUJdCkPaMJp1firJNv2sCIQDs5A9jhOQtsV4C" + System.lineSeparator() +
                "+v7ep/sK8kgMjiKpkmzw0xcbEgrdIgB3AN2ZNPyl5ySAyVZofYE0mQhJskn3tWnY" + System.lineSeparator() +
                "x7yrP1zB825kAAABZ0K+FR4AAAQDAEgwRgIhAMsFphjNxqMEpB5HIgZCOdujjsco" + System.lineSeparator() +
                "fVLRPntCEXoTSpXvAiEAyWf3EdceQS130qTfhboW4lrshoLyVBb5cOHCnI5UkT4w" + System.lineSeparator() +
                "DQYJKoZIhvcNAQELBQADggEBAGzFBD9ybCDO6KqcD2FCA46uS1TCTedOT2VMozJM" + System.lineSeparator() +
                "DXmGR3y/deVOc+OyTOqzPpl894EHjYz5CvlosX2Pf3LBi+VhfTM7/UgVDLyYJ+dp" + System.lineSeparator() +
                "Kh/bt4lyO9903COG/9OVDTWwychZN5vYQdUOFLNZWwd9dDbHNega11uGEoPwq4ON" + System.lineSeparator() +
                "O3IOBp+DwD1fAHJKzS2S0kroWs64yf0V0m0RJeEguHfmQ2p85UhLY4+3/vKPUuaV" + System.lineSeparator() +
                "uRAP8L3q8uR0B2RiKZHJ6DZPHC3e/KREXfDEpb6K5Dr0ChShF+YUZMoWNCi0WPn+" + System.lineSeparator() +
                "i2cGYlqkhtSCg2gFPrKhw9A0ItxKd3M/Hv/jSmX3O/el4e8=" + System.lineSeparator() +
                "-----END CERTIFICATE-----" + System.lineSeparator() +
                "" + System.lineSeparator() +
                "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIERTCCAy2gAwIBAgICElowDQYJKoZIhvcNAQELBQAwKzEpMCcGA1UEAwwgY2Fj" + System.lineSeparator() +
                "a2xpbmcgY3J5cHRvZ3JhcGhlciBmYWtlIFJPT1QwHhcNMTYwMzIyMDI0NzUyWhcN" + System.lineSeparator() +
                "MjEwMzIxMDI0NzUyWjAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTCC" + System.lineSeparator() +
                "ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMIKR3maBcUSsncXYzQT13D5" + System.lineSeparator() +
                "Nr+Z3mLxMMh3TUdt6sACmqbJ0btRlgXfMtNLM2OU1I6a3Ju+tIZSdn2v21JBwvxU" + System.lineSeparator() +
                "zpZQ4zy2cimIiMQDZCQHJwzC9GZn8HaW091iz9H0Go3A7WDXwYNmsdLNRi00o14U" + System.lineSeparator() +
                "joaVqaPsYrZWvRKaIRqaU0hHmS0AWwQSvN/93iMIXuyiwywmkwKbWnnxCQ/gsctK" + System.lineSeparator() +
                "FUtcNrwEx9Wgj6KlhwDTyI1QWSBbxVYNyUgPFzKxrSmwMO0yNff7ho+QT9x5+Y/7" + System.lineSeparator() +
                "XE59S4Mc4ZXxcXKew/gSlN9U5mvT+D2BhDtkCupdfsZNCQWp27A+b/DmrFI9NqsC" + System.lineSeparator() +
                "AwEAAaOCAX0wggF5MBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgGG" + System.lineSeparator() +
                "MH8GCCsGAQUFBwEBBHMwcTAyBggrBgEFBQcwAYYmaHR0cDovL2lzcmcudHJ1c3Rp" + System.lineSeparator() +
                "ZC5vY3NwLmlkZW50cnVzdC5jb20wOwYIKwYBBQUHMAKGL2h0dHA6Ly9hcHBzLmlk" + System.lineSeparator() +
                "ZW50cnVzdC5jb20vcm9vdHMvZHN0cm9vdGNheDMucDdjMB8GA1UdIwQYMBaAFOmk" + System.lineSeparator() +
                "P+6epeby1dd5YDyTpi4kjpeqMFQGA1UdIARNMEswCAYGZ4EMAQIBMD8GCysGAQQB" + System.lineSeparator() +
                "gt8TAQEBMDAwLgYIKwYBBQUHAgEWImh0dHA6Ly9jcHMucm9vdC14MS5sZXRzZW5j" + System.lineSeparator() +
                "cnlwdC5vcmcwPAYDVR0fBDUwMzAxoC+gLYYraHR0cDovL2NybC5pZGVudHJ1c3Qu" + System.lineSeparator() +
                "Y29tL0RTVFJPT1RDQVgzQ1JMLmNybDAdBgNVHQ4EFgQU+3hPEvlgFYMsnxd/NBmz" + System.lineSeparator() +
                "LjbqQYkwDQYJKoZIhvcNAQELBQADggEBAKvePfYXBaAcYca2e0WwkswwJ7lLU/i3" + System.lineSeparator() +
                "GIFM8tErKThNf3gD3KdCtDZ45XomOsgdRv8oxYTvQpBGTclYRAqLsO9t/LgGxeSB" + System.lineSeparator() +
                "jzwY7Ytdwwj8lviEGtiun06sJxRvvBU+l9uTs3DKBxWKZ/YRf4+6wq/vERrShpEC" + System.lineSeparator() +
                "KuQ5+NgMcStQY7dywrsd6x1p3bkOvowbDlaRwru7QCIXTBSb8TepKqCqRzr6YREt" + System.lineSeparator() +
                "doIw2FE8MKMCGR2p+U3slhxfLTh13MuqIOvTuA145S/qf6xCkRc9I92GpjoQk87Z" + System.lineSeparator() +
                "v1uhpkgT9uwbRw0Cs5DMdxT/LgIUSfUTKU83GNrbrQNYinkJ77i6wG0=" + System.lineSeparator() +
                "-----END CERTIFICATE-----" + System.lineSeparator() + System.lineSeparator();

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_1, QUERY_ACCT_RESPONSE_BODY_1, QUERY_ACCT_REPLAY_NONCE_1, ACCT_LOCATION, 200)
                .updateAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_2, QUERY_ACCT_RESPONSE_BODY_2, QUERY_ACCT_REPLAY_NONCE_2, ACCT_PATH, 200)
                .orderCertificateRequestAndResponse(ORDER_CERT_REQUEST_BODY, ORDER_CERT_RESPONSE_BODY, ORDER_CERT_REPLAY_NONCE, ORDER_LOCATION, 201, false)
                .addAuthorizationResponseBody(AUTHZ_URL, AUTHZ_REQUEST_BODY, AUTHZ_RESPONSE_BODY, AUTHZ_REPLAY_NONCE)
                .addChallengeRequestAndResponse(CHALLENGE_REQUEST_BODY, CHALLENGE_URL, CHALLENGE_RESPONSE_BODY, CHALLENGE_REPLAY_NONCE, CHALLENGE_LOCATION, CHALLENGE_LINK, 200, false, VERIFY_CHALLENGE_URL, CHALLENGE_FILE_CONTENTS, AUTHZ_URL, UPDATED_AUTHZ_RESPONSE_BODY, UPDATED_AUTHZ_REPLAY_NONCE)
                .addFinalizeRequestAndResponse(FINALIZE_RESPONSE_BODY, FINALIZE_REPLAY_NONCE, FINALIZE_URL, FINALIZE_LOCATION, 200)
                .addCheckOrderRequestAndResponse(CHECK_ORDER_URL, CHECK_ORDER_REQUEST_BODY, CHECK_ORDER_RESPONSE_BODY, CHECK_ORDER_REPLAY_NONCE, 200)
                .addCertificateRequestAndResponse(CERT_URL, CERT_REQUEST_BODY, CERT_RESPONSE_BODY, CERT_REPLAY_NONCE,  200)
                .build();
    }

    private ClientAndServer setupTestObtainCertificateWithUnsupportedPublicKey() {
        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"faxV5ndBJsE\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"meta\": {" + System.lineSeparator() +
                "    \"caaIdentities\": [" + System.lineSeparator() +
                "      \"happy-hacker-ca.invalid\"" + System.lineSeparator() +
                "    ]," + System.lineSeparator() +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator() +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String NEW_NONCE_RESPONSE = "yTHivfhVul8gJCCi0zflLw0NcZm2XCq3D0f2OZKL_9Y";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJpVk5xSnVoM3VvWW5xdkNfZGtYQzRFMDN4R292eTdLUjAwd3M4QUwwcHJWcktzajhnZFdhWjBLZkZ1Q0NUaUtMU1BhNVQ0ZnRWNFdia2l0djFMa0JWU29Wd1hqSDE0bFpIMWFHYkptR1lCX3pSOV9uVzZJTzRVb1RGc2Vqb3paN05kNW8waVFpQWpyRjBmMDhGVC1xYS1TVVZiVk16dkNnQW16SjJFVlhzOXdOQ2pzSVRnNGh3eDdZRzl5eHRhZjFoT0hkV1dKVWtwZ0hnQkVfclpZT1B5YVNlb2JyeE5mMllxVmhFNWM2ZjhrZUhYdnU2dnprODctZVNLWXlndk9hSW1YOUhFbFZhQXRVcnI0S3hFV3VvUDdNRzZCV0s2TDVpam9Db0VMQjBqM0w2UHNuXzM1VnMxQi05OFR6SFZqYU1sU1NGV20xQjdtS0NzNGZMeE1pRXcifSwibm9uY2UiOiJ5VEhpdmZoVnVsOGdKQ0NpMHpmbEx3ME5jWm0yWENxM0QwZjJPWktMXzlZIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"Dd24E9F3IAg_r4-YnXk18RbLsfak0scs6xAgfrx04jqowBLT6sUVDH2Gg7K4H7GOJc7mBnMvdVMAdEsEwgvRg3zyFgTdsVvCVRV56G13dCsRszwSCPReocirhTyNeL6LFVzK2xniN6yncR_FUPulCDCytDtOryeinEmOOIl0ABR-PXV0rfGMBRmyZGsFEwix6b5VMsdhXHDxbQoc5HUfKNWW0CV0i_G_3BQFRJ7lgt3JG0a1aw3ml1X1FzCNyarnFkzBQOp3RqN7ODa3TxXSVtMZu9gXyGfJ-YBSxNq83DxcocHPWbBoUZE8tug3-IH600u3FIWulA7rSn98SnUajA\"}";

        final String QUERY_ACCT_RESPONSE_BODY= "";

        final String QUERY_ACCT_REPLAY_NONCE = "PTG0ESBbbcEJlcyrfjCLu8YyJwYJ9lGGgD9Af97Vo4Q";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/3";


        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiUFRHMEVTQmJiY0VKbGN5cmZqQ0x1OFl5SndZSjlsR0dnRDlBZjk3Vm80USIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1vcmRlciJ9\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoiaXJhY2x6bGNxZ2F5bXJjLmNvbSJ9XX0\",\"signature\":\"OJKxVKwVDKHZF7b9kf8GVUrnfWlpjhS1m2SmHJPTJc-Zpgniv2-x6ZPnNLswYSxHIndywsSLf2BmeNBzZpDiFFJOgZU9GYeSezq6XVH-arfNCkBPa73A33PoOC3Ts_2Pd2P5fuJ2WdsLVRmpIWixPFyjedRfMuv3eVWxKmmgCc1GE-g5n6cULTrdVyEayabwfpHkr7vTOMw9X_dFYuvWvkA6F1BCnoNSxxu8Vh-UWpUS_77OhCJd9QQnL5iewjweRacY9ng2UgtsyOMnnrFILuI7B9fMv3TgaFEtwQufqmAEcP6hS29yLFBfsbUV1FcpT0UDxUlLUPiTTj1BeWjtkQ\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T23:06:59.386130633Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/3/12\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "Xb79RAHpMC0bOQXPPQMhLepRKc2JCrI0OXoz71P7PTQ";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/3/12";

        final String AUTHZ_REPLAY_NONCE = "3y0I9NLsV-UXaNdFRsnv4mYdIzcCDXOzJIgik85XVgg";
        final String AUTHZ_URL = "/acme/authz/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE";
        final String AUTHZ_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiWGI3OVJBSHBNQzBiT1FYUFBRTWhMZXBSS2MySkNySTBPWG96NzFQN1BUUSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2F1dGh6L1gzYWJyVkZIWXhPTlNZdTAxTkdGUDhzNDlMdDJyZVNtUk9VOHRKRzcxVEUifQ\",\"payload\":\"\",\"signature\":\"LpeAtFiB2hxRdMRN0QDf016QnZwj2m81Dyb2kVdpipvz7RCMkheoc7I1vkuGbM_2LlenvBHxmHhSkSHSj3lB0IVdqmg4y5d25YgwJEgVDVZdJ0nwkJBOKp1bXDOO2HJsN8do8RT3MB2NvXJVLWyvMbHIFlKpI0ZJALaAor8U6Qo_LhXcJ7tyATsydgim4s9GvMqQbOjXoaTx0X-XIbIpYbAms6D1N7xpt1zCVv6Z7hkFaxTJsG8vmzluNDoNKYIWP8vzA_bZSoQ4yLa_t_TPVzTQ8yKBLCRDeUkJmDaB8qwd1Ykc6tRwiHGID6GE3q1z-H1fSw0na8PbXunem1uZ7g\"}";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-11-30T23:06:59Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/33\"," + System.lineSeparator() +
                "      \"token\": \"iE2BAsOSYCgCALb-0nx1M2UDmXBkMhv07ssGmUK4kak\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/34\"," + System.lineSeparator() +
                "      \"token\": \"1rcnRMNMzxxH1AHQvEcc2tVZw9QDHRBeU3v2euASMDo\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/35\"," + System.lineSeparator() +
                "      \"token\": \"pDWP9Ja1fNgl-GPThTK7UN90ZXt5F1jwY2mG1WjK3I0\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/36\"," + System.lineSeparator() +
                "      \"token\": \"BbXZvuNCX3sbW_MQd-4vvLZzd6llkKP8tG-YNqcVBbA\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMyIsIm5vbmNlIjoiM3kwSTlOTHNWLVVYYU5kRlJzbnY0bVlkSXpjQ0RYT3pKSWdpazg1WFZnZyIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2NoYWxsZW5nZS9YM2FiclZGSFl4T05TWXUwMU5HRlA4czQ5THQycmVTbVJPVTh0Skc3MVRFLzM2In0\",\"payload\":\"e30\",\"signature\":\"VMZBhgSpGBVvs1rp2ZVCoga1WdbngKBJQ3DjjYak532XZvoGrVPFCIj30s-ZJeowy0gZPxHO3CT1AqhXg85jdtStmd58yveMpgOzLVeyqUtevKL9QSLY_FBj2SEUWQa_CbzNBPI2nKsAVxJQm_guZBcmFVGfadNhi3owP0w_PSagVpYzqU8F2EtJIi4EPKrG5f3o1I8lxXNY80Fc0e3JJpZ-dMxT667VI168XtH_CB47q8T9fMO1VCFBe9Kx-GXH5xc8hkX_S04ThLb52sGHpoFolWaQTvE-91BM1K-tjTXppspDnNzlC-obOi40M34BstdIYx3IOEMC56j3jVUV-A\"}";
        final String CHALLENGE_URL = "/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/36";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/36\"," + System.lineSeparator() +
                "  \"token\": \"BbXZvuNCX3sbW_MQd-4vvLZzd6llkKP8tG-YNqcVBbA\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "mQo66pQBT1bIF2vKKEdGcgL0GecJGPk8HWNB8NduV3o";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/36";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/BbXZvuNCX3sbW_MQd-4vvLZzd6llkKP8tG-YNqcVBbA";
        final String CHALLENGE_FILE_CONTENTS = "BbXZvuNCX3sbW_MQd-4vvLZzd6llkKP8tG-YNqcVBbA.952Xm_XyluK_IpyAn6NKkgOGuXbeWn8qoo0Bs9I8mFg";

        final String UPDATED_AUTHZ_REPLAY_NONCE = "uP6PqDT0I_vf0OMVncdp-T1zmxxzVhskIrg4Nz191B4";
        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-12-23T23:06:59Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/33\"," + System.lineSeparator() +
                "      \"token\": \"iE2BAsOSYCgCALb-0nx1M2UDmXBkMhv07ssGmUK4kak\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/34\"," + System.lineSeparator() +
                "      \"token\": \"1rcnRMNMzxxH1AHQvEcc2tVZw9QDHRBeU3v2euASMDo\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/35\"," + System.lineSeparator() +
                "      \"token\": \"pDWP9Ja1fNgl-GPThTK7UN90ZXt5F1jwY2mG1WjK3I0\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/X3abrVFHYxONSYu01NGFP8s49Lt2reSmROU8tJG71TE/36\"," + System.lineSeparator() +
                "      \"token\": \"BbXZvuNCX3sbW_MQd-4vvLZzd6llkKP8tG-YNqcVBbA\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://172.17.0.1:5002/.well-known/acme-challenge/BbXZvuNCX3sbW_MQd-4vvLZzd6llkKP8tG-YNqcVBbA\"," + System.lineSeparator() +
                "          \"hostname\": \"iraclzlcqgaymrc.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"172.17.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"172.17.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_URL = "/acme/finalize/3/12";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"urn:ietf:params:acme:error:malformed\"," + System.lineSeparator() +
                "  \"detail\": \"Error finalizing order :: invalid public key in CSR: unknown key type *dsa.PublicKey\"," + System.lineSeparator() +
                "  \"status\": 400" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "l2LQJWlhk9_JtZNqJ2kfS6X4_9lCQY3KAwiWxIRpp94";
        final String FINALIZE_LOCATION = "";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY, QUERY_ACCT_RESPONSE_BODY, QUERY_ACCT_REPLAY_NONCE, ACCT_LOCATION, 200)
                .orderCertificateRequestAndResponse(ORDER_CERT_REQUEST_BODY, ORDER_CERT_RESPONSE_BODY, ORDER_CERT_REPLAY_NONCE, ORDER_LOCATION, 201, false)
                .addAuthorizationResponseBody(AUTHZ_URL, AUTHZ_REQUEST_BODY, AUTHZ_RESPONSE_BODY, AUTHZ_REPLAY_NONCE)
                .addChallengeRequestAndResponse(CHALLENGE_REQUEST_BODY, CHALLENGE_URL, CHALLENGE_RESPONSE_BODY, CHALLENGE_REPLAY_NONCE, CHALLENGE_LOCATION, CHALLENGE_LINK, 200, false, VERIFY_CHALLENGE_URL, CHALLENGE_FILE_CONTENTS, AUTHZ_URL, UPDATED_AUTHZ_RESPONSE_BODY, UPDATED_AUTHZ_REPLAY_NONCE)
                .addFinalizeRequestAndResponse(FINALIZE_RESPONSE_BODY, FINALIZE_REPLAY_NONCE, FINALIZE_URL, FINALIZE_LOCATION, 400, true)
                .build();
    }

    private ClientAndServer setupTestRevokeCertificateWithoutReason() {

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"meta\": {" + System.lineSeparator() +
                "    \"caaIdentities\": [" + System.lineSeparator() +
                "      \"happy-hacker-ca.invalid\"" + System.lineSeparator() +
                "    ]," + System.lineSeparator() +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator() +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"," + System.lineSeparator() +
                "  \"yNEulSQUUIA\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String NEW_NONCE_RESPONSE = "R_4ZVLQ2G3kNzArEuHmve0UvjR1XSxp8B2g6mOBCskE";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJwdUwtV2NNWVVKMkFqZHkxVXNVZ056am42ZWNEeGlXZDdOR1VHcTI2N1NPTHdoS2pTV1dNd2tvcGZjZzVWTWpQSldFRTM4SUlYeWpXNW5GS0NxRkFJZjNabGloXzFTTGNqZ1ZGYmlibi1vTUdGTFpzOWdncjJialJHSnNic0pRSU9LbWdWczJ5M2w1UmNJeUYyTS1VT3g0R3RBVVFKc1lpdHRjaEJMeHFqczBTQmpXZHRwV3phWDRmd1RDeng0OFJYdVpoa3lfbUtBeUtiaEFZbklHZERoY1ZJWnNmZjZ6ekVNMWJwSkVENk9CWmg2cHlQLU4wa094Y0dtUFBDSE1mME16d2puSzhWckZQRWFJSWZRQWJVQzFyVGF1aXFaWDdnbEVuTjJrWXFPd2w4ZzNuZjVmYlg2c1V1RFUxNWZWMGNtZFV0aHk4X0dIeUUycWR6alBSTHcifSwibm9uY2UiOiJSXzRaVkxRMkcza056QXJFdUhtdmUwVXZqUjFYU3hwOEIyZzZtT0JDc2tFIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"N_86Lf5tGJOHfdAcQWnmO-ha-8Ulu7yHIJWrP2CN3eSpEc2BgjRP00U-SiwJ0vNv0RftbtK-REXSAwRVvsPOULruZPG_3Dd9GUYpYvvVhklXa3d9o0-X-Bg-xJe6QfNeLmcS5KQ9CFEkO_EvOFeE9BgLnDmEpx-1VsJSKwVkyQXl2CZFqap_wsPH1UKnwbWyP6tAnCHh8p6_n8_oqoLeilal0KT3hAPmCj2qT3PF4ABRJVk3gUY-MqLtawPl0VJ9gOUvbp5PKmi31LHAzKU6105Y9O5vccPkL6AJCskbJdoos8VkV_fk_Ip4kyPcM-q9PAx2P5uq9fg-_SufSaE-8g\"}";

        final String QUERY_ACCT_RESPONSE_BODY= "";

        final String QUERY_ACCT_REPLAY_NONCE = "n13g7hLxpXHWocmPsq_Qx-i5nvJF1OzSqPQ7naadMZw";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/384";

        final String REVOKE_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzg0Iiwibm9uY2UiOiJuMTNnN2hMeHBYSFdvY21Qc3FfUXgtaTVudkpGMU96U3FQUTduYWFkTVp3IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvcmV2b2tlLWNlcnQifQ\",\"payload\":\"eyJjZXJ0aWZpY2F0ZSI6Ik1JSUZaekNDQkUtZ0F3SUJBZ0lUQVAtWWVJSDJiVjRkWDZhMXVOb3JxNk5PWVRBTkJna3Foa2lHOXcwQkFRc0ZBREFmTVIwd0d3WURWUVFEREJSb01uQndlU0JvTW1OclpYSWdabUZyWlNCRFFUQWVGdzB4T0RBME16QXhPREEyTkRCYUZ3MHhPREEzTWpreE9EQTJOREJhTUI0eEhEQWFCZ05WQkFNVEUydHNhV3Q2Wld0dGJHcDFkM2hyYlM1amIyMHdnZ0VpTUEwR0NTcUdTSWIzRFFFQkFRVUFBNElCRHdBd2dnRUtBb0lCQVFDWlhuRVBXRXlYRDl0RUpyejB5T3hpNTRuNWR0RTBsOEJzRkt2OGk0bXJmdlMtYXhiaF9OUzdMb3Y0anN5Zy0tLVN6am9xQ3pJbkY4OExQVWxGanFPVlVwYkdhWjM1MWlYN1FkN216bXBsdkFSY2RhdnZXVXRrdjRXN2ZQOGF0N3VsODJaanBmc0VrS2pGcXJ1czZkZFNfQkxXeGNxblhoS3NrdUstZ3MzZ2F3SjFuTU93b01VeGJpYm5EamdpQ1JIVm9wRm5WS0NhMUttWG42MkFBTmUySnNSQTZySlJFZFE0TnE4MVRBZFpieGwyTXdjVnFUY1pYX1BBTVB5RlBCM1EtS0o0VlhPR3R2SVNTb2J1cThUaHFvWXJzeGJ6dXcwMnZYdnd4RzZPaUs3UlFobm9wOHNpdWNIZ0RsaUVlQ25BYWNkZFdRalBieTh0ajBEZzlOTTNBZ01CQUFHamdnS2JNSUlDbHpBT0JnTlZIUThCQWY4RUJBTUNCYUF3SFFZRFZSMGxCQll3RkFZSUt3WUJCUVVIQXdFR0NDc0dBUVVGQndNQ01Bd0dBMVVkRXdFQl93UUNNQUF3SFFZRFZSME9CQllFRk5xM0VGWmk3dDhYT1Z0aUw4YjBjRGJ3a2szWU1COEdBMVVkSXdRWU1CYUFGUHQ0VHhMNVlCV0RMSjhYZnpRWnN5NDI2a0dKTUdRR0NDc0dBUVVGQndFQkJGZ3dWakFpQmdnckJnRUZCUWN3QVlZV2FIUjBjRG92THpFeU55NHdMakF1TVRvME1EQXlMekF3QmdnckJnRUZCUWN3QW9Za2FIUjBjRG92TDJKdmRXeGtaWEk2TkRRek1DOWhZMjFsTDJsemMzVmxjaTFqWlhKME1CNEdBMVVkRVFRWE1CV0NFMnRzYVd0NlpXdHRiR3AxZDNocmJTNWpiMjB3SndZRFZSMGZCQ0F3SGpBY29CcWdHSVlXYUhSMGNEb3ZMMlY0WVcxd2JHVXVZMjl0TDJOeWJEQmhCZ05WSFNBRVdqQllNQWdHQm1lQkRBRUNBVEJNQmdNcUF3UXdSVEFpQmdnckJnRUZCUWNDQVJZV2FIUjBjRG92TDJWNFlXMXdiR1V1WTI5dEwyTndjekFmQmdnckJnRUZCUWNDQWpBVERCRkVieUJYYUdGMElGUm9iM1VnVjJsc2REQ0NBUVFHQ2lzR0FRUUIxbmtDQkFJRWdmVUVnZklBOEFCM0FCYm9hY0hSbGVyWHdfaVhHdVB3ZGdIM2pPRzJuVEdvVWhpMmczOHhxQlVJQUFBQll4ZnpJLVVBQUFRREFFZ3dSZ0loQUlIMEtzUEJjdTBWSUZuSWswdHc0QVZwbW9vMl9jT2ZyRzdDLXN6OGZNMFRBaUVBa3NKbXF4cXlUWGFXZDc5dVNKQlNBTWJWNGpmdHVqbktCY2RhT1JCWFZMWUFkUURkbVRUOHBlY2tnTWxXYUgyQk5Ka0lTYkpKOTdWcDJNZThxejljd2ZOdVpBQUFBV01YOHlQbEFBQUVBd0JHTUVRQ0lGS2paSFc1YkhTZnF1ZXo4TXlWXzhsRVU4TzExQWczVWVyMEFraVVfT255QWlBSkQ2a3FsbVhfVnhOTi1MZ3o1TEJFalFvc2hReURfMFhOOXdDM2FMMFozREFOQmdrcWhraUc5dzBCQVFzRkFBT0NBUUVBWndQMGMyTjdReTJBV3R2cDRtd25zZ2pWMmMyY3IzMFJCTTRNNkZCblM5QlEwSU13LWRMT3BhcVAxNEM0N1BYa2M4ZmVyRmZZTFVsWW9NWkFIMHlscUFUemFxd3dnZ0V4ZmF3UlhKM2s4Z1BZWHFuSXdtdDFMNkpNZ0RuZjd6MlJxci1sTlZJNUg4REFpbnFDSjJLRmdtVHh2U1JudHdkYkh2X1J6TUFJRWhTOVp2SnpQOHRRWHBjclRHeWxha0VqWndnV1lOQWs4WTdRcnhfMWhoM0E2YWpXWWNhb1FUTzJVOS1pMThaNnB2TzFwRlZSZEo0ZUozamJrVzR0UUNJVDkxeGtsWFlfT1gyZF9qc0Z3TzFBaTNEV19Eb1ViMGtPUmFaMkswZjZJZF9BczREOU5USDVXSDdEX2FrMm42T2l2V2dpTHBqZ0pxRUgzNWtPN0hWdGNnIn0\",\"signature\":\"U6822aPK85QdIwsJH6ekvg-LkmvjBlLmJmk8OViNYr79GNTbu3LBO-x9p2_R3deKotShjYE3WpcmzqcW9xpHg-FRSWgcIFczS_0EAX9d-OhI4LFzQroHyTXcEev0OruiMq_4tZrGjy1CFFfdaaXyRbpDqnP4vC_Tq2KyUHhV6LbhHhg11qaQjov3z-0jMM6eKGybmne6yDrE2lG6uKZscWzYqwGi5gkQ_iBHCb_qzYYphYs8IZLPTt6T8PAIDmRpsRCHXzgDCk0QVhj-Gl7y2H2xEn_BknKT-oPa33zSICovn5cR6utf788FRz9oh8t7tIpOAvVStwVSrb6BV6WOUQ\"}";
        final String REVOKE_CERT_REPLAY_NONCE = "poBc-xx1Oxnprg_hgWFZI_0Ji-4qgEpAnGrAdxEP6sU";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY, QUERY_ACCT_RESPONSE_BODY, QUERY_ACCT_REPLAY_NONCE, ACCT_LOCATION, 200)
                .addRevokeCertificateRequestAndResponse(REVOKE_CERT_REQUEST_BODY, REVOKE_CERT_REPLAY_NONCE, 200)
                .build();
    }

    private ClientAndServer setupTestRevokeCertificateWithReason() {

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"FpVd7yM-nVU\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"meta\": {" + System.lineSeparator() +
                "    \"caaIdentities\": [" + System.lineSeparator() +
                "      \"happy-hacker-ca.invalid\"" + System.lineSeparator() +
                "    ]," + System.lineSeparator() +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator() +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String NEW_NONCE_RESPONSE = "-mlJhcox_6FFuDwNhcmL06FWD6uL7K7lam9Jel-MqqM";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJwdUwtV2NNWVVKMkFqZHkxVXNVZ056am42ZWNEeGlXZDdOR1VHcTI2N1NPTHdoS2pTV1dNd2tvcGZjZzVWTWpQSldFRTM4SUlYeWpXNW5GS0NxRkFJZjNabGloXzFTTGNqZ1ZGYmlibi1vTUdGTFpzOWdncjJialJHSnNic0pRSU9LbWdWczJ5M2w1UmNJeUYyTS1VT3g0R3RBVVFKc1lpdHRjaEJMeHFqczBTQmpXZHRwV3phWDRmd1RDeng0OFJYdVpoa3lfbUtBeUtiaEFZbklHZERoY1ZJWnNmZjZ6ekVNMWJwSkVENk9CWmg2cHlQLU4wa094Y0dtUFBDSE1mME16d2puSzhWckZQRWFJSWZRQWJVQzFyVGF1aXFaWDdnbEVuTjJrWXFPd2w4ZzNuZjVmYlg2c1V1RFUxNWZWMGNtZFV0aHk4X0dIeUUycWR6alBSTHcifSwibm9uY2UiOiItbWxKaGNveF82RkZ1RHdOaGNtTDA2RldENnVMN0s3bGFtOUplbC1NcXFNIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"lztzTXBmbrxXGMspfEetHDGKdZ2NrpQTioysqHIa9aaL5dy8bPmKZ_Vmz68-xnUJcjK-5FMCn5vtYEKAJlJ7W3wVYzthcVuYlv-b6FNw3IYsdSSHMr5RLm0rSt9EwYd-BI4bCoT7dioYpCMHzTrd-3X8QjDS4fx1o6D-po_Hwkt4PWx5Yoo9ExlykM5cHOQlCQENPk3Pn0M4_8XkfH1QTvVTIm4A4lbo_Eko1aU9PgvWbNsqkEhRzH7rBb5FUlxFgRoSHuTJwn6uJL-H0cfYQUn-J5JyD5C-P8su3M7NoAXCj0vy_84TziHMxe1C8fI-A64M6CtlL9qGm5MwPgv8Gg\"}";

        final String QUERY_ACCT_RESPONSE_BODY= "";

        final String QUERY_ACCT_REPLAY_NONCE = "zbQR7CL_GSx0oydZ0AVoNEh7omY_XONdWFpYOfeFVQc";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/384";

        final String REVOKE_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzg0Iiwibm9uY2UiOiJ6YlFSN0NMX0dTeDBveWRaMEFWb05FaDdvbVlfWE9OZFdGcFlPZmVGVlFjIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvcmV2b2tlLWNlcnQifQ\",\"payload\":\"eyJjZXJ0aWZpY2F0ZSI6Ik1JSUZaekNDQkUtZ0F3SUJBZ0lUQVBfNDBNVEh3LWw1M3lpOWVOMnptclFkX1RBTkJna3Foa2lHOXcwQkFRc0ZBREFmTVIwd0d3WURWUVFEREJSb01uQndlU0JvTW1OclpYSWdabUZyWlNCRFFUQWVGdzB4T0RBME16QXhPRFF4TURoYUZ3MHhPREEzTWpreE9EUXhNRGhhTUI0eEhEQWFCZ05WQkFNVEUyaHRlSFJ1ZFd0c2JHaDRlR3hpYUM1amIyMHdnZ0VpTUEwR0NTcUdTSWIzRFFFQkFRVUFBNElCRHdBd2dnRUtBb0lCQVFDWUpyX3BaQkNTeV9LZHdLd1c0TDdyNnhWYVB1R0dna1JKY3lnTE5EWUhNd2JObm9zM3FnckpEMk0tRW5HOWlrSmlIRzd5VUtfVHRGNWZrVFA3UEROUzNlallkVTl1RTFHeTM1VTcyVGVzbVpzSC1aNy11NHJsc1JxdzVXcURDUjBGeW1PR0xuUEpVa3hGN29PRlFHc1lwZ3h3T1JVV0g5TlBEUzZTT3RTWF9XbUJ0S015VGM5QW9GRjBlRHM3NlBmOWl5eXZONjh4ejF6Y3g5aENnbDB5ZVNXTFhUNHV1SUJibHIxNXZhdzdCVVFNMnBGdE9aNGFIcWRiTDUtQ05TOWVxNUk2WTRpMW1yQVBEWklkN2xMOHAxY2tQLXI0dlh0a0VVdmxEaXFNMzdiRlB3enZDMWVVeGtOanNTdnQ0OGh4TTBtMU82cHZhTVB2Qm1CWGxHOUZBZ01CQUFHamdnS2JNSUlDbHpBT0JnTlZIUThCQWY4RUJBTUNCYUF3SFFZRFZSMGxCQll3RkFZSUt3WUJCUVVIQXdFR0NDc0dBUVVGQndNQ01Bd0dBMVVkRXdFQl93UUNNQUF3SFFZRFZSME9CQllFRkl3VXBFcGpUbmhUTl9XN3JlckkwT3V2alVMck1COEdBMVVkSXdRWU1CYUFGUHQ0VHhMNVlCV0RMSjhYZnpRWnN5NDI2a0dKTUdRR0NDc0dBUVVGQndFQkJGZ3dWakFpQmdnckJnRUZCUWN3QVlZV2FIUjBjRG92THpFeU55NHdMakF1TVRvME1EQXlMekF3QmdnckJnRUZCUWN3QW9Za2FIUjBjRG92TDJKdmRXeGtaWEk2TkRRek1DOWhZMjFsTDJsemMzVmxjaTFqWlhKME1CNEdBMVVkRVFRWE1CV0NFMmh0ZUhSdWRXdHNiR2g0ZUd4aWFDNWpiMjB3SndZRFZSMGZCQ0F3SGpBY29CcWdHSVlXYUhSMGNEb3ZMMlY0WVcxd2JHVXVZMjl0TDJOeWJEQmhCZ05WSFNBRVdqQllNQWdHQm1lQkRBRUNBVEJNQmdNcUF3UXdSVEFpQmdnckJnRUZCUWNDQVJZV2FIUjBjRG92TDJWNFlXMXdiR1V1WTI5dEwyTndjekFmQmdnckJnRUZCUWNDQWpBVERCRkVieUJYYUdGMElGUm9iM1VnVjJsc2REQ0NBUVFHQ2lzR0FRUUIxbmtDQkFJRWdmVUVnZklBOEFCMUFOMlpOUHlsNXlTQXlWWm9mWUUwbVFoSnNrbjN0V25ZeDd5clAxekI4MjVrQUFBQll4Z1NzYVFBQUFRREFFWXdSQUlnTUFGb19yNFl0aWNfc1lpVmxpaE10ZGZSZDFnclNYSUl1U2pwQzNZT1NOZ0NJRzdMWTlkMGl2cVV2czJ3Y0Z1Q0tNZkFsdDFNWTNvcjR6cGJlelFsNWpvREFIY0FGdWhwd2RHVjZ0ZkQtSmNhNF9CMkFmZU00YmFkTWFoU0dMYURmekdvRlFnQUFBRmpHQkt4cFFBQUJBTUFTREJHQWlFQTRYSmZVd3JVbkxWUGxRbF9IVVFxakRUVkFRdDJIN29BdXNrWUhiT3EtYTRDSVFEcGZwa3pNbkxudlNxay02QU5ZRWRKb0p5Q0M3M1ZwdHo0WG1MVnJMNHNtekFOQmdrcWhraUc5dzBCQVFzRkFBT0NBUUVBc1VEMUJ6M2NWQzA4NXF4a2VkYzJqd3FUSEk0UF9OaERrQVFmSGhrQ0VlaFoyVTVmRE1YWXFwZDh0UUluZUdoZU1ZTkQ4OWRFQXYyXzI5SXNGXzhKNC1uSURrLU1XQkFsQm43VUtES2xDbEdza0RDenJPajF6clJwOUtscTNLaElFSkUzT01nTGIyM3pNbERLeWRIcXA5OGtTc25hQmFoS1VlV3l1WXcxdmNwemZ3TjE0UG9xMW1jRnJWUFAxcWRBNG1NMTVFVHgyV0tZdTFWaWIySVVESmx2STNYbUg5SFR5ODZYRTRMNXFTd20xalJFbzZ5a3FDTmhSMHJMeHhHeXhDRldWVXVLNG9SaFR3YmF0VzEzR3JvSlhGdGNQeVVuRGJkSU9iRzIwLV9DME9ZMk9Rc1pWQTNWTC1IQ2c3ckt6QnZOSTNlaVkzVVNMYVBMM1I0dWhnIiwicmVhc29uIjoxMH0\",\"signature\":\"eP8PR2UEdU-HW7hM0XyeDWuPADRh_XKwmNM8QmowJzn4WLYkp-pHbnpGnID0aRTAjFQsvvPmkWIrNN9TMCgwfr5EqP7xoU1uGS3J6uNydZI4TyjGZaJ9v1I9sqb5Zw_Q5cht-vSMnxznmuEu3K_6jrDLq9x-U22sNFyA_aoqu5odPNJl_l2D2ZHaPbO19NjOfc2-mgBKR4y850oEzz8vKsFcPjtASFMoC3Ulyc2kDHuUeH9HL3W4DqvD0ygVhcbh5R9NRzwefj1h2YSD_8QJj20DprPSReJ_LxZTZzy3-oB3WWibLUaVS6xr0ZbMCPQSp_rTSRWpekWoM7vm_XwdCQ\"}";
        final String REVOKE_CERT_REPLAY_NONCE = "q4qaFhcWgftkiRaaeEZskz_fp9ue2OJGRDW3mYBGCNk";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY, QUERY_ACCT_RESPONSE_BODY, QUERY_ACCT_REPLAY_NONCE, ACCT_LOCATION, 200)
                .addRevokeCertificateRequestAndResponse(REVOKE_CERT_REQUEST_BODY, REVOKE_CERT_REPLAY_NONCE, 200)
                .build();
    }
}