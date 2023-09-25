/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;

/**
 * Generates the jBossClient KeyStore and TrustStore needed for the manualmode tests
 * @author <a href="mailto:jucook@redhat.com">Justin Cook</a>
 */
public class GenerateJBossStores {
    private static final String WORKING_DIRECTORY_LOCATION = GenerateJBossStores.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "/ssl";
    private static final char[] GENERATED_KEYSTORE_PASSWORD = "clientPassword".toCharArray();

    private static KeyStore loadKeyStore() throws Exception{
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    private static SelfSignedX509CertificateAndSigningKey createIssuer() {
        X500Principal issuerDN = new X500Principal("CN=localhost, OU=Client Unit, O=JBoss, L=Pune, ST=Maharashtra, C=IN");

        return SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(issuerDN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA1withRSA")
                .setKeySize(1024)
                .build();
    }

    private static KeyStore createKeyStore(SelfSignedX509CertificateAndSigningKey selfSignedX509CertificateAndSigningKey) throws Exception {
        KeyStore keyStore = loadKeyStore();

        X509Certificate certificate = selfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        keyStore.setKeyEntry("clientalias", selfSignedX509CertificateAndSigningKey.getSigningKey(), GENERATED_KEYSTORE_PASSWORD, new X509Certificate[]{certificate});

        return keyStore;
    }

    private static KeyStore createTrustStore(SelfSignedX509CertificateAndSigningKey selfSignedX509CertificateAndSigningKey) throws Exception {
        KeyStore trustStore = loadKeyStore();

        X509Certificate certificate = selfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();

        trustStore.setKeyEntry("clientalias", selfSignedX509CertificateAndSigningKey.getSigningKey(), GENERATED_KEYSTORE_PASSWORD, new X509Certificate[]{certificate});
        trustStore.setCertificateEntry("jbossalias", certificate);

        return trustStore;
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

        File keyStoreFile = new File(workingDir, "jbossClient.keystore");
        File trustStoreFile = new File(workingDir, "jbossClient.truststore");

        SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey = createIssuer();

        KeyStore keyStore = createKeyStore(issuerSelfSignedX509CertificateAndSigningKey);
        KeyStore trustStore = createTrustStore(issuerSelfSignedX509CertificateAndSigningKey);

        createTemporaryKeyStoreFile(keyStore, keyStoreFile);
        createTemporaryKeyStoreFile(trustStore, trustStoreFile);
    }

    public static void main(String[] args) throws Exception {
        setUpKeyStores();
    }
}
