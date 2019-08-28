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
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.OneTimePassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.OneTimePasswordSpec;

import java.io.IOException;
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

        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("HashedPropertyRealm");
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        testAbstractPropertyRealm(securityRealm);

        ServiceName serviceName2 = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("ClearPropertyRealm");
        SecurityRealm securityRealm2 = (SecurityRealm) services.getContainer().getService(serviceName2).getValue();
        testAbstractPropertyRealm(securityRealm2);

        RealmIdentity identity1 = securityRealm2.getRealmIdentity(fromName("user1"));
        Object[] groups = identity1.getAuthorizationIdentity().getAttributes().get("groupAttr").toArray();
        Assert.assertArrayEquals(new Object[]{"firstGroup","secondGroup"}, groups);
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
