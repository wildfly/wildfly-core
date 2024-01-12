/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.elytron.sasl.mgmt;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.test.integration.security.common.SecurityTestConstants.KEYSTORE_PASSWORD;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.directory.api.ldap.model.constants.SupportedSaslMechanisms;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.annotations.CreateKdcServer;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.annotations.SaslMechanism;
import org.apache.directory.server.core.annotations.AnnotationUtils;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.core.kerberos.KeyDerivationInterceptor;
import org.apache.directory.server.factory.ServerAnnotationProcessor;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.handlers.sasl.cramMD5.CramMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.sasl.digestMD5.DigestMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.sasl.gssapi.GssapiMechanismHandler;
import org.apache.directory.server.ldap.handlers.sasl.ntlm.NtlmMechanismHandler;
import org.apache.directory.server.ldap.handlers.sasl.plain.PlainMechanismHandler;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.security.SecurityFactory;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.test.security.common.kerberos.AbstractKrb5ConfServerSetupTask;
import org.wildfly.test.security.common.kerberos.InMemoryDirectoryServiceFactory;
import org.wildfly.test.security.common.kerberos.KDCServerAnnotationProcessor;
import org.wildfly.test.security.common.kerberos.KerberosTestUtils;
import org.wildfly.test.security.common.kerberos.Krb5LoginConfiguration;
import org.wildfly.test.security.common.kerberos.ManagedCreateLdapServer;
import org.wildfly.test.security.common.kerberos.ManagedCreateTransport;
import org.xnio.http.RedirectException;

/**
 * Parent class for Elytron Kerberos remoting (GSSAPI and GS2-KRB5* SASL mechanism) testing.
 *
 * @author Josef Cacek
 */
public abstract class AbstractKerberosMgmtSaslTestBase {

    protected static Logger LOGGER = Logger.getLogger(AbstractKerberosMgmtSaslTestBase.class);

    protected static final int CONNECTION_TIMEOUT_IN_MS = TimeoutUtil.adjust(6 * 1000);

    protected static final int PORT_NATIVE = 10567;

    protected static final int LDAP_PORT = 10389;
    protected static final String LDAP_URL = "ldap://" + NetworkUtils.formatPossibleIpv6Address(
            CoreUtils.getCannonicalHost(TestSuiteEnvironment.getSecondaryTestAddress(false))) + ":" + LDAP_PORT;

    protected static final File WORK_DIR_GSSAPI;
    static {
        try {
            WORK_DIR_GSSAPI = Files.createTempDirectory("gssapi-").toFile();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary folder", e);
        }
    }

