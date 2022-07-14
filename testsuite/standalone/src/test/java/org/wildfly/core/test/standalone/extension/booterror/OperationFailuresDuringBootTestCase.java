/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.wildfly.core.test.standalone.extension.booterror;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.IOException;
import javax.inject.Inject;

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
 * @author Brian Stansberry
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(ServerReload.SetupTask.class)
public class OperationFailuresDuringBootTestCase {

    private static final String MODULE_NAME = "extensionbooterrormodule";

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
    public void testBootErrorsFromNonBootOperations() throws Exception {
        ModelControllerClient client = managementClient.getControllerClient();

        ModelNode bootErrors = readBootErrors(managementClient);
        Assert.assertTrue(bootErrors.asString(),!bootErrors.isDefined() || bootErrors.asInt() == 0);

        //Add extension and subsystem
        executeOperation(client, ADD, PathAddress.pathAddress(PathElement.pathElement(EXTENSION, MODULE_NAME)));
        executeOperation(client, ADD, PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME)));
        executeOperation(client, ADD, PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME)).append("key", "value"));

        // Confirm the service ran a failed op during add of the subsystem
        ModelNode resourceTree = readResource(client);
        Assert.assertTrue(resourceTree.toString(), resourceTree.get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());
        Assert.assertTrue(resourceTree.toString(), resourceTree.get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME, RootResourceDefinition.ATTRIBUTE.getName()).isDefined());

        // Sanity check that a post-boot op doesn't magically record boot errors
        bootErrors = readBootErrors(managementClient);
        Assert.assertTrue(bootErrors.toString(),!bootErrors.isDefined() || bootErrors.asInt() == 0);

        // Reload so our subsystem now gets added during boot
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        resourceTree = readResource(client);
        // the service again ran a failed op during add of the subsystem
        Assert.assertTrue(resourceTree.toString(), resourceTree.get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());
        Assert.assertTrue(resourceTree.get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).toString(), resourceTree.get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME, RootResourceDefinition.ATTRIBUTE.getName()).isDefined());

        // Now we expect a boot error, but only from the child resource, not the parent that executes a separate op from the service
        bootErrors = readBootErrors(managementClient);
        Assert.assertEquals(bootErrors.toString(),1, bootErrors.asInt());
        Assert.assertEquals(bootErrors.toString(), RootResourceDefinition.BOOT_ERROR_MSG, bootErrors.get(0).get(FAILURE_DESCRIPTION).asString());

            //Remove subsystem and extension. Do it in a composite as a WFCORE-3385 check
        ModelNode op = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = op.get(STEPS);
        steps.add(Util.createEmptyOperation(REMOVE, PathAddress.pathAddress(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME)));
        steps.add(Util.createEmptyOperation(REMOVE, PathAddress.pathAddress(EXTENSION, MODULE_NAME)));
        ModelNode response = client.execute(op);
        Assert.assertEquals(response.toString(), "success", response.get(OUTCOME).asString());
        Assert.assertFalse(readResource(client).get(EXTENSION, MODULE_NAME).isDefined());
        Assert.assertFalse(readResource(client).get(SUBSYSTEM, TestExtension.SUBSYSTEM_NAME).isDefined());

    }

    private ModelNode readBootErrors(ManagementClient managementClient) throws IOException {
        return executeOperation(managementClient.getControllerClient(), "read-boot-errors", PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT));
    }

    private ModelNode executeOperation(ModelControllerClient client, String name, PathAddress address) throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set(name);
        op.get(OP_ADDR).set(address.toModelNode());

        ModelNode response = client.execute(op);
        Assert.assertEquals(response.asString(), "success", response.get(OUTCOME).asString());
        Assert.assertFalse(response.asString(), response.get(FAILURE_DESCRIPTION).isDefined());
        return response.get(RESULT);
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

