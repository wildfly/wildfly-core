/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import java.io.File;
import java.net.URL;
import java.security.KeyStore;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.wildfly.extension.elytron.common.ClassLoadingAttributeDefinitions;
import org.wildfly.extension.elytron.common.ElytronCommonLdapService;
import org.wildfly.extension.elytron.common.ElytronCommonTestEnvironment;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;

import mockit.Mock;
import mockit.MockUp;

class TestEnvironment extends ElytronCommonTestEnvironment {

    protected static final String WORKING_DIRECTORY_LOCATION = "./target/test-classes/org/wildfly/extension/elytron";

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

    TestEnvironment() {
        super();
    }

    TestEnvironment(RunningMode runningMode) {
        super(runningMode);
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
            ElytronCommonLdapService.getCommonBuilder()
                    .setWorkingDir(new File("./target/apache-ds/working1"))
                    .createDirectoryService("TestService1")
                    .addPartition("Elytron", "dc=elytron,dc=wildfly,dc=org", 5, "uid")
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-schemas.ldif"))
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-data.ldif"))
                    .addTcpServer(TestEnvironment.class, "Default TCP", "localhost", LDAPS1_PORT, "localhost.keystore", "Elytron")
                    .start();
            ElytronCommonLdapService.getCommonBuilder()
                    .setWorkingDir(new File("./target/apache-ds/working2"))
                    .createDirectoryService("TestService2")
                    .addPartition("Elytron", "dc=elytron,dc=wildfly,dc=org", 5, "uid")
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-schemas.ldif"))
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-referred.ldif"))
                    .addTcpServer(TestEnvironment.class, "Default TCP", "localhost", LDAPS2_PORT, "localhost.keystore", "Elytron")
                    .start();
        } catch (Exception e) {
            throw new RuntimeException("Could not start LDAP embedded server.", e);
        }
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
}
