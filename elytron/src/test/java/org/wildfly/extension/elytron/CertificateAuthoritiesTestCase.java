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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.wildfly.extension.elytron.common.CredentialStoreUtility;
import org.wildfly.extension.elytron.common.ElytronCommonCertificateAuthoritiesTestCase;
import org.wildfly.extension.elytron.common.ElytronCommonConstants;
import org.wildfly.extension.elytron.common.JdkUtils;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;

/**
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class CertificateAuthoritiesTestCase extends ElytronCommonCertificateAuthoritiesTestCase {


    private static final String CERTIFICATE_AUTHORITY_NAME = "CertAuthority";
    private static final String SIMULATED_LETS_ENCRYPT_ENDPOINT = "http://localhost:4001/directory"; // simulated Let's Encrypt server instance
    private static final String CERTIFICATE_AUTHORITY_TEST_URL = "http://www.test.com";
    private static final String ACCOUNTS_KEYSTORE_NAME = "AccountsKeyStore";
    private static final String KEYSTORE_PASSWORD = "elytron";

    public CertificateAuthoritiesTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private KernelServices services = null;

    @Override
    protected KernelServices getServices() {
        return services;
    }

    @BeforeClass
    public static void initTests() {
        setServer(new ClientAndServer(4001));
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(wildFlyElytronProvider, 1);
            }
        });
        setCSUtil(new CredentialStoreUtility("target/tlstest.keystore", CS_PASSWORD));
        getCsUtil().addEntry("the-key-alias", "Elytron");
        getCsUtil().addEntry("primary-password-alias", "Elytron");
    }

    @AfterClass
    public static void cleanUpTests() {
        if (getServer() != null) {
            getServer().stop();
        }
        getCsUtil().cleanUp();
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
    public void testCreateAccount() throws Exception {
        addKeyStore(ACCOUNTS_KEYSTORE_NAME);
        addCertificateAuthorityWithoutStagingUrl();
        addCertificateAuthorityAccountWithCustomCA("account1v2");
        setServer(setupTestCreateAccount());
        AcmeAccount acmeAccount = getAcmeAccount();
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CREATE_ACCOUNT);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
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
        setServer(setupTestCreateAccount());
        AcmeAccount acmeAccount = getAcmeAccount();
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CREATE_ACCOUNT);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
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
        setServer(setupTestCreateAccount());
        AcmeAccount acmeAccount = getAcmeAccount();
        final String NEW_ACCT_LOCATION = "http://localhost:4001/acme/acct/384";
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CREATE_ACCOUNT);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(ElytronCommonConstants.STAGING).set(true);
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
        setServer(setupTestCreateAccount());
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CREATE_ACCOUNT);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
            operation.get(ElytronCommonConstants.STAGING).set(true);
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
        setServer(setupTestCreateAccountNonExistingAlias());
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CREATE_ACCOUNT);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(true);
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
        setServer(setupTestCreateAccountWithoutAgreeingToTermsOfService());
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CREATE_ACCOUNT);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(false);
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
        setServer(setupTestUpdateAccount());
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.UPDATE_ACCOUNT);
            operation.get(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE).set(false);
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
        setServer(setupTestChangeAccountKey());
        // old account
        AcmeAccount acmeAccount = getAcmeAccount();
        X509Certificate oldCertificate = acmeAccount.getCertificate();
        X500Principal oldDn = acmeAccount.getDn();
        try {
            assertNull(acmeAccount.getAccountUrl());
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.CHANGE_ACCOUNT_KEY);
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
        setServer(setupTestDeactivateAccount());
        AcmeAccount acmeAccount = getAcmeAccount();
        acmeAccount.setAccountUrl(ACCT_LOCATION);
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.DEACTIVATE_ACCOUNT);
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
        setServer(setupTestGetMetadataAllValuesSet());
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.GET_METADATA);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals("https://boulder:4431/terms/v7", result.get(ElytronCommonConstants.TERMS_OF_SERVICE).asString());
            assertEquals("https://github.com/letsencrypt/boulder", result.get(ElytronCommonConstants.WEBSITE).asString());
            List<ModelNode> caaIdentities = result.get(ElytronCommonConstants.CAA_IDENTITIES).asList();
            assertEquals("happy-hacker-ca.invalid", caaIdentities.get(0).asString());
            assertEquals("happy-hacker2-ca.invalid", caaIdentities.get(1).asString());
            assertTrue(result.get(ElytronCommonConstants.EXTERNAL_ACCOUNT_REQUIRED).asBoolean());
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
        setServer(setupTestGetMetadataSomeValuesSet());
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.GET_METADATA);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals("https://boulder:4431/terms/v7", result.get(ElytronCommonConstants.TERMS_OF_SERVICE).asString());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronCommonConstants.WEBSITE).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronCommonConstants.CAA_IDENTITIES).getType());
            assertFalse(result.get(ElytronCommonConstants.EXTERNAL_ACCOUNT_REQUIRED).asBoolean());
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
        setServer(setupTestGetMetadataNoValuesSet());
        AcmeAccount acmeAccount = getAcmeAccount();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
            operation.get(ClientConstants.OP).set(ElytronCommonConstants.GET_METADATA);
            ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
            assertEquals(ModelType.UNDEFINED, result.get(ElytronCommonConstants.TERMS_OF_SERVICE).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronCommonConstants.WEBSITE).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronCommonConstants.CAA_IDENTITIES).getType());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronCommonConstants.EXTERNAL_ACCOUNT_REQUIRED).getType());
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
        operation.get(ElytronCommonConstants.URL).set(CERTIFICATE_AUTHORITY_TEST_URL);
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
            assertEquals(SIMULATED_LETS_ENCRYPT_ENDPOINT, result.get(ElytronCommonConstants.URL).asString());
            assertEquals(SIMULATED_LETS_ENCRYPT_ENDPOINT, result.get(ElytronCommonConstants.STAGING_URL).asString());
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
            assertEquals(SIMULATED_LETS_ENCRYPT_ENDPOINT, result.get(ElytronCommonConstants.URL).asString());
            assertEquals(ModelType.UNDEFINED, result.get(ElytronCommonConstants.STAGING_URL).getType());
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
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
        operation.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronCommonConstants.CERTIFICATE_AUTHORITY);
        operation.get(ClientConstants.VALUE).set(CERTIFICATE_AUTHORITY_NAME);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityWithoutStagingUrl() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.URL).set(SIMULATED_LETS_ENCRYPT_ENDPOINT);
        assertSuccess(services.executeOperation(operation));
    }

    private void addCertificateAuthorityWithStagingUrl() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority", CERTIFICATE_AUTHORITY_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.URL).set(SIMULATED_LETS_ENCRYPT_ENDPOINT);
        operation.get(ElytronCommonConstants.STAGING_URL).set(SIMULATED_LETS_ENCRYPT_ENDPOINT);
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
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        ModelNode result = assertSuccess(getServices().executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals(certificateAuthorityName, result.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY).asString());
    }

    private void addCertificateAuthorityAccount(String alias) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.CONTACT_URLS).add("mailto:admin@anexample.com");
        operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY).set(CertificateAuthority.LETS_ENCRYPT.getName());
        operation.get(ElytronCommonConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronCommonConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(getServices().executeOperation(operation));
    }

    private void addCertificateAuthorityAccountWithCustomCA(String alias) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.CONTACT_URLS).add("mailto:admin@anexample.com");
        operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY).set(CERTIFICATE_AUTHORITY_NAME);
        operation.get(ElytronCommonConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronCommonConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(getServices().executeOperation(operation));
    }

    private void addCertificateAuthorityAccountWithCustomCA(String alias, String[] contactUrlsList) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        ModelNode contactUrls = new ModelNode();
        for (String contactUrl : contactUrlsList) {
            contactUrls = contactUrls.add(contactUrl);
        }
        operation.get(ElytronCommonConstants.CONTACT_URLS).set(contactUrls);
        operation.get(ElytronCommonConstants.CERTIFICATE_AUTHORITY).set(CERTIFICATE_AUTHORITY_NAME);
        operation.get(ElytronCommonConstants.KEY_STORE).set(ACCOUNTS_KEYSTORE_NAME);
        operation.get(ElytronCommonConstants.ALIAS).set(alias);
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(getServices().executeOperation(operation));
    }

    private void removeCertificateAuthorityAccount() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("certificate-authority-account", getCertificateAuthorityAccountName());
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(getServices().executeOperation(operation));
    }

    private void addKeyStore(String keyStoreName) throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        Files.copy(resources.resolve("account.keystore"), resources.resolve("test-copy.keystore"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronCommonConstants.PATH).set(resources + "/test-copy.keystore");
        operation.get(ElytronCommonConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(KEYSTORE_PASSWORD);
        assertSuccess(getServices().executeOperation(operation));
    }

    private void removeKeyStore(String keyStoreName) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(getServices().executeOperation(operation));
    }
}
