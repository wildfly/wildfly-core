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
        operation.get(ElytronDescriptionConstants.PATH).set(resources + "/firefly-copy.keystore");
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
        operation.get(ElytronDescriptionConstants.ALIAS).set("ca");
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
        operation.get(ElytronDescriptionConstants.ALIAS).set("firefly");
        ModelNode firefly = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals("firefly", firefly.get(ElytronDescriptionConstants.ALIAS).asString());
        assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), firefly.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
        assertTrue(firefly.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).isDefined());
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
            operation.get(ElytronDescriptionConstants.PATH).set(resources + csrFileName);
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronDescriptionConstants.PATH).set(resources + replyFileName);
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronDescriptionConstants.PATH).set(resources + replyFileName);
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronDescriptionConstants.PATH).set(resources + replyFileName);
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronDescriptionConstants.PATH).set(resources + replyFileName);
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
            operation.get(ElytronDescriptionConstants.PATH).set(resources + certificateFileName);
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
            operation.get(ElytronDescriptionConstants.PATH).set(resources + certificateFileName);
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
        obtainCertificate(keyAlgorithmName, keySize, "qbqxylgyjmgywtk.com", alias, keyStore);
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
        operation.get(ElytronDescriptionConstants.PATH).set(resources + "/test-copy.keystore");
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
        operation.get(ElytronDescriptionConstants.PATH).set(resources + "/test-copy.keystore");
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
        operation.get(ElytronDescriptionConstants.ALIAS).set(aliasName);
        ModelNode alias = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals(aliasName, alias.get(ElytronDescriptionConstants.ALIAS).asString());
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
        operation.get(ElytronDescriptionConstants.PATH).set(resources + nonExistentFileName);
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
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).add("mailto:admin@example.com");
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
        final String ACCT_PATH = "/acme/acct/398";
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

        final String NEW_NONCE_RESPONSE = "jR7PUuYjk-llJPIByXxUrxN_4Ugkh8Y35DO_H74nCLk";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoNWlULUY4UzZMczJLZlRMNUZpNV9hRzhpdWNZTl9yajJVXy16ck8yckpxczg2WHVHQnY1SDdMZm9vOWxqM3lsaXlxNVQ2ejdkY3RZOW1rZUZXUEIxaEk0Rjg3em16azFWR05PcnM5TV9KcDlPSVc4QVllNDFsMHBvWVpNQTllQkE0ZnV6YmZDTUdONTdXRjBfMjhRRmJuWTVXblhXR3VPa0N6QS04Uk5IQlRxX3Q1a1BWRV9jNFFVemRJcVoyZG54el9FZ05jdU1hMXVHZEs3YmNybEZIdmNrWjNxMkpsT0NEckxEdEJpYW96ZnlLR0lRUlpheGRYSlE2cl9tZVdHOWhmZUJuMTZKcG5nLTU4TFd6X0VIUVFtLTN1bl85UVl4d2pIY2RDdVBUQ1RXNEFwcFdnZ1FWdE00ZTd6U1ZzMkZYczdpaVZKVzhnMUF1dFFINU53Z1EifSwibm9uY2UiOiJqUjdQVXVZamstbGxKUElCeVh4VXJ4Tl80VWdraDhZMzVET19INzRuQ0xrIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"RhnB-JjfGene8dHSJHI6z8X5ZEtx6YX6oJiZqhFa-n7ugx6RTP78ZHbWDZXtPN-pIBiQZLR0GOh-2AmqC33DOX-0-8IB3OTZFbSR08UdahBgQ9S-FqKuvBWTMVFiPo-_D9U1n7dQNbcRyNoY5AIJqIHuoXVrJ2Da5ROAKCHirFemr7ibqvTWNpRjyhpk306ORKb69x-XQ58p8cJooJSRxmZ2Nb2j7YnVqKvWVeT546RmM9ZtfYbZMNPIu7Zxcwb2nArsEbbnBG90kxTmZiruBsMZ6LcVKpNTQGqG64N5PWFKIPm5fVDi3CCoPnClFWzUtGaHDe8Z102akAaIm97tGA\"}";


        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "0ueRZEEzs5mQ82ENOemcg9ePFWSzQxHpTggVH2hA1uM";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/398";

        final String QUERY_ACCT_REQUEST_BODY_2 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiIwdWVSWkVFenM1bVE4MkVOT2VtY2c5ZVBGV1N6UXhIcFRnZ1ZIMmhBMXVNIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYWNjdC8zOTgifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"J__rbJZS5CUpy6rt86e8GEmqbRyrAQgnpkLjJrcSAFThwVqfifsxbCeihsiRv0NZkgLccgNV0ipa0amazsw-ZGXK2qZwead5HaAkUX4mHpFJPHEXHqIoFBTrWzofiP2EmyKNTE5y1FFpOdD_C28eB7hN_Hk15TwR96NZiY3AIniXPOv_BcclNjRS3FJt3TN3DqbNKI3GIUEsKRXfHKljlevSn5M9pTIzCBi5SlWygJqP6rywNSEniodmlOChNy4bhcct3iwWKLx5SMeaGsOj90O-DtZBq-ljpzjVA1K-hNEqyd1rWdrId-V3H56_qp2FO1Xf3nTQmogOlLRdP-ZMVA\"}";

        final String QUERY_ACCT_RESPONSE_BODY_2= "{" + System.lineSeparator() +
                "  \"id\": 398," + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"h5iT-F8S6Ls2KfTL5Fi5_aG8iucYN_rj2U_-zrO2rJqs86XuGBv5H7Lfoo9lj3yliyq5T6z7dctY9mkeFWPB1hI4F87zmzk1VGNOrs9M_Jp9OIW8AYe41l0poYZMA9eBA4fuzbfCMGN57WF0_28QFbnY5WnXWGuOkCzA-8RNHBTq_t5kPVE_c4QUzdIqZ2dnxz_EgNcuMa1uGdK7bcrlFHvckZ3q2JlOCDrLDtBiaozfyKGIQRZaxdXJQ6r_meWG9hfeBn16Jpng-58LWz_EHQQm-3un_9QYxwjHcdCuPTCTW4AppWggQVtM4e7zSVs2FXs7iiVJW8g1AutQH5NwgQ\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@example.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2018-06-22T11:02:28-04:00\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "ggf6K6xbBH8NK5ND69jGbM9TRMFO7HssBxzgWKDq0Js";

        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiJnZ2Y2SzZ4YkJIOE5LNU5ENjlqR2JNOVRSTUZPN0hzc0J4emdXS0RxMEpzIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LW9yZGVyIn0\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoiaW5sbmVzZXBwd2tmd2V3LmNvbSJ9XX0\",\"signature\":\"LbOPleWSCBaL1id8fw5fc5xm8eqFGLMOv_kwFXBr8QxfYF5RIDkW6Jsi-6gqCvDY7w5A7UcX-Fzcc2nUwAfwdHEOsUM9hkSBZkS54LmYWxZPLsIuBdTvkCSCSS94bqAnSnZXIch7seiJ4ZR1VQXVRnkMk5hD-_ipIOMYgVSwGqALz2NpW222QoY03LPaA5NkhnMdnIOia5aPzla5NQ9MXmOHBI5MIlTYIrYoccEXhM3jiqu1eDQohvMirUV76e2iAv8BovR8ys7fVC2AC36ithZNA-hRaxcHzJzXg9RGei4yOXcFoCHg6Xn1wygxshd2cc2Ov61TvTx9NUPmeDqK7g\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T18:27:35.087023897Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/398/186\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "u7eY2Z97yZOJTU82Z3nNa9-gFTe4-srSEECIUpa-63c";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/398/186";

        final String AUTHZ_URL = "/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T14:27:35-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/535\"," + System.lineSeparator() +
                "      \"token\": \"AYnykYJWn-VsPeMLf6IFIXH1h9el6vmJf4LuX3qitwI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/536\"," + System.lineSeparator() +
                "      \"token\": \"yLCOHl4TTraVOukhyFglf2u6bV7yhc3bQULkUJ1KWKI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537\"," + System.lineSeparator() +
                "      \"token\": \"6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiJ1N2VZMlo5N3laT0pUVTgyWjNuTmE5LWdGVGU0LXNyU0VFQ0lVcGEtNjNjIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2hhbGxlbmdlL0xKUkgzLWdqVVB0NVU1djh3SDFDaDNlRmN4dThVSy11b3RqdXRtNU5COXMvNTM3In0\",\"payload\":\"e30\",\"signature\":\"gCp9SSPiVyJNAQ9PUB8rsBVb5aceV-XrjyjtWiXa8JJ5kgN1V4T_KIz372FLd1Bn7w6wGt1uMND_KBHvHRkTTspPJZxfQaJPDLzHvnswPjLsKK1-KHH5Bz3wjXDN379H9rVD8Qo0ZWU2VrI3d5JeuN4VEh5-PpQHJifCCd1pe7eNyOtN2aAZK8Up6HdDU__1CqtBgxbjqVy2uzZ-YiQJptZ5Zp0KnxHbeOPFJlfStoJdl6Xw0B_AFggRiDMOjIU3A4NCAKFdZjo06nd4XNFHusmgPKZTymRmmA6qhfn-NUgVxxv-KhvwMWOJkG61KNyliSjvNUADEKTauc664rENhA\"}";
        final String CHALLENGE_URL = "/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537\"," + System.lineSeparator() +
                "  \"token\": \"6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "rjB3PBI-cOW5kdhoWhhruGwub0UnLVn_0PnlwdHP5aI";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8";
        final String CHALLENGE_FILE_CONTENTS = "6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8.w2Peh-j-AQnRWPMr_Xjf-IdvQBZYnSj__5h29xxhwkk";

        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-27T14:27:35-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/535\"," + System.lineSeparator() +
                "      \"token\": \"AYnykYJWn-VsPeMLf6IFIXH1h9el6vmJf4LuX3qitwI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/536\"," + System.lineSeparator() +
                "      \"token\": \"yLCOHl4TTraVOukhyFglf2u6bV7yhc3bQULkUJ1KWKI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537\"," + System.lineSeparator() +
                "      \"token\": \"6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://inlneseppwkfwew.com.com:5002/.well-known/acme-challenge/6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"," + System.lineSeparator() +
                "          \"hostname\": \"finlneseppwkfwew.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"127.0.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"127.0.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiJyakIzUEJJLWNPVzVrZGhvV2hocnVHd3ViMFVuTFZuXzBQbmx3ZEhQNWFJIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvZmluYWxpemUvMzk4LzE4NiJ9\",\"payload\":\"eyJjc3IiOiJNSUlFc3pDQ0Fwc0NBUUF3SGpFY01Cb0dBMVVFQXd3VGFXNXNibVZ6WlhCd2QydG1kMlYzTG1OdmJUQ0NBaUl3RFFZSktvWklodmNOQVFFQkJRQURnZ0lQQURDQ0Fnb0NnZ0lCQU1pSHBocGhaMTNRaG5zM0E5ZDVaSUx5MEpoNkNrZDhCNU1uTEJxbVl6UEwzWWR2VXFSSHdiZTBNWDVUQWRYdmR1eDZzNHBNbUg1a21xMUc2STFXU3pzcFBSN1g2TWZ6SGl5dEhPNlc1c0NidlpwVFRBYmw4NGMtZE5UckctdjJaMDA2dHRKWXp2NV9oYzI4UV82TnpZdkM1TWQ0T0QxelZXWnE5eU9KYjF0aHl4RUdwc0lIRVh4LUV4M3lWM2tpbmNfYnBvN2RUQjBndldIeDdiaHhLTUdmM1ktSm9mVUd0WHBWcTY1dDBfdnBQVHRIelBDbWppaG93WXc4S3RwVm5xcTdYVVMyUFFHU3UtcGNFYzBma0huSnNJUmdoRTRPR3E5THR5SkI3Zi1YLWdhRi14NzBDVEdoZ25JMFhaMlNBakYwTVdsVll2Mk9XNUQxOUM2YU1GeS12VlFqUU5VcjFUZGpvZVd3eEJEV0ZvNkwxTVZCcE5lUWVCZXJWT3lVelREZk1XLXBDSXJJUnMxV3lYVmV2WmJMc0pQTUZxRV9RNmV4bWdvU2NNVWUzN3gxenVCMV95VURZZkF4dVZVaVJfT3FUeHRfUUd1ZU8xVTJmQXpfRy0tN3VmbFhSQWw0OXZQM0hGc0ZlZHFKTXdNb2pvTDBSMWFvdEZBNGRSZ1dMc1l0Z3hqM1MtRVZYZWZScjJrTFFuTU1vbngwTjdOMTFuV09seGNSSDhOeld2NGMwYWh0TEliaUtmN2x2YTNMMklPN21RQlVjSHFkbm9pNndpbXJKTTZrQndIU0RyRDVXcVpTenQ0aFZTMmxtOTNEUDVGX2VuVEpDVnl4OUJVVUhoeDljeUxweEtyZ3BLcnk2OVp4MUdnbUUtTTNvVDNYMU4tdy1rMGViNVZSQWdNQkFBR2dVREJPQmdrcWhraUc5dzBCQ1E0eFFUQV9NQjRHQTFVZEVRUVhNQldDRTJsdWJHNWxjMlZ3Y0hkclpuZGxkeTVqYjIwd0hRWURWUjBPQkJZRUZOVmR6WV9nNTQxem80d0VIZUtJZjl5Mml5a1dNQTBHQ1NxR1NJYjNEUUVCREFVQUE0SUNBUUE3VTFYRll0RUM2QmxZY1BpUXhyQmg3eVoyNmM3cGw0U05IMFd4R2tQS1MwbVdDUlUzdm5zb05xblJjZXlxR2oyQmN4WnVUSms0U0IyOTJPc015Qm1sc0l3VWNYMHlodmpYT3RQLTRVUlBaS0o0WUdldmxlMW1xaGttZWpMT2R1bXEtNXFmajd5QXJsXzlETlUwam5UMDY1cHd6UkxCcS1VcXNtQXgxQ3czLW40LWE5VlIyemltNVFUUjZ1ZTF2NUJsTmxBTmI5eGZac3VHVXJ3akhsQ0NQU3FUWERKWnZNdGs4Y05SNUJtY21lZXFiZE9Yc1ZLSktaYTBhaW9ZcG9tT1pmREExQTZpT3RuNzRKc2tWNHBraEVmZUc1a0FEUnBJbmtkWkNIMlB6V1JvSWJRSmViNXY5RzU4aENyS182LWVaV1FjQW5sMkxTcDl5T0JkT2FPOGF4OGRwQUZYQVNyOVdKTFNWcHRuaDVKNnlaWER0eXFiYnctRXVpbjZTektmdTRYWlhUaHNnSmVfeWlncmNpZjRIQnNGc0wwWGFaTXUyY3U3cV9jaHM4bkJpOG5VM0F4RmZoVFZIeURjYkxLa1Z2Qm05WUZFUlFrWEl1WDZid1U0clhWLVFtcUpGNzJWV2ItZ0R0d040UnotWlFzaDZxS01HNTI3Mi15NWZaTjZMQkpTZTJ5WWpBbHhiM2xzZ0hNbFRKYzlPMkhnUE9udkREWmhCYUdPc1EtbTdha3ZJcWc2ZFVuYTB5c2t3MmI5WVd3TDRoMzdPT2JHZVJ1T2t4QTY5Z1N0bmZ1aFIxbGItQVRrMmZYaHFQRlAteGxIT2JtNUh2ekZKVE5ZRU5Rc3VnaFRIY3hoNERUM0F1dVo3aWZtVjQtNHVWTGdKVkdsVHdFcXJTS29PQSJ9\",\"signature\":\"NNtlMV9rfVtUvxgK9ucvWfXxwynELu5KeB-CGYrrM2VavfAeHWYDCr5Hs8Y3_UyOXSwXANUcVR4VjJnfoxsVn4TM-Zd0T6osmorVTIZGaI-xsWyxBckZ5g6xb7AGE6VLYKvCR4if_DhYq9M31Ge7l95rUTgxPg6xQbibGkbUfT1K-CcNetPWfCtQOhEf4V4jIO78MZUKuyb7eQXdWJqP5-ed4UAuqoclKqJ259zxrs1QcqbJGVjV5OJOpL-4odc086dkHvKPEkKIG3s-vFeYcToAVerR1rmIXPFenDu_JN9qqYtuyMrpfT_AhSavyN-DMaFKGvZ6YISQ5A4gq4ESJQ\"}";
        final String FINALIZE_URL = "/acme/finalize/398/186";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T18:27:35Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/398/186\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ffba1352e17b57c2032136e6729b0c2ebac9\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "CZleS8d9p38tiIdjbzLa1PRJIEFcbLevx_jtlZQzYbo";
        final String FINALIZE_LOCATION = "http://localhost:4001/acme/order/398/186";

        final String CHECK_ORDER_URL = "/acme/order/398/186";

        final String CHECK_ORDER_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T18:27:35Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/398/186\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ffba1352e17b57c2032136e6729b0c2ebac9\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CERT_URL = "/acme/cert/ffba1352e17b57c2032136e6729b0c2ebac9";

        final String CERT_RESPONSE_BODY = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIGZjCCBU6gAwIBAgITAP+6E1Lhe1fCAyE25nKbDC66yTANBgkqhkiG9w0BAQsF" + System.lineSeparator() +
                "ADAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTAeFw0xODA0MjcxNzI3" + System.lineSeparator() +
                "MzlaFw0xODA3MjYxNzI3MzlaMB4xHDAaBgNVBAMTE2lubG5lc2VwcHdrZndldy5j" + System.lineSeparator() +
                "b20wggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDIh6YaYWdd0IZ7NwPX" + System.lineSeparator() +
                "eWSC8tCYegpHfAeTJywapmMzy92Hb1KkR8G3tDF+UwHV73bserOKTJh+ZJqtRuiN" + System.lineSeparator() +
                "Vks7KT0e1+jH8x4srRzulubAm72aU0wG5fOHPnTU6xvr9mdNOrbSWM7+f4XNvEP+" + System.lineSeparator() +
                "jc2LwuTHeDg9c1VmavcjiW9bYcsRBqbCBxF8fhMd8ld5Ip3P26aO3UwdIL1h8e24" + System.lineSeparator() +
                "cSjBn92PiaH1BrV6VauubdP76T07R8zwpo4oaMGMPCraVZ6qu11Etj0BkrvqXBHN" + System.lineSeparator() +
                "H5B5ybCEYIRODhqvS7ciQe3/l/oGhfse9AkxoYJyNF2dkgIxdDFpVWL9jluQ9fQu" + System.lineSeparator() +
                "mjBcvr1UI0DVK9U3Y6HlsMQQ1haOi9TFQaTXkHgXq1TslM0w3zFvqQiKyEbNVsl1" + System.lineSeparator() +
                "Xr2Wy7CTzBahP0OnsZoKEnDFHt+8dc7gdf8lA2HwMblVIkfzqk8bf0BrnjtVNnwM" + System.lineSeparator() +
                "/xvvu7n5V0QJePbz9xxbBXnaiTMDKI6C9EdWqLRQOHUYFi7GLYMY90vhFV3n0a9p" + System.lineSeparator() +
                "C0JzDKJ8dDezddZ1jpcXER/Dc1r+HNGobSyG4in+5b2ty9iDu5kAVHB6nZ6IusIp" + System.lineSeparator() +
                "qyTOpAcB0g6w+VqmUs7eIVUtpZvdwz+Rf3p0yQlcsfQVFB4cfXMi6cSq4KSq8uvW" + System.lineSeparator() +
                "cdRoJhPjN6E919TfsPpNHm+VUQIDAQABo4ICmjCCApYwDgYDVR0PAQH/BAQDAgWg" + System.lineSeparator() +
                "MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAAMB0G" + System.lineSeparator() +
                "A1UdDgQWBBTVXc2P4OeNc6OMBB3iiH/ctospFjAfBgNVHSMEGDAWgBT7eE8S+WAV" + System.lineSeparator() +
                "gyyfF380GbMuNupBiTBkBggrBgEFBQcBAQRYMFYwIgYIKwYBBQUHMAGGFmh0dHA6" + System.lineSeparator() +
                "Ly8xMjcuMC4wLjE6NDAwMi8wMAYIKwYBBQUHMAKGJGh0dHA6Ly9ib3VsZGVyOjQ0" + System.lineSeparator() +
                "MzAvYWNtZS9pc3N1ZXItY2VydDAeBgNVHREEFzAVghNpbmxuZXNlcHB3a2Z3ZXcu" + System.lineSeparator() +
                "Y29tMCcGA1UdHwQgMB4wHKAaoBiGFmh0dHA6Ly9leGFtcGxlLmNvbS9jcmwwYQYD" + System.lineSeparator() +
                "VR0gBFowWDAIBgZngQwBAgEwTAYDKgMEMEUwIgYIKwYBBQUHAgEWFmh0dHA6Ly9l" + System.lineSeparator() +
                "eGFtcGxlLmNvbS9jcHMwHwYIKwYBBQUHAgIwEwwRRG8gV2hhdCBUaG91IFdpbHQw" + System.lineSeparator() +
                "ggEDBgorBgEEAdZ5AgQCBIH0BIHxAO8AdgDdmTT8peckgMlWaH2BNJkISbJJ97Vp" + System.lineSeparator() +
                "2Me8qz9cwfNuZAAAAWMIXFWgAAAEAwBHMEUCIAXRs9kJpmcgC2u5ErVOqK1OMUkx" + System.lineSeparator() +
                "xgnft0tykRpsUCRJAiEAzSVDO8nVa1MuAT4ak5G8gLy416yx/A2otdf9m7PejScA" + System.lineSeparator() +
                "dQAW6GnB0ZXq18P4lxrj8HYB94zhtp0xqFIYtoN/MagVCAAAAWMIXFWhAAAEAwBG" + System.lineSeparator() +
                "MEQCIF9IqHmvenOE4Oezwe4WdtRyEFoPbSdlXsO4owIuhaTFAiB2V77wpchHm1Gd" + System.lineSeparator() +
                "J4IyR23E6h+w69l3hT7GJAViHM8SoDANBgkqhkiG9w0BAQsFAAOCAQEACQvKKtNy" + System.lineSeparator() +
                "o0vlQq06Qmm8RRZUZCeWbaYUcMDxQhWgHaG89rG2JKhk/l1/raxPBj+q/StoFtwM" + System.lineSeparator() +
                "fOobIYqthjn0tMO+boRyI63CWTS5iQAAOxN/iV1noCejGYWyeRY3O1hqKn5xzflV" + System.lineSeparator() +
                "GAMCjvIVo3IBn4BjIBfcx+wj7giADWSaZI6jef7lPvFG1zekOtois4/SK1U9DUQB" + System.lineSeparator() +
                "pMdRMQKbH8BOC5WzpOAxJqg9M3BUAg+uqknX9c9A/OBm+Aw56aNrHUq9bX1svWht" + System.lineSeparator() +
                "RUBIKAHFtzW+W3R/KUddkuwYDDrTiZRWPNO4MjC8edLBLZV80XJzVoEmwocIcBjG" + System.lineSeparator() +
                "53PzUdxmaWsaTQ==" + System.lineSeparator() +
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
                .addAuthorizationResponseBody(AUTHZ_URL, AUTHZ_RESPONSE_BODY)
                .addChallengeRequestAndResponse(CHALLENGE_REQUEST_BODY, CHALLENGE_URL, CHALLENGE_RESPONSE_BODY, CHALLENGE_REPLAY_NONCE, CHALLENGE_LOCATION, CHALLENGE_LINK, 200, false, VERIFY_CHALLENGE_URL, CHALLENGE_FILE_CONTENTS, AUTHZ_URL, UPDATED_AUTHZ_RESPONSE_BODY)
                .addFinalizeRequestAndResponse(FINALIZE_RESPONSE_BODY, FINALIZE_REPLAY_NONCE, FINALIZE_URL, FINALIZE_LOCATION, 200)
                .addCheckOrderRequestAndResponse(CHECK_ORDER_URL, CHECK_ORDER_RESPONSE_BODY, 200)
                .addCertificateRequestAndResponse(CERT_URL, CERT_RESPONSE_BODY, 200)
                .build();
    }

    private ClientAndServer setupTestObtainCertificateWithECPublicKey() {
        // set up a mock Let's Encrypt server
        final String ACCT_PATH = "/acme/acct/401";
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

        final String NEW_NONCE_RESPONSE = "50DeS-RHE5Hwp0QwhZA03bw-Exs6r6UO3HuvxAuLXYw";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJpVk5xSnVoM3VvWW5xdkNfZGtYQzRFMDN4R292eTdLUjAwd3M4QUwwcHJWcktzajhnZFdhWjBLZkZ1Q0NUaUtMU1BhNVQ0ZnRWNFdia2l0djFMa0JWU29Wd1hqSDE0bFpIMWFHYkptR1lCX3pSOV9uVzZJTzRVb1RGc2Vqb3paN05kNW8waVFpQWpyRjBmMDhGVC1xYS1TVVZiVk16dkNnQW16SjJFVlhzOXdOQ2pzSVRnNGh3eDdZRzl5eHRhZjFoT0hkV1dKVWtwZ0hnQkVfclpZT1B5YVNlb2JyeE5mMllxVmhFNWM2ZjhrZUhYdnU2dnprODctZVNLWXlndk9hSW1YOUhFbFZhQXRVcnI0S3hFV3VvUDdNRzZCV0s2TDVpam9Db0VMQjBqM0w2UHNuXzM1VnMxQi05OFR6SFZqYU1sU1NGV20xQjdtS0NzNGZMeE1pRXcifSwibm9uY2UiOiI1MERlUy1SSEU1SHdwMFF3aFpBMDNidy1FeHM2cjZVTzNIdXZ4QXVMWFl3IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"WsHikTzA1wqIArFpwaSr42zeC0HKBCO6afzeyCK9kqJW_yrja8uUC8AhLu0w4Q9sD8CgSJM1uz84_JXmBJrAehAVbhENfzmJ1CR0S-3mgCu43aAP4-cE3fpR8NyYlNHkCp-G-1BjXTcBDxuUAaXf4TW4tXHN_UTR4_bN71cHJVhu7D9zLNSZEE2mDS4iJrvWmrLbVGieKdnG__paeK__LvFMq385UFlyIEElKVPPEYOJLP7j6I0Up_FtK0bu1C8g6n-C7Om8djW9dDK4jeJvAq2nLYzy-3hlStDNiG_vq7Jq4Tl2q1wh9N7Zgc587tKH13P_awCBckY9TXOQagOVZQ\"}";


        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "78y9QtgVp7b-z3x7MSNPd4CF7PhhWv9iL6k4bOYDkJU";

        final String QUERY_ACCT_REQUEST_BODY_2 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNDAxIiwibm9uY2UiOiI3OHk5UXRnVnA3Yi16M3g3TVNOUGQ0Q0Y3UGhoV3Y5aUw2azRiT1lEa0pVIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYWNjdC80MDEifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"Nsn01pAULX0CqOuBNxgh-IZvcRzJ4ou9iSwnNmaiC58Rx-C3P-kzvEcHPzShxyBdi-24niFt6eFgRUfQOkYT_YVRAlwxa7RprGafcAwQl_9oCG6WXST-mEQSsIbB-Q4-NUV2eP2VwTrQP9rLmZmtMpbhbi_c3sT4GWyjV1oqzIfjIF1J9IF1W2cRTLLtZJs7CnXoyU8JMpCOclBeJWvPBPGo5FAe-yZFOPPPu6ISqZ18gW-xcG8VrjtEgA_bucv_4dWXDmzE1ZGdtpTwyetpaz5oT1X51ZxKEaqYvvePBjmt2Gp1OMI3QSKiuRrcNnzJUrbS1U2HWrEvVpS6GIHKzQ\"}";

        final String QUERY_ACCT_RESPONSE_BODY_2 = "";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "0s56tTjhgzilhZhjcSuTZNHX_dyWLnZK3fTzRZ9fBjg";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/401";


        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNDAxIiwibm9uY2UiOiIwczU2dFRqaGd6aWxoWmhqY1N1VFpOSFhfZHlXTG5aSzNmVHpSWjlmQmpnIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LW9yZGVyIn0\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoicWJxeHlsZ3lqbWd5d3RrLmNvbSJ9XX0\",\"signature\":\"gAt4IcsxvmkLDKLF7euaiycFgsP2CCdnUueSlsg2xveJbinGytlKwN98BpGhoKBJLxh0m-sU6CrXuEtIRqJu_RXOQPUMkSuOsC-VSVBbWnxxysMm-JeBbVohQVpC8wIh6U3UUyMVEQU_99Vmk0vBmhJVltjr9g_XqdGo3wVl1-ReFRnFeE2LMMQQj1XzaNXLaZOsfVBPsNWMPUpc-VTFl30d6iGpyo_HrI3icze5D7iLj48ETui5wVmmiX2BHlIeMvhnYYoh5OY2LEQ6MG5-duJvf-2-bGz7ZWl4p_viEWmO36gzsB7_5f2qjJyKKIb8WrsYNbCMivipu4aGCFbeiA\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T20:10:36.21542246Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"qbqxylgyjmgywtk.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/401/190\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "c4714cbBAMIc2nda2_LSdHRrYJGjDU58RydXW2cAuTE";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/401/190";

        final String AUTHZ_URL = "/acme/authz/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"qbqxylgyjmgywtk.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T16:10:36-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/544\"," + System.lineSeparator() +
                "      \"token\": \"Rk2XeCkkC5SjqzK3dxzhuxQW3LQ2tyHsxm8cGSD8PX0\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/545\"," + System.lineSeparator() +
                "      \"token\": \"hH4lU_5CeB6i3GtHnhASoqjgVBMo6lnTcZOVqXAQsco\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/546\"," + System.lineSeparator() +
                "      \"token\": \"8AEqb3RMaBtCpfUqsvi9r8nJ5Gt1pt0xN8B3ucfiih4\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNDAxIiwibm9uY2UiOiJjNDcxNGNiQkFNSWMybmRhMl9MU2RIUnJZSkdqRFU1OFJ5ZFhXMmNBdVRFIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2hhbGxlbmdlL1p1MFpxUXFoNmdrWnNwSFhJYzFoQmp2LV9EVG40elcxVlZLYzRRVVdWbTQvNTQ2In0\",\"payload\":\"e30\",\"signature\":\"OFNrhBeZNd6hEMyzs6f7TENycjvsjR2FQ4L4A2ab__dBGkqUtGoL6MwKYauT30aGkzDJKrlcw99MIUhc6LniLwUijvXCi56qcMvHnCLwQAI859PbQUnh-5KV6nVYOgsaf_JUf-O0kyzfNieAxVuib6RFN1iF6fGWmrjKZqbANWATTPGboC49LrGxDYdjIu8hmKMpvcv5w4dpaTqwIFrCAscr7wey9uQWh-9y6ljaVObTLTeZkLxZ1nEGcfutpDKAyUXyGrBqMTJWsepIzicnGpDcBzW8AV3uufIHjzzf4h67Jn7lbtRHNNAP-jw2YPUILa1SYTT6gwZuHMwg_1Z7rw\"}";
        final String CHALLENGE_URL = "/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/546";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/546\"," + System.lineSeparator() +
                "  \"token\": \"8AEqb3RMaBtCpfUqsvi9r8nJ5Gt1pt0xN8B3ucfiih4\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "kx6a_hma4yYVZDd4jyCEau7XBGFZfoQ_JlxoSf4NUCI";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/546";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/8AEqb3RMaBtCpfUqsvi9r8nJ5Gt1pt0xN8B3ucfiih4";
        final String CHALLENGE_FILE_CONTENTS = "8AEqb3RMaBtCpfUqsvi9r8nJ5Gt1pt0xN8B3ucfiih4.952Xm_XyluK_IpyAn6NKkgOGuXbeWn8qoo0Bs9I8mFg";

        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"qbqxylgyjmgywtk.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-27T16:10:36-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/544\"," + System.lineSeparator() +
                "      \"token\": \"Rk2XeCkkC5SjqzK3dxzhuxQW3LQ2tyHsxm8cGSD8PX0\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/545\"," + System.lineSeparator() +
                "      \"token\": \"hH4lU_5CeB6i3GtHnhASoqjgVBMo6lnTcZOVqXAQsco\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4/546\"," + System.lineSeparator() +
                "      \"token\": \"8AEqb3RMaBtCpfUqsvi9r8nJ5Gt1pt0xN8B3ucfiih4\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://qbqxylgyjmgywtk.com:5002/.well-known/acme-challenge/8AEqb3RMaBtCpfUqsvi9r8nJ5Gt1pt0xN8B3ucfiih4\"," + System.lineSeparator() +
                "          \"hostname\": \"qbqxylgyjmgywtk.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"127.0.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"127.0.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_URL = "/acme/finalize/401/190";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T20:10:36Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"qbqxylgyjmgywtk.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/401/190\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff7d5abb5ad7b36b2919d7d0c43ebe901488\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "YJepHZtqau_kGJ78rrlLih4kzpqO2NLyULRJt1X8H8g";
        final String FINALIZE_LOCATION = "http://localhost:4001/acme/order/401/190";

        final String CHECK_ORDER_URL = "/acme/order/401/190";

        final String CHECK_ORDER_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T20:10:36.21542246Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"qbqxylgyjmgywtk.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/Zu0ZqQqh6gkZspHXIc1hBjv-_DTn4zW1VVKc4QUWVm4\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/401/190\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff7d5abb5ad7b36b2919d7d0c43ebe901488\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CERT_URL = "/acme/cert/ff7d5abb5ad7b36b2919d7d0c43ebe901488";

        final String CERT_RESPONSE_BODY = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIEnjCCA4agAwIBAgITAP99Wrta17NrKRnX0MQ+vpAUiDANBgkqhkiG9w0BAQsF" + System.lineSeparator() +
                "ADAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTAeFw0xODA0MjcxOTEw" + System.lineSeparator() +
                "MzZaFw0xODA3MjYxOTEwMzZaMB4xHDAaBgNVBAMTE3FicXh5bGd5am1neXd0ay5j" + System.lineSeparator() +
                "b20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQdAeum8KpMfZq5PmYKpI2GQaxg" + System.lineSeparator() +
                "3E0cMIFEkr5MKhc0g31Ja+pU2hS4afOZMZauXyDoXhwZmibdThzcI2gWviXio4IC" + System.lineSeparator() +
                "nTCCApkwDgYDVR0PAQH/BAQDAgeAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF" + System.lineSeparator() +
                "BQcDAjAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBS/CJzFa4XYCtYqaG0A008skSnR" + System.lineSeparator() +
                "5TAfBgNVHSMEGDAWgBT7eE8S+WAVgyyfF380GbMuNupBiTBmBggrBgEFBQcBAQRa" + System.lineSeparator() +
                "MFgwIgYIKwYBBQUHMAGGFmh0dHA6Ly8xMjcuMC4wLjE6NDAwMi8wMgYIKwYBBQUH" + System.lineSeparator() +
                "MAKGJmh0dHA6Ly8xMjcuMC4wLjE6NDAwMC9hY21lL2lzc3Vlci1jZXJ0MB4GA1Ud" + System.lineSeparator() +
                "EQQXMBWCE3FicXh5bGd5am1neXd0ay5jb20wJwYDVR0fBCAwHjAcoBqgGIYWaHR0" + System.lineSeparator() +
                "cDovL2V4YW1wbGUuY29tL2NybDBhBgNVHSAEWjBYMAgGBmeBDAECATBMBgMqAwQw" + System.lineSeparator() +
                "RTAiBggrBgEFBQcCARYWaHR0cDovL2V4YW1wbGUuY29tL2NwczAfBggrBgEFBQcC" + System.lineSeparator() +
                "AjATDBFEbyBXaGF0IFRob3UgV2lsdDCCAQQGCisGAQQB1nkCBAIEgfUEgfIA8AB1" + System.lineSeparator() +
                "ABboacHRlerXw/iXGuPwdgH3jOG2nTGoUhi2g38xqBUIAAABYwi6mMIAAAQDAEYw" + System.lineSeparator() +
                "RAIgQq7meXdYdkJLa2Bi5uV5cA2cnGY1rulVuBpqrDcPd5MCIFBo8W015liL6UIB" + System.lineSeparator() +
                "Y8z263MEA+JCcPd7twbHBUd3k4raAHcA3Zk0/KXnJIDJVmh9gTSZCEmySfe1adjH" + System.lineSeparator() +
                "vKs/XMHzbmQAAAFjCLqYwwAABAMASDBGAiEAkzofAX5ZsYqSbFHVKIiehZCAMsFs" + System.lineSeparator() +
                "QZC7bO+0O37VEwgCIQCDZfOfjbNRttx9pp9ksw3KtrqTj5OF6DvH59Tr6Fey5TAN" + System.lineSeparator() +
                "BgkqhkiG9w0BAQsFAAOCAQEADqXOHLreDEJ1xj7vA9H6WtG/cp3dOeTVQs7jAOd5" + System.lineSeparator() +
                "3Ffz9biwTi6quCiMzRbH+vbWExVYLuIIA7Wxa74+tHk1zFXxjB7ld2JaJzPHQGch" + System.lineSeparator() +
                "owCMPtLmOOLtZ3tPHPC18PAYPbBc3MN2L7QYHsLkMJe7ucDLAzSbConqyWhUNrx0" + System.lineSeparator() +
                "bMJR8AY2MbQLOb04f75gEpZEcnipzDX4uihH3qhliLanXgNMhZ0zRdaWCPNRUQes" + System.lineSeparator() +
                "ut19jxS5dZArysqq7Zok+kaRL5MxXVsLmtL/x1dmekgIsUJ9wgxzbGulb+uww/Qa" + System.lineSeparator() +
                "2lhjwCXdqSW3tXr4iWkUrUiVvwyQEipswNCuYeHDEbG0tQ==" + System.lineSeparator() +
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
                .addAuthorizationResponseBody(AUTHZ_URL, AUTHZ_RESPONSE_BODY)
                .addChallengeRequestAndResponse(CHALLENGE_REQUEST_BODY, CHALLENGE_URL, CHALLENGE_RESPONSE_BODY, CHALLENGE_REPLAY_NONCE, CHALLENGE_LOCATION, CHALLENGE_LINK, 200, false, VERIFY_CHALLENGE_URL, CHALLENGE_FILE_CONTENTS, AUTHZ_URL, UPDATED_AUTHZ_RESPONSE_BODY)
                .addFinalizeRequestAndResponse(FINALIZE_RESPONSE_BODY, FINALIZE_REPLAY_NONCE, FINALIZE_URL, FINALIZE_LOCATION, 200)
                .addCheckOrderRequestAndResponse(CHECK_ORDER_URL, CHECK_ORDER_RESPONSE_BODY, 200)
                .addCertificateRequestAndResponse(CERT_URL, CERT_RESPONSE_BODY, 200)
                .build();
    }

    private ClientAndServer setupTestObtainCertificateWithUnsupportedPublicKey() {
        // set up a mock Let's Encrypt server
        final String ACCT_PATH = "/acme/acct/401";
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

        final String NEW_NONCE_RESPONSE = "50DeS-RHE5Hwp0QwhZA03bw-Exs6r6UO3HuvxAuLXYw";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJpVk5xSnVoM3VvWW5xdkNfZGtYQzRFMDN4R292eTdLUjAwd3M4QUwwcHJWcktzajhnZFdhWjBLZkZ1Q0NUaUtMU1BhNVQ0ZnRWNFdia2l0djFMa0JWU29Wd1hqSDE0bFpIMWFHYkptR1lCX3pSOV9uVzZJTzRVb1RGc2Vqb3paN05kNW8waVFpQWpyRjBmMDhGVC1xYS1TVVZiVk16dkNnQW16SjJFVlhzOXdOQ2pzSVRnNGh3eDdZRzl5eHRhZjFoT0hkV1dKVWtwZ0hnQkVfclpZT1B5YVNlb2JyeE5mMllxVmhFNWM2ZjhrZUhYdnU2dnprODctZVNLWXlndk9hSW1YOUhFbFZhQXRVcnI0S3hFV3VvUDdNRzZCV0s2TDVpam9Db0VMQjBqM0w2UHNuXzM1VnMxQi05OFR6SFZqYU1sU1NGV20xQjdtS0NzNGZMeE1pRXcifSwibm9uY2UiOiI1MERlUy1SSEU1SHdwMFF3aFpBMDNidy1FeHM2cjZVTzNIdXZ4QXVMWFl3IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"WsHikTzA1wqIArFpwaSr42zeC0HKBCO6afzeyCK9kqJW_yrja8uUC8AhLu0w4Q9sD8CgSJM1uz84_JXmBJrAehAVbhENfzmJ1CR0S-3mgCu43aAP4-cE3fpR8NyYlNHkCp-G-1BjXTcBDxuUAaXf4TW4tXHN_UTR4_bN71cHJVhu7D9zLNSZEE2mDS4iJrvWmrLbVGieKdnG__paeK__LvFMq385UFlyIEElKVPPEYOJLP7j6I0Up_FtK0bu1C8g6n-C7Om8djW9dDK4jeJvAq2nLYzy-3hlStDNiG_vq7Jq4Tl2q1wh9N7Zgc587tKH13P_awCBckY9TXOQagOVZQ\"}";


        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "78y9QtgVp7b-z3x7MSNPd4CF7PhhWv9iL6k4bOYDkJU";

        final String QUERY_ACCT_REQUEST_BODY_2 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNDAxIiwibm9uY2UiOiI3OHk5UXRnVnA3Yi16M3g3TVNOUGQ0Q0Y3UGhoV3Y5aUw2azRiT1lEa0pVIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYWNjdC80MDEifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"Nsn01pAULX0CqOuBNxgh-IZvcRzJ4ou9iSwnNmaiC58Rx-C3P-kzvEcHPzShxyBdi-24niFt6eFgRUfQOkYT_YVRAlwxa7RprGafcAwQl_9oCG6WXST-mEQSsIbB-Q4-NUV2eP2VwTrQP9rLmZmtMpbhbi_c3sT4GWyjV1oqzIfjIF1J9IF1W2cRTLLtZJs7CnXoyU8JMpCOclBeJWvPBPGo5FAe-yZFOPPPu6ISqZ18gW-xcG8VrjtEgA_bucv_4dWXDmzE1ZGdtpTwyetpaz5oT1X51ZxKEaqYvvePBjmt2Gp1OMI3QSKiuRrcNnzJUrbS1U2HWrEvVpS6GIHKzQ\"}";

        final String QUERY_ACCT_RESPONSE_BODY_2 = "";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "Rvyjsq8CE1kTtdrt-HrToIvAJdPMr1TnxtFEnaHsGU0";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/401";

        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNDAxIiwibm9uY2UiOiJSdnlqc3E4Q0Uxa1R0ZHJ0LUhyVG9JdkFKZFBNcjFUbnh0RkVuYUhzR1UwIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LW9yZGVyIn0\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoiaXJhY2x6bGNxZ2F5bXJjLmNvbSJ9XX0\",\"signature\":\"d0CZTIK3hBJ9YOCWU0Hv1CnZp2TU_pCoxfJEOurZNRZj6B_kXbc4of-t33Dx_eeM1JRPMXJ0AcOWaF9DrIyFWm4iFXGe9AB3aZMO0t3bSQ23DFWhX4s8k7UkxQfLVJ7Mga63sHQG5gRuQzJPsnX_bqWGyDzBlYzMBEVG8P2-TgNDHGVGG_MDnAcXjZjyakZH8888i-fnTcdaPuiaLfYYiaGEYkX22j_TSbdQewwrVCD6Nferxn84SyvV3WkM3ZNjwQ9Pa8495mwAhsu2-pQxGzSnFZcJCivzJP4VSjr5Ur4ZHbGV2kR4isuX6IWk5KRntX2Ltx1-VSRMnkCe4DAUJg\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-07T14:29:36.239815881Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/401/201\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "dfsGDv2NEk2p3bwCSzjPWuVicILXJ7gW1cWLQBImpYw";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/401/201";

        final String AUTHZ_URL = "/acme/authz/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-07T10:29:36-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/577\"," + System.lineSeparator() +
                "      \"token\": \"drmkP7AAB0Uv3LC-wS8GcQchsYIvCp560duP_hej9iw\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/578\"," + System.lineSeparator() +
                "      \"token\": \"P34a6nt-4Ko2apwGYFOUK-DXS_BiTcg9hAyVxtg5BCI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/579\"," + System.lineSeparator() +
                "      \"token\": \"YMPTs8LpgdDA883WALOI_kyXoOS54wUzS82yUKqnQ70\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNDAxIiwibm9uY2UiOiJkZnNHRHYyTkVrMnAzYndDU3pqUFd1VmljSUxYSjdnVzFjV0xRQkltcFl3IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2hhbGxlbmdlL2k4WnJQWmprX1ktcDE3bzJfZEt1OTlaNWRTQmlmVEZwLUVzQ3pSd2pjRmMvNTc4In0\",\"payload\":\"e30\",\"signature\":\"CigVBit8h-3juGM_z_LycpRPIFXhuX0TvCpVi2Ef-Ka__fvCur_r-JgzmIoxM8UksCmV3QcaT8anBT05GYnuKHkocRMv7a0swSG5HWzpi39YHM39z2k5ayXHRNcow5gvBMBosaXMRk_jZ77xArOiOykycnG7wbRLTD3GOHkUUqLxBDX4YQQscQ-Jid_kgVZEyeuf2XejlaChCDCGqL2Z3cBTBZFYIX0o4oiEA6TsGqvTyJBhyMexdM5_OlKw5u_F3d6q-c93V1opw-9vyjCP7-4wPSkkBT5uSZQzeVdXfZ-QGK2oD74Ju_QMasdfW-12340k7ePRFhsC21ipU-pXig\"}";
        final String CHALLENGE_URL = "/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/578";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/578\"," + System.lineSeparator() +
                "  \"token\": \"P34a6nt-4Ko2apwGYFOUK-DXS_BiTcg9hAyVxtg5BCI\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "jWFqKg3AkFN0Fg9cP5pwIrCU8gn0JruLHOQ0XftVNQU";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/578";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/P34a6nt-4Ko2apwGYFOUK-DXS_BiTcg9hAyVxtg5BCI";
        final String CHALLENGE_FILE_CONTENTS = "P34a6nt-4Ko2apwGYFOUK-DXS_BiTcg9hAyVxtg5BCI.952Xm_XyluK_IpyAn6NKkgOGuXbeWn8qoo0Bs9I8mFg";

        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-30T10:29:36-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/577\"," + System.lineSeparator() +
                "      \"token\": \"drmkP7AAB0Uv3LC-wS8GcQchsYIvCp560duP_hej9iw\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/578\"," + System.lineSeparator() +
                "      \"token\": \"P34a6nt-4Ko2apwGYFOUK-DXS_BiTcg9hAyVxtg5BCI\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://iraclzlcqgaymrc.com:5002/.well-known/acme-challenge/P34a6nt-4Ko2apwGYFOUK-DXS_BiTcg9hAyVxtg5BCI\"," + System.lineSeparator() +
                "          \"hostname\": \"iraclzlcqgaymrc.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"127.0.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"127.0.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/i8ZrPZjk_Y-p17o2_dKu99Z5dSBifTFp-EsCzRwjcFc/579\"," + System.lineSeparator() +
                "      \"token\": \"YMPTs8LpgdDA883WALOI_kyXoOS54wUzS82yUKqnQ70\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_URL = "/acme/finalize/401/201";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"urn:ietf:params:acme:error:malformed\"," + System.lineSeparator() +
                "  \"detail\": \"Error finalizing order :: invalid public key in CSR: unknown key type *dsa.PublicKey\"," + System.lineSeparator() +
                "  \"status\": 400" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "YpsZ4fckMfRjeRAFcJLoNecfDVXyYhxVLAfydrR1xEw";
        final String FINALIZE_LOCATION = "";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_1, QUERY_ACCT_RESPONSE_BODY_1, QUERY_ACCT_REPLAY_NONCE_1, ACCT_LOCATION, 200)
                .updateAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_2, QUERY_ACCT_RESPONSE_BODY_2, QUERY_ACCT_REPLAY_NONCE_2, ACCT_PATH, 200)
                .orderCertificateRequestAndResponse(ORDER_CERT_REQUEST_BODY, ORDER_CERT_RESPONSE_BODY, ORDER_CERT_REPLAY_NONCE, ORDER_LOCATION, 201, false)
                .addAuthorizationResponseBody(AUTHZ_URL, AUTHZ_RESPONSE_BODY)
                .addChallengeRequestAndResponse(CHALLENGE_REQUEST_BODY, CHALLENGE_URL, CHALLENGE_RESPONSE_BODY, CHALLENGE_REPLAY_NONCE, CHALLENGE_LOCATION, CHALLENGE_LINK, 200, false, VERIFY_CHALLENGE_URL, CHALLENGE_FILE_CONTENTS, AUTHZ_URL, UPDATED_AUTHZ_RESPONSE_BODY)
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