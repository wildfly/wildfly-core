/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.management.api;

import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;


/**
 * Test supported remoting libraries combinations.
 * Tests are inherited from the superclass {@link ClientCompatibilityUnitTestBase}
 *
 * @author Emanuel Muckenhuber
 */
@RunWith(WildflyTestRunner.class)
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
