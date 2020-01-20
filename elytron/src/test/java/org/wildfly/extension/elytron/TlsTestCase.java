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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.AccessController;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.openssl.MiscPEMGenerator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemWriter;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.x500.cert.BasicConstraintsExtension;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class TlsTestCase extends AbstractSubsystemTest {

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility csUtil = null;

    private final int TESTING_PORT = 18201;

    private static final String WORKING_DIRECTORY_LOCATION = "./target/test-classes/org/wildfly/extension/elytron";
    private static final char[] GENERATED_KEYSTORE_PASSWORD = "Elytron".toCharArray();
    private static final X500Principal ISSUER_DN = new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA ");
    private static final X500Principal FIREFLY_DN = new X500Principal("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly");
    private static final X500Principal LOCALHOST_DN = new X500Principal("OU=Elytron, O=Elytron, C=CZ, ST=Elytron, CN=localhost");
    private static final X500Principal MUNERASOFT_DN = new X500Principal("C=BR, ST=DF, O=MuneraSoft.io, CN=MuneraSoft.io Intermediate CA, emailAddress=contact@munerasoft.com");
    private static final X500Principal NEW_DN = new X500Principal("O=Root Certificate Authority New, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA");
    private static final File TRUST_FILE = new File(WORKING_DIRECTORY_LOCATION, "ca.truststore");
    private static final File FIREFLY_FILE = new File(WORKING_DIRECTORY_LOCATION, "firefly.keystore");
    private static final File LOCALHOST_FILE = new File(WORKING_DIRECTORY_LOCATION, "localhost.keystore");
    private static final File CRL_FILE = new File(WORKING_DIRECTORY_LOCATION, "crl.pem");

    private static final String INIT_TEST_FILE = "/trust-manager-reload-test.truststore";
    private static final String INIT_TEST_TRUSTSTORE = "myTS";
    private static final String INIT_TEST_TRUSTMANAGER = "myTM";

    public TlsTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private KernelServices services = null;

    private static KeyStore loadKeyStore() throws Exception{
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    private static SelfSignedX509CertificateAndSigningKey createIssuer() {
        return SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(ISSUER_DN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA1withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
    }

    private static KeyStore createTrustStore(SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey) throws Exception {
        KeyStore trustStore = loadKeyStore();

        X509Certificate issuerCertificate = issuerSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        trustStore.setCertificateEntry("mykey", issuerCertificate);

        return trustStore;
    }

    private static KeyStore createFireflyKeyStore(SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair fireflyKeys = keyPairGenerator.generateKeyPair();
        PrivateKey fireflySigningKey = fireflyKeys.getPrivate();
        PublicKey fireflyPublicKey = fireflyKeys.getPublic();

        KeyStore fireflyKeyStore = loadKeyStore();
        X509Certificate issuerCertificate = issuerSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        fireflyKeyStore.setCertificateEntry("ca", issuerCertificate);

        X509Certificate fireflyCertificate = new X509CertificateBuilder()
                .setIssuerDn(ISSUER_DN)
                .setSubjectDn(FIREFLY_DN)
                .setSignatureAlgorithmName("SHA1withRSA")
                .setSigningKey(issuerSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(fireflyPublicKey)
                .setSerialNumber(new BigInteger("1"))
                .addExtension(new BasicConstraintsExtension(false, false, -1))
                .build();
        fireflyKeyStore.setKeyEntry("firefly", fireflySigningKey, GENERATED_KEYSTORE_PASSWORD, new X509Certificate[]{fireflyCertificate,issuerCertificate});

        return fireflyKeyStore;
    }

    private static KeyStore createLocalhostKeyStore(SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair localhostKeys = keyPairGenerator.generateKeyPair();
        PrivateKey localhostSigningKey = localhostKeys.getPrivate();
        PublicKey localhostPublicKey = localhostKeys.getPublic();

        KeyStore localhostKeyStore = loadKeyStore();

        X509Certificate issuerCertificate = issuerSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        localhostKeyStore.setCertificateEntry("ca", issuerCertificate);

        X509Certificate localhostCertificate = new X509CertificateBuilder()
                .setIssuerDn(ISSUER_DN)
                .setSubjectDn(LOCALHOST_DN)
                .setSignatureAlgorithmName("SHA1withRSA")
                .setSigningKey(issuerSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(localhostPublicKey)
                .setSerialNumber(new BigInteger("3"))
                .addExtension(new BasicConstraintsExtension(false, false, -1))
                .build();
        localhostKeyStore.setKeyEntry("localhost", localhostSigningKey, GENERATED_KEYSTORE_PASSWORD, new X509Certificate[]{localhostCertificate,issuerCertificate});

        return localhostKeyStore;
    }

    private static X509CRLHolder createCRL() throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        SelfSignedX509CertificateAndSigningKey muneraSelfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(MUNERASOFT_DN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA256withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
        X509Certificate muneraCertificate = muneraSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();

        Calendar calendar = Calendar.getInstance();
        Date currentDate = calendar.getTime();
        calendar.add(Calendar.YEAR, 1);
        Date nextYear = calendar.getTime();
        calendar.add(Calendar.YEAR, -1);
        calendar.add(Calendar.SECOND, -30);
        Date revokeDate = calendar.getTime();

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(
                new X500Name(MUNERASOFT_DN.getName()),
                currentDate
        );
        crlBuilder.addExtension(
                Extension.authorityKeyIdentifier, false, new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(muneraCertificate.getPublicKey())
        );
        crlBuilder.addExtension(
                Extension.cRLNumber, false, new CRLNumber(BigInteger.valueOf(4110))
        );
        crlBuilder.addCRLEntry(
                new BigInteger("1005"),
                revokeDate,
                CRLReason.unspecified
        );
        crlBuilder.addCRLEntry(
                new BigInteger("1006"),
                revokeDate,
                CRLReason.unspecified
        );
        return crlBuilder.setNextUpdate(nextYear).build(
                new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC")
                        .build(muneraSelfSignedX509CertificateAndSigningKey.getSigningKey())
        );
    }

    private static void createTemporaryCRLFile(X509CRLHolder crlHolder, File outputFile) throws Exception {
        try (PemWriter output = new PemWriter(new OutputStreamWriter(new FileOutputStream(outputFile)))){
            output.writeObject(new MiscPEMGenerator(crlHolder));
        }
    }

    private static void createTemporaryKeyStoreFile(KeyStore keyStore, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            keyStore.store(fos, GENERATED_KEYSTORE_PASSWORD);
        }
    }

    private static void setUpKeyStores() throws Exception {
        File workingDir = new File(WORKING_DIRECTORY_LOCATION);
        if (workingDir.exists() == false) {
            workingDir.mkdirs();
        }

        SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey = createIssuer();
        KeyStore trustStore = createTrustStore(issuerSelfSignedX509CertificateAndSigningKey);

        KeyStore fireflyKeyStore = createFireflyKeyStore(issuerSelfSignedX509CertificateAndSigningKey);
        KeyStore localhostKeyStore = createLocalhostKeyStore(issuerSelfSignedX509CertificateAndSigningKey);

        createTemporaryKeyStoreFile(trustStore, TRUST_FILE);
        createTemporaryKeyStoreFile(fireflyKeyStore, FIREFLY_FILE);
        createTemporaryKeyStoreFile(localhostKeyStore, LOCALHOST_FILE);

        X509CRLHolder crlHolder = createCRL();
        createTemporaryCRLFile(crlHolder, CRL_FILE);
    }

    private static void deleteKeyStoreFiles() {
        File[] testFiles = {
                TRUST_FILE,
                FIREFLY_FILE,
                LOCALHOST_FILE,
                CRL_FILE
        };
        for (File file : testFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
    }


    @BeforeClass
    public static void noJDK14Plus() {
        Assume.assumeFalse("Avoiding JDK 14 due to https://issues.jboss.org/browse/WFCORE-4532", "14".equals(System.getProperty("java.specification.version")));
    }

    private static boolean isJDK14Plus() {
        return "14".equals(System.getProperty("java.specification.version"));
    }

    @BeforeClass
    public static void initTests() throws Exception {
        if (isJDK14Plus()) return; // TODO: remove this line once WFCORE-4532 is fixed
        setUpKeyStores();
        AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Security.insertProviderAt(wildFlyElytronProvider, 1));
        csUtil = new CredentialStoreUtility("target/tlstest.keystore");
        csUtil.addEntry("the-key-alias", "Elytron");
        csUtil.addEntry("master-password-alias", "Elytron");
    }

    @AfterClass
    public static void cleanUpTests() {
        if (isJDK14Plus()) return; // TODO: remove this line once WFCORE-4532 is fixed
        deleteKeyStoreFiles();
        csUtil.cleanUp();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            Security.removeProvider(wildFlyElytronProvider.getName());
            return null;
        });
    }

    @Before
    public void prepare() throws Throwable {
        if (services != null) return;
        String subsystemXml;
        if (JdkUtils.isIbmJdk()) {
            subsystemXml = "tls-ibm.xml";
        } else {
            subsystemXml = JdkUtils.getJavaSpecVersion() <= 12 ? "tls-sun.xml" : "tls-oracle13plus.xml";
        }
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(subsystemXml).build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }
    }


    @Test
    public void testSslServiceNoAuth() throws Throwable {
        testCommunication("ServerSslContextNoAuth", "ClientSslContextNoAuth", false, "OU=Elytron,O=Elytron,C=CZ,ST=Elytron,CN=localhost", null);
    }

    @Test
    public void testSslServiceNoAuth_Default() throws Throwable {
        testCommunication("ServerSslContextNoAuth", "ClientSslContextNoAuth", true, "OU=Elytron,O=Elytron,C=CZ,ST=Elytron,CN=localhost", null);
    }

    @Test
    public void testSslServiceAuth() throws Throwable {
        testCommunication("ServerSslContextAuth", "ClientSslContextAuth", false, "OU=Elytron,O=Elytron,C=CZ,ST=Elytron,CN=localhost", "OU=Elytron,O=Elytron,C=UK,ST=Elytron,CN=Firefly");
    }

    @Test
    public void testSslServiceAuthTLS13() throws Throwable {
        Assume.assumeTrue("Skipping testSslServiceAuthTLS13, test is not being run on JDK 11.",
                System.getProperty("java.specification.version").equals("11"));
        testCommunication("ServerSslContextTLS13", "ClientSslContextTLS13", false, "OU=Elytron,O=Elytron,C=CZ,ST=Elytron,CN=localhost",
                "OU=Elytron,O=Elytron,C=UK,ST=Elytron,CN=Firefly", "TLS_AES_256_GCM_SHA384", true);
    }

    @Test
    public void testSslServiceAuthProtocolMismatch() throws Throwable {
        Assume.assumeTrue("Skipping testSslServiceAuthProtocolMismatch, test is not being run on JDK 11.",
                System.getProperty("java.specification.version").equals("11"));
        try {
            testCommunication("ServerSslContextTLS12Only", "ClientSslContextTLS13Only", false, "",
                    "", "");
            fail("Expected SSLHandshakeException not thrown");
        } catch (SSLHandshakeException expected) {
        }
    }

    @Test
    public void testSslServiceAuthCipherSuiteMismatch() throws Throwable {
        Assume.assumeTrue("Skipping testSslServiceAuthCipherSuiteMismatch, test is not being run on JDK 11.",
                System.getProperty("java.specification.version").equals("11"));
        try {
            testCommunication("ServerSslContextTLS13Only", "ClientSslContextTLS13Only", false, "",
                    "", "");
            fail("Expected SSLHandshakeException not thrown");
        } catch (SSLHandshakeException expected) {
        }
    }

    @Test(expected = SSLHandshakeException.class)
    public void testSslServiceAuthRequiredButNotProvided() throws Throwable {
        testCommunication("ServerSslContextAuth", "ClientSslContextNoAuth", false, "OU=Elytron,O=Elytron,C=UK,ST=Elytron,CN=Firefly", "");
    }

    @Test
    public void testProviderTrustManager() throws Throwable {
        ServiceName serviceName = Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("ProviderTrustManager");
        TrustManager trustManager = (TrustManager) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManager);
    }

    @Test
    public void testRevocationLists() throws Throwable {
        ServiceName serviceName = Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-crl");
        TrustManager trustManager = (TrustManager) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManager);

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.TRUST_MANAGER, "trust-with-crl");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.RELOAD_CERTIFICATE_REVOCATION_LIST);
        Assert.assertTrue(services.executeOperation(operation).get(OUTCOME).asString().equals(SUCCESS));
    }

    @Test
    public void testRevocationListsDp() throws Throwable {
        ServiceName serviceName = Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-crl-dp");
        TrustManager trustManager = (TrustManager) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManager);

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.TRUST_MANAGER, "trust-with-crl-dp");
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.RELOAD_CERTIFICATE_REVOCATION_LIST);
        Assert.assertTrue(services.executeOperation(operation).get(OUTCOME).asString().equals(FAILED)); // not realoadable
    }

    @Test
    public void testReloadTrustManager() throws Throwable {
        Path resources = Paths.get(TlsTestCase.class.getResource(".").toURI());
        Files.copy(Paths.get(TRUST_FILE.toString()), Paths.get(WORKING_DIRECTORY_LOCATION + INIT_TEST_FILE), StandardCopyOption.REPLACE_EXISTING);

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.KEY_STORE, INIT_TEST_TRUSTSTORE);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.PATH).set(resources + INIT_TEST_FILE);
        operation.get(ElytronDescriptionConstants.TYPE).set("JKS");
        operation.get(CredentialReference.CREDENTIAL_REFERENCE).get(CredentialReference.CLEAR_TEXT).set("Elytron");
        Assert.assertEquals(services.executeOperation(operation).get(OUTCOME).asString(), SUCCESS);

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.TRUST_MANAGER, INIT_TEST_TRUSTMANAGER);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.KEY_STORE).set(INIT_TEST_TRUSTSTORE);
        Assert.assertEquals(services.executeOperation(operation).get(OUTCOME).asString(), SUCCESS);

        ServiceName serviceName = Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName(INIT_TEST_TRUSTMANAGER);
        X509ExtendedTrustManager trustManager = (X509ExtendedTrustManager) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManager);
        Assert.assertEquals(trustManager.getAcceptedIssuers().length, 1);

        X509Certificate originalFoundDN = trustManager.getAcceptedIssuers()[0];
        Assert.assertEquals(originalFoundDN.getIssuerX500Principal(), ISSUER_DN);

        // Update the trust store certificate
        SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(NEW_DN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA1withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
        KeyStore trustStore = createTrustStore(issuerSelfSignedX509CertificateAndSigningKey);
        createTemporaryKeyStoreFile(trustStore, new File(WORKING_DIRECTORY_LOCATION + INIT_TEST_FILE));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.KEY_STORE, INIT_TEST_TRUSTSTORE);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.LOAD);
        Assert.assertEquals(services.executeOperation(operation).get(OUTCOME).asString(), SUCCESS);

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.TRUST_MANAGER, INIT_TEST_TRUSTMANAGER);
        operation.get(ClientConstants.OP).set(ElytronDescriptionConstants.INIT);
        Assert.assertEquals(services.executeOperation(operation).get(OUTCOME).asString(), SUCCESS);

        Assert.assertEquals(trustManager.getAcceptedIssuers().length, 1);

        // See if the trust manager contains the new certificate
        X509Certificate newFoundDN = trustManager.getAcceptedIssuers()[0];
        Assert.assertEquals(newFoundDN.getIssuerX500Principal(), NEW_DN);

        Files.delete(Paths.get(WORKING_DIRECTORY_LOCATION + INIT_TEST_FILE));
    }

    @Test
    public void testOcspCrl() {
        ServiceName serviceName = Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-ocsp-crl");
        TrustManager trustManager = (TrustManager) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManager);
    }

    @Test
    public void testOcspSimple() {
        ServiceName serviceName = Capabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-ocsp-simple");
        TrustManager trustManager = (TrustManager) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManager);
    }

    private SSLContext getSslContext(String contextName) {
        ServiceName serviceName = Capabilities.SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(contextName);
        SSLContext sslContext = (SSLContext) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(sslContext);
        return sslContext;
    }

    private void testCommunication(String serverContextName, String clientContextName, boolean defaultClient, String expectedServerPrincipal, String expectedClientPrincipal) throws Throwable {
        testCommunication(serverContextName, clientContextName, defaultClient, expectedServerPrincipal, expectedClientPrincipal, null);
    }

    private void testCommunication(String serverContextName, String clientContextName, boolean defaultClient, String expectedServerPrincipal, String expectedClientPrincipal, String expectedCipherSuite) throws Throwable {
        testCommunication(serverContextName, clientContextName, defaultClient, expectedServerPrincipal, expectedClientPrincipal, expectedCipherSuite, false);
    }

    private void testCommunication(String serverContextName, String clientContextName, boolean defaultClient, String expectedServerPrincipal, String expectedClientPrincipal, String expectedCipherSuite, boolean tls13Test) throws Throwable {
        boolean testSessions = ! System.getProperty("java.specification.version").equals("11"); // session IDs are essentially obsolete in TLSv1.3
        SSLContext serverContext = getSslContext(serverContextName);
        SSLContext clientContext = defaultClient ? SSLContext.getDefault() : getSslContext(clientContextName);

        ServerSocket listeningSocket = serverContext.getServerSocketFactory().createServerSocket();
        listeningSocket.bind(new InetSocketAddress("localhost", TESTING_PORT));
        SSLSocket clientSocket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", TESTING_PORT);
        SSLSocket serverSocket = (SSLSocket) listeningSocket.accept();

        ExecutorService serverExecutorService = Executors.newSingleThreadExecutor();
        Future<byte[]> serverFuture = serverExecutorService.submit(() -> {
            try {
                byte[] received = new byte[2];
                serverSocket.getInputStream().read(received);
                serverSocket.getOutputStream().write(new byte[]{0x56, 0x78});

                if (expectedClientPrincipal != null) {
                    Assert.assertEquals(expectedClientPrincipal, serverSocket.getSession().getPeerPrincipal().getName());
                }

                return received;
            } catch (Exception e) {
                throw new RuntimeException("Server exception", e);
            }
        });

        ExecutorService clientExecutorService = Executors.newSingleThreadExecutor();
        Future<byte[]> clientFuture = clientExecutorService.submit(() -> {
            try {
                byte[] received = new byte[2];
                clientSocket.getOutputStream().write(new byte[]{0x12, 0x34});
                clientSocket.getInputStream().read(received);

                if (expectedServerPrincipal != null) {
                    Assert.assertEquals(expectedServerPrincipal, clientSocket.getSession().getPeerPrincipal().getName());
                }

                return received;
            } catch (Exception e) {
                throw new RuntimeException("Client exception", e);
            }
        });

        try {
            Assert.assertArrayEquals(new byte[]{0x12, 0x34}, serverFuture.get());
            Assert.assertArrayEquals(new byte[]{0x56, 0x78}, clientFuture.get());
            if (testSessions) {
                testSessionsReading(serverContextName, clientContextName, expectedServerPrincipal, expectedClientPrincipal);
            }
            if (expectedCipherSuite != null) {
                Assert.assertEquals(expectedCipherSuite, serverSocket.getSession().getCipherSuite());
                Assert.assertEquals(expectedCipherSuite, clientSocket.getSession().getCipherSuite());
            }
            if (tls13Test) {
                Assert.assertEquals("TLSv1.3", serverSocket.getSession().getProtocol());
                Assert.assertEquals("TLSv1.3", clientSocket.getSession().getProtocol());
            } else {
                Assert.assertFalse(serverSocket.getSession().getProtocol().equals("TLSv1.3")); // since TLS 1.3 is not enabled by default (WFCORE-4789)
                Assert.assertFalse(clientSocket.getSession().getProtocol().equals("TLSv1.3")); // since TLS 1.3 is not enabled by default
            }
        } catch (ExecutionException e) {
            if (e.getCause() != null && e.getCause() instanceof RuntimeException && e.getCause().getCause() != null) {
                throw e.getCause().getCause(); // unpack
            } else {
                throw e;
            }
        } finally {
            serverSocket.close();
            clientSocket.close();
            listeningSocket.close();
        }
    }

    private void testSessionsReading(String serverContextName, String clientContextName, String expectedServerPrincipal, String expectedClientPrincipal) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, serverContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.ACTIVE_SESSION_COUNT);
        Assert.assertEquals("active session count", 1, services.executeOperation(operation).get(ClientConstants.RESULT).asInt());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, serverContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_CHILDREN_NAMES_OPERATION);
        operation.get(ClientConstants.CHILD_TYPE).set(ElytronDescriptionConstants.SSL_SESSION);
        List<ModelNode> sessions = services.executeOperation(operation).get(ClientConstants.RESULT).asList();
        Assert.assertEquals("session count in list", 1, sessions.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, serverContextName).add(ElytronDescriptionConstants.SSL_SESSION, sessions.get(0).asString());
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.PEER_CERTIFICATES);
        ModelNode result = services.executeOperation(operation).get(ClientConstants.RESULT);
        System.out.println("server's peer certificates:");
        System.out.println(result);
        if (expectedClientPrincipal == null) {
            Assert.assertFalse(result.get(0).get(ElytronDescriptionConstants.SUBJECT).isDefined());
        } else {
            Assert.assertEquals(expectedClientPrincipal, result.get(0).get(ElytronDescriptionConstants.SUBJECT).asString());
        }

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, clientContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.ACTIVE_SESSION_COUNT);
        Assert.assertEquals("active session count", 1, services.executeOperation(operation).get(ClientConstants.RESULT).asInt());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, clientContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_CHILDREN_NAMES_OPERATION);
        operation.get(ClientConstants.CHILD_TYPE).set(ElytronDescriptionConstants.SSL_SESSION);
        sessions = services.executeOperation(operation).get(ClientConstants.RESULT).asList();
        Assert.assertEquals("session count in list", 1, sessions.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, clientContextName).add(ElytronDescriptionConstants.SSL_SESSION, sessions.get(0).asString());
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.PEER_CERTIFICATES);
        result = services.executeOperation(operation).get(ClientConstants.RESULT);
        System.out.println("client's peer certificates:");
        System.out.println(result);
        if (expectedServerPrincipal == null) {
            Assert.assertFalse(result.get(0).get(ElytronDescriptionConstants.SUBJECT).isDefined());
        } else {
            Assert.assertEquals(expectedServerPrincipal, result.get(0).get(ElytronDescriptionConstants.SUBJECT).asString());
        }
    }
}
