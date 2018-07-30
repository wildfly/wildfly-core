/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.common.iteration.CodePointIterator;
import org.wildfly.extension.elytron.capabilities._private.DirContextSupplier;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.ModifiableRealmIdentity;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.evidence.X509PeerCertificateChainEvidence;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.x500.cert.BasicConstraintsExtension;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.security.auth.x500.X500Principal;

import java.io.File;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

/**
 * Tests of LDAP related components (excluded from their natural TestCases to prevent repeated LDAP starting)
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class LdapTestCase extends AbstractSubsystemTest {

    private KernelServices services;
    private static final String WORKING_DIRECTORY_LOCATION = "./target/test-classes/org/wildfly/extension/elytron";
    private static final String LDIF_LOCATION = "/ldap-data.ldif";
    private static final X500Principal ISSUER_DN = new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA ");
    private static final X500Principal SCARAB_DN = new X500Principal("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Scarab");
    private static X509Certificate SCARAB_CERTIFICATE;

    private static X509Certificate createScarabCertificate() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair scarabKeys = keyPairGenerator.generateKeyPair();
        PublicKey scarabPublicKey = scarabKeys.getPublic();

        SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(ISSUER_DN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA1withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();

        return new X509CertificateBuilder()
                .setIssuerDn(ISSUER_DN)
                .setSubjectDn(SCARAB_DN)
                .setSignatureAlgorithmName("SHA1withRSA")
                .setSigningKey(issuerSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(scarabPublicKey)
                .setSerialNumber(new BigInteger("4"))
                .addExtension(new BasicConstraintsExtension(false, false, -1))
                .build();
    }

    private static void rewriteLdapDataLdif(X509Certificate certificate) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        String digest = ByteIterator.ofBytes(md.digest(certificate.getEncoded())).hexEncode(true).drainToString();

        CodePointIterator certificateBytes = ByteIterator.ofBytes(certificate.getEncoded()).base64Encode();
        String certificateBaseString = "usercertificate:: " + certificateBytes.drainToString();
        String certificateString = "";
        int counter = 0;
        for (int i = 0; i < certificateBaseString.length(); i++){
            if(i == 78 || i == (78+77*counter)){
                certificateString = certificateString + System.getProperty("line.separator");
                certificateString = certificateString + " ";
                counter += 1;
            }
            certificateString = certificateString + certificateBaseString.charAt(i);
        }

        // Read in the file and then replace the x509digest and usercertificate
        Path filePath = Paths.get(WORKING_DIRECTORY_LOCATION + LDIF_LOCATION);
        String ldapDataLdifString = new String(Files.readAllBytes(filePath),StandardCharsets.UTF_8);
        ldapDataLdifString = ldapDataLdifString.replaceFirst("x509digest: (.*)", "x509digest: " + digest);
        ldapDataLdifString = ldapDataLdifString.replaceFirst(Pattern.quote("usercertificate::"), certificateString);
        Files.write(filePath, ldapDataLdifString.getBytes(StandardCharsets.UTF_8));
    }

    private static void loadResources() throws Exception {
        Files.copy(Paths.get(WORKING_DIRECTORY_LOCATION + LDIF_LOCATION), Paths.get(WORKING_DIRECTORY_LOCATION + LDIF_LOCATION + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        SCARAB_CERTIFICATE = createScarabCertificate();
        rewriteLdapDataLdif(SCARAB_CERTIFICATE);
    }

    @BeforeClass
    public static void startLdapService() throws Exception {
        loadResources();
        TestEnvironment.startLdapService();
    }

    @AfterClass
    public static void restoreLdif() throws Exception {
        Files.copy(Paths.get(WORKING_DIRECTORY_LOCATION + LDIF_LOCATION + ".bak"), Paths.get(WORKING_DIRECTORY_LOCATION + LDIF_LOCATION), StandardCopyOption.REPLACE_EXISTING);
        new File(WORKING_DIRECTORY_LOCATION + LDIF_LOCATION + ".bak").delete();
    }

    public LdapTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Before
    public void initializeSubsystem() throws Exception {
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("ldap.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }
    }

    @Test
    public void testDirContextInsecure() throws Exception {
        ServiceName serviceNameDirContext = Capabilities.DIR_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName("DirContextInsecure");
        ExceptionSupplier<DirContext, NamingException> dirContextSup = (DirContextSupplier) services.getContainer().getService(serviceNameDirContext).getValue();
        DirContext dirContext = dirContextSup.get();
        Assert.assertNotNull(dirContext);
        Assert.assertEquals("org.wildfly.security.auth.realm.ldap.DelegatingLdapContext", dirContext.getClass().getName());
        dirContext.close();
    }

    @Test
    public void testDirContextSsl() throws Exception {
        ServiceName serviceNameDirContext = Capabilities.DIR_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName("DirContextSsl");
        ExceptionSupplier<DirContext, NamingException> dirContextSup = (DirContextSupplier) services.getContainer().getService(serviceNameDirContext).getValue();
        DirContext dirContext = dirContextSup.get();
        Assert.assertNotNull(dirContext);
        Assert.assertEquals("org.wildfly.security.auth.realm.ldap.DelegatingLdapContext", dirContext.getClass().getName());
        dirContext.close();
    }

    @Test
    public void testDirContextSslCredential() throws Exception {
        ServiceName serviceNameDirContext = Capabilities.DIR_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName("DirContextSslCredential");
        ExceptionSupplier<DirContext, NamingException> dirContextSup = (DirContextSupplier) services.getContainer().getService(serviceNameDirContext).getValue();
        DirContext dirContext = dirContextSup.get();
        Assert.assertNotNull(dirContext);
        Assert.assertEquals("org.wildfly.security.auth.realm.ldap.DelegatingLdapContext", dirContext.getClass().getName());
        dirContext.close();
    }

    @Test
    public void testLdapRealm() throws Exception {
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("LdapRealm");
        ModifiableSecurityRealm securityRealm = (ModifiableSecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(new NamePrincipal("plainUser"));
        Assert.assertTrue(identity1.exists());
        Attributes as = identity1.getAttributes();
        Assert.assertArrayEquals(new String[]{"uid=plainUser,dc=users,dc=elytron,dc=wildfly,dc=org"}, as.get("userDn").toArray());
        Assert.assertArrayEquals(new String[]{"plainUser"}, as.get("userName").toArray());
        Assert.assertArrayEquals(new String[]{"plainUserCn"}, as.get("firstName").toArray());
        Assert.assertArrayEquals(new String[]{"plainUserSn"}, as.get("SN").toArray());
        Assert.assertArrayEquals(new String[]{"(408) 555-2468", "+420 123 456 789"}, as.get("phones").toArray());
        Assert.assertArrayEquals(new String[]{"cn=Retail,ou=Finance,dc=groups,dc=elytron,dc=wildfly,dc=org"}, as.get("rolesDn").toArray());
        Assert.assertArrayEquals(new String[]{"Retail","Sales"}, as.get("rolesRecRdnCn").toArray());
        Assert.assertArrayEquals(new String[]{"Retail"}, as.get("rolesCn").toArray());
        Assert.assertArrayEquals(new String[]{"Retail department","Second description","Sales department"}, as.get("rolesDescription").toArray());
        Assert.assertArrayEquals(new String[]{"StreetOfRoleByName1","StreetOfRoleByName2"}, as.get("rolesByName").toArray());
        Assert.assertArrayEquals(new String[]{"Sales department","Management department"}, as.get("memberOfDescription").toArray());
        Assert.assertArrayEquals(new String[]{"cn=Manager,ou=Finance,dc=groups,dc=elytron,dc=wildfly,dc=org","cn=Sales,ou=Finance,dc=groups,dc=elytron,dc=wildfly,dc=org"}, as.get("memberOfDn").toArray());
        Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence("plainPassword".toCharArray())));
        identity1.dispose();

        RealmIdentity identity2 = securityRealm.getRealmIdentity(new NamePrincipal("refUser")); // referrer test
        Assert.assertTrue(identity2.exists());
        as = identity2.getAttributes();
        Assert.assertArrayEquals(new String[]{"uid=refUser,dc=referredUsers,dc=elytron,dc=wildfly,dc=org"}, as.get("userDn").toArray());
        Assert.assertArrayEquals(new String[]{"refUserCn"}, as.get("firstName").toArray());
        Assert.assertTrue(identity2.verifyEvidence(new PasswordGuessEvidence("plainPassword".toCharArray())));
        identity2.dispose();

        RealmIdentity x509User = securityRealm.getRealmIdentity(new NamePrincipal("x509User"));
        Assert.assertTrue(x509User.exists());
        Assert.assertTrue(x509User.verifyEvidence(new X509PeerCertificateChainEvidence(SCARAB_CERTIFICATE)));
    }

    @Test
    public void testLdapRealmIterating() throws Exception {
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("LdapRealm");
        ModifiableSecurityRealm securityRealm = (ModifiableSecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        Set<String> dns = new HashSet<>();
        Iterator<ModifiableRealmIdentity> it = securityRealm.getRealmIdentityIterator();
        while (it.hasNext()) {
            dns.add(it.next().getAuthorizationIdentity().getAttributes().getFirst("userDn"));
        }
        ((AutoCloseable) it).close();
        System.out.println(dns);
        Assert.assertTrue(dns.contains("uid=plainUser,dc=users,dc=elytron,dc=wildfly,dc=org"));
        Assert.assertTrue(dns.contains("uid=refUser,dc=referredUsers,dc=elytron,dc=wildfly,dc=org"));
    }

    @Test
    public void testLdapRealmModifiability() throws Exception {
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("LdapRealm");
        ModifiableSecurityRealm securityRealm = (ModifiableSecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        RealmsTestCase.testModifiability(securityRealm);
    }

    @Test
    public void testLdapRealmDirectVerification() throws Exception {
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName("LdapRealmDirectVerification");
        ModifiableSecurityRealm securityRealm = (ModifiableSecurityRealm) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(securityRealm);

        RealmIdentity identity1 = securityRealm.getRealmIdentity(new NamePrincipal("plainUser"));
        Assert.assertTrue(identity1.exists());
        Assert.assertTrue(identity1.verifyEvidence(new PasswordGuessEvidence("plainPassword".toCharArray())));
        identity1.dispose();

        RealmIdentity identity2 = securityRealm.getRealmIdentity(new NamePrincipal("refUser")); // referrer test
        Assert.assertTrue(identity2.exists());
        Assert.assertTrue(identity2.verifyEvidence(new PasswordGuessEvidence("plainPassword".toCharArray())));
        identity2.dispose();
    }

    private X509Certificate loadCertificate(String name) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        InputStream is = LdapTestCase.class.getResourceAsStream(name);
        return (X509Certificate) certificateFactory.generateCertificate(is);
    }

    @Test
    public void testLdapKeyStoreMinimalService() throws Exception {
        testLdapKeyStoreService("LdapKeyStoreMinimal", "firefly");
    }

    @Test
    public void testLdapKeyStoreMaximalService() throws Exception {
        testLdapKeyStoreService("LdapKeyStoreMaximal", "serenity");
    }

    private void testLdapKeyStoreService(String keystoreName, String alias) throws Exception {
        ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName(keystoreName);
        KeyStore keyStore = (KeyStore) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(keyStore);

        Assert.assertTrue(keyStore.containsAlias(alias));
        Assert.assertTrue(keyStore.isKeyEntry(alias));
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
        Assert.assertEquals("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly", cert.getSubjectDN().getName());
        Assert.assertEquals(alias, keyStore.getCertificateAlias(cert));

        Certificate[] chain = keyStore.getCertificateChain(alias);
        Assert.assertEquals("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly", ((X509Certificate) chain[0]).getSubjectDN().getName());
        Assert.assertEquals("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA", ((X509Certificate) chain[1]).getSubjectDN().getName());
    }

    @Test
    public void testLdapKeyStoreMinimalCli() throws Exception {
        testLdapKeyStoreCli("LdapKeyStoreMinimal", "firefly");
    }

    @Test
    public void testLdapKeyStoreMaximalCli() throws Exception {
        testLdapKeyStoreCli("LdapKeyStoreMaximal", "serenity");
    }

    private void testLdapKeyStoreCli(String keystoreName, String alias) throws Exception {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("ldap-key-store", keystoreName);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);
        List<ModelNode> nodes = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList();
        Assert.assertEquals(1, nodes.size());
        Assert.assertEquals(alias, nodes.get(0).asString());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("ldap-key-store", keystoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.STATE);
        Assert.assertEquals(ServiceController.State.UP.toString(), assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asString());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("ldap-key-store", keystoreName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.SIZE);
        Assert.assertEquals(1, assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asInt());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("ldap-key-store", keystoreName);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIAS);
        operation.get(ElytronDescriptionConstants.ALIAS).set(alias);

        ModelNode aliasNode = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        Assert.assertNotNull(aliasNode.get(ElytronDescriptionConstants.CREATION_DATE).asString());
        Assert.assertEquals(KeyStore.PrivateKeyEntry.class.getSimpleName(), aliasNode.get(ElytronDescriptionConstants.ENTRY_TYPE).asString());
        Assert.assertFalse(aliasNode.get(ElytronDescriptionConstants.CERTIFICATE).isDefined()); // chain defined, certificate should be blank

        List<ModelNode> chain = aliasNode.get(ElytronDescriptionConstants.CERTIFICATE_CHAIN).asList();
        Assert.assertEquals("OU=Elytron,O=Elytron,C=UK,ST=Elytron,CN=Firefly", chain.get(0).get(ElytronDescriptionConstants.SUBJECT).asString());
        Assert.assertEquals("O=Root Certificate Authority,1.2.840.113549.1.9.1=#1613656c7974726f6e4077696c64666c792e6f7267,C=UK,ST=Elytron,CN=Elytron CA", chain.get(1).get(ElytronDescriptionConstants.SUBJECT).asString());
    }

    @Test
    public void testLdapKeyStoreCopyRemoveAlias() throws Exception {
        ServiceName serviceName = Capabilities.KEY_STORE_RUNTIME_CAPABILITY.getCapabilityServiceName("LdapKeyStoreMaximal");
        LdapKeyStoreService ldapKeyStoreService = (LdapKeyStoreService) services.getContainer().getService(serviceName).getService();
        KeyStore keyStore = ldapKeyStoreService.getModifiableValue();
        Assert.assertNotNull(keyStore);

        Key key = keyStore.getKey("serenity", "Elytron".toCharArray());
        Certificate[] chain = keyStore.getCertificateChain("serenity");
        Assert.assertNotNull(key);
        Assert.assertNotNull(chain);
        Assert.assertEquals(1, keyStore.size());

        // create two copies
        keyStore.setKeyEntry("serenity1", key, "password1".toCharArray(), chain);
        keyStore.setKeyEntry("serenity2", key, "password2".toCharArray(), chain);
        Assert.assertNotNull(keyStore.getKey("serenity1", "password1".toCharArray()));
        Assert.assertNotNull(keyStore.getKey("serenity2", "password2".toCharArray()));
        Assert.assertEquals(3, keyStore.size());
        Assert.assertEquals(3, Collections.list(keyStore.aliases()).size());

        ModelNode operation = new ModelNode(); // check count of copies through subsystem
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("ldap-key-store", "LdapKeyStoreMaximal");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);
        Assert.assertEquals(3, assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT).asList().size());

        keyStore.deleteEntry("serenity1"); // remove through keystore operation
        Assert.assertNull(keyStore.getKey("serenity1", "password1".toCharArray()));
        Assert.assertEquals(2, keyStore.size());

        operation = new ModelNode(); // remove through subsystem operation
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("ldap-key-store", "LdapKeyStoreMaximal");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.REMOVE_ALIAS);
        operation.get(ElytronDescriptionConstants.ALIAS).set("serenity2");
        assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        Assert.assertNull(keyStore.getKey("serenity2", "password2".toCharArray()));
        Assert.assertEquals(1, keyStore.size());
    }

    private ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }
}
