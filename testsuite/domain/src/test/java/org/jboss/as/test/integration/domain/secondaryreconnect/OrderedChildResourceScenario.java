/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.secondaryreconnect;

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

    private static final PathAddress PRIMARY_SERVER_SUBSYSTEM_ADDRESS =
            PathAddress.pathAddress(HOST, "primary").append(SERVER, "main-one").append(OrderedChildResourceExtension.SUBSYSTEM_PATH);

    private static final PathAddress SECONDARY_SERVER_ADDRESS =
            PathAddress.pathAddress(HOST, "secondary").append(SERVER, "main-three");

    private static final PathAddress SECONDARY_SERVER_SUBSYSTEM_ADDRESS =
            SECONDARY_SERVER_ADDRESS.append(OrderedChildResourceExtension.SUBSYSTEM_PATH);

    //Just to know how much was initialised in the setup method, so we know what to tear down
    private int initialised = 0;


    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        // Initialize the test extension
        ExtensionSetup.initializeOrderedChildResourceExtension(testSupport);
        DomainTestUtils.executeForResult(Util.createAddOperation(PathAddress.pathAddress(EXTENSION_ADDRESS)), primaryClient);
        initialised = 1;
        //Add the subsystem
        DomainTestUtils.executeForResult(Util.createAddOperation(SUBSYSTEM_ADDRESS), primaryClient);
        initialised = 2;

    }

    @Override
    void tearDownDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        if (initialised >=2) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SUBSYSTEM_ADDRESS), primaryClient);
        }
        if (initialised >= 1) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(EXTENSION_ADDRESS)), primaryClient);
        }
    }


    @Override
    void testOnInitialStartup(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        checkDomainChildOrder(primaryClient);
        compareSubsystemModels(primaryClient, secondaryClient);

        //Now add some child resources
        addChild(primaryClient, "Z", -1);
        checkDomainChildOrder(primaryClient, "Z");
        compareSubsystemModels(primaryClient, secondaryClient);

        //Try an indexed add
        addChild(primaryClient, "N", 0);
        checkDomainChildOrder(primaryClient, "N", "Z");
        compareSubsystemModels(primaryClient, secondaryClient);

    }

    @Override
    void testWhilePrimaryInAdminOnly(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Execute an indexed add op on the DC. The DC should change,
        //but the secondary should stay the same since the DC is now in admin-only mode
        addChild(primaryClient, "S", 1);
        //Check the domain model rather than the secondary model since the DC is admin-only
        checkDomainChildOrder(primaryClient, SUBSYSTEM_ADDRESS, "N", "S", "Z");
        checkDomainChildOrder(secondaryClient, SUBSYSTEM_ADDRESS, "N", "Z");
    }

    @Override
    void testAfterReconnect(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Check the secondary again after reboot
        checkDomainChildOrder(primaryClient, "N", "S", "Z");

        // Might need to loop a bit here to make sure that the fresh domain model gets pulled down
        compareSubsystemModels(primaryClient, secondaryClient, true);

        ModelNode reloadServer =
                Util.createEmptyOperation("reload", PathAddress.pathAddress(HOST, "secondary").append(SERVER_CONFIG, "main-three"));
        reloadServer.get("blocking").set(true);
        DomainTestUtils.executeForResult(reloadServer, secondaryClient);
        compareSubsystemModels(primaryClient, secondaryClient);
    }

    private void addChild(DomainClient primaryClient, String childName, int index) throws Exception {
        final ModelNode op =  Util.createAddOperation(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(OrderedChildResourceExtension.CHILD.getKey(), childName)));
        op.get("attr").set(childName.toLowerCase());
        if (index >= 0) {
            op.get(ADD_INDEX).set(index);
        }
        DomainTestUtils.executeForResult(op, primaryClient);
    }


    private void checkDomainChildOrder(DomainClient client, String...childNames ) throws Exception {
        checkDomainChildOrder(client, PRIMARY_SERVER_SUBSYSTEM_ADDRESS, childNames);
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

    private ModelNode compareSubsystemModels(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        return compareSubsystemModels(primaryClient, secondaryClient, false);
    }

    private ModelNode compareSubsystemModels(DomainClient primaryClient, DomainClient secondaryClient, boolean serverReload) throws Exception {
        final ModelNode domain = DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SUBSYSTEM_ADDRESS), primaryClient);

        Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SUBSYSTEM_ADDRESS), secondaryClient));
        Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(PRIMARY_SERVER_SUBSYSTEM_ADDRESS), primaryClient));

        if (!serverReload) {
            //A reconnect with a changed model does not propagate the model changes to the servers, but puts them in reload-required
            Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SECONDARY_SERVER_SUBSYSTEM_ADDRESS), primaryClient));
        }
        String runtimeConfigurationState = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SECONDARY_SERVER_ADDRESS, "runtime-configuration-state"), primaryClient).asString();
        Assert.assertEquals(serverReload ? RELOAD_REQUIRED : "ok", runtimeConfigurationState);
        String serverState = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SECONDARY_SERVER_ADDRESS, "server-state"), primaryClient).asString();
        Assert.assertEquals(serverReload ? RELOAD_REQUIRED : "running", serverState);
        return domain;
    }

    private ModelNode getRecursiveReadResourceOperation(PathAddress address) {
        final ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, address);
        op.get(RECURSIVE).set(true);
        return op;
    }
}
