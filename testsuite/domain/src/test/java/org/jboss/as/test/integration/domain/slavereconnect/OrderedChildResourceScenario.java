/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.slavereconnect;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.extension.OrderedChildResourceExtension;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

/**
 * Tests ordered child resources on reconnect
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OrderedChildResourceScenario extends ReconnectTestScenario {
    private static final PathAddress EXTENSION_ADDRESS =
            PathAddress.pathAddress(EXTENSION, OrderedChildResourceExtension.MODULE_NAME);

    private static final PathAddress SUBSYSTEM_ADDRESS =
            PathAddress.pathAddress(PROFILE, "default").append(OrderedChildResourceExtension.SUBSYSTEM_PATH);

    private static final PathAddress MASTER_SERVER_SUBSYSTEM_ADDRESS =
            PathAddress.pathAddress(HOST, "master").append(SERVER, "main-one").append(OrderedChildResourceExtension.SUBSYSTEM_PATH);

    private static final PathAddress SLAVE_SERVER_ADDRESS =
            PathAddress.pathAddress(HOST, "slave").append(SERVER, "main-three");

    private static final PathAddress SLAVE_SERVER_SUBSYSTEM_ADDRESS =
            SLAVE_SERVER_ADDRESS.append(OrderedChildResourceExtension.SUBSYSTEM_PATH);

    //Just to know how much was initialised in the setup method, so we know what to tear down
    private int initialised = 0;


    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient masterClient, DomainClient slaveClient) throws Exception {
        // Initialize the test extension
        ExtensionSetup.initializeOrderedChildResourceExtension(testSupport);
        DomainTestUtils.executeForResult(Util.createAddOperation(PathAddress.pathAddress(EXTENSION_ADDRESS)), masterClient);
        initialised = 1;
        //Add the subsystem
        DomainTestUtils.executeForResult(Util.createAddOperation(SUBSYSTEM_ADDRESS), masterClient);
        initialised = 2;

    }

    @Override
    void tearDownDomain(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        if (initialised >=2) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SUBSYSTEM_ADDRESS), masterClient);
        }
        if (initialised >= 1) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(EXTENSION_ADDRESS)), masterClient);
        }
    }


    @Override
    void testOnInitialStartup(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        checkDomainChildOrder(masterClient);
        compareSubsystemModels(masterClient, slaveClient);

        //Now add some child resources
        addChild(masterClient, "Z", -1);
        checkDomainChildOrder(masterClient, "Z");
        compareSubsystemModels(masterClient, slaveClient);

        //Try an indexed add
        addChild(masterClient, "N", 0);
        checkDomainChildOrder(masterClient, "N", "Z");
        compareSubsystemModels(masterClient, slaveClient);

    }

    @Override
    void testWhileMasterInAdminOnly(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Execute an indexed add op on the DC. The DC should change,
        //but the slave should stay the same since the DC is now in admin-only mode
        addChild(masterClient, "S", 1);
        //Check the domain model rather than the slave model since the DC is admin-only
        checkDomainChildOrder(masterClient, SUBSYSTEM_ADDRESS, "N", "S", "Z");
        checkDomainChildOrder(slaveClient, SUBSYSTEM_ADDRESS, "N", "Z");
    }

    @Override
    void testAfterReconnect(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Check the slave again after reboot
        checkDomainChildOrder(masterClient, "N", "S", "Z");

        // Might need to loop a bit here to make sure that the fresh domain model gets pulled down
        compareSubsystemModels(masterClient, slaveClient, true);

        ModelNode reloadServer =
                Util.createEmptyOperation("reload", PathAddress.pathAddress(HOST, "slave").append(SERVER_CONFIG, "main-three"));
        reloadServer.get("blocking").set(true);
        DomainTestUtils.executeForResult(reloadServer, slaveClient);
        compareSubsystemModels(masterClient, slaveClient);
    }

    private void addChild(DomainClient masterClient, String childName, int index) throws Exception {
        final ModelNode op =  Util.createAddOperation(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(OrderedChildResourceExtension.CHILD.getKey(), childName)));
        op.get("attr").set(childName.toLowerCase());
        if (index >= 0) {
            op.get(ADD_INDEX).set(index);
        }
        DomainTestUtils.executeForResult(op, masterClient);
    }


    private void checkDomainChildOrder(DomainClient client, String...childNames ) throws Exception {
        checkDomainChildOrder(client, MASTER_SERVER_SUBSYSTEM_ADDRESS, childNames);
    }

    private void checkDomainChildOrder(DomainClient client, PathAddress address, String...childNames ) throws Exception {
        final ModelNode result = DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(address), client);
        if (childNames.length > 0) {
            Assert.assertTrue(result.hasDefined(OrderedChildResourceExtension.CHILD.getKey()));
            Set<String> childKeys = result.get(OrderedChildResourceExtension.CHILD.getKey()).keys();
            String[] childKeyArray = childKeys.toArray(new String[childKeys.size()]);
            Assert.assertArrayEquals(childNames, childKeyArray);
        } else {
            Assert.assertFalse(result.hasDefined(OrderedChildResourceExtension.CHILD.getKey()));
        }
    }

    private ModelNode compareSubsystemModels(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        return compareSubsystemModels(masterClient, slaveClient, false);
    }

    private ModelNode compareSubsystemModels(DomainClient masterClient, DomainClient slaveClient, boolean serverReload) throws Exception {
        final ModelNode domain = DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SUBSYSTEM_ADDRESS), masterClient);

        Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SUBSYSTEM_ADDRESS), slaveClient));
        Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(MASTER_SERVER_SUBSYSTEM_ADDRESS), masterClient));

        if (!serverReload) {
            //A reconnect with a changed model does not propagate the model changes to the servers, but puts them in reload-required
            Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SLAVE_SERVER_SUBSYSTEM_ADDRESS), masterClient));
        }
        String runtimeConfigurationState = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SLAVE_SERVER_ADDRESS, "runtime-configuration-state"), masterClient).asString();
        Assert.assertEquals(serverReload ? RELOAD_REQUIRED : "ok", runtimeConfigurationState);
        String serverState = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SLAVE_SERVER_ADDRESS, "server-state"), masterClient).asString();
        Assert.assertEquals(serverReload ? RELOAD_REQUIRED : "running", serverState);
        return domain;
    }

    private ModelNode getRecursiveReadResourceOperation(PathAddress address) {
        final ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, address);
        op.get(RECURSIVE).set(true);
        return op;
    }
}
