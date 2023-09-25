/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INET_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SCHEMA_LOCATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateFailedResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.SchemaLocationAddHandler;
import org.jboss.as.controller.operations.common.SchemaLocationRemoveHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various management operations to confirm they are or are not allowed access.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagementAccessTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static final String TEST = "mgmt-access-test";
    private static final ModelNode ROOT_ADDRESS = new ModelNode().setEmptyList();
    private static final ModelNode PRIMARY_ROOT_ADDRESS = new ModelNode().add(HOST, "primary");
    private static final ModelNode SECONDARY_ROOT_ADDRESS = new ModelNode().add(HOST, "secondary");
    private static final ModelNode ROOT_PROP_ADDRESS = new ModelNode().add(SYSTEM_PROPERTY, TEST);
    private static final ModelNode OTHER_SERVER_GROUP_ADDRESS = new ModelNode().add(SERVER_GROUP, "other-server-group");
    private static final ModelNode TEST_SERVER_GROUP_ADDRESS = new ModelNode().add(SERVER_GROUP, "test-server-group");
    private static final ModelNode PRIMARY_INTERFACE_ADDRESS = new ModelNode().set(PRIMARY_ROOT_ADDRESS).add(INTERFACE, "management");
    private static final ModelNode SECONDARY_INTERFACE_ADDRESS = new ModelNode().set(SECONDARY_ROOT_ADDRESS).add(INTERFACE, "management");
    private static final ModelNode MAIN_RUNNING_SERVER_ADDRESS = new ModelNode().add(HOST, "primary").add(SERVER, "main-one");
    private static final ModelNode MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS = new ModelNode().set(MAIN_RUNNING_SERVER_ADDRESS).add(CORE_SERVICE, PLATFORM_MBEAN).add(TYPE, "class-loading");
    private static final ModelNode OTHER_RUNNING_SERVER_ADDRESS = new ModelNode().add(HOST, "secondary").add(SERVER, "other-two");
    private static final ModelNode OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS = new ModelNode().set(OTHER_RUNNING_SERVER_ADDRESS).add(CORE_SERVICE, PLATFORM_MBEAN).add(TYPE, "class-loading");

    static {
        ROOT_ADDRESS.protect();
        PRIMARY_ROOT_ADDRESS.protect();
        SECONDARY_ROOT_ADDRESS.protect();
        ROOT_PROP_ADDRESS.protect();
        OTHER_SERVER_GROUP_ADDRESS.protect();
        PRIMARY_INTERFACE_ADDRESS.protect();
        SECONDARY_INTERFACE_ADDRESS.protect();
        MAIN_RUNNING_SERVER_ADDRESS.protect();
        MAIN_RUNNING_SERVER_CLASSLOADING_ADDRESS.protect();
        OTHER_RUNNING_SERVER_ADDRESS.protect();
        OTHER_RUNNING_SERVER_CLASSLOADING_ADDRESS.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ManagementAccessTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    private DomainClient primaryClient;
    private DomainClient secondaryClient;

    @Before
    public void setup() throws Exception {
        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
    }

    @After
    public void teardown() throws Exception {
        primaryClient.execute(Util.getEmptyOperation(REMOVE, TEST_SERVER_GROUP_ADDRESS));
        primaryClient.execute(SchemaLocationRemoveHandler.getRemoveSchemaLocationOperation(ROOT_ADDRESS, "uri"));
        primaryClient.execute(SchemaLocationRemoveHandler.getRemoveSchemaLocationOperation(PRIMARY_ROOT_ADDRESS, "uri"));
        primaryClient.execute(SchemaLocationRemoveHandler.getRemoveSchemaLocationOperation(SECONDARY_ROOT_ADDRESS, "uri"));
    }

    @Test
    public void testDomainReadAccess() throws IOException {

        // Start with reads of the root resource
        ModelNode response = primaryClient.execute(getReadAttributeOperation(ROOT_ADDRESS, SCHEMA_LOCATIONS));
        ModelNode returnVal = validateResponse(response);

        response = secondaryClient.execute(getReadAttributeOperation(ROOT_ADDRESS, SCHEMA_LOCATIONS));
        ModelNode secondaryReturnVal = validateResponse(response);

        Assert.assertEquals(returnVal, secondaryReturnVal);

        // Now try a resource below root
        response = primaryClient.execute(getReadAttributeOperation(OTHER_SERVER_GROUP_ADDRESS, PROFILE));
        returnVal = validateResponse(response);
        Assert.assertEquals("other", returnVal.asString());

        response = secondaryClient.execute(getReadAttributeOperation(OTHER_SERVER_GROUP_ADDRESS, PROFILE));
        secondaryReturnVal = validateResponse(response);

        Assert.assertEquals(returnVal, secondaryReturnVal);
    }

    @Test
    public void testCompositeDomainReadAccess() throws IOException {

        ModelNode request = getEmptyOperation(COMPOSITE, null);
        ModelNode steps = request.get(STEPS);
        steps.add(getReadAttributeOperation(ROOT_ADDRESS, SCHEMA_LOCATIONS));
        steps.add(getReadAttributeOperation(OTHER_SERVER_GROUP_ADDRESS, PROFILE));
        request.protect();

        ModelNode response = primaryClient.execute(request);
        System.out.println(response);
        ModelNode returnVal = validateResponse(response);
        validateResponse(returnVal.get("step-1"));
        ModelNode profile = validateResponse(returnVal.get("step-2"));
        Assert.assertEquals("other", profile.asString());

        response = secondaryClient.execute(request);
        System.out.println(response);
        ModelNode secondaryReturnVal = validateResponse(response);
        Assert.assertEquals(returnVal, secondaryReturnVal);
    }

    @Test
    public void testHostReadAccess() throws IOException {

        // Start with reads of the root resource
        ModelNode response = primaryClient.execute(getReadAttributeOperation(PRIMARY_ROOT_ADDRESS, NAME));
        ModelNode returnVal = validateResponse(response);
        Assert.assertEquals("primary", returnVal.asString());

        response = secondaryClient.execute(getReadAttributeOperation(SECONDARY_ROOT_ADDRESS, NAME));
        ModelNode secondaryReturnVal = validateResponse(response);
        Assert.assertEquals("secondary", secondaryReturnVal.asString());

        // Now try a resource below root
        response = primaryClient.execute(getReadAttributeOperation(PRIMARY_INTERFACE_ADDRESS, INET_ADDRESS));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());

        response = secondaryClient.execute(getReadAttributeOperation(SECONDARY_INTERFACE_ADDRESS, INET_ADDRESS));
        secondaryReturnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, secondaryReturnVal.getType());

        response = primaryClient.execute(getReadAttributeOperation(SECONDARY_INTERFACE_ADDRESS, INET_ADDRESS));
        returnVal = validateResponse(response);
        Assert.assertEquals(ModelType.EXPRESSION, returnVal.getType());

        Assert.assertEquals(returnVal, secondaryReturnVal);

        // Can't access the primary via the secondary
        response = secondaryClient.execute(getReadAttributeOperation(PRIMARY_ROOT_ADDRESS, NAME));
        validateFailedResponse(response);
        response = secondaryClient.execute(getReadAttributeOperation(PRIMARY_INTERFACE_ADDRESS, INET_ADDRESS));
        validateFailedResponse(response);
    }

    @Test
    public void testCompositeHostReadAccess() throws IOException {

        ModelNode primaryRequest = getEmptyOperation(COMPOSITE, null);
        ModelNode steps = primaryRequest.get(STEPS);
        steps.add(getReadAttributeOperation(PRIMARY_ROOT_ADDRESS, NAME));
        steps.add(getReadAttributeOperation(PRIMARY_INTERFACE_ADDRESS, INET_ADDRESS));
        primaryRequest.protect();

        ModelNode response = primaryClient.execute(primaryRequest);
        System.out.println(response);
        ModelNode returnVal = validateResponse(response);
        ModelNode name = validateResponse(returnVal.get("step-1"));
        Assert.assertEquals("primary", name.asString());
        ModelNode inetAddress = validateResponse(returnVal.get("step-2"));
        Assert.assertEquals(ModelType.EXPRESSION, inetAddress.getType());

        ModelNode secondaryRequest = getEmptyOperation(COMPOSITE, null);
        steps = secondaryRequest.get(STEPS);
        steps.add(getReadAttributeOperation(SECONDARY_ROOT_ADDRESS, NAME));
        steps.add(getReadAttributeOperation(SECONDARY_INTERFACE_ADDRESS, INET_ADDRESS));
        primaryRequest.protect();

        response = secondaryClient.execute(secondaryRequest);
        System.out.println(response);
        ModelNode secondaryReturnVal = validateResponse(response);
        name = validateResponse(secondaryReturnVal.get("step-1"));
        Assert.assertEquals("secondary", name.asString());
        inetAddress = validateResponse(secondaryReturnVal.get("step-2"));
        Assert.assertEquals(ModelType.EXPRESSION, inetAddress.getType());

        // Check we get the same thing via the primary
        response = primaryClient.execute(secondaryRequest);
        returnVal = validateResponse(response);
        Assert.assertEquals(returnVal, secondaryReturnVal);

        // Can't access the primary via the secondary
        response = secondaryClient.execute(primaryRequest);
        validateFailedResponse(response);
    }

    @Test
    public void testCompositeCrossHostReadAccess() throws IOException {

        ModelNode primaryRequest = getEmptyOperation(COMPOSITE, null);
        ModelNode steps = primaryRequest.get(STEPS);
        steps.add(getReadAttributeOperation(PRIMARY_ROOT_ADDRESS, NAME));
        steps.add(getReadAttributeOperation(PRIMARY_INTERFACE_ADDRESS, INET_ADDRESS));
        steps.add(getReadAttributeOperation(SECONDARY_ROOT_ADDRESS, NAME));
        steps.add(getReadAttributeOperation(SECONDARY_INTERFACE_ADDRESS, INET_ADDRESS));
        steps.add(getReadAttributeOperation(MAIN_RUNNING_SERVER_ADDRESS, NAME));
        steps.add(getReadAttributeOperation(OTHER_RUNNING_SERVER_ADDRESS, NAME));
        primaryRequest.protect();

        System.out.println(primaryRequest);
        ModelNode response = primaryClient.execute(primaryRequest);
        System.out.println(response);
        ModelNode returnVal = validateResponse(response);
        ModelNode name = validateResponse(returnVal.get("step-1"));
        Assert.assertEquals("primary", name.asString());
        ModelNode inetAddress = validateResponse(returnVal.get("step-2"));
        Assert.assertEquals(ModelType.EXPRESSION, inetAddress.getType());
        name = validateResponse(returnVal.get("step-3"));
        Assert.assertEquals("secondary", name.asString());
        inetAddress = validateResponse(returnVal.get("step-4"));
        Assert.assertEquals(ModelType.EXPRESSION, inetAddress.getType());
        name = validateResponse(returnVal.get("step-5"));
        Assert.assertEquals("main-one", name.asString());
        name = validateResponse(returnVal.get("step-6"));
        Assert.assertEquals("other-two", name.asString());

        // Can't access the primary via the secondary
        response = secondaryClient.execute(primaryRequest);
        validateFailedResponse(response);
    }

    @Test
    public void testDomainWriteAccess() throws IOException {

        // Start with writes of the root resource
        final ModelNode addSchemaLocRequest = SchemaLocationAddHandler.getAddSchemaLocationOperation(ROOT_ADDRESS, "uri", "location");
        ModelNode response = primaryClient.execute(addSchemaLocRequest);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(ROOT_ADDRESS, SCHEMA_LOCATIONS));
        ModelNode returnVal = validateResponse(response);
        Assert.assertTrue(hasTestSchemaLocation(returnVal));

        // Now try a resource below root
        final ModelNode addServerGroupRequest = Util.getEmptyOperation(ADD, TEST_SERVER_GROUP_ADDRESS);
        addServerGroupRequest.get(PROFILE).set("default");
        addServerGroupRequest.get(SOCKET_BINDING_GROUP).set("standard-sockets");

        response = primaryClient.execute(addServerGroupRequest);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(TEST_SERVER_GROUP_ADDRESS, PROFILE));
        returnVal = validateResponse(response);
        Assert.assertEquals("default", returnVal.asString());

        // Secondary can't write
        response = secondaryClient.execute(addSchemaLocRequest);
        validateFailedResponse(response);

        response = secondaryClient.execute(addServerGroupRequest);
        validateFailedResponse(response);
    }

    @Test
    public void testCompositeDomainWriteAccess() throws IOException {

        ModelNode primaryRequest = getEmptyOperation(COMPOSITE, null);
        ModelNode steps = primaryRequest.get(STEPS);
        steps.add(SchemaLocationAddHandler.getAddSchemaLocationOperation(ROOT_ADDRESS, "uri", "location"));

        // Now try a resource below root
        final ModelNode addServerGroupRequest = Util.getEmptyOperation(ADD, TEST_SERVER_GROUP_ADDRESS);
        addServerGroupRequest.get(PROFILE).set("default");
        addServerGroupRequest.get(SOCKET_BINDING_GROUP).set("standard-sockets");

        steps.add(addServerGroupRequest);
        primaryRequest.protect();

        ModelNode response = primaryClient.execute(primaryRequest);
        System.out.println(response);
        validateResponse(response);

        response = primaryClient.execute(getReadAttributeOperation(ROOT_ADDRESS, SCHEMA_LOCATIONS));
        ModelNode returnVal = validateResponse(response);
        Assert.assertTrue(hasTestSchemaLocation(returnVal));

        response = primaryClient.execute(getReadAttributeOperation(TEST_SERVER_GROUP_ADDRESS, PROFILE));
        returnVal = validateResponse(response);
        Assert.assertEquals("default", returnVal.asString());

        // Secondary can't write
        response = secondaryClient.execute(primaryRequest);
        validateFailedResponse(response);
    }

    private boolean hasTestSchemaLocation(ModelNode returnVal) {

        Assert.assertEquals(ModelType.LIST, returnVal.getType());
        for (Property prop : returnVal.asPropertyList()) {
            if ("uri".equals(prop.getName()) && "location".equals(prop.getValue().asString())) {
                return true;
            }
        }

        return false;
    }

    private static ModelNode getReadAttributeOperation(ModelNode address, String attribute) {
        ModelNode result = getEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
        result.get(NAME).set(attribute);
        return result;
    }

    private static ModelNode getEmptyOperation(String operationName, ModelNode address) {
        ModelNode op = new ModelNode();
        op.get(OP).set(operationName);
        if (address != null) {
            op.get(OP_ADDR).set(address);
        }
        else {
            // Just establish the standard structure; caller can fill in address later
            op.get(OP_ADDR);
        }
        return op;
    }
}