    protected static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR_GSSAPI, SecurityTestConstants.SERVER_KEYSTORE);
    protected static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR_GSSAPI, SecurityTestConstants.SERVER_TRUSTSTORE);
    protected static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR_GSSAPI, SecurityTestConstants.CLIENT_TRUSTSTORE);

    protected static Krb5LoginConfiguration KRB5_CONFIGURATION;

    protected static SecurityFactory<SSLContext> sslFactory;

    @BeforeClass
    public static void beforeClass() {
        KerberosTestUtils.assumeKerberosAuthenticationSupported();
    }

    /**
     * Test GSSAPI SASL mechanism configured on the server-side for management interface without using SSL.
     */
    @Test
    public void testGssapiWithoutSsl() throws Exception {
        try (AutoCloseable ac = configureSaslMechanismOnServer("GSSAPI", false)) {
            assertKerberosSaslMechPasses("GSSAPI", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GS2-KRB5", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GS2-KRB5-PLUS", "hnelson", "secret", false);
        }
    }

    /**
     * Tests that Kerberos login correctly fails when a wrong username or a wrong password is used.
     */
    @Test
    public void testKerberosWrongCredentials() throws Exception {
        assertKerberosLoginFails("hnelson", "wrongpassword");
        assertKerberosLoginFails("wronguser", "secret");
    }

    /**
     * Test GSSAPI SASL mechanism configured on the server-side for management interface with using SSL.
     */
    @Test
    public void testGssapiOverSsl() throws Exception {
        try (AutoCloseable ac = configureSaslMechanismOnServer("GSSAPI", true)) {
            assertKerberosSaslMechPasses("GSSAPI", "hnelson", "secret", true);
            assertKerberosSaslMechFails("GSSAPI", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GS2-KRB5", "hnelson", "secret", true);
            assertKerberosSaslMechFails("GS2-KRB5-PLUS", "hnelson", "secret", true);
        }
    }

    /**
     * Test GS2-KRB5 SASL mechanism configured on the server-side for management interface without using SSL.
     */
    @Test
    public void testGs2Krb5WithoutSsl() throws Exception {
        try (AutoCloseable ac = configureSaslMechanismOnServer("GS2-KRB5", false)) {
            assertKerberosSaslMechPasses("GS2-KRB5", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GSSAPI", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GS2-KRB5-PLUS", "hnelson", "secret", false);
        }
    }

    /**
     * Test GS2-KRB5 SASL mechanism configured on the server-side for management interface with using SSL.
     */
    @Test
    public void testGs2Krb5OverSsl() throws Exception {
        try (AutoCloseable ac = configureSaslMechanismOnServer("GS2-KRB5", true)) {
            assertKerberosSaslMechFails("GS2-KRB5", "hnelson", "secret", true);
            assertKerberosSaslMechFails("GS2-KRB5", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GSSAPI", "hnelson", "secret", true);
            assertKerberosSaslMechFails("GS2-KRB5-PLUS", "hnelson", "secret", true);
        }
    }

    /**
     * Test GS2-KRB5-PLUS SASL mechanism configured on the server-side for management interface with using SSL.
     */
    @Test
    public void testGs2Krb5PlusWithoutSsl() throws Exception {
        try (AutoCloseable ac = configureSaslMechanismOnServer("GS2-KRB5-PLUS", false)) {
            assertKerberosSaslMechFails("GS2-KRB5-PLUS", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GS2-KRB5", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GSSAPI", "hnelson", "secret", false);
        }
    }

    /**
     * Test GS2-KRB5-PLUS SASL mechanism configured on the server-side for management interface with using SSL.
     */
    @Test
    public void testGs2Krb5PlusOverSsl() throws Exception {
        try (AutoCloseable ac = configureSaslMechanismOnServer("GS2-KRB5-PLUS", true)) {
            assertKerberosSaslMechPasses("GS2-KRB5-PLUS", "hnelson", "secret", true);
            assertKerberosSaslMechFails("GS2-KRB5-PLUS", "hnelson", "secret", false);
            assertKerberosSaslMechFails("GS2-KRB5", "hnelson", "secret", true);
            assertKerberosSaslMechFails("GSSAPI", "hnelson", "secret", true);
        }
    }

    /**
     * Configures given mechanism with (or without) SSL on the server side. The returned {@link AutoCloseable} instance (must
     * not be {@code null}) wraps the tests in try-with-resource block and is used after the test to do a clean-up.
     */
    protected abstract AutoCloseable configureSaslMechanismOnServer(String mechanism, boolean withSsl) throws Exception;

    /**
     * Asserts that given user can authenticate with given Kerberos SASL mechanism.
     */
    protected void assertKerberosSaslMechPasses(String mech, String user, String password, boolean withSsl)
            throws MalformedURLException, LoginException, Exception {
        // 1. Authenticate to Kerberos.
        final LoginContext lc = KerberosTestUtils.loginWithKerberos(KRB5_CONFIGURATION, user, password);
        try {
            AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                    .setSaslMechanismSelector(SaslMechanismSelector.fromString(mech))
                    .useGSSCredential(getGSSCredential(lc.getSubject()));

            AuthenticationContext authnCtx = AuthenticationContext.empty().with(MatchRule.ALL, authCfg);
            if (withSsl) {
                authnCtx = authnCtx.withSsl(MatchRule.ALL, sslFactory);
            }
            final AuthenticationContext authnCtxFinal = authnCtx;
            Subject.doAs(lc.getSubject(), (PrivilegedAction<Void>) () -> {
                authnCtxFinal.run(() -> assertWhoAmI(user + "@JBOSS.ORG", withSsl));
                return null;
            });
        } finally {
            lc.logout();
        }
    }

    protected void assertKerberosSaslMechFails(String mech, String user, String password, boolean withSsl)
            throws MalformedURLException, LoginException, Exception {
        // 1. Authenticate to Kerberos.
        final LoginContext lc = KerberosTestUtils.loginWithKerberos(KRB5_CONFIGURATION, user, password);
        try {
            AuthenticationConfiguration authCfg = AuthenticationConfiguration.empty()
                    .setSaslMechanismSelector(SaslMechanismSelector.fromString(mech))
                    .useGSSCredential(getGSSCredential(lc.getSubject()));

            AuthenticationContext authnCtx = AuthenticationContext.empty().with(MatchRule.ALL, authCfg);
            if (withSsl) {
                authnCtx = authnCtx.withSsl(MatchRule.ALL, sslFactory);
            }
            final AuthenticationContext authnCtxFinal = authnCtx;
            Subject.doAs(lc.getSubject(), (PrivilegedAction<Void>) () -> {
                authnCtxFinal.run(() -> assertAuthenticationFails(null, null, withSsl));
                return null;
            });
        } finally {
            lc.logout();
        }
    }

    protected void assertKerberosLoginFails(String user, String password) {
        LoginContext lc = null;
        try {
            lc = KerberosTestUtils.loginWithKerberos(KRB5_CONFIGURATION, user, password);
            fail("Kerberos authentication failure was expected.");
        } catch (LoginException e) {
            LOGGER.debug("Kerberos authentication failed as expected.");
        } finally {
            if (lc != null) {
                try {
                    lc.logout();
                } catch (LoginException e) {
                    LOGGER.warn("Unsuccessful logout", e);
                }
            }
        }
    }

    /**
     * Retrieves {@link GSSCredential} from given Subject
     */
    protected GSSCredential getGSSCredential(Subject subject) {
        return Subject.doAs(subject, new PrivilegedAction<GSSCredential>() {
            @Override
            public GSSCredential run() {
                try {
                    GSSManager gssManager = GSSManager.getInstance();
                    return gssManager.createCredential(GSSCredential.INITIATE_ONLY);
                } catch (Exception e) {
                    LOGGER.warn("Unable to retrieve GSSCredential from given Subject.", e);
                }
                return null;
            }
        });
    }

    /**
     * Get the trust manager for {@link #CLIENT_TRUSTSTORE_FILE}.
     *
     * @return the trust manager
     */
    protected static X509TrustManager getTrustManager() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(loadKeyStore(CLIENT_TRUSTSTORE_FILE));

        for (TrustManager current : trustManagerFactory.getTrustManagers()) {
            if (current instanceof X509TrustManager) {
                return (X509TrustManager) current;
            }
        }

        throw new IllegalStateException("Unable to obtain X509TrustManager.");
    }

    protected static KeyStore loadKeyStore(final File ksFile) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }
        return ks;
    }

    protected void assertAuthenticationFails(String message, Class<? extends Exception> secondCauseClass, boolean withTls) {
        if (message == null) {
            message = "The failure of :whoami operation execution was expected, but the call passed";
        }
        final long startTime = System.currentTimeMillis();
        try {
            executeWhoAmI(withTls);
            fail(message);
        } catch (IOException | GeneralSecurityException e) {
            assertTrue("Connection reached its timeout (hang).",
                    startTime + CONNECTION_TIMEOUT_IN_MS > System.currentTimeMillis());
            Throwable cause = e.getCause();
            assertThat("ConnectionException was expected as a cause when authentication fails", cause,
                    is(instanceOf(ConnectException.class)));
            assertThat("Unexpected type of inherited exception for authentication failure", cause.getCause(),
                    anyOf(is(instanceOf(SSLException.class)), is(instanceOf(SaslException.class)),
                            is(instanceOf(RedirectException.class)), is(instanceOf(ClosedChannelException.class))));
        }
    }

    protected abstract ModelNode executeWhoAmI(boolean withTls) throws IOException, GeneralSecurityException;

    protected void assertWhoAmI(String expected, boolean withTls) {
        try {
            ModelNode result = executeWhoAmI(withTls);
            assertTrue("The whoami operation should finish with success", Operations.isSuccessfulOutcome(result));
            assertEquals("The whoami operation returned unexpected value", expected,
                    Operations.readResult(result).get("identity").get("username").asString());
        } catch (Exception e) {
            LOGGER.warn("Operation execution failed", e);
            throw new AssertionError("The whoami operation failed", e);
        }
    }

    public static class KeyMaterialSetup implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            FileUtils.deleteQuietly(WORK_DIR_GSSAPI);
            WORK_DIR_GSSAPI.mkdir();
            CoreUtils.createKeyMaterial(WORK_DIR_GSSAPI);

            sslFactory = new SSLContextBuilder().setClientMode(true).setTrustManager(getTrustManager()).build();
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            FileUtils.deleteQuietly(WORK_DIR_GSSAPI);
        }

    }

    /**
     * Task which generates krb5.conf and keytab file(s).
     */
    public static class Krb5ConfServerSetupTask extends AbstractKrb5ConfServerSetupTask {
        public static final File HNELSON_KEYTAB_FILE = new File(WORK_DIR, "hnelson.keytab");
        public static final File JDUKE_KEYTAB_FILE = new File(WORK_DIR, "jduke.keytab");
        public static final String REMOTE_PRINCIPAL = "remote/"
                + CoreUtils.getCannonicalHost(TestSuiteEnvironment.getSecondaryTestAddress(false)) + "@JBOSS.ORG";
        public static final File REMOTE_KEYTAB_FILE = new File(WORK_DIR, "remote.keytab");

        @Override
        protected List<UserForKeyTab> kerberosUsers() {
            List<UserForKeyTab> users = new ArrayList<UserForKeyTab>();
            users.add(new UserForKeyTab("hnelson@JBOSS.ORG", "secret", HNELSON_KEYTAB_FILE));
            users.add(new UserForKeyTab("jduke@JBOSS.ORG", "theduke", JDUKE_KEYTAB_FILE));
            users.add(new UserForKeyTab(REMOTE_PRINCIPAL, "zelvicka", REMOTE_KEYTAB_FILE));
            return users;
        }

    }

    // @formatter:off
    @CreateDS(name = "WildFlyDS", factory = InMemoryDirectoryServiceFactory.class, partitions = @CreatePartition(name = "wildfly", suffix = "dc=wildfly,dc=org"), additionalInterceptors = {
            KeyDerivationInterceptor.class }, allowAnonAccess = true)
    @CreateKdcServer(primaryRealm = "JBOSS.ORG", kdcPrincipal = "krbtgt/JBOSS.ORG@JBOSS.ORG", searchBaseDn = "dc=wildfly,dc=org", transports = {
            @CreateTransport(protocol = "UDP", port = 6088) })
    @CreateLdapServer(transports = {
            @CreateTransport(protocol = "LDAP", port = LDAP_PORT) }, saslHost = "localhost", saslPrincipal = "ldap/localhost@JBOSS.ORG", saslMechanisms = {
                    @SaslMechanism(name = SupportedSaslMechanisms.PLAIN, implClass = PlainMechanismHandler.class),
                    @SaslMechanism(name = SupportedSaslMechanisms.CRAM_MD5, implClass = CramMd5MechanismHandler.class),
                    @SaslMechanism(name = SupportedSaslMechanisms.DIGEST_MD5, implClass = DigestMd5MechanismHandler.class),
                    @SaslMechanism(name = SupportedSaslMechanisms.GSSAPI, implClass = GssapiMechanismHandler.class),
                    @SaslMechanism(name = SupportedSaslMechanisms.NTLM, implClass = NtlmMechanismHandler.class),
                    @SaslMechanism(name = SupportedSaslMechanisms.GSS_SPNEGO, implClass = NtlmMechanismHandler.class) })
    // @formatter:on
    static class DirectoryServerSetupTask implements ServerSetupTask {

        private DirectoryService directoryService;
        private KdcServer kdcServer;
        private LdapServer ldapServer;
        private boolean removeBouncyCastle = false;

        /**
         * Creates directory services, starts LDAP server and KDCServer
         *
         * @param managementClient
         * @param containerId
         * @throws Exception
         * @see org.jboss.as.arquillian.api.ServerSetupTask#setup(org.jboss.as.arquillian.container.ManagementClient,
         *      java.lang.String)
         */
        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                    removeBouncyCastle = true;
                }
            } catch (SecurityException ex) {
                LOGGER.warn("Cannot register BouncyCastleProvider", ex);
            }
            directoryService = DSAnnotationProcessor.getDirectoryService();
            final String hostname = CoreUtils.getCannonicalHost(TestSuiteEnvironment.getHttpAddress());
            final Map<String, String> map = new HashMap<String, String>();
            map.put("hostname", NetworkUtils.formatPossibleIpv6Address(hostname));
            final String secondaryTestAddress = NetworkUtils
                    .canonize(CoreUtils.getCannonicalHost(TestSuiteEnvironment.getSecondaryTestAddress(false)));
            map.put("ldaphost", secondaryTestAddress);
            final String ldifContent = StrSubstitutor.replace(IOUtils.toString(
                    AbstractKerberosMgmtSaslTestBase.class.getResourceAsStream("remoting-krb5-test.ldif"), "UTF-8"), map);
            LOGGER.trace(ldifContent);
            final SchemaManager schemaManager = directoryService.getSchemaManager();
            try {
                for (LdifEntry ldifEntry : new LdifReader(IOUtils.toInputStream(ldifContent, StandardCharsets.UTF_8))) {
                    directoryService.getAdminSession().add(new DefaultEntry(schemaManager, ldifEntry.getEntry()));
                }
            } catch (Exception e) {
                LOGGER.warn("Importing LDIF to a directoryService failed.", e);
                throw e;
            }
            kdcServer = KDCServerAnnotationProcessor.getKdcServer(directoryService, 1024, hostname);
            final ManagedCreateLdapServer createLdapServer = new ManagedCreateLdapServer(
                    (CreateLdapServer) AnnotationUtils.getInstance(CreateLdapServer.class));
            createLdapServer.setSaslHost(secondaryTestAddress);
            createLdapServer.setSaslPrincipal("ldap/" + secondaryTestAddress + "@JBOSS.ORG");
            KerberosTestUtils.fixApacheDSTransportAddress(createLdapServer, secondaryTestAddress);
            ldapServer = ServerAnnotationProcessor.instantiateLdapServer(createLdapServer, directoryService);
            ldapServer.getSaslHost();
            ldapServer.setSearchBaseDn("dc=wildfly,dc=org");
            ldapServer.start();

            KRB5_CONFIGURATION = new Krb5LoginConfiguration(CoreUtils.getLoginConfiguration());
            // Use our custom configuration to avoid reliance on external config
            Configuration.setConfiguration(KRB5_CONFIGURATION);

        }

        /**
         * Stops LDAP server and KDCServer and shuts down the directory service.
         *
         * @param managementClient
         * @param containerId
         * @throws Exception
         * @see org.jboss.as.arquillian.api.ServerSetupTask#tearDown(org.jboss.as.arquillian.container.ManagementClient,
         *      java.lang.String)
         */
        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            KRB5_CONFIGURATION.resetConfiguration();
            ldapServer.stop();
            kdcServer.stop();
            directoryService.shutdown();
            FileUtils.deleteDirectory(directoryService.getInstanceLayout().getInstanceDirectory());
            if (removeBouncyCastle) {
                try {
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
                } catch (SecurityException ex) {
                    LOGGER.warn("Cannot deregister BouncyCastleProvider", ex);
                }
            }

        }

        /**
         * Fixes/replaces LDAP bind address in the CreateTransport annotation of ApacheDS.
         *
         * @param createLdapServer
         * @param address
         */
        public static void fixApacheDSTransportAddress(ManagedCreateLdapServer createLdapServer, String address) {
            final CreateTransport[] createTransports = createLdapServer.transports();
            for (int i = 0; i < createTransports.length; i++) {
                final ManagedCreateTransport mgCreateTransport = new ManagedCreateTransport(createTransports[i]);
                // localhost is a default used in original CreateTransport annotation. We use it as a fallback.
                mgCreateTransport.setAddress(address != null ? address : "localhost");
                createTransports[i] = mgCreateTransport;
            }
        }
    }

}
