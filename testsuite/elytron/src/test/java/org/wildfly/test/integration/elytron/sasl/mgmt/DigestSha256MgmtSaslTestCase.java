/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.elytron.sasl.mgmt;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.ConfigurableElement;

/**
 * Tests Digest SHA-256 SASL mechanism used for management interface.
 *
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({ DigestSha256MgmtSaslTestCase.ServerSetup.class })
public class DigestSha256MgmtSaslTestCase extends AbstractMgmtSaslTestBase {

    private static final String MECHANISM = "DIGEST-SHA-256";

    @Override
    protected String getMechanism() {
        return MECHANISM;
    }

    /**
     * Tests that client is able to use mechanism when server allows it.
     */
    @Test
    public void testCorrectMechanismPasses() throws Exception {
        assertMechPassWhoAmI(MECHANISM, USERNAME);
    }

    @Test
    public void testCorrectDigestMechPasses() throws Exception {
        assertDigestMechPassWhoAmI(MECHANISM, DIGEST_ALGORITHM_SHA256);
    }

    /**
     * Setup task which configures Elytron security domains and remoting connectors for this test.
     */
    public static class ServerSetup extends TestRunnerConfigSetupTask {

        @Override
        protected ConfigurableElement[] getConfigurableElements() {
            List<ConfigurableElement> elements = createConfigurableElementsForSaslMech(MECHANISM);
            return elements.toArray(new ConfigurableElement[elements.size()]);
        }
    }
}
