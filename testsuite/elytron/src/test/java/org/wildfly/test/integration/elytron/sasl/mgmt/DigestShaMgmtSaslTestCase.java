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

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.security.common.TestRunnerConfigSetupTask;
import org.wildfly.test.security.common.elytron.ConfigurableElement;

/**
 * Tests Digest SHA1 SASL mechanism used for management interface.
 *
 * @author Josef Cacek
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({ DigestShaMgmtSaslTestCase.ServerSetup.class })
public class DigestShaMgmtSaslTestCase extends AbstractMgmtSaslTestBase {

    private static final String MECHANISM = "DIGEST-SHA";

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
        assertDigestMechPassWhoAmI(MECHANISM, DIGEST_ALGORITHM_SHA);
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
