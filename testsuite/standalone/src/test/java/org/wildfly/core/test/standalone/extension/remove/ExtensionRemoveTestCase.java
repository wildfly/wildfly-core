/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.extension.remove;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(ServerReload.SetupTask.class)
public class ExtensionRemoveTestCase {

    private static final String MODULE_NAME = "extensionremovemodule";

    @Inject
    private ManagementClient managementClient;

    @Before
    public void installExtensionModule() throws IOException {
        ExtensionUtils.createExtensionModule(MODULE_NAME, TestExtension.class);
    }

    @After
    public void removeExtensionModule() {
        ExtensionUtils.deleteExtensionModule(MODULE_NAME);
    }

    @Test
    public void testAddAndRemoveExtension() throws Exception {
        ModelControllerClient client = managementClient.getControllerClient();

            //Check extension and subsystem is not there
            Assert.assertFalse(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
            Assert.assertFalse(readResourceDescription(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());
            Assert.assertFalse(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());

            //Add extension, no subsystem yet
            executeOperation(client, ADD, PathAddress.pathAddress(PathElement.pathElement(EXTENSION, MODULE_NAME)), false);
            Assert.assertTrue(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
            Assert.assertTrue(readResourceDescription(client).get(CHILDREN, SUBSYSTEM, MODEL_DESCRIPTION, TestExtension.SUBSYSTEM_NAME).isDefined());
            Assert.assertFalse(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());

            //Add subsystem
            executeOperation(client, ADD, PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME)), false);
            executeOperation(client, ADD, PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME), PathElement.pathElement("child", "one")), false);
            Assert.assertTrue(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
            Assert.assertTrue(readResourceDescription(client).get(CHILDREN, SUBSYSTEM, MODEL_DESCRIPTION, TestExtension.SUBSYSTEM_NAME).isDefined());
            Assert.assertTrue(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());

            //Should not be possible to remove extension before subsystem is removed
            executeOperation(client, REMOVE, PathAddress.pathAddress(PathElement.pathElement(EXTENSION, MODULE_NAME)), true);
            Assert.assertTrue(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
            Assert.assertTrue(readResourceDescription(client).get(CHILDREN, SUBSYSTEM, MODEL_DESCRIPTION, TestExtension.SUBSYSTEM_NAME).isDefined());
            Assert.assertTrue(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());

            //Remove subsystem and extension. Do it in a composite as a WFCORE-3385 check
        ModelNode op = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = op.get(STEPS);
        steps.add(Util.createEmptyOperation(REMOVE, PathAddress.pathAddress(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME)));
        steps.add(Util.createEmptyOperation(REMOVE, PathAddress.pathAddress(EXTENSION, MODULE_NAME)));
        ModelNode response = client.execute(op);
        Assert.assertEquals(response.toString(), "success", response.get(OUTCOME).asString());
        Assert.assertFalse(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
        Assert.assertFalse(readResourceDescription(client).get(CHILDREN, SUBSYSTEM, MODEL_DESCRIPTION, TestExtension.SUBSYSTEM_NAME).isDefined());
        Assert.assertFalse(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());

    }

    @Test
    public void testRemoveUnexistingExtension() throws Exception {
        ModelControllerClient client = managementClient.getControllerClient();

        //Check extension and subsystem is not there
        Assert.assertFalse(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
        Assert.assertFalse(readResourceDescription(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());
        Assert.assertFalse(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());
        //Remove extension
        executeOperation(client, REMOVE, PathAddress.pathAddress(PathElement.pathElement(EXTENSION, MODULE_NAME)), true);
        Assert.assertFalse(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
        Assert.assertFalse(readResourceDescription(client).get(CHILDREN, SUBSYSTEM, MODEL_DESCRIPTION, TestExtension.SUBSYSTEM_NAME).isDefined());
        Assert.assertFalse(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());
    }

    private void executeOperation(ModelControllerClient client, String name, PathAddress address, boolean fail) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(name);
        op.get(OP_ADDR).set(address.toModelNode());

        ModelNode result = client.execute(op);
        if (!fail) {
            Assert.assertEquals("success", result.get(OUTCOME).asString());
            Assert.assertFalse(result.get(FAILURE_DESCRIPTION).toString(), result.get(FAILURE_DESCRIPTION).isDefined());
        } else {
            Assert.assertEquals("failed", result.get(OUTCOME).asString());
            Assert.assertTrue(result.get(FAILURE_DESCRIPTION).isDefined());
        }
    }

    private ModelNode readResourceDescription(ModelControllerClient client) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
        op.get(RECURSIVE).set(true);

        ModelNode result = client.execute(op);
        Assert.assertFalse(result.hasDefined(FAILURE_DESCRIPTION));
        return result.get(RESULT);
    }

    private ModelNode readResource(ModelControllerClient client) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_RESOURCE_OPERATION);
        op.get(RECURSIVE).set(true);

        ModelNode result = client.execute(op);
        Assert.assertFalse(result.hasDefined(FAILURE_DESCRIPTION));
        return result.get(RESULT);
    }
}

