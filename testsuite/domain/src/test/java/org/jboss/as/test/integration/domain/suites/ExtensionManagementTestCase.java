/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRIMARY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.extension.TestExtension;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests management of extensions.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtensionManagementTestCase {

    private static final String ADDRESS = "extension=" + TestExtension.MODULE_NAME;
    private static final PathElement EXTENSION_ELEMENT = PathElement.pathElement(EXTENSION, TestExtension.MODULE_NAME);
    private static final PathAddress EXTENSION_ADDRESS = PathAddress.pathAddress(EXTENSION_ELEMENT);
    private static final PathElement SUBSYSTEM_ELEMENT = PathElement.pathElement(SUBSYSTEM, "1");
    private static final PathAddress PROFILE_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(PROFILE, DEFAULT).append(SUBSYSTEM_ELEMENT);
    private static final PathAddress SERVER_ONE_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(HOST, PRIMARY).append(SERVER, "main-one").append(SUBSYSTEM_ELEMENT);
    private static final PathAddress SERVER_THREE_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(HOST, "secondary").append(SERVER, "main-three").append(SUBSYSTEM_ELEMENT);
    private static final PathAddress SERVER_ONE_EXT_ADDRESS = PathAddress.pathAddress(HOST, PRIMARY).append(SERVER, "main-one").append(EXTENSION_ELEMENT);
    private static final PathAddress SERVER_THREE_EXT_ADDRESS = PathAddress.pathAddress(HOST, "secondary").append(SERVER, "main-three").append(EXTENSION_ELEMENT);

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ExtensionManagementTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
        // Initialize the test extension
        ExtensionSetup.initializeTestExtension(testSupport);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testAddRemoveExtension() throws Exception  {
        ModelNode op = createOpNode(ADDRESS, "add");
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        executeForResult(op, primaryClient);
        extensionVersionTest(primaryClient, null);
        extensionVersionTest(primaryClient, "host=primary/server=main-one");
        extensionVersionTest(primaryClient, "host=secondary/server=main-three");
        DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
        extensionVersionTest(secondaryClient, null);

        op = createOpNode(ADDRESS, "remove");
        executeForResult(op, primaryClient);
        extensionRemovalTest(primaryClient, null);
        extensionRemovalTest(primaryClient, "host=primary/server=main-one");
        extensionRemovalTest(primaryClient, "host=secondary/server=main-three");
        extensionRemovalTest(secondaryClient, null);
    }

    @Test
    public void testExtensionSubsystemComposite() throws Exception {
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        Exception err = null;
        try {
            // 1) Sanity check -- subsystem not there
            ModelNode read = Util.getReadAttributeOperation(PROFILE_SUBSYSTEM_ADDRESS, NAME);
            testBadOp(read);

            // 2) Sanity check -- Confirm secondary has resources
            verifyNotOnSecondary();

            // 3) Sanity check -- Confirm servers no longer have resource
            verifyNotOnServers();

            // 4) sanity check -- subsystem add w/o extension -- fail
            ModelNode subAdd = Util.createAddOperation(PROFILE_SUBSYSTEM_ADDRESS);
            subAdd.get(NAME).set(TestExtension.MODULE_NAME);
            testBadOp(subAdd);

            // 5) ext add + sub add + sub read in composite
            ModelNode extAdd = Util.createAddOperation(EXTENSION_ADDRESS);
            ModelNode goodAdd = buildComposite(extAdd, subAdd, read);
            testGoodComposite(goodAdd);

            // 6) Sanity check -- try read again outside the composite
            ModelNode response = executeOp(read, "success");
            assertTrue(response.toString(), response.has("result"));
            assertEquals(response.toString(), TestExtension.MODULE_NAME, response.get("result").asString());

            // 7) Confirm secondary has resources
            verifyOnSecondary();

            // 8) Confirm servers have the resources
            verifyOnServers();

            // 9) sub remove + ext remove + sub add in composite -- fail
            ModelNode subRemove = Util.createRemoveOperation(PROFILE_SUBSYSTEM_ADDRESS);
            ModelNode extRemove = Util.createRemoveOperation(EXTENSION_ADDRESS);
            ModelNode badRemove = buildComposite(read, subRemove, extRemove, subAdd);
            testBadOp(badRemove);

            // 10) Confirm servers still have resources
            verifyOnServers();

            // 11) sub remove + ext remove in composite
            ModelNode goodRemove = buildComposite(read, subRemove, extRemove);
            response = executeOp(goodRemove, "success");
            validateInvokePublicStep(response, 1, false);

            // 12) Confirm secondary no longer has resources
            verifyNotOnSecondary();

            // 13) Confirm servers no longer have resource
            verifyNotOnServers();

            // 14) confirm ext add + sub add + sub read still works
            testGoodComposite(goodAdd);

            // 15) Sanity check -- try read again outside the composite
            response = executeOp(read, "success");
            assertTrue(response.toString(), response.has("result"));
            assertEquals(response.toString(), TestExtension.MODULE_NAME, response.get("result").asString());

            // 16) Confirm secondary again has resources
            verifyOnSecondary();

            // 17) Confirm servers again have resources
            verifyOnServers();

        } catch (Exception e) {
            err = e;
        } finally {
            //Cleanup
            removeIgnoreFailure(primaryClient, PROFILE_SUBSYSTEM_ADDRESS);
            removeIgnoreFailure(primaryClient, EXTENSION_ADDRESS);
        }

        if (err != null) {
            throw err;
        }
    }

    private void extensionVersionTest(ModelControllerClient client, String addressPrefix) throws Exception {

        String address = addressPrefix == null ? ADDRESS : addressPrefix + '/' + ADDRESS;
        ModelNode op = createOpNode(address, "read-resource");
        op.get("recursive").set(true);
        op.get("include-runtime").set(true);

        ModelNode result = executeForResult(op, client);
        ModelNode subsystems = result.get("subsystem");
        Assert.assertEquals("extension has no subsystems", ModelType.OBJECT, subsystems.getType());
        for (Property subsystem : subsystems.asPropertyList()) {
            String subsystemName = subsystem.getName();
            int version = Integer.parseInt(subsystemName);
            ModelNode value = subsystem.getValue();
            Assert.assertEquals(subsystemName + " has major version", ModelType.INT, value.get("management-major-version").getType());
            Assert.assertEquals(subsystemName + " has minor version", ModelType.INT, value.get("management-minor-version").getType());
            Assert.assertEquals(subsystemName + " has micro version", ModelType.INT, value.get("management-micro-version").getType());
            Assert.assertEquals(subsystemName + " has namespaces", ModelType.LIST, value.get("xml-namespaces").getType());
            Assert.assertEquals(subsystemName + " has correct major version", version, value.get("management-major-version").asInt());
            Assert.assertEquals(subsystemName + " has correct minor version", version, value.get("management-minor-version").asInt());
            Assert.assertEquals(subsystemName + " has correct micro version", version, value.get("management-micro-version").asInt());
            Assert.assertTrue(subsystemName + " has more than zero namespaces", value.get("xml-namespaces").asInt() > 0);
        }
    }

    private void extensionRemovalTest(ModelControllerClient client, String addressPrefix) throws Exception {
        ModelNode op = createOpNode(addressPrefix, "read-children-names");
        op.get("child-type").set("extension");
        List<ModelNode> result = executeForResult(op, client).asList();
        for (ModelNode extension : result) {
            Assert.assertFalse(TestExtension.MODULE_NAME.equals(extension.asString()));
        }
    }

    private static ModelNode createOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        ModelNode list = op.get("address").setEmptyList();
        if (address != null) {
            String [] pathSegments = address.split("/");
            for (String segment : pathSegments) {
                String[] elements = segment.split("=");
                list.add(elements[0], elements[1]);
            }
        }
        op.get("operation").set(operation);
        return op;
    }

    private static ModelNode executeForResult(final ModelNode op, final ModelControllerClient modelControllerClient) throws IOException, MgmtOperationException {
        try {
            return DomainTestUtils.executeForResult(op, modelControllerClient);
        } catch (MgmtOperationException e) {
            System.out.println(" Op failed:");
            System.out.println(e.getOperation());
            System.out.println("with result");
            System.out.println(e.getResult());
            throw e;
        }
    }

    private ModelNode executeOp(ModelNode op, String outcome) throws IOException {
        ModelNode response = domainPrimaryLifecycleUtil.getDomainClient().execute(op);
        assertTrue(response.toString(), response.hasDefined(OUTCOME));
        assertEquals(response.toString(), outcome, response.get(OUTCOME).asString());
        return response;
    }

    private void testGoodComposite(ModelNode composite) throws IOException {
        ModelNode result = executeOp(composite, "success");
        validateInvokePublicStep(result, 3, false);
    }

    private void testBadOp(ModelNode badOp) throws IOException {
        ModelNode response = executeOp(badOp, "failed");
        String msg = response.toString();
        assertTrue(msg, response.has("failure-description"));
        ModelNode failure = response.get("failure-description");
        assertTrue(msg, failure.asString().contains("WFLYCTL0030"));
    }

    private void verifyOnSecondary() throws IOException, MgmtOperationException {
        DomainClient client = domainSecondaryLifecycleUtil.getDomainClient();
        ModelNode readExt = Util.createEmptyOperation(READ_RESOURCE_OPERATION, EXTENSION_ADDRESS);
        ModelNode result = executeForResult(readExt, client);
        assertEquals(result.toString(), TestExtension.MODULE_NAME, result.get(MODULE).asString());
        ModelNode read = Util.getReadAttributeOperation(PROFILE_SUBSYSTEM_ADDRESS, NAME);
        result = executeForResult(read, client);
        assertEquals(result.toString(), TestExtension.MODULE_NAME, result.asString());
    }

    private void verifyOnServers() throws IOException, MgmtOperationException {
        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode readExtOne = Util.getReadAttributeOperation(SERVER_ONE_EXT_ADDRESS, MODULE);
        ModelNode result = executeForResult(readExtOne, client);
        assertEquals(result.toString(), TestExtension.MODULE_NAME, result.asString());

        ModelNode readSubOne = Util.getReadAttributeOperation(SERVER_ONE_SUBSYSTEM_ADDRESS, NAME);
        result = executeForResult(readSubOne, client);
        assertEquals(result.toString(), TestExtension.MODULE_NAME, result.asString());

        ModelNode readExtThree = Util.getReadAttributeOperation(SERVER_THREE_EXT_ADDRESS, MODULE);
        result = executeForResult(readExtThree, client);
        assertEquals(result.toString(), TestExtension.MODULE_NAME, result.asString());

        ModelNode readSubThree = Util.getReadAttributeOperation(SERVER_THREE_SUBSYSTEM_ADDRESS, NAME);
        result = executeForResult(readSubThree, client);
        assertEquals(result.toString(), TestExtension.MODULE_NAME, result.asString());
    }

    private void verifyNotOnSecondary() throws IOException {
        ModelNode readExt = Util.createEmptyOperation(READ_RESOURCE_OPERATION, EXTENSION_ADDRESS);
        executeOp(readExt, FAILED);
        ModelNode read = Util.getReadAttributeOperation(PROFILE_SUBSYSTEM_ADDRESS, NAME);
        executeOp(read, FAILED);
    }

    private void verifyNotOnServers() throws IOException {
        ModelNode readExtOne = Util.getReadAttributeOperation(SERVER_ONE_EXT_ADDRESS, MODULE);
        executeOp(readExtOne, FAILED);

        ModelNode readSubOne = Util.getReadAttributeOperation(SERVER_ONE_SUBSYSTEM_ADDRESS, NAME);
        executeOp(readSubOne, FAILED);

        ModelNode readExtThree = Util.getReadAttributeOperation(SERVER_THREE_EXT_ADDRESS, MODULE);
        executeOp(readExtThree, FAILED);

        ModelNode readSubThree = Util.getReadAttributeOperation(SERVER_THREE_SUBSYSTEM_ADDRESS, NAME);
        executeOp(readSubThree, FAILED);

    }

    private static ModelNode buildComposite(ModelNode... steps) {
        ModelNode result = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        ModelNode stepsParam = result.get("steps");
        for (ModelNode step : steps) {
            stepsParam.add(step);
        }
        return result;
    }

    private static void validateInvokePublicStep(ModelNode response, int step, boolean expectRollback) {
        String msg = response.toString();
        assertTrue(msg, response.has("result"));
        ModelNode result = response.get("result");
        assertTrue(msg, result.isDefined());
        String stepKey = "step-"+step;
        assertEquals(msg, expectRollback ? "failed" : "success", result.get(stepKey, "outcome").asString());
        assertTrue(msg, result.has(stepKey, "result"));
        assertEquals(msg, TestExtension.MODULE_NAME, result.get(stepKey, "result").asString());
        if (expectRollback) {
            assertTrue(msg, result.has(stepKey, "rolled-back"));
            assertTrue(msg, result.get(stepKey, "rolled-back").asBoolean());
        } else {
            assertFalse(msg, result.has(stepKey, "rolled-back"));
        }
    }

    private void removeIgnoreFailure(ModelControllerClient client, PathAddress subsystemAddress) throws Exception {
        try {
            ModelNode op = Util.createRemoveOperation(subsystemAddress);
            client.execute(op);
        } catch (Exception ignore) {

        }
    }
}
