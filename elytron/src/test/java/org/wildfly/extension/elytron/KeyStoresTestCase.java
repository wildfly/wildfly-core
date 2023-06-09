/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
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
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.wildfly.extension.elytron.common.CredentialStoreUtility;
import org.wildfly.extension.elytron.common.ElytronCommonCapabilities;
import org.wildfly.extension.elytron.common.ElytronCommonConstants;
import org.wildfly.extension.elytron.common.ElytronCommonKeyStoresTestCase;
import org.wildfly.extension.elytron.common.JdkUtils;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.cert.KeyUsage;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class KeyStoresTestCase extends ElytronCommonKeyStoresTestCase {


    private static final String KEYSTORE_NAME = "ModifiedKeystore";

    private static final String CERTIFICATE_AUTHORITY_ACCOUNT_NAME = "CertAuthorityAccount";
    private static final String CERTIFICATE_AUTHORITY_NAME = "CertAuthority";
    private static final String ACCOUNTS_KEYSTORE = "account.keystore";
    private static final String ACCOUNTS_KEYSTORE_NAME = "AccountsKeyStore";
    private static final String ACCOUNTS_KEYSTORE_PASSWORD = "elytron";
    private static final String SIMULATED_LETS_ENCRYPT_ENDPOINT = "http://localhost:4001/directory"; // simulated Let's Encrypt server instance
    private static final String WORKING_DIRECTORY_LOCATION = "./target/test-classes/org/wildfly/extension/elytron";

    public KeyStoresTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension(), WORKING_DIRECTORY_LOCATION);
    }

    private KernelServices services = null;

    private static void createServerEnvironment() {
        File home = new File("target/wildfly");
        home.mkdir();
        File challengeDir = new File(home, ".well-known/acme-challenge");
        challengeDir.mkdirs();
        setOldHomeDir(System.setProperty("jboss.home.dir", home.getAbsolutePath()));
    }

    @Override
    public KernelServices getServices() {
        return services;
    }

    private static void setUpFiles() throws Exception {
        File workingDir = new File(WORKING_DIRECTORY_LOCATION);
        if (workingDir.exists() == false) {
            workingDir.mkdirs();
        }
        setWorkingDirectoryLocation(WORKING_DIRECTORY_LOCATION);

        SelfSignedX509CertificateAndSigningKey rootSelfSignedX509CertificateAndSigningKey = createCertRoot(ROOT_DN);
        SelfSignedX509CertificateAndSigningKey anotherRootSelfSignedX509CertificateAndSigningKey = createCertRoot(ANOTHER_ROOT_DN);
        SelfSignedX509CertificateAndSigningKey sallySelfSignedX509CertificateAndSigningKey = createSallyCertificate();

        PublicKey publicKey = sallySelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate().getPublicKey();

        // Key Stores
        KeyStore fireflyKeyStore = createFireflyKeyStore();
        KeyStore testKeyStore = createTestKeyStore(rootSelfSignedX509CertificateAndSigningKey, sallySelfSignedX509CertificateAndSigningKey);

        createTemporaryKeyStoreFile(fireflyKeyStore, FIREFLY_FILE.get());
        createTemporaryKeyStoreFile(testKeyStore, TEST_FILE.get());

        // Cert Files
        X509Certificate sSmithCertificate = createSSmithCertificate(rootSelfSignedX509CertificateAndSigningKey, publicKey);

        X509Certificate testTrustedCert = createTestTrustedCertificate(rootSelfSignedX509CertificateAndSigningKey);
        X509Certificate testUntrustedCert = anotherRootSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        X509Certificate testUntrustedCertChainReplyCert = createTestUntrustedCertChainReplyCertificate(anotherRootSelfSignedX509CertificateAndSigningKey, publicKey);

        createTemporaryCertFile(sSmithCertificate, TEST_SINGLE_CERT_REPLY_FILE.get());

        createTemporaryCertFile(rootSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate(), TEST_CERT_CHAIN_REPLY_FILE.get());
        createTemporaryCertFile(sSmithCertificate, TEST_CERT_CHAIN_REPLY_FILE.get());

        createTemporaryCertFile(sallySelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate(), TEST_EXPORTED_FILE.get());
        createTemporaryCertFile(testTrustedCert, TEST_TRUSTED_FILE.get());
        createTemporaryCertFile(testUntrustedCert, TEST_UNTRUSTED_FILE.get());

        createTemporaryCertFile(testUntrustedCertChainReplyCert, TEST_UNTRUSTED_CERT_CHAIN_REPLY_FILE.get());
        createTemporaryCertFile(anotherRootSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate(), TEST_UNTRUSTED_CERT_CHAIN_REPLY_FILE.get());
    }

    @BeforeClass
    public static void initTests() throws Exception {
        setServer(new ClientAndServer(4001));
        setUpFiles();
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(wildFlyElytronProvider, 1);
            }
        });
        csUtil = new CredentialStoreUtility("target/tlstest.keystore", CS_PASSWORD);
        csUtil.addEntry("the-key-alias", KEYSTORE_PASSWORD);
        csUtil.addEntry("primary-password-alias", KEYSTORE_PASSWORD);
        createServerEnvironment();
    }

    @AfterClass
    public static void cleanUpTests() {
        if (getServer() != null) {
            getServer().stop();
        }
        removeTestFiles();
        csUtil.cleanUp();
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Security.removeProvider(wildFlyElytronProvider.getName());

                return null;
            }
        });
        if (getOldHomeDir() == null) {
            System.clearProperty("jboss.home.dir");
        } else {
            System.setProperty("jboss.home.dir", getOldHomeDir());
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
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(subsystemXml).build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    @Test
    public void testKeystoreCli() throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("firefly.keystore"), resources.resolve("firefly-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ModelNode operation = new ModelNode(); // add keystore
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", "ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/firefly-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
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
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("firefly.keystore"), resources.resolve("firefly-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ModelNode operation = new ModelNode(); // add keystore
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", "ModifiedKeyStore");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/firefly-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
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
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
            Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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
        setServer(setupTestCreateAccountWithoutAgreeingToTermsOfService());
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
        setServer(setupTestObtainCertificateWithKeySize());
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
        setServer(setupTestObtainCertificateWithECPublicKey());
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
        setServer(setupTestObtainCertificateWithUnsupportedPublicKey());
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
    public void testShouldRenewCertificateExpiresWithinGivenDays() throws Exception {
        final ZonedDateTime notValidBeforeDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        final ZonedDateTime notValidAfterDate = notValidBeforeDate.plusDays(60).plusMinutes(1);
        ModelNode result = shouldRenewCertificate(notValidBeforeDate, notValidAfterDate, 90);
        assertTrue(result.get(ElytronCommonConstants.SHOULD_RENEW_CERTIFICATE).asBoolean());
        assertEquals(60, result.get(ElytronCommonConstants.DAYS_TO_EXPIRY).asLong());
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
            expiryKeyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
        addKeyStore(expiryKeyStoreFileName, KEYSTORE_NAME, KEYSTORE_PASSWORD);

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
            setServer(setupTestRevokeCertificateWithReason());
        } else {
            setServer(setupTestRevokeCertificateWithoutReason());
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

    private void addKeyStore() throws Exception {
        addKeyStore("test.keystore", KEYSTORE_NAME, KEYSTORE_PASSWORD);
    }

    private void addKeyStore(String keyStoreFile, String keyStoreName, String keyStorePassword) throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
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

    private void addOriginalKeyStore() throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("test-original.keystore"), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/test-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
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
        operation.get(ElytronCommonConstants.PATH).set(resources + nonExistentFileName);
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
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
}
