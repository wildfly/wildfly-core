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

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.ModifiableRealmIdentity;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.OneTimePassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.OneTimePasswordSpec;

import java.io.IOException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.spec.KeySpec;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class RealmsTestCase extends AbstractSubsystemBaseTest {

    public RealmsTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
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
            Assert.fail(services.getBootError().toString());
        }

        // hex encoded using UTF-8
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("HashedPropertyRealm");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        testAbstractPropertyRealm(securityRealm);

        ServiceName serviceName2 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("ClearPropertyRealm");
        SecurityRealm securityRealm2 = (SecurityRealm) services.getContainer().getService(serviceName2).getValue();
        testAbstractPropertyRealm(securityRealm2);

        // base64 encoded using UTF-8
        ServiceName serviceName3 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("HashedPropertyRealmBase64Encoded");
        SecurityRealm securityRealm3 = (SecurityRealm) services.getContainer().getService(serviceName3).getValue();
        performHashedFileTest(securityRealm3, "elytron","passwd12#$");

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
        Assert.assertFalse(identity1.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity1.dispose();

        RealmIdentity identity9 = realm.getRealmIdentity(fromName("user9"));
        Assert.assertFalse(identity9.exists());
        Assert.assertFalse(identity9.verifyEvidence(new PasswordGuessEvidence("password9".toCharArray())));
        identity9.dispose();
    }


    private void testAbstractPropertyRealm(SecurityRealm securityRealm) throws Exception {
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(fromName("user1"));
        Assert.assertTrue(identity1.exists());
        Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence("password1".toCharArray())));
        Assert.assertFalse(identity1.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity1.dispose();

        RealmIdentity identity2 = securityRealm.getRealmIdentity(fromName("user2"));
        Assert.assertTrue(identity2.exists());
        Assert.assertTrue(identity2.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity2.dispose();

        RealmIdentity identity9 = securityRealm.getRealmIdentity(fromName("user9"));
        Assert.assertFalse(identity9.exists());
        Assert.assertFalse(identity9.verifyEvidence(new PasswordGuessEvidence("password9".toCharArray())));
        identity9.dispose();
    }

    /* Test filesystem-realm with existing filesystem from resources, without relative-to */
    @Test
    public void testFilesystemRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
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
            Assert.fail(services.getBootError().toString());
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

    private void testAbstractFilesystemRealm(SecurityRealm securityRealm, String username, String password) throws Exception {
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(fromName(username));
        Assert.assertTrue(identity1.exists());
        Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence(password.toCharArray())));
        Assert.assertFalse(identity1.verifyEvidence(new PasswordGuessEvidence("password2".toCharArray())));
        identity1.dispose();

        RealmIdentity identity9 = securityRealm.getRealmIdentity(fromName("user9"));
        Assert.assertFalse(identity9.exists());
        Assert.assertFalse(identity9.verifyEvidence(new PasswordGuessEvidence("password9".toCharArray())));
        identity9.dispose();
    }


    private void testAddingAndDeletingEncodedHash(ModifiableSecurityRealm securityRealm, Charset hashCharset, String password) throws Exception {

        // obtain original count of identities
        int oldCount = getRealmIdentityCount(securityRealm);
        Assert.assertTrue(oldCount > 0);

        // create identity
        ModifiableRealmIdentity identity1 = securityRealm.getRealmIdentityForUpdate(fromName("createdUser"));
        Assert.assertFalse(identity1.exists());
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
        Assert.assertFalse(identity1.exists());
        identity1.dispose();
    }

    @Test
    public void testJwtRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
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
            Assert.fail(services.getBootError().toString());
        }

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("OAuth2Realm");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);
    }

    @Test
    public void testAggregateRealm() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("realms-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
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
            Assert.fail(services.getBootError().toString());
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

    static void testModifiability(ModifiableSecurityRealm securityRealm) throws Exception {

        // obtain original count of identities
        int oldCount = getRealmIdentityCount(securityRealm);
        Assert.assertTrue(oldCount > 0);

        // create identity
        ModifiableRealmIdentity identity1 = securityRealm.getRealmIdentityForUpdate(fromName("createdUser"));
        Assert.assertFalse(identity1.exists());
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
        Assert.assertFalse(identity1.exists());
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
