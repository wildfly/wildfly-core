/*
 * Copyright 2019 Red Hat, Inc.
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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.wildfly.extension.elytron.KeyPairUtil.DSA_ALGORITHM;
import static org.wildfly.extension.elytron.KeyPairUtil.EC_ALGORITHM;
import static org.wildfly.extension.elytron.KeyPairUtil.RSA_ALGORITHM;
import static org.wildfly.extension.elytron.KeyPairUtil.readKeyFromFile;
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

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.credential.KeyPairCredential;
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

    @Test
    public void testGenerateKeyPairDefault() {
        String aliasName = "testKeyPairAlias";
        String storeType = "credential-store";
        String storeName = "test";

        generateKeyPair(storeType, storeName, aliasName);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testGenerateKeyPairRSA() {
        String aliasName = "testRSAKeyPairAlias";
        String storeType = "credential-store";
        String storeName = "test";
        String algorithm = RSA_ALGORITHM;
        String size = "3072";

        generateKeyPair(storeType, storeName, aliasName, algorithm, size);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testGenerateKeyPairDSA() {
        String aliasName = "testDSAKeyPairAlias";
        String storeType = "credential-store";
        String storeName = "test";
        String algorithm = DSA_ALGORITHM;
        String size = "2048";

        generateKeyPair(storeType, storeName, aliasName, algorithm, size);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testGenerateKeyPairECDSA() {
        String aliasName = "testECDSAKeyPairAlias";
        String storeType = "credential-store";
        String storeName = "test";
        String algorithm = EC_ALGORITHM;
        String size = "521";

        generateKeyPair(storeType, storeName, aliasName, algorithm, size);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testExportPublicKey() {
        String aliasName = "testExportKeyPairAlias";
        String storeType = "credential-store";
        String storeName = "test";

        generateKeyPair(storeType, storeName, aliasName);
        String publicKey = exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);

        Assert.assertTrue(publicKey.contains("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ"));
    }

    @Test
    public void testExportPublicKeyFailNonExistingAlias() {
        String aliasName = "testExportKeyPairAlias";
        String storeType = "credential-store";
        String storeName = "test";

        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        export.get(ClientConstants.OP).set("export-key-pair-public-key");
        export.get(ElytronDescriptionConstants.ALIAS).set(aliasName);

        ModelNode exportResult = assertFailed(services.executeOperation(export));
        assertThat(exportResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00920:"));
    }

    @Test
    public void testImportKeyPairFailDuplicatePrivateKeyResource() {
        String aliasName = "testImportKeyPairFailDuplicatePrivateKeyResource";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "secret";
        String privateKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa";
        String privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
                "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABCdRswttV\n" +
                "UNQ6nKb6ojozTGAAAAEAAAAAEAAABoAAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlz\n" +
                "dHAyNTYAAABBBAKxnsRT7n6qJLKoD3mFfAvcH5ZFUyTzJVW8t60pNgNaXO4q5S4qL9yCCZ\n" +
                "cKyg6QtVgRuVxkUSseuR3fiubyTnkAAADQq3vrkvuSfm4n345STr/i/29FZEFUd0qD++B2\n" +
                "ZoWGPKU/xzvxH7S2GxREb5oXcIYO889jY6mdZT8LZm6ZZig3rqoEAqdPyllHmEadb7hY+y\n" +
                "jwcQ4Wr1ekGgVwNHCNu2in3cYXxbrYGMHc33WmdNrbGRDUzK+EEUM2cwUiM7Pkrw5s88Ff\n" +
                "IWI0V+567Ob9LxxIUO/QvSbKMJGbMM4jZ1V9V2Ti/GziGJ107CBudZr/7wNwxIK86BBAEg\n" +
                "hfnrhYBIaOLrtP8R+96i8iu4iZAvcIbQ==\n" +
                "-----END OPENSSH PRIVATE KEY-----";

        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(aliasName);
        importKeyPair.get(ElytronDescriptionConstants.OPENSSH_PRIVATE_KEY_LOCATION).set(privateKeyLocation);
        importKeyPair.get(ElytronDescriptionConstants.OPENSSH_PRIVATE_KEY).set(privateKey);
        importKeyPair.get(ElytronDescriptionConstants.PASSPHRASE).set(passphrase);

        assertFailed(services.executeOperation(importKeyPair));
    }

    @Test
    public void testImportKeyPairFailDuplicatePublicKeyResource() {
        String aliasName = "testImportKeyPairFailDuplicatePublicKeyResource";
        String storeType = "credential-store";
        String storeName = "test";
        String privateKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa_pkcs";
        String publicKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa_pkcs.pub";
        String publicKey = "-----BEGIN PUBLIC KEY-----\n" +
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETwqaS+N07bvXhz1J09s9HlAJhImZ\n" +
                "VWCF/apVdSU3nZjPAQMK+hGATb/UICDGatGvMprD49ezxcNzHUufCn7IvA==\n" +
                "-----END PUBLIC KEY-----";

        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(aliasName);
        importKeyPair.get(ElytronDescriptionConstants.PRIVATE_KEY_LOCATION).set(privateKeyLocation);
        importKeyPair.get(ElytronDescriptionConstants.PUBLIC_KEY).set(publicKey);
        importKeyPair.get(ElytronDescriptionConstants.PUBLIC_KEY_LOCATION).set(publicKeyLocation);

        assertFailed(services.executeOperation(importKeyPair));
    }

    @Test
    public void testImportKeyPairNoPrivateKey() {
        String aliasName = "testImportKeyPairNoPrivateKey";
        String storeType = "credential-store";
        String storeName = "test";

        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(aliasName);

        ModelNode importResult = assertFailed(services.executeOperation(importKeyPair));
        assertThat(importResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00933:"));
    }

    @Test
    public void testImportOpenSSHKeyPairFromFile() {
        String aliasName = "testOpenSSHKeyPairFromFileAlias";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "secret";
        String privateKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa";

        importOpensshKeyPairKeyFromFile(storeType, storeName, aliasName, privateKeyLocation, passphrase);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testImportOpenSSHKeyPairFromFileGeneratedWithoutPassphrase() {
        String aliasName = "testImportOpenSSHKeyPairFromFileGeneratedWithoutPassphrase";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "";
        String privateKeyLocation = "./target/test-classes/ssh-keys/id_rsa";
        String publicKeyLocation = "./target/test-classes/ssh-keys/id_rsa.pub";

        importOpensshKeyPairKeyFromFile(storeType, storeName, aliasName, privateKeyLocation, passphrase);
        String exportedPublicString = exportKeyPairPublicKey(storeType, storeName, aliasName);
        String publicKey = readKeyFromFile(publicKeyLocation);
        assertEquals(publicKey, exportedPublicString);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testImportOpenSSHKeyPairFromFileWithoutPassphrase() {
        String aliasName = "testImportOpenSSHKeyPairFromFileGeneratedWithoutPassphrase";
        String storeType = "credential-store";
        String storeName = "test";
        String privateKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa";

        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(aliasName);
        importKeyPair.get(ElytronDescriptionConstants.OPENSSH_PRIVATE_KEY_LOCATION).set(privateKeyLocation);

        assertFailed(services.executeOperation(importKeyPair));
    }

    @Test
    public void testImportPKCSKeyPairFromFileWithoutPublicKey() {
        String aliasName = "testImportPKCSKeyPairFromFileWithoutPublicKey";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "secret";
        String privateKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa_pkcs";

        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(aliasName);
        importKeyPair.get(ElytronDescriptionConstants.PRIVATE_KEY_LOCATION).set(privateKeyLocation);
        importKeyPair.get(ElytronDescriptionConstants.PASSPHRASE).set(passphrase);

        ModelNode importResult = assertFailed(services.executeOperation(importKeyPair));
        assertThat(importResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00931:"));
    }

    @Test
    public void testImportPKCSKeyPairFromFile() {
        String aliasName = "testPKCSKeyPairFromFileAlias";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "secret";
        String privateKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa_pkcs";
        String publicKeyLocation = "./target/test-classes/ssh-keys/id_ecdsa_pkcs.pub";

        importKeyPairKeyFromFile(storeType, storeName, aliasName, privateKeyLocation, publicKeyLocation, passphrase);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testImportOpenSSHKeyPairFromString() {
        String aliasName = "testOpenSSHKeyPairFromStringAlias";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "secret";
        String key = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
                "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABCdRswttV\n" +
                "UNQ6nKb6ojozTGAAAAEAAAAAEAAABoAAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlz\n" +
                "dHAyNTYAAABBBAKxnsRT7n6qJLKoD3mFfAvcH5ZFUyTzJVW8t60pNgNaXO4q5S4qL9yCCZ\n" +
                "cKyg6QtVgRuVxkUSseuR3fiubyTnkAAADQq3vrkvuSfm4n345STr/i/29FZEFUd0qD++B2\n" +
                "ZoWGPKU/xzvxH7S2GxREb5oXcIYO889jY6mdZT8LZm6ZZig3rqoEAqdPyllHmEadb7hY+y\n" +
                "jwcQ4Wr1ekGgVwNHCNu2in3cYXxbrYGMHc33WmdNrbGRDUzK+EEUM2cwUiM7Pkrw5s88Ff\n" +
                "IWI0V+567Ob9LxxIUO/QvSbKMJGbMM4jZ1V9V2Ti/GziGJ107CBudZr/7wNwxIK86BBAEg\n" +
                "hfnrhYBIaOLrtP8R+96i8iu4iZAvcIbQ==\n" +
                "-----END OPENSSH PRIVATE KEY-----";

        importOpensshKeyPairKey(storeType, storeName, aliasName, key, passphrase);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    @Test
    public void testImportPKCSKeyPairFromStringWithoutPublicKey() {
        String aliasName = "testImportPKCSKeyPairFromStringWithoutPublicKey";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "secret";
        String privateKey = "-----BEGIN PRIVATE KEY-----\n" +
                "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgj+ToYNaHz/pISg/Z\n" +
                "I9BjdhcTre/SJpIxASY19XtOV1ehRANCAASngcxUTBf2atGC5lQWCupsQGRNwwnK\n" +
                "6Ww9Xt37SmaHv0bX5n1KnsAal0ykJVKZsD0Z09jVF95jL6udwaKpWQwb\n" +
                "-----END PRIVATE KEY-----";

        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(aliasName);
        importKeyPair.get(ElytronDescriptionConstants.PRIVATE_KEY).set(privateKey);
        importKeyPair.get(ElytronDescriptionConstants.PASSPHRASE).set(passphrase);

        ModelNode importResult = assertFailed(services.executeOperation(importKeyPair));
        assertThat(importResult.get(ClientConstants.FAILURE_DESCRIPTION).asString(), containsString("WFLYELY00931:"));
    }

    @Test
    public void testImportPKCSKeyPairFromString() {
        String aliasName = "testPKCSKeyPairFromStringAlias";
        String storeType = "credential-store";
        String storeName = "test";
        String passphrase = "secret";
        String privateKey = "-----BEGIN PRIVATE KEY-----\n" +
                "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgj+ToYNaHz/pISg/Z\n" +
                "I9BjdhcTre/SJpIxASY19XtOV1ehRANCAASngcxUTBf2atGC5lQWCupsQGRNwwnK\n" +
                "6Ww9Xt37SmaHv0bX5n1KnsAal0ykJVKZsD0Z09jVF95jL6udwaKpWQwb\n" +
                "-----END PRIVATE KEY-----";
        String publicKey = "-----BEGIN PUBLIC KEY-----\n" +
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEp4HMVEwX9mrRguZUFgrqbEBkTcMJ\n" +
                "yulsPV7d+0pmh79G1+Z9Sp7AGpdMpCVSmbA9GdPY1RfeYy+rncGiqVkMGw==\n" +
                "-----END PUBLIC KEY-----\n";

        importKeyPairKey(storeType, storeName, aliasName, privateKey, publicKey, passphrase);
        exportKeyPairPublicKey(storeType, storeName, aliasName);
        removeKeyPair(storeType, storeName, aliasName);
    }

    private void generateKeyPair(final String storeType, final String storeName, String alias) {
        generateKeyPair(storeType, storeName, alias, "", true, "", true);
    }

    private void generateKeyPair(final String storeType, final String storeName, String alias, String algorithm, String size) {
        generateKeyPair(storeType, storeName, alias, algorithm, false, size, false);
    }

    private void generateKeyPair(final String storeType, final String storeName, String alias, String algorithm, boolean omitAlgorithm, String size, boolean omitSize) {
        ModelNode generate = new ModelNode();
        generate.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        generate.get(ClientConstants.OP).set("generate-key-pair");
        generate.get(ElytronDescriptionConstants.ALIAS).set(alias);
        if (omitAlgorithm == false) {
            generate.get(ElytronDescriptionConstants.ALGORITHM).set(algorithm);
        }
        if (omitSize == false) {
            generate.get(ElytronDescriptionConstants.SIZE).set(size);
        }

        assertSuccess(services.executeOperation(generate));
    }

    private String exportKeyPairPublicKey(final String storeType, final String storeName, String alias) {
        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        export.get(ClientConstants.OP).set("export-key-pair-public-key");
        export.get(ElytronDescriptionConstants.ALIAS).set(alias);

        ModelNode exportResult = assertSuccess(services.executeOperation(export));
        String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.PUBLIC_KEY).asString();

        return key;
    }

    private void importKeyPairKey(final String storeType, final String storeName, String alias, String privateKey, String publicKey, String passphrase) {
        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(alias);
        if (!privateKey.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PRIVATE_KEY).set(privateKey);
        }
        if (!publicKey.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PUBLIC_KEY).set(publicKey);
        }
        if (!passphrase.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PASSPHRASE).set(passphrase);
        }

        assertSuccess(services.executeOperation(importKeyPair));
    }

    private void importOpensshKeyPairKey(final String storeType, final String storeName, String alias, String privateKey, String passphrase) {
        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(alias);
        if (!privateKey.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.OPENSSH_PRIVATE_KEY).set(privateKey);
        }
        if (!passphrase.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PASSPHRASE).set(passphrase);
        }

        assertSuccess(services.executeOperation(importKeyPair));
    }

    private void importKeyPairKeyFromFile(final String storeType, final String storeName, String alias, String privateKeyLocation, String publicKeyLocation, String passphrase) {
        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(alias);
        if (!privateKeyLocation.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PRIVATE_KEY_LOCATION).set(privateKeyLocation);
        }
        if (!publicKeyLocation.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PUBLIC_KEY_LOCATION).set(publicKeyLocation);
        }
        if (!passphrase.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PASSPHRASE).set(passphrase);
        }

        assertSuccess(services.executeOperation(importKeyPair));
    }

    private void importOpensshKeyPairKeyFromFile(final String storeType, final String storeName, String alias, String privateKeyLocation, String passphrase) {
        ModelNode importKeyPair = new ModelNode();
        importKeyPair.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        importKeyPair.get(ClientConstants.OP).set("import-key-pair");
        importKeyPair.get(ElytronDescriptionConstants.ALIAS).set(alias);
        if (!privateKeyLocation.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.OPENSSH_PRIVATE_KEY_LOCATION).set(privateKeyLocation);
        }
        if (!passphrase.isEmpty()) {
            importKeyPair.get(ElytronDescriptionConstants.PASSPHRASE).set(passphrase);
        }

        assertSuccess(services.executeOperation(importKeyPair));
    }

    private void removeKeyPair(final String storeType, final String storeName, String alias) {
        ModelNode remove = new ModelNode();
        remove.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(storeType, storeName);
        remove.get(ClientConstants.OP).set("remove-alias");
        remove.get(ElytronDescriptionConstants.ENTRY_TYPE).set(KeyPairCredential.class.getSimpleName());
        remove.get(ElytronDescriptionConstants.ALIAS).set(alias);

        assertSuccess(services.executeOperation(remove));
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
