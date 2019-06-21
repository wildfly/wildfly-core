/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.integration.ClientAndServer;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.x500.cert.acme.AcmeAccount;

import mockit.Mock;
import mockit.MockUp;
import mockit.integration.junit4.JMockit;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;


/**
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
@RunWith(JMockit.class)
public class CertificateAuthoritiesTestCase extends AbstractSubsystemTest {

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility csUtil = null;
    private static final String CS_PASSWORD = "super_secret";
    private static final String CERTIFICATE_AUTHORITY_ACCOUNT_NAME = "CertAuthorityAccount";
    private static final String CERTIFICATE_AUTHORITY_NAME = "CertAuthority";
    private static final String SIMULATED_LETS_ENCRYPT_ENDPOINT = "http://localhost:4001/directory"; // simulated Let's Encrypt server instance
    private static final String CERTIFICATE_AUTHORITY_TEST_URL = "http://www.test.com";
    private static final String ACCOUNTS_KEYSTORE_NAME = "AccountsKeyStore";
    private static final String KEYSTORE_PASSWORD = "elytron";
    private static ClientAndServer server; // used to simulate a Let's Encrypt server instance


    public CertificateAuthoritiesTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private KernelServices services = null;

    private static void mockCertificateAuthorityUrl() {
        Class<?> classToMock;
        try {
            classToMock = Class.forName("org.wildfly.security.x500.cert.acme.AcmeAccount", true, CertificateAuthorityAccountDefinition.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(e.getMessage());
        }
        new MockUp<Object>(classToMock) {
            @Mock
            public String getServerUrl(){
                return SIMULATED_LETS_ENCRYPT_ENDPOINT; // use a simulated Let's Encrypt server instance for these tests
            }
        };
    }

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
        server = new ClientAndServer(4001);
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(wildFlyElytronProvider, 1);
            }
        });
        csUtil = new CredentialStoreUtility("target/tlstest.keystore", CS_PASSWORD);
        csUtil.addEntry("the-key-alias", "Elytron");
        csUtil.addEntry("master-password-alias", "Elytron");
        mockCertificateAuthorityUrl();
    }

    @AfterClass
    public static void cleanUpTests() {
        if (server != null) {
            server.stop();
        }
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
        String subsystemXml;
        if (JdkUtils.isIbmJdk()) {
            subsystemXml = "tls-ibm.xml";
        } else {
            subsystemXml = JdkUtils.getJavaSpecVersion() <= 12 ? "tls-sun.xml" : "tls-oracle13plus.xml";
        }
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(subsystemXml).build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }
    }

    @Test
    public void testCreateAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("account1");
        server = setupTestCreateAccount();
        AcmeAccount acmeAccount = getAcmeAccount();
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            assertSuccess(services.executeOperation(operation));
            assertEquals(NEW_ACCT_LOCATION, acmeAccount.getAccountUrl());
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testCreateAccountWithCustomCA() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1");
        server = setupTestCreateAccount();
        AcmeAccount acmeAccount = getAcmeAccount();
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            assertSuccess(services.executeOperation(operation));
            assertEquals(NEW_ACCT_LOCATION, acmeAccount.getAccountUrl());
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testCreateAccountWithCustomCAWithoutStaging() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1");
        server = setupTestCreateAccount();
        AcmeAccount acmeAccount = getAcmeAccount();
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            assertSuccess(services.executeOperation(operation));
            assertEquals(NEW_ACCT_LOCATION, acmeAccount.getAccountUrl());
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testCreateAccountWithCustomCAWithStaging() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1");
        server = setupTestCreateAccount();
        AcmeAccount acmeAccount = getAcmeAccount();
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(ElytronDescriptionConstants.STAGING).set(true);
            assertSuccess(services.executeOperation(operation));
            assertEquals(NEW_ACCT_LOCATION, acmeAccount.getAccountUrl());
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testCreateAccountWithEmptyStagingUrlAndStagingValueTrue() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1");
        server = setupTestCreateAccount();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(ElytronDescriptionConstants.STAGING).set(true);
            assertFailed(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testCreateAccountNonExistingAlias() throws Exception {
        String alias = "nonExisting";
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        KeyStore accountsKeyStore = getKeyStore(ACCOUNTS_KEYSTORE_NAME);
        assertFalse(accountsKeyStore.containsAlias(alias));
        addCertificateAuthorityAccount(alias);
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/399";
        server = setupTestCreateAccountNonExistingAlias();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            assertSuccess(services.executeOperation(operation));
            assertEquals(NEW_ACCT_LOCATION, acmeAccount.getAccountUrl());
            assertNotNull(accountsKeyStore.containsAlias(alias));
            assertTrue(accountsKeyStore.getEntry(alias, new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray())) instanceof KeyStore.PrivateKeyEntry);
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testCreateAccountWithoutAgreeingToTermsOfService() throws Exception {
        String alias = "invalid";
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount(alias);
        server = setupTestCreateAccountWithoutAgreeingToTermsOfService();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(false);
            assertFailed(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testUpdateAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("account1", new String[] { "mailto:certificates@example.com", "mailto:admin@example.com" });
        server = setupTestUpdateAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.UPDATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(false);
            assertSuccess(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testChangeAccountKey() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("account6");
        server = setupTestChangeAccountKey();
        // old account
        AcmeAccount acmeAccount = getAcmeAccount();
        X509Certificate oldCertificate = acmeAccount.getCertificate();
        X500Principal oldDn = acmeAccount.getDn();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CHANGE_ACCOUNT_KEY);
            assertSuccess(services.executeOperation(operation));
            assertTrue(! oldCertificate.equals(acmeAccount.getCertificate()));
            assertEquals(oldDn, acmeAccount.getDn());
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testDeactivateAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("account10");
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/6";
        server = setupTestDeactivateAccount();
        AcmeAccount acmeAccount = getAcmeAccount();
        acmeAccount.setAccountUrl(ACCT_LOCATION);
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.DEACTIVATE_ACCOUNT);
            assertSuccess(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testGetMetadataAllValuesSet() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("account5");
        server = setupTestGetMetadataAllValuesSet();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GET_METADATA);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals("https://boulder:4431/terms/v7", result.get(ElytronDescriptionConstants.TERMS_OF_SERVICE).asString());
            assertEquals("https://github.com/letsencrypt/boulder", result.get(ElytronDescriptionConstants.WEBSITE).asString());
            List<ModelNode> caaIdentities = result.get(ElytronDescriptionConstants.CAA_IDENTITIES).asList();
            assertEquals("happy-hacker-ca.invalid", caaIdentities.get(0).asString());
            assertEquals("happy-hacker2-ca.invalid", caaIdentities.get(1).asString());
            assertTrue(result.get(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_REQUIRED).asBoolean());
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testGetMetadataSomeValuesSet() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("account5");
        server = setupTestGetMetadataSomeValuesSet();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GET_METADATA);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals("https://boulder:4431/terms/v7", result.get(ElytronDescriptionConstants.TERMS_OF_SERVICE).asString());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronDescriptionConstants.WEBSITE).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronDescriptionConstants.CAA_IDENTITIES).getType());
            assertFalse(result.get(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_REQUIRED).asBoolean());
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testGetMetadataNoValuesSet() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("account5");
        server = setupTestGetMetadataNoValuesSet();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GET_METADATA);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals(ModelType.UNDEFINED, result.get(ElytronDescriptionConstants.TERMS_OF_SERVICE).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronDescriptionConstants.WEBSITE).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronDescriptionConstants.CAA_IDENTITIES).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_REQUIRED).getType());
        } finally {
            removeCertificateAuthorityAccount();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testAddCertificateAuthorityWithBothUrlsEmpty() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testAddCertificateAuthorityWithLetsEncryptName() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CertificateAuthority.LETS_ENCRYPT.getName());
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.URL).set(CERTIFICATE_AUTHORITY_TEST_URL);
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testAddCertificateAuthorityWithStagingUrl() {
        addCertificateAuthorityWithStagingUrl();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
            operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals(CERTIFICATE_AUTHORITY_TEST_URL, result.get(ElytronDescriptionConstants.URL).asString());
            assertEquals(SIMULATED_LETS_ENCRYPT_ENDPOINT, result.get(ElytronDescriptionConstants.STAGING_URL).asString());
        } finally {
            removeCertificateAuthority();
        }
    }

    @Test
    public void testAddCertificateAuthorityWithEmptyStagingUrl() {
        addCertificateAuthorityWithoutStagingUrl();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
            operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals(CERTIFICATE_AUTHORITY_TEST_URL, result.get(ElytronDescriptionConstants.URL).asString());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronDescriptionConstants.STAGING_URL).getType());
        } finally {
            removeCertificateAuthority();
        }
    }

    @Test
    public void testRemoveCertificateAuthority() {
        addCertificateAuthorityWithStagingUrl();
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testRemoveCertificateAuthorityUsedByCertificateAuthorityAccount() throws Exception {
        addCertificateAuthorityWithoutStagingUrl();
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccountWithCustomCA("account");
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
            operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
            assertFailed(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testChangeCertificateAuthorityInCertificateAuthorityAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("alias");
        checkCertificateAuthorityIs(CertificateAuthority.LETS_ENCRYPT.getName());
        addCertificateAuthorityWithoutStagingUrl();
        try {
            changeCertificateAuthorityInCertificateAuthorityAccount();
            checkCertificateAuthorityIs(CERTIFICATE_AUTHORITY_NAME);
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
        }
    }

    private void changeCertificateAuthorityInCertificateAuthorityAccount() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY);
        operation.get(ClientConstants.VALUE).set(CERTIFICATE_AUTHORITY_NAME);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityWithoutStagingUrl() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.URL).set(CERTIFICATE_AUTHORITY_TEST_URL);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityWithStagingUrl() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.URL).set(CERTIFICATE_AUTHORITY_TEST_URL);
        operation.get(ElytronDescriptionConstants.STAGING_URL).set(SIMULATED_LETS_ENCRYPT_ENDPOINT);
        assertSuccess(services.executeOperation(operation));
    }

    private void removeCertificateAuthority() {
        ModelNode operation;
        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    private void checkCertificateAuthorityIs(String certificateAuthorityName) {
        ModelNode operation;
        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals(certificateAuthorityName, result.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).asString());
    }

    private AcmeAccount getAcmeAccount() {
        ServiceName serviceName = Capabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY.getCapabilityServiceName(CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        return (AcmeAccount) services.getContainer().getService(serviceName).getValue();
    }

    private KeyStore getKeyStore(String keyStoreName) {
        ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(keyStoreName);
        return (KeyStore) services.getContainer().getService(serviceName).getValue();
    }

    private void addCertificateAuthorityAccount(String alias) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).add("mailto:admin@example.com");
        operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).set(CertificateAuthority.LETS_ENCRYPT.getName());
        operation.get(ElytronDescriptionConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityAccountWithCustomCA(String alias) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).add("mailto:admin@example.com");
        operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).set(CERTIFICATE_AUTHORITY_NAME);
        operation.get(ElytronDescriptionConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityAccount(String alias, String[] contactUrlsList) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        ModelNode contactUrls = new ModelNode();
        for (String contactUrl : contactUrlsList) {
            contactUrls = contactUrls.add(contactUrl);
        }
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).set(contactUrls);
        operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).set(CertificateAuthority.LETS_ENCRYPT.getName());
        operation.get(ElytronDescriptionConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(services.executeOperation(operation));
    }

    private void removeCertificateAuthorityAccount() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    private void addKeyStore(String keyStoreName) throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("account.keystore"), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set(resources + "/test-copy.keystore");
        operation.get(ElytronDescriptionConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(services.executeOperation(operation));
    }

    private void removeKeyStore(String keyStoreName) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    /* -- Helper methods used to set up the messages that should be sent from the mock Let's Encrypt server to our ACME client. -- */

    private ClientAndServer setupTestCreateAccount() {

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

        final String NEW_NONCE_RESPONSE = "8-k95dsqpJLtOQapuL-0XGrBH0UM6lcfdop9OUp05_I";

        final String NEW_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJwdUwtV2NNWVVKMkFqZHkxVXNVZ056am42ZWNEeGlXZDdOR1VHcTI2N1NPTHdoS2pTV1dNd2tvcGZjZzVWTWpQSldFRTM4SUlYeWpXNW5GS0NxRkFJZjNabGloXzFTTGNqZ1ZGYmlibi1vTUdGTFpzOWdncjJialJHSnNic0pRSU9LbWdWczJ5M2w1UmNJeUYyTS1VT3g0R3RBVVFKc1lpdHRjaEJMeHFqczBTQmpXZHRwV3phWDRmd1RDeng0OFJYdVpoa3lfbUtBeUtiaEFZbklHZERoY1ZJWnNmZjZ6ekVNMWJwSkVENk9CWmg2cHlQLU4wa094Y0dtUFBDSE1mME16d2puSzhWckZQRWFJSWZRQWJVQzFyVGF1aXFaWDdnbEVuTjJrWXFPd2w4ZzNuZjVmYlg2c1V1RFUxNWZWMGNtZFV0aHk4X0dIeUUycWR6alBSTHcifSwibm9uY2UiOiI4LWs5NWRzcXBKTHRPUWFwdUwtMFhHckJIMFVNNmxjZmRvcDlPVXAwNV9JIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"Js_fvpkTcDkWhJqwYNMdDqQKz6pWTxT0I5XzT0PrF0hTupSMc0uvUBc19xD64_x4fFsEZMlv1l_d2jm1pt-7nySWcYQFbkYh-tdRuxygzCCXdFhsXsw3MGh13KghkgiawjW37TFw8DrIWSwlsuGEIjofF2TqExecX0mkyF-vl6VA7Gm9oiqxfRiKx-X4YaO7-ijUnG7EMyesSKfu3PmBcaPsO9gIjRQ4FHrOb1RTSmTupskb4pZ0D2tkwKZcWWmXwO2XnLPjF5ZZi6c0p7GA_f578r665toyqP9n7PV6Vlf8w8XrM_EsF201r4oCFyVTEuAYx9fozKYIEhZe-PDWdw\"}";

        final String NEW_ACCT_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"id\": 384," + System.lineSeparator()  +
                "  \"key\": {" + System.lineSeparator()  +
                "    \"kty\": \"RSA\"," + System.lineSeparator()  +
                "    \"n\": \"puL-WcMYUJ2Ajdy1UsUgNzjn6ecDxiWd7NGUGq267SOLwhKjSWWMwkopfcg5VMjPJWEE38IIXyjW5nFKCqFAIf3Zlih_1SLcjgVFbibn-oMGFLZs9ggr2bjRGJsbsJQIOKmgVs2y3l5RcIyF2M-UOx4GtAUQJsYittchBLxqjs0SBjWdtpWzaX4fwTCzx48RXuZhky_mKAyKbhAYnIGdDhcVIZsff6zzEM1bpJED6OBZh6pyP-N0kOxcGmPPCHMf0MzwjnK8VrFPEaIIfQAbUC1rTauiqZX7glEnN2kYqOwl8g3nf5fbX6sUuDU15fV0cmdUthy8_GHyE2qdzjPRLw\"," + System.lineSeparator()  +
                "    \"e\": \"AQAB\"" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"contact\": [" + System.lineSeparator()  +
                "    \"mailto:admin@example.com\"" + System.lineSeparator()  +
                "  ]," + System.lineSeparator()  +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator()  +
                "  \"createdAt\": \"2018-04-23T11:10:28.490176768-04:00\"," + System.lineSeparator()  +
                "  \"status\": \"valid\"" + System.lineSeparator()  +
                "}";

        final String NEW_ACCT_REPLAY_NONCE = "20y41JJoZ3Rn0VCEKDRa5AzT0kjcz6b6FushFyVS4zY";
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(NEW_ACCT_REQUEST_BODY, NEW_ACCT_RESPONSE_BODY, NEW_ACCT_REPLAY_NONCE, NEW_ACCT_LOCATION, 201)
                .build();
    }

    private ClientAndServer setupTestCreateAccountNonExistingAlias() {

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

        final String NEW_NONCE_RESPONSE = "8-k95dsqpJLtOQapuL-0XGrBH0UM6lcfdop9OUp05_I";

        final String NEW_ACCT_REQUEST_BODY = "";

        final String NEW_ACCT_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"id\": 384," + System.lineSeparator()  +
                "  \"key\": {" + System.lineSeparator()  +
                "    \"kty\": \"RSA\"," + System.lineSeparator()  +
                "    \"n\": \"puL-WcMYUJ2Ajdy1UsUgNzjn6ecDxiWd7NGUGq267SOLwhKjSWWMwkopfcg5VMjPJWEE38IIXyjW5nFKCqFAIf3Zlih_1SLcjgVFbibn-oMGFLZs9ggr2bjRGJsbsJQIOKmgVs2y3l5RcIyF2M-UOx4GtAUQJsYittchBLxqjs0SBjWdtpWzaX4fwTCzx48RXuZhky_mKAyKbhAYnIGdDhcVIZsff6zzEM1bpJED6OBZh6pyP-N0kOxcGmPPCHMf0MzwjnK8VrFPEaIIfQAbUC1rTauiqZX7glEnN2kYqOwl8g3nf5fbX6sUuDU15fV0cmdUthy8_GHyE2qdzjPRLw\"," + System.lineSeparator()  +
                "    \"e\": \"AQAB\"" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"contact\": [" + System.lineSeparator()  +
                "    \"mailto:admin@example.com\"" + System.lineSeparator()  +
                "  ]," + System.lineSeparator()  +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator()  +
                "  \"createdAt\": \"2018-04-23T11:10:28.490176768-04:00\"," + System.lineSeparator()  +
                "  \"status\": \"valid\"" + System.lineSeparator()  +
                "}";

        final String NEW_ACCT_REPLAY_NONCE = "20y41JJoZ3Rn0VCEKDRa5AzT0kjcz6b6FushFyVS4zY";
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/399";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(NEW_ACCT_REQUEST_BODY, NEW_ACCT_RESPONSE_BODY, NEW_ACCT_REPLAY_NONCE, NEW_ACCT_LOCATION, 201)
                .build();
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

    private ClientAndServer setupTestUpdateAccount() {

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"UlOnbFfGuy0\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
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
                "}" + System.lineSeparator() ;

        final String NEW_NONCE_RESPONSE = "OCmFPbtyQAV0FiQfH6AeOFlvmYonKMFfOQx5m6WSRTw";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJwdUwtV2NNWVVKMkFqZHkxVXNVZ056am42ZWNEeGlXZDdOR1VHcTI2N1NPTHdoS2pTV1dNd2tvcGZjZzVWTWpQSldFRTM4SUlYeWpXNW5GS0NxRkFJZjNabGloXzFTTGNqZ1ZGYmlibi1vTUdGTFpzOWdncjJialJHSnNic0pRSU9LbWdWczJ5M2w1UmNJeUYyTS1VT3g0R3RBVVFKc1lpdHRjaEJMeHFqczBTQmpXZHRwV3phWDRmd1RDeng0OFJYdVpoa3lfbUtBeUtiaEFZbklHZERoY1ZJWnNmZjZ6ekVNMWJwSkVENk9CWmg2cHlQLU4wa094Y0dtUFBDSE1mME16d2puSzhWckZQRWFJSWZRQWJVQzFyVGF1aXFaWDdnbEVuTjJrWXFPd2w4ZzNuZjVmYlg2c1V1RFUxNWZWMGNtZFV0aHk4X0dIeUUycWR6alBSTHcifSwibm9uY2UiOiJPQ21GUGJ0eVFBVjBGaVFmSDZBZU9GbHZtWW9uS01GZk9ReDVtNldTUlR3IiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"ctdxjoRbd7SHD5KbRdKdVB8iQubvsxHbZ-jVTYDV01_GiGBQdEtEKZ6N-0eREn1aFLk-J_Xm2uTQ7pf8foYITiMIAtHHzyqcgS5rKz0dI9aUbWelh4oiDR4sj-TbGU-Cf4_XBsODSToSxf3uu1mPBFYbY68P3dSPWYWItuKyxztgcH1tUjtWYJeN1g9KouTALDUY1yh6FCVGKYTiT98pMPTM1nw8R5ZvqehU3oZZY8--QpWp8rFfCkFDi6eqWhLEXPD8SaarrhXUKQHBS9f-4ZOgHxdwX7hAgZeK_6bM2InhmPijiBwu_QK0OpDSnj1FZHXi5CKax7D8yq1e_71j7w\"}";

        final String QUERY_ACCT_RESPONSE_BODY = "";

        final String QUERY_ACCT_REPLAY_NONCE = "JdKG7fynD7xEnJzPd6pzWi42WvwoIemOSavTZs104CY";

        final String ACCT_PATH = "/acme/acct/384";
        final String ACCT_LOCATION = "http://localhost:4001" + ACCT_PATH;

        final String UPDATE_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzg0Iiwibm9uY2UiOiJKZEtHN2Z5bkQ3eEVuSnpQZDZweldpNDJXdndvSWVtT1NhdlRaczEwNENZIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYWNjdC8zODQifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6ZmFsc2UsImNvbnRhY3QiOlsibWFpbHRvOmNlcnRpZmljYXRlc0BleGFtcGxlLmNvbSIsIm1haWx0bzphZG1pbkBleGFtcGxlLmNvbSJdfQ\",\"signature\":\"ahcbxs7RTwFsMjWmcUgJ-ldYBS3x8QfVa_M7czNloN2WVtHvN7NziSnLKoxvUFNPzfxFl_z1aPkjYl6D9YR8CGTJAQZ-hwjFVyBN69b2u70Wje8H8Ja41TutBG91MGRVc4pzoqrznzb-uYjWAc8fqSBfKfFF6A1KFnyhguUPadL_beTOVpC1cQUJjb7zuAPD45HCSFgK7-IpSHrrtqTzN_7Rs0xnK9xZpqhAx1fuDTa0_UhRw5kQ6UOGddTjWYyb6uRXURXCW1vkFVnHPrMyGRHoUtnilLC-uangjQtZ5fGTUI8L5nc6Ljrpv8E5BxCovmrMcOUtUEgYSc_fmbha8g\"}";

        final String UPDATE_ACCT_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"id\": 384," + System.lineSeparator()  +
                "  \"key\": {" + System.lineSeparator()  +
                "    \"kty\": \"RSA\"," + System.lineSeparator()  +
                "    \"n\": \"puL-WcMYUJ2Ajdy1UsUgNzjn6ecDxiWd7NGUGq267SOLwhKjSWWMwkopfcg5VMjPJWEE38IIXyjW5nFKCqFAIf3Zlih_1SLcjgVFbibn-oMGFLZs9ggr2bjRGJsbsJQIOKmgVs2y3l5RcIyF2M-UOx4GtAUQJsYittchBLxqjs0SBjWdtpWzaX4fwTCzx48RXuZhky_mKAyKbhAYnIGdDhcVIZsff6zzEM1bpJED6OBZh6pyP-N0kOxcGmPPCHMf0MzwjnK8VrFPEaIIfQAbUC1rTauiqZX7glEnN2kYqOwl8g3nf5fbX6sUuDU15fV0cmdUthy8_GHyE2qdzjPRLw\"," + System.lineSeparator()  +
                "    \"e\": \"AQAB\"" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"contact\": [" + System.lineSeparator()  +
                "    \"mailto:admin@example.com\"," + System.lineSeparator()  +
                "    \"mailto:certificates@example.com\"" + System.lineSeparator()  +
                "  ]," + System.lineSeparator()  +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator()  +
                "  \"createdAt\": \"2018-04-23T11:10:28-04:00\"," + System.lineSeparator()  +
                "  \"status\": \"valid\"" + System.lineSeparator()  +
                "}";

        final String UPDATE_ACCT_REPLAY_NONCE = "rXNvQT-1t0WL34fe6LVNHeP0o3LYW020wNy_NJPvi_o";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY, QUERY_ACCT_RESPONSE_BODY, QUERY_ACCT_REPLAY_NONCE, ACCT_LOCATION, 200)
                .updateAccountRequestAndResponse(UPDATE_ACCT_REQUEST_BODY, UPDATE_ACCT_RESPONSE_BODY, UPDATE_ACCT_REPLAY_NONCE, ACCT_PATH, 200)
                .build();
    }

    private ClientAndServer setupTestChangeAccountKey() {

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"DSKtJkFv-s0\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "Q2ZDfeIokvIooH5va3-1GSCFkM5x-hhjqoVoomTfbQA";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoNWlULUY4UzZMczJLZlRMNUZpNV9hRzhpdWNZTl9yajJVXy16ck8yckpxczg2WHVHQnY1SDdMZm9vOWxqM3lsaXlxNVQ2ejdkY3RZOW1rZUZXUEIxaEk0Rjg3em16azFWR05PcnM5TV9KcDlPSVc4QVllNDFsMHBvWVpNQTllQkE0ZnV6YmZDTUdONTdXRjBfMjhRRmJuWTVXblhXR3VPa0N6QS04Uk5IQlRxX3Q1a1BWRV9jNFFVemRJcVoyZG54el9FZ05jdU1hMXVHZEs3YmNybEZIdmNrWjNxMkpsT0NEckxEdEJpYW96ZnlLR0lRUlpheGRYSlE2cl9tZVdHOWhmZUJuMTZKcG5nLTU4TFd6X0VIUVFtLTN1bl85UVl4d2pIY2RDdVBUQ1RXNEFwcFdnZ1FWdE00ZTd6U1ZzMkZYczdpaVZKVzhnMUF1dFFINU53Z1EifSwibm9uY2UiOiJRMlpEZmVJb2t2SW9vSDV2YTMtMUdTQ0ZrTTV4LWhoanFvVm9vbVRmYlFBIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"eQKqqCXeXqLwGvg__P-ESbkZvtSs7d8w-360ymPZrns2OgJtDegbyJsBxPvuTCFP4FV2xiHpx-pOfurzs_9NHjjRZkVY6F8OTEKZbVDn1FSJELcDCPaHQ1s9p4IUBpl3C_HTcJrroi8qBTpImEmYvDPyIPBaopItMFglK_AxyAQpBkxfoT-osrFIi0Zrw0BVUbhhaNQ5xu3z29I4EW2VKPEZFd5komOKqmDDkixDxIyDKhuSGTBQJRFpy0adMSEmzwbqxAn0FkV3iMDonvjzSHjOOf-ly7LN22OnzBsW7JeHGHqbeNARYSYvBRms8kGXm_8nPdtJRhol_BoEJznniQ\"}";

        final String QUERY_ACCT_RESPONSE_BODY_1 = "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "4orQIw6HjOa-8X1HzkpiOdEuIXRmruVcT518zICV3to";

        final String ACCT_PATH = "/acme/acct/398";
        final String ACCT_LOCATION = "http://localhost:4001" + ACCT_PATH;

        final String CHANGE_KEY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"id\": 398," + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"l5i-D4_48ZXGR3MF4VIhiBzSpGctEfCYu3l3cVPo215a3YDxZhLwCwH5x0FYesd4_uhfyJJcojntrigoaphe-Nm2K2SeBOws6c6lAb0zmN8gFPRG2wUYYGOJpSADtSWC6iZQsCronaHnk3pGutWvgumMniT0Rw8dEEVd5k36MfkknqZGT6ewOHxh0mz3kbVZy3wuAtG1sK13tokF4Qa3Qf9BsGkWcJ8ukpQ7YyDSJ_BnxjK8DgPbs48qH4f0QZZxXitavPDGkqUmLbxRAj7UeolevwkUv5nkV6X7tWdm2alZTrRNADR7oe8jmotIeDX1GSgE8T7VJion9sSTJKiyBw\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@example.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"agreement\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator() +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2018-04-27T14:27:35-04:00\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}" + System.lineSeparator();
        final String CHANGE_KEY_REPLAY_NONCE = "0I0Wx7k59VLykloVFtY_QoKXDg8Z2s-v6bWj28RVjaQ";

        final String QUERY_ACCT_RESPONSE_BODY_2 = "{" + System.lineSeparator() +
                "  \"id\": 398," + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"l5i-D4_48ZXGR3MF4VIhiBzSpGctEfCYu3l3cVPo215a3YDxZhLwCwH5x0FYesd4_uhfyJJcojntrigoaphe-Nm2K2SeBOws6c6lAb0zmN8gFPRG2wUYYGOJpSADtSWC6iZQsCronaHnk3pGutWvgumMniT0Rw8dEEVd5k36MfkknqZGT6ewOHxh0mz3kbVZy3wuAtG1sK13tokF4Qa3Qf9BsGkWcJ8ukpQ7YyDSJ_BnxjK8DgPbs48qH4f0QZZxXitavPDGkqUmLbxRAj7UeolevwkUv5nkV6X7tWdm2alZTrRNADR7oe8jmotIeDX1GSgE8T7VJion9sSTJKiyBw\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@example.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2018-04-27T14:27:35-04:00\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String QUERY_ACCT_REPLAY_NONCE_2 = "AkKqbV-WR4_5TI0QDZ_d7AfQv9fxDFB4hJvfgmeGJ0Q";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_1, QUERY_ACCT_RESPONSE_BODY_1, QUERY_ACCT_REPLAY_NONCE_1, ACCT_LOCATION, 200)
                .addChangeKeyRequestAndResponse("", CHANGE_KEY_RESPONSE_BODY, CHANGE_KEY_REPLAY_NONCE, 200)
                .updateAccountRequestAndResponse("", QUERY_ACCT_RESPONSE_BODY_2, QUERY_ACCT_REPLAY_NONCE_2, ACCT_PATH, 200)
                .build();
    }

    private ClientAndServer setupTestDeactivateAccount() {
        final String ACCT_PATH = "/acme/acct/6";

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"ZrMhBE165Rs\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "e8aGQUdmuZd2BI2iLmOSp-5Ac6ntDEm1q4SIHKJkXUY";

        final String UPDATE_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNiIsIm5vbmNlIjoiZThhR1FVZG11WmQyQkkyaUxtT1NwLTVBYzZudERFbTFxNFNJSEtKa1hVWSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNiJ9\",\"payload\":\"eyJzdGF0dXMiOiJkZWFjdGl2YXRlZCJ9\",\"signature\":\"qn9BveVslRMk2vUCluPbmzIL5yhsIIcObCYDVzo6aHrSJ-1YmphP_GmRiQj-_iBuCWBbYMJ6N58QE1unEQV_SeBRJHzDLuRgM7dGkiwr5h1BDlBRCYK3F4J_Tvd78c7NVjvBqDFvk7Przq4aBhleuvXfMkinmDuPdE9BOl-rofto7EVkwOBTEHoByKLtUhSzEX5KfTkp7pNObNkujz64bCVcBdpyHVP5Hrmwr3zUI76VFIf9dMaNeayvNgFy9L-YzOF1-KL_kGiGVCq27Dx7YguFnkY1hJVIz0fGA37bNG2plryCtGE2WO5vQ4vYpp1W0wXfPakyy4Xk7uwDLOuWcA\"}";

        final String UPDATE_ACCT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"id\": 6," + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"5fqeWddYJOZvOjktoJp_rjtCXyMWIyyJ-9Yy9s_LVlv0FH3rITex7KG-dL43-Zn_FIWZhAOLKAarBqjoOZ99Eav3qjS1gVA9YjlX5a6p6U9VhMBPQHYCycWo5J4o2FTnSxvO0Jb_w_9v_ysYMYVefEW1Ov0heF0UMuXX2rLEf5gSaXPlrJLDmcKVsip1r0yB8b_p3unUzb-RSHJFxbZKCL2mkBLZq7J85qjXxg_ypMe8NO-iloQ6_xLK4RXsLnCEQjb80rmPH34Qtm0BEa6Ghy1TGxJPQq7n1Q9QKNKIV0krc0S1kOwe7jsy6xKI6N9NtgL-Z0KA-oIgxucg7RWXiw\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@examples.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2018-11-27T15:56:09Z\"," + System.lineSeparator() +
                "  \"status\": \"deactivated\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String UPDATE_ACCT_REPLAY_NONCE = "vhoT4ml_X2qAyg3i7aBqKRpE2SMI5AzYqldekMzvy-8";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .updateAccountRequestAndResponse(UPDATE_ACCT_REQUEST_BODY, UPDATE_ACCT_RESPONSE_BODY, UPDATE_ACCT_REPLAY_NONCE, ACCT_PATH, 200)
                .build();
    }

    private ClientAndServer setupTestGetMetadataAllValuesSet() {
        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"TrOIFke5bdM\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator()  +
                "  \"meta\": {" + System.lineSeparator()  +
                "    \"caaIdentities\": [" + System.lineSeparator()  +
                "      \"happy-hacker-ca.invalid\"," + System.lineSeparator()  +
                "      \"happy-hacker2-ca.invalid\"" + System.lineSeparator()  +
                "    ]," + System.lineSeparator()  +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator()  +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"," + System.lineSeparator()  +
                "    \"externalAccountRequired\": true" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator()  +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator()  +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator()  +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator()  +
                "}";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .build();
    }

    private ClientAndServer setupTestGetMetadataSomeValuesSet() {
        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"iXia3_B0CrU\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"meta\": {" + System.lineSeparator() +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .build();
    }

    private ClientAndServer setupTestGetMetadataNoValuesSet() {
        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"iXia3_B0CrU\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .build();
    }

}
