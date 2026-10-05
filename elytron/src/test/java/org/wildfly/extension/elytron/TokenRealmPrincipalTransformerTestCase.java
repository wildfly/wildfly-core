/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringWriter;
import java.security.AccessController;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.version.Stability;
import org.jboss.msc.service.ServiceName;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.evidence.BearerTokenEvidence;
import org.wildfly.security.realm.token.test.util.JwtTestUtil;

/**
 * Tests that the community stability {@code principal-transformer} attribute of {@code token-realm} is applied to
 * the principal obtained from the token's principal claim.
 */
public class TokenRealmPrincipalTransformerTestCase extends AbstractSubsystemTest {

    private static final String SUBSYSTEM_XML = "token-realm-principal-transformer.xml";
    private static final String PUBLIC_KEY_PLACEHOLDER = "PUBLIC_KEY_PLACEHOLDER";

    /** The {@code sub} claim {@link JwtTestUtil} puts into the tokens it creates. */
    private static final String PRINCIPAL_CLAIM_VALUE = "elytron@jboss.org";

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();

    private static KeyPair keyPair;

    public TokenRealmPrincipalTransformerTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension(), Stability.COMMUNITY);
    }

    @BeforeClass
    public static void setUp() throws Exception {
        AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Security.insertProviderAt(wildFlyElytronProvider, 1));
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    @Test
    public void testPrincipalIsTransformed() throws Exception {
        KernelServices services = createKernelServices();
        BearerTokenEvidence evidence = new BearerTokenEvidence(JwtTestUtil.createJwt(keyPair, 60, -1));
        SecurityRealm securityRealm = assertSecurityRealmNotNull(services, "JwtRealmWithPrincipalTransformer");
        RealmIdentity identity = securityRealm.getRealmIdentity(evidence);
        assertTrue(identity.exists());
        assertEquals(PRINCIPAL_CLAIM_VALUE.toUpperCase(), identity.getRealmIdentityPrincipal().getName());
    }

    @Test
    public void testPrincipalIsUnchangedWithoutTransformer() throws Exception {

        KernelServices services = createKernelServices();
        BearerTokenEvidence evidence = new BearerTokenEvidence(JwtTestUtil.createJwt(keyPair, 60, -1));
        SecurityRealm securityRealm = assertSecurityRealmNotNull(services, "JwtRealmWithoutPrincipalTransformer");
        RealmIdentity identity = securityRealm.getRealmIdentity(evidence);
        assertTrue(identity.exists());
        assertEquals(PRINCIPAL_CLAIM_VALUE, identity.getRealmIdentityPrincipal().getName());
    }

    private KernelServices createKernelServices() throws Exception {
        String subsystemXml = readResource(SUBSYSTEM_XML).replace(PUBLIC_KEY_PLACEHOLDER, getPemStringFromPublicKey(keyPair));
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment(Stability.COMMUNITY))
                .setSubsystemXml(subsystemXml).build();
        if (!services.isSuccessfulBoot()) {
            fail(services.getBootError() != null ? services.getBootError().toString() : "Failed to boot, no reason provided");
        }
        return services;
    }

    private SecurityRealm assertSecurityRealmNotNull(KernelServices services, String securityRealmName) {
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName(securityRealmName);
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        assertNotNull(securityRealm);
        return securityRealm;
    }

    private static String getPemStringFromPublicKey(KeyPair keyPair) throws Exception {
        PublicKey publicKey = keyPair.getPublic();
        StringWriter writer = new StringWriter();
        PemWriter pemWriter = new PemWriter(writer);
        pemWriter.writeObject(new PemObject("PUBLIC KEY", publicKey.getEncoded()));
        pemWriter.flush();
        pemWriter.close();
        return writer.toString();
    }
}
