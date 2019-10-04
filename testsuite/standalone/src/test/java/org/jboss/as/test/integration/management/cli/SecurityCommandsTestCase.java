/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.test.integration.management.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockserver.integration.ClientAndServer;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;

import static org.jboss.as.test.integration.management.cli.AcmeMockServerBuilder.setupTestObtainCertificateWithKeySize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class SecurityCommandsTestCase {

    private static final ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();

    private static CommandContext ctx;
    private static final String LOCALHOST_ACME = "LOCALHOST_ACME";
    private static final String LOCALHOST_ACME_URL = "http://localhost:4001/directory";
    private static final String GENERATED_KEY_STORE_FILE_NAME = "gen-key-store.keystore";
    private static final String GENERATED_PEM_FILE_NAME = "gen-key-store.pem";
    private static final String GENERATED_CSR_FILE_NAME = "gen-key-store.csr";
    private static final String GENERATED_KEY_STORE_PASSWORD = "mysecret";
    private static final String GENERATED_KEY_STORE_ALIAS = "myalias";
    private static final String GENERATED_TRUST_STORE_FILE_NAME = "gen-trust-store.truststore";

    private static final String SERVER_KEY_STORE_FILE = "cli-security-test-server.keystore";
    private static final String CLIENT_KEY_STORE_FILE = "cli-security-test-client.keystore";
    private static final String CLIENT_CERTIFICATE_FILE = "cli-security-test-client-certificate.pem";
    private static final String KEY_STORE_PASSWORD = "secret";

    private static final String KEY_STORE_NAME = "ks1";
    private static final String KEY_MANAGER_NAME = "km1";
    private static final String TRUST_STORE_NAME = "ts1";
    private static final String TRUST_MANAGER_NAME = "tm1";
    private static final String SSL_CONTEXT_NAME = "sslCtx1";
    private static File serverKeyStore;
    private static File clientKeyStore;
    private static File clientCertificate;

    private static final String ACCOUNTS_KEYSTORE_FILE_NAME = "account.keystore";
    private static final String ACCOUNTS_KEYSTORE_PASSWORD = "elytron";
    private static final String CA_ACCOUNT_ALIAS = "account6";
    private static final String CA_ACCOUNT_NAME = "CertAuthorityAccount";
    private static final String KEYSTORE_FILE = "test.keystore";
    private static final String KEYSTORE_PASSWORD = "secret";
    private static final String certificateAlias = "server";
    private static ClientAndServer server;
    private static CertificateAuthority certificateAuthority = CertificateAuthority.getDefault();

    @ClassRule
    public static final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    @Test
    public void testEnableLetsEncryptSSLInteractiveDeclineTOS() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");

        try {
            cli.executeInteractive();
            cli.clearOutput();

            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload --lets-encrypt",
                    "File name (default accounts.keystore.jks)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(ACCOUNTS_KEYSTORE_FILE_NAME, "Password (blank generated):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(ACCOUNTS_KEYSTORE_PASSWORD, "Account name (default CertAuthorityAccount)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(CA_ACCOUNT_NAME, "Contact email(s)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("admin@example.com", "Password (blank generated):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(ACCOUNTS_KEYSTORE_PASSWORD, "Alias"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(CA_ACCOUNT_ALIAS, "Certificate authority URL (default "+ certificateAuthority.getUrl() +"):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(LOCALHOST_ACME_URL, "Do you agree to Let's Encrypt terms of service? y/n:"));
            //Check that the interactive mode exits after rejecting the TOS
            Assert.assertTrue(cli.pushLineAndWaitForResults("n", null));
            Assert.assertTrue(cli.getOutput().contains("Ignoring, command not executed. You need to accept the TOS to create account and obtain certificates."));
        } catch (Throwable ex) {
            throw new Exception(cli.getOutput(), ex);
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testEnableLetsEncryptSSLInteractiveUseFakeCaAccount() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");

        try {
            cli.executeInteractive();
            cli.clearOutput();

            Path jbossStandaloneConfig = Paths.get(TestSuiteEnvironment.getJBossHome() +"/standalone/configuration");
            Path KSFile = jbossStandaloneConfig.resolve(KEYSTORE_FILE);

            //Copy the pre-generated key stores to the server config
            Files.copy(AcmeMockServerBuilder.class.getResourceAsStream(ACCOUNTS_KEYSTORE_FILE_NAME),
                    jbossStandaloneConfig.resolve(ACCOUNTS_KEYSTORE_FILE_NAME),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            //Check that the KSFile is not present
            assertFalse(KSFile.toFile().exists());

            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload"
                            + " --lets-encrypt"
                            + " --ca-account=FakeCaAccount", "Key-store file name (default management.keystore):" ));
            Assert.assertTrue(cli.pushLineAndWaitForResults(KEYSTORE_FILE, "Password (blank generated)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(KEYSTORE_PASSWORD, "Your domain name(s) (must be accessible by the Let's Encrypt server at 80 & 443 ports) [example.com,second.example.com]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("inlneseppwkfwew.com", "Alias (blank generated):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(certificateAlias, "Enable SSL Mutual Authentication"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("n", "Do you confirm"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", null));
            Assert.assertTrue(cli.getOutput().contains("FakeCaAccount not found"));

            //Check that the security command failed and the the KSFile is still not present
            assertFalse(KSFile.toFile().exists());

        } catch (Throwable ex) {
            throw new Exception(cli.getOutput(), ex);
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testEnableLetsEncryptSSLInteractiveConfirm() throws Exception {
        testEnableLetsEncryptSSLInteractiveConfirm(false);
    }

    @Test
    public void testEnableLetsEncryptSSLInteractiveConfirmUseCaAccount() throws Exception {
        testEnableLetsEncryptSSLInteractiveConfirm(true);
    }

    private void testEnableLetsEncryptSSLInteractiveConfirm(boolean useCaAccount) throws Exception {
        setupTestObtainCertificateWithKeySize(server, useCaAccount);

        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        File genKeyStore = null;
        File genTrustStore = null;
        File genServerCertificate = null;
        File genCsr = null;
        try {
            cli.executeInteractive();
            cli.clearOutput();

            Path jbossStandaloneConfig = Paths.get(TestSuiteEnvironment.getJBossHome() +"/standalone/configuration");
            Path KSFile = jbossStandaloneConfig.resolve(KEYSTORE_FILE);
            genKeyStore = KSFile.toFile();

            //Copy the pre-generated key stores to the server config
            Files.copy(AcmeMockServerBuilder.class.getResourceAsStream(ACCOUNTS_KEYSTORE_FILE_NAME),
                    jbossStandaloneConfig.resolve(ACCOUNTS_KEYSTORE_FILE_NAME),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            //Check that the KSFile is not present
            assertFalse(KSFile.toFile().exists());

            if (useCaAccount) {
                //In this case the ACME Mock server will ignore, that the user did not accept TOS this is enforced only in test.
                //Normally it would fail since the TOS were not accepted.
                ctx.handle("/subsystem=elytron/certificate-authority=" + LOCALHOST_ACME + ":add(url=\"" + LOCALHOST_ACME_URL + "\",staging-url=\"http://localhost:4001/directory\"");
                ctx.handle("/subsystem=elytron/key-store=AccountsKeyStore:add(path=" + ACCOUNTS_KEYSTORE_FILE_NAME + ", type=JKS,"
                        + " relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ", credential-reference={clear-text=" + ACCOUNTS_KEYSTORE_PASSWORD + "})");
                ctx.handle("/subsystem=elytron/certificate-authority-account=" + CA_ACCOUNT_NAME + ":add(certificate-authority=" + LOCALHOST_ACME + ", contact-urls=[mailto:admin@example.com],"
                        + " key-store=AccountsKeyStore, alias=" + CA_ACCOUNT_ALIAS + ", credential-reference={clear-text="+ ACCOUNTS_KEYSTORE_PASSWORD + "})");

                Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload"
                                + " --lets-encrypt"
                                + " --ca-account=" + CA_ACCOUNT_NAME,
                        ("Key-store file name (default management.keystore):")));
            } else {
                Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload"
                                + " --lets-encrypt",
                        ("File name (default accounts.keystore.jks)")));

                //skip this when ca Account already created
                Assert.assertTrue(cli.pushLineAndWaitForResults(ACCOUNTS_KEYSTORE_FILE_NAME, "Password (blank generated):"));
                Assert.assertTrue(cli.pushLineAndWaitForResults(ACCOUNTS_KEYSTORE_PASSWORD, "Account name (default CertAuthorityAccount)"));
                Assert.assertTrue(cli.pushLineAndWaitForResults(CA_ACCOUNT_NAME, "Contact email(s)"));
                Assert.assertTrue(cli.pushLineAndWaitForResults("admin@example.com", "Password (blank generated):"));
                Assert.assertTrue(cli.pushLineAndWaitForResults(ACCOUNTS_KEYSTORE_PASSWORD, "Alias"));
                Assert.assertTrue(cli.pushLineAndWaitForResults(CA_ACCOUNT_ALIAS, "Certificate authority URL (default "+ certificateAuthority.getUrl() +"):"));
                Assert.assertTrue(cli.pushLineAndWaitForResults(LOCALHOST_ACME_URL, "Do you agree to Let's Encrypt terms of service? y/n:"));
                Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Key-store file name (default management.keystore):"));
            }
            Assert.assertTrue(cli.pushLineAndWaitForResults(KEYSTORE_FILE, "Password (blank generated)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(KEYSTORE_PASSWORD, "Your domain name(s) (must be accessible by the Let's Encrypt server at 80 & 443 ports) [example.com,second.example.com]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("inlneseppwkfwew.com", "Alias (blank generated):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(certificateAlias, "Enable SSL Mutual Authentication"));

            //Mutual auth
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Client certificate (path to pem file)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(clientCertificate.getAbsolutePath(), "Validate certificate"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("n", "Trust-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_TRUST_STORE_FILE_NAME, "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_PASSWORD, "Do you confirm"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", null));
            //Check that the key-store does contain generated aliases
            checkObtainedCertificate("RSA", 4096, certificateAlias, loadKeyStore(KSFile.toString(), KEYSTORE_PASSWORD));

            assertTLSNumResources(3, 1, 1, 1);
            genTrustStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_TRUST_STORE_FILE_NAME);
            Assert.assertTrue(genTrustStore.exists());

            // Check the model contains the provided values.
            List<String> ksList = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
            List<String> kmList = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
            List<String> tmList = getNames(ctx.getModelControllerClient(), Util.TRUST_MANAGER);
            List<String> sslContextList = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
            ModelNode trustManager = getResource(Util.TRUST_MANAGER, tmList.get(0), null);
            checkModel(null, KEYSTORE_FILE, Util.JBOSS_SERVER_CONFIG_DIR,
                    KEYSTORE_PASSWORD, GENERATED_TRUST_STORE_FILE_NAME,
                    GENERATED_KEY_STORE_PASSWORD, ksList.get(1), kmList.get(0),
                    trustManager.get(Util.KEY_STORE).asString(), tmList.get(0), sslContextList.get(0));

            ctx.handle("security disable-ssl-management --no-reload");
            cli.clearOutput();
            // Test that existing account-key-store file makes the command to abort.
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive  --no-reload"
                    + " --lets-encrypt", "File name (default accounts.keystore.jks)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(ACCOUNTS_KEYSTORE_FILE_NAME, null));

            // Test that existing key-store file makes the command to abort.
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive  --no-reload"
                    + " --lets-encrypt", "File name (default accounts.keystore.jks)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Password (blank generated):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Account name (default CertAuthorityAccount)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("CertAuthorityAccount1", "Contact email(s)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("admin@example.com", "Password (blank generated):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Alias"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Certificate authority URL"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Do you agree to Let's Encrypt terms of service? y/n:"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Key-store file name (default management.keystore):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(KEYSTORE_FILE, null));

            //Test that existing Ca account makes the command to abort.
            // Test that existing key-store file makes the command to abort.
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive  --no-reload"
                    + " --lets-encrypt", "File name (default accounts.keystore.jks)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Password (blank generated):"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Account name (default CertAuthorityAccount)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("CertAuthorityAccount", null));

        } catch (Throwable ex) {
            throw new Exception(cli.getOutput(), ex);
        } finally {
            if (genKeyStore != null) {
                genKeyStore.delete();
            }
            if (genTrustStore != null) {
                genTrustStore.delete();
            }
            if (genServerCertificate != null) {
                genServerCertificate.delete();
            }
            if (genCsr != null) {
                genCsr.delete();
            }
            cli.destroyProcess();
        }
    }

    private KeyStore loadKeyStore(String fileName, String password) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        try(final FileInputStream in = new FileInputStream(fileName)) {
            final KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(in, password.toCharArray());
            return ks;
        }
    }

    private void checkObtainedCertificate(String keyAlgorithmName, int keySize, String alias, KeyStore keyStore) throws Exception {
        assertTrue(keyStore.containsAlias(alias));
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, KEYSTORE_PASSWORD.toCharArray());
        X509Certificate signedCert = (X509Certificate) keyStore.getCertificate(alias);
        assertEquals(keyAlgorithmName, privateKey.getAlgorithm());
        assertEquals(keyAlgorithmName, signedCert.getPublicKey().getAlgorithm());
        if (keyAlgorithmName.equals("EC")) {
            assertEquals(keySize, ((ECPublicKey) signedCert.getPublicKey()).getParams().getCurve().getField().getFieldSize());
        } else if (keyAlgorithmName.equals("RSA")) {
            assertEquals(keySize, ((RSAPublicKey) signedCert.getPublicKey()).getModulus().bitLength());
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
         server = new ClientAndServer(4001);

        // Create ctx, used to setup the test and do the final reload.
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setConsoleOutput(consoleOutput).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();

        // generate key-store file for server.
        ctx.handle("/subsystem=elytron/key-store=cli-server-key-store:add(path=" + SERVER_KEY_STORE_FILE + " ,type=JKS"
                + ", relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ", credential-reference={clear-text=" + KEY_STORE_PASSWORD + "})");
        ctx.handle("/subsystem=elytron/key-store=cli-server-key-store:generate-key-pair(distinguished-name=\"CN=CA\", algorithm=RSA, key-size=1024, alias=localhost)");
        ctx.handle("/subsystem=elytron/key-store=cli-server-key-store:store()");
        // Remove the key-store resource.
        ctx.handle("/subsystem=elytron/key-store=cli-server-key-store:remove()");
        serverKeyStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator
                + "standalone" + File.separator + "configuration" + File.separator + SERVER_KEY_STORE_FILE);
        if (!serverKeyStore.exists()) {
            throw new Exception("No key-store generated");
        }

        // generate key-store file for client.
        ctx.handle("/subsystem=elytron/key-store=cli-client-key-store:add(path=" + CLIENT_KEY_STORE_FILE + " ,type=JKS"
                + ", relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ", credential-reference={clear-text=" + KEY_STORE_PASSWORD + "})");
        ctx.handle("/subsystem=elytron/key-store=cli-client-key-store:generate-key-pair(distinguished-name=\"CN=CA\", algorithm=RSA, key-size=1024, alias=client)");
        ctx.handle("/subsystem=elytron/key-store=cli-client-key-store:store()");

        // export the client certificate.
        ctx.handle("/subsystem=elytron/key-store=cli-client-key-store:export-certificate(relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                + ",path=" + CLIENT_CERTIFICATE_FILE + ", alias=client, pem=true)");

        // Remove the key-store resource.
        ctx.handle("/subsystem=elytron/key-store=cli-client-key-store:remove()");

        clientKeyStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + CLIENT_KEY_STORE_FILE);
        if (!clientKeyStore.exists()) {
            throw new Exception("No key-store generated");
        }
        clientCertificate = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + CLIENT_CERTIFICATE_FILE);
        if (!clientCertificate.exists()) {
            throw new Exception("No certificate exported");
        }

    }

    @After
    public void cleanupTest() throws Exception {
        try {
            eraseInterfaces();
        } finally {
            try {
                removeTLS();
            } finally {
                ctx.handle("reload");
            }
        }
    }

    @AfterClass
    public static void cleanup() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (serverKeyStore != null) {
            serverKeyStore.delete();
        }
        if (clientKeyStore != null) {
            clientKeyStore.delete();
        }
        if (clientCertificate != null) {
            clientCertificate.delete();
        }
        if (ctx != null) {
            try {
                ctx.handle("reload");
            } finally {
                ctx.terminateSession();
            }
        }
    }

    @Test
    public void testEmbedded() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--no-color-output");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            String prompt = "[standalone@embedded /]";
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("embed-server --std-out=echo", prompt));
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                    + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --key-store-password=" + KEY_STORE_PASSWORD + " --no-reload", prompt));

            // Disable ssl
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security disable-ssl-management --no-reload", prompt));

            // Re-enable
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                    + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                    + " --no-reload", prompt));

            // Disable ssl
            Assert.assertTrue(cli.getOutput(), cli.pushLineAndWaitForResults("security disable-ssl-management --no-reload", prompt));
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testInvalidEnableSSL() throws Exception {
        assertEmptyModel(null);
        {
            boolean failed = false;
            try {
                ctx.handle("security enable-ssl-management");
            } catch (Exception ex) {
                failed = true;
                // XXX OK, expected
            }
            Assert.assertTrue(failed);
            assertEmptyModel(null);
        }

        {
            boolean failed = false;
            try {
                ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                        + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload"
                        + " --management-interface=foo");
            } catch (Exception ex) {
                failed = true;
                // XXX OK, expected
            }
            Assert.assertTrue(failed);
            assertEmptyModel(null);
        }

        // Call the command with an invalid key-store-path.
        {
            boolean failed = false;
            try {
                ctx.handle("security enable-ssl-management --key-store-path=" + "foo.bar"
                        + " --key-store-password=" + KEY_STORE_PASSWORD + " --no-reload");
            } catch (Exception ex) {
                failed = true;
                // XXX OK, expected
            }
            Assert.assertTrue(failed);
            assertEmptyModel(null);
        }
        {
            // Call the command with no password.
            boolean failed = false;
            try {
                ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                        + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
            } catch (Exception ex) {
                failed = true;
                // XXX OK, expected
            }
            Assert.assertTrue(failed);
            assertEmptyModel(null);
        }
        {
            // Call the command with an invalid key-store-name.
            boolean failed = false;
            try {
                ctx.handle("security enable-ssl-management --key-store-name=" + "foo.bar" + " --no-reload");
            } catch (Exception ex) {
                failed = true;
                // XXX OK, expected
            }
            Assert.assertTrue(failed);
            assertEmptyModel(null);
        }
        {
            boolean failed = false;
            // call the command with both key-store-name and path
            ctx.handle("/subsystem=elytron/key-store=foo:add(path=foo.bar, type=JKS"
                    + ", relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ", credential-reference={clear-text=" + KEY_STORE_PASSWORD + "})");
            try {
                try {
                    ctx.handle("security enable-ssl-management --key-store-name=foo"
                            + " --key-store-path=" + SERVER_KEY_STORE_FILE
                            + " --key-store-password=" + KEY_STORE_PASSWORD
                            + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
                } catch (Exception ex) {
                    failed = true;
                    // XXX OK, expected
                }
            } finally {
                ctx.handle("/subsystem=elytron/key-store=foo:remove()");
            }
            Assert.assertTrue(failed);
            assertEmptyModel(null);
        }

        {
            boolean failed = false;
            // call the command with both trust-store-name and certificate path
            ctx.handle("/subsystem=elytron/key-store=foo:add(path=foo.bar, type=JKS"
                    + ", relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ", credential-reference={clear-text=" + KEY_STORE_PASSWORD + "})");
            try {
                try {
                    ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                            + " --key-store-password=" + KEY_STORE_PASSWORD
                            + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                            + " --trusted-certificate-path=" + clientCertificate.getAbsolutePath()
                            + " --trust-store-name=foo"
                            + " --trust-store-file-name=" + GENERATED_TRUST_STORE_FILE_NAME
                            + " --trust-store-file-password=" + GENERATED_KEY_STORE_PASSWORD
                            + " --new-trust-store-name=" + TRUST_STORE_NAME
                            + " --new-trust-manager-name=" + TRUST_MANAGER_NAME
                            + " --new-key-store-name=" + KEY_STORE_NAME
                            + " --new-key-manager-name=" + KEY_MANAGER_NAME
                            + " --new-ssl-context-name=" + SSL_CONTEXT_NAME
                            + " --no-reload");
                } catch (Exception ex) {
                    failed = true;
                    // XXX OK, expected
                }
            } finally {
                ctx.handle("/subsystem=elytron/key-store=foo:remove()");
            }
            Assert.assertTrue(failed);
            assertEmptyModel(null);
        }
    }

    @Test
    public void testEnableSSLTwoWay() throws Exception {
        assertEmptyModel(null);
        // first validation must fail
        boolean failed = false;
        try {
            ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                    + " --key-store-password=" + KEY_STORE_PASSWORD
                    + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                    + " --trusted-certificate-path=" + clientCertificate.getAbsolutePath()
                    + " --trust-store-file-name=" + GENERATED_TRUST_STORE_FILE_NAME
                    + " --trust-store-file-password=" + GENERATED_KEY_STORE_PASSWORD
                    + " --new-trust-store-name=" + TRUST_STORE_NAME
                    + " --new-trust-manager-name=" + TRUST_MANAGER_NAME
                    + " --new-key-store-name=" + KEY_STORE_NAME
                    + " --new-key-manager-name=" + KEY_MANAGER_NAME
                    + " --new-ssl-context-name=" + SSL_CONTEXT_NAME
                    + " --no-reload");
        } catch (Exception ex) {
            failed = true;
        }
        Assert.assertTrue(failed);

        // Call the command without validation and no-reload.
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD
                + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                + " --trusted-certificate-path=" + clientCertificate.getAbsolutePath()
                + " --trust-store-file-name=" + GENERATED_TRUST_STORE_FILE_NAME
                + " --trust-store-file-password=" + GENERATED_KEY_STORE_PASSWORD
                + " --new-trust-store-name=" + TRUST_STORE_NAME
                + " --new-trust-manager-name=" + TRUST_MANAGER_NAME
                + " --new-key-store-name=" + KEY_STORE_NAME
                + " --new-key-manager-name=" + KEY_MANAGER_NAME
                + " --new-ssl-context-name=" + SSL_CONTEXT_NAME
                + " --no-trusted-certificate-validation"
                + " --no-reload");
        File genTrustStore = null;
        try {
            assertTLSNumResources(2, 1, 1, 1);
            // Check that the trustStore has been generated.
            genTrustStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_TRUST_STORE_FILE_NAME);
            Assert.assertTrue(genTrustStore.exists());

            // Check the model contains the provided values.
            checkModel(null, SERVER_KEY_STORE_FILE, Util.JBOSS_SERVER_CONFIG_DIR,
                    KEY_STORE_PASSWORD, GENERATED_TRUST_STORE_FILE_NAME,
                    GENERATED_KEY_STORE_PASSWORD, KEY_STORE_NAME, KEY_MANAGER_NAME,
                    TRUST_STORE_NAME, TRUST_MANAGER_NAME, SSL_CONTEXT_NAME);
        } finally {
            if (genTrustStore != null) {
                genTrustStore.delete();
            }
        }

        ctx.handle("security disable-ssl-management --no-reload");

        // Re-use the trust-store generated in previous step.
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD
                + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                + " --trust-store-name=" + TRUST_STORE_NAME
                + " --no-reload");
        assertTLSNumResources(2, 1, 1, 1);
        // Check that the model has not been updated.
        checkModel(null, SERVER_KEY_STORE_FILE, Util.JBOSS_SERVER_CONFIG_DIR,
                KEY_STORE_PASSWORD, GENERATED_TRUST_STORE_FILE_NAME,
                GENERATED_KEY_STORE_PASSWORD, KEY_STORE_NAME, KEY_MANAGER_NAME,
                TRUST_STORE_NAME, TRUST_MANAGER_NAME, SSL_CONTEXT_NAME);

        ctx.handle("security disable-ssl-management --no-reload");
    }

    @Test
    public void testEnableSSLHttp() throws Exception {
        testEnableSSL(Util.HTTP_INTERFACE);
    }

    @Test
    public void testEnableSSL() throws Exception {
        testEnableSSL(null);
    }

    @Test
    public void testInteractiveFailure() throws Exception {
        // Remove management-https
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SOCKET_BINDING_GROUP, Util.STANDARD_SOCKETS);
        builder.addNode(Util.SOCKET_BINDING, Util.MANAGEMENT_HTTPS);
        ModelNode response = ctx.getModelControllerClient().execute(builder.buildRequest());
        ModelNode resource = null;
        if (Util.isSuccess(response)) {
            if (response.hasDefined(Util.RESULT)) {
                resource = response.get(Util.RESULT);
            }
        }
        if (resource == null) {
            throw new Exception("can't retrieve management-https");
        }
        ctx.handle("/socket-binding-group=standard-sockets/socket-binding=management-https:remove");
        try {
            CliProcessWrapper cli = new CliProcessWrapper().
                    addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                    addCliArgument("--controller=remote+http://"
                            + TestSuiteEnvironment.getServerAddress() + ":"
                            + TestSuiteEnvironment.getServerPort()).
                    addCliArgument("--connect");
            cli.executeInteractive();
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload", "Key-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_FILE_NAME, "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_PASSWORD, "What is your first and last name? [Unknown]"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organizational unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organization? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your City or Locality? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your State or Province? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the two-letter country code for this unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Is CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown correct y/n [y]"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Validity"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Alias"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_ALIAS, "Enable SSL Mutual Authentication"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("n", "Do you confirm"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", null));
            // Nothing was generated due to missing management-https
            assertEmptyModel(null);
            // check that the server is not in reload state
            builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.READ_RESOURCE);
            response = ctx.getModelControllerClient().execute(builder.buildRequest());
            if (response.has(Util.RESPONSE_HEADERS)) {
                ModelNode mn = response.get(Util.RESPONSE_HEADERS);
                if (mn.has("process-state")) {
                    ModelNode ps = mn.get("process-state");
                    if ("reload-required".equals(ps.asString())) {
                        throw new Exception("Server is in reload state");
                    }
                }
            }
        } finally {
            try {
                builder = new DefaultOperationRequestBuilder();
                builder.setOperationName(Util.ADD);
                builder.addNode(Util.SOCKET_BINDING_GROUP, Util.STANDARD_SOCKETS);
                builder.addNode(Util.SOCKET_BINDING, Util.MANAGEMENT_HTTPS);
                builder.getModelNode().get("port").set(resource.get("port"));
                builder.getModelNode().get("interface").set(resource.get("interface"));
                response = ctx.getModelControllerClient().execute(builder.buildRequest());
                if (!Util.isSuccess(response)) {
                    throw new Exception("Failure adding back management-https");
                }
            } finally {
                ctx.handle("reload");
            }
        }
    }

    @Test
    public void testEnableSSLNative() throws Exception {
        enableNative(ctx);
        try {
            testEnableSSL("native-interface");
        } finally {
            disableNative(ctx);
        }
    }

    @Test
    public void testEnableSSLInteractiveNoGeneration() throws Exception {
        assertEmptyModel(null);
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        File genKeyStore = null;
        File genTrustStore = null;
        File genServerCertificate = null;
        File genCsr = null;
        try {
            cli.executeInteractive();
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload", "Key-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_FILE_NAME, "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_PASSWORD, "What is your first and last name? [Unknown]"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organizational unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organization? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your City or Locality? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your State or Province? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the two-letter country code for this unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Is CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown correct y/n [y]"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Validity"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Alias"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_ALIAS, "Enable SSL Mutual Authentication"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Client certificate (path to pem file)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(clientCertificate.getAbsolutePath(), "Validate certificate"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Trust-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_TRUST_STORE_FILE_NAME, "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_PASSWORD, "Do you confirm"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", null));
            assertEmptyModel(null);
            // Check that the files have been generated.
            genKeyStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_KEY_STORE_FILE_NAME);
            Assert.assertFalse(genKeyStore.exists());
            genTrustStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_TRUST_STORE_FILE_NAME);
            Assert.assertFalse(genTrustStore.exists());
            genServerCertificate = new File(TestSuiteEnvironment.getSystemProperty("jboss.home") + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_PEM_FILE_NAME);
            Assert.assertFalse(genServerCertificate.exists());
            genCsr = new File(TestSuiteEnvironment.getSystemProperty("jboss.home") + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_CSR_FILE_NAME);
            Assert.assertFalse(genCsr.exists());

            ctx.handle("security disable-ssl-management --no-reload");
        } catch (Throwable ex) {
            throw new Exception(cli.getOutput(), ex);
        } finally {
            if (genKeyStore != null) {
                genKeyStore.delete();
            }
            if (genTrustStore != null) {
                genTrustStore.delete();
            }
            if (genServerCertificate != null) {
                genServerCertificate.delete();
            }
            if (genCsr != null) {
                genCsr.delete();
            }
            cli.destroyProcess();
        }
    }

    @Test
    public void testEnableSSLInteractiveConfirm() throws Exception {
        testEnableSSLInteractiveConfirm(null);
    }

    @Test
    public void testEnableSSLInteractiveConfirmNative() throws Exception {
        enableNative(ctx);
        try {
            testEnableSSLInteractiveConfirm("native-interface");
        } finally {
            disableNative(ctx);
        }
    }

    @Test
    public void testEnableSSLInteractiveConfirmHttp() throws Exception {
        testEnableSSLInteractiveConfirm(Util.HTTP_INTERFACE);
    }

    @Test
    public void testEnableSSLInteractiveNoConfirm() throws Exception {
        assertEmptyModel(null);
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload", "Key-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Password"));

            //Loop until DN has been provided.
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is your first and last name? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organizational unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organization? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your City or Locality? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your State or Province? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the two-letter country code for this unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Is CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown correct y/n [y]"));
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("n", "What is your first and last name? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("foo", "What is the name of your organizational unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("bar", "What is the name of your organization? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("foofoo", "What is the name of your City or Locality? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("barbar", "What is the name of your State or Province? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("toto", "What is the two-letter country code for this unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("TO", "Is CN=foo, OU=bar, O=foofoo, L=barbar, ST=toto, C=TO correct y/n [y]"));
            //Loop until value is valid.
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Validity"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("Hello", "Validity"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Alias"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Enable SSL Mutual Authentication"));
            cli.clearOutput();
            // Loop until value is y or n.
            Assert.assertTrue(cli.pushLineAndWaitForResults("TT", "Enable SSL Mutual Authentication"));
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("COCO", "Enable SSL Mutual Authentication"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Client certificate (path to pem file)"));
            cli.clearOutput();
            //Loop until certificate file exists
            Assert.assertTrue(cli.pushLineAndWaitForResults("foo.bar", "Client certificate (path to pem file)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(clientCertificate.getAbsolutePath(), "Validate certificate"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Trust-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Do you confirm"));
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("PP", "Do you confirm"));
            cli.clearOutput();
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("COCO", "Do you confirm"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("n", null));
            assertEmptyModel(null);
        } catch (Throwable ex) {
            throw new Exception(cli.getOutput(), ex);
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testKeyStoreDifferentPassword() throws Exception {
        assertEmptyModel(null);
        // Create a credential store and alias
        ctx.handle("/subsystem=elytron/credential-store=cs:add(credential-reference={clear-text=cs-secret},"
                + "create,location=cs.store,relative-to=jboss.server.config.dir");
        try {
            ctx.handle("/subsystem=elytron/credential-store=cs:add-alias(alias=xxx,secret-value=secret");
            ctx.handle("/subsystem=elytron/key-store=ks1:add(credential-reference={alias=xxx,store=cs},type=JKS,relative-to="
                    + Util.JBOSS_SERVER_CONFIG_DIR + ",path=" + SERVER_KEY_STORE_FILE);
            assertTLSNumResources(1, 0, 0, 0);
            // Don't reuse the ks because different credential-reference
            ctx.handle("security enable-ssl-management --key-store-path="
                    + SERVER_KEY_STORE_FILE
                    + " --key-store-password=" + KEY_STORE_PASSWORD
                    + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
            assertTLSNumResources(2, 1, 0, 1);
            ctx.handle("security disable-ssl-management --no-reload");
        } finally {
            File credentialStores = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + "cs.store");
            credentialStores.delete();
        }
    }

    @Test
    public void testKeyStoreDifferentAlias() throws Exception {
        assertEmptyModel(null);
        // add a key-store with different alias.
        ctx.handle("/subsystem=elytron/key-store=ks3:add(credential-reference={clear-text=secret},"
                + "type=JKS,relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ",path=" + SERVER_KEY_STORE_FILE + ",alias-filter=foo");
        assertTLSNumResources(1, 0, 0, 0);
        //Don't reuse the key-store because different alias-filter
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD
                + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
        assertTLSNumResources(2, 1, 0, 1);
        ctx.handle("security disable-ssl-management --no-reload");
    }

    @Test
    public void testKeyManagerDifferentAlgorithm() throws Exception {
        assertEmptyModel(null);
        // add a key-store that will be reused.
        ctx.handle("/subsystem=elytron/key-store=ks1:add(credential-reference={clear-text=secret},"
                + "type=JKS,relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ",path=" + SERVER_KEY_STORE_FILE);
        // A leymanager that will be not re-used.
        ctx.handle("/subsystem=elytron/key-manager=km:add(algorithm=PKIX,credential-reference={clear-text=secret},key-store=ks1");
        assertTLSNumResources(1, 1, 0, 0);
        //Reuse the key-store but create a new key-manager
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD
                + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
        assertTLSNumResources(1, 2, 0, 1);
        ctx.handle("security disable-ssl-management --no-reload");
    }

    @Test
    public void testKeyManagerDifferentAlias() throws Exception {
        assertEmptyModel(null);
        // add a key-store that will be reused.
        ctx.handle("/subsystem=elytron/key-store=ks1:add(credential-reference={clear-text=secret},"
                + "type=JKS,relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ",path=" + SERVER_KEY_STORE_FILE);
        // A keymanager that will be not re-used.
        ctx.handle("/subsystem=elytron/key-manager=km:add(alias-filter=foo,credential-reference={clear-text=secret},key-store=ks1");
        assertTLSNumResources(1, 1, 0, 0);
        //Reuse the key-store but create a new key-manager
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD
                + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
        assertTLSNumResources(1, 2, 0, 1);
        ctx.handle("security disable-ssl-management --no-reload");
    }

    @Test
    public void testSSLContextDifferentNeedWant() throws Exception {
        assertEmptyModel(null);
        // add a key-store that will be reused.
        ctx.handle("/subsystem=elytron/key-store=ks1:add(credential-reference={clear-text=secret},"
                + "type=JKS,relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ",path=" + SERVER_KEY_STORE_FILE);
        // A keymanager that will be reused.
        ctx.handle("/subsystem=elytron/key-manager=km:add(credential-reference={clear-text=secret},key-store=ks1");
        // An SSLContext not reused.
        ctx.handle("/subsystem=elytron/server-ssl-context=ctx:add(key-manager=km,need-client-auth=true,want-client-auth=true)");
        assertTLSNumResources(1, 1, 0, 1);
        //Reuse the key-store but create a new key-manager
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD
                + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
        assertTLSNumResources(1, 1, 0, 2);
        ctx.handle("security disable-ssl-management --no-reload");
    }

    @Test
    public void testSSLContextDifferentTrustManager() throws Exception {
        assertEmptyModel(null);
        // add a key-store that will be reused.
        ctx.handle("/subsystem=elytron/key-store=ks1:add(credential-reference={clear-text=secret},"
                + "type=JKS,relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + ",path=" + SERVER_KEY_STORE_FILE);
        // A keymanager that will be reused.
        ctx.handle("/subsystem=elytron/key-manager=km:add(credential-reference={clear-text=secret},key-store=ks1");
        // A trust manager.
        ctx.handle("/subsystem=elytron/trust-manager=tm:add(key-store=ks1");
        // An SSLContext not reused.
        ctx.handle("/subsystem=elytron/server-ssl-context=ctx:add(key-manager=km,trust-manager=tm)");
        assertTLSNumResources(1, 1, 1, 1);
        //Reuse the key-store but create a new key-manager
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD
                + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload");
        assertTLSNumResources(1, 1, 1, 2);
        ctx.handle("security disable-ssl-management --no-reload");
    }

    public static void enableNative(CommandContext ctx) throws Exception {
        ctx.handle("/socket-binding-group=standard-sockets/socket-binding=management-native:add(port=9999,interface=management)");
        ModelNode operation = org.jboss.as.controller.operations.common.Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        operation.get("steps").add(createOpNode("core-service=management/security-realm=native-realm", ModelDescriptionConstants.ADD));
        ModelNode localAuth = createOpNode("core-service=management/security-realm=native-realm/authentication=local", ModelDescriptionConstants.ADD);
        localAuth.get("default-user").set("$local");
        operation.get("steps").add(localAuth);
        CoreUtils.applyUpdate(operation, ctx.getModelControllerClient());
        ctx.handle("/core-service=management/management-interface=native-interface:add(security-realm=native-realm, socket-binding=management-native)");
    }

    public static void disableNative(CommandContext ctx) throws CommandLineException {
        try {
            ctx.handle("/core-service=management/management-interface=native-interface:remove()");
        } finally {
            try {
                ctx.handle("/socket-binding-group=standard-sockets/socket-binding=management-native:remove()");
            } finally {
                ctx.handle("/core-service=management/security-realm=native-realm:remove");
            }
        }
    }

    private void testEnableSSL(String mgmtInterface) throws Exception {
        assertEmptyModel(mgmtInterface);
        // Call the command but no-reload.
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR + " --no-reload"
                + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));

        // A new key-store
        List<String> ks = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
        Assert.assertEquals(1, ks.size());
        // A new keyManager
        List<String> km = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
        Assert.assertEquals(1, km.size());
        // A new SSLContext
        List<String> sslCtx = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
        Assert.assertEquals(1, sslCtx.size());
        // Http-interface is secured.
        String usedSslCtx = getManagementInterfaceSSLContextName(ctx, mgmtInterface);
        Assert.assertNotNull(usedSslCtx);
        Assert.assertEquals(sslCtx.get(0), usedSslCtx);

        // Disable ssl, resources shouldn't be deleted
        ctx.handle("security disable-ssl-management --no-reload"
                + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        String usedSslCtx2 = getManagementInterfaceSSLContextName(ctx, mgmtInterface);
        Assert.assertNull(usedSslCtx2);
        List<String> ks2 = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
        Assert.assertEquals(ks, ks2);
        List<String> km2 = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
        Assert.assertEquals(km, km2);
        List<String> sslCtx2 = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
        Assert.assertEquals(sslCtx, sslCtx2);

        // Re-enable, no new resources should be created.
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                + " --no-reload" + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        String usedSslCtx3 = getManagementInterfaceSSLContextName(ctx, mgmtInterface);
        Assert.assertNotNull(usedSslCtx3);
        List<String> ks3 = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
        Assert.assertEquals(ks, ks3);
        List<String> km3 = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
        Assert.assertEquals(km, km3);
        List<String> sslCtx3 = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
        Assert.assertEquals(sslCtx, sslCtx3);

        // Try to enable again, exception should be thrown.
        boolean failed = false;
        try {
            ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                    + " --key-store-password=" + KEY_STORE_PASSWORD
                    + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                    + " --no-reload" + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        } catch (Exception ex) {
            failed = true;
            // XXX OK, expected
        }
        Assert.assertTrue(failed);

        // Disable ssl
        ctx.handle("security disable-ssl-management --no-reload" + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));

        // Enable SSL with key-store-name, no new resources created.
        ctx.handle("security enable-ssl-management --key-store-name=" + ks.get(0)
                + " --no-reload" + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        String usedSslCtx4 = getManagementInterfaceSSLContextName(ctx, mgmtInterface);
        Assert.assertNotNull(usedSslCtx4);
        List<String> ks4 = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
        Assert.assertEquals(ks, ks4);
        List<String> km4 = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
        Assert.assertEquals(km, km4);
        List<String> sslCtx4 = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
        Assert.assertEquals(sslCtx, sslCtx4);

        // Disable ssl
        ctx.handle("security disable-ssl-management --no-reload"
                + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));

        // Enable SSL, provide new key-store, key-manager and ssl-context names;
        ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                + " --new-key-store-name=" + KEY_STORE_NAME + " --new-key-manager-name="
                + KEY_MANAGER_NAME + " --new-ssl-context-name=" + SSL_CONTEXT_NAME + " --no-reload"
                + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        String usedSslCtx5 = getManagementInterfaceSSLContextName(ctx, mgmtInterface);
        Assert.assertEquals(SSL_CONTEXT_NAME, usedSslCtx5);
        List<String> ks5 = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
        Assert.assertEquals(2, ks5.size());
        Assert.assertTrue(ks5.contains(KEY_STORE_NAME));
        List<String> km5 = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
        Assert.assertEquals(2, km5.size());
        Assert.assertTrue(km5.contains(KEY_MANAGER_NAME));
        List<String> sslCtx5 = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
        Assert.assertEquals(2, sslCtx5.size());
        Assert.assertTrue(sslCtx5.contains(SSL_CONTEXT_NAME));

        checkModel(mgmtInterface, SERVER_KEY_STORE_FILE, Util.JBOSS_SERVER_CONFIG_DIR,
                KEY_STORE_PASSWORD, null,
                null, KEY_STORE_NAME, KEY_MANAGER_NAME, null, null, SSL_CONTEXT_NAME);

        // Disable ssl
        ctx.handle("security disable-ssl-management --no-reload"
                + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));

        // Enable SSL, provide same new-key-store-name, exception thrown because already exists
        failed = false;
        try {
            ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                    + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                    + " --new-key-store-name=" + KEY_STORE_NAME + " --no-reload"
                    + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        } catch (Exception ex) {
            // XXX OK expected
            failed = true;
        }
        Assert.assertTrue(failed);
        // Check that it has not been enabled.
        Assert.assertNull(getManagementInterfaceSSLContextName(ctx, mgmtInterface));

        // Enable SSL, provide same new-key-manager-name, exception thrown because already exists
        failed = false;
        try {
            ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                    + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                    + " --new-key-manager-name=" + KEY_MANAGER_NAME + " --no-reload"
                    + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        } catch (Exception ex) {
            // XXX OK expected
            failed = true;
        }
        Assert.assertTrue(failed);
        // Check that it has not been enabled.
        Assert.assertNull(getManagementInterfaceSSLContextName(ctx, mgmtInterface));

        // Enable SSL, provide same new-key-manager-name, exception thrown because already exists
        failed = false;
        try {
            ctx.handle("security enable-ssl-management --key-store-path=" + SERVER_KEY_STORE_FILE
                    + " --key-store-password=" + KEY_STORE_PASSWORD + " --key-store-path-relative-to=" + Util.JBOSS_SERVER_CONFIG_DIR
                    + " --new-ssl-context-name=" + SSL_CONTEXT_NAME + " --no-reload"
                    + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
        } catch (Exception ex) {
            // XXX OK expected
            failed = true;
        }
        Assert.assertTrue(failed);
        // Check that it has not been enabled.
        Assert.assertNull(getManagementInterfaceSSLContextName(ctx, mgmtInterface));
    }

    private void testEnableSSLInteractiveConfirm(String mgmtInterface) throws Exception {
        assertEmptyModel(mgmtInterface);
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        File genKeyStore = null;
        File genTrustStore = null;
        File genServerCertificate = null;
        File genCsr = null;
        try {
            cli.executeInteractive();
            cli.clearOutput();
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload"
                    + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface), "Key-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_FILE_NAME, "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_PASSWORD, "What is your first and last name? [Unknown]"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organizational unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organization? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your City or Locality? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your State or Province? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the two-letter country code for this unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Is CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown correct y/n [y]"));

            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Validity"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Alias"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_ALIAS, "Enable SSL Mutual Authentication"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Client certificate (path to pem file)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(clientCertificate.getAbsolutePath(), "Validate certificate"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("n", "Trust-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_TRUST_STORE_FILE_NAME, "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_PASSWORD, "Do you confirm"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", null));
            assertTLSNumResources(2, 1, 1, 1);
            // Check that the files have been generated.
            genKeyStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_KEY_STORE_FILE_NAME);
            Assert.assertTrue(genKeyStore.exists());
            genTrustStore = new File(TestSuiteEnvironment.getJBossHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_TRUST_STORE_FILE_NAME);
            Assert.assertTrue(genTrustStore.exists());
            genServerCertificate = new File(TestSuiteEnvironment.getSystemProperty("jboss.home") + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_PEM_FILE_NAME);
            Assert.assertTrue(genServerCertificate.exists());
            genCsr = new File(TestSuiteEnvironment.getSystemProperty("jboss.home") + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + GENERATED_CSR_FILE_NAME);
            Assert.assertTrue(genCsr.exists());
            // Check the model contains the provided values.
            List<String> ksList = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
            List<String> kmList = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
            List<String> tmList = getNames(ctx.getModelControllerClient(), Util.TRUST_MANAGER);
            List<String> sslContextList = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
            ModelNode trustManager = getResource(Util.TRUST_MANAGER, tmList.get(0), null);
            checkModel(mgmtInterface, GENERATED_KEY_STORE_FILE_NAME, Util.JBOSS_SERVER_CONFIG_DIR,
                    GENERATED_KEY_STORE_PASSWORD, GENERATED_TRUST_STORE_FILE_NAME,
                    GENERATED_KEY_STORE_PASSWORD, ksList.get(0), kmList.get(0),
                    trustManager.get(Util.KEY_STORE).asString(), tmList.get(0), sslContextList.get(0));

            ctx.handle("security disable-ssl-management --no-reload"
                    + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface));
            cli.clearOutput();
            // Test that existing key-store file makes the command to abort.
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload"
                    + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface), "Key-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_FILE_NAME, null));

            //Test that existing trust-store file makes command to abort
            Assert.assertTrue(cli.pushLineAndWaitForResults("security enable-ssl-management --interactive --no-reload"
                    + (mgmtInterface == null ? "" : " --management-interface=" + mgmtInterface), "Key-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("foo", "Password"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_PASSWORD, "What is your first and last name? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organizational unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your organization? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your City or Locality? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the name of your State or Province? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "What is the two-letter country code for this unit? [Unknown]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Is CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown correct y/n [y]"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Validity"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("", "Alias"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_KEY_STORE_ALIAS, "Enable SSL Mutual Authentication"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("y", "Client certificate (path to pem file)"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(clientCertificate.getAbsolutePath(), "Validate certificate"));
            Assert.assertTrue(cli.pushLineAndWaitForResults("n", "Trust-store file name"));
            Assert.assertTrue(cli.pushLineAndWaitForResults(GENERATED_TRUST_STORE_FILE_NAME, null));

        } catch (Throwable ex) {
            throw new Exception(cli.getOutput(), ex);
        } finally {
            if (genKeyStore != null) {
                genKeyStore.delete();
            }
            if (genTrustStore != null) {
                genTrustStore.delete();
            }
            if (genServerCertificate != null) {
                genServerCertificate.delete();
            }
            if (genCsr != null) {
                genCsr.delete();
            }
            cli.destroyProcess();
        }
    }

    private static String getManagementInterfaceSSLContextName(CommandContext ctx, String interfaceName) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
            builder.addNode(Util.MANAGEMENT_INTERFACE, interfaceName == null ? Util.HTTP_INTERFACE : interfaceName);
            builder.addProperty(Util.NAME, Util.SSL_CONTEXT);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(outcome)) {
                ModelNode mn = outcome.get(Util.RESULT);
                if (mn.isDefined()) {
                    return outcome.get(Util.RESULT).asString();
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
        }

        return null;
    }

    private static List<String> getNames(ModelControllerClient client, String type) {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
            builder.addProperty(Util.CHILD_TYPE, type);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (Util.isSuccess(outcome)) {
                return Util.getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    private static void removeTLS() throws CommandLineException {
        List<String> sslContextList = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
        for (String ssl : sslContextList) {
            ctx.handle("/subsystem=elytron/server-ssl-context=" + ssl + ":remove");
        }
        List<String> kmList = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
        for (String km : kmList) {
            ctx.handle("/subsystem=elytron/key-manager=" + km + ":remove");
        }
        List<String> tmList = getNames(ctx.getModelControllerClient(), Util.TRUST_MANAGER);
        for (String tm : tmList) {
            ctx.handle("/subsystem=elytron/trust-manager=" + tm + ":remove");
        }
        List<String> caAccountsList = getNames(ctx.getModelControllerClient(), Util.CERTIFICATE_AUTHORITY_ACCOUNT);
        for (String caAccount : caAccountsList) {
            ctx.handle("/subsystem=elytron/"+Util.CERTIFICATE_AUTHORITY_ACCOUNT+"=" + caAccount + ":remove");
        }
        // Remove Localhost certificate authority
        List<String> certificatAuthorities = getNames(ctx.getModelControllerClient(), Util.CERTIFICATE_AUTHORITY);
        for (String certificateAuthority : certificatAuthorities) {
            ctx.handle("/subsystem=elytron/"+Util.CERTIFICATE_AUTHORITY+"=" + certificateAuthority + ":remove");
        }
        List<String> ksList = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
        for (String ks : ksList) {
            ctx.handle("/subsystem=elytron/key-store=" + ks + ":remove");
        }
        List<String> credentialStoreList = getNames(ctx.getModelControllerClient(), "credential-store");
        for (String cs : credentialStoreList) {
            ctx.handle("/subsystem=elytron/credential-store=" + cs + ":remove");
        }
    }

    private static void eraseInterfaces() throws CommandLineException {
        ctx.handle("/core-service=management/management-interface=http-interface:write-attribute(name=" + Util.SSL_CONTEXT + ")");
        ctx.handle("/core-service=management/management-interface=http-interface:write-attribute(name=" + Util.SECURE_SOCKET_BINDING + ")");
    }

    private static void assertEmptyModel(String mgmtInterface) throws Exception {
        String ssl = getManagementInterfaceSSLContextName(ctx, mgmtInterface);
        Assert.assertNull(ssl);
        assertTLSEmpty();
    }

    private static void assertTLSNumResources(int numKS, int numKeyManager, int numTrustManager, int numSSLContext) throws Exception {
        List<String> ks = getNames(ctx.getModelControllerClient(), Util.KEY_STORE);
        Assert.assertEquals(numKS, ks.size());
        List<String> km = getNames(ctx.getModelControllerClient(), Util.KEY_MANAGER);
        Assert.assertEquals(numKeyManager, km.size());
        List<String> tm = getNames(ctx.getModelControllerClient(), Util.TRUST_MANAGER);
        Assert.assertEquals(numTrustManager, tm.size());
        List<String> sslCtx = getNames(ctx.getModelControllerClient(), Util.SERVER_SSL_CONTEXT);
        Assert.assertEquals(numSSLContext, sslCtx.size());
    }

    private static void assertTLSEmpty() throws Exception {
        assertTLSNumResources(0, 0, 0, 0);
    }

    private static ModelNode buildKeyStoreResource(File path, String relativeTo,
            String password, String type, Boolean required, String alias) throws IOException {
        ModelNode localKS = new ModelNode();
        if (path != null) {
            localKS.get(Util.PATH).set(path.getPath());
        }
        if (relativeTo != null) {
            localKS.get(Util.RELATIVE_TO).set(relativeTo);
        } else {
            localKS.get(Util.RELATIVE_TO);
        }
        if (password != null) {
            localKS.get(Util.CREDENTIAL_REFERENCE).set(buildCredentialReferences(password));
        }
        if (type != null) {
            localKS.get(Util.TYPE).set(type);
        }
        if (required != null) {
            localKS.get(Util.REQUIRED).set(required);
        }
        if (alias != null) {
            localKS.get(Util.ALIAS_FILTER, alias);
        }
        return localKS;
    }

    private static ModelNode buildSSLContextResource(String trustManager, String keyManager, Boolean want, Boolean need) throws IOException {
        ModelNode sslContext = new ModelNode();
        if (trustManager != null) {
            sslContext.get(Util.TRUST_MANAGER).set(trustManager);
        }
        if (keyManager != null) {
            sslContext.get(Util.KEY_MANAGER).set(keyManager);
        }
        if (want != null) {
            sslContext.get(Util.WANT_CLIENT_AUTH).set(want);
        }
        if (need != null) {
            sslContext.get(Util.NEED_CLIENT_AUTH).set(need);
        }
        sslContext.get(Util.PROTOCOLS).add("TLSv1.2");
        return sslContext;
    }

    private static ModelNode buildCredentialReferences(String password) {
        ModelNode mn = new ModelNode();
        mn.get(Util.CLEAR_TEXT).set(password);
        return mn;
    }

    private static ModelNode getResource(String type, String name, ModelNode filter) throws Exception {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        builder.setOperationName(Util.READ_RESOURCE);
        builder.addNode(Util.SUBSYSTEM, Util.ELYTRON);
        builder.addNode(type, name);
        request = builder.buildRequest();

        final ModelNode outcome = ctx.getModelControllerClient().execute(request);
        if (Util.isSuccess(outcome)) {
            ModelNode res = outcome.get(Util.RESULT);
            if (filter != null) {
                List<String> toRemove = new ArrayList<>();
                for (String k : res.keys()) {
                    if (!filter.hasDefined(k)) {
                        toRemove.add(k);
                    }
                }
                for (String k : toRemove) {
                    res.remove(k);
                }
            }
            return res;
        } else {
            return null;
        }
    }

    private static void checkModel(String mgmtInterface, String keyStoreFile, String relativeTo,
            String password, String trustStoreFile, String tsPassword,
            String keyStoreName, String keyManagerName, String trustStoreName, String trustManagerName, String sslContextName) throws Exception {
        mgmtInterface = mgmtInterface == null ? Util.HTTP_INTERFACE : mgmtInterface;

        // Check the model contains the provided values.
        ModelNode expectedKeyStore = buildKeyStoreResource(new File(keyStoreFile), relativeTo, password, "JKS", false, null);

        ModelNode kManager = getResource(Util.KEY_MANAGER, keyManagerName, null);
        ModelNode tManager;
        if (trustManagerName != null) {
            tManager = getResource(Util.TRUST_MANAGER, trustManagerName, null);
            ModelNode expectedTrustStore = buildKeyStoreResource(new File(trustStoreFile), relativeTo, tsPassword, "JKS", false, null);
            ModelNode ts = getResource(Util.KEY_STORE, tManager.get(Util.KEY_STORE).asString(), expectedTrustStore);
            Assert.assertEquals(trustStoreName, tManager.get(Util.KEY_STORE).asString());
            Assert.assertEquals(expectedTrustStore, ts);
        }

        Assert.assertEquals(keyStoreName, kManager.get(Util.KEY_STORE).asString());
        ModelNode ks = getResource(Util.KEY_STORE, kManager.get(Util.KEY_STORE).asString(), expectedKeyStore);

        Assert.assertEquals(expectedKeyStore, ks);

        //Check that the SSLContext properly references both km and ts
        ModelNode expectedSSLContext = buildSSLContextResource(trustManagerName, keyManagerName, false, trustManagerName != null);
        ModelNode actualSSLContext = getResource(Util.SERVER_SSL_CONTEXT, sslContextName, expectedSSLContext);
        Assert.assertEquals(expectedSSLContext, actualSSLContext);

        //Check that the sslContext is referenced from the interface
        String usedSslCtx = getManagementInterfaceSSLContextName(ctx, mgmtInterface);
        Assert.assertEquals(usedSslCtx, sslContextName);
    }
}
