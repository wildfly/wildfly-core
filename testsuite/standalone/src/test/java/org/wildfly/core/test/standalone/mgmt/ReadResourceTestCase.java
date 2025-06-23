/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt;

import java.io.IOException;

import jakarta.inject.Inject;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
public class ReadResourceTestCase {

    @Inject
    private static ManagementClient managementClient;


    @Test
    public void testCompleteReadResource() throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        operation.get(ModelDescriptionConstants.RECURSIVE).set(true);
        operation.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
        operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(true);
        final ModelNode response = managementClient.getControllerClient().execute(operation);
        Assert.assertEquals(operation.toString(), ModelDescriptionConstants.SUCCESS, response.get(ModelDescriptionConstants.OUTCOME).asString());
        System.out.println(response.get(ModelDescriptionConstants.RESULT, ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.PLATFORM_MBEAN, ModelDescriptionConstants.TYPE));

    }
}
