/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.security.realm.token.test.util.JwtTestUtil.createRsaJwk;
import static org.wildfly.security.realm.token.test.util.JwtTestUtil.createTokenDispatcher;
import static org.wildfly.security.realm.token.test.util.JwtTestUtil.jwksToJson;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.security.AccessController;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;

import javax.net.ssl.SSLContext;

import javax.json.JsonObject;

import okhttp3.mockwebserver.MockWebServer;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.evidence.BearerTokenEvidence;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.realm.token.test.util.RsaJwk;
import org.wildfly.security.realm.token.test.util.JwtTestUtil;

/**
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class JwtSecurityRealmTestCase extends AbstractSubsystemBaseTest {

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static final String JWT_REALM_TEST = "jwt-realm-test.xml";
    private static final MockWebServer server = new MockWebServer();
    private static final String JKU_ALLOWED_VALUES_PROPERTY = "wildfly.elytron.jwt.allowed.jku.values.JwtRealm";

    private static KeyPair keyPair1;
    private static KeyPair keyPair2;

    private static RsaJwk jwk1;
    private static RsaJwk jwk2;
    private static String jwksResponse;

    public JwtSecurityRealmTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @BeforeClass
    public static void setUp() throws Exception {
        AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Security.insertProviderAt(wildFlyElytronProvider, 1));

        TestEnvironment.setUpKeyStores();

        keyPair1 = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        jwk1 = createRsaJwk(keyPair1, "1");

        keyPair2 = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        jwk2 = createRsaJwk(keyPair2, "2");

        JsonObject jwks = jwksToJson(jwk1, jwk2);
        jwksResponse = jwks.toString();

        server.setDispatcher(createTokenDispatcher(jwksResponse));
        server.start(50831);

        System.setProperty(JKU_ALLOWED_VALUES_PROPERTY, "https://localhost:50832 https://localhost:50831");
    }

    @AfterClass
    public static void cleanUp() throws IOException {
        System.clearProperty(JKU_ALLOWED_VALUES_PROPERTY);
        server.shutdown();
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource(JWT_REALM_TEST);
    }

    @Test
    public void testJwtRealmWithJkuValueAllowed() throws Exception {
        KernelServices services = createKernelServices();

        BearerTokenEvidence evidence = new BearerTokenEvidence(
                JwtTestUtil.createJwt(keyPair1, 60, -1, "1", new URI("https://localhost:50831")));

        SecurityRealm securityRealm = assertSecurityRealmNotNull(services, "JwtRealm");

        // token validation should succeed
        assertTrue(identityExists(securityRealm, evidence));
    }

    @Test
    public void testJwtRealmWithJkuValueNotAllowed() throws Exception {
        KernelServices services = createKernelServices();

        BearerTokenEvidence evidence = new BearerTokenEvidence(
                JwtTestUtil.createJwt(keyPair1, 60, -1, "1", new URI("https://localhost:50834")));

        SecurityRealm securityRealm = assertSecurityRealmNotNull(services, "JwtRealm");

        // token validation should fail
        assertFalse(identityExists(securityRealm, evidence));
    }

    @Test
    public void testAllowedJkuValuesNotConfigured() throws Exception {
        KernelServices services = createKernelServices();

        BearerTokenEvidence evidence = new BearerTokenEvidence(
                JwtTestUtil.createJwt(keyPair1, 60, -1, "1", new URI("https://localhost:50831")));

        SecurityRealm securityRealm = assertSecurityRealmNotNull(services, "JwtRealmWithoutAllowedJkuValues");

        // token validation should fail
        assertFalse(identityExists(securityRealm, evidence));
    }

    @Test
    public void testTokenWithoutJkuValue() throws Exception {
        KernelServices services = createKernelServicesWithPublicKeysConfigured();

        BearerTokenEvidence evidence1 = new BearerTokenEvidence(
                JwtTestUtil.createJwt(keyPair1, 60, -1, "1", null));
        BearerTokenEvidence evidence2 = new BearerTokenEvidence(
                JwtTestUtil.createJwt(keyPair2, 60, -1, "2", null));

        SecurityRealm securityRealm = assertSecurityRealmNotNull(services, "JwtRealm");

        // token validation should succeed
        assertTrue(identityExists(securityRealm, evidence1));
        assertTrue(identityExists(securityRealm, evidence2));
    }

    private SecurityRealm assertSecurityRealmNotNull(KernelServices services, String securityRealmName) {
        ServiceName serviceName = Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY.getCapabilityServiceName(securityRealmName);
        SecurityRealm securityRealm = (SecurityRealm) services.getContainer().getService(serviceName).getValue();
        assertNotNull(securityRealm);
        return securityRealm;
    }

    private void setSSLContext(KernelServices services, String sslContextName) {
        ServiceName serviceName = Capabilities.SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(sslContextName);
        SSLContext sslContext = (SSLContext) services.getContainer().getService(serviceName).getValue();
        assertNotNull(sslContext);
        server.useHttps(sslContext.getSocketFactory(), false);
    }

    private KernelServices createKernelServices() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(JWT_REALM_TEST).build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                fail(services.getBootError().toString());
            }
            fail("Failed to boot, no reason provided");
        }
        setSSLContext(services, "SslContext");
        return services;
    }

    private KernelServices createKernelServicesWithPublicKeysConfigured() throws Exception {
        KernelServices services = createKernelServices();

        SecurityRealm securityRealm = assertSecurityRealmNotNull(services, "JwtRealm");
        ModelNode keyMap = new ModelNode();
        keyMap.get("1").set(getPemStringFromPublicKey(keyPair1));
        keyMap.get("2").set(getPemStringFromPublicKey(keyPair2));

        ModelNode write = Util.getWriteAttributeOperation(getSecurityRealmAddress("JwtRealm"), "jwt.key-map", keyMap);
        ModelNode response = services.executeOperation(write);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        String realmWithConfiguredPublicKeys = services.getPersistedSubsystemXml();

        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXml(realmWithConfiguredPublicKeys).build();

        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                fail(services.getBootError().toString());
            }
            fail("Failed to boot, no reason provided");
        }
        setSSLContext(services, "SslContext");
        return services;
    }

    private boolean identityExists(SecurityRealm realm, Evidence evidence) throws RealmUnavailableException {
        RealmIdentity identity = realm.getRealmIdentity(evidence);
        assertNotNull(identity);
        return identity.exists();
    }

    private static PathAddress getSecurityRealmAddress(String securityRealmName) {
        return PathAddress.pathAddress(SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME).append("token-realm", securityRealmName);
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
