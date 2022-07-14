/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2021 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * This uses the TEST_ENVIRONMENT_VARIABLE environment variable set up in pom.xml
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildFlyRunner.class)
public class ResolveExpressionFromEnvironmentVariableTestCase {

    @Inject
    private static ManagementClient managementClient;

    @Test
    public void testResolveExpressionFromEnvironmentVariable() throws Exception {
        checkExpression("Hello world", "${test.environment-variable}");
    }

    @Test
    public void testResolveExpressionFromEnvironmentVariableOverriddenBySystemProperty() throws Exception {
        PathAddress propAddr = PathAddress.pathAddress("system-property", "test.environment-variable");
        ModelNode addProp = Util.createAddOperation(propAddr);
        addProp.get(VALUE).set("Hola mundo");
        managementClient.executeForResult(addProp);
        try {
            checkExpression("Hola mundo", "${test.environment-variable}");
        } finally {
            ModelNode removeProp = Util.createRemoveOperation(propAddr);
            managementClient.executeForResult(removeProp);
        }
    }


    @Test
    public void testResolveExpressionFromLegacyEnvironmentVariable() throws Exception {
        PathAddress propAddr = PathAddress.pathAddress("system-property", "test.environment-variable");
        ModelNode addProp = Util.createAddOperation(propAddr);
        addProp.get(VALUE).set("Hola mundo");
        managementClient.executeForResult(addProp);
        try {
            checkExpression("Hello world", "${env.TEST_ENVIRONMENT_VARIABLE}");
        } finally {
            ModelNode removeProp = Util.createRemoveOperation(propAddr);
            managementClient.executeForResult(removeProp);
        }
    }

    private void checkExpression(String expected, String expression) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set("resolve-expression");
        op.get(OP_ADDR).setEmptyList();
        op.get("expression").set(expression);
        ModelNode result = managementClient.executeForResult(op);
        Assert.assertEquals(expected, result.asString());
    }
}
