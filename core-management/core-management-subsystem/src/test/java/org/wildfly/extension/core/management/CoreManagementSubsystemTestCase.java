/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat Inc.
 */
@RunWith(Parameterized.class)
public class CoreManagementSubsystemTestCase extends AbstractSubsystemSchemaTest<CoreManagementSubsystemSchema> {

    public static final String PROCESS_STATE_LISTENER = "process-state-listener";
    public static final String PROPERTIES = "properties";
    public static final String TIMEOUT = "timeout";

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<CoreManagementSubsystemSchema> getParameters() {
        return EnumSet.allOf(CoreManagementSubsystemSchema.class);
    }

    public CoreManagementSubsystemTestCase(CoreManagementSubsystemSchema schema) {
        super(CoreManagementExtension.SUBSYSTEM_NAME, new CoreManagementExtension(), schema, CoreManagementSubsystemSchema.CURRENT.get(schema.getStability()));
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization.AdminOnlyHostControllerAdditionalInitialization(getSubsystemSchema());
    }

    @Test
    public void testExpressionAttributesResolved() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization()).setSubsystemXml(getSubsystemXml());
        KernelServices kernelServices = builder.build();
        Assert.assertTrue("Subsystem boot failed!", kernelServices.isSuccessfulBoot());

        ModelNode address = Operations.createAddress("subsystem", "core-management");
        ModelNode op = Operations.createReadResourceOperation(address, true);
        op.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).set(true);
        ModelNode result = kernelServices.executeOperation(op).get("result");

        ModelNode processStateListener = result.get(PROCESS_STATE_LISTENER).get("x");
        ModelNode processStateListenerProperties = processStateListener.get(PROPERTIES);

        assertEquals("5000", getValue(processStateListener, TIMEOUT));
        assertEquals("2", getValue(processStateListenerProperties, "bar"));
    }

    private Object getValue(ModelNode node, String attributeName) {
        return node.get(attributeName).asString();
    }
}
