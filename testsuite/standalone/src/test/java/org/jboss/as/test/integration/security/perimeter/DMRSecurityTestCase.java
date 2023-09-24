/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
