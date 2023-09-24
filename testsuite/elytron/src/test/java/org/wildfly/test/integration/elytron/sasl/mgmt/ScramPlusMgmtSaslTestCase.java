/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.elytron.sasl.mgmt;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.test.integration.security.common.SecurityTestConstants.KEYSTORE_PASSWORD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.CONNECTION_TIMEOUT_IN_MS;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.PORT_NATIVE;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.assertAuthenticationFails;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.assertWhoAmI;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.executeWhoAmI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.sasl.SaslException;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.SecurityFactory;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.mechanism.ScramServerErrorCode;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.CliPath;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.ConstantPermissionMapper;
import org.wildfly.test.security.common.elytron.ConstantPrincipalTransformer;
import org.wildfly.test.security.common.elytron.CredentialReference;
import org.wildfly.test.security.common.elytron.FileSystemRealm;
import org.wildfly.test.security.common.elytron.MechanismConfiguration;
import org.wildfly.test.security.common.elytron.MechanismConfiguration.Builder;
import org.wildfly.test.security.common.elytron.PermissionRef;
import org.wildfly.test.security.common.elytron.SaslFilter;
import org.wildfly.test.security.common.elytron.SimpleConfigurableSaslServerFactory;
import org.wildfly.test.security.common.elytron.SimpleKeyManager;
import org.wildfly.test.security.common.elytron.SimpleKeyStore;
import org.wildfly.test.security.common.elytron.SimpleSaslAuthenticationFactory;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain.SecurityDomainRealm;
import org.wildfly.test.security.common.elytron.SimpleServerSslContext;
import org.wildfly.test.security.common.elytron.SimpleTrustManager;
import org.wildfly.test.security.common.elytron.TrustedDomainsConfigurator;
import org.wildfly.test.security.common.other.SimpleMgmtNativeInterface;
import org.wildfly.test.security.common.other.SimpleSocketBinding;

