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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
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

import org.jboss.as.controller.Extension;
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
import org.mockserver.integration.ClientAndServer;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.cert.BasicConstraintsExtension;
import org.wildfly.security.x500.cert.KeyUsage;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public abstract class ElytronCommonKeyStoresTestCase extends AbstractSubsystemTest {

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility csUtil = null;
    private static final String CS_PASSWORD = "super_secret";
    private static final String KEYSTORE_NAME = "ModifiedKeystore";
    private static final String KEY_PASSWORD = "secret";
    private static final String CERTIFICATE_AUTHORITY_ACCOUNT_NAME = "CertAuthorityAccount";
    private static final String CERTIFICATE_AUTHORITY_NAME = "CertAuthority";
    private static final String ACCOUNTS_KEYSTORE = "account.keystore";
    private static final String ACCOUNTS_KEYSTORE_NAME = "AccountsKeyStore";
    private static final String ACCOUNTS_KEYSTORE_PASSWORD = "elytron";
    private static final String SIMULATED_LETS_ENCRYPT_ENDPOINT = "http://localhost:4001/directory"; // simulated Let's Encrypt server instance
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
    private static final File FIREFLY_COPY_FILE = new File(WORKING_DIRECTORY_LOCATION, "firefly-copy.keystore");
    private static final File TEST_FILE = new File(WORKING_DIRECTORY_LOCATION, "test.keystore");
    private static final File TEST_SINGLE_CERT_REPLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-single-cert-reply.cert");
    private static final File TEST_CERT_CHAIN_REPLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-cert-chain-reply.cert");
    private static final File TEST_EXPORTED_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-exported.cert");
    private static final File TEST_TRUSTED_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-trusted.cert");
    private static final File TEST_UNTRUSTED_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-untrusted.cert");
    private static final File TEST_UNTRUSTED_CERT_CHAIN_REPLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "test-untrusted-cert-chain-reply.cert");

    private static void createServerEnvironment() {
        File home = new File("target/wildfly");
        home.mkdir();
        File challengeDir = new File(home, ".well-known/acme-challenge");
        challengeDir.mkdirs();
        oldHomeDir = System.setProperty("jboss.home.dir", home.getAbsolutePath());
    }

    public ElytronCommonKeyStoresTestCase(final String mainSubsystemName, final Extension mainExtension) {
        super(mainSubsystemName, mainExtension);
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
                .setSignatureAlgorithmName("SHA256withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
        X509Certificate issuerCertificate = issuerSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        fireflyKeyStore.setCertificateEntry("ca", issuerCertificate);

        X509Certificate fireflyCertificate = new X509CertificateBuilder()
                .setIssuerDn(ROOT_DN)
                .setSubjectDn(FIREFLY_DN)
                .setSignatureAlgorithmName("SHA256withRSA")
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
                FIREFLY_COPY_FILE,
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
        csUtil.addEntry("primary-password-alias", "Elytron");
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
        String subsystemXml;
        if (JdkUtils.isIbmJdk()) {
            subsystemXml = "tls-ibm.xml";
        } else {
            subsystemXml = JdkUtils.getJavaSpecVersion() <= 12 ? "tls-sun.xml" : "tls-oracle13plus.xml";
        }
        services = super.createKernelServicesBuilder(new ElytronCommonTestEnvironment()).setSubsystemXmlResource(subsystemXml).build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    @Test
    public void testKeystoreService() throws Exception {
        ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName("FireflyKeystore");
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
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("firefly.keystore"), resources.resolve("firefly-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ModelNode operation = new ModelNode(); // add keystore
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", "ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/firefly-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(2, nodes.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.REMOVE_ALIAS);
        operation.get(ElytronCommonConstants.ALIAS).set("ca");
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIASES);
        nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(1, nodes.size());

        operation = new ModelNode(); // remove keystore
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testKeystoreReadVerbose() throws Exception {
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("firefly.keystore"), resources.resolve("firefly-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ModelNode operation = new ModelNode(); // add keystore
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", "ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/firefly-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(2, nodes.size());
        //["ca", "firefly"]
        assertEquals("ca",nodes.get(0).asString());
        assertEquals("firefly",nodes.get(1).asString());

        validateRecursiveReadAliases(true);
        validateRecursiveReadAliases(false);

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIAS);
        operation.get(ElytronCommonConstants.ALIAS).set("firefly");
        operation.get(ElytronCommonConstants.VERBOSE).set(false);
        checkCertificate(services.executeOperation(operation).get(ClientConstants.RESULT), false);
    }

    private void validateRecursiveReadAliases(final boolean verbose) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIASES);
        operation.get(ElytronCommonConstants.VERBOSE).set(verbose);
        operation.get(ElytronCommonConstants.RECURSIVE).set(true);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(2, nodes.size());
        //[("ca" => {}),("firefly" => {})]

        for(ModelNode node : nodes) {
            node = node.get(0);
            final String alias = node.get(ElytronCommonConstants.ALIAS).asString();
            final ModelNode certificateChain = node.get(ElytronCommonConstants.CERTIFICATE_CHAIN);
            if(certificateChain.isDefined()) {
                for(ModelNode certificateFromChain : certificateChain.asList())
                    checkCertificate(certificateFromChain, verbose);
            } else {
                //need to clean after above .get
                node.remove(ElytronCommonConstants.CERTIFICATE_CHAIN);
                checkCertificate(node.get(ElytronCommonConstants.CERTIFICATE), verbose);
            }

            final ModelNode readOperation = new ModelNode();
            readOperation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store","ModifiedKeyStore");
            readOperation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIAS);
            readOperation.get(ElytronCommonConstants.VERBOSE).set(verbose);
            readOperation.get(ElytronCommonConstants.ALIAS).set(alias);
            final ModelNode aliasReadNode = assertSuccess(services.executeOperation(readOperation)).get(ClientConstants.RESULT);
            assertEquals(aliasReadNode, node);
        }
    }

    private void checkCertificate(final ModelNode certificate, final boolean verbose) {
        final ModelNode publicKey = certificate.get(ElytronCommonConstants.PUBLIC_KEY);
        if(verbose) {
            assertNotNull(publicKey);
            assertTrue(publicKey.isDefined());
        } else {
            assertNotNull(publicKey);
            assertTrue(!publicKey.isDefined());
            //Cleanup
            certificate.remove(ElytronCommonConstants.PUBLIC_KEY);
        }
        final ModelNode encoded = certificate.get(ElytronCommonConstants.ENCODED);
        if(verbose) {
            assertNotNull(encoded);
            assertTrue(encoded.isDefined());
        } else {
            assertNotNull(encoded);
            assertTrue(!encoded.isDefined());
            //Cleanup
            certificate.remove(ElytronCommonConstants.ENCODED);
        }
    }

    @Test
    public void testFilteringKeystoreService() throws Exception {
        ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName("FilteringKeyStore");
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
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronCommonConstants.FILTERING_KEY_STORE,"FilteringKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(1, nodes.size());
        assertEquals("firefly", nodes.get(0).asString());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronCommonConstants.FILTERING_KEY_STORE,"FilteringKeyStore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIAS);
        operation.get(ElytronCommonConstants.ALIAS).set("firefly");
        ModelNode firefly = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals("firefly", firefly.get(ElytronCommonConstants.ALIAS).asString());
        assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), firefly.get(ElytronCommonConstants.ENTRY_TYPE).asString());
        assertTrue(firefly.get(ElytronCommonConstants.CERTIFICATE_CHAIN).isDefined());
    }

    @Test
    public void testAutomaticKeystoreService() throws Exception {
        ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName("AutomaticKeystore");
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
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronCommonConstants.KEY_STORE,"AutomaticKeystore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        assertEquals(2, nodes.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronCommonConstants.KEY_STORE,"AutomaticKeystore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIAS);
        operation.get(ElytronCommonConstants.ALIAS).set("firefly");
        ModelNode firefly = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals("firefly", firefly.get(ElytronCommonConstants.ALIAS).asString());
        assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), firefly.get(ElytronCommonConstants.ENTRY_TYPE).asString());
        assertTrue(firefly.get(ElytronCommonConstants.CERTIFICATE_CHAIN).isDefined());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add(ElytronCommonConstants.KEY_STORE,"AutomaticKeystore");
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIAS);
        operation.get(ElytronCommonConstants.ALIAS).set("ca");
        ModelNode ca = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals("ca", ca.get(ElytronCommonConstants.ALIAS).asString());
        assertEquals(KeyStore.TrustedCertificateEntry.class.getSimpleName(), ca.get(ElytronCommonConstants.ENTRY_TYPE).asString());
    }

    @Test
    public void testGenerateKeyPair() throws Exception {
        addKeyStore();

        try {
            int numAliasesBefore = readAliases().size();

            // This value will be compared with a Date representation with precision of seconds only so
            // drop any nanoseconds.
            ZonedDateTime startTime = ZonedDateTime.now().withNano(0);

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.GENERATE_KEY_PAIR);
            operation.get(ElytronCommonConstants.ALIAS).set("bsmith");
            operation.get(ElytronCommonConstants.ALGORITHM).set("RSA");
            operation.get(ElytronCommonConstants.KEY_SIZE).set(1024);
            operation.get(ElytronCommonConstants.VALIDITY).set(365);
            operation.get(ElytronCommonConstants.SIGNATURE_ALGORITHM).set("SHA256withRSA");
            operation.get(ElytronCommonConstants.DISTINGUISHED_NAME).set("CN=bob smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
            ModelNode extensions = new ModelNode();
            extensions.add(getExtension(false, "ExtendedKeyUsage", "clientAuth"));
            extensions.add(getExtension(true, "KeyUsage", "digitalSignature"));
            extensions.add(getExtension(false, "SubjectAlternativeName", "email:bobsmith@example.com,DNS:bobsmith.example.com"));
            operation.get(ElytronCommonConstants.EXTENSIONS).set(extensions);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            assertSuccess(services.executeOperation(operation));
            assertEquals(numAliasesBefore + 1, readAliases().size());

            // Don't drop the nanoseconds from this value as this is being used to check the expirtation is no longer
            // than 365 days in the future.
            ZonedDateTime createdTime = ZonedDateTime.now();

            ModelNode newAlias = readAlias("bsmith");
            assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), newAlias.get(ElytronCommonConstants.ENTRY_TYPE).asString());
            assertEquals(1, newAlias.get(ElytronCommonConstants.CERTIFICATE_CHAIN).asList().size());

            ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate("bsmith");
            assertEquals("RSA", certificate.getPublicKey().getAlgorithm());
            assertEquals(1024, ((RSAKey) certificate.getPublicKey()).getModulus().bitLength());

            ZonedDateTime notBefore = ZonedDateTime.ofInstant(certificate.getNotBefore().toInstant(), ZoneId.systemDefault());
            assertTrue("Certificate not valid before test ran.", !notBefore.isBefore(startTime));

            ZonedDateTime calculatedAfter = notBefore.plusDays(365);
            Date notAfter = certificate.getNotAfter();
            assertEquals("Expected 'notAfter' value.", calculatedAfter.toInstant(), notAfter.toInstant());

            ZonedDateTime in365Days = createdTime.plusDays(365);
            assertTrue("Certificate not valid more than 365 days after creation", !notAfter.toInstant().isAfter(in365Days.toInstant()));

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
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        File csrFile = new File(resources + csrFileName);
        // Use the original KeyStore since this test depends on the encoding being identical but not the expiration date
        addOriginalKeyStore();

        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.GENERATE_CERTIFICATE_SIGNING_REQUEST);
            operation.get(ElytronCommonConstants.ALIAS).set("ssmith");
            operation.get(ElytronCommonConstants.SIGNATURE_ALGORITHM).set("SHA512withRSA");
            operation.get(ElytronCommonConstants.DISTINGUISHED_NAME).set("CN=ssmith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
            ModelNode extensions = new ModelNode();
            extensions.add(getExtension(false, "ExtendedKeyUsage", "clientAuth"));
            extensions.add(getExtension(true, "KeyUsage", "digitalSignature"));
            operation.get(ElytronCommonConstants.EXTENSIONS).set(extensions);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            operation.get(ElytronCommonConstants.PATH).set(resources + csrFileName);
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
            assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), alias.get(ElytronCommonConstants.ENTRY_TYPE).asString());
            assertEquals(1, alias.get(ElytronCommonConstants.CERTIFICATE_CHAIN).asList().size());

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set("ssmith");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronCommonConstants.PATH).set(resources + replyFileName);
            assertSuccess(services.executeOperation(operation));

            alias = readAlias("ssmith");
            assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), alias.get(ElytronCommonConstants.ENTRY_TYPE).asString());
            assertEquals(2, alias.get(ElytronCommonConstants.CERTIFICATE_CHAIN).asList().size());

            ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            Certificate[] chain = keyStore.getCertificateChain("ssmith");
            X509Certificate firstCertificate = (X509Certificate) chain[0];
            X509Certificate secondCertificate = (X509Certificate) chain[1];
            String expectedIssuerDn = "O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA";
            assertEquals(new X500Principal("CN=ssmith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us"), firstCertificate.getSubjectX500Principal());
            assertEquals(new X500Principal(expectedIssuerDn).getName(), firstCertificate.getIssuerX500Principal().getName());
            assertEquals(new X500Principal(expectedIssuerDn).getName(), secondCertificate.getSubjectX500Principal().getName());
            assertEquals(new X500Principal(expectedIssuerDn).getName(), secondCertificate.getIssuerX500Principal().getName());
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
            ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            KeyStore.PrivateKeyEntry aliasBefore = (KeyStore.PrivateKeyEntry) keyStore.getEntry("ssmith", new KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray()));
            assertEquals(1, aliasBefore.getCertificateChain().length);

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set("ssmith");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronCommonConstants.PATH).set(resources + replyFileName);
            operation.get(ElytronCommonConstants.VALIDATE).set(validate);
            operation.get(ElytronCommonConstants.TRUST_CACERTS).set(true);

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
                assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), alias.get(ElytronCommonConstants.ENTRY_TYPE).asString());
                assertEquals(2, alias.get(ElytronCommonConstants.CERTIFICATE_CHAIN).asList().size());

                keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
                assertNotNull(keyStore);
                Certificate[] chain = keyStore.getCertificateChain("ssmith");
                X509Certificate firstCertificate = (X509Certificate) chain[0];
                X509Certificate secondCertificate = (X509Certificate) chain[1];
                String expectedIssuerDn = "O=Another Root Certificate Authority, EMAILADDRESS=anotherca@wildfly.org, C=UK, ST=Elytron, CN=Another Elytron CA";
                assertEquals(new X500Principal("CN=ssmith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us").getName(), firstCertificate.getSubjectX500Principal().getName());
                assertEquals(new X500Principal(expectedIssuerDn).getName(), firstCertificate.getIssuerX500Principal().getName());
                assertEquals(new X500Principal(expectedIssuerDn).getName(), secondCertificate.getSubjectX500Principal().getName());
                assertEquals(new X500Principal(expectedIssuerDn).getName(), secondCertificate.getIssuerX500Principal().getName());
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
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set("intermediateCA");
            Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronCommonConstants.PATH).set(resources + replyFileName);
            operation.get(ElytronCommonConstants.TRUST_CACERTS).set(true);
            assertSuccess(services.executeOperation(operation));
            assertEquals(numAliasesBefore + 1, readAliases().size());

            ModelNode alias = readAlias("intermediateCA");
            assertEquals(KeyStore.TrustedCertificateEntry.class.getSimpleName(), alias.get(ElytronCommonConstants.ENTRY_TYPE).asString());
            assertTrue(alias.get(ElytronCommonConstants.CERTIFICATE).isDefined());

            ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate("intermediateCA");
            assertEquals(new X500Principal("O=Intermediate Certificate Authority, EMAILADDRESS=intermediateca@wildfly.org, C=UK, ST=Elytron, CN=Intermediate Elytron CA").getName(), certificate.getSubjectX500Principal().getName());
            assertEquals(new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA").getName(), certificate.getIssuerX500Principal().getName());
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
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.IMPORT_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set("anotherCA");
            Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
            operation.get(ElytronCommonConstants.PATH).set(resources + replyFileName);
            operation.get(ElytronCommonConstants.VALIDATE).set(validate);
            operation.get(ElytronCommonConstants.TRUST_CACERTS).set(true);

            if (validate) {
                assertFailed(services.executeOperation(operation));
                assertEquals(numAliasesBefore, readAliases().size());
            } else {
                assertSuccess(services.executeOperation(operation));
                assertEquals(numAliasesBefore + 1, readAliases().size());

                ModelNode alias = readAlias("anotherCA");
                assertEquals(KeyStore.TrustedCertificateEntry.class.getSimpleName(), alias.get(ElytronCommonConstants.ENTRY_TYPE).asString());
                assertTrue(alias.get(ElytronCommonConstants.CERTIFICATE).isDefined());

                ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
                KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
                assertNotNull(keyStore);
                String expectedDn = "O=Another Root Certificate Authority, EMAILADDRESS=anotherca@wildfly.org, C=UK, ST=Elytron, CN=Another Elytron CA";
                X509Certificate certificate = (X509Certificate) keyStore.getCertificate("anotherCA");
                assertEquals(new X500Principal(expectedDn).getName(), certificate.getSubjectX500Principal().getName());
                assertEquals(new X500Principal(expectedDn).getName(), certificate.getIssuerX500Principal().getName());
            }
        } finally {
            removeKeyStore();
        }
    }

    @Test
    public void testExportCertificate() throws Exception {
        String expectedCertificateFileName = "/test-exported.cert";
        String certificateFileName = "/exported-cert.cert";
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        File certificateFile = new File(resources + certificateFileName);
        addKeyStore();

        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.EXPORT_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set("ssmith");
            operation.get(ElytronCommonConstants.PATH).set(resources + certificateFileName);
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
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        File certificateFile = new File(resources + certificateFileName);
        // Use the original KeyStore since this test depends on the encoding being identical but not the expiration date
        addOriginalKeyStore();

        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.EXPORT_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set("ssmith");
            operation.get(ElytronCommonConstants.PATH).set(resources + certificateFileName);
            operation.get(ElytronCommonConstants.PEM).set(true);
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

            ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(KEYSTORE_NAME);
            KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
            assertNotNull(keyStore);
            KeyStore.PrivateKeyEntry aliasBefore = (KeyStore.PrivateKeyEntry) keyStore.getEntry("ssmith", new KeyStore.PasswordProtection(KEY_PASSWORD.toCharArray()));

            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CHANGE_ALIAS);
            operation.get(ElytronCommonConstants.ALIAS).set("ssmith");
            operation.get(ElytronCommonConstants.NEW_ALIAS).set("sallysmith");
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
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        File file = new File(resources + nonExistentFileName);

        ModelNode operation = getAddKeyStoreUsingNonExistingFileOperation(false, nonExistentFileName);
        assertSuccess(services.executeOperation(operation));
        try {
            operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.STORE);
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
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("invalid", "mailto:admin@anexample.com");
        addKeyStore(); // to store the obtained certificate
        server = setupTestCreateAccountWithoutAgreeingToTermsOfService();
        String alias = "server";
        KeyStore keyStore = getKeyStore(KEYSTORE_NAME);
        assertFalse(keyStore.containsAlias(alias));
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.OBTAIN_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set(alias);
            operation.get(ElytronCommonConstants.DOMAIN_NAMES).add("www.example.com");
            operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            ModelNode result = services.executeOperation(operation);
            assertFailed(result);
            String failureDescription = result.get(FAILURE_DESCRIPTION).asString();
            assertTrue(failureDescription.contains("must agree to terms of service"));
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
            removeKeyStore(KEYSTORE_NAME);
        }
    }

    @Test
    public void testObtainCertificateWithKeySize() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account6v2", "mailto:admin@myexample.com");
        addKeyStore(); // to store the obtained certificate
        server = setupTestObtainCertificateWithKeySize();
        String alias = "server";
        String keyAlgorithmName = "RSA";
        int keySize = 4096;
        KeyStore keyStore = getKeyStore(KEYSTORE_NAME);
        assertFalse(keyStore.containsAlias(alias));
        obtainCertificate(keyAlgorithmName, keySize, "inlneseppwkfwewv2.com", alias, keyStore);
    }

    @Test
    public void testObtainCertificateWithECPublicKey() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account7v2", "mailto:admin@myexample.com");
        addKeyStore(); // to store the obtained certificate
        server = setupTestObtainCertificateWithECPublicKey();
        String alias = "server";
        String keyAlgorithmName = "EC";
        int keySize = 256;
        KeyStore keyStore = getKeyStore(KEYSTORE_NAME);
        assertFalse(keyStore.containsAlias(alias));
        obtainCertificate(keyAlgorithmName, keySize, "mndelkdnbcilohgv2.com", alias, keyStore);
    }

    @Test
    public void testObtainCertificateWithUnsupportedPublicKey() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account7v2", "mailto:admin@myexample.com");
        addKeyStore(); // to store the obtained certificate
        server = setupTestObtainCertificateWithUnsupportedPublicKey();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.OBTAIN_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set("server");
            operation.get(ElytronCommonConstants.DOMAIN_NAMES).add("iraclzlcqgaymrc.com");
            operation.get(ElytronCommonConstants.ALGORITHM).set("DSA");
            operation.get(ElytronCommonConstants.KEY_SIZE).set(2048);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            ModelNode result = services.executeOperation(operation);
            assertFailed(result);
            String failureDescription = result.get(FAILURE_DESCRIPTION).asString();
            assertTrue(failureDescription.contains("WFLYCTL0129"));
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
            removeKeyStore(KEYSTORE_NAME);
        }
    }

    private void obtainCertificate(String keyAlgorithmName, int keySize, String domainName, String alias, KeyStore keyStore) throws Exception {
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KEYSTORE_NAME);
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.OBTAIN_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set(alias);
            operation.get(ElytronCommonConstants.DOMAIN_NAMES).add(domainName);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEY_PASSWORD);
            operation.get(ElytronCommonConstants.ALGORITHM).set(keyAlgorithmName);
            operation.get(ElytronCommonConstants.KEY_SIZE).set(keySize);
            operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
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
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
            removeKeyStore(KEYSTORE_NAME);
        }
    }
    @Test
    public void testRevokeCertificateWithoutReason() throws Exception {
        revokeCertificate("revokealiasv2", null);
    }

    @Test
    public void testRevokeCertificateWithReason() throws Exception {
        revokeCertificate("revokewithreasonaliasv2", "KeyCompromise");
    }

    @Test
    public void testShouldRenewCertificateAlreadyExpired() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.of(2018, 03, 24, 23, 59, 59, 0, ZoneOffset.UTC);
        final ZonedDateTime notValidAfterDate = ZonedDateTime.of(2018, 04, 24, 23, 59, 59, 0, ZoneOffset.UTC);
        ModelNode result = shouldRenewCertificate(notValidBeforeDate, notValidAfterDate, 30);
        assertTrue(result.get(ElytronCommonConstants.SHOULD_RENEW_CERTIFICATE).asBoolean());
        assertEquals(0, result.get(ElytronCommonConstants.DAYS_TO_EXPIRY).asLong());
    }

    @Test
    public void testShouldRenewCertificateExpiresWithinGivenDays() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        final ZonedDateTime notValidAfterDate = notValidBeforeDate.plusDays(60).plusMinutes(1);
        ModelNode result = shouldRenewCertificate(notValidBeforeDate, notValidAfterDate, 90);
        assertTrue(result.get(ElytronCommonConstants.SHOULD_RENEW_CERTIFICATE).asBoolean());
        assertEquals(60, result.get(ElytronCommonConstants.DAYS_TO_EXPIRY).asLong());
    }

    @Test
    public void testShouldRenewCertificateDoesNotExpireWithinGivenDays() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        final ZonedDateTime notValidAfterDate = notValidBeforeDate.plusDays(30).plusMinutes(1);
        ModelNode result = shouldRenewCertificate(notValidBeforeDate, notValidAfterDate, 15);
        assertFalse(result.get(ElytronCommonConstants.SHOULD_RENEW_CERTIFICATE).asBoolean());
        assertEquals(30, result.get(ElytronCommonConstants.DAYS_TO_EXPIRY).asLong());
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
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.SHOULD_RENEW_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set(alias);
            operation.get(ElytronCommonConstants.EXPIRATION).set(expiration);
            return assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        } finally {
            removeKeyStore(KEYSTORE_NAME);
        }
    }

    private void revokeCertificate(String alias, String reason) throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE, ACCOUNTS_KEYSTORE_NAME, ACCOUNTS_KEYSTORE_PASSWORD);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1v2", "mailto:admin@anexample.com");
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
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.REVOKE_CERTIFICATE);
            operation.get(ElytronCommonConstants.ALIAS).set(alias);
            if (reason != null) {
                operation.get(ElytronCommonConstants.REASON).set(reason);
            }
            operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY_ACCOUNT).set(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            assertSuccess(services.executeOperation(operation));
            assertFalse(keyStore.containsAlias(alias));
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
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
                .setSignatureAlgorithmName("SHA256withRSA")
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
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve(keyStoreFile), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/test-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(keyStorePassword);
        assertSuccess(services.executeOperation(operation));
    }

    private KeyStore getKeyStore(String keyStoreName) {
        ServiceName serviceName = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(keyStoreName);
        return (KeyStore) services.getContainer().getService(serviceName).getValue();
    }

    private void addOriginalKeyStore() throws Exception {
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("test-original.keystore"), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/test-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        assertSuccess(services.executeOperation(operation));
    }

    private List<ModelNode> readAliases() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIASES);
        return assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
    }

    private ModelNode readAlias(String aliasName) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ElytronCommonConstants.READ_ALIAS);
        operation.get(ElytronCommonConstants.ALIAS).set(aliasName);
        ModelNode alias = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals(aliasName, alias.get(ElytronCommonConstants.ALIAS).asString());
        return alias;
    }

    private ModelNode getExtension(boolean critical, String name, String value) {
        ModelNode extension = new ModelNode();
        extension.get(ElytronCommonConstants.CRITICAL).set(critical);
        extension.get(ElytronCommonConstants.NAME).set(name);
        extension.get(ElytronCommonConstants.VALUE).set(value);
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
        Path resources = Paths.get(ElytronCommonKeyStoresTestCase.class.getResource(".").toURI());
        File file = new File(resources + nonExistentFileName);
        assertTrue (! file.exists());

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + nonExistentFileName);
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        if (required) {
            operation.get(ElytronCommonConstants.REQUIRED).set(true);
        }
        return operation;
    }


    private void addCertificateAuthorityWithoutStagingUrl() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.URL).set(SIMULATED_LETS_ENCRYPT_ENDPOINT);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityAccountWithCustomCA(String alias, String contactURL) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.CONTACT_URLS).add(contactURL);
        operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY).set(CERTIFICATE_AUTHORITY_NAME);
        operation.get(ElytronCommonConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronCommonConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(ACCOUNTS_KEYSTORE_PASSWORD);
        assertSuccess(services.executeOperation(operation));
    }

    private void removeCertificateAuthority() {
        ModelNode operation;
        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
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
                "  \"oyv-_7dfkxs\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
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

        final String NEW_NONCE_RESPONSE = "zinctnSRWv_CmPz5dhMgom1tppGmuXIqB8X8pZO_0YTF1Nc";

        final String NEW_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJsblhXQ1JDUUpLOW93RXMzMVZrVFNLLWZ2aDcxMDVYOVhYNUFRaVYtbXgxRkJtdU95OVBBS3JTTUlNcXR2ZXk2REhRY0gwVzc5OGp4X3MwRFFmekhCb1E1aU56YUFtVDFhMG5xN3hBemhsRGM5YXZ6WndiTDl0WW1nV3pDM0VQZm1PcXVlNTFoQkZ0VzN3X29IaHpaeTU5YUV2bmZra0hfalJLYUdZRFlPb3F4VXNfNmNXMTVGV3JmWERhOVFIUEVkUEdHTEt6ZTF1aW92bGxON2dNdWZkUFdzZElKRGhwOU1JSGhhVTZjOUZSSDhRS0I1WXlSd0dYR0ZpVmNZU1cyUWtsQkxJN0EzWkdWd2Y5YXNOR0VoUzZfRUxIc2FZVnR3LWFwd1NSSnM1ZnlwVTZidFRDS2J0dUN3X0M0T1FtQXExNDRmdkstOEJSUk1WaE56Qkh6SXcifSwibm9uY2UiOiJ6aW5jdG5TUld2X0NtUHo1ZGhNZ29tMXRwcEdtdVhJcUI4WDhwWk9fMFlURjFOYyIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6ZmFsc2UsImNvbnRhY3QiOlsibWFpbHRvOmFkbWluQGFuZXhhbXBsZS5jb20iXX0\",\"signature\":\"OhI4lDd6BsTlKvqMsiBY8bCnfozYsclQPpB7apFVuP0BTfO9iUbybiZ1gHDRGsyUF84gMoBaozZX6iMIApBW9j21uQuWBCGyn-wyM_Fu6n5ruenNQPYyiQteiVYP36oXuSKT76AnsoqXbX5NHfvjOlPiREmD95sfKRuvlsDlgaRD1hGU5qFNt9gTr90vVADPrMN20O0QKSCx5d4cKjm2BvD4oM4xA-Qll2HCREeb40F7eeIGUdCxHflHQOPObm2JBHm2lhOieankj0HPunP43L607iCZ8W2DAaX6EKDfMYunnnbpj9vXkkRUm7yEi4LNRs6OS4Hc-LHqKsgWoWc3kQ\"}";

        final String NEW_ACCT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"urn:ietf:params:acme:error:malformed\"," + System.lineSeparator() +
                "  \"detail\": \"must agree to terms of service\"," + System.lineSeparator() +
                "  \"status\": 400" + System.lineSeparator() +
                "}";

        final String NEW_ACCT_REPLAY_NONCE = "tarobHhNBawhfG-BpSmsBEpiESawT4Aw_k-sX2rqwjl-Mac";
        final String NEW_ACCT_LOCATION = "";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(NEW_ACCT_REQUEST_BODY, NEW_ACCT_RESPONSE_BODY, NEW_ACCT_REPLAY_NONCE, NEW_ACCT_LOCATION, 400, true)
                .build();
    }

    private ClientAndServer setupTestObtainCertificateWithKeySize() {

        // set up a mock Let's Encrypt server
        final String ACCT_PATH = "/acme/acct/10";
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"g01M7uvywHo\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "zinci6YzZrHxreJbW6Llpxjrnnw1mkm8eKsy5iUESlKMp0k";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJpcVZkd3laNGl0VlNoOFVWX2Z3NlpnVjh3Mk56SEtGdzZWeWl5cGRLMmlyUkk0T3BMdWhJNEhqQ3pSTHR0WkJPX3ZLRjFZaTB1dVdMaFFzMnVpWlJ5eXFCa0R6SXU3UnIwZWp2T2UtLVc2aWhLanE2WnNCQ2Q3eDhMUl9yYXp1X242V1BkQWJZeWZxdnBuS0V0bGZxdW4yMWJnWk1yT1R4YW0tS0FNS2kyNlJlVi1oVDlYU05kbWpoWnhtSzZzQ0NlTl9JOTVEUXZ1VG55VFctUUJFd2J2MVVOTEEtOXRIR3QyUzQ0a2JvT0JtemV6RGdPSVlfNFpNd3MtWXZucFd5VElsU0k3TmlNMVhKb1NXMHlSLWdjaFlRT1FuSEU2QUhtdk5KbV9zSTlZN0ZhQmJVeVJpS0RnTi1vZlR3cXNzdzZ2ejVucUxUanU3Y2dzWld4S1dESHcifSwibm9uY2UiOiJ6aW5jaTZZelpySHhyZUpiVzZMbHB4anJubncxbWttOGVLc3k1aVVFU2xLTXAwayIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AbXlleGFtcGxlLmNvbSJdfQ\",\"signature\":\"dRwzfHcuS_7Lsg_KnqA5tCCYpECu_BnCLAqxUIvczD4mDMPBKwRUnyKnd86jxr7v1PRzMF77S7pcXfodHCDPEuSsfmmvDhZs7fVDuT4U4d15Ls5A7HgN0RKOmbkRxJOGzHZ-07428e_sFGbApeNwj6xB6SWCuxMrGES7g9i0McNjPkbhB4IFo9_TCQ3KQGfFE33YHC0_DEscC3QT7y5SaMU0HE51XnpMIxmnULXjgQU_ugBvooNjQ2xkzlU3eTyXgTNkX3zFS06PkuYmGfHvakfcsB15OvdOyzvMp-qw9TinJT7VUyf4GDeOFsTk5RN4h-2aOkUBhL88Jonmii2CwQ\"}";


        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "taroNVru6SF-j0qWY8im0bC0tYef6U-RfcPvtdjJ1JOktV4";
        final String ACCT_LOCATION = "http://localhost:4001" + ACCT_PATH;

        final String QUERY_ACCT_REQUEST_BODY_2 = "";

        final String QUERY_ACCT_RESPONSE_BODY_2= "{" + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"iqVdwyZ4itVSh8UV_fw6ZgV8w2NzHKFw6VyiypdK2irRI4OpLuhI4HjCzRLttZBO_vKF1Yi0uuWLhQs2uiZRyyqBkDzIu7Rr0ejvOe--W6ihKjq6ZsBCd7x8LR_razu_n6WPdAbYyfqvpnKEtlfqun21bgZMrOTxam-KAMKi26ReV-hT9XSNdmjhZxmK6sCCeN_I95DQvuTnyTW-QBEwbv1UNLA-9tHGt2S44kboOBmzezDgOIY_4ZMws-YvnpWyTIlSI7NiM1XJoSW0yR-gchYQOQnHE6AHmvNJm_sI9Y7FaBbUyRiKDgN-ofTwqssw6vz5nqLTju7cgsZWxKWDHw\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@myexample.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2019-07-18T15:01:10Z\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "taroq4c6_F5_CIj2KA3dyxojYl4TX3k2jHoGBqchbp9D3ks";

        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTAiLCJub25jZSI6InRhcm9xNGM2X0Y1X0NJajJLQTNkeXhvallsNFRYM2syakhvR0JxY2hicDlEM2tzIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LW9yZGVyIn0\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoiaW5sbmVzZXBwd2tmd2V3djIuY29tIn1dfQ\",\"signature\":\"Ec9GvU8kMXffSZ2nsXDzNxkZDRwQKJRZrDk4ijg4C-Xsvpk5ggfb9gN7-5LFJzhpxqBSjgZsA6EBG_bRNPN7th5mJWqhOfIGU4UbxOuIdirvprlBbuv_EHucGIncwlFfju5uAnJsnQQrZzsWj6yvaj2vpgvdSpzanmIsDbO4dRN9NDwntORy95Px54FGt9TxBrI1kGXLGwA6SX5qOSeLu-aMqys2kzf5Tnox-rmnvAKVaym3vWSX2wBHmnVGiynIAupW9Znakeqw2lOWWQdG_0ybXphvyAjnMgPgjSd28b02lz9Rs3N25Pg5zT4BnrRDrJY734-tIC5VoY2cm3wb3g\"}";
        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T20:20:42.047150201Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwewv2.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/v2/47\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/10/69\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "zincTg-qOV-A1AthHGt9XKWZjPyd7459Ab5bFS2Rsb-k2pU";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/10/69";

        final String AUTHZ_URL = "/acme/authz/v2/47";
        final String AUTHZ_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTAiLCJub25jZSI6InppbmNUZy1xT1YtQTFBdGhIR3Q5WEtXWmpQeWQ3NDU5QWI1YkZTMlJzYi1rMnBVIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYXV0aHovdjIvNDcifQ\",\"payload\":\"\",\"signature\":\"F2rVeV76ynMI6nt_4oncsrx4m7-hnraZPz0rUyVHHkaoRUicAFgI0MlXszbFEzMhJ4vztfq5ovhBWfWKKxK2oUZGLqW834-HHKByyAiOUSgOSw0naLxFs96U09OAPQGBTcd1z9bsVmXG8orVIxxoKVDjQzkxfb0L1QC0FdNx-Vl0R6CKDdgLbUPKR2pbagSVKMJ8HPFWuYYnUeQZqmr3i0vkjNQRHO6pFcLPRXSHwywKO73JaGDbynOSu-MAWmkSQ2TaMfNQxrmaSjgRvB1K0wJXjCz3nyO7xYraC10ldbLwEmTu-DDIVJ0zyMgAEp3NGYYx-77qUHp_2UCrrY5KhA\"}";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwewv2.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T20:20:42Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/47/z7sh0A\"," + System.lineSeparator() +
                "      \"token\": \"215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/47/mlvrMA\"," + System.lineSeparator() +
                "      \"token\": \"215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/47/_wf1QQ\"," + System.lineSeparator() +
                "      \"token\": \"215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String AUTHZ_REPLAY_NONCE = "tarozNXDL3x4apxYH38Mz3y3oDRdMLMp8fujKrf8JtplOww";

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTAiLCJub25jZSI6InRhcm96TlhETDN4NGFweFlIMzhNejN5M29EUmRNTE1wOGZ1aktyZjhKdHBsT3d3IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2hhbGxlbmdlL3YyLzQ3L3o3c2gwQSJ9\",\"payload\":\"e30\",\"signature\":\"V-wYzOrZNxeHBxcVEoGvo5ISccYRSBVRiTajcmSla04f9SCm1nvgeE7u0N0Uc07ATlEqBTSvqN0v3iiLGuMxff-xEePQ15DsEUv8aN231ZiUvGalZYtV8UCM_8yz9Ixc4dgFJEBzY3zhyV3yKiPteF1hh6QMyTmk9erOuWJqIuWuluKo3vbF1K5A7z5E0Uq1uSysMQ5h-c5wqY7eDQzrGZ-PadSzUXXzAMKXqpNizpPRF3PfE0D6R3AiutunSgNCkd4qYgEGqZ1Xx8_caJ_s_2dHuNPbyDHShesAL9vp0SIs0etfzCbAYJFut6erA5hfyJDyIRl4VadC9obNSn_zqA\"}";
        final String CHALLENGE_URL = "/acme/challenge/v2/47/z7sh0A";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/v2/47/z7sh0A\"," + System.lineSeparator() +
                "  \"token\": \"215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "zincdmVABCB6HjsLMqEUskJi720sehqIUtrhyOeUOZ3IrCU";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/v2/47/z7sh0A";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/v2/47>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc";
        final String CHALLENGE_FILE_CONTENTS = "215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc.N6GU8Z78VIOWz1qOEJObBvhcmfflldy-TQWkizoonrU";

        final String UPDATED_AUTHZ_REPLAY_NONCE = "taroT16QLPZ628OzZtwZU6yA6-hnbfWexEyGOC-H5ji3hJw";
        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwewv2.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2019-08-22T20:20:42Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/47/z7sh0A\"," + System.lineSeparator() +
                "      \"token\": \"215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://inlneseppwkfwewv2.com/.well-known/acme-challenge/215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"," + System.lineSeparator() +
                "          \"hostname\": \"inlneseppwkfwewv2.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"172.17.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"172.17.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/47/mlvrMA\"," + System.lineSeparator() +
                "      \"token\": \"215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/47/_wf1QQ\"," + System.lineSeparator() +
                "      \"token\": \"215ACi-BkIjZ28ynW7kiDco21cv4wxRyByykbgxl9Pc\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTAiLCJub25jZSI6InRhcm9UMTZRTFBaNjI4T3padHdaVTZ5QTYtaG5iZldleEV5R09DLUg1amkzaEp3IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvZmluYWxpemUvMTAvNjkifQ\",\"payload\":\"eyJjc3IiOiJNSUlFdHpDQ0FwOENBUUF3SURFZU1Cd0dBMVVFQXd3VmFXNXNibVZ6WlhCd2QydG1kMlYzZGpJdVkyOXRNSUlDSWpBTkJna3Foa2lHOXcwQkFRRUZBQU9DQWc4QU1JSUNDZ0tDQWdFQXBjMDZSYnJSRDF2Y3pCQ3k1ZnhodXNTSTdPLW96MlYzd293NURIdUFlN2h3LTNaWDd1SG1Pb21PUHlSLUQ3NGtONEdpckNTSWlUcE1GRC1GcUxFR3hCbXhPMjY3dUlvTnY2SjVrZHlJT0VjMkl4RldacnRyTExWd2NHeC1ROWRhcWZVMWk4cW5YMWx2RkMydUM4Vlp6OU14U0MwV29jNjN3RTVCRnJVQjFPSTQxZDlINVpwYVhRSGctY1ZraXBWWEN2TWJFVnZ1MFVKNkV5eS1JX2pSSFh3QnFYcXhZa2NtMXJuTkFBUUxKc3U3ZThyU3JIV0dNREktcy1lLUsyYm51VGZnelBJSmZZQ19peHplQ0hXU3lnQmV4d1NUd0VfOC04QWJ2SlotQmVjZl9ndnVSQm42TGtYX1FkUndnRWN5d1pGUldka1VvMG90M2xRbFF5a3g1VjFZZ1k3YXMtaEFDZmpSdXRlSFAzUXkyNE1rSmJwLV9rLThDNFp1aGRUQ1pmNlJoRWR1MGpGNF85blhLUkhGUkVOZFUzZThtaENEdFBkblBVeFFoVmoyVjdmZ1hIV2RMZThzdUpkLTlaRjBKdkpqUVJVNEdtWE5qNkxpd3dDWjBQamZZSV9mai1XTFVKVWstMXVSZk0xUDRqRE5jQ01yNjlMdVhoS0U1b2JRYXVEOUxoOF8ydmtQeG1nSTc4T1E3STZ4T2JOWWg2M0hiUHBpelJZcUwtcW53SHcyck45dXZ5S2xTSnh6NW9LVWJPTzdHa3B4bV9TQWg4ZmduOTNYbTZEZFlMbk1zZFlHdGFKSjRnRHBpUVVLTldWeC04SW4wcXBFWUxtNEdsVVNTNWViUnJWQ0JkSjI4TGNaRF8zYktCMHNMMlRVdXk2eE96LS1KdmZLVnBzQ0F3RUFBYUJTTUZBR0NTcUdTSWIzRFFFSkRqRkRNRUV3SUFZRFZSMFJCQmt3RjRJVmFXNXNibVZ6WlhCd2QydG1kMlYzZGpJdVkyOXRNQjBHQTFVZERnUVdCQlQycUd6RUQyTlVidkxnSGhsVVlDZXREczJ0N1RBTkJna3Foa2lHOXcwQkFRd0ZBQU9DQWdFQW1hcVNwY1EzYlZBbEZuNUROYkhfNEFWaHdBdVV1VzlpUkNNSkt4TS16aEVUT1hOdElVdWFsLUFlTEdkMkFCQkdHLUJQcmtVUkR6a21BbTlKOWJHcEJ5UzFDV1A4d19XcmdvRTdlcmp4NjUwSGRFc05JTk80dzhHck4zb2VCdFVUV01KaC0wdWNULXRBN20yUGstaWpPN0NRYW90cmZuUUxXY0pPV3JYM3FWU09vZ204dVpRWWlKM0tENTdXSTNmenZHUTdaM2pNSXhQdk45b0xkLVY3S1JmRGdmNXdXaDJYYVNTM2RWRkY5TFJKaTR3X1RRWjk0eDhtLXE4Y1gxMU9ybXliNGVUYmZiZDJ2MkptRVJhWE8zeUs5SkFpV2lrTHdwbzk0MFMxVHUyWm10YWNmLVQ2elp3eFdONG5UMEJob3dkc3duNmdmQkdFUE9URUduVHZDZ2RiUHFScTZBbWQ0R2NKOFNVVjhJRmpXTC1zTG92VzlSc2VJaDY4dzJab0t3NTFua2ZGX3RXdkVxY0RILWtOajdHRHZCc0xaYTIyWnptV3lNTjRTQlJjdjZKX19OcnlidWowODhVVmlyc2JWcnMzMmQzMFVfOG1TbXBqaURqcmw1dFlyTVd1NUp6U3RNalczUEgzY0hTcFdXM1V6VEVpN2JjQVE1N2ZmZW1VRjlaa2FTOWltdHl4eXZYeW8yNTA2NzNjdEh4aUNCbHhXVm9VTGtZTmswZ3NfdDhLWkEyMUkyblhKQzNOT2dKMi1BRWFPaFQ5X3lkekpvMzh0UmR0MkkwSy0zZ1ZqSUxiYXhWZEJuWGU4cnYwRllaSmFyWXhfdHhXa1VtcE1GZ3dTSllNS0tUa2FzZzZJb1N6bURDcXFKb3d0RmpIMWtSUHRHMWdSMjFVNUVJIn0\",\"signature\":\"RQ9ONpJ_ZFH7x2d0KKvEiv_enItUtRUS2tpvpt9tQVSihOOj9x-UVbSpb5QAc8MNzqAYA_vLzVYu3vj7TwMtUaVTthsaKh6-cb_0NZYqmch6JqNrUcl9G8A2zLXKzkQDbgdNWSKhg19c56HtmW4I2R3YLYZ-j9_zV7C6b0UGgIabLv6WU2wjE7teeCIRNCabkM9aQr_PLf-muaScL_xLDkfr8q5d0UeO2UoXkxwyQDOfY3y5QTHD9eyW7PG3iXSKCZCex3RLZt3PKbZJX2o2Ia8R6z8ZO3IDG3Nqs6OMzgn3hdqB1zA4TJ98NRP3p5RcQkGy7BhPaZEKjkynEZDzCA\"}";
        final String FINALIZE_URL = "/acme/finalize/10/69";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T20:20:42Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwewv2.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/v2/47\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/10/69\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff4d6960900116d153bd9079b2ef4ceb686e\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "zincMfatmlrYZzGRlf2Lob__5LTX8HmpWUDRDcl7nR6e27Y";
        final String FINALIZE_LOCATION = "http://localhost:4001/acme/order/10/69";

        final String CHECK_ORDER_URL = "/acme/order/10/69";
        final String CHECK_ORDER_REPLAY_NONCE = "taroYphvMFXUwHY9NvweFKTFO2ZEHkqXXAAFIGgwRq1XUk8";

        final String CHECK_ORDER_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTAiLCJub25jZSI6InppbmNNZmF0bWxyWVp6R1JsZjJMb2JfXzVMVFg4SG1wV1VEUkRjbDduUjZlMjdZIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvb3JkZXIvMTAvNjkifQ\",\"payload\":\"\",\"signature\":\"N2BAMYSzcrwWhEjFkwBcKSqwe2z2C0p8rOIyF3K-roYwUMX-pA4pjA1dEgpn28YmcuL-oQ3r1dVGirf6lP7-ge1jVClLvoe0xMJnt8wtL5-RfM4huwxCf8nJHilXGAIjexU-23U1gqORTNseUEjmq5C0qM4VO-jI1gctmmpBhh1ND34C0LqwvbgBuErJu3i2aTOJeVa9zslvRNv54qQIH44NN4jaJP_rOkIf5Ip3ol9lhUNp9aWhF1cPrxy8dRoV8wB-zrSidqZcyrdW5S-kBpTU54xQNyerKWJuSBxYMlWWqwwJTxJIDULf3sOBUMIdHITHnD0NVmXA9Hyt4Qi_hQ\"}";
        final String CHECK_ORDER_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T20:20:42Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwewv2.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/v2/47\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/10/69\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff4d6960900116d153bd9079b2ef4ceb686e\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CERT_URL = "/acme/cert/ff4d6960900116d153bd9079b2ef4ceb686e";
        final String CERT_REPLAY_NONCE = "zincKaLFWirFsBTqH1JdVRy2kiSYehDe_Jl_POZcX9Oio2o";

        final String CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTAiLCJub25jZSI6InRhcm9ZcGh2TUZYVXdIWTlOdndlRktURk8yWkVIa3FYWEFBRklHZ3dScTFYVWs4IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2VydC9mZjRkNjk2MDkwMDExNmQxNTNiZDkwNzliMmVmNGNlYjY4NmUifQ\",\"payload\":\"\",\"signature\":\"Ba_xGjJri5ZxNyav1uTH7TPgmzrtCNwwYcVUUn7TX4lhWKPUjYM0QDqUnbDMjt34rY1xe3apIoH4xvLCwaQ-R90ad8sXYr0nBVwRTUPFY0ytDQAFBfwUMLRJ61d8PXZOXp3TNJ6QE-OzgONGDXaw1P2MUvbXbacA5ePsi3_D50fi9RVJGO88Znj-GmgmRm7nP1QlN2Ce-puNt1FoQhM3KFBslnDc5gQPPG1iV7QXyx7o8driUeqY5x87oOXjUfDjzw1LjNEh4ULAGXBeRwZ7jTJSqVYQHh_bnC3nkMxoBRq9sJGoVoNlwhy_d4pavYzUe1oreb1HVeEePicg-PKZ4w\"}";
        final String CERT_RESPONSE_BODY = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIGSjCCBTKgAwIBAgITAP9NaWCQARbRU72QebLvTOtobjANBgkqhkiG9w0BAQsF" + System.lineSeparator() +
                "ADAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTAeFw0xOTA3MTYxOTIw" + System.lineSeparator() +
                "NDNaFw0xOTEwMTQxOTIwNDNaMCAxHjAcBgNVBAMTFWlubG5lc2VwcHdrZndld3Yy" + System.lineSeparator() +
                "LmNvbTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAKXNOkW60Q9b3MwQ" + System.lineSeparator() +
                "suX8YbrEiOzvqM9ld8KMOQx7gHu4cPt2V+7h5jqJjj8kfg++JDeBoqwkiIk6TBQ/" + System.lineSeparator() +
                "haixBsQZsTtuu7iKDb+ieZHciDhHNiMRVma7ayy1cHBsfkPXWqn1NYvKp19ZbxQt" + System.lineSeparator() +
                "rgvFWc/TMUgtFqHOt8BOQRa1AdTiONXfR+WaWl0B4PnFZIqVVwrzGxFb7tFCehMs" + System.lineSeparator() +
                "viP40R18Aal6sWJHJta5zQAECybLu3vK0qx1hjAyPrPnvitm57k34MzyCX2Av4sc" + System.lineSeparator() +
                "3gh1ksoAXscEk8BP/PvAG7yWfgXnH/4L7kQZ+i5F/0HUcIBHMsGRUVnZFKNKLd5U" + System.lineSeparator() +
                "JUMpMeVdWIGO2rPoQAn40brXhz90MtuDJCW6fv5PvAuGboXUwmX+kYRHbtIxeP/Z" + System.lineSeparator() +
                "1ykRxURDXVN3vJoQg7T3Zz1MUIVY9le34Fx1nS3vLLiXfvWRdCbyY0EVOBplzY+i" + System.lineSeparator() +
                "4sMAmdD432CP34/li1CVJPtbkXzNT+IwzXAjK+vS7l4ShOaG0Grg/S4fP9r5D8Zo" + System.lineSeparator() +
                "CO/DkOyOsTmzWIetx2z6Ys0WKi/qp8B8Nqzfbr8ipUicc+aClGzjuxpKcZv0gIfH" + System.lineSeparator() +
                "4J/d15ug3WC5zLHWBrWiSeIA6YkFCjVlcfvCJ9KqRGC5uBpVEkuXm0a1QgXSdvC3" + System.lineSeparator() +
                "GQ/92ygdLC9k1LsusTs/vib3ylabAgMBAAGjggJ8MIICeDAOBgNVHQ8BAf8EBAMC" + System.lineSeparator() +
                "BaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMAwGA1UdEwEB/wQCMAAw" + System.lineSeparator() +
                "HQYDVR0OBBYEFPaobMQPY1Ru8uAeGVRgJ60Oza3tMB8GA1UdIwQYMBaAFPt4TxL5" + System.lineSeparator() +
                "YBWDLJ8XfzQZsy426kGJMGQGCCsGAQUFBwEBBFgwVjAiBggrBgEFBQcwAYYWaHR0" + System.lineSeparator() +
                "cDovLzEyNy4wLjAuMTo0MDAyLzAwBggrBgEFBQcwAoYkaHR0cDovL2JvdWxkZXI6" + System.lineSeparator() +
                "NDQzMC9hY21lL2lzc3Vlci1jZXJ0MCAGA1UdEQQZMBeCFWlubG5lc2VwcHdrZndl" + System.lineSeparator() +
                "d3YyLmNvbTAnBgNVHR8EIDAeMBygGqAYhhZodHRwOi8vZXhhbXBsZS5jb20vY3Js" + System.lineSeparator() +
                "MEAGA1UdIAQ5MDcwCAYGZ4EMAQIBMCsGAyoDBDAkMCIGCCsGAQUFBwIBFhZodHRw" + System.lineSeparator() +
                "Oi8vZXhhbXBsZS5jb20vY3BzMIIBBAYKKwYBBAHWeQIEAgSB9QSB8gDwAHUA3Zk0" + System.lineSeparator() +
                "/KXnJIDJVmh9gTSZCEmySfe1adjHvKs/XMHzbmQAAAFr/HHFxQAABAMARjBEAiAX" + System.lineSeparator() +
                "5d7ffdn8ctWiJWoPxYtGOwG5yaiqTpHSaiBagPd2xQIgGi68uA+M6MHf80Ko0rgk" + System.lineSeparator() +
                "Mtp5dkS7p9C8R11o2WCdnBsAdwAW6GnB0ZXq18P4lxrj8HYB94zhtp0xqFIYtoN/" + System.lineSeparator() +
                "MagVCAAAAWv8ccXFAAAEAwBIMEYCIQD/+kWwYf5pRIKjg5j2VcvUY9IOsKaem/wX" + System.lineSeparator() +
                "1C/GFU9+VQIhAOHY9SmknOkqMl8HwEYGeD7uksNwTBMzi19Hqowh4s55MA0GCSqG" + System.lineSeparator() +
                "SIb3DQEBCwUAA4IBAQAS1xht2BdputK1iEdEPG6357zcv5F65aBSOdqIc5WeEoa1" + System.lineSeparator() +
                "Jx06Wyc+5dQH65iCRF7qXQGb9gP0Bwi6JrfouQMQwjNjERg20CfqfLZdJqeUR+SO" + System.lineSeparator() +
                "wTZTqVrTQsdKPGUeGrdusC7gHMyvFagqf4J/gonHZvlI3FdGOEP3MiyHeQlsALRW" + System.lineSeparator() +
                "6WAX3okXEEm91chmdXuU3TRN/ZRNumU6z1J4RHYCW405qxKQWQB8NUIADBSVfm7x" + System.lineSeparator() +
                "J8KlZBgRH37R1FbIWHmD5W83cTEYRxPeYfS0HGel0wuPnt/JmbJzMcEeh75tDsRI" + System.lineSeparator() +
                "w/XRgmIMfizJa4LJLcXnUE+9ccraCB/quPldjjEN" + System.lineSeparator() +
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
        final String ACCT_PATH = "/acme/acct/18";
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"9DRXKSMFUn0\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "taroFCThaeLOJydoXXRny52fFmFvcqyS_JMmIJr66Zqp2JU";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJxTjcwc2VsZEVrVTlKV0RMUkNibkp2QWo2WWs3UnV4QktUV0dNLTZaMUxsQlpXV1A1OGthbWU5cDA1THFLa05rdC1Yakc3Wkt5T1FQUXZ4cU5oRURKLTNpck93V0NzWi1BRk5aYU5BMlFoZ0dRYnB2MkRkRDIzMWdqVUZRT3dlS2pLdlhDdUg1TFlxVjVObUx0TFBNbjBsTFpFX21NVlB3dnhLTV9FLTBjRFhqeDZzTU9BbWtvVWkzWGRaOU0tUUNNc1BxTUhnbUt1T2xCUnVXdmRJNVRDaUcyemNXMFo2T0Y5T0lybVhoWTBFeVNDTjc3RkJ2dkJNN2NsYkNiN1gxS0ZGLVNpUDQ3a013VlNyR2h1eER6dVpzaU4wOThxT3IwVG8yd1BzQ2V5SzZwT3ZST3VwQi1ZX01yXy1QRjl5dnE5bXkzLVBXeVAtcnB3T09XVWZYZXcifSwibm9uY2UiOiJ0YXJvRkNUaGFlTE9KeWRvWFhSbnk1MmZGbUZ2Y3F5U19KTW1JSnI2NlpxcDJKVSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AbXlleGFtcGxlLmNvbSJdfQ\",\"signature\":\"YhNeycG9CwuJvGeN61TqI5d88SWif9AFl2zPx2tqQAdvn9fck8Kw4aixTgdu_JWprONuL-H3hDidOhVfkrWa-KQgVbK4jeOkyoIf2tzAFvXPDpa-cMqF6b6G9HwULfPxeLlLnhMLt94sgXzRFm5f00VgXNHgER8-PVI6Zf2x7oHBZvyfY663ZUuSkMPAFitRa4s6hGJj1FNlx5v82f_3tcVB331YlWPv_SldGeelPFjx9puuVgaReqVPHLmfAgdUbYbNTXT2Y4liuXYGS3NZJUKc_qzYlBzgO83cNepHeEaJ3xY1G3xKdQ1C7m9axRduJigTug1LxHocZbg6WxuH5g\"}";


        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "zincyZi3eMnAoR1uc1k8yxj7xpiOAPRMhwSQ4IM-hQQ1dAc";

        final String QUERY_ACCT_REQUEST_BODY_2 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InppbmN5WmkzZU1uQW9SMXVjMWs4eXhqN3hwaU9BUFJNaHdTUTRJTS1oUVExZEFjIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYWNjdC8xOCJ9\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AbXlleGFtcGxlLmNvbSJdfQ\",\"signature\":\"OjzGOzYKnNt0nLc8qY4VMzafwFmR-ywSRxEinsAbLzpulASz0xfTBxpidvR0xGx72g0hiLX7G3YAkmPcURtSMl61RVaTQRFZPjTQ12VkctB25QceQyiZQK0Am5B4AbIS6wv0ThV39JXqEASu7mVPERFkbKBiOEnvseASzyYfrnkWVur7z_YWSPCMqG0mtQ31YMRMxzXf8d9JQSnojzKRbBp6YiAyMYOIt4FiA10UMdWmPud5jojw5wKfXpmcJe2yz5cojN247XXNlz1ObkeA12b34M7NlcaRXv9H_ITGeR2cXbrv0TKVN8zkObqZNJjswE6Wl5DdqCWZz14N_Il0ZQ\"}";

        final String QUERY_ACCT_RESPONSE_BODY_2= "";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "tarofVbsbXN3xs8rOEaBjoQn9FmZ7nSiAAChR1qMsvOADyA";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/18";


        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InRhcm9mVmJzYlhOM3hzOHJPRWFCam9RbjlGbVo3blNpQUFDaFIxcU1zdk9BRHlBIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LW9yZGVyIn0\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoibW5kZWxrZG5iY2lsb2hndjIuY29tIn1dfQ\",\"signature\":\"gKs76q7TeUcXauHv8Hzky-mKP3BRrmjbjtsbY89_S46jAmSAd2Vs8lwR1KjuuEngROkKbsmVMRqRq-DrCnUzFxPhZVm5yfuqRgLYphaZXeffSABoR1iqyxvVYC0RkqoBLjkj8u8Ttbzq9I8H_-kGkdXyVbR35I9c0oK8gre40u0LtjtMY7Eiw89tA2r0YPnvldv-Osf3ocwlANHlV-W-tCrqxQuTI_MY78MLHyguWFdB8-wMEjbLle6HDNStGFJsidLmDh3qkvukTRyOwRuXiA9ER41oWeDFdzeHTTQnwn9kxvo8gmFX_FPL2b5HdwvEh7ebV0aOb_wnZ_aHQxKVCQ\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T19:57:55.887357634Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"mndelkdnbcilohgv2.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/v2/46\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/18/68\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "zincEh5_TsAbCPq_V2N78rG3_dUxcZnA6ISd3UBdGC3LN2E";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/18/68";

        final String AUTHZ_URL = "/acme/authz/v2/46";
        final String AUTHZ_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InppbmNFaDVfVHNBYkNQcV9WMk43OHJHM19kVXhjWm5BNklTZDNVQmRHQzNMTjJFIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYXV0aHovdjIvNDYifQ\",\"payload\":\"\",\"signature\":\"MAVAt8kSDWcy6rZRLz5ClGgXmLSTCRY5V0dhrDnA-3DMdnQyTnFI8NnmuxVBVpCLN1Sse2YGn1yrHp5I6L83Ob90OvCiWMZCi0or1hqxrmDErVE4q-BOKdV6sbrgejwNsDSkdtDaGSvGR8qSBeqFV6VWvny395qXY6ASzpkXgyeSXFGEQ-tlPW_CTAaHS7Eo_IKHQ8puJeOqxhUkgseQogtkC2ExddOnAiZq9M__SV6jysxJmqn-4vNdBmFJtq2F0aHrbWdf-9zUWnrxtgUPP-WvWCfUtejS5CcjDv8kcnvOumKm64b8eHMoGtPSxuGfZ_K1Qoh605KuiwD5lfaSkQ\"}";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"mndelkdnbcilohgv2.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T19:57:55Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/46/1EAfog\"," + System.lineSeparator() +
                "      \"token\": \"6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/46/OHt9Vw\"," + System.lineSeparator() +
                "      \"token\": \"6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/46/WXhsUg\"," + System.lineSeparator() +
                "      \"token\": \"6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String AUTHZ_REPLAY_NONCE = "taroi1V-ebsk9OogUGWzggfhZzPb_caysV4CnnQ4OdjClZ0";

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InRhcm9pMVYtZWJzazlPb2dVR1d6Z2dmaFp6UGJfY2F5c1Y0Q25uUTRPZGpDbFowIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2hhbGxlbmdlL3YyLzQ2LzFFQWZvZyJ9\",\"payload\":\"e30\",\"signature\":\"XAvtgCZ15XD37k4syk2smvoIoFm-WeXgqnKr74_L8W-AYou4roOQCRAMPE7rmboEFB4ZILxrec__6GN9OS99tdQ9o4T1CLgkwuZUDQFXFBI98tOKF_sjtTrVNQnMitmN2noAWxFBdl-6Mt_FO5tt8l2HG7TGPxriJe2oPDxoMBZszHGzz5GFYEhVcwpTiDWlri3ggXiHQyEWnei-TmO_O5p5a6hJm-TNEziyWDCvmlupRpwDyv9ANJccsrVg6OYVW3jQ1rDscAPqIILQr8EhzA4QyZw9FhrfgGJdn6ru1Wwl86Y8SfTojIq3wKmcac5c3iXBeIKkkq9clGONJpiNwQ\"}";
        final String CHALLENGE_URL = "/acme/challenge/v2/46/1EAfog";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/v2/46/1EAfog\"," + System.lineSeparator() +
                "  \"token\": \"6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "zincA0YMk-y4X0yRFWdOVUMHoGsfBE6qgdMPYPcX9-Q-zY4";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/v2/46/1EAfog";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/v2/46>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY";
        final String CHALLENGE_FILE_CONTENTS = "6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY.2NVC_ENUU-TZ83gkUxQvXl7_ixvttxk_dPlNqIyXGKY";

        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"mndelkdnbcilohgv2.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2019-08-22T19:57:55Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/46/1EAfog\"," + System.lineSeparator() +
                "      \"token\": \"6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://mndelkdnbcilohgv2.com/.well-known/acme-challenge/6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"," + System.lineSeparator() +
                "          \"hostname\": \"mndelkdnbcilohgv2.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"172.17.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"172.17.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/46/OHt9Vw\"," + System.lineSeparator() +
                "      \"token\": \"6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/46/WXhsUg\"," + System.lineSeparator() +
                "      \"token\": \"6PAmWU2m3sPP7Lqfz2NAlpztc-Qqw3o9rTbQtrFUrdY\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String UPDATED_AUTHZ_REPLAY_NONCE = "taro_CYDgtDCXgc4gmxsCnwXERad3qCJ6bOEG3ZKVdqMKCQ";

        final String FINALIZE_URL = "/acme/finalize/18/68";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T19:57:55Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"mndelkdnbcilohgv2.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/v2/46\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/18/68\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff1093811c2dca68b305c777d1d6b058cb14\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "zinclJZorHeTFZcnqLLeS1ZgNtVjKLFXeA-sZDk6Od996nI";
        final String FINALIZE_LOCATION = "http://localhost:4001/acme/order/18/68";

        final String CHECK_ORDER_URL = "/acme/order/18/68";
        final String CHECK_ORDER_REPLAY_NONCE = "taroRCnVpN7wg7EQvwrBvKkNUdyXISuSwN1_Y1huVn4IQOo";

        final String CHECK_ORDER_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InppbmNsSlpvckhlVEZaY25xTExlUzFaZ050VmpLTEZYZUEtc1pEazZPZDk5Nm5JIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvb3JkZXIvMTgvNjgifQ\",\"payload\":\"\",\"signature\":\"arc3WgxoV-CTvgMRboJhSQmNTNNE6NAHGdPkB6Io3j3fqPSqqyYtAKTKa0fTBhNS2uS7Vh5FOYPPG-11T9f-_asncxD0C43MYX-mWHtvCH_SmhXTyB2rm4kiWoRVjYHuffDgOrauPvmk4WbUY64sq7-7TSUpfDD9Ds6ll8ZDysA-7yG4UZjnGLgN4r-14uyWiSS4PQJYFLdzDsTnmLMjKL09uuC3hW3Sc-T3x8yQ9ONY6wstpyHbLVGL9ZikRpDf4ZWUJDXg-apngRHVwF7eUXAD6nNsERdnF7qBHll-51ItH-LKzfgXu7H9q-I8d1y3RNMx-lqvIuAmi5K2_FmkiQ\"}";
        final String CHECK_ORDER_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-23T19:57:55Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"mndelkdnbcilohgv2.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/v2/46\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/18/68\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ff1093811c2dca68b305c777d1d6b058cb14\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CERT_URL = "/acme/cert/ff1093811c2dca68b305c777d1d6b058cb14";
        final String CERT_REPLAY_NONCE = "zinctQYf_mnOJoVGDbAM1CdUSVnq_1X0yixRU_nAWuCrZkU";

        final String CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InRhcm9SQ25WcE43d2c3RVF2d3JCdktrTlVkeVhJU3VTd04xX1kxaHVWbjRJUU9vIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2VydC9mZjEwOTM4MTFjMmRjYTY4YjMwNWM3NzdkMWQ2YjA1OGNiMTQifQ\",\"payload\":\"\",\"signature\":\"BY_MtgoIMBOVgh34tkd8qMAWehaC3_II8ghIdkuXVTLqMETipZ97c0uD5zDJGw4BVStxnYvGHgdwk1aOSz8y5-i8IhbFS1dZYOgscaZp5V57VXIvar5t4mKklevafg9Vj3O2fNg5NKdGaJEn7ZgHutGcEyJrqEWqUq4nfYAn5dUZeABrfpxjzR4nksLSQjCdzZcmOHT-OYgb5UiI-IjCJcsZlBcIV_Y9oGKUDJ_BMg7sYS-NIQ7EatToVvY6NasB0gFPJ95vz_oz0cTRU439VCHF7u-YowXO2cXF9M8kak5Oaept8QFQFRBKDODqEIzLjBcP1Um6_ybPO0sLiJG4uw\"}";
        final String CERT_RESPONSE_BODY = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIEoTCCA4mgAwIBAgITAP8Qk4EcLcposwXHd9HWsFjLFDANBgkqhkiG9w0BAQsF" + System.lineSeparator() +
                "ADAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTAeFw0xOTA3MTYxODU3" + System.lineSeparator() +
                "NTZaFw0xOTEwMTQxODU3NTZaMCAxHjAcBgNVBAMTFW1uZGVsa2RuYmNpbG9oZ3Yy" + System.lineSeparator() +
                "LmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABNWdSNeeqVApfg5V6xDRpqac" + System.lineSeparator() +
                "CeW/MR8M8aaMAy37FLeVHewJ9+N/Lk6iov3/lNp6hTuzoljFZTD3/bD3+RVTkAKj" + System.lineSeparator() +
                "ggKeMIICmjAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG" + System.lineSeparator() +
                "AQUFBwMCMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFLUOwBDYB0/t83fH65E9+EhK" + System.lineSeparator() +
                "c+e4MB8GA1UdIwQYMBaAFPt4TxL5YBWDLJ8XfzQZsy426kGJMGYGCCsGAQUFBwEB" + System.lineSeparator() +
                "BFowWDAiBggrBgEFBQcwAYYWaHR0cDovLzEyNy4wLjAuMTo0MDAyLzAyBggrBgEF" + System.lineSeparator() +
                "BQcwAoYmaHR0cDovLzEyNy4wLjAuMTo0MDAwL2FjbWUvaXNzdWVyLWNlcnQwIAYD" + System.lineSeparator() +
                "VR0RBBkwF4IVbW5kZWxrZG5iY2lsb2hndjIuY29tMCcGA1UdHwQgMB4wHKAaoBiG" + System.lineSeparator() +
                "Fmh0dHA6Ly9leGFtcGxlLmNvbS9jcmwwYQYDVR0gBFowWDAIBgZngQwBAgEwTAYD" + System.lineSeparator() +
                "KgMEMEUwIgYIKwYBBQUHAgEWFmh0dHA6Ly9leGFtcGxlLmNvbS9jcHMwHwYIKwYB" + System.lineSeparator() +
                "BQUHAgIwEwwRRG8gV2hhdCBUaG91IFdpbHQwggEDBgorBgEEAdZ5AgQCBIH0BIHx" + System.lineSeparator() +
                "AO8AdQDdmTT8peckgMlWaH2BNJkISbJJ97Vp2Me8qz9cwfNuZAAAAWv8XOpXAAAE" + System.lineSeparator() +
                "AwBGMEQCID2B0PeB+cxwEuSIr0xakdkDi4zStQffSeXwy5Xi2z3XAiA9b2uo0QJu" + System.lineSeparator() +
                "W/rZoKjPxO53Q7LclMQYnHucagWfy7irXwB2ABboacHRlerXw/iXGuPwdgH3jOG2" + System.lineSeparator() +
                "nTGoUhi2g38xqBUIAAABa/xc6lYAAAQDAEcwRQIhAOdHz0WsKkHx9La5p6A4nfGP" + System.lineSeparator() +
                "HpeOvcNss3feW8qXvGKbAiAleyPIvJjZlXGMR/H5doyiG/uuJ3QLpGnzXH7tegx2" + System.lineSeparator() +
                "hjANBgkqhkiG9w0BAQsFAAOCAQEAJv2VRPTuDr73+G0aWZMgTSIyCfSutstBRf4S" + System.lineSeparator() +
                "skZy5OBdtvCJ+KZ//4aO8GzBLIK2oO15zb6J0LzGLBN8fh44zBEXyB82xV77xZsU" + System.lineSeparator() +
                "h3iXHVr/xJryq5vfxKLQdmSOxljZqIUb1ewk6z0lgVSCkSCysRYFFCO6FUVtCQtL" + System.lineSeparator() +
                "6QvB8fdtnIP0badkB5N2QJzJ7wqxi2J6HPVhEiCTRHzKjK1d5z8daaVQjd1XU4+x" + System.lineSeparator() +
                "GgpK++cGcpLrkQ8uCXGfIHz78wnnLxco19K6F41D1zGlevAAaHiG++NafW3/kJIN" + System.lineSeparator() +
                "hk6FdLvhm+dFuaXMBzFr9sSzXQo9tsady2/VwR55xlTaXeD31w==" + System.lineSeparator() +
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
                "  \"Cd6YLrbpxmk\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "zincntRzrBcL6fSvrCekkI48w39j89-rUjvGi7QYg9rdnys";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJxTjcwc2VsZEVrVTlKV0RMUkNibkp2QWo2WWs3UnV4QktUV0dNLTZaMUxsQlpXV1A1OGthbWU5cDA1THFLa05rdC1Yakc3Wkt5T1FQUXZ4cU5oRURKLTNpck93V0NzWi1BRk5aYU5BMlFoZ0dRYnB2MkRkRDIzMWdqVUZRT3dlS2pLdlhDdUg1TFlxVjVObUx0TFBNbjBsTFpFX21NVlB3dnhLTV9FLTBjRFhqeDZzTU9BbWtvVWkzWGRaOU0tUUNNc1BxTUhnbUt1T2xCUnVXdmRJNVRDaUcyemNXMFo2T0Y5T0lybVhoWTBFeVNDTjc3RkJ2dkJNN2NsYkNiN1gxS0ZGLVNpUDQ3a013VlNyR2h1eER6dVpzaU4wOThxT3IwVG8yd1BzQ2V5SzZwT3ZST3VwQi1ZX01yXy1QRjl5dnE5bXkzLVBXeVAtcnB3T09XVWZYZXcifSwibm9uY2UiOiJ6aW5jbnRSenJCY0w2ZlN2ckNla2tJNDh3MzlqODktclVqdkdpN1FZZzlyZG55cyIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"TDqm_Jpezttg3kUIskpO18JthPtxzm4Q6-bXhYUiiD409_LLHyWt8gpmdSZ2g1EdnvegpKRfGHe-svggQVwJc5sJZrg_s3KrfpC2LwaxvmK6J5czRakBY1HLDhvB-dfDufp9Hua5wTyCM3VJhBzuwxT04C8mBD6TXVMhwt-ELWTviageuVidWJNw9STAfohS3cfU2kDkbLENn93VdnuGwr5PIuvTB7pBbyy7C0VmIDWlh2E9kXTnv2QPmYiIZOkTfHgN1EbRoWdJx4QKIWazOCfTuMT41YJs460a2CnXAk51eo6XlS5gmSlNGux3Qg2S0bzOY1IIMoFQH0MYDGiSMw\"}";

        final String QUERY_ACCT_RESPONSE_BODY= "";

        final String QUERY_ACCT_REPLAY_NONCE = "taroJmBHDyFedEWgCGh75dU3FcP5ubQTK2-WTPz7vZoN3NI";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/18";


        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InRhcm9KbUJIRHlGZWRFV2dDR2g3NWRVM0ZjUDV1YlFUSzItV1RQejd2Wm9OM05JIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LW9yZGVyIn0\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoiaXJhY2x6bGNxZ2F5bXJjLmNvbSJ9XX0\",\"signature\":\"GgDQPFBK5AtUwG0100zmtcN0vaqWItG8neHt_UmyWme0zpdcdlDv7Kl_ayTPTRB0dzCEu3mrCbUsk2cHZu80_xkkmuTuGu4RRIpElhVAgkj8h5Gs5nmj1Rx8tY31GIyHgE4WhmQMurDaQUWEVzK8TMNFMuql_gr6b3vT577sTneDVQxRy5yLYzjDw60eQsm687CSHb8JgbUlh3qzz4NvC-bQxMAkHQctLU2WH7xyOyWNTWl49zFDaeaa_xC3FE01goHyToDEMOIuMKvvwXf4XooivWMSMqRlaaGwB81f3wzSNwjNdqsrY-pp2O5WJS2660RfRbWfwjIoQbAV6lNBmw\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-29T16:28:22.889849436Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/v2/67\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/18/99\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "zinc-xZ1copze4zkgzj2cwJVZ0lg2pZtUEZjaYVTpChDbMs";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/18/99";

        final String AUTHZ_REPLAY_NONCE = "tarolh01L8-AR79io0VLNh2X_gInZpiTubW0QhHIE2BPKK4";
        final String AUTHZ_URL = "/acme/authz/v2/67";
        final String AUTHZ_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InppbmMteFoxY29wemU0emtnemoyY3dKVlowbGcycFp0VUVaamFZVlRwQ2hEYk1zIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYXV0aHovdjIvNjcifQ\",\"payload\":\"\",\"signature\":\"DNpOi-b2YLtBrbIC98LUcovvbYwq0xq5nyc3KOS8e6rl90vf9-ZCLUeWfzjXjXfN4Hgie1zZMySIVb9Blbm9bb2GrhiPdCGv8SixFLEyB5i_ZNDuU15nUrs1dQricv9PH9TTf6YrBJGjQtSH4nkQIRX9K6rzQAX-mPRQtk7TXOxU1cztj0DRpnw9MgqOj3zX54N-2tl9hoeUFyErFGDxRxKHFiEAxuvlaNv30VN9OXyRLxl3qURNhJR1_9T_P5uk1QEraUruAZR5DX1mVYMJLwQKxrkAIEzAJscFj6d8DE01XO2On49x3UKLAdXpyJQeVzP2lJ1uRymwMYzpAu2whA\"}";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2019-07-29T16:28:22Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/67/jabkIQ\"," + System.lineSeparator() +
                "      \"token\": \"d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/67/nOqZzg\"," + System.lineSeparator() +
                "      \"token\": \"d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/67/5jeSaw\"," + System.lineSeparator() +
                "      \"token\": \"d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMTgiLCJub25jZSI6InRhcm9saDAxTDgtQVI3OWlvMFZMTmgyWF9nSW5acGlUdWJXMFFoSElFMkJQS0s0IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2hhbGxlbmdlL3YyLzY3L2phYmtJUSJ9\",\"payload\":\"e30\",\"signature\":\"KOQARJFyGIZpsavmvmyjBPgnY4nSdBGlYCf4HukPe1iOLh-gwY6HTbh8SCjlNfEviWglwwUGUKeQGZaGhXu8dUZg7KnzqD5cN5unHXrJVlt3Atv-ysFXYYswTTihk7gRjHaNkFJ88hoomYdOG5jD_ijQUQyheqU_qsRJXNHw8z1dTdys_gwdDmML3PQNJ-5kkSYnIoBLViI1c8OhHWiBBOWzbeGUNc_nE9M_qJyBqcGRofctGcKlrGaajI4Zbf6Rp6Bc3L2oy1cDC_Ys4UJ-1k_wOeFeRLhhtjftKT3RTFTT9YsEGRxWaEwKnUAsWew8HkCfQjLf7phY6ZjMogubAg\"}";
        final String CHALLENGE_URL = "/acme/challenge/v2/67/jabkIQ";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/v2/67/jabkIQ\"," + System.lineSeparator() +
                "  \"token\": \"d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "zinc45dbw3ymv3Jy609ZCbK-JFYQ7-bukseiA0C9MCG1RaI";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/v2/67/jabkIQ";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/v2/67>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ";
        final String CHALLENGE_FILE_CONTENTS = "d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ.2NVC_ENUU-TZ83gkUxQvXl7_ixvttxk_dPlNqIyXGKY";

        final String UPDATED_AUTHZ_REPLAY_NONCE = "taroU2YGBQVFTtrEh8v1qkH5DxOMYWG0rO4ABhGAzt2A6yc";
        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"iraclzlcqgaymrc.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2019-08-28T16:28:22Z\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/67/jabkIQ\"," + System.lineSeparator() +
                "      \"token\": \"d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://iraclzlcqgaymrc.com/.well-known/acme-challenge/d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"," + System.lineSeparator() +
                "          \"hostname\": \"iraclzlcqgaymrc.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"172.17.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"172.17.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/67/nOqZzg\"," + System.lineSeparator() +
                "      \"token\": \"d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-alpn-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/v2/67/5jeSaw\"," + System.lineSeparator() +
                "      \"token\": \"d2fs_-qypCU68P4jck7U9k1JGdN-SlQbjfWUCIo8SAQ\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_URL = "/acme/finalize/18/99";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"urn:ietf:params:acme:error:malformed\"," + System.lineSeparator() +
                "  \"detail\": \"Error finalizing order :: invalid public key in CSR: unknown key type *dsa.PublicKey\"," + System.lineSeparator() +
                "  \"status\": 400" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "zincIG_1w_6ME9wlbC5CmT7FQ9h2LLtLFzONU5qh1bkaDh8";
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
                "  \"kgIgyHU3yA0\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String NEW_NONCE_RESPONSE = "taroDvhk2H7ErEkhFCq8zux1hCbY0KzFQDEFGjMaSvvCC_k";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoOE9lZTViZURSZ3hOUGVfZU1FOUg2Vm83NEZ1ZzhIZ3Jpa2ZiZkNhVTNsS0Y2NDhRRzFYMWtHRFpUaEF5OGRhcUo4YnY2YzNQSmRueDJIcjhqT3psNTA5Ym5NNmNDV2Z5d1RwY0lab1V6UVFaTFlfSzhHTURBeWdsc1FySXRnQ2lRYWxJcWJ1SkVrb2MzV1FBSXhKMjN4djliSzV4blZRa1RXNHJWQkFjWU5Rd29CakdZT1dTaXpUR2ZqZ21RcVRYbG9hYW1GWkpuOTdIbmIxcWp5NVZZbTA2YnV5cXdBYUdIczFDTHUzY0xaZ1FwVkhRNGtGc3prOFlPNVVBRWppb2R1Z1dwWlVSdTlUdFJLek4wYmtFZGVRUFlWcGF1cFVxMWNxNDdScDJqcVZVWGRpUUxla3l4clFidDhBMnVHNEx6RFF1LWI0Y1pwcG16YzNobGhTR3cifSwibm9uY2UiOiJ0YXJvRHZoazJIN0VyRWtoRkNxOHp1eDFoQ2JZMEt6RlFERUZHak1hU3Z2Q0NfayIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"WpICzZNwgHJckeO7fn8rytSB23poud38FEg1fwmVvd3rag-KLZ5rOrqmOzr6BIUpaY0DEeZ03QzQFYIoywVNg8Apvbh12RZvkWW_VuIfpnJOz6dWOhV-lM5aH9oVy7mW9nNVqzqlTEyWRXPGo8XSD_vWtxSu-zLbHOTvnURiiCOO3DM96xRZrnvexTI97RRO6cBrI4HzjSBpat03YOkwxEWzrbqdZD7RVgUxTh6ELK7BE1U87IF2iBO_V1VllUZdH9P2EiTtFBwj5xkBXhyeBiTj2BqWzb4-Y5o_W0b5hMX1IQiPa-zb56L-SkcEJMNu-hJSGmPy6uoRJVAwCHcP-w\"}";

        final String QUERY_ACCT_RESPONSE_BODY= "";

        final String QUERY_ACCT_REPLAY_NONCE = "zincgzgBIqMgfFmkcIQYn6rGST-aEs9SOGlh0b8u4QYFqiA";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/5";

        final String REVOKE_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNSIsIm5vbmNlIjoiemluY2d6Z0JJcU1nZkZta2NJUVluNnJHU1QtYUVzOVNPR2xoMGI4dTRRWUZxaUEiLCJ1cmwiOiJodHRwOi8vbG9jYWxob3N0OjQwMDEvYWNtZS9yZXZva2UtY2VydCJ9\",\"payload\":\"eyJjZXJ0aWZpY2F0ZSI6Ik1JSUZUVENDQkRXZ0F3SUJBZ0lUQVA4b01Ib3hOS19JbWdkN3lzelh3S2RXd3pBTkJna3Foa2lHOXcwQkFRc0ZBREFmTVIwd0d3WURWUVFEREJSb01uQndlU0JvTW1OclpYSWdabUZyWlNCRFFUQWVGdzB4T1RBM01UWXhOalE0TWpkYUZ3MHhPVEV3TVRReE5qUTRNamRhTUNFeEh6QWRCZ05WQkFNVEZtbHViRzVsYzJWd2NIZHJabmRsZDNaNE1pNWpiMjB3Z2dFaU1BMEdDU3FHU0liM0RRRUJBUVVBQTRJQkR3QXdnZ0VLQW9JQkFRQ1NaQllqclZ0S05jbFVhQV9nZ3hJaGNSVVdrOUVJbXBJMG5heWQwaURsYUdUWkxicTZVNHZvUWxzNkRDZmpzdzIwU2QzdTgzb2RqTDU2QUdOeHBIUzZaYTVjN3RLS1hGUmgwOVFjVEkzdFJwbWF5bFgwZXhWd2tVcllSaUdLSXA3Mjg2YzhWdi1qenk4VkkyLXlnaFpvLThHRDNjVm9XdFhEWnpWNWxnZlFGeGhDc2tUUjhWUXB6b0FOMm5Fai01UVRNdi1HdUhfSDJ5U3FNLWtTN0NwR2JUaDZZSUp2OVZfblFUaC1FTEFnY29ic01UN24tU2dfRTdUQ21nNmxqaWduaGRwdzZGMElMdWlfX3pJYzFEbG0yRC1UVi0tWm4yQnp1b2FkeFJlZ05TcXMyRkkyUDF3MXlSSHFHYmtTOEpabEY0LWg3MVhWV01kazFtWUVaRTVCQWdNQkFBR2pnZ0otTUlJQ2VqQU9CZ05WSFE4QkFmOEVCQU1DQmFBd0hRWURWUjBsQkJZd0ZBWUlLd1lCQlFVSEF3RUdDQ3NHQVFVRkJ3TUNNQXdHQTFVZEV3RUJfd1FDTUFBd0hRWURWUjBPQkJZRUZObThPaFpQbWYweWdhNUR6MHNGaTB3TXZ3cWJNQjhHQTFVZEl3UVlNQmFBRlB0NFR4TDVZQldETEo4WGZ6UVpzeTQyNmtHSk1HUUdDQ3NHQVFVRkJ3RUJCRmd3VmpBaUJnZ3JCZ0VGQlFjd0FZWVdhSFIwY0Rvdkx6RXlOeTR3TGpBdU1UbzBNREF5THpBd0JnZ3JCZ0VGQlFjd0FvWWthSFIwY0RvdkwySnZkV3hrWlhJNk5EUXpNQzloWTIxbEwybHpjM1ZsY2kxalpYSjBNQ0VHQTFVZEVRUWFNQmlDRm1sdWJHNWxjMlZ3Y0hkclpuZGxkM1o0TWk1amIyMHdKd1lEVlIwZkJDQXdIakFjb0JxZ0dJWVdhSFIwY0RvdkwyVjRZVzF3YkdVdVkyOXRMMk55YkRCQUJnTlZIU0FFT1RBM01BZ0dCbWVCREFFQ0FUQXJCZ01xQXdRd0pEQWlCZ2dyQmdFRkJRY0NBUllXYUhSMGNEb3ZMMlY0WVcxd2JHVXVZMjl0TDJOd2N6Q0NBUVVHQ2lzR0FRUUIxbmtDQkFJRWdmWUVnZk1BOFFCM0FCYm9hY0hSbGVyWHdfaVhHdVB3ZGdIM2pPRzJuVEdvVWhpMmczOHhxQlVJQUFBQmFfdm1ZVElBQUFRREFFZ3dSZ0loQUt0b2NobEprQm5idFduQzFLUjlVZ2p0TXZldFFjeUEyaTRvVE9rVjVTUFVBaUVBdEFqeXd6RW11eHRJcmc0US1oWExHTllqbVlFSWROSUxnVDBTZGVkUE5kQUFkZ0FvZGhvWWtDZjc3enpRMWhvQmpYYXdVRmNweDZkQkc4eTk5Z1QwWFVKaFV3QUFBV3Y3NW1FekFBQUVBd0JITUVVQ0lGVVJmVkJfUWVaV3N1dk1LVVpUVDJDaVAzR182OVkzVUNhV0pfNExYbWozQWlFQXBkQXVXMVU1T2Z5TF9ZcGVBRVBBbVpyOVpvNWFDejRsWU0zcTluTGh4V0l3RFFZSktvWklodmNOQVFFTEJRQURnZ0VCQURtODV2cHZFaU9VWWNQRXB3bE9RdlRJYW4ybm1MMWN0ejhUN0xLbmJpQ3pYX2lYcjNUY2FrSEV3NUVNVUk0ZEFEN3ZTRklIclhDem5GZTVlYTVQWGh2bi1KQjFFVnQtVnZmV3lGQXlWLVBuVzc0dDBGa1p5Z1ZQSGdwbktnVWozemFSMHhfRDlZMEEybW16REZCYkpHNmpDR0k1V3lmSnY2c0gyS2xuTDRvaWhGS1VQVm9HY3VNZWhySkd3TWNUMllhRnM3dDhXaHROTkM4ek9HbkxHODdiQUM4SjUxY0VEdjlGeUYyaUtBc0NLY1JNWFRnd0Q2Y2pRVExacnZ1ZnJQN1FTR3pzYWdRYXlPZEVNZkl3N2UydXZoSS1iRWtMdFdxb1BKcmZFV2Y4YU1CY2RLVEhXQXc4NGhDOXR0am52S0xGbFVvNjlHYWhmZm5KV2ZEN1RucyJ9\",\"signature\":\"A14QNG3HbUD341rmJ7ibxiIMlcCuDIUrLWtvcmnH-byrBetX5J5VrXaiHOOPYKK2YCjDJEr2f29Cq3i6Q0IlC2UGAPGOEETYKNDBv3zHrtNe7I0VMXMqfB8ClNydSoNdAL9OB1m9syZT7ijZxq_RldTWLsCIDDdWom1xEgb3RUCpTTMUMhsTQZdf5t3y0CNa5p7wfCT8ejLcQ3aYMUm-chDjn4nC8YBdGVSlpacLdafrsDeoTFSF8yhCL9pBk_hz8FMXFKS3ctCBGVJTIHeWPWvnYJn4owEAjbmVqC_khACM-Zo7N-Gx7--47_qyG4dW2IvYansrMrlIwLlwDtmPig\"}";
        final String REVOKE_CERT_REPLAY_NONCE = "taroo4s_zllDSu1x5i0l1x595sQJalOjAPXRnz6oc7vMiHc";

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
                "  \"oBarDLD1zzc\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "zincwFvSlMEm-Dg4LsDtx1JBKaWnu2qiBYBUG6jSZLiexMY";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoOE9lZTViZURSZ3hOUGVfZU1FOUg2Vm83NEZ1ZzhIZ3Jpa2ZiZkNhVTNsS0Y2NDhRRzFYMWtHRFpUaEF5OGRhcUo4YnY2YzNQSmRueDJIcjhqT3psNTA5Ym5NNmNDV2Z5d1RwY0lab1V6UVFaTFlfSzhHTURBeWdsc1FySXRnQ2lRYWxJcWJ1SkVrb2MzV1FBSXhKMjN4djliSzV4blZRa1RXNHJWQkFjWU5Rd29CakdZT1dTaXpUR2ZqZ21RcVRYbG9hYW1GWkpuOTdIbmIxcWp5NVZZbTA2YnV5cXdBYUdIczFDTHUzY0xaZ1FwVkhRNGtGc3prOFlPNVVBRWppb2R1Z1dwWlVSdTlUdFJLek4wYmtFZGVRUFlWcGF1cFVxMWNxNDdScDJqcVZVWGRpUUxla3l4clFidDhBMnVHNEx6RFF1LWI0Y1pwcG16YzNobGhTR3cifSwibm9uY2UiOiJ6aW5jd0Z2U2xNRW0tRGc0THNEdHgxSkJLYVdudTJxaUJZQlVHNmpTWkxpZXhNWSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"bFUmpw50SUd29FFzPgpZw-lkSzl8tlO4ZEUkxRNyL3NSfBZvKDUpPA9P4gg7NkKq-kLl09O2_w_9zoDu_AxIpfylnBuQmK3PGA_f61tQHjWG41hX7NaPUqPieFiEMD4EC7-z4oEN2O79hdCLzhtujwkX8kUav7q60VoUDdmLongJkoQJYHqYJisYmmvGBf28qe3jq9KmgeLav33z8xdsg3i-Cc7jZDWdRMtY72PqEMT53WhYBof15HXrrSZf5b6AAEOX8xMfPkMvx0p_TG2RCEiYY-L7yxgE634_-ye146uUL47X7h5ajmuqu3EsOL4456cjpcKGyhpU9aAhCDKHNQ\"}";

        final String QUERY_ACCT_RESPONSE_BODY= "";

        final String QUERY_ACCT_REPLAY_NONCE = "taroaIprXC7Gi1SYzYi8ETK0IooQwJyv-Qsv4ALL-xw8uu0";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/5";

        final String REVOKE_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNSIsIm5vbmNlIjoidGFyb2FJcHJYQzdHaTFTWXpZaThFVEswSW9vUXdKeXYtUXN2NEFMTC14dzh1dTAiLCJ1cmwiOiJodHRwOi8vbG9jYWxob3N0OjQwMDEvYWNtZS9yZXZva2UtY2VydCJ9\",\"payload\":\"eyJjZXJ0aWZpY2F0ZSI6Ik1JSUZSVENDQkMyZ0F3SUJBZ0lUQVA4S2RpM2JyejdmaTlHYkpDM2pQRGxUT2pBTkJna3Foa2lHOXcwQkFRc0ZBREFmTVIwd0d3WURWUVFEREJSb01uQndlU0JvTW1OclpYSWdabUZyWlNCRFFUQWVGdzB4T1RBM01UWXhOekV5TURkYUZ3MHhPVEV3TVRReE56RXlNRGRhTUI0eEhEQWFCZ05WQkFNVEUyMXVaR1ZzYTJSdVltTnBiRzlvWnk1amIyMHdnZ0VpTUEwR0NTcUdTSWIzRFFFQkFRVUFBNElCRHdBd2dnRUtBb0lCQVFDSXhLNzVXS0dCSzJ4Y0F1QWVfTmJPRnJvcUJaU3hHUFZFOWd0Y0lMRF9HU0hSYzFWbHNmc2x1UXpsdThiSDNnaW91OFdEUU85NDNYaUxWdldJSU1oSGQ4MzJZM0xRdXMwQnlUeEtlUnVubXdhVjdHWkZoQTNrTEFJclpzUUNRNGxMUUtLTHB4N09PcVREZUhSY1Q4UHV1NDNqQXh6NTItZ0ZFc1MzOFM2WS14aGxmdTZSN1ZvMUJnenlNNFV2MkVLQVNwbDh0N2twOFdiRzUwejhSYzd4SjR1VnZEa3dSQmhJWUtPTmttaHFPVkJvNGlIT0ZoU3JFRGhzRXJERHZjdi00dzdLZU43ZDhURmtucjR1R1U2emtrWDFZYi1GX2ZSSEpzMFhXYTFJSTlGcDFmTXNnQ3lPb0R4MERKSFBIVDE0WS10TVc1bHlRTS1MT2ZYMnJ5RzdBZ01CQUFHamdnSjVNSUlDZFRBT0JnTlZIUThCQWY4RUJBTUNCYUF3SFFZRFZSMGxCQll3RkFZSUt3WUJCUVVIQXdFR0NDc0dBUVVGQndNQ01Bd0dBMVVkRXdFQl93UUNNQUF3SFFZRFZSME9CQllFRkREdmMwX1JzRG1pTjhrUlFBbmxicWNfUUJ5Mk1COEdBMVVkSXdRWU1CYUFGUHQ0VHhMNVlCV0RMSjhYZnpRWnN5NDI2a0dKTUdRR0NDc0dBUVVGQndFQkJGZ3dWakFpQmdnckJnRUZCUWN3QVlZV2FIUjBjRG92THpFeU55NHdMakF1TVRvME1EQXlMekF3QmdnckJnRUZCUWN3QW9Za2FIUjBjRG92TDJKdmRXeGtaWEk2TkRRek1DOWhZMjFsTDJsemMzVmxjaTFqWlhKME1CNEdBMVVkRVFRWE1CV0NFMjF1WkdWc2EyUnVZbU5wYkc5b1p5NWpiMjB3SndZRFZSMGZCQ0F3SGpBY29CcWdHSVlXYUhSMGNEb3ZMMlY0WVcxd2JHVXVZMjl0TDJOeWJEQkFCZ05WSFNBRU9UQTNNQWdHQm1lQkRBRUNBVEFyQmdNcUF3UXdKREFpQmdnckJnRUZCUWNDQVJZV2FIUjBjRG92TDJWNFlXMXdiR1V1WTI5dEwyTndjekNDQVFNR0Npc0dBUVFCMW5rQ0JBSUVnZlFFZ2ZFQTd3QjJBQmJvYWNIUmxlclh3X2lYR3VQd2RnSDNqT0cyblRHb1VoaTJnMzh4cUJVSUFBQUJhX3Y4QzRFQUFBUURBRWN3UlFJaEFQbC1wWUdvcGZGb0xwSS1VT2pQN2J3YjQ2UmhlYXl6a1VUZDFxV3U4TUhiQWlBa0xFaG5fNllXTm1iWEtxNmtiNk9aQzFSNy1ud1NKNk41X1BQNm9KQlhvZ0IxQU4yWk5QeWw1eVNBeVZab2ZZRTBtUWhKc2tuM3RXbll4N3lyUDF6QjgyNWtBQUFCYV92OERYWUFBQVFEQUVZd1JBSWdXZkRRU2Rfamo1SzV5ZHh6MFU2Z0xpVV9LcGtZek44bG9DTHZNUXVPSDlFQ0lHT0d5eWJNcXA2ZVRtUFZxeXdDcEVEa0xRS0J2NE1DNV9McmtqN3JaMmhxTUEwR0NTcUdTSWIzRFFFQkN3VUFBNElCQVFBUjZTLWl6NlUteU41YTNVSmJlVVR1TU9XZUNkZWFPdVBMYy1GUnpSUExxamlvb240U2VOSnp4QXNBVnNrQmtjN3ZfRGpNWFVTR3BYSm1IUVkwM1pJYUNoSWRuVXRyUTdLX0FhaXFLck1IeTV5U29JSUFENzFKU09kT1RRdTBvRDBVZVZBdUVJMlExcVBZcnNoYmZ6UGhraWxBV3BwaGhTc1l1U0g0aFlHQVRyck4zaEE5ckF3UkN4eVFmTUpUQXBiWnc5T0dZOVp5MW12NWU3cDh6WXZsanlRWEFmUmpTaEhjQ1R2UVhXUFNIb2VGVTRMa1ViMHdlXy1uRzNtdDdfTGhYckpNUFZ2T1VNQlB6R2RQSDE3dDFYeWYzVEdaV2x0Wkdjc0MzcmRjdWkxaGJkcHpxNU5zcHV3Qlg4b0F4d0Rnck9CajF4VldfRkFMSXd3NTBCMHkiLCJyZWFzb24iOjF9\",\"signature\":\"bmgU30KFfJ5QLUBFF6b2e1mBV0W3YgKJHHS3goSyxzaANUocAEBYaEAId4EglE8op1HqvVBul5o7hCA6UfkNRE_hv0Y6c5xS_OQPRt0sRk_KRe6rVeVZd2ov5IqXmjdGq7xOnyRFXq1ErPfb3KSoz1IUOagemSZzUgbPNwIIJMSnQuRXW8ScOECssoDTy_R4OL6drkyxN8qXP7dJUQ4T4rTRBXnSEv1fUHFBZLRvVb2jqMc-Iiwp6hjdahBlWqPudiMyD8pinghyns0m5btw_OmOWERMEI4lIsOJjVg2Tu7HALDiLGSk6dyUV1HXyAeWBVr1QJBFeq2Gw3rD-26d1w\"}";
        final String REVOKE_CERT_REPLAY_NONCE = "zinci0BXolnLRwsa-i7xBiVz4Zy0LDbw7hjIv9UvBDP10CQ";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY, QUERY_ACCT_RESPONSE_BODY, QUERY_ACCT_REPLAY_NONCE, ACCT_LOCATION, 200)
                .addRevokeCertificateRequestAndResponse(REVOKE_CERT_REQUEST_BODY, REVOKE_CERT_REPLAY_NONCE, 200)
                .build();
    }
}