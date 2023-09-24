/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.wildfly.security.encryption.SecretKeyUtil.importSecretKey;

import java.io.File;
import java.io.IOException;
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

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.credential.store.WildFlyElytronCredentialStoreProvider;
import org.wildfly.security.encryption.CipherUtil;
import org.wildfly.security.encryption.SecretKeyUtil;

/**
 * A test case to test expression resolution using the expression=encryption resource.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ExpressionResolutionTestCase extends AbstractElytronSubsystemBaseTest {

    private static final Provider PROVIDER = new WildFlyElytronCredentialStoreProvider();

    private static final String CSONE_SECURE_KEY = "RUxZAUsXHVcDh99zAdxGEzTBK1h2qjW+sZg2+37w7ijhDEiJEw==";
    private static final String CSTHREE_TEST_KEY = "RUxZAUv5+IwidHJCNNG/cEe2GmWvieV3Ecg7M4xZaJiSULKlBQ==";

    private static final String CLEAR_TEXT = "Lorem ipsum dolor sit amet";


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

    public ExpressionResolutionTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("expression-encryption.xml");
    }

    @Test
    public void testPreConfiguredHierarchy() throws Exception {
        CredentialStoreUtility csOne = null;
        CredentialStoreUtility csTwo = null;
        CredentialStoreUtility csThree = null;
        try {
            csOne = createCredentialStoreOne();
            csTwo = createCredentialStoreTwo();
            csThree = createCredentialStoreThree();

            KernelServices services = super.createKernelServicesBuilder(new TestEnvironment())
                    .setSubsystemXmlResource("expression-encryption.xml").build();
            if (!services.isSuccessfulBoot()) {
                Assert.fail(services.getBootError().toString());
            }

            testExpectedAliases(services, "secret-key-credential-store", "CredentialStoreThree", "testkey", "key");
            testExpectedAliases(services, "credential-store", "CredentialStoreTwo", "csone");
            testExpectedAliases(services, "credential-store", "CredentialStoreOne", "ksone", "securekey");
            // No aliases in the key stores but we want them to be up to be queried.
            testExpectedAliases(services, "key-store", "KeyStoreOne");
            testExpectedAliases(services, "key-store", "KeyStoreTwo");
        } finally {
            cleanUp(csOne);
            cleanUp(csTwo);
            cleanUp(csThree);
        }
    }

    protected AdditionalInitialization createAdditionalInitialization() {
        // Our use of the expression=encryption resource requires kernel capability setup that TestEnvironment provides
        return new TestEnvironment(RunningMode.ADMIN_ONLY);
    }

    private static void cleanUp(CredentialStoreUtility csUtil) {
        if (csUtil != null) {
            csUtil.cleanUp();
        }
    }

    private static CredentialStoreUtility createCredentialStoreOne() throws GeneralSecurityException {
        CredentialStoreUtility csOne = new CredentialStoreUtility("target/credential-store-one.cs", "CSOnePassword");
        csOne.addEntry("ksone", "KSOnePassword");
        csOne.addEntry("securekey", SecretKeyUtil.importSecretKey(CSONE_SECURE_KEY));
        return csOne;
    }

    private static CredentialStoreUtility createCredentialStoreTwo() throws GeneralSecurityException {
        CredentialStoreUtility csTwo = new CredentialStoreUtility("target/credential-store-two.cs", "CSTwoPassword");
        csTwo.addEntry("csone", "CSOnePassword");
        return csTwo;
    }

    private static CredentialStoreUtility createCredentialStoreThree() throws GeneralSecurityException {
        CredentialStoreUtility csTwo = new CredentialStoreUtility("target/credential-store-three.cs", true);
        csTwo.addEntry("testkey", SecretKeyUtil.importSecretKey(CSTHREE_TEST_KEY));
        return csTwo;
    }

    private void testExpectedAliases(KernelServices services, String resourceType, String resourceName,
            String... expectedAliases) throws Exception {

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
            assertTrue("Undefined result expected.", !result.isDefined());
        }
    }

    @Test
    public void testExpressionEncryptionOperations() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXml(emptySubsystemXml()).build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        String testStorePath = "target/secret-key-store.cs";
        File testStoreFile = new File(testStorePath);
        if (testStoreFile.exists()) {
            testStoreFile.delete();
        }

        // Start with an empty subsystem and define a secret key credential store with two keys
        ModelNode add = new ModelNode();
        add.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE, "my-test-store");
        add.get(ClientConstants.OP).set(ClientConstants.ADD);
        add.get(ElytronDescriptionConstants.PATH).set(testStorePath);
        add.get(ElytronDescriptionConstants.POPULATE).set(false);

        assertSuccess(services.executeOperation(add));

          // Generate one and export it.
        ModelNode generate = new ModelNode();
        generate.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE, "my-test-store");
        generate.get(ClientConstants.OP).set(ElytronDescriptionConstants.GENERATE_SECRET_KEY);
        generate.get(ElytronDescriptionConstants.ALIAS).set("TestKeyOne");

        assertSuccess(services.executeOperation(generate));

        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE, "my-test-store");
        export.get(ClientConstants.OP).set(ElytronDescriptionConstants.EXPORT_SECRET_KEY);
        export.get(ElytronDescriptionConstants.ALIAS).set("TestKeyOne");

        ModelNode exportResult = assertSuccess(services.executeOperation(export));

        final String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();

        SecretKey testKeyOne = importSecretKey(key);

          // Generate one in test and import it.
        SecretKey testKeyTwo = SecretKeyUtil.generateSecretKey(192);

        ModelNode importOP = new ModelNode();
        importOP.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE, "my-test-store");
        importOP.get(ClientConstants.OP).set(ElytronDescriptionConstants.IMPORT_SECRET_KEY);
        importOP.get(ElytronDescriptionConstants.ALIAS).set("TestKeyTwo");
        importOP.get(ElytronDescriptionConstants.KEY).set(SecretKeyUtil.exportSecretKey(testKeyTwo));

        assertSuccess(services.executeOperation(importOP));

          // Test read-aliases
        testExpectedAliases(services, ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE, "my-test-store", "testkeyone", "testkeytwo");

        // Define an expression=encryption resource with three resolvers (one for each key and one for a missing key)
        add = new ModelNode();
        add.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION);
        add.get(ClientConstants.OP).set(ClientConstants.ADD);

        ModelNode resolvers = new ModelNode();
        resolvers.add(resolver("ResolverOne", "my-test-store", "testkeyone"));
        resolvers.add(resolver("ResolverTwo", "my-test-store", "testkeytwo"));
        resolvers.add(resolver("ResolverThree", "my-test-store", "testkeythree"));
        add.get(ElytronDescriptionConstants.RESOLVERS).set(resolvers);

        add.get(ElytronDescriptionConstants.DEFAULT_RESOLVER).set("ResolverTwo");

        assertSuccess(services.executeOperation(add));

        // Test generation fails for the resolver with the invalid key.
        // Do this first to check it doesn't break anything.
        testCreateExpression(services, null, "ResolverThree");
        // Test generation using both resolvers by specifying name.
        testCreateExpression(services, testKeyOne, "ResolverOne");
        testCreateExpression(services, testKeyTwo, "ResolverTwo");
        // Test generation using the default resolver.
        testCreateExpression(services, testKeyTwo, null);

        // It would also be a bug if anything that handled this file during the test run had not closed it.
        testStoreFile.delete();
    }

    private static void testCreateExpression(KernelServices services, SecretKey secretKey, String resolver) throws Exception {
        ModelNode createExpression = new ModelNode();
        createExpression.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION);
        createExpression.get(ClientConstants.OP).set(ElytronDescriptionConstants.CREATE_EXPRESSION);
        if (resolver != null) {
            createExpression.get(ElytronDescriptionConstants.RESOLVER).set(resolver);
        }
        createExpression.get(ElytronDescriptionConstants.CLEAR_TEXT).set(CLEAR_TEXT);

        ModelNode result = services.executeOperation(createExpression);

        if (secretKey != null) {
            assertSuccess(result);
            String expression = result.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.EXPRESSION).asString();

            String expectedPrefix = resolver != null ? "${ENC::" + resolver + ":" : "${ENC::";
            assertTrue("Expected Prefix", expression.startsWith(expectedPrefix));

            String extracted = expression.substring(expectedPrefix.length(), expression.length() - 1);

            String decrypted = CipherUtil.decrypt(extracted, secretKey);
            assertEquals("Successful descryption", CLEAR_TEXT, decrypted);
        } else {
            assertTrue("Failure expected", result.get(OUTCOME).asString().equals(ClientConstants.FAILED));
            assertTrue("Expected Error Code", result.get(ClientConstants.FAILURE_DESCRIPTION).asString().contains("WFLYELY00920:"));
        }
    }

    private static ModelNode resolver(final String name, final String credentialStore, final String secretKey) {
        ModelNode resolver = new ModelNode();
        resolver.get(ElytronDescriptionConstants.NAME).set(name);
        resolver.get(ElytronDescriptionConstants.CREDENTIAL_STORE).set(credentialStore);
        resolver.get(ElytronDescriptionConstants.SECRET_KEY).set(secretKey);
        return resolver;
    }
    private static ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private static String emptySubsystemXml() {
        StringBuffer sb = new StringBuffer("<subsystem xmlns=\"");
        sb.append(ElytronExtension.CURRENT_NAMESPACE);
        sb.append("\"></subsystem>");

        return sb.toString();
    }

    @Test
    public void testExpressionEncryptionCycle() throws Exception {
        SecretKey keyOne = SecretKeyUtil.generateSecretKey(128);
        SecretKey keyTwo = SecretKeyUtil.generateSecretKey(192);
        SecretKey keyThree = SecretKeyUtil.generateSecretKey(256);

        // Uses key from two
        String passwordForone = "${ENC::ResolverTwo:" + CipherUtil.encrypt(CredentialStoreUtility.DEFAULT_PASSWORD, keyTwo) + "}";
        // Uses key from three
        String passwordForTwo = "${ENC::ResolverThree:" + CipherUtil.encrypt(CredentialStoreUtility.DEFAULT_PASSWORD, keyThree) + "}";
        // Uses key from one
        String passwordForThree = "${ENC::ResolverOne:" + CipherUtil.encrypt(CredentialStoreUtility.DEFAULT_PASSWORD, keyOne) + "}";

        // Define the credential store resources in a single composite operation, a cycle should be detected which
        // prevents them from coming up.
        ModelNode composite = Operations.createCompositeOperation();
        ModelNode steps = composite.get(ClientConstants.STEPS);

        // Create 3 Credential Stores, each with a secret key. (Offline)
        CredentialStoreUtility csUtilOne = createCredentialStoreUtility("target/cycle-store-one.cs", steps, "store-one", passwordForone);
        csUtilOne.addEntry("key", keyOne);
        CredentialStoreUtility csUtilTwo = createCredentialStoreUtility("target/cycle-store-two.cs", steps, "store-two", passwordForTwo);
        csUtilTwo.addEntry("key", keyTwo);
        CredentialStoreUtility csUtilThree = createCredentialStoreUtility("target/cycle-store-three.cs", steps, "store-three", passwordForThree);
        csUtilThree.addEntry("key", keyThree);

        // Create an expression=encryption with 3 resolvers, one for each key.
        ModelNode addEE = new ModelNode();
        addEE.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION);
        addEE.get(ClientConstants.OP).set(ClientConstants.ADD);

        ModelNode resolvers = new ModelNode();
        resolvers.add(resolver("ResolverOne", "store-one", "key"));
        resolvers.add(resolver("ResolverTwo", "store-two", "key"));
        resolvers.add(resolver("ResolverThree", "store-three", "key"));
        addEE.get(ElytronDescriptionConstants.RESOLVERS).set(resolvers);
        steps.add(addEE);

        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXml(emptySubsystemXml()).build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        ModelNode result = services.executeOperation(composite);
        assertTrue("Failure expected", result.get(OUTCOME).asString().equals(ClientConstants.FAILED));
        assertTrue("Expected Error Code (Cycle Detected)",
                result.get(ClientConstants.FAILURE_DESCRIPTION).asString().contains("WFLYELY00043:"));

        csUtilOne.cleanUp();
        csUtilTwo.cleanUp();
        csUtilThree.cleanUp();
    }

    private CredentialStoreUtility createCredentialStoreUtility(final String path, final ModelNode steps, final String storeName,
            final String clearPassword) {
        File storeFile = new File(path);
        if (storeFile.exists()) {
            storeFile.delete();
        }

        ModelNode add = new ModelNode();
        add.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.CREDENTIAL_STORE, storeName);
        add.get(ClientConstants.OP).set(ClientConstants.ADD);
        add.get(ElytronDescriptionConstants.PATH).set(path);
        add.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set(clearPassword);

        steps.add(add);

        return new CredentialStoreUtility(path);
    }

}
