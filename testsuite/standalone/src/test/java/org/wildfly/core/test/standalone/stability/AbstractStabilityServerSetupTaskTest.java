/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.test.standalone.stability;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.server.controller.descriptions.ServerDescriptionConstants.SERVER_ENVIRONMENT;

public abstract class AbstractStabilityServerSetupTaskTest {
    private final Stability desiredStability;

    @Inject
    private ManagementClient managementClient;

    public AbstractStabilityServerSetupTaskTest(Stability desiredStability) {
        this.desiredStability = desiredStability;
    }

    @Test
    public void testStabilityMatchesSetupTask() throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress(CORE_SERVICE, SERVER_ENVIRONMENT), STABILITY);
        ModelNode result = ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
        Stability stability = Stability.fromString(result.asString());
        Assert.assertEquals(desiredStability, stability);
    }

    @Test
    public void testSystemPropertyWasSetByDoSetupCalls() throws Exception {
        ModelNode read = Util.getReadAttributeOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, AbstractStabilityServerSetupTaskTest.class.getName()), VALUE);
        ModelNode result = ManagementOperations.executeOperation(managementClient.getControllerClient(), read);
        Assert.assertEquals(this.getClass().getName(), result.asString());
    }


    protected static <T extends AbstractStabilityServerSetupTaskTest> void addSystemProperty(ManagementClient client, Class<T> clazz) throws Exception {
        ModelNode add = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, AbstractStabilityServerSetupTaskTest.class.getName()));
        add.get(VALUE).set(clazz.getName());
        ManagementOperations.executeOperation(client.getControllerClient(), add);
    }
}
