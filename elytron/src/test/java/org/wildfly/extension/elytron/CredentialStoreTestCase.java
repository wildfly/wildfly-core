/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.wildfly.security.encryption.SecretKeyUtil.importSecretKey;

import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.encryption.CipherUtil;

/**
 * Test case testing operations against a credential store using the management operations.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class CredentialStoreTestCase extends AbstractSubsystemTest {

    private static final Provider PROVIDER = new WildFlyElytronProvider();

    private static final String CLEAR_TEXT = "Lorem ipsum dolor sit amet";
    private static final String CONFIGURATION = "credential-store.xml";

    private static final PathAddress ROOT_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);
    private static final PathAddress CRED_STORE_ADDRESS = ROOT_ADDRESS.append(ElytronDescriptionConstants.CREDENTIAL_STORE, "test");
    private static final PathAddress SECRET_KEY_CRED_STORE_128 = ROOT_ADDRESS.append(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE, "test128");

    private KernelServices services = null;

    public CredentialStoreTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Before
    public void init() throws Exception {
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(CONFIGURATION).build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

    }

    @BeforeClass
    public static void initTests() throws Exception {
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(PROVIDER, 1);
            }
        });
    }

    @AfterClass
    public static void cleanUpTests() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Security.removeProvider(PROVIDER.getName());

                return null;
            }
        });
    }

    @Test
    public void testWriteAttributeCreateCredentialStoreToFalse() {
        ModelNode operation = Util.getWriteAttributeOperation(CRED_STORE_ADDRESS, ElytronDescriptionConstants.CREATE, false);
        assertSuccess(services.executeOperation(operation));
        assertEquals(false, readResource(CRED_STORE_ADDRESS).get(ElytronDescriptionConstants.CREATE).asBoolean());
    }

    @Test
    public void testWriteAttributeKeySizeSecretKeyCredStoreTo192() {
        ModelNode operation = Util.getWriteAttributeOperation(SECRET_KEY_CRED_STORE_128, ElytronDescriptionConstants.KEY_SIZE, 192);
        assertSuccess(services.executeOperation(operation));
        assertEquals(192, readResource(SECRET_KEY_CRED_STORE_128).get(ElytronDescriptionConstants.KEY_SIZE).asInt());
    }

    private ModelNode readResource(PathAddress address) {
        return assertSuccess(services.executeOperation(Util.getReadResourceOperation(address))).get(ClientConstants.RESULT);
    }

    // Test Contents of dynamically initialised credential stores

    @Test
    public void testDefaultContentTest() throws GeneralSecurityException {
        testDefaultContent("credential-store", "test", null, -1);
    }

    @Test
    public void testDefaultContentTestEmpty() throws GeneralSecurityException {
        testDefaultContent("secret-key-credential-store", "testEmpty", null, -1);
    }

    @Test
    public void testDefaultContentTest128() throws GeneralSecurityException {
        testDefaultContent("secret-key-credential-store", "test128", "key", 128);
    }

    @Test
    public void testDefaultContentTest192() throws GeneralSecurityException {
        testDefaultContent("secret-key-credential-store", "test192", "192", 192);
    }

    @Test
    public void testDefaultContentTest256() throws GeneralSecurityException {
        testDefaultContent("secret-key-credential-store", "test256", "key", 256);
    }

    private void testDefaultContent(final String storeType, final String storeName, String expectedAlias, int expectedKeySize) throws GeneralSecurityException {
        // First Check The Default Aliases
        ModelNode readAliases = new ModelNode();
        readAliases.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        readAliases.get(ClientConstants.OP).set("read-aliases");

        ModelNode aliases = assertSuccess(services.executeOperation(readAliases)).get("result");
        System.out.println(aliases.toString());
        List<ModelNode> aliasValues = aliases.asList();
        assertEquals("Expected alias count", expectedAlias != null ? 1 : 0, aliasValues.size());
        if (expectedAlias != null) {
            assertEquals("Expected alias name", expectedAlias, aliasValues.get(0).asString());

            // Export the generated SecretKey
            ModelNode export = new ModelNode();
            export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
            export.get(ClientConstants.OP).set("export-secret-key");
            export.get(ElytronDescriptionConstants.ALIAS).set(expectedAlias);
            ModelNode exportResult = assertSuccess(services.executeOperation(export));

            final String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();
            SecretKey secretKey = importSecretKey(key);
            // Key Sizes are in bits.
            assertEquals("Expected Key Size", expectedKeySize, secretKey.getEncoded().length * 8);
        }
    }

    // Add SecretKey Flow for secret-key-credential-store but take into account the resource also has a default key size.

    @Test
    public void testSecretKeyFlowDefault() throws GeneralSecurityException {
        testSecretKeyGenerateExportImport("credential-store", "test", 256, true, true);
        testSecretKeyGenerateExportImport("secret-key-credential-store", "test128", 128, true, false);
        testSecretKeyGenerateExportImport("secret-key-credential-store", "test192", 192, true, false);
        testSecretKeyGenerateExportImport("secret-key-credential-store", "test256", 256, true, false);
    }

    @Test
    public void testSecretKeyFlow128() throws GeneralSecurityException {
        testSecretKeyGenerateExportImport("credential-store", "test", 128, false, true);
        // Use a store which does not default to 128 bits.
        testSecretKeyGenerateExportImport("secret-key-credential-store", "test256", 128, false, false);
    }

    @Test
    public void testSecretKeyFlow192() throws GeneralSecurityException {
        testSecretKeyGenerateExportImport("credential-store", "test", 192, false, true);
        // Use a store which does not default to 192 bits.
        testSecretKeyGenerateExportImport("secret-key-credential-store", "test256", 192, false, false);
    }

    @Test
    public void testSecretKeyFlow256() throws GeneralSecurityException {
        testSecretKeyGenerateExportImport("credential-store", "test", 256, false, true);
        // Use a store which does not default to 256 bits.
        testSecretKeyGenerateExportImport("secret-key-credential-store", "test128", 256, false, false);
    }

    private void testSecretKeyGenerateExportImport(final String storeType, final String storeName, final int keySize,
            boolean omitKeySize, boolean entryTypeRequired) throws GeneralSecurityException {
        final String alias = "test";

        // Generate and store a new SecretKey using the specified keySize.
        ModelNode generate = new ModelNode();
        generate.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        generate.get(ClientConstants.OP).set("generate-secret-key");
        generate.get(ElytronDescriptionConstants.ALIAS).set(alias);
        if (omitKeySize == false) {
            if (keySize > 0) {
                generate.get(ElytronDescriptionConstants.KEY_SIZE).set(keySize);
            }
        }
        assertSuccess(services.executeOperation(generate));

        // Export the generated SecretKey
        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        export.get(ClientConstants.OP).set("export-secret-key");
        export.get(ElytronDescriptionConstants.ALIAS).set(alias);
        ModelNode exportResult = assertSuccess(services.executeOperation(export));

        final String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();

        SecretKey secretKey = importSecretKey(key);
        // Key Sizes are in bits.
        assertEquals("Expected Key Size", keySize, secretKey.getEncoded().length * 8);


        String importAlias = alias + "Import";
        // Import the previously exported SecretKey
        ModelNode importKey = new ModelNode();
        importKey.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKey.get(ClientConstants.OP).set("import-secret-key");
        importKey.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        importKey.get(ElytronDescriptionConstants.KEY).set(key);
        assertSuccess(services.executeOperation(importKey));

        // Re-export so keys can be compared
        ModelNode export2 = new ModelNode();
        export2.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        export2.get(ClientConstants.OP).set("export-secret-key");
        export2.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        ModelNode exportResult2 = assertSuccess(services.executeOperation(export2));

        final String key2 = exportResult2.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();
        assertNotNull("Exported SecretKey", key);
        assertEquals("Matching keys", key, key2);

        // Remove aliases
        ModelNode remove = new ModelNode();
        remove.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        remove.get(ClientConstants.OP).set("remove-alias");
        if (entryTypeRequired) {
            remove.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        }
        remove.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(remove));

        ModelNode remove2 = new ModelNode();
        remove2.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        remove2.get(ClientConstants.OP).set("remove-alias");
        if (entryTypeRequired) {
            remove2.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        }
        remove2.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        assertSuccess(services.executeOperation(remove2));
    }

    @Test
    public void testCreateExpression() throws Exception {
        // First obtain the generated SecretKey as we will want this to test any expressions can be decrypted.
        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("secret-key-credential-store", "test256");
        export.get(ClientConstants.OP).set("export-secret-key");
        export.get(ElytronDescriptionConstants.ALIAS).set("key");
        ModelNode exportResult = assertSuccess(services.executeOperation(export));

        final String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();

        SecretKey secretKey = importSecretKey(key);

        ModelNode createExpression = new ModelNode();
        createExpression.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("expression", "encryption");
        createExpression.get(ClientConstants.OP).set("create-expression");
        createExpression.get(ElytronDescriptionConstants.RESOLVER).set("A");
        createExpression.get(ElytronDescriptionConstants.CLEAR_TEXT).set(CLEAR_TEXT);
        ModelNode createExpressionResult = assertSuccess(services.executeOperation(createExpression));

        String expression = createExpressionResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.EXPRESSION).asString();
        assertEquals("Expected Expression Prefix", "${CIPHER::A:", expression.substring(0, 12));
        String cipherTextToken = expression.substring(12, expression.length() - 1);
        assertEquals("Decrypted value", CLEAR_TEXT, CipherUtil.decrypt(cipherTextToken, secretKey));

        createExpression = new ModelNode();
        createExpression.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("expression", "encryption");
        createExpression.get(ClientConstants.OP).set("create-expression");
        createExpression.get(ElytronDescriptionConstants.CLEAR_TEXT).set(CLEAR_TEXT);
        createExpressionResult = assertSuccess(services.executeOperation(createExpression));

        expression = createExpressionResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.EXPRESSION).asString();
        assertEquals("Expected Expression Prefix", "${CIPHER::", expression.substring(0, 10));
        cipherTextToken = expression.substring(10, expression.length() - 1);
        assertEquals("Decrypted value", CLEAR_TEXT, CipherUtil.decrypt(cipherTextToken, secretKey));
    }

    @Test
    public void testErrorsOfDoubleOperationsInFlow() throws GeneralSecurityException {
        testErrorsOfDoubleOperationsInFlow("credential-store", "test", true);
        testErrorsOfDoubleOperationsInFlow("secret-key-credential-store", "test128", false);
    }

    private void testErrorsOfDoubleOperationsInFlow(final String storeType, final String storeName,
            boolean entryTypeRequired) throws GeneralSecurityException {

        final String alias = "test";

        // Generate and store a new SecretKey.
        ModelNode generate = new ModelNode();
        generate.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        generate.get(ClientConstants.OP).set("generate-secret-key");
        generate.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(generate));

        ModelNode doubleGenerateResult = assertFailed(services.executeOperation(generate));
        assertThat(doubleGenerateResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00913:"));

        // Export the generated SecretKey
        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        export.get(ClientConstants.OP).set("export-secret-key");
        export.get(ElytronDescriptionConstants.ALIAS).set(alias);
        ModelNode exportResult = assertSuccess(services.executeOperation(export));

        final String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();

        String importAlias = alias + "Import";
        // Import the previously exported SecretKey
        ModelNode importKey = new ModelNode();
        importKey.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKey.get(ClientConstants.OP).set("import-secret-key");
        importKey.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        importKey.get(ElytronDescriptionConstants.KEY).set(key);
        assertSuccess(services.executeOperation(importKey));

        ModelNode doubleImportResult = assertFailed(services.executeOperation(importKey));
        assertThat(doubleImportResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00913:"));

        // Remove aliases
        ModelNode remove = new ModelNode();
        remove.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        remove.get(ClientConstants.OP).set("remove-alias");
        if (entryTypeRequired) {
            remove.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        }
        remove.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(remove));

        ModelNode remove2 = new ModelNode();
        remove2.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        remove2.get(ClientConstants.OP).set("remove-alias");
        if (entryTypeRequired) {
            remove2.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        }
        remove2.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        assertSuccess(services.executeOperation(remove2));

        ModelNode doubleRemoveResult = assertFailed(services.executeOperation(remove2));
        assertThat(doubleRemoveResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00920:"));
    }

    @Test
    public void testExportNonExistingAlias() throws GeneralSecurityException {
        testExportNonExistingAlias("credential-store", "test");
        testExportNonExistingAlias("secret-key-credential-store", "test192");
    }

    private void testExportNonExistingAlias(final String storeType, final String storeName) throws GeneralSecurityException {
        final String alias = "test";

        // Export the generated SecretKey
        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        export.get(ClientConstants.OP).set("export-secret-key");
        export.get(ElytronDescriptionConstants.ALIAS).set(alias);
        ModelNode exportResult = assertFailed(services.executeOperation(export));
        assertThat(exportResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00920:"));
    }

    @Test
    public void testImportInvalidSecretKey() throws GeneralSecurityException {
        testImportInvalidSecretKey("credential-store", "test");
        testImportInvalidSecretKey("secret-key-credential-store", "test256");
    }

    private void testImportInvalidSecretKey(final String storeType, final String storeName) throws GeneralSecurityException {
        final String alias = "test";

        // Generate and store a new SecretKey.
        ModelNode generate = new ModelNode();
        generate.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        generate.get(ClientConstants.OP).set("generate-secret-key");
        generate.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(generate));

        // Export the generated SecretKey
        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        export.get(ClientConstants.OP).set("export-secret-key");
        export.get(ElytronDescriptionConstants.ALIAS).set(alias);
        ModelNode exportResult = assertSuccess(services.executeOperation(export));

        final String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();
        final String truncatedKey = key.substring(0, key.length() - 2);

        String importAlias = alias + "Import";
        // Import the previously exported and truncated SecretKey
        ModelNode importKey = new ModelNode();
        importKey.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKey.get(ClientConstants.OP).set("import-secret-key");
        importKey.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        importKey.get(ElytronDescriptionConstants.KEY).set(truncatedKey);
        ModelNode importResult = assertFailed(services.executeOperation(importKey));
        assertThat(importResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("ELY19004:"));

        // Remove aliases
        ModelNode remove = new ModelNode();
        remove.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        remove.get(ClientConstants.OP).set("remove-alias");
        remove.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        remove.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(remove));
    }

    @Test
    public void testRemoveAliasOfEntryType() throws GeneralSecurityException {
        final String alias = "test";

        testExpectedAliases("credential-store", "test");

        // Generate and store a new SecretKey.
        ModelNode generateSecretKey = new ModelNode();
        generateSecretKey.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        generateSecretKey.get(ClientConstants.OP).set("generate-secret-key");
        generateSecretKey.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(generateSecretKey));

        testExpectedAliases("credential-store", "test", alias);

        // Store a new Password.
        ModelNode addPassword = new ModelNode();
        addPassword.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        addPassword.get(ClientConstants.OP).set("add-alias");
        addPassword.get(ElytronDescriptionConstants.ALIAS).set(alias);
        addPassword.get(ElytronDescriptionConstants.SECRET_VALUE).set("password");
        assertSuccess(services.executeOperation(addPassword));

        testExpectedAliases("credential-store", "test", alias);

        // Remove SecretKeyCredential alias
        ModelNode removeSecretKey = new ModelNode();
        removeSecretKey.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        removeSecretKey.get(ClientConstants.OP).set("remove-alias");
        removeSecretKey.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        removeSecretKey.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(removeSecretKey));

        testExpectedAliases("credential-store", "test", alias);

        // Generate and store a new SecretKey again.
        ModelNode generateSecretKeyAgain = new ModelNode();
        generateSecretKeyAgain.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        generateSecretKeyAgain.get(ClientConstants.OP).set("generate-secret-key");
        generateSecretKeyAgain.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(generateSecretKeyAgain));

        testExpectedAliases("credential-store", "test", alias);

        // Remove PasswordCredential alias (default type)
        ModelNode removePassword = new ModelNode();
        removePassword.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        removePassword.get(ClientConstants.OP).set("remove-alias");
        removePassword.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(removePassword));

        testExpectedAliases("credential-store", "test", alias);

        // Remove SecretKeyCredential alias again
        ModelNode removeSecretKeyAgain = new ModelNode();
        removeSecretKeyAgain.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        removeSecretKeyAgain.get(ClientConstants.OP).set("remove-alias");
        removeSecretKeyAgain.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        removeSecretKeyAgain.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(removeSecretKeyAgain));

        testExpectedAliases("credential-store", "test");
    }

    private void testExpectedAliases(String resourceType, String resourceName,
            String... expectedAliases) throws GeneralSecurityException {

        ModelNode readAliases = new ModelNode();
        readAliases.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(resourceType, resourceName);
        readAliases.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);

        ModelNode readAliasesResult = assertSuccess(services.executeOperation(readAliases));

        ModelNode result = readAliasesResult.get(ClientConstants.RESULT);
        if (expectedAliases.length > 0) {
            Set<String> expectedSet = new HashSet<>(Arrays.asList(expectedAliases));

            List<ModelNode> aliasList = result.asList();
            for (ModelNode aliasNode : aliasList) {
                String alias = aliasNode.asString();
                if (!expectedSet.remove(alias)) {
                    fail("Alias '" + alias + "' not expected.");
                }
            }

            assertEquals("All expected aliases found", 0, expectedSet.size());
        } else {
            assertTrue("No aliases expected", result.asList().isEmpty());
        }
    }

    private static ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private static ModelNode assertFailed(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

}
