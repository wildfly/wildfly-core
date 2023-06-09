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
package org.wildfly.extension.elytron.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.x500.cert.BasicConstraintsExtension;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.security.x500.cert.X509CertificateBuilder;

/**
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public abstract class ElytronCommonTestEnvironment extends AdditionalInitialization {

    protected static final int LDAPS1_PORT = 11391;
    protected static final int LDAPS2_PORT = 11392;

    protected static final char[] GENERATED_KEYSTORE_PASSWORD = "Elytron".toCharArray();
    protected static final X500Principal ISSUER_DN = new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA");
    protected static final X500Principal LOCALHOST_DN = new X500Principal("OU=Elytron, O=Elytron, C=CZ, ST=Elytron, CN=localhost");

    protected static KeyStore loadKeyStore() throws Exception{
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    protected static SelfSignedX509CertificateAndSigningKey createIssuer() {
        return SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(ISSUER_DN)
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

    protected static void createTemporaryKeyStoreFile(KeyStore keyStore, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            keyStore.store(fos, GENERATED_KEYSTORE_PASSWORD);
        }
    }

    protected final RunningMode runningMode;

    protected ElytronCommonTestEnvironment() {
        this(RunningMode.NORMAL);
    }

    protected ElytronCommonTestEnvironment(RunningMode runningMode) {
        this.runningMode = runningMode;
    }

    @Override
    protected RunningMode getRunningMode() {
        return runningMode;
    }

    @Override
    protected abstract ControllerInitializer createControllerInitializer();

    protected void emptyDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void activateService(KernelServices services, RuntimeCapability capability, String... dynamicNameElements) throws InterruptedException {
        ServiceName serviceName = capability.getCapabilityServiceName(dynamicNameElements);
        ServiceController<?> serviceController = services.getContainer().getService(serviceName);
        serviceController.setMode(ServiceController.Mode.ACTIVE);
        serviceController.awaitValue();
    }
}
