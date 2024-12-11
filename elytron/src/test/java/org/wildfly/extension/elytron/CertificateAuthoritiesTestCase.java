/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.client.helpers.ClientConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
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
import org.mockserver.integration.ClientAndServer;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;


/**
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
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
    private static final PathAddress ROOT_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);
    private static final PathAddress CERT_AUTHORITY_ACCOUNT_ADDRESS = ROOT_ADDRESS.append(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT, CERTIFICATE_AUTHORITY_ACCOUNT_NAME);

    private static ClientAndServer server; // used to simulate a Let's Encrypt server instance


    public CertificateAuthoritiesTestCase() {
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
        server = new ClientAndServer(4001);
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(wildFlyElytronProvider, 1);
            }
        });
        csUtil = new CredentialStoreUtility("target/tlstest.keystore", CS_PASSWORD);
        csUtil.addEntry("the-key-alias", "Elytron");
        csUtil.addEntry("primary-password-alias", "Elytron");
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
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    @Test
    public void testWriteAttributeAliasOnly() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("alias");
        checkCertificateAuthorityIs(CertificateAuthority.LETS_ENCRYPT.getName());
        addCertificateAuthorityWithoutStagingUrl();
        try {

            ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, CERT_AUTHORITY_ACCOUNT_ADDRESS);
            operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.ALIAS);
            operation.get(ClientConstants.VALUE).set("new-alias");
            assertSuccess(services.executeOperation(operation));

            checkCertificateAuthorityAttributeIs(ElytronDescriptionConstants.ALIAS, "new-alias");
            checkCertificateAuthorityAttributeIsNot(ElytronDescriptionConstants.ALIAS, "alias");

        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
        }
    }

    @Test
    public void testWriteAttributeContactUrlsOnly() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("alias");
        checkCertificateAuthorityIs(CertificateAuthority.LETS_ENCRYPT.getName());
        addCertificateAuthorityWithoutStagingUrl();
        try {
            ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, CERT_AUTHORITY_ACCOUNT_ADDRESS);
            operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.CONTACT_URLS);
            operation.get(ClientConstants.VALUE).add("mailto:superadmin@anexample.com");
            assertSuccess(services.executeOperation(operation));
            checkCertificateAuthorityAttributeIs(ElytronDescriptionConstants.CONTACT_URLS, "[\"mailto:superadmin@anexample.com\"]");
            checkCertificateAuthorityAttributeIsNot(ElytronDescriptionConstants.CONTACT_URLS, "[\"mailto:admin@anexample.com\"]");
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
        }
    }

    @Test
    public void testWriteAttributeCredentialReferenceOnly() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("alias");
        checkCertificateAuthorityIs(CertificateAuthority.LETS_ENCRYPT.getName());
        addCertificateAuthorityWithoutStagingUrl();
        try {

            ModelNode credentialReference = new ModelNode();
            credentialReference.get(CredentialReference.CLEAR_TEXT).set("StorePassword");

            ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, CERT_AUTHORITY_ACCOUNT_ADDRESS);
            operation.get(ClientConstants.NAME).set(CredentialReference.CREDENTIAL_REFERENCE);
            operation.get(ClientConstants.VALUE).set(credentialReference);
            assertSuccess(services.executeOperation(operation));
            checkCertificateAuthorityAttributeIs(CredentialReference.CREDENTIAL_REFERENCE, "{\"clear-text\" => \"StorePassword\"}");
            checkCertificateAuthorityAttributeIsNot(CredentialReference.CREDENTIAL_REFERENCE, "{\"clear-text\" => \""+ KEYSTORE_PASSWORD +"\"}");
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
        }
    }

    @Test
    public void testChangeKeystoreAttributeOnly() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("alias");
        checkCertificateAuthorityIs(CertificateAuthority.LETS_ENCRYPT.getName());
        addCertificateAuthorityWithoutStagingUrl();
        try {

            addKeyStore("AccountsKeyStore2");
            ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, CERT_AUTHORITY_ACCOUNT_ADDRESS);
            operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.KEY_STORE);
            operation.get(ClientConstants.VALUE).set("AccountsKeyStore2");
            assertSuccess(services.executeOperation(operation));
            checkCertificateAuthorityAttributeIs(ElytronDescriptionConstants.KEY_STORE, "AccountsKeyStore2");
            checkCertificateAuthorityAttributeIsNot(ElytronDescriptionConstants.KEY_STORE, ACCOUNTS_KEYSTORE_NAME);

        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
        }
    }

    @Test
    public void testCreateAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1v2");
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
        addCertificateAuthorityAccountWithCustomCA("account1v2");
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
        addCertificateAuthorityAccountWithCustomCA("account1v2");
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
        addCertificateAuthorityAccountWithCustomCA("account1v2");
        server = setupTestCreateAccount();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(ElytronDescriptionConstants.STAGING).set(true);
            ModelNode result = services.executeOperation(operation);
            assertFailed(result);
            String failureDescription = result.get(FAILURE_DESCRIPTION).asString();
            assertTrue(failureDescription.contains("WFLYELY01043") && failureDescription.contains("ELY10057"));
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
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA(alias);
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/25";
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
            assertTrue(accountsKeyStore.containsAlias(alias));
            assertTrue(accountsKeyStore.getEntry(alias, new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray())) instanceof KeyStore.PrivateKeyEntry);
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testCreateAccountWithoutAgreeingToTermsOfService() throws Exception {
        String alias = "invalid";
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA(alias);
        server = setupTestCreateAccountWithoutAgreeingToTermsOfService();
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(false);
            ModelNode result = services.executeOperation(operation);
            assertFailed(result);
            String failureDescription = result.get(FAILURE_DESCRIPTION).asString();
            assertTrue(failureDescription.contains("WFLYELY01043") && failureDescription.contains("must agree to terms of service"));
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testUpdateAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1v2", new String[] { "mailto:certificates@anexample.com", "mailto:admin@anexample.com" });
        server = setupTestUpdateAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.UPDATE_ACCOUNT);
            operation.get(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE).set(false);
            assertSuccess(services.executeOperation(operation));
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testChangeAccountKey() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account6v2");
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
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testDeactivateAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account10v2");
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/27";
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
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testGetMetadataAllValuesSet() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account8v2");
        server = setupTestGetMetadataAllValuesSet();
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
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testGetMetadataSomeValuesSet() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account8v2");
        server = setupTestGetMetadataSomeValuesSet();
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
            removeCertificateAuthority();
            removeKeyStore(ACCOUNTS_KEYSTORE_NAME);
        }
    }

    @Test
    public void testGetMetadataNoValuesSet() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account8v2");
        server = setupTestGetMetadataNoValuesSet();
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
            removeCertificateAuthority();
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
            assertEquals(SIMULATED_LETS_ENCRYPT_ENDPOINT, result.get(ElytronDescriptionConstants.URL).asString());
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
            assertEquals(SIMULATED_LETS_ENCRYPT_ENDPOINT, result.get(ElytronDescriptionConstants.URL).asString());
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
    public void testWriteCertificateAuthorityInCertificateAuthorityAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityAccount("alias");
        checkCertificateAuthorityIs(CertificateAuthority.LETS_ENCRYPT.getName());
        addCertificateAuthorityWithoutStagingUrl();
        try {
            writeAttributeCertificateAuthorityInCertificateAuthorityAccount();
            checkCertificateAuthorityIs(CERTIFICATE_AUTHORITY_NAME);
        } finally {
            removeCertificateAuthorityAccount();
            removeCertificateAuthority();
        }
    }

    private void writeAttributeCertificateAuthorityInCertificateAuthorityAccount() {
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
        operation.get(ElytronDescriptionConstants.URL).set(SIMULATED_LETS_ENCRYPT_ENDPOINT);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityWithStagingUrl() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.URL).set(SIMULATED_LETS_ENCRYPT_ENDPOINT);
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

    private void checkCertificateAuthorityAttributeIs(String attribute, String expected) {
        ModelNode result = readResourceNode();
        assertEquals(expected, result.get(attribute).asString());
    }

    private void checkCertificateAuthorityAttributeIsNot(String attribute, String expected) {
        ModelNode result = readResourceNode();
        assertNotEquals(expected, result.get(attribute).asString());
    }

    private ModelNode readResourceNode() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        return assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
    }

    private void checkCertificateAuthorityIs(String certificateAuthorityName) {
        ModelNode operation = new ModelNode();
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
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).add("mailto:admin@anexample.com");
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
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).add("mailto:admin@anexample.com");
        operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).set(CERTIFICATE_AUTHORITY_NAME);
        operation.get(ElytronDescriptionConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronDescriptionConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityAccountWithCustomCA(String alias, String[] contactUrlsList) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", CERTIFICATE_AUTHORITY_ACCOUNT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        ModelNode contactUrls = new ModelNode();
        for (String contactUrl : contactUrlsList) {
            contactUrls = contactUrls.add(contactUrl);
        }
        operation.get(ElytronDescriptionConstants.CONTACT_URLS).set(contactUrls);
        operation.get(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY).set(CERTIFICATE_AUTHORITY_NAME);
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
                "  \"wnR-SBn2GN4\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
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

        final String NEW_NONCE_RESPONSE = "zincwl_lThkXLp0V7HAAcQEbIrx1R-gTI_ZQ8INAsrR5aQU";

        final String NEW_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoOE9lZTViZURSZ3hOUGVfZU1FOUg2Vm83NEZ1ZzhIZ3Jpa2ZiZkNhVTNsS0Y2NDhRRzFYMWtHRFpUaEF5OGRhcUo4YnY2YzNQSmRueDJIcjhqT3psNTA5Ym5NNmNDV2Z5d1RwY0lab1V6UVFaTFlfSzhHTURBeWdsc1FySXRnQ2lRYWxJcWJ1SkVrb2MzV1FBSXhKMjN4djliSzV4blZRa1RXNHJWQkFjWU5Rd29CakdZT1dTaXpUR2ZqZ21RcVRYbG9hYW1GWkpuOTdIbmIxcWp5NVZZbTA2YnV5cXdBYUdIczFDTHUzY0xaZ1FwVkhRNGtGc3prOFlPNVVBRWppb2R1Z1dwWlVSdTlUdFJLek4wYmtFZGVRUFlWcGF1cFVxMWNxNDdScDJqcVZVWGRpUUxla3l4clFidDhBMnVHNEx6RFF1LWI0Y1pwcG16YzNobGhTR3cifSwibm9uY2UiOiJ6aW5jd2xfbFRoa1hMcDBWN0hBQWNRRWJJcngxUi1nVElfWlE4SU5Bc3JSNWFRVSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AYW5leGFtcGxlLmNvbSJdfQ\",\"signature\":\"RPIM6OGU33uPXurdKJuKwXNkJbgEXcUr9QxEBjynhzROWGreB_p6esSlTxTkkNmP8EIBmcc2g5FjkBHjwIhqcvVC5AHhJ0XMq-WhRqlMdwQFn55nuG5O4nOrfr-5u31jw8DGnHs0Lv3_X4rVfLomT8y1eZ_IzPdZzw_QaEJWWIlrn-H_AkcmbZUxvozJ1yvadQ6cUUl9Hw6Kj8sSSdcUQ9tGtfAOiiXDtH-42-G0pMUivnJKyF5m8HdMXqKeFvRk4gvei-NdCzK44uehvoRTULQFbeu-h8YzWBZJwP1LXX8LSyLacxrXH8vukN8qjbBXXrB-QcimuPba4jmF124IDg\"}";

        final String NEW_ACCT_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"key\": {" + System.lineSeparator()  +
                "    \"kty\": \"RSA\"," + System.lineSeparator()  +
                "    \"n\": \"h8Oee5beDRgxNPe_eME9H6Vo74Fug8HgrikfbfCaU3lKF648QG1X1kGDZThAy8daqJ8bv6c3PJdnx2Hr8jOzl509bnM6cCWfywTpcIZoUzQQZLY_K8GMDAyglsQrItgCiQalIqbuJEkoc3WQAIxJ23xv9bK5xnVQkTW4rVBAcYNQwoBjGYOWSizTGfjgmQqTXloaamFZJn97Hnb1qjy5VYm06buyqwAaGHs1CLu3cLZgQpVHQ4kFszk8YO5UAEjiodugWpZURu9TtRKzN0bkEdeQPYVpaupUq1cq47Rp2jqVUXdiQLekyxrQbt8A2uG4LzDQu-b4cZppmzc3hlhSGw\"," + System.lineSeparator()  +
                "    \"e\": \"AQAB\"" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"contact\": [" + System.lineSeparator()  +
                "    \"mailto:admin@anexample.com\"" + System.lineSeparator()  +
                "  ]," + System.lineSeparator()  +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator()  +
                "  \"createdAt\": \"2019-07-12T16:52:19.171896513Z\"," + System.lineSeparator()  +
                "  \"status\": \"valid\"" + System.lineSeparator()  +
                "}";

        final String NEW_ACCT_REPLAY_NONCE = "taroOQPjumKybWIQEmqmB2DZ8ouIQ5uBoaDQZosCDyUzbJs";
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
                "  \"09glGRT1wYc\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
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

        final String NEW_NONCE_RESPONSE = "taro09OqE4A5hQ1S9HcwyYZ8RJg3GnWciidIzG3CQxUr6Co";

        final String NEW_ACCT_REQUEST_BODY = "";

        final String NEW_ACCT_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"key\": {" + System.lineSeparator()  +
                "    \"kty\": \"RSA\"," + System.lineSeparator()  +
                "    \"n\": \"ybX5MhMnxJ2p0DaRsY5nO5xNpr5AQSydmsXpxeXyG3hYJsWyDUv2bvmfi2luQYkbOh_v6g9V5MsmsXjm8xkL7Fh_5gPCzx_ECTmPAeCgVO30j7KWPYtOmkVEAC5rSRKFhtBaQjlCbX5p20J7PTvTD2lxksSk2gILOs2r-D3Pa_kHmWkWqV9oB4wIiH3qBDINPffvyjSg1TA6T6MjzAZTwwx0PH5ZUZ27ht5D9GEjFycy1qmpucOSBzyd92fj5q25EIND59t4__3x3bOIDZ_IjBgQkFy5ef5nVUcxzDhQX7fb-_NCBvbZQjne6U1gd4QIb2eGKijTJDK59Mkbc3DkHQ\"," + System.lineSeparator()  +
                "    \"e\": \"AQAB\"" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"contact\": [" + System.lineSeparator()  +
                "    \"mailto:admin@anexample.com\"" + System.lineSeparator()  +
                "  ]," + System.lineSeparator()  +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator()  +
                "  \"createdAt\": \"2019-07-17T20:04:57.993736012Z\"," + System.lineSeparator()  +
                "  \"status\": \"valid\"" + System.lineSeparator()  +
                "}";

        final String NEW_ACCT_REPLAY_NONCE = "zincrxWHWICvs8INUWbrSrx1o7nxJVrgFCxUE5uZcyr6BTs";
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/25";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(NEW_ACCT_REQUEST_BODY, NEW_ACCT_RESPONSE_BODY, NEW_ACCT_REPLAY_NONCE, NEW_ACCT_LOCATION, 201)
                .build();
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

    private ClientAndServer setupTestUpdateAccount() {

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"glYy9rwsgUk\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
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

        final String NEW_NONCE_RESPONSE = "zinckwe2yTRoZS7IVKa7d5SqTgkDkk7br3Ee1pYknH8yMjU";

        final String QUERY_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoOE9lZTViZURSZ3hOUGVfZU1FOUg2Vm83NEZ1ZzhIZ3Jpa2ZiZkNhVTNsS0Y2NDhRRzFYMWtHRFpUaEF5OGRhcUo4YnY2YzNQSmRueDJIcjhqT3psNTA5Ym5NNmNDV2Z5d1RwY0lab1V6UVFaTFlfSzhHTURBeWdsc1FySXRnQ2lRYWxJcWJ1SkVrb2MzV1FBSXhKMjN4djliSzV4blZRa1RXNHJWQkFjWU5Rd29CakdZT1dTaXpUR2ZqZ21RcVRYbG9hYW1GWkpuOTdIbmIxcWp5NVZZbTA2YnV5cXdBYUdIczFDTHUzY0xaZ1FwVkhRNGtGc3prOFlPNVVBRWppb2R1Z1dwWlVSdTlUdFJLek4wYmtFZGVRUFlWcGF1cFVxMWNxNDdScDJqcVZVWGRpUUxla3l4clFidDhBMnVHNEx6RFF1LWI0Y1pwcG16YzNobGhTR3cifSwibm9uY2UiOiJ6aW5ja3dlMnlUUm9aUzdJVkthN2Q1U3FUZ2tEa2s3YnIzRWUxcFlrbkg4eU1qVSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"AD35pVNdrGh_Ee6hPsEy57bgWi6P2u9Pnz_3_NYsMSEosbEZkOsnbuqvr6WDy2F1WCgGdL37zcpzxmYpO-7Jkh5Z_cAFQwOrbo6DZs3s7IyXocNebvE_pVsXx1sYiE4hTuPrRJCFDkXeYVCaIusr8Wjp4Zv2EZPqmMxxpV1cX3eIEwOgO4bkK1ni0Njw1MFPBHxtzJiBMLwvnFCvjbHDe6rqMYF_cWYclGhRuwdOsA97HggjX70FczicaNL2nwaIxEnqr1hvJs5IRYH8z0F-PImFApvEI21xJug372HfwolKgMr0nfBvgSCCOMLiGlchjIYMWMpQQ-rXtiECdaNIPQ\"}";

        final String QUERY_ACCT_RESPONSE_BODY = "";

        final String QUERY_ACCT_REPLAY_NONCE = "taro4jDwwpaHvLRPOKMPVNOy83ulFHS1I7ANXO-bBCxe1xs";

        final String ACCT_PATH = "/acme/acct/5";
        final String ACCT_LOCATION = "http://localhost:4001" + ACCT_PATH;

        final String UPDATE_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvNSIsIm5vbmNlIjoidGFybzRqRHd3cGFIdkxSUE9LTVBWTk95ODN1bEZIUzFJN0FOWE8tYkJDeGUxeHMiLCJ1cmwiOiJodHRwOi8vbG9jYWxob3N0OjQwMDEvYWNtZS9hY2N0LzUifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6ZmFsc2UsImNvbnRhY3QiOlsibWFpbHRvOmNlcnRpZmljYXRlc0BhbmV4YW1wbGUuY29tIiwibWFpbHRvOmFkbWluQGFuZXhhbXBsZS5jb20iXX0\",\"signature\":\"ACtHDATUrrv3HqnK2A8AuIvRZMBaIMOlJiXcMnPgCr2UlSrtsmwRDvEYevpuEk1AHGomyDZdeo92JxB2ijH9v3qMMBWcPWck0kuovAUvlr8qNv7M3WLtfNhLg26qcOOTkvUTAHHT-gO31Q45x7RUIyO2gl4Qz4lFBJX2kGseT2ay-jaS6KViud05G_uhhN7DC3D0KDPNdjzWlp7q78ZEdguBnSeVKb7W0Vxl8UrVdvBoVV3VRHqyiV-PZ1Of33a_R5Q5ZWKfMPfE7ol_LVhDMsSsn7ZKQ_fzFxzxk7clacDaIezKhcDr4JAokxFJHPBDJ1DaMGI_9Z1GAukYKLrogA\"}";

        final String UPDATE_ACCT_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"key\": {" + System.lineSeparator()  +
                "    \"kty\": \"RSA\"," + System.lineSeparator()  +
                "    \"n\": \"h8Oee5beDRgxNPe_eME9H6Vo74Fug8HgrikfbfCaU3lKF648QG1X1kGDZThAy8daqJ8bv6c3PJdnx2Hr8jOzl509bnM6cCWfywTpcIZoUzQQZLY_K8GMDAyglsQrItgCiQalIqbuJEkoc3WQAIxJ23xv9bK5xnVQkTW4rVBAcYNQwoBjGYOWSizTGfjgmQqTXloaamFZJn97Hnb1qjy5VYm06buyqwAaGHs1CLu3cLZgQpVHQ4kFszk8YO5UAEjiodugWpZURu9TtRKzN0bkEdeQPYVpaupUq1cq47Rp2jqVUXdiQLekyxrQbt8A2uG4LzDQu-b4cZppmzc3hlhSGw\"," + System.lineSeparator()  +
                "    \"e\": \"AQAB\"" + System.lineSeparator()  +
                "  }," + System.lineSeparator()  +
                "  \"contact\": [" + System.lineSeparator()  +
                "    \"mailto:admin@anexample.com\"," + System.lineSeparator()  +
                "    \"mailto:certificates@anexample.com\"" + System.lineSeparator()  +
                "  ]," + System.lineSeparator()  +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator()  +
                "  \"createdAt\": \"2019-07-12T16:52:19Z\"," + System.lineSeparator()  +
                "  \"status\": \"valid\"" + System.lineSeparator()  +
                "}";

        final String UPDATE_ACCT_REPLAY_NONCE = "zincExjmJtQPd5Uj0U5a9-tCXBmis2U623JoApXOoIN6Oec";

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
                "  \"uBZzMh54N6Q\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "taroUPFo0aLcaedcnx3SpPDbSr2j84m3qw8rW2tZJnZm2FE";

        final String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJpcVZkd3laNGl0VlNoOFVWX2Z3NlpnVjh3Mk56SEtGdzZWeWl5cGRLMmlyUkk0T3BMdWhJNEhqQ3pSTHR0WkJPX3ZLRjFZaTB1dVdMaFFzMnVpWlJ5eXFCa0R6SXU3UnIwZWp2T2UtLVc2aWhLanE2WnNCQ2Q3eDhMUl9yYXp1X242V1BkQWJZeWZxdnBuS0V0bGZxdW4yMWJnWk1yT1R4YW0tS0FNS2kyNlJlVi1oVDlYU05kbWpoWnhtSzZzQ0NlTl9JOTVEUXZ1VG55VFctUUJFd2J2MVVOTEEtOXRIR3QyUzQ0a2JvT0JtemV6RGdPSVlfNFpNd3MtWXZucFd5VElsU0k3TmlNMVhKb1NXMHlSLWdjaFlRT1FuSEU2QUhtdk5KbV9zSTlZN0ZhQmJVeVJpS0RnTi1vZlR3cXNzdzZ2ejVucUxUanU3Y2dzWld4S1dESHcifSwibm9uY2UiOiJ0YXJvVVBGbzBhTGNhZWRjbngzU3BQRGJTcjJqODRtM3F3OHJXMnRaSm5abTJGRSIsInVybCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL25ldy1hY2N0In0\",\"payload\":\"eyJvbmx5UmV0dXJuRXhpc3RpbmciOnRydWV9\",\"signature\":\"XzrLN-kD_C0TAN7NhxdUJqIeAbUw2WWp5pwnK5tPkF177gUStWbyuCKpK68lMgmQa_EpImAUNQ8wT-oXP4ARZCYcuJZ-Hii1qXYGkXC9DCGuEPffk4c4M6F7JhA4FdlV_0nAV5i5Q6nILtn7nARqSc239jPJasM-ZjGdhfc7UCObpufoZqCChQn5N8MfLnZA8-SZK9M5pm9SM72JjUc3L9FRvFCG8p7iU_A0Lt7g9yD9LyddtONuVrzhmIm43e3pU3CaarhxA7vHlS-Vahnl-8fFCwEnsaC3b_EMfZYxBvvI28n4tn7QgwcOy6kLaNp1TXs0vxP23v_3y5dO79GSig\"}";

        final String QUERY_ACCT_RESPONSE_BODY_1 = "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "zinchourU8rrtvhVwzICl7mpth8YWPTP-7Z4aU2UNEXVONs";

        final String ACCT_PATH = "/acme/acct/10";
        final String ACCT_LOCATION = "http://localhost:4001" + ACCT_PATH;

        final String CHANGE_KEY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"iqVdwyZ4itVSh8UV_fw6ZgV8w2NzHKFw6VyiypdK2irRI4OpLuhI4HjCzRLttZBO_vKF1Yi0uuWLhQs2uiZRyyqBkDzIu7Rr0ejvOe--W6ihKjq6ZsBCd7x8LR_razu_n6WPdAbYyfqvpnKEtlfqun21bgZMrOTxam-KAMKi26ReV-hT9XSNdmjhZxmK6sCCeN_I95DQvuTnyTW-QBEwbv1UNLA-9tHGt2S44kboOBmzezDgOIY_4ZMws-YvnpWyTIlSI7NiM1XJoSW0yR-gchYQOQnHE6AHmvNJm_sI9Y7FaBbUyRiKDgN-ofTwqssw6vz5nqLTju7cgsZWxKWDHw\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@anexample.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2019-07-15T19:53:57Z\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}" + System.lineSeparator();
        final String CHANGE_KEY_REPLAY_NONCE = "zinchourU8rrtvhVwzICl7mpth8YWPTP-7Z4aU2UNEXVONs";

        final String QUERY_ACCT_RESPONSE_BODY_2 = "{" + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"rDYXH58ys0MT97z_7gLkNFmQSXR_eb49c_55Wk3eSQpT3sUyq1YuKGWRc92-nBz6twdXa3VixAoXkxWhCxu0A_rbo_eTXe8WlVpCBKr5rM6wAlKENDrSQZD6MdzLLGaA207a_WFG7UPDUKH2_qH98CN5eleDn0TUYa6RYFF6j5D_T1Jg5nhC9I3P4zQ-WDNYvYEkEqPUgzK4cPOBXiMB_XFb2wf8mpm2pN8Fr5XOpQYeY1YXH-HGuYG5StUq__BDForbbQ_R7HSemdMglwujM46LteCvAr-Z5XBa2ue7mRK2RAkk_3-3Tmuj8ewyNGFw_AANvl8nyhZ-BU4VZvw-HQ\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@anexample.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2019-07-15T19:53:57Z\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String QUERY_ACCT_REPLAY_NONCE_2 = "taroG712eL8LB6nca1rdSadsNXQftZ5wOLN8unyyuakUuLE";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_1, QUERY_ACCT_RESPONSE_BODY_1, QUERY_ACCT_REPLAY_NONCE_1, ACCT_LOCATION, 200)
                .addChangeKeyRequestAndResponse("", CHANGE_KEY_RESPONSE_BODY, CHANGE_KEY_REPLAY_NONCE, 200)
                .updateAccountRequestAndResponse("", QUERY_ACCT_RESPONSE_BODY_2, QUERY_ACCT_REPLAY_NONCE_2, ACCT_PATH, 200)
                .build();
    }

    private ClientAndServer setupTestDeactivateAccount() {
        final String ACCT_PATH = "/acme/acct/27";

        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"3eIUmKQ9AnQ\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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

        final String NEW_NONCE_RESPONSE = "zincZiszqbk6nHHJlWsqX1-GJ1PSxqQGAtTIU0SRPrqF06E";
        final String UPDATE_ACCT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMjciLCJub25jZSI6InppbmNaaXN6cWJrNm5ISEpsV3NxWDEtR0oxUFN4cVFHQXRUSVUwU1JQcnFGMDZFIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYWNjdC8yNyJ9\",\"payload\":\"eyJzdGF0dXMiOiJkZWFjdGl2YXRlZCJ9\",\"signature\":\"K_6fwCbn5ZGsTgGeibADLSiGXi-x5RU8SlOChF_tL6HAhmypXmdU94emuAMtPjfMOoyjcKd_g0Fl2TpgFW8eLdb7JaiFLpisnYWxAJ99eK2TlNifpbKQQ1N-s1Y13jO2KcPZfrRI_bWFl5J66tKUvfuqewJA2QtFmqn1H7s2N6Yn7hOQ4FZAs7FdYu9Q6PNcwKb5lVrysPoHKXEeRdjYMTW7J6W9ajxxqm3C2mQ-s-kwZgDs_4PH4-D9RTMonSMqcK-dPoimT-wlEicllDYIxfTfeAQpL4MXD5eAuSje2c5DRMWL4WQdxBnRu5ytAD1vQ-cvKqwqCI2bRR9SCJn1VQ\"}";

        final String UPDATE_ACCT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"xnddRMbKWbaEmR-SlgpG3C2SdsUtcqsC8Xp4bHWtW9PLTWz18IGupxtP_EtRjuvBZZIHObY3-NvVRxWztLpFqYi-ns07rKWGQ49Y3Q2KC5lEAezahFB1_PGBf5bAO0qZZmX17vRxXfdB3LipT_I4W-CkRq_zsJnM4cgOvRGqpt_fBHDajN9VWKXWl93i-1ddANMe2enTgHm60k5eFXZaxC_tCnM7ryHRLyp36RRs3-R9AgMtBgOruFWSIJjISwknvGi2Fu6xVwc6hRj7lB4xuJPzHHNjXztSiHaLWrADS7mmA6nbUF84kUGe6-AgsNPSgh-gq_fiGYCxvJ9Bn1qbPw\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@anexample.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"10.77.77.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2019-07-17T20:34:28Z\"," + System.lineSeparator() +
                "  \"status\": \"deactivated\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String UPDATE_ACCT_REPLAY_NONCE = "taro1luvl7fpnbTOOb2dTFMLlDW--VyPhD_rG5GCpvbqyOE";

        return new AcmeMockServerBuilder(server)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .updateAccountRequestAndResponse(UPDATE_ACCT_REQUEST_BODY, UPDATE_ACCT_RESPONSE_BODY, UPDATE_ACCT_REPLAY_NONCE, ACCT_PATH, 200)
                .build();
    }

    private ClientAndServer setupTestGetMetadataAllValuesSet() {
        // set up a mock Let's Encrypt server
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator()  +
                "  \"JDkpnLkaC1Q\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator()  +
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
                "  \"LRkPnZpS4yE\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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
                "  \"N6HzXUZ-eWI\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
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
