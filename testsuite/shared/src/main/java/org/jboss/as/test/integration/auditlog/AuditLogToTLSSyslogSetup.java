/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KEYSTORE_PASSWORD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KEYSTORE_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TLS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.syslogserver.TLSSyslogServerConfig;
import org.jboss.dmr.ModelNode;
import org.productivity.java.syslog4j.server.SyslogServerConfigIF;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;


/**
 * {@link org.wildfly.core.testrunner.ServerSetupTask} implementation which configures syslog server and
 * auditlog-to-syslog handler for this test. It creates key material in a temporary folder in addition to actions
 * described in the parent class.
 *
 * @author Josef Cacek
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class AuditLogToTLSSyslogSetup extends AuditLogToSyslogSetup {

    private static final File WORK_DIR = new File("audit-workdir");

    public static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, "server.keystore");
    public static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, "server.truststore");
    public static final File CLIENT_KEYSTORE_FILE = new File(WORK_DIR, "client.keystore");
    public static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, "client.truststore");

    private static String PASSWORD = "123456";

    private static final String STANDALONE_KEYSTORE_DIRECTORY = "./target/test-classes/org/jboss/as/test/integration/auditlog";
    private static final String MANUALMODE_KEYSTORE_DIRECTORY = "./target/test-classes/org/jboss/as/test/manualmode/auditlog";
    private static final char[] KEYSTORE_CREATION_PASSWORD = "123456".toCharArray();

    private static final File STANDALONE_CLIENT_KEY_FILE = new File(STANDALONE_KEYSTORE_DIRECTORY, "client.keystore");
    private static final File STANDALONE_CLIENT_TRUST_FILE = new File(STANDALONE_KEYSTORE_DIRECTORY, "client.truststore");
    private static final File STANDALONE_SERVER_KEY_FILE = new File(STANDALONE_KEYSTORE_DIRECTORY, "server.keystore");
    private static final File STANDALONE_SERVER_TRUST_FILE = new File(STANDALONE_KEYSTORE_DIRECTORY, "server.truststore");

    private static final File MANUALMODE_CLIENT_KEY_FILE = new File(MANUALMODE_KEYSTORE_DIRECTORY, "client.keystore");
    private static final File MANUALMODE_CLIENT_TRUST_FILE = new File(MANUALMODE_KEYSTORE_DIRECTORY, "client.truststore");
    private static final File MANUALMODE_SERVER_KEY_FILE = new File(MANUALMODE_KEYSTORE_DIRECTORY, "server.keystore");
    private static final File MANUALMODE_SERVER_TRUST_FILE = new File(MANUALMODE_KEYSTORE_DIRECTORY, "server.truststore");

    private KeyStore serverKeyStore = null;
    private KeyStore clientKeyStore = null;
    private KeyStore serverTrustStore = null;
    private KeyStore clientTrustStore = null;

    private void createKeyStoreTrustStore(KeyStore keyStore, KeyStore trustStore, String DN, String alias) throws Exception {
        X500Principal principal = new X500Principal(DN);

        SelfSignedX509CertificateAndSigningKey selfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA256withRSA")
                .setDn(principal)
                .setKeySize(2048)
                .build();
        X509Certificate certificate = selfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();

        keyStore.setKeyEntry(alias, selfSignedX509CertificateAndSigningKey.getSigningKey(), KEYSTORE_CREATION_PASSWORD, new X509Certificate[]{certificate});
        trustStore.setCertificateEntry(alias, certificate);
    }

    private static KeyStore loadKeyStore() throws Exception{
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    private static void createTemporaryKeyStoreFile(KeyStore keyStore, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            keyStore.store(fos, KEYSTORE_CREATION_PASSWORD);
        }
    }

    private void createStandaloneKeyStoresFiles() throws Exception {
        File workingDir = new File(STANDALONE_KEYSTORE_DIRECTORY);
        if (workingDir.exists() == false) {
            workingDir.mkdirs();
        }

        createTemporaryKeyStoreFile(clientKeyStore, STANDALONE_CLIENT_KEY_FILE);
        createTemporaryKeyStoreFile(clientTrustStore, STANDALONE_CLIENT_TRUST_FILE);
        createTemporaryKeyStoreFile(serverKeyStore, STANDALONE_SERVER_KEY_FILE);
        createTemporaryKeyStoreFile(serverTrustStore, STANDALONE_SERVER_TRUST_FILE);
    }

    private void createManualModeKeyStoresFiles() throws Exception {
        File workingDir = new File(MANUALMODE_KEYSTORE_DIRECTORY);
        if (workingDir.exists() == false) {
            workingDir.mkdirs();
        }

        createTemporaryKeyStoreFile(clientKeyStore, MANUALMODE_CLIENT_KEY_FILE);
        createTemporaryKeyStoreFile(clientTrustStore, MANUALMODE_CLIENT_TRUST_FILE);
        createTemporaryKeyStoreFile(serverKeyStore, MANUALMODE_SERVER_KEY_FILE);
        createTemporaryKeyStoreFile(serverTrustStore, MANUALMODE_SERVER_TRUST_FILE);
    }

    private void beforeTest() throws Exception {
        clientKeyStore = loadKeyStore();
        clientTrustStore = loadKeyStore();
        serverKeyStore = loadKeyStore();
        serverTrustStore = loadKeyStore();

        createKeyStoreTrustStore(clientKeyStore, serverTrustStore, "CN=client", "cn=client");
        createKeyStoreTrustStore(serverKeyStore, clientTrustStore, "CN=server", "cn=server");

        createStandaloneKeyStoresFiles();

        createKeyStoreTrustStore(clientKeyStore, serverTrustStore, "CN=JBAS", "client");
        createKeyStoreTrustStore(serverKeyStore, clientTrustStore, "CN=Syslog", "server");

        createManualModeKeyStoresFiles();
    }

    private static void deleteKeyStoreFiles() {
        File[] testFiles = {
                STANDALONE_CLIENT_KEY_FILE,
                STANDALONE_CLIENT_TRUST_FILE,
                STANDALONE_SERVER_KEY_FILE,
                STANDALONE_SERVER_TRUST_FILE,
                MANUALMODE_CLIENT_KEY_FILE,
                MANUALMODE_CLIENT_TRUST_FILE,
                MANUALMODE_SERVER_KEY_FILE,
                MANUALMODE_SERVER_TRUST_FILE
        };
        for (File file : testFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    protected String getSyslogProtocol() {
        return TLS;
    }

    @Override
    protected ModelNode addAuditlogSyslogProtocol(PathAddress syslogHandlerAddress) {
        ModelNode op = Util.createAddOperation(syslogHandlerAddress.append(PROTOCOL, TLS));
        op.get("message-transfer").set("OCTET_COUNTING");
        return op;
    }

    @Override
    protected SyslogServerConfigIF getSyslogConfig() {
        TLSSyslogServerConfig config = new TLSSyslogServerConfig();
        config.setKeyStore(SERVER_KEYSTORE_FILE.getAbsolutePath());
        config.setKeyStorePassword(PASSWORD);
        config.setTrustStore(SERVER_TRUSTSTORE_FILE.getAbsolutePath());
        config.setTrustStorePassword(PASSWORD);
        return config;
    }

    @Override
    protected List<ModelNode> addProtocolSettings(PathAddress syslogHandlerAddress) {
        PathAddress protocolAddress = syslogHandlerAddress.append(PROTOCOL, TLS);
        List<ModelNode> ops = new ArrayList<ModelNode>();
        ModelNode op1 = Util.createAddOperation(protocolAddress.append(AUTHENTICATION, TRUSTSTORE));
        op1.get(KEYSTORE_PATH).set(CLIENT_TRUSTSTORE_FILE.getAbsolutePath());
        op1.get(KEYSTORE_PASSWORD).set(PASSWORD);
        ops.add(op1);
        ModelNode op2 = Util.createAddOperation(protocolAddress.append(AUTHENTICATION, CLIENT_CERT_STORE));
        op2.get(KEYSTORE_PATH).set(CLIENT_KEYSTORE_FILE.getAbsolutePath());
        op2.get(KEYSTORE_PASSWORD).set(PASSWORD);
        ops.add(op2);
        return ops;
    }

    /**
     * Creates {@link #WORK_DIR} folder and copies keystores and truststores to it. Then calls parent
     * {@link org.wildfly.core.testrunner.ServerSetupTask#setup(org.wildfly.core.testrunner.ManagementClient)} method.
     *
     * @see
     * org.jboss.as.test.integration.auditlog.AuditLogToSyslogSetup#setup(org.jboss.as.arquillian.container.ManagementClient,
     * java.lang.String)
     */
    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        FileUtils.deleteDirectory(WORK_DIR);
        WORK_DIR.mkdirs();
        beforeTest();
        createTestResource(SERVER_KEYSTORE_FILE);
        createTestResource(SERVER_TRUSTSTORE_FILE);
        createTestResource(CLIENT_KEYSTORE_FILE);
        createTestResource(CLIENT_TRUSTSTORE_FILE);
        super.setup(managementClient);
    }

    /**
     * Then calls parent
     * {@link org.wildfly.core.testrunner.ServerSetupTask#tearDown(org.wildfly.core.testrunner.ManagementClient)} method
     * and then deletes {@link #WORK_DIR} folder. Creates {@link #WORK_DIR} folder and copies keystores and truststores
     * to it.
     *
     * @see
     * org.jboss.as.test.integration.auditlog.AuditLogToSyslogSetup#tearDown(org.jboss.as.arquillian.container.ManagementClient,
     * java.lang.String)
     */
    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        super.tearDown(managementClient);
        FileUtils.deleteDirectory(WORK_DIR);
        deleteKeyStoreFiles();
    }

    /**
     * Copies a resource file from current package to location denoted by given {@link java.io.File} instance.
     *
     * @param file
     * @throws java.io.IOException
     */
    private void createTestResource(File file) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            IOUtils.copy(getClass().getResourceAsStream(file.getName()), fos);
        } finally {
            IOUtils.closeQuietly(fos);
        }
    }

}
