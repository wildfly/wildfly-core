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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.openssl.MiscPEMGenerator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemWriter;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.Extension;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.ssl.X509RevocationTrustManager;
import org.wildfly.security.x500.cert.BasicConstraintsExtension;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public abstract class ElytronCommonTlsTestCase extends AbstractSubsystemTest {

    protected static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility csUtil = null;

    private static final char[] GENERATED_KEYSTORE_PASSWORD = "Elytron".toCharArray();
    protected static final X500Principal ISSUER_DN = new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA ");
    protected static final X500Principal OTHER_ISSUER_DN = new X500Principal("O= Other Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=WildFly, CN=WildFly CA ");
    private static final X500Principal FIREFLY_DN = new X500Principal("OU=Elytron, O=Elytron, C=UK, ST=Elytron, CN=Firefly");
    private static final X500Principal LOCALHOST_DN = new X500Principal("OU=Elytron, O=Elytron, C=CZ, ST=Elytron, CN=localhost");
    protected static final X500Principal MUNERASOFT_DN = new X500Principal("C=BR, ST=DF, O=MuneraSoft.io, CN=MuneraSoft.io Intermediate CA, emailAddress=contact@munerasoft.com");
    protected static final X500Principal NEW_DN = new X500Principal("O=Root Certificate Authority New, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA");
    protected static final String PROTOCOLS_SERVER = "enabledProtocolsServer";
    protected static final String PROTOCOLS_CLIENT = "enabledProtocolsClient";
    protected static final String NEGOTIATED_PROTOCOL = "negotiatedProtocol";

    protected static final String INIT_TEST_FILE = "/trust-manager-reload-test.truststore";
    protected static final String INIT_TEST_TRUSTSTORE = "myTS";
    protected static final String INIT_TEST_TRUSTMANAGER = "myTM";
    public static String disabledAlgorithms;


    public ElytronCommonTlsTestCase(final String mainSubsystemName, final Extension mainExtension) {
        super(mainSubsystemName, mainExtension);
    }
    protected abstract KernelServices getServices();

    protected static void setCSUtil(CredentialStoreUtility newCsUtil) {
        csUtil = newCsUtil;
    }

    protected static CredentialStoreUtility getCSUtil() {
        return csUtil;
    }

    private static KeyStore loadKeyStore() throws Exception{
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    protected static SelfSignedX509CertificateAndSigningKey createIssuer(X500Principal issuerDN) {
        return SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(issuerDN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA256withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
    }

    protected static KeyStore createTrustStore(SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey) throws Exception {
        KeyStore trustStore = loadKeyStore();

        X509Certificate issuerCertificate = issuerSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        trustStore.setCertificateEntry("mykey", issuerCertificate);

        return trustStore;
    }

    protected static KeyStore createFireflyKeyStore(SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey) throws Exception {
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
                .setSignatureAlgorithmName("SHA256withRSA")
                .setSigningKey(issuerSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(fireflyPublicKey)
                .setSerialNumber(new BigInteger("1"))
                .addExtension(new BasicConstraintsExtension(false, false, -1))
                .build();
        fireflyKeyStore.setKeyEntry("firefly", fireflySigningKey, GENERATED_KEYSTORE_PASSWORD, new X509Certificate[]{fireflyCertificate,issuerCertificate});

        return fireflyKeyStore;
    }

    protected static KeyStore createLocalhostKeyStore(SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey) throws Exception {
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
                .setSignatureAlgorithmName("SHA256withRSA")
                .setSigningKey(issuerSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(localhostPublicKey)
                .setSerialNumber(new BigInteger("3"))
                .addExtension(new BasicConstraintsExtension(false, false, -1))
                .build();
        localhostKeyStore.setKeyEntry("localhost", localhostSigningKey, GENERATED_KEYSTORE_PASSWORD, new X509Certificate[]{localhostCertificate,issuerCertificate});

        return localhostKeyStore;
    }

    protected static X509CRLHolder createCRL(SelfSignedX509CertificateAndSigningKey issuerCertificateAndSigningKey,
                                           String[] serialNumbers) throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        Calendar calendar = Calendar.getInstance();
        Date currentDate = calendar.getTime();
        calendar.add(Calendar.YEAR, 1);
        Date nextYear = calendar.getTime();
        calendar.add(Calendar.YEAR, -1);
        calendar.add(Calendar.SECOND, -30);
        Date revokeDate = calendar.getTime();

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(
                convertSunStyleToBCStyle(issuerCertificateAndSigningKey.getSelfSignedCertificate().getSubjectDN()),
                currentDate
        );

        for (String serialNumber : serialNumbers) {
            crlBuilder.addCRLEntry(
                    new BigInteger(serialNumber),
                    revokeDate,
                    CRLReason.unspecified
            );
        }

        return crlBuilder.setNextUpdate(nextYear).build(
                new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC")
                        .build(issuerCertificateAndSigningKey.getSigningKey())
        );

    }

    private static org.bouncycastle.asn1.x500.X500Name convertSunStyleToBCStyle(Principal dn) {
        String dnName = dn.getName();
        String[] dnComponents = dnName.split(", ");
        StringBuilder dnBuffer = new StringBuilder(dnName.length());

        dnBuffer.append(dnComponents[dnComponents.length-1]);
        for(int i = dnComponents.length-2; i >= 0; i--){
            dnBuffer.append(',');
            dnBuffer.append(dnComponents[i]);
        }

        return new X500Name(dnBuffer.toString());
    }

    protected static void createTemporaryCRLFile(X509CRLHolder crlHolder, File outputFile) throws Exception {
        try (PemWriter output = new PemWriter(new OutputStreamWriter(new FileOutputStream(outputFile)))){
            output.writeObject(new MiscPEMGenerator(crlHolder));
        }
    }

    protected static void createTemporaryKeyStoreFile(KeyStore keyStore, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            keyStore.store(fos, GENERATED_KEYSTORE_PASSWORD);
        }
    }

    protected static boolean isJDK14Plus() {
        return "14".equals(System.getProperty("java.specification.version"));
    }


    /**
     * Verifies accepted issuers are sent when CRLs are configured.
     * Verifies ELY-2057 (No acceptedIssuers sent when CRLs are configured) is resolved.
     */
    @Test
    public void testAcceptedIssuersWithCRLs() throws Throwable {
        ServiceName serviceName = ElytronCommonCapabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-crl");
        TrustManager trustManager = (TrustManager) getServices().getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManager);
        Assert.assertTrue(((X509TrustManager) trustManager).getAcceptedIssuers().length > 0);
    }

    @Test
    public void testOcspCrl() {
        ServiceName serviceName = ElytronCommonCapabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-ocsp-crl");
        TrustManager trustManager = (TrustManager) getServices().getContainer().getService(serviceName).getValue();
        MatcherAssert.assertThat(trustManager, CoreMatchers.instanceOf(X509RevocationTrustManager.class));
    }

    @Test
    public void testOcspSimple() {
        ServiceName serviceName = ElytronCommonCapabilities.TRUST_MANAGER_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-ocsp-simple");
        TrustManager trustManager = (TrustManager) getServices().getContainer().getService(serviceName).getValue();
        MatcherAssert.assertThat(trustManager, CoreMatchers.instanceOf(X509RevocationTrustManager.class));
    }

    protected SSLContext getSslContext(String contextName) {
        return getSslContext(contextName, true);
    }

    private SSLContext getSslContext(String contextName, boolean assertNotNull) {
        ServiceName serviceName = ElytronCommonCapabilities.SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(contextName);
        SSLContext sslContext = (SSLContext) getServices().getContainer().getService(serviceName).getValue();
        if (assertNotNull) {
            Assert.assertNotNull(sslContext);
        }
        return sslContext;
    }
}
