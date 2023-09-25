/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.domain.extension.TestAliasReadResourceDescriptionAddressExtension.MODULE_NAME;
import static org.jboss.as.test.integration.domain.extension.TestAliasReadResourceDescriptionAddressExtension.SUBSYSTEM_NAME;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.executeForResult;

import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that a read-resource-description with a wildcard in the path for an alias returns the alias address in the
 * address list
 *
 * @author Kabir Khan
 */
public class ReadAliasResourceDescriptionAddressTestCase {

    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static final PathAddress PROFILE_SINGLETON_ALIAS = PathAddress.pathAddress(PROFILE, "default")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("singleton-alias", "uno");
    private static final PathAddress PROFILE_WILDCARD_ALIAS = PathAddress.pathAddress(PROFILE, "default")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("wildcard-alias", "*");
    private static final PathAddress SERVER_SINGLETON_ALIAS = PathAddress.pathAddress(HOST, "primary")
            .append(SERVER, "main-one")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("singleton-alias", "uno");
    private static final PathAddress SERVER_WILDCARD_ALIAS = PathAddress.pathAddress(HOST, "primary")
            .append(SERVER, "main-one")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("wildcard-alias", "*");
    private static final PathAddress PROFILE_SINGLETON_REAL = PathAddress.pathAddress(PROFILE, "default")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("singleton", "one");
    private static final PathAddress PROFILE_WILDCARD_REAL = PathAddress.pathAddress(PROFILE, "default")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("wildcard", "*");
    private static final PathAddress SERVER_SINGLETON_REAL = PathAddress.pathAddress(HOST, "primary")
            .append(SERVER, "main-one")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("singleton", "one");
    private static final PathAddress SERVER_WILDCARD_REAL = PathAddress.pathAddress(HOST, "primary")
            .append(SERVER, "main-one")
            .append(SUBSYSTEM, SUBSYSTEM_NAME)
            .append("thing", "*")
            .append("wildcard", "*");

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ReadAliasResourceDescriptionAddressTestCase.class.getSimpleName());
        primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        // Initialize the test extension
        ExtensionSetup.initializeTestAliasReadResourceAddressExtension(testSupport);

        ModelNode addExtension = Util.createAddOperation(
                PathAddress.pathAddress(EXTENSION, MODULE_NAME));

        executeForResult(addExtension, primaryClient);

        ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(
                PathElement.pathElement(PROFILE, "default"),
                PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME)));
        executeForResult(addSubsystem, primaryClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        ModelNode removeSubsystem = Util.createRemoveOperation(
                PathAddress.pathAddress(
                        PathElement.pathElement(PROFILE, "default"),
                        PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME)));
        executeForResult(removeSubsystem, primaryClient);

        ModelNode removeExtension = Util.createRemoveOperation(
                PathAddress.pathAddress(EXTENSION, MODULE_NAME));
        executeForResult(removeExtension, primaryClient);

        testSupport = null;
        primaryClient = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testAliasAddressReturned() throws Exception {
        checkAddressReturned(PROFILE_SINGLETON_ALIAS, PROFILE_WILDCARD_ALIAS, SERVER_SINGLETON_ALIAS, SERVER_WILDCARD_ALIAS);
    }

    @Test
    public void testRealAddressReturned() throws Exception {
        checkAddressReturned(PROFILE_SINGLETON_REAL, PROFILE_WILDCARD_REAL, SERVER_SINGLETON_REAL, SERVER_WILDCARD_REAL);
    }

    private void checkAddressReturned(PathAddress profileSingleton, PathAddress profileWildcard,
                                      PathAddress serverSingleton, PathAddress serverWildcard) throws Exception {
        ModelNode readProfileSingleton = createRrd(profileSingleton);
        checkAddress(profileSingleton, false,
                DomainTestUtils.executeForResult(readProfileSingleton, primaryClient));

        ModelNode readProfileWildcard = createRrd(profileWildcard);
        checkAddress(profileWildcard, false,
                DomainTestUtils.executeForResult(readProfileWildcard, primaryClient));

        ModelNode readServerSingleton = createRrd(serverSingleton);
        checkAddress(serverSingleton, true,
                DomainTestUtils.executeForResult(readServerSingleton, primaryClient));

        ModelNode readServerWildcard = createRrd(serverWildcard);
        checkAddress(serverWildcard, true,
                DomainTestUtils.executeForResult(readServerWildcard, primaryClient));
    }

    @Test
    public void testAddressReturnedComposite() throws Exception {
        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS);

        //Mix up server/profile and real/alias a bit
        ModelNode[] ops = new ModelNode[]{
                createRrd(SERVER_SINGLETON_REAL),
                createRrd(PROFILE_SINGLETON_ALIAS),
                createRrd(SERVER_SINGLETON_ALIAS),
                createRrd(PROFILE_SINGLETON_REAL),
                createRrd(PROFILE_WILDCARD_ALIAS),
                createRrd(SERVER_WILDCARD_REAL),
                createRrd(SERVER_SINGLETON_ALIAS),
                Util.createEmptyOperation(READ_RESOURCE_OPERATION, PROFILE_WILDCARD_ALIAS),
                createRrd(PROFILE_WILDCARD_REAL)};

        for (ModelNode op: ops) {
            steps.add(op.clone());
        }
        ModelNode result = DomainTestUtils.executeForResult(composite, primaryClient);

        Assert.assertEquals(ops.length, result.keys().size());
        for (int i = 0; i < ops.length; i++) {
            ModelNode stepResult = result.get("step-" + (i + 1));
            Assert.assertTrue(stepResult.isDefined());
            Assert.assertEquals(SUCCESS, stepResult.get(OUTCOME).asString());
            if (ops[i].get(OP).asString().equals(READ_RESOURCE_DESCRIPTION_OPERATION)) {
                PathAddress addr = PathAddress.pathAddress(ops[i].get(OP_ADDR));
                boolean server = addr.getElement(0).getKey().equals(HOST);
                checkAddress(addr, server, stepResult.get(RESULT));
            }
        }
    }

    private void checkAddress(PathAddress expectedAddress, boolean server, ModelNode result) {
        if (server) {
            expectedAddress = expectedAddress.subAddress(2);
        }
        List<ModelNode> list = result.asList();
        Assert.assertEquals(1, list.size());
        ModelNode entry = list.get(0);
        Assert.assertEquals(SUCCESS, entry.get(OUTCOME).asString());
        Assert.assertTrue(entry.hasDefined(ADDRESS));
        PathAddress returnedAddress = PathAddress.pathAddress(entry.get(ADDRESS));
        Assert.assertEquals(expectedAddress, returnedAddress);
    }

    private ModelNode createRrd(PathAddress pathAddress) {
        return Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, pathAddress);
    }
}
