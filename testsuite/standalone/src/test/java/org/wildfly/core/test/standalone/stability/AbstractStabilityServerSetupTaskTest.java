/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
