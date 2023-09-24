/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.elytron.sasl.mgmt;

import static org.jboss.as.test.integration.security.common.SecurityTestConstants.KEYSTORE_PASSWORD;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.CliPath;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.ConstantPermissionMapper;
import org.wildfly.test.security.common.elytron.CredentialReference;
import org.wildfly.test.security.common.elytron.DirContext;
import org.wildfly.test.security.common.elytron.IdentityMapping;
import org.wildfly.test.security.common.elytron.KerberosSecurityFactory;
import org.wildfly.test.security.common.elytron.LdapRealm;
import org.wildfly.test.security.common.elytron.MechanismConfiguration;
import org.wildfly.test.security.common.elytron.MechanismRealmConfiguration;
import org.wildfly.test.security.common.elytron.PermissionRef;
import org.wildfly.test.security.common.elytron.SimpleConfigurableSaslServerFactory;
import org.wildfly.test.security.common.elytron.SimpleKeyManager;
import org.wildfly.test.security.common.elytron.SimpleKeyStore;
import org.wildfly.test.security.common.elytron.SimpleSaslAuthenticationFactory;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain.SecurityDomainRealm;
import org.wildfly.test.security.common.elytron.SimpleServerSslContext;
import org.wildfly.test.security.common.elytron.SimpleTrustManager;
import org.wildfly.test.security.common.kerberos.KerberosSystemPropertiesSetupTask;
import org.wildfly.test.security.common.other.AccessIdentityConfigurator;
import org.wildfly.test.security.common.other.HttpMgmtConfigurator;
import org.wildfly.test.security.common.other.SimpleMgmtNativeInterface;
import org.wildfly.test.security.common.other.SimpleSocketBinding;

