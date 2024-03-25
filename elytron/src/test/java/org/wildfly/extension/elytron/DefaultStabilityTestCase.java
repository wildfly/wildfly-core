/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

public class DefaultStabilityTestCase extends AbstractSubsystemTest {

    private static final String DYNAMIC_SSL_CLIENT_CONTEXT_NAME = "dcsc";
    private static final String SUBSYSTEM = "subsystem";
    private static final String ELYTRON = "elytron";

    public DefaultStabilityTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension(), Stability.DEFAULT);
    }

    private static KernelServices services = null;

    @Before
    public void initServices() throws Exception {
        TestEnvironment testEnvironment = new TestEnvironment(Stability.DEFAULT);
        services = super.createKernelServicesBuilder(testEnvironment).setSubsystemXmlResource("authentication-client.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    @Test
    public void testAddDynamicClientSSLContextFailsInDefaultStability() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR)
                .add(SUBSYSTEM, ELYTRON).add(ElytronDescriptionConstants.DYNAMIC_CLIENT_SSL_CONTEXT, DYNAMIC_SSL_CLIENT_CONTEXT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT).set("ac");
        ModelNode response = services.executeOperation(operation);

        if (!response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }

        if (!response.get("failure-description").asString().contains("No resource definition is registered for address")) {
            Assert.fail(response.toJSONString(false));
        }
    }
}