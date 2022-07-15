/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.security.perimeter;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * This class contains a check that the management api access is secured.
 *
 * @author <a href="mailto:jlanik@redhat.com">Jan Lanik</a>.
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(DisableLocalAuthServerSetupTask.class)
public class DMRSecurityTestCase {

    /**
     * This test checks that CLI access is secured.
     *
     * @throws Exception We do not provide any credentials so the IOException is required to be thrown.
     */
    @Test(expected = java.io.IOException.class)
    public void testConnect() throws Exception {
        try (ModelControllerClient modelControllerClient = TestSuiteEnvironment.getModelControllerClient()) {

            modelControllerClient.execute(Operations.createReadAttributeOperation(new ModelNode().setEmptyList(), "server-state"));
            Assert.fail("Operation should have failed, but was successful");
        }
    }

}