/**
 * Tests Elytron Kerberos remoting (GSSAPI and GS2-KRB5* SASL mechanism) through HTTP management interface.
 *
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
@org.wildfly.core.testrunner.ServerSetup({ AbstractKerberosMgmtSaslTestBase.Krb5ConfServerSetupTask.class, //
        KerberosSystemPropertiesSetupTask.class, //
        AbstractKerberosMgmtSaslTestBase.DirectoryServerSetupTask.class, //
        AbstractKerberosMgmtSaslTestBase.KeyMaterialSetup.class, //
        KerberosHttpMgmtSaslTestCase.ServerSetup.class })
public class KerberosHttpMgmtSaslTestCase extends AbstractKerberosMgmtSaslTestBase {

    private static final String NAME = KerberosHttpMgmtSaslTestCase.class.getSimpleName();

    private static final ModelControllerClient client = ModelControllerClient.Factory
            .create(new ModelControllerClientConfiguration.Builder().setHostName(CoreUtils.getDefaultHost(false))
                    .setPort(PORT_NATIVE).setProtocol("remote").setConnectionTimeout(CONNECTION_TIMEOUT_IN_MS).build());

    /**
     * Configures test sasl-server-factory to use given mechanism. It also enables/disables SSL based on provided flag.
     */
    @Override
    protected AutoCloseable configureSaslMechanismOnServer(String mechanism, boolean withSsl) throws Exception {
        HttpMgmtConfigurator.Builder httpMgmtConfigBuilder = HttpMgmtConfigurator.builder().withSaslAuthenticationFactory(NAME);
        configurePatternFilter(mechanism);
        if (withSsl) {
            httpMgmtConfigBuilder.withSslContext(NAME).withSecureSocketBinding("management-https");
        }
        final HttpMgmtConfigurator httpMgmtConfig = httpMgmtConfigBuilder.build();
        httpMgmtConfig.create(client, null);

        ServerReload.executeReloadAndWaitForCompletion(client, 30 * 1000, false, "remote",
                TestSuiteEnvironment.getServerAddress(), PORT_NATIVE);
        return () -> {
            httpMgmtConfig.remove(client, null);
            ServerReload.executeReloadAndWaitForCompletion(client, 30 * 1000, false, "remote",
                    TestSuiteEnvironment.getServerAddress(), PORT_NATIVE);
        };
    }

    /**
     * @param mechanism
     * @throws Exception
     */
    private void configurePatternFilter(String mechanism) throws Exception {
        String patternFilter = mechanism + "$";
        ModelNode op = Util.createEmptyOperation("write-attribute",
                PathAddress.pathAddress().append("subsystem", "elytron").append("configurable-sasl-server-factory", NAME));
        op.get("name").set("filters");
        ModelNode newValue = new ModelNode();
        newValue.get("pattern-filter").set(patternFilter);
        op.get("value").add(newValue);
        CoreUtils.applyUpdate(op, client);
    }

    @Override
    protected ModelNode executeWhoAmI(boolean withTls) throws IOException, GeneralSecurityException {
        ModelControllerClientConfiguration.Builder clientConfigBuilder = new ModelControllerClientConfiguration.Builder()
                .setHostName(CoreUtils.getDefaultHost(false)).setPort(withTls ? 9993 : 9990)
                .setProtocol(withTls ? "https-remoting" : "http-remoting").setConnectionTimeout(CONNECTION_TIMEOUT_IN_MS);
        if (withTls) {
            clientConfigBuilder.setSslContext(sslFactory.create());
        }
        ModelControllerClient client = ModelControllerClient.Factory.create(clientConfigBuilder.build());

        ModelNode operation = new ModelNode();
        operation.get("operation").set("whoami");
        operation.get("verbose").set("true");

        return client.execute(operation);
    }


    /**
     * Setup task which configures Elytron security domains and remoting connectors for this test.
     */
    public static class ServerSetup extends TestRunnerConfigSetupTask {

        @Override
        protected ConfigurableElement[] getConfigurableElements() {
            List<ConfigurableElement> elements = new ArrayList<>();

            elements.add(ConstantPermissionMapper.builder().withName(NAME)
                    .withPermissions(PermissionRef.fromPermission(new LoginPermission())).build());

            final CredentialReference credentialReference = CredentialReference.builder().withClearText(KEYSTORE_PASSWORD)
                    .build();

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

            // dir-context
            elements.add(DirContext.builder().withName(NAME).withUrl(LDAP_URL).withPrincipal("uid=admin,ou=system")
                    .withCredentialReference(CredentialReference.builder().withClearText("secret").build()).build());
            // ldap-realm
            elements.add(LdapRealm.builder()
                    .withName(NAME).withDirContext(NAME).withIdentityMapping(IdentityMapping.builder()
                            .withRdnIdentifier("krb5PrincipalName").withSearchBaseDn("ou=Users,dc=wildfly,dc=org").build())
                    .build());
            // security-domain
            elements.add(SimpleSecurityDomain.builder().withName(NAME).withDefaultRealm(NAME).withPermissionMapper(NAME)
                    .withRealms(SecurityDomainRealm.builder().withRealm(NAME).build()).build());
            elements.add(AccessIdentityConfigurator.builder().build());

            // kerberos-security-factory
            elements.add(
                    KerberosSecurityFactory.builder()
                            .withName(NAME).withPrincipal(Krb5ConfServerSetupTask.REMOTE_PRINCIPAL).withCliPath(CliPath
                                    .builder().withPath(Krb5ConfServerSetupTask.REMOTE_KEYTAB_FILE.getAbsolutePath()).build())
                            .build());

            // SASL Authentication
            elements.add(SimpleConfigurableSaslServerFactory.builder().withName(NAME).withSaslServerFactory("elytron").build());
            MechanismConfiguration.Builder mechConfigBuilder = MechanismConfiguration.builder()
                    .addMechanismRealmConfiguration(MechanismRealmConfiguration.builder().withRealmName(NAME).build())
                    .withCredentialSecurityFactory(NAME);
            elements.add(SimpleSaslAuthenticationFactory.builder().withName(NAME).withSaslServerFactory(NAME)
                    .withSecurityDomain(NAME).addMechanismConfiguration(mechConfigBuilder.withMechanismName("GSSAPI").build())
                    .addMechanismConfiguration(mechConfigBuilder.withMechanismName("GS2-KRB5").build())
                    .addMechanismConfiguration(mechConfigBuilder.withMechanismName("GS2-KRB5-PLUS").build())
                    .addMechanismConfiguration(MechanismConfiguration.builder().build()).build());

            // Socket binding and native management interface
            elements.add(SimpleSocketBinding.builder().withName(NAME).withPort(PORT_NATIVE).build());
            elements.add(SimpleMgmtNativeInterface.builder().withSocketBinding(NAME)
                    .withSaslAuthenticationFactory("management-sasl-authentication").build());

            // SSLContext
            elements.add(SimpleServerSslContext.builder().withName(NAME).withKeyManagers("server-keymanager")
                    .withTrustManagers("server-trustmanager").build());

            // HTTP(s) management interface
            // elements.add(HttpMgmtConfigurator.builder().withSaslAuthenticationFactory(NAME).build());

            return elements.toArray(new ConfigurableElement[elements.size()]);
        }
    }
}
