/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.api;

import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;


/**
 * Test supported remoting libraries combinations.
 * Tests are inherited from the superclass {@link ClientCompatibilityUnitTestBase}
 *
 * @author Emanuel Muckenhuber
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(ClientCompatibilityUnitTestCase.ClientCompatibilityUnitTestCaseServerSetup.class)
public class ClientCompatibilityUnitTestCase extends ClientCompatibilityUnitTestBase {
    static class ClientCompatibilityUnitTestCaseServerSetup extends ClientCompatibilityUnitTestBase.ClientCompatibilityServerSetup implements ServerSetupTask {

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            super.setup(managementClient.getControllerClient());
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {
            super.tearDown(managementClient.getControllerClient());
        }
    }
}
