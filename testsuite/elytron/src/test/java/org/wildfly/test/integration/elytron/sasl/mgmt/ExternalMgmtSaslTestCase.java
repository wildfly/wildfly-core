/*
 * Copyright 2017 Red Hat, Inc.
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

package org.wildfly.test.integration.elytron.sasl.mgmt;

import static org.hamcrest.CoreMatchers.anyOf;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.sasl.Sasl;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.SecurityFactory;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.CliPath;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.ConstantPermissionMapper;
import org.wildfly.test.security.common.elytron.ConstantPrincipalTransformer;
import org.wildfly.test.security.common.elytron.CredentialReference;
import org.wildfly.test.security.common.elytron.KeyStoreRealm;
import org.wildfly.test.security.common.elytron.MechanismConfiguration;
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
import org.wildfly.test.security.common.elytron.X500AttributePrincipalDecoder;
import org.wildfly.test.security.common.other.SimpleMgmtNativeInterface;
import org.wildfly.test.security.common.other.SimpleSocketBinding;

/**
 * Tests EXTERNAL SASL mechanism used for management interface.
 *
 * @author Josef Cacek
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({ ExternalMgmtSaslTestCase.KeyMaterialSetup.class, ExternalMgmtSaslTestCase.ServerSetup.class })
public class ExternalMgmtSaslTestCase {

    private static final String NAME = ExternalMgmtSaslTestCase.class.getSimpleName();

    private static final File WORK_DIR;
    static {
        try {
            WORK_DIR = Files.createTempDirectory("external-").toFile();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary folder", e);
        }
    }

    private static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    private static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    private static final File CLIENT_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_KEYSTORE);
    private static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);
    private static final File UNTRUSTED_STORE_FILE = new File(WORK_DIR, SecurityTestConstants.UNTRUSTED_KEYSTORE);

    private static final String MECHANISM = "EXTERNAL";

    // @Override
    protected String getMechanism() {
        return MECHANISM;
    }

    /**
     * Tests that client is able to use mechanism when server allows it.
     */
    @Test
    public void testCorrectMechanismPasses() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(MECHANISM));

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true)
                .setKeyManager(getKeyManager(CLIENT_KEYSTORE_FILE)).setTrustManager(getTrustManager()).build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI("client"));
    }

    /**
     * Tests SASL mechanism filtering through policy properties which are matched by EXTERNAL mechanism.
     *
     * @see <a href="https://issues.jboss.org/browse/ELY-982">ELY-982</a>
     */
    @Test
    public void testMatchingFilteringProperties() throws Exception {
        Map<String, String> mechanismProperties = new HashMap<>();
        mechanismProperties.put(Sasl.POLICY_NOPLAINTEXT, "true");
        mechanismProperties.put(Sasl.POLICY_NOACTIVE, "true");
        mechanismProperties.put(Sasl.POLICY_NODICTIONARY, "true");
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(MECHANISM))
                .useMechanismProperties(mechanismProperties);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true)
                .setKeyManager(getKeyManager(CLIENT_KEYSTORE_FILE)).setTrustManager(getTrustManager()).build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertWhoAmI("client"));
    }

    /**
     * Tests SASL mechanism filtering through policy properties which are not matched by EXTERNAL mechanism.
     *
     * @see <a href="https://issues.jboss.org/browse/ELY-982">ELY-982</a>
     */
    @Test
    public void testUnmatchingFilteringProperties() throws Exception {
        assertUnmatchingFilteringPropertyFails(Sasl.POLICY_FORWARD_SECRECY);
        assertUnmatchingFilteringPropertyFails(Sasl.POLICY_PASS_CREDENTIALS);
        assertUnmatchingFilteringPropertyFails(Sasl.POLICY_NOANONYMOUS);
    }

    /**
     * @param policyProperty
     * @throws Exception
     */
    private void assertUnmatchingFilteringPropertyFails(String policyProperty) throws Exception {
        Map<String, String> mechanismProperties = new HashMap<>();
        mechanismProperties.put(policyProperty, "true");
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(MECHANISM))
                .useMechanismProperties(mechanismProperties);

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true)
                .setKeyManager(getKeyManager(CLIENT_KEYSTORE_FILE)).setTrustManager(getTrustManager()).build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertAuthenticationFails(
                        String.format("The EXTERNAL SASL mechanism should not be selected, when property %s is true.", policyProperty)));
    }

    /**
     * Tests that client with wrong (untrusted) certificate is not able to execute operation on server through the mechanism.
     */
    @Test
    public void testUntrustedCertFails() throws Exception {
        AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(MECHANISM));

        SecurityFactory<SSLContext> ssl = new SSLContextBuilder().setClientMode(true)
                .setKeyManager(getKeyManager(UNTRUSTED_STORE_FILE)).setTrustManager(getTrustManager()).build();
        AuthenticationContext.empty().with(MatchRule.ALL, authCfg).withSsl(MatchRule.ALL, ssl)
                .run(() -> assertCertAuthenticationFails(
                        "Client certificate authentication should fail for an untrusted certificate."));
    }

    protected static void assertCertAuthenticationFails(String message) {
        final long startTime = System.currentTimeMillis();
        try {
            executeWhoAmI();
            fail(message);
        } catch (IOException e) {
            assertTrue("Connection reached its timeout (hang).",
                    startTime + CONNECTION_TIMEOUT_IN_MS > System.currentTimeMillis());
            Throwable cause = e.getCause();
            assertThat("ConnectionException was expected as a cause when certificate authentication fails", cause,
                    is(instanceOf(ConnectException.class)));
            assertThat("SSLException was expected as the second cause when certificate authentication fails", cause.getCause(),
                    anyOf(is(instanceOf(SSLException.class)), is(instanceOf(IOException.class)))); // JDK-8172163: IOException: Broken pipe needs to be accepted too
        }
    }

    /**
     * Get the key manager backed by the specified key store.
     *
     * @return the initialized key manager.
     */
    private static X509ExtendedKeyManager getKeyManager(final File ksFile) throws Exception {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(loadKeyStore(ksFile), KEYSTORE_PASSWORD.toCharArray());

        for (KeyManager current : keyManagerFactory.getKeyManagers()) {
            if (current instanceof X509ExtendedKeyManager) {
                return (X509ExtendedKeyManager) current;
            }
        }

        throw new IllegalStateException("Unable to obtain X509ExtendedKeyManager.");
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
            elements.add(KeyStoreRealm.builder().withName(NAME).withKeyStore("server-truststore").build());

            // Mappers
            elements.add(X500AttributePrincipalDecoder.builder().withName(NAME + "Decoder").withAttributeName("CN")
                    .withMaximumSegments(1).build());

            // Transformer
            elements.add(ConstantPrincipalTransformer.builder().withName(NAME).withConstant("cn=client").build());

            // Domain
            elements.add(SimpleSecurityDomain.builder().withName(NAME).withDefaultRealm(NAME).withPermissionMapper(NAME)
                    .withPrincipalDecoder(NAME + "Decoder")
                    .withRealms(SecurityDomainRealm.builder().withRealm(NAME).withPrincipalTransformer(NAME).build()).build());
            elements.add(new ConfigurableElement() {
                @Override
                public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
                    cli.sendLine(String.format(
                            "/subsystem=elytron/security-domain=ManagementDomain:write-attribute(name=trusted-security-domains, value=[%s])",
                            NAME));
                }

                @Override
                public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
                    cli.sendLine(
                            "/subsystem=elytron/security-domain=ManagementDomain:undefine-attribute(name=trusted-security-domains)");
                }

                @Override
                public String getName() {
                    return "domain-trust";
                }
            });

            // SASL Authentication
            elements.add(SimpleConfigurableSaslServerFactory.builder().withName(NAME).withSaslServerFactory("elytron")
                    .addFilter(SaslFilter.builder().withPatternFilter(MECHANISM).build()).build());
            elements.add(SimpleSaslAuthenticationFactory.builder().withName(NAME).withSaslServerFactory(NAME)
                    .withSecurityDomain(NAME)
                    .addMechanismConfiguration(MechanismConfiguration.builder().withMechanismName(MECHANISM).build()).build());

            // SSLContext
            elements.add(SimpleServerSslContext.builder().withName(NAME).withKeyManagers("server-keymanager")
                    .withTrustManagers("server-trustmanager").withSecurityDomain(NAME).withAuthenticationOptional(false)
                    .withNeedClientAuth(true).build());

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
