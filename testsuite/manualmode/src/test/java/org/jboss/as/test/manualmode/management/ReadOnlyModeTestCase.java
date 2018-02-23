/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.management;


import javax.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ReadOnlyModeTestCase {

    @Inject
    private ServerController container;

    @Before
    public void startContainer() throws Exception {
        // Start the server
        container.startReadOnly();
    }

    @Test
    public void testConfigurationNotUpdated() throws Exception {
        ModelNode address = PathAddress.pathAddress("system-property", "read-only").toModelNode();
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            ModelNode op = Operations.createAddOperation(address);
            op.get("value").set(true);
            CoreUtils.applyUpdate(op, client);
            Assert.assertTrue(Operations.readResult(client.execute(Operations.createReadAttributeOperation(address, "value"))).asBoolean());
            container.reload();
            Assert.assertTrue(Operations.readResult(client.execute(Operations.createReadAttributeOperation(address, "value"))).asBoolean());
        }
        container.stop();
        container.startReadOnly();
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            Assert.assertTrue(Operations.getFailureDescription(client.execute(Operations.createReadAttributeOperation(address, "value"))).asString().contains("WFLYCTL0216"));
        }
    }

    @After
    public void stopContainer() throws Exception {
            // Stop the container
            container.stop();
    }
}
