/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.wildfly.common.function.ExceptionFunction;

/** Signed status responses are supplied at the public session boundary; no sockets, DNS or global property changes. */
@RunWith(Parameterized.class)
public class OcspStaplingTrustManagerTestCase extends AbstractSubsystemTest {
    private static final PathAddress SUBSYSTEM = PathAddress.pathAddress("subsystem", "elytron");
    private static final PathAddress MANAGER = SUBSYSTEM.append("trust-manager", "tm");
    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();
    private static X509Certificate issuer;
    private static X509Certificate leaf;
    private static KeyPair issuerKeys;
    private static byte[] goodResponse;
    private static byte[] revokedResponse;
    private static byte[] crl;
    private final boolean early;
    private final AtomicReference<TrustManager> earlyManager = new AtomicReference<>();
    private KernelServices services;
    private File storeFile;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "early={0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[] {false}, new Object[] {true});
    }

    public OcspStaplingTrustManagerTestCase(boolean early) {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension(), Stability.PREVIEW);
        this.early = early;
    }

    @BeforeClass
    public static void certificatesAndResponses() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        issuerKeys = generator.generateKeyPair();
        KeyPair leafKeys = generator.generateKeyPair();
        Date from = new Date(System.currentTimeMillis() - 60000);
        Date until = new Date(System.currentTimeMillis() + 86400000);
        X500Name ca = new X500Name("CN=Stapling Test CA");
        issuer = new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(ca, BigInteger.ONE,
                from, until, ca, issuerKeys.getPublic()).addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .build(signer()));
        // Deliberately no AIA or CRL distribution points: missing status fails locally, without network access.
        leaf = new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(ca, BigInteger.TWO,
                from, until, new X500Name("CN=stapling.test"), leafKeys.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(false)).build(signer()));
        goodResponse = response(CertificateStatus.GOOD);
        revokedResponse = response(new RevokedStatus(from, CRLReason.keyCompromise));
        crl = new X509v2CRLBuilder(ca, from).setNextUpdate(until).build(signer()).getEncoded();
    }

    private static ContentSigner signer() throws Exception {
        return new JcaContentSignerBuilder("SHA256withRSA").setProvider(PROVIDER).build(issuerKeys.getPrivate());
    }

    private static byte[] response(CertificateStatus status) throws Exception {
        X509CertificateHolder ca = new X509CertificateHolder(issuer.getEncoded());
        var digest = new JcaDigestCalculatorProviderBuilder().setProvider(PROVIDER).build().get(CertificateID.HASH_SHA1);
        var builder = new JcaBasicOCSPRespBuilder(issuer.getPublicKey(), digest);
        builder.addResponse(new CertificateID(digest, ca, leaf.getSerialNumber()), status,
                new Date(System.currentTimeMillis() - 60000), new Date(System.currentTimeMillis() + 86400000), null);
        return new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL,
                builder.build(signer(), new X509CertificateHolder[] {ca}, new Date())).getEncoded();
    }

    @Before
    public void boot() throws Exception {
        storeFile = temporaryFolder.newFile("trust.jks");
        KeyStore store = KeyStore.getInstance("JKS");
        store.load(null, null);
        store.setCertificateEntry("issuer", issuer);
        try (FileOutputStream output = new FileOutputStream(storeFile)) {
            store.store(output, "password".toCharArray());
        }
        services = createKernelServicesBuilder(new TestEnvironment(Stability.PREVIEW) {
            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry registry, Resource root,
                    ManagementResourceRegistration registration, RuntimeCapabilityRegistry capabilities) {
                super.initializeExtraSubystemsAndModel(registry, root, registration, capabilities);
                registration.registerOperationHandler(new SimpleOperationDefinitionBuilder("obtain-early-manager",
                        NonResolvingResourceDescriptionResolver.INSTANCE).setPrivateEntry().build(), (context, operation) -> context.addStep((runtimeContext, runtimeOperation) -> {
                    assertNull(runtimeContext.getServiceRegistry(false).getService(Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("tm")));
                    @SuppressWarnings("unchecked")
                    ExceptionFunction<OperationContext, TrustManager, OperationFailedException> api = runtimeContext.getCapabilityRuntimeAPI(
                            Capabilities.TRUST_MANAGER_API_CAPABILITY, "tm", ExceptionFunction.class);
                    earlyManager.set(api.apply(runtimeContext));
                }, OperationContext.Stage.RUNTIME, true));
            }
        }).setSubsystemXml("<subsystem xmlns=\"urn:wildfly:elytron:preview:19.0\"/>").build();
        assertTrue(String.valueOf(services.getBootError()), services.isSuccessfulBoot());
    }

    @Test
    public void testGoodStaple() throws Exception {
        X509ExtendedTrustManager manager = manager(true, false, false);
        StapledSession session = check(manager, goodResponse);
        assertTrue("The provider must obtain the staple", session.reads > 0);
        ModelNode init = services.executeOperation(Util.createOperation("init", MANAGER));
        assertEquals(init.toString(), SUCCESS, init.get(OUTCOME).asString());
        check(serviceManager(), goodResponse);
    }

    @Test
    public void testRevokedStapleRejected() throws Exception {
        rejects(manager(true, false, false), revokedResponse);
    }

    @Test
    public void testFalseIgnoresStapleWithExistingOcspPolicy() throws Exception {
        StapledSession session = rejects(manager(false, true, false), goodResponse);
        assertEquals(0, session.reads);
    }

    @Test
    public void testDefaultDoesNotEnableRevocation() throws Exception {
        check(manager(null, false, false), revokedResponse);
    }

    @Test
    public void testFalseDoesNotEnableRevocation() throws Exception {
        check(manager(false, false, false), revokedResponse);
    }

    @Test
    public void testCrlReloadRetainsStapleAcceptance() throws Exception {
        X509ExtendedTrustManager manager = manager(true, true, true);
        check(manager, goodResponse);
        ModelNode reload = services.executeOperation(Util.createOperation("reload-certificate-revocation-list", MANAGER));
        assertEquals(reload.toString(), SUCCESS, reload.get(OUTCOME).asString());
        rejects(manager, revokedResponse);
    }

    private X509ExtendedTrustManager manager(Boolean accept, boolean ocsp, boolean withCrl) throws Exception {
        ModelNode add = Util.createAddOperation(MANAGER);
        add.get("algorithm").set("PKIX");
        add.get("key-store").set("store");
        if (accept != null) add.get("accept-ocsp-stapling").set(accept);
        if (ocsp) add.get("ocsp").setEmptyObject();
        if (withCrl) {
            File file = temporaryFolder.newFile("issuer.crl");
            Files.write(file.toPath(), crl);
            add.get("certificate-revocation-list", "path").set(file.getAbsolutePath());
        }
        return addManager(add);
    }

    private X509ExtendedTrustManager addManager(ModelNode add) throws Exception {
        ModelNode result = services.executeOperation(composite(add));
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
        X509ExtendedTrustManager manager = serviceManager();
        assertNotNull(manager);
        assertFalse(Arrays.asList(manager.getAcceptedIssuers()).isEmpty());
        if (early) assertSame(earlyManager.get(), manager);
        return manager;
    }

    private ModelNode composite(ModelNode add) {
        ModelNode store = Util.createAddOperation(SUBSYSTEM.append("key-store", "store"));
        store.get("type").set("JKS");
        store.get("path").set(storeFile.getAbsolutePath());
        store.get("credential-reference", "clear-text").set("password");
        ModelNode composite = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        composite.get("steps").add(store).add(add);
        if (early) composite.get("steps").add(Util.createEmptyOperation("obtain-early-manager", PathAddress.EMPTY_ADDRESS));
        return composite;
    }

    private X509ExtendedTrustManager serviceManager() throws Exception {
        return (X509ExtendedTrustManager) services.getContainer().getRequiredService(
                Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("tm")).awaitValue();
    }

    private static StapledSession check(X509ExtendedTrustManager manager, byte[] response) throws Exception {
        StapledSession session = new StapledSession(response);
        manager.checkServerTrusted(new X509Certificate[] {leaf, issuer}, "RSA", new HandshakeEngine(session));
        return session;
    }

    private static StapledSession rejects(X509ExtendedTrustManager manager, byte[] response) throws Exception {
        StapledSession session = new StapledSession(response);
        try {
            manager.checkServerTrusted(new X509Certificate[] {leaf, issuer}, "RSA", new HandshakeEngine(session));
            fail("Certificate validation must fail");
        } catch (CertificateException expected) {
            // Assert the public validation result, without depending on provider exception nesting.
        }
        return session;
    }

    private static final class HandshakeEngine extends SSLEngine {
        private final SSLSession session;
        private HandshakeEngine(SSLSession session) {
            this.session = session;
        }
        @Override
        public SSLSession getHandshakeSession() {
            return session;
        }
        @Override
        public SSLSession getSession() {
            return session;
        }
        @Override
        public SSLParameters getSSLParameters() {
            return new SSLParameters();
        }
        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }
        @Override
        public String[] getEnabledCipherSuites() {
            return new String[0];
        }
        @Override
        public String[] getSupportedProtocols() {
            return new String[] {"TLSv1.2"};
        }
        @Override
        public String[] getEnabledProtocols() {
            return getSupportedProtocols();
        }
        @Override
        public boolean getUseClientMode() {
            return true;
        }
        @Override
        public boolean getNeedClientAuth() {
            return false;
        }
        @Override
        public boolean getWantClientAuth() {
            return false;
        }
        @Override
        public boolean getEnableSessionCreation() {
            return false;
        }
        @Override
        public boolean isInboundDone() {
            return false;
        }
        @Override
        public boolean isOutboundDone() {
            return false;
        }
        @Override
        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            throw new UnsupportedOperationException();
        }
        @Override
        public SSLEngineResult wrap(ByteBuffer[] src, int offset, int length, ByteBuffer dst) {
            throw new UnsupportedOperationException();
        }
        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dst, int offset, int length) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Runnable getDelegatedTask() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void closeInbound() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void closeOutbound() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setEnabledCipherSuites(String[] suites) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setEnabledProtocols(String[] protocols) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void beginHandshake() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setUseClientMode(boolean mode) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setNeedClientAuth(boolean need) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setWantClientAuth(boolean want) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setEnableSessionCreation(boolean enabled) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StapledSession extends ExtendedSSLSession {
        private final List<byte[]> responses;
        private int reads;
        private StapledSession(byte[] response) {
            responses = Collections.singletonList(response);
        }
        @Override
        public List<byte[]> getStatusResponses() {
            reads++; return responses;
        }
        @Override
        public String[] getLocalSupportedSignatureAlgorithms() {
            return new String[] {"SHA256withRSA"};
        }
        @Override
        public String[] getPeerSupportedSignatureAlgorithms() {
            return getLocalSupportedSignatureAlgorithms();
        }
        @Override
        public String getProtocol() {
            return "TLSv1.2";
        }
        @Override
        public String getPeerHost() {
            return "stapling.test";
        }
        @Override
        public int getPeerPort() {
            return -1;
        }
        @Override
        public Certificate[] getPeerCertificates() {
            return new Certificate[] {leaf, issuer};
        }
        @Override
        public Certificate[] getLocalCertificates() {
            return null;
        }
        @Override
        public Principal getPeerPrincipal() {
            return leaf.getSubjectX500Principal();
        }
        @Override
        public Principal getLocalPrincipal() {
            return null;
        }
        @Override
        public String getCipherSuite() {
            return "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
        }
        @Override
        public byte[] getId() {
            return new byte[0];
        }
        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }
        @Override
        public long getCreationTime() {
            return 0;
        }
        @Override
        public long getLastAccessedTime() {
            return 0;
        }
        @Override
        public void invalidate() {

        }
        @Override
        public boolean isValid() {
            return true;
        }
        @Override
        public void putValue(String name, Object value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Object getValue(String name) {
            return null;
        }
        @Override
        public void removeValue(String name) {
            throw new UnsupportedOperationException();
        }
        @Override
        public String[] getValueNames() {
            return new String[0];
        }
        @Override
        public int getPacketBufferSize() {
            return 0;
        }
        @Override
        public int getApplicationBufferSize() {
            return 0;
        }
    }
}
