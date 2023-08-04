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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.common.iteration.CodePointIterator;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.realm.JaasSecurityRealm;
import org.wildfly.security.auth.server.ModifiableRealmIdentity;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.interfaces.OneTimePassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.OneTimePasswordSpec;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class RealmsTestCase extends AbstractElytronSubsystemBaseTest {

    public RealmsTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();

    @BeforeClass
    public static void setUp() {
        AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Security.insertProviderAt(wildFlyElytronProvider, 1));
    }
    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("realms-test.xml");
    }

    /* Test properties-realm */
    @Test
    public void testPropertyRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        // hex encoded using UTF-8
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("HashedPropertyRealm");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        testAbstractPropertyRealm(securityRealm);

        ServiceName serviceName2 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("ClearPropertyRealm");
        SecurityRealm securityRealm2 = (SecurityRealm) services.getContainer().getService(serviceName2).getValue();
        testAbstractPropertyRealm(securityRealm2);
        testExternalModificationPropertyRealm(securityRealm2, "users-clear.properties", "user999", "password999", "password999");

        // base64 encoded using UTF-8
        ServiceName serviceName3 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("HashedPropertyRealmBase64Encoded");
        SecurityRealm securityRealm3 = (SecurityRealm) services.getContainer().getService(serviceName3).getValue();
        performHashedFileTest(securityRealm3, "elytron","passwd12#$");
        testExternalModificationPropertyRealm(securityRealm3, "users-hashedbase64.properties", "user999",
                "password999", generateHashedPassword("user999", "password999", "ManagementRealm"));

        // base64 encoded using charset GB2312
        ServiceName serviceName4 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("HashedPropertyRealmBase64EncodedCharset");
        SecurityRealm securityRealm4 = (SecurityRealm) services.getContainer().getService(serviceName4).getValue();
        performHashedFileTest(securityRealm4, "elytron4", "password密码");

        RealmIdentity identity1 = securityRealm2.getRealmIdentity(fromName("user1"));
        Object[] groups = identity1.getAuthorizationIdentity().getAttributes().get("groupAttr").toArray();
        Assert.assertArrayEquals(new Object[]{"firstGroup","secondGroup"}, groups);
    }

    private void performHashedFileTest(SecurityRealm realm, String username, String password) throws Exception{
        Assert.assertNotNull(realm);

        RealmIdentity identity1 = realm.getRealmIdentity(fromName(username));
        Assert.assertTrue(identity1.exists());
        Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence(password.toCharArray())));
        assertFalse(identity1.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity1.dispose();

        RealmIdentity identity9 = realm.getRealmIdentity(fromName("user9"));
        assertFalse(identity9.exists());
        assertFalse(identity9.verifyEvidence(new PasswordGuessEvidence("password9".toCharArray())));
        identity9.dispose();
    }


    private void testAbstractPropertyRealm(SecurityRealm securityRealm) throws Exception {
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(fromName("user1"));
        Assert.assertTrue(identity1.exists());
        Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence("password1".toCharArray())));
        assertFalse(identity1.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity1.dispose();

        RealmIdentity identity2 = securityRealm.getRealmIdentity(fromName("user2"));
        Assert.assertTrue(identity2.exists());
        Assert.assertTrue(identity2.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity2.dispose();

        RealmIdentity identity9 = securityRealm.getRealmIdentity(fromName("user9"));
        assertFalse(identity9.exists());
        assertFalse(identity9.verifyEvidence(new PasswordGuessEvidence("password9".toCharArray())));
        identity9.dispose();
    }

    private String generateHashedPassword(String username, String password, String realm) throws Exception {
        EncryptablePasswordSpec passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), new DigestPasswordAlgorithmSpec(username, realm), StandardCharsets.UTF_8);
        PasswordFactory passwordFactory = PasswordFactory.getInstance(DigestPassword.ALGORITHM_DIGEST_MD5);
        DigestPassword digestPwd = passwordFactory.generatePassword(passwordSpec).castAs(DigestPassword.class);
        return ByteIterator.ofBytes(digestPwd.getDigest()).base64Encode().drainToString();
    }

    /* Performs a manual modification of the properties file and checks if it is refreshed */
    private void testExternalModificationPropertyRealm(SecurityRealm securityRealm, String fileName, String username, String password, String hash) throws Exception {
        // assert the username principal does not exist in the realm
        RealmIdentity identity = securityRealm.getRealmIdentity(fromName(username));
        assertFalse("Identity " + username + " already exists in the realm", identity.exists());
        identity.dispose();
        long current = System.currentTimeMillis();

        URL url = getClass().getResource(fileName);
        Assert.assertNotNull("The properties file " + fileName + " does not exist", url);
        Path propsPath = Paths.get(url.toURI());
        byte[] backup = Files.readAllBytes(propsPath);
        try {
            // modify the properties file adding the username and hashed password
            String line = System.lineSeparator() + username + "=" + hash;
            Files.write(propsPath, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            File file = propsPath.toFile();
            if (current >= file.lastModified()) {
                boolean modified = false;
                for (int i = 0; !modified && i < 10; i++) {
                    TimeUnit.MILLISECONDS.sleep(200);
                    file.setLastModified(System.currentTimeMillis());
                    modified = current < file.lastModified();
                }
                if (!modified) {
                    // file system is not updating the last modified time, so test won't work
                    Assert.fail("File System is not updating the modification timestamp");
                }
            }

            // assert that the property realm detects the external modification
            identity = securityRealm.getRealmIdentity(fromName(username));
            Assert.assertTrue("Identity " + username + " is not detected after external modification", identity.exists());
            Assert.assertTrue("Invalid password for the added identity", identity.verifyEvidence(new PasswordGuessEvidence(password.toCharArray())));
            identity.dispose();
        } finally {
            Files.write(Paths.get(url.toURI()), backup, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /* Test filesystem-realm with existing filesystem from resources, without relative-to */
    @Test
    public void testFilesystemRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemRealm");
        ModifiableSecurityRealm securityRealm = (ModifiableSecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(fromName("firstUser"));
        Assert.assertTrue(identity1.exists());
        identity1.dispose();

        testModifiability(securityRealm);
    }

    /**
     * Test the filesystem realm can handle identities with hashed passwords using string encodings and different character
     * sets
     */
    @Test
    public void testFileSystemRealmEncodingAndCharset() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        // Testing filesystem realm hex encoded using UTF-8 charset
        ServiceName serviceName3 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemRealm3");
        ModifiableSecurityRealm securityRealm3 = (ModifiableSecurityRealm) services.getContainer().getService(serviceName3).getValue();
        testAbstractFilesystemRealm(securityRealm3, "plainUser", "secretPassword");
        testAddingAndDeletingEncodedHash(securityRealm3, StandardCharsets.UTF_8, "secretPassword");


        // Testing filesystem realm hex encoded using GB2312 charset
        ServiceName serviceName4 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemRealm4");
        ModifiableSecurityRealm securityRealm4 = (ModifiableSecurityRealm) services.getContainer().getService(serviceName4).getValue();
        testAbstractFilesystemRealm(securityRealm4, "plainUser", "password密码");
        testAddingAndDeletingEncodedHash(securityRealm4, Charset.forName("gb2312"), "password密码");

    }

    /**
     * Test the filesystem realm can handle identities with different types of encrypted passwords and attributes. Also
     * ensures a pre-assigned SecretKey as well as a newly generated SecretKey work.
     */
    @Test
    public void testFilesystemRealmEncrypted() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemRealm5");
        ModifiableSecurityRealm securityRealm = (ModifiableSecurityRealm) services.getContainer().getService(serviceName).getValue();
        ServiceName serviceNameGeneratedKey = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemRealm6");
        ModifiableSecurityRealm securityRealmGeneratedKey = (ModifiableSecurityRealm) services.getContainer().getService(serviceNameGeneratedKey).getValue();

        // Test Clear Encrypted Password
        Assert.assertNotNull(securityRealm);
        char[] password = "secretPassword".toCharArray();
        RealmIdentity identityClear = securityRealm.getRealmIdentity(fromName("plainUser"));
        Assert.assertTrue(identityClear.exists());
        Assert.assertTrue(identityClear.verifyEvidence(new PasswordGuessEvidence(password)));
        Assert.assertFalse(identityClear.verifyEvidence(new PasswordGuessEvidence("secretPassword123".toCharArray())));
        identityClear.dispose();

        // Test BCrypt Hashed Encrypted Password
        RealmIdentity identityBcrypt = securityRealm.getRealmIdentity(fromName("plainUser1"));
        Assert.assertTrue(identityBcrypt.exists());
        Assert.assertTrue(identityBcrypt.verifyEvidence(new PasswordGuessEvidence(password)));
        Assert.assertFalse(identityBcrypt.verifyEvidence(new PasswordGuessEvidence("secretPassword123".toCharArray())));
        identityBcrypt.dispose();

        // Test encrypted attributes
        MapAttributes newAttributes = new MapAttributes();
        newAttributes.addFirst("name", "plainUser");
        newAttributes.addAll("roles", Arrays.asList("Employee", "Manager", "Admin"));

        RealmIdentity identityAttribute = securityRealm.getRealmIdentity(fromName("attributeUser"));
        Assert.assertTrue(identityAttribute.exists());
        AuthorizationIdentity authorizationIdentity = identityAttribute.getAuthorizationIdentity();
        Attributes existingAttributes = authorizationIdentity.getAttributes();
        identityAttribute.dispose();

        assertEquals(newAttributes.size(), existingAttributes.size());
        assertTrue(newAttributes.get("name").containsAll(existingAttributes.get("name")));
        assertTrue(newAttributes.get("roles").containsAll(existingAttributes.get("roles")));

        // Test OTP and BCrypt Encrypted Passwords
        RealmIdentity identityEverything = securityRealm.getRealmIdentity(fromName("plainUserEverything"));
        byte[] hash = CodePointIterator.ofString("505d889f90085847").hexDecode().drain();
        String seed = "ke1234";
        Assert.assertTrue(identityEverything.exists());
        Assert.assertTrue(identityEverything.verifyEvidence(new PasswordGuessEvidence(password)));
        OneTimePassword otp = identityEverything.getCredential(PasswordCredential.class, OneTimePassword.ALGORITHM_OTP_SHA1).getPassword(OneTimePassword.class);
        assertNotNull(otp);
        assertEquals(OneTimePassword.ALGORITHM_OTP_SHA1, otp.getAlgorithm());
        assertArrayEquals(hash, otp.getHash());
        assertEquals(seed, otp.getSeed());
        identityEverything.dispose();

        // Test creating new encrypted realm with identity
        List<Credential> credentials = new LinkedList<>();
        PasswordFactory factory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR);
        ClearPassword clearPassword = (ClearPassword) factory.generatePassword(new ClearPasswordSpec(password));
        credentials.add(new PasswordCredential(clearPassword));

        ModifiableRealmIdentity identityEmpty = securityRealmGeneratedKey.getRealmIdentityForUpdate(fromName("emptyIdentity"));
        Assert.assertFalse(identityEmpty.exists());
        identityEmpty.create();
        Assert.assertTrue(identityEmpty.exists());
        identityEmpty.setCredentials(credentials);
        identityEmpty.dispose();

        ModifiableRealmIdentity identityEmpty2 = securityRealmGeneratedKey.getRealmIdentityForUpdate(fromName("emptyIdentity"));
        Assert.assertTrue(identityEmpty2.exists());
        Assert.assertTrue(identityEmpty2.verifyEvidence(new PasswordGuessEvidence(password)));
        identityEmpty.delete();
        Assert.assertFalse(identityEmpty.exists());
        identityEmpty.dispose();
    }

    /**
     * Test the signature of the filesystem realm when enabling integrity support
     */
    @Test
    public void testFilesystemRealmIntegrity() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemRealmIntegrity");
        ModifiableSecurityRealm securityRealm = (ModifiableSecurityRealm) services.getContainer().getService(serviceName).getValue();
        ServiceName serviceNameBoth = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("FilesystemRealmIntegrityAndEncryption");
        ModifiableSecurityRealm securityRealmBoth = (ModifiableSecurityRealm) services.getContainer().getService(serviceNameBoth).getValue();
        Assert.assertNotNull(securityRealm);
        Assert.assertNotNull(securityRealmBoth);

        String targetDir = Paths.get("target/test-classes/org/wildfly/extension/elytron/").toAbsolutePath().toString();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] keyStorePassword = "secret".toCharArray();
        keyStore.load(new FileInputStream(targetDir + "/keystore"), keyStorePassword);
        PublicKey publicKey = keyStore.getCertificate("localhost").getPublicKey();
        PublicKey invalidPublicKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();


        char[] password = "password".toCharArray();
        ModifiableRealmIdentity identity = securityRealm.getRealmIdentityForUpdate(fromName("user"));
        Assert.assertTrue(identity.exists());
        Assert.assertTrue(identity.verifyEvidence(new PasswordGuessEvidence(password)));
        Assert.assertFalse(identity.verifyEvidence(new PasswordGuessEvidence("secretPassword123".toCharArray())));


        File identityFile = new File(targetDir + "/filesystem-realm-integrity/u/user-OVZWK4Q.xml");
        assertTrue(validateDigitalSignature(identityFile, publicKey));
        assertFalse(validateDigitalSignature(identityFile, invalidPublicKey));

        MapAttributes newAttributes = new MapAttributes();
        newAttributes.addFirst("firstName", "John");
        newAttributes.addFirst("lastName", "Smith");
        newAttributes.addAll("roles", Arrays.asList("Employee", "Manager", "Admin"));
        identity.setAttributes(newAttributes);
        // Test that the publicKey still works correctly after signature is changed
        assertTrue(validateDigitalSignature(identityFile, publicKey));

        // Verify that an identity with an incorrect signature doesn't validate
        RealmIdentity identity2 = securityRealm.getRealmIdentity(fromName("user2"));
        Assert.assertTrue(identity.exists());
        File identityFile2 = new File(targetDir + "/filesystem-realm-integrity/u/user2-OVZWK4RS.xml");
        assertFalse(validateDigitalSignature(identityFile2, publicKey));

        // Verify a new identity generates a signature and is correct
        ModifiableRealmIdentity identity3 = securityRealm.getRealmIdentityForUpdate(new NamePrincipal("user3"));
        // This identity may exist from a previous test execution if no maven clean was performed.
        // If so, remove it, otherwise the create() call will fail.
        if (identity3.exists()) {
            identity3.delete();
            Assert.assertFalse(identity3.exists());
        }
        identity3.create();
        identity3.setAttributes(newAttributes);
        PasswordFactory factory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR, WildFlyElytronPasswordProvider.getInstance());
        ClearPassword clearPassword = (ClearPassword) factory.generatePassword(new ClearPasswordSpec(password));
        identity3.setCredentials(Collections.singleton(new PasswordCredential(clearPassword)));
        File identityFile3 = new File(targetDir + "/filesystem-realm-integrity/u/user3-OVZWK4RT.xml");
        assertTrue(validateDigitalSignature(identityFile3, publicKey));

        identity.dispose();
        identity2.dispose();
        identity3.dispose();

        // Verify that this works for a realm with encryption and integrity enabled
        identity = securityRealmBoth.getRealmIdentityForUpdate(new NamePrincipal("user1"));
        // This identity may exist from a previous test execution if no maven clean was performed.
        // If so, remove it, otherwise the create() call will fail.
        if (identity.exists()) {
            identity.delete();
            Assert.assertFalse(identity.exists());
        }
        identity.create();
        identity.setAttributes(newAttributes);
        identity.setCredentials(Collections.singleton(new PasswordCredential(clearPassword)));
        identityFile = new File(targetDir + "/filesystem-realm-integrity-encryption/O/OVZWK4RR.xml");
        assertTrue(validateDigitalSignature(identityFile, publicKey));
        identity.dispose();
    }

    private boolean validateDigitalSignature(File path, PublicKey publicKey) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(path);
            NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (nl.getLength() == 0) {
                return false;
            }
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            DOMValidateContext valContext = new DOMValidateContext(publicKey, nl.item(0));
            XMLSignature signature = fac.unmarshalXMLSignature(valContext);
            boolean coreValidity = signature.validate(valContext);
            if (!coreValidity) {
                boolean sv = signature.getSignatureValue().validate(valContext);
                // check the validation status of each Reference
                Iterator i = signature.getSignedInfo().getReferences().iterator();
                for (int j = 0; i.hasNext(); j++) {
                    ((Reference) i.next()).validate(valContext);
                }
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }


    private void testAbstractFilesystemRealm(SecurityRealm securityRealm, String username, String password) throws Exception {
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(fromName(username));
        Assert.assertTrue(identity1.exists());
        Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence(password.toCharArray())));
        assertFalse(identity1.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity1.dispose();

        RealmIdentity identity9 = securityRealm.getRealmIdentity(fromName("user9"));
        assertFalse(identity9.exists());
        assertFalse(identity9.verifyEvidence(new PasswordGuessEvidence("password9".toCharArray())));
        identity9.dispose();
    }


    private void testAddingAndDeletingEncodedHash(ModifiableSecurityRealm securityRealm, Charset hashCharset, String password) throws Exception {

        // obtain original count of identities
        int oldCount = getRealmIdentityCount(securityRealm);
        Assert.assertTrue(oldCount > 0);

        // create identity
        ModifiableRealmIdentity identity1 = securityRealm.getRealmIdentityForUpdate(fromName("createdUser"));
        assertFalse(identity1.exists());
        identity1.create();
        Assert.assertTrue(identity1.exists());

        // write password credential
        List<Credential> credentials = new LinkedList<>();

        PasswordFactory passwordFactory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT);
        char[] actualPassword = password.toCharArray();
        byte[] salt = {(byte) 0x49, (byte) 0x3a, (byte) 0x6c, (byte) 0x23, (byte) 0x4d, (byte) 0x51, (byte) 0x9a, (byte) 0x54, (byte) 0x17,
                (byte) 0x87, (byte) 0xad, (byte) 0x37, (byte) 0x51, (byte) 0x98, (byte) 0x51, (byte) 0x76};
        BCryptPassword bCryptPassword = (BCryptPassword) passwordFactory.generatePassword(
                new EncryptablePasswordSpec(actualPassword, new IteratedSaltedPasswordAlgorithmSpec(10, salt), hashCharset));

        credentials.add(new PasswordCredential(bCryptPassword));

        identity1.setCredentials(credentials);
        identity1.dispose();

        // read created identity
        ModifiableRealmIdentity identity2 = securityRealm.getRealmIdentityForUpdate(fromName("createdUser"));
        Assert.assertTrue(identity2.exists());

        // verify password
        Assert.assertTrue(identity2.verifyEvidence(new PasswordGuessEvidence(password.toCharArray())));

        // delete identity
        identity1.delete();
        assertFalse(identity1.exists());
        identity1.dispose();
    }

    @Test
    public void testJwtRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("JwtRealm");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("EmptyJwtRealm");
        securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);
    }

    @Test
    public void testOAuth2Realm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("OAuth2Realm");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);
    }

    @Test
    public void testAggregateRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("AggregateRealmOne");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(fromName("firstUser"));
        Assert.assertTrue(identity1.exists());

        Assert.assertEquals(3, identity1.getAuthorizationIdentity().getAttributes().size());
        Assert.assertEquals("[Jane]", identity1.getAuthorizationIdentity().getAttributes().get("firstName").toString());
        Assert.assertEquals("[Doe]", identity1.getAuthorizationIdentity().getAttributes().get("lastName").toString());
        Assert.assertEquals("[Employee, Manager, Admin]", identity1.getAuthorizationIdentity().getAttributes().get("roles").toString());

        identity1.dispose();
    }

    @Test
    public void testAggregateRealmWithPrincipalTransformer() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("AggregateRealmTwo");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(fromName("firstUser"));
        Assert.assertTrue(identity1.exists());
        //Assert that transformation was successful and the correct identity and attributes were loaded from filesystem-realm-2
        Assert.assertEquals(3, identity1.getAuthorizationIdentity().getAttributes().size());
        Assert.assertEquals("[Jane2]", identity1.getAuthorizationIdentity().getAttributes().get("firstName").toString());
        Assert.assertEquals("[Doe2]", identity1.getAuthorizationIdentity().getAttributes().get("lastName").toString());
        Assert.assertEquals("[Employee2, Manager2, Admin2]", identity1.getAuthorizationIdentity().getAttributes().get("roles").toString());

        identity1.dispose();
    }

    @Test
    public void testJAASRealm() throws Exception {
        try {
            KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
            if (!services.isSuccessfulBoot()) {
                Assert.fail(services.getBootError().toString());
            }

            JaasSecurityRealm securityRealm;
            if (!(JdkUtils.isIbmJdk())) {
                ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("myJaasRealm");
                securityRealm = (JaasSecurityRealm) services.getContainer().getService(serviceName).getValue();
                Assert.assertNotNull(securityRealm);
            } else {
                // IBM JDK 8 does not recognize default policy type "JavaLoginConfig" so the path to JAAS configuration file must be provided via system property
                System.setProperty("java.security.auth.login.config", RealmsTestCase.class.getResource("jaas-login.config").toString());
                securityRealm = new JaasSecurityRealm("Entry1");
            }

            RealmIdentity identity1 = securityRealm.getRealmIdentity(new NamePrincipal("user"));

            // verify password
            assertFalse(identity1.verifyEvidence(new PasswordGuessEvidence("incorrectPassword".toCharArray())));
            Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence("userPassword".toCharArray())));

            Assert.assertTrue(identity1.exists());

            RealmIdentity identity2 = securityRealm.getRealmIdentity(new NamePrincipal("nonexistentUser"));
            // verify password
            assertFalse(identity2.verifyEvidence(new PasswordGuessEvidence("password".toCharArray())));
            // even nonexistent identity will return true for exists() method,
            // because we have no way of finding this out, but at least the authorization identity always exists
            Assert.assertTrue(identity2.exists());
            Assert.assertTrue(identity2.getAuthorizationIdentity().getAttributes().isEmpty());

            SecurityDomain securityDomain = SecurityDomain.builder().setDefaultRealmName("default").addRealm("default", securityRealm).build()
                    .setPermissionMapper(((permissionMappable, roles) -> LoginPermission.getInstance()))
                    .build();

            ServerAuthenticationContext sac1 = securityDomain.createNewAuthenticationContext();
            sac1.setAuthenticationPrincipal(new NamePrincipal("user"));
            assertFalse(sac1.verifyEvidence(new PasswordGuessEvidence("incorrect".toCharArray())));
            assertTrue(sac1.verifyEvidence(new PasswordGuessEvidence("userPassword".toCharArray())));
            Assert.assertTrue(sac1.authorize());
            Assert.assertFalse(sac1.getAuthorizedIdentity().getAttributes().containsKey("Roles"));
            Assert.assertTrue(sac1.getAuthorizedIdentity().getAttributes().containsKey("NamePrincipal"));
            Assert.assertFalse(sac1.getAuthorizedIdentity().getAttributes().containsKey("NonexistentPrincipal"));
            Assert.assertEquals("User", sac1.getAuthorizedIdentity().getAttributes().getFirst("NamePrincipal"));
        } finally {
            System.clearProperty("java.security.auth.login.config");
        }
    }

    @Test
    public void testEntryInJAASConfigMustBeProvided() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR)
                .add("subsystem", "elytron").add("jaas-realm", "my-jaas-realm");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        // operation.get(ElytronDescriptionConstants.ENTRY).set("any_entry");
        ModelNode response = services.executeOperation(operation);
        // operation will fail because path does not exist
        if (! response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }
        Assert.assertTrue(response.asString().contains("'entry' may not be null"));
    }

    @Test
    public void testPathToJAASConfigFileDoesNotExist() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR)
                .add("subsystem", "elytron").add("jaas-realm", "my-jaas-realm");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.ENTRY).set("any_entry");
        operation.get(ElytronDescriptionConstants.PATH).set("this/is/non/existen/path");
        ModelNode response = services.executeOperation(operation);
        // operation will fail because path does not exist
        if (! response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }
        Assert.assertTrue(response.asString().contains("JAAS configuration file does not exist."));
    }

    @Test
    public void testDistributedRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("DistributedRealmNoUnavailable");
        SecurityRealm distributedRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        testDistributedRealmSuccessful(distributedRealm);

        ServiceName serviceName2 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("DistributedRealmFirstUnavailable");
        SecurityRealm distributedRealm2 = (SecurityRealm) services.getContainer().getService(serviceName2).getValue();
        testDistributedRealmFailure(distributedRealm2);

        ServiceName serviceName3 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("DistributedRealmFirstUnavailableIgnored");
        SecurityRealm distributedRealm3 = (SecurityRealm) services.getContainer().getService(serviceName3).getValue();
        testDistributedRealmSuccessful(distributedRealm3);

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "DistributedRealmDomain");
        ServiceName serviceName4 = Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY.getCapabilityServiceName("DistributedRealmDomain");
        SecurityDomain domain = (SecurityDomain) services.getContainer().getService(serviceName4).getValue();
        Assert.assertNotNull(domain);
        if (SecurityDomain.getCurrent() == null) {
            domain.registerWithClassLoader(Thread.currentThread().getContextClassLoader());
        }
        ServerAuthenticationContext context = domain.createNewAuthenticationContext();
        context.setAuthenticationName("firstUser");
        Assert.assertTrue(context.exists());

        Assert.assertTrue(isSecurityRealmUnavailableEventLogged("LdapRealm"));
    }

    private boolean isSecurityRealmUnavailableEventLogged(String realmName) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("target/audit.log"), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.contains("SecurityRealmUnavailableEvent") && line.contains(realmName)) {
                return true;
            }
        }
        return false;
    }

    private void testDistributedRealmSuccessful(SecurityRealm distributedRealm) throws RealmUnavailableException {
        Assert.assertNotNull(distributedRealm);

        RealmIdentity identity = distributedRealm.getRealmIdentity(fromName("firstUser"));
        Assert.assertTrue(identity.exists());
        identity.dispose();
    }

    private void testDistributedRealmFailure(SecurityRealm distributedRealm) throws RealmUnavailableException {
        Assert.assertNotNull(distributedRealm);

        Assert.assertThrows(RealmUnavailableException.class, () -> {
            distributedRealm.getRealmIdentity(fromName("firstUser"));
        });
    }

    static void testModifiability(ModifiableSecurityRealm securityRealm) throws Exception {

        // obtain original count of identities
        int oldCount = getRealmIdentityCount(securityRealm);
        Assert.assertTrue(oldCount > 0);

        // create identity
        ModifiableRealmIdentity identity1 = securityRealm.getRealmIdentityForUpdate(fromName("createdUser"));
        assertFalse(identity1.exists());
        identity1.create();
        Assert.assertTrue(identity1.exists());

        // write password credential
        List<Credential> credentials = new LinkedList<>();
        PasswordFactory factory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR);
        KeySpec spec = new ClearPasswordSpec("createdPassword".toCharArray());
        credentials.add(new PasswordCredential(factory.generatePassword(spec)));

        PasswordFactory factoryOtp = PasswordFactory.getInstance(OneTimePassword.ALGORITHM_OTP_SHA1);
        KeySpec specOtp = new OneTimePasswordSpec(new byte[]{0x12}, "4", 56789);
        credentials.add(new PasswordCredential(factoryOtp.generatePassword(specOtp)));

        identity1.setCredentials(credentials);
        identity1.dispose();

        // read created identity
        ModifiableRealmIdentity identity2 = securityRealm.getRealmIdentityForUpdate(fromName("createdUser"));
        Assert.assertTrue(identity2.exists());

        // verify password
        Assert.assertTrue(identity2.verifyEvidence(new PasswordGuessEvidence("createdPassword".toCharArray())));

        // obtain OTP
        OneTimePassword otp = identity2.getCredential(PasswordCredential.class, OneTimePassword.ALGORITHM_OTP_SHA1).getPassword(OneTimePassword.class);
        Assert.assertArrayEquals(new byte[]{0x12}, otp.getHash());
        Assert.assertEquals("4", otp.getSeed());
        Assert.assertEquals(56789, otp.getSequenceNumber());
        identity2.dispose();

        // iterate (include created identity)
        int newCount = getRealmIdentityCount(securityRealm);
        Assert.assertEquals(oldCount + 1, newCount);

        // delete identity
        identity1 = securityRealm.getRealmIdentityForUpdate(fromName("createdUser"));
        identity1.delete();
        assertFalse(identity1.exists());
        identity1.dispose();
    }

    private static int getRealmIdentityCount(final ModifiableSecurityRealm securityRealm) throws Exception {
        int count = 0;
        Iterator<ModifiableRealmIdentity> it = securityRealm.getRealmIdentityIterator();
        while (it.hasNext()) {
            ModifiableRealmIdentity identity = it.next();
            Assert.assertTrue(identity.exists());
            identity.dispose();
            count++;
        }
        return count;
    }

    private static Principal fromName(final String name) {
        return new NamePrincipal(name);
    }

}