/**
 * Tests for Elytron SCRAM-*-PLUS SASL mechanisms. The test configuration allows all SCRAM mechanisms on the server side (
 * {@code SCRAM-*}) and configures server SSL context so the channel binding is possible. For the *-PLUS mechanisms is used
 * pre-realm-principal-transformer, so the authentication results in another identity than for non-plus mechanisms.
 * <p>
 * The test methods checks different client side configurations.
 * </p>
 *
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({ ScramPlusMgmtSaslTestCase.KeyMaterialSetup.class, ScramPlusMgmtSaslTestCase.ServerSetup.class })
public class ScramPlusMgmtSaslTestCase {

    private static final String NAME = ScramPlusMgmtSaslTestCase.class.getSimpleName();
    protected static final String PLUS_TRANSFORMER = "plusTransformer";
    protected static final String USERNAME = "guest";
    protected static final String PASSWORD = "guest-pwd";
    protected static final String USERNAME_CLIENT = "client";

    private static final File WORK_DIR;
    static {
        try {
            WORK_DIR = Files.createTempDirectory("scramplus-").toFile();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary folder", e);
        }
    }

    private static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    private static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    private static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);

    /**
     * Tests that client is able to use mechanism when server allows it.
     */
    @Test
    public void testPlusMechanismPasses() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("#PLUS")).useName(USERNAME).usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI(USERNAME_CLIENT));
    }

    /**
     * Tests that client is able to use mechanism when server allows it.
     */
    @Test
    public void testFamilyMechanismPasses() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("#FAMILY(SCRAM)")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI(USERNAME_CLIENT));
    }

    /**
     * Tests that SSL handshake fails when trustmanager is not configured on the client.
     */
    @Test
    public void testFamilyMechanismNoSslTrustFails() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("#FAMILY(SCRAM)")).useName(USERNAME)
                .usePassword(PASSWORD);

        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).run(
                () -> assertAuthenticationFails("Connection without trustmanager configured should fail", SSLException.class));
    }

    /**
     * Tests that authentication fails for non-existing SASL mechanism name.
     */
    @Test
    public void testNonexistingSaslMechFail() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-WHATEVER-PLUS")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertAuthenticationFails("Connection through unexisting SASL mechanism should fail."));
    }

    /**
     * Tests that authentication with non-PLUS mechanism fails when SSL context is used. (It's due to fact, Elytron client
     * doesn't provide support for disabling channel binding.
     */
    @Test
    public void testSha1Fail() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-1")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertAuthenticationFailsPlusRequired());
    }

    /**
     * Tests that authentication is possible using SCRAM-SHA-1-PLUS.
     */
    @Test
    public void testSha1PlusPass() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-1-PLUS")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI(USERNAME_CLIENT));
    }

    /**
     * Tests that authentication with non-PLUS mechanism fails when SSL context is used. (It's due to fact, Elytron client
     * doesn't provide support for disabling channel binding.
     */
    @Test
    public void testSha256Fail() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-256")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertAuthenticationFailsPlusRequired());
    }

    /**
     * Asserts that execution of :whoami operation fail and exception contains
     */
    protected static void assertAuthenticationFailsPlusRequired() {
        final long startTime = System.currentTimeMillis();
        try {
            executeWhoAmI();
            fail("Client authentication failure is expected.");
        } catch (IOException e) {
            assertTrue("Connection reached its timeout (hang).",
                    startTime + CONNECTION_TIMEOUT_IN_MS > System.currentTimeMillis());
            Throwable cause = e.getCause();
            assertThat("ConnectionException was expected as a cause when SASL authentication fails", cause,
                    is(instanceOf(ConnectException.class)));
            Throwable saslException = cause.getCause();
            assertThat("An unexpected second Exception cause came when authentication failed", saslException,
                    is(instanceOf(SaslException.class)));
            final String saslExceptionMsg = saslException.getMessage();
            assertThat("SASL Error message should contain reason of failure (channel binding configuration).", saslExceptionMsg,
                    allOf(containsString("ELY05166"), containsString(ScramServerErrorCode.SERVER_DOES_SUPPORT_CHANNEL_BINDING.getText())));
        }
    }

    /**
     * Tests that authentication is possible using SCRAM-SHA-256-PLUS.
     */
    @Test
    public void testSha256PlusPass() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-256-PLUS")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI(USERNAME_CLIENT));
    }

    /**
     * Tests that authentication with non-PLUS mechanism fails when SSL context is used. (It's due to fact, Elytron client
     * doesn't provide support for disabling channel binding.
     */
    @Test
    public void testSha384Fail() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-384")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertAuthenticationFailsPlusRequired());
    }

    /**
     * Tests that authentication is possible using SCRAM-SHA-384-PLUS.
     */
    @Test
    public void testSha384PlusPass() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-384-PLUS")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI(USERNAME_CLIENT));
    }

    /**
     * Tests that authentication with non-PLUS mechanism fails when SSL context is used. (It's due to fact, Elytron client
     * doesn't provide support for disabling channel binding.
     */
    @Test
    public void testSha512Fail() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-512")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertAuthenticationFailsPlusRequired());
    }

    /**
     * Tests that authentication is possible using SCRAM-SHA-512-PLUS.
     */
    @Test
    public void testSha512PlusPass() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString("SCRAM-SHA-512-PLUS")).useName(USERNAME)
                .usePassword(PASSWORD);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager())
                .build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI(USERNAME_CLIENT));
    }

    /**
     * Get the trust manager for {@link #CLIENT_TRUSTSTORE_FILE}.
     *
     * @return the trust manager
     */
    private static X509TrustManager getTrustManager() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(loadKeyStore(CLIENT_TRUSTSTORE_FILE));

        for (TrustManager current : trustManagerFactory.getTrustManagers()) {
            if (current instanceof X509TrustManager) {
                return (X509TrustManager) current;
            }
        }

        throw new IllegalStateException("Unable to obtain X509TrustManager.");
    }

    private static KeyStore loadKeyStore(final File ksFile) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }
        return ks;
    }

    /**
     * Setup task which configures Elytron security domains and remoting connectors for this test.
     */
    public static class ServerSetup extends TestRunnerConfigSetupTask {

        @Override
        protected ConfigurableElement[] getConfigurableElements() {
            List<ConfigurableElement> elements = new ArrayList<>();

            final CredentialReference credentialReference = CredentialReference.builder().withClearText(KEYSTORE_PASSWORD)
                    .build();

            elements.add(ConstantPermissionMapper.builder().withName(NAME)
                    .withPermissions(PermissionRef.fromPermission(new LoginPermission())).build());

            // KeyStores
            final SimpleKeyStore.Builder ksCommon = SimpleKeyStore.builder().withType("JKS")
                    .withCredentialReference(credentialReference);
            elements.add(ksCommon.withName("server-keystore")
                    .withPath(CliPath.builder().withPath(SERVER_KEYSTORE_FILE.getAbsolutePath()).build()).build());
            elements.add(ksCommon.withName("server-truststore")
                    .withPath(CliPath.builder().withPath(SERVER_TRUSTSTORE_FILE.getAbsolutePath()).build()).build());

            // Key and Trust Managers
            elements.add(SimpleKeyManager.builder().withName("server-keymanager").withCredentialReference(credentialReference)
                    .withKeyStore("server-keystore").build());
            elements.add(
                    SimpleTrustManager.builder().withName("server-trustmanager").withKeyStore("server-truststore").build());

            // Realms
            elements.add(FileSystemRealm.builder().withName(NAME).withUser(USERNAME, PASSWORD)
                    .withUser(USERNAME_CLIENT, PASSWORD).build());

            // PrincipalTransformer for plus mechanisms
            elements.add(
                    ConstantPrincipalTransformer.builder().withName(PLUS_TRANSFORMER).withConstant(USERNAME_CLIENT).build());

            // Domain
            elements.add(SimpleSecurityDomain.builder().withName(NAME).withDefaultRealm(NAME).withPermissionMapper(NAME)
                    .withRealms(SecurityDomainRealm.builder().withRealm(NAME).build()).build());
            elements.add(
                    TrustedDomainsConfigurator.builder().withName("ManagementDomain").withTrustedSecurityDomains(NAME).build());

            // SASL Authentication
            Builder plusMechsConfig = MechanismConfiguration.builder().withPreRealmPrincipalTransformer(PLUS_TRANSFORMER);

            elements.add(SimpleConfigurableSaslServerFactory.builder().withName(NAME).withSaslServerFactory("elytron")
                    .addFilter(SaslFilter.builder().withPatternFilter("SCRAM-*").build()).build());
            elements.add(SimpleSaslAuthenticationFactory.builder().withName(NAME).withSaslServerFactory(NAME)
                    .withSecurityDomain(NAME)
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-1-PLUS").build())
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-256-PLUS").build())
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-384-PLUS").build())
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-512-PLUS").build())
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-1").build())
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-256").build())
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-384").build())
                    .addMechanismConfiguration(plusMechsConfig.withMechanismName("SCRAM-SHA-512").build())
                    .build());

            // SSLContext
            elements.add(SimpleServerSslContext.builder().withName(NAME).withKeyManagers("server-keymanager")
                    .withTrustManagers("server-trustmanager").withSecurityDomain(NAME).withAuthenticationOptional(true)
                    .withNeedClientAuth(false).build());

            // Socket binding and native management interface
            elements.add(SimpleSocketBinding.builder().withName(NAME).withPort(PORT_NATIVE).build());
            elements.add(SimpleMgmtNativeInterface.builder().withSocketBinding(NAME).withSaslAuthenticationFactory(NAME)
                    .withSslContext(NAME).build());

            return elements.toArray(new ConfigurableElement[elements.size()]);
        }
    }

    public static class KeyMaterialSetup implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            FileUtils.deleteQuietly(WORK_DIR);
            WORK_DIR.mkdir();
            CoreUtils.createKeyMaterial(WORK_DIR);
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            FileUtils.deleteQuietly(WORK_DIR);
        }

    }
}
