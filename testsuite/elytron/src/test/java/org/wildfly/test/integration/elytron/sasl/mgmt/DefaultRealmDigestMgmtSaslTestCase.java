/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.elytron.sasl.mgmt;

import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.NAME;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.PASSWORD_SFX;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.PORT_NATIVE;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.USERNAME;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.assertAuthenticationFails;
import static org.wildfly.test.integration.elytron.sasl.mgmt.AbstractMgmtSaslTestBase.assertWhoAmI;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.ConstantPermissionMapper;
import org.wildfly.test.security.common.elytron.FileSystemRealm;
import org.wildfly.test.security.common.elytron.PermissionRef;
import org.wildfly.test.security.common.elytron.SimpleSaslAuthenticationFactory;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain.SecurityDomainRealm;
import org.wildfly.test.security.common.other.SimpleMgmtNativeInterface;
import org.wildfly.test.security.common.other.SimpleSocketBinding;
import org.wildfly.test.security.common.other.TrustedDomainsConfigurator;

/**
 * Tests that the Elytron DIGEST-* SASL mechanisms work also with a default realm name (usually hostname is used).
 *
 * @see <a href="https://issues.jboss.org/browse/ELY-1186">ELY-1186</a>
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({ DefaultRealmDigestMgmtSaslTestCase.ServerSetup.class })
public class DefaultRealmDigestMgmtSaslTestCase {

    @Test
    public void testDigestMd5() throws Exception {
        assertDefaultRealmWorks("DIGEST-MD5");
    }

    @Test
    public void testDigestSha() throws Exception {
        assertDefaultRealmWorks("DIGEST-SHA");
    }

    @Test
    public void testDigestSha256() throws Exception {
        assertDefaultRealmWorks("DIGEST-SHA-256");
    }

    @Test
    public void testDigestSha384() throws Exception {
        assertDefaultRealmWorks("DIGEST-SHA-384");
    }

    @Test
    public void testDigestSha512() throws Exception {
        assertDefaultRealmWorks("DIGEST-SHA-512");
    }

    /**
     * Tests if DIGEST-* mechanism with default realm used works correctly for both valid and invalid username/password
     * combinations.
     *
     * @param mechanism DIGEST mechanism name
     */
    private void assertDefaultRealmWorks(String mechanism) throws Exception {
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine(String.format(
                    "/subsystem=elytron/sasl-authentication-factory=%s:write-attribute(name=mechanism-configurations, value=[{mechanism-name=%s}])",
                    NAME, mechanism));
        }
        ServerReload.reloadIfRequired(TestSuiteEnvironment.getModelControllerClient());

        AuthenticationConfiguration authnCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(mechanism)).useName(USERNAME)
                .usePassword(USERNAME + PASSWORD_SFX);
        AuthenticationContext.empty().with(MatchRule.ALL, authnCfg).run(() -> assertWhoAmI(USERNAME));

        authnCfg = AuthenticationConfiguration.empty().setSaslMechanismSelector(SaslMechanismSelector.fromString(mechanism))
                .useName("noSuchUser").usePassword("aPassword");
        AuthenticationContext.empty().with(MatchRule.ALL, authnCfg).run(() -> assertAuthenticationFails(
                "Authentication should fail when the user doesn't exist in the security-realm"));
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
            elements.add(FileSystemRealm.builder().withName(NAME).withUser(USERNAME, USERNAME + PASSWORD_SFX).build());
            elements.add(SimpleSecurityDomain.builder().withName(NAME).withDefaultRealm(NAME).withPermissionMapper(NAME)
                    .withRealms(SecurityDomainRealm.builder().withRealm(NAME).build()).build());
            elements.add(
                    TrustedDomainsConfigurator.builder().withName("ManagementDomain").withTrustedSecurityDomains(NAME).build());

            elements.add(SimpleSaslAuthenticationFactory.builder().withName(NAME).withSaslServerFactory("elytron")
                    .withSecurityDomain(NAME).build());

            elements.add(SimpleSocketBinding.builder().withName(NAME).withPort(PORT_NATIVE).build());
            elements.add(
                    SimpleMgmtNativeInterface.builder().withSocketBinding(NAME).withSaslAuthenticationFactory(NAME).build());
            return elements.toArray(new ConfigurableElement[elements.size()]);
        }
    }
}
