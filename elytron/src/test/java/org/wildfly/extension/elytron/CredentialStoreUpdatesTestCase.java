/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.security.CredentialReference.ALIAS;
import static org.jboss.as.controller.security.CredentialReference.CLEAR_TEXT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * Tests for automatic updates of credential stores.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class CredentialStoreUpdatesTestCase extends AbstractSubsystemTest {

    private static final String CS_PASSWORD1 = "super_secret1";
    private static final String CS_PASSWORD2 = "super_secret2";
    private static final String CS_PASSWORD3 = "super_secret3";
    private static final String EMPTY_CS_NAME1 = "store1";
    private static final String EMPTY_CS_PATH1 = "target/test.credential.store1";
    private static final String EMPTY_CS_NAME2 = "store2";
    private static final String EMPTY_CS_PATH2 = "target/test.credential.store2";
    private static final String NON_EMPTY_CS_NAME = "store3";
    private static final String NON_EMPTY_CS_PATH = "target/test.credential.store3";
    private static final String KS_NAME = "test-keystore";
    private static final String CLEAR_TEXT_ATTRIBUTE_NAME = CredentialReference.CREDENTIAL_REFERENCE + "." + CLEAR_TEXT;
    private static final String ALIAS_ATTRIBUTE_NAME = CredentialReference.CREDENTIAL_REFERENCE + "." + ALIAS;
    private static final String EXISTING_ALIAS = "existingAlias";
    private static final String EXISTING_PASSWORD = "existingPassword";
    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility emptyCSUtil1 = null;
    private static CredentialStoreUtility emptyCSUtil2 = null;
    private static CredentialStoreUtility nonEmptyCSUtil = null;
    private KernelServices services = null;

    public CredentialStoreUpdatesTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @BeforeClass
    public static void initTests() {
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(wildFlyElytronProvider, 1);
            }
        });
    }

    @Before
    public void init() throws Exception {
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("credential-store-updates.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }
        emptyCSUtil1 = new CredentialStoreUtility(EMPTY_CS_PATH1, CS_PASSWORD1);
        emptyCSUtil2 = new CredentialStoreUtility(EMPTY_CS_PATH2, CS_PASSWORD2);
        nonEmptyCSUtil = new CredentialStoreUtility(NON_EMPTY_CS_PATH, CS_PASSWORD3);
    }

    @After
    public void cleanUpCredentialStores() {
        emptyCSUtil1.cleanUp();
        emptyCSUtil2.cleanUp();
        nonEmptyCSUtil.cleanUp();
    }

    @AfterClass
    public static void cleanUpTests() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Security.removeProvider(wildFlyElytronProvider.getName());
                return null;
            }
        });
    }

    @Test
    public void testCredentialReferenceAddNewEntryToEmptyCredentialStore() throws Exception {
        String alias = "newAlias";
        String password = "newPassword";
        try {
            CredentialStore credentialStore = getCredentialStore(EMPTY_CS_NAME1);
            assertEquals(0, credentialStore.getAliases().size());

            addKeyStoreWithCredentialReference(KS_NAME, EMPTY_CS_NAME1, alias, password, false);
            assertEquals(1, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(alias, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(alias, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));

            assertEquals(null, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(alias, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceAddNewEntryWithGeneratedAliasToEmptyCredentialStore() throws Exception {
        String password = "newPassword";
        try {
            CredentialStore credentialStore = getCredentialStore(EMPTY_CS_NAME2);
            assertEquals(0, credentialStore.getAliases().size());

            String generatedAlias = addKeyStoreWithCredentialReference(KS_NAME, EMPTY_CS_NAME2,null, password, false);
            assertEquals(1, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(generatedAlias, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(generatedAlias, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));

            assertEquals(null, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(generatedAlias, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceAddNewEntry() throws Exception {
        String alias = "newAlias";
        String password = "newPassword";
        try {
            CredentialStore credentialStore = getCredentialStore();
            assertFalse(credentialStore.exists(alias, PasswordCredential.class));
            int numAliases = credentialStore.getAliases().size();

            addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, alias, password, false);
            assertEquals(numAliases + 1, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(alias, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(alias, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));

            assertEquals(null, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(alias, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceAddNewEntryWithGeneratedAlias() throws Exception {
        String password = "newPassword";
        try {
            CredentialStore credentialStore = getCredentialStore();
            int numAliases = credentialStore.getAliases().size();

            String generatedAlias = addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME,  null, password, false);
            assertEquals(numAliases + 1, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(generatedAlias, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(generatedAlias, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));

            assertEquals(null, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(generatedAlias, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceUpdateExistingEntry() throws Exception {
        String newPassword = "newPassword";
        try {
            CredentialStore credentialStore = getCredentialStore();
            credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
            credentialStore.flush();

            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword()));
            int numAliases = credentialStore.getAliases().size();

            addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, EXISTING_ALIAS, newPassword, true);
            assertEquals(numAliases, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(newPassword.toCharArray(), clearPassword.getPassword()));

            assertEquals(null, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(EXISTING_ALIAS, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceAddNewEntryFromOperation() throws Exception {
        try {
            CredentialStore credentialStore = getCredentialStore();
            credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
            credentialStore.flush();
            addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, EXISTING_ALIAS, null, true, false);

            String alias = "newAlias";
            String password = "newPassword";
            assertFalse(credentialStore.exists(alias, PasswordCredential.class));
            int numAliases = credentialStore.getAliases().size();

            // specify a credential-reference when executing a key-store operation
            generateKeyPairWithCredentialStoreUpdate(KS_NAME, NON_EMPTY_CS_NAME, alias, password, false);
            assertEquals(numAliases + 1, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(alias, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(alias, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceAddNewEntryWithGeneratedAliasFromOperation() throws Exception {
        try {
            CredentialStore credentialStore = getCredentialStore();
            credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
            credentialStore.flush();
            addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, EXISTING_ALIAS, null, true, false);

            String password = "newPassword";
            int numAliases = credentialStore.getAliases().size();

            // specify a credential-reference when executing a key-store operation
            String generatedAlias = generateKeyPairWithCredentialStoreUpdate(KS_NAME, NON_EMPTY_CS_NAME, null, password, false);
            assertEquals(numAliases + 1, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(generatedAlias, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(generatedAlias, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceUpdateExistingEntryFromOperation() throws Exception {
        try {
            addKeyStoreWithCredentialReference(KS_NAME, EMPTY_CS_NAME1, "alias1", "secret", false, false);

            CredentialStore credentialStore = getCredentialStore();
            credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
            credentialStore.flush();
            int numAliases = credentialStore.getAliases().size();
            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword()));

            // specify a credential-reference when executing a key-store operation
            String password = "newPassword";
            generateKeyPairWithCredentialStoreUpdate(KS_NAME, NON_EMPTY_CS_NAME, EXISTING_ALIAS, password, true);

            assertEquals(numAliases, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceUpdateExistingEntryFromWriteAttributeOperation() throws Exception {
        try {
            CredentialStore credentialStore = getCredentialStore();
            credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
            credentialStore.flush();

            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword()));
            int numAliases = credentialStore.getAliases().size();
            addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, EXISTING_ALIAS, null, true);

            String password = "newPassword";
            writeClearTextAttribute("key-store", KS_NAME, password);

            assertEquals(numAliases, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(password.toCharArray(), clearPassword.getPassword()));

            assertEquals(null, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(EXISTING_ALIAS, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceNoUpdate() throws Exception {
        try {
            CredentialStore credentialStore = getCredentialStore();
            credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
            credentialStore.flush();

            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            PasswordCredential passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword()));
            int numAliases = credentialStore.getAliases().size();

            addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, EXISTING_ALIAS, null, true);
            assertEquals(numAliases, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword()));

            assertEquals(null, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(EXISTING_ALIAS, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceClearTextOnly() throws Exception {
        try {
            String password = "secret";
            addKeyStoreWithCredentialReference(KS_NAME, null, null, "secret", false, false);
            assertEquals(password, readAttribute(KS_NAME, CLEAR_TEXT_ATTRIBUTE_NAME));
            assertEquals(null, readAttribute(KS_NAME, ALIAS_ATTRIBUTE_NAME));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceRollbackOfNewlyAddedCredentialDuringAddOperation() throws Exception {
        String alias = "newAlias";
        String password = "newPassword";

        CredentialStore credentialStore = getCredentialStore();
        assertFalse(credentialStore.exists(alias, PasswordCredential.class));
        int numAliases = credentialStore.getAliases().size();

        // Add an invalid key-store, specify a credential-reference attribute that will result in a new entry being added
        // to the credential store. The new entry will be rolled back when the key-store add operation fails
        addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, alias, password, "InvalidType", false, true, true);
        assertEquals(numAliases, credentialStore.getAliases().size());
        assertFalse(credentialStore.exists(alias, PasswordCredential.class));

    }

    @Test
    public void testCredentialReferenceRollbackOfUpdatedExistingCredentialDuringAddOperation() throws Exception {
        String password = "newPassword";

        CredentialStore credentialStore = getCredentialStore();
        credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
        credentialStore.flush();

        assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
        PasswordCredential passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
        ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
        assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword()));
        int numAliases = credentialStore.getAliases().size();

        // Add an invalid key-store, specify a credential-reference attribute that will result in an existing entry being updated
        // in the credential store. The updated entry will be rolled back to its previous value when the key-store add operation fails
        addKeyStoreWithCredentialReference(KS_NAME, NON_EMPTY_CS_NAME, EXISTING_ALIAS, password, "InvalidType", false, true, true);
        assertEquals(numAliases, credentialStore.getAliases().size());
        assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
        passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
        clearPassword = passwordCredential.getPassword(ClearPassword.class);
        assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword())); // password should remain unchanged
    }

    @Test
    public void testFailedAddOperationWithClearTextAttributeOnly() throws Exception {
        String password = "secret";
        addKeyStoreWithCredentialReference(KS_NAME, null, null, password, "InvalidType", false, false, true);
    }

    @Test
    public void testCredentialReferenceRollbackOfNewlyAddedCredentialDuringRuntimeOperation() throws Exception {
        String alias = "newAlias";
        String password = "newPassword";
        addKeyStore();
        CredentialStore credentialStore = getCredentialStore();
        assertFalse(credentialStore.exists(alias, PasswordCredential.class));
        int numAliases = credentialStore.getAliases().size();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KS_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GENERATE_KEY_PAIR);
            operation.get(ElytronDescriptionConstants.ALIAS).set("bsmith");
            operation.get(ElytronDescriptionConstants.ALGORITHM).set("Invalid");
            operation.get(ElytronDescriptionConstants.DISTINGUISHED_NAME).set("CN=bob smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.STORE).set(NON_EMPTY_CS_NAME);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(ALIAS).set(alias);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CLEAR_TEXT).set(password);
            ModelNode response = assertFailed(services.executeOperation(operation)).get(RESULT);
            validateFailedResponse(response);

            assertEquals(numAliases, credentialStore.getAliases().size());
            assertFalse(credentialStore.exists(alias, PasswordCredential.class));
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testCredentialReferenceRollbackOfUpdatedExistingCredentialDuringRuntimeOperation() throws Exception {
        String password = "newPassword";
        addKeyStore();

        CredentialStore credentialStore = getCredentialStore();
        credentialStore.store(EXISTING_ALIAS, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, EXISTING_PASSWORD.toCharArray())));
        credentialStore.flush();

        assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
        PasswordCredential passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
        ClearPassword clearPassword = passwordCredential.getPassword(ClearPassword.class);
        assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword()));
        int numAliases = credentialStore.getAliases().size();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KS_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GENERATE_KEY_PAIR);
            operation.get(ElytronDescriptionConstants.ALIAS).set("bsmith");
            operation.get(ElytronDescriptionConstants.ALGORITHM).set("Invalid");
            operation.get(ElytronDescriptionConstants.DISTINGUISHED_NAME).set("CN=bob smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.STORE).set(NON_EMPTY_CS_NAME);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(ALIAS).set(EXISTING_ALIAS);
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CLEAR_TEXT).set(password);
            ModelNode response = assertFailed(services.executeOperation(operation)).get(RESULT);
            validateFailedResponse(response);

            assertEquals(numAliases, credentialStore.getAliases().size());
            assertTrue(credentialStore.exists(EXISTING_ALIAS, PasswordCredential.class));
            passwordCredential = credentialStore.retrieve(EXISTING_ALIAS, PasswordCredential.class);
            clearPassword = passwordCredential.getPassword(ClearPassword.class);
            assertTrue(Arrays.equals(EXISTING_PASSWORD.toCharArray(), clearPassword.getPassword())); // password should remain unchanged
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    @Test
    public void testFailedRuntimeOperationWithClearTextAttributeOnly() throws Exception {
        addKeyStore();
        try {
            ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", KS_NAME);
            operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GENERATE_KEY_PAIR);
            operation.get(ElytronDescriptionConstants.ALIAS).set("bsmith");
            operation.get(ElytronDescriptionConstants.ALGORITHM).set("Invalid");
            operation.get(ElytronDescriptionConstants.DISTINGUISHED_NAME).set("CN=bob smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us");
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CLEAR_TEXT).set("secret");
            assertFailed(services.executeOperation(operation)).get(RESULT);
        } finally {
            removeKeyStore(KS_NAME);
        }
    }

    private String addKeyStoreWithCredentialReference(String keyStoreName, String store, String alias, String secret, boolean exists) throws Exception {
        return addKeyStoreWithCredentialReference(keyStoreName, store, alias, secret, "JKS", exists, true, false);
    }

    private String addKeyStoreWithCredentialReference(String keyStoreName, String store, String alias, String secret, boolean exists, boolean validateResponse) throws Exception {
        return addKeyStoreWithCredentialReference(keyStoreName, store, alias, secret, "JKS", exists, validateResponse, false);
    }

    private String addKeyStoreWithCredentialReference(String keyStoreName, String store, String alias, String secret, String type, boolean exists, boolean validateResponse) throws Exception {
        return addKeyStoreWithCredentialReference(keyStoreName, store, alias, secret, type, exists, validateResponse, false);
    }

    private String addKeyStoreWithCredentialReference(String keyStoreName, String store, String alias, String secret, String type, boolean exists, boolean validateResponse, boolean assertFailed) throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set(resources + "/test.keystore");
        operation.get(ElytronDescriptionConstants.TYPE).set(type);
        if (store != null) {
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.STORE).set(store);
        }
        boolean autoGeneratedAlias = false;
        if (alias != null) {
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(ALIAS).set(alias);
        } else {
            autoGeneratedAlias = true;
        }
        if (secret != null) {
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CLEAR_TEXT).set(secret);
        }
        if (assertFailed) {
            ModelNode response = assertFailed(services.executeOperation(operation)).get(RESULT);
            if (validateResponse) {
                return validateFailedResponse(response);
            } else {
                return null;
            }
        } else {
            ModelNode response = assertSuccess(services.executeOperation(operation)).get(RESULT);
            if (validateResponse) {
                return validateResponse(response, secret, autoGeneratedAlias, exists);
            } else {
                return null;
            }
        }
    }

    private void writeClearTextAttribute(String resource, String resourceName, String secret) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(resource, resourceName);
        operation.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(CLEAR_TEXT_ATTRIBUTE_NAME);
        operation.get(ClientConstants.VALUE).set(secret);
        ModelNode response = assertSuccess(services.executeOperation(operation)).get(RESULT);
        validateResponse(response, secret, false, true);
    }

    private String generateKeyPairWithCredentialStoreUpdate(String keyStoreName, String store, String alias, String secret, boolean exists) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.GENERATE_KEY_PAIR);
        operation.get(ElytronDescriptionConstants.ALIAS).set("bsmith");
        operation.get(ElytronDescriptionConstants.DISTINGUISHED_NAME).set("CN=bob smith");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.STORE).set(store);
        boolean autoGeneratedAlias = false;
        if (alias != null) {
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.ALIAS).set(alias);
        } else {
            autoGeneratedAlias = true;
        }
        if (secret != null) {
            operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(secret);
        }
        ModelNode response = assertSuccess(services.executeOperation(operation)).get(RESULT);
        return validateResponse(response, secret, autoGeneratedAlias, exists);
    }

    private String validateResponse(ModelNode response, String secret, boolean autoGeneratedAlias, boolean exists) {
        if (secret == null) {
            assertFalse(response.isDefined());
            return null;
        }
        ModelNode credentialStoreUpdate = response.get(CredentialReference.CREDENTIAL_STORE_UPDATE);
        if (! exists) {
            assertTrue(credentialStoreUpdate.get(CredentialReference.STATUS).asString().equals(CredentialReference.NEW_ENTRY_ADDED));
        } else {
            assertTrue(credentialStoreUpdate.get(CredentialReference.STATUS).asString().equals(CredentialReference.EXISTING_ENTRY_UPDATED));
        }
        if (autoGeneratedAlias) {
            String generatedAlias = credentialStoreUpdate.get(CredentialReference.NEW_ALIAS).asString();
            assertTrue(generatedAlias != null && ! generatedAlias.isEmpty());
            return generatedAlias;
        }
        return null;
    }

    private String validateFailedResponse(ModelNode response) {
        ModelNode credentialStoreUpdate = response.get(CredentialReference.CREDENTIAL_STORE_UPDATE);
        assertTrue(credentialStoreUpdate.get(CredentialReference.STATUS).asString().equals(CredentialReference.UPDATE_ROLLED_BACK));
        return null;
    }

    private String readAttribute(String keyStoreName, String attributeName) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set(attributeName);
        return assertSuccess(services.executeOperation(operation)).get(RESULT).asStringOrNull();
    }

    private void removeKeyStore(String keyStoreName) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));
    }

    private CredentialStore getCredentialStore() {
        return getCredentialStore(NON_EMPTY_CS_NAME);
    }

    private CredentialStore getCredentialStore(String store) {
        ServiceName serviceName = Capabilities.CREDENTIAL_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(store);
        return (CredentialStore) services.getContainer().getService(serviceName).getValue();
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

    private void addKeyStore() throws Exception {
        addKeyStore("test.keystore", KS_NAME, "Elytron");
    }

    private void addKeyStore(String keyStoreFile, String keyStoreName, String keyStorePassword) throws Exception {
        Path resources = Paths.get(KeyStoresTestCase.class.getResource(".").toURI());
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OPERATION_HEADERS).get("allow-resource-service-restart").set(Boolean.TRUE);
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("key-store", keyStoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set(resources + "/test.keystore");
        operation.get(ElytronDescriptionConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(keyStorePassword);
        assertSuccess(services.executeOperation(operation));
    }
}
