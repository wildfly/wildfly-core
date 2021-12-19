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

import mockit.Mock;
import mockit.MockUp;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
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

class TestEnvironment extends AdditionalInitialization {

    static final int LDAPS1_PORT = 11391;
    static final int LDAPS2_PORT = 11392;

    private static final String WORKING_DIRECTORY_LOCATION = "./target/test-classes/org/wildfly/extension/elytron";
    private static final char[] GENERATED_KEYSTORE_PASSWORD = "Elytron".toCharArray();
    private static final X500Principal ISSUER_DN = new X500Principal("O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA");
    private static final X500Principal LOCALHOST_DN = new X500Principal("OU=Elytron, O=Elytron, C=CZ, ST=Elytron, CN=localhost");

    private static KeyStore loadKeyStore() throws Exception{
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    private static SelfSignedX509CertificateAndSigningKey createIssuer() {
        return SelfSignedX509CertificateAndSigningKey.builder()
                .setDn(ISSUER_DN)
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA256withRSA")
                .addExtension(false, "BasicConstraints", "CA:true,pathlen:2147483647")
                .build();
    }

    private static KeyStore createTrustStore(SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey) throws Exception {
        KeyStore trustStore = loadKeyStore();

        X509Certificate issuerCertificate = issuerSelfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
        trustStore.setCertificateEntry("mykey", issuerCertificate);

        return trustStore;
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
                .setSignatureAlgorithmName("SHA256withRSA")
                .setSigningKey(issuerSelfSignedX509CertificateAndSigningKey.getSigningKey())
                .setPublicKey(localhostPublicKey)
                .setSerialNumber(new BigInteger("3"))
                .addExtension(new BasicConstraintsExtension(false, false, -1))
                .build();
        localhostKeyStore.setKeyEntry("localhost", localhostSigningKey, GENERATED_KEYSTORE_PASSWORD, new X509Certificate[]{localhostCertificate,issuerCertificate});

        return localhostKeyStore;
    }

    private static void createTemporaryKeyStoreFile(KeyStore keyStore, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            keyStore.store(fos, GENERATED_KEYSTORE_PASSWORD);
        }
    }

    public static void setUpKeyStores() throws Exception {
        File workingDir = new File(WORKING_DIRECTORY_LOCATION);
        if (workingDir.exists() == false) {
            workingDir.mkdirs();
        }

        SelfSignedX509CertificateAndSigningKey issuerSelfSignedX509CertificateAndSigningKey = createIssuer();
        File trustFile = new File(workingDir, "ca.truststore");
        KeyStore trustStore = createTrustStore(issuerSelfSignedX509CertificateAndSigningKey);
        File localhostFile = new File(workingDir, "localhost.keystore");
        KeyStore localhostKeyStore = createLocalhostKeyStore(issuerSelfSignedX509CertificateAndSigningKey);

        createTemporaryKeyStoreFile(trustStore, trustFile);
        createTemporaryKeyStoreFile(localhostKeyStore, localhostFile);
    }

    private final RunningMode runningMode;

    TestEnvironment() {
        this(RunningMode.NORMAL);
    }

    TestEnvironment(RunningMode runningMode) {
        this.runningMode = runningMode;
    }

    @Override
    protected RunningMode getRunningMode() {
        return runningMode;
    }

    @Override
    protected ControllerInitializer createControllerInitializer() {
        ControllerInitializer initializer = new ControllerInitializer();

        try {
            URL fsr = getClass().getResource("filesystem-realm-empty");
            if (fsr != null) emptyDirectory(new File(fsr.getFile()).toPath());
        } catch (Exception e) {
            throw new RuntimeException("Could ensure empty testing filesystem directory", e);
        }

        try {
            initializer.addPath("jboss.server.config.dir", getClass().getResource(".").getFile(), null);
            initializer.addPath("jboss.server.data.dir", "target", null);
        } catch (Exception e) {
            throw new RuntimeException("Could not create test config directory", e);
        }

        return initializer;
    }

    public static void startLdapService() {
        try {
            setUpKeyStores();
            LdapService.builder()
                    .setWorkingDir(new File("./target/apache-ds/working1"))
                    .createDirectoryService("TestService1")
                    .addPartition("Elytron", "dc=elytron,dc=wildfly,dc=org", 5, "uid")
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-schemas.ldif"))
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-data.ldif"))
                    .addTcpServer("Default TCP", "localhost", LDAPS1_PORT, "localhost.keystore", "Elytron")
                    .start();
            LdapService.builder()
                    .setWorkingDir(new File("./target/apache-ds/working2"))
                    .createDirectoryService("TestService2")
                    .addPartition("Elytron", "dc=elytron,dc=wildfly,dc=org", 5, "uid")
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-schemas.ldif"))
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-referred.ldif"))
                    .addTcpServer("Default TCP", "localhost", LDAPS2_PORT, "localhost.keystore", "Elytron")
                    .start();
        } catch (Exception e) {
            throw new RuntimeException("Could not start LDAP embedded server.", e);
        }
    }

    private void emptyDirectory(Path directory) throws IOException {
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

    // classloader obtaining mock to load classes from testsuite
    private static class ClassLoadingAttributeDefinitionsMock extends MockUp<ClassLoadingAttributeDefinitions> {
        @Mock
        static ClassLoader resolveClassLoader(String module) {
            return SaslTestCase.class.getClassLoader();
        }
    }

    static void mockCallerModuleClassloader() {
        new ClassLoadingAttributeDefinitionsMock();
    }

    static void activateService(KernelServices services, RuntimeCapability capability, String... dynamicNameElements) throws InterruptedException {
        ServiceName serviceName = capability.getCapabilityServiceName(dynamicNameElements);
        ServiceController<?> serviceController = services.getContainer().getService(serviceName);
        serviceController.setMode(ServiceController.Mode.ACTIVE);
        serviceController.awaitValue();
    }
}
