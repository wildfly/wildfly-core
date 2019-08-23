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

import static org.jboss.as.test.integration.security.common.SecurityTestConstants.KEYSTORE_PASSWORD;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.runner.RunWith;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.wildfly.core.testrunner.WildflyTestRunner;
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
import org.wildfly.test.security.common.other.SimpleMgmtNativeInterface;
import org.wildfly.test.security.common.other.SimpleSocketBinding;

/**
 * Tests Elytron Kerberos remoting (GSSAPI and GS2-KRB5* SASL mechanism) through management interface.
 *
 * @author Josef Cacek
 */
@RunWith(WildflyTestRunner.class)
@org.wildfly.core.testrunner.ServerSetup({ AbstractKerberosMgmtSaslTestBase.Krb5ConfServerSetupTask.class, //
        KerberosSystemPropertiesSetupTask.class, //
        AbstractKerberosMgmtSaslTestBase.DirectoryServerSetupTask.class, //
        AbstractKerberosMgmtSaslTestBase.KeyMaterialSetup.class, //
        KerberosNativeMgmtSaslTestCase.ServerSetup.class })
public class KerberosNativeMgmtSaslTestCase extends AbstractKerberosMgmtSaslTestBase {


    private static final String NAME = KerberosNativeMgmtSaslTestCase.class.getSimpleName();

    @BeforeClass
    public static void noJDK14Plus() {
        Assume.assumeFalse("Avoiding JDK 14 due to https://issues.jboss.org/browse/WFCORE-4532", "14".equals(System.getProperty("java.specification.version")));
    }

    /**
     * Configures test sasl-server-factory to use given mechanism. It also enables/disables SSL based on provided flag.
     */
    @Override
    protected AutoCloseable configureSaslMechanismOnServer(String mechanism, boolean withSsl) throws Exception {
        String patternFilter = mechanism + "\\$";
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine(String.format(
                    "/subsystem=elytron/configurable-sasl-server-factory=%s:write-attribute(name=filters, value=[{pattern-filter=%s}])",
                    NAME, patternFilter));
            String sslContextCli = withSsl ? String.format(
                    "/core-service=management/management-interface=native-interface:write-attribute(name=ssl-context, value=%s)",
                    NAME) : "/core-service=management/management-interface=native-interface:write-attribute(name=ssl-context)";
            cli.sendLine(sslContextCli);
        }
        ServerReload.reloadIfRequired(TestSuiteEnvironment.getModelControllerClient());
        return ()->LOGGER.debug("No cleanup needed after the test.");
    }

    @Override
    protected ModelNode executeWhoAmI(boolean withTls) throws IOException, GeneralSecurityException {
        ModelControllerClientConfiguration.Builder clientConfigBuilder = new ModelControllerClientConfiguration.Builder()
                .setHostName(CoreUtils.getDefaultHost(false)).setPort(PORT_NATIVE)
                .setProtocol("remote").setConnectionTimeout(CONNECTION_TIMEOUT_IN_MS);
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
            elements.add(LdapRealm
                    .builder().withName(NAME).withDirContext(NAME).withIdentityMapping(IdentityMapping.builder()
                            .withRdnIdentifier("krb5PrincipalName").withSearchBaseDn("ou=Users,dc=wildfly,dc=org").build())
                    .build());
            // security-domain
            elements.add(SimpleSecurityDomain.builder().withName(NAME).withDefaultRealm(NAME).withPermissionMapper(NAME)
                    .withRealms(SecurityDomainRealm.builder().withRealm(NAME).build()).build());
            elements.add(AccessIdentityConfigurator.builder().build());

            // kerberos-security-factory
            elements.add(KerberosSecurityFactory.builder().withName(NAME)
                    .withPrincipal(Krb5ConfServerSetupTask.REMOTE_PRINCIPAL)
                    .withCliPath(
                            CliPath.builder().withPath(Krb5ConfServerSetupTask.REMOTE_KEYTAB_FILE.getAbsolutePath()).build())
                    .build());

            // SASL Authentication
            elements.add(SimpleConfigurableSaslServerFactory.builder().withName(NAME).withSaslServerFactory("elytron").build());
            MechanismConfiguration.Builder mechConfigBuilder = MechanismConfiguration.builder()
                    .addMechanismRealmConfiguration(MechanismRealmConfiguration.builder().withRealmName(NAME).build())
                    .withCredentialSecurityFactory(NAME);
            elements.add(SimpleSaslAuthenticationFactory.builder().withName(NAME).withSaslServerFactory(NAME)
                    .withSecurityDomain(NAME).addMechanismConfiguration(mechConfigBuilder.withMechanismName("GSSAPI").build())
                    .addMechanismConfiguration(mechConfigBuilder.withMechanismName("GS2-KRB5").build())
                    .addMechanismConfiguration(mechConfigBuilder.withMechanismName("GS2-KRB5-PLUS").build()).build());

            // SSLContext
            elements.add(SimpleServerSslContext.builder().withName(NAME).withKeyManagers("server-keymanager")
                    .withTrustManagers("server-trustmanager").build());

            // Socket binding and native management interface
            elements.add(SimpleSocketBinding.builder().withName(NAME).withPort(PORT_NATIVE).build());
            elements.add(SimpleMgmtNativeInterface.builder().withSocketBinding(NAME).withSaslAuthenticationFactory(NAME)
                    .withSslContext(NAME).build());

            return elements.toArray(new ConfigurableElement[elements.size()]);
        }
    }
}
