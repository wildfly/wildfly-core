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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAKey;
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
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.cert.KeyUsage;


/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class KeyStoresTestCase extends AbstractSubsystemTest {

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility csUtil = null;
    private static final String CS_PASSWORD = "super_secret";
    private static final String KEYSTORE_NAME = "ModifiedKeystore";
    private static final String KEY_PASSWORD = "secret";


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

    @BeforeClass
    public static void initTests() {
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(wildFlyElytronProvider, 1);
            }
        });
        csUtil = new CredentialStoreUtility("target/tlstest.keystore", CS_PASSWORD);
        csUtil.addEntry("the-key-alias", "Elytron");
        csUtil.addEntry("master-password-alias", "Elytron");
    }

    @AfterClass
    public static void cleanUpTests() {
        csUtil.cleanUp();
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Security.removeProvider(wildFlyElytronProvider.getName());

                return null;
            }
        });
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

        Assert.assertFalse(keyStore.containsAlias("ca"));
        Assert.assertFalse(keyStore.isCertificateEntry("ca"));
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
        addKeyStore();

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
        addKeyStore();

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

    private void addKeyStore() throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("test.keystore"), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", KEYSTORE_NAME);
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
}