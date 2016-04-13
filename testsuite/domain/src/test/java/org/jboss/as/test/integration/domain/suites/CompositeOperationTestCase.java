/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY_GROUPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCEPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.ReadResourceDescriptionHandler;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various scenarios involving composite operations in a domain.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class CompositeOperationTestCase {

    private static final PathElement HOST_MASTER = PathElement.pathElement(HOST, "master");
    private static final PathElement HOST_SLAVE = PathElement.pathElement(HOST, "slave");
    private static final PathElement SERVER_ONE = PathElement.pathElement(RUNNING_SERVER, "main-one");
    private static final PathElement SERVER_TWO = PathElement.pathElement(RUNNING_SERVER, "main-two");
    private static final PathElement SERVER_THREE = PathElement.pathElement(RUNNING_SERVER, "main-three");
    private static final PathElement SERVER_FOUR = PathElement.pathElement(RUNNING_SERVER, "main-four");
    private static final PathElement SYS_PROP_ELEMENT = PathElement.pathElement(SYSTEM_PROPERTY, "composite-op");
    private static final PathElement HOST_SYS_PROP_ELEMENT = PathElement.pathElement(SYSTEM_PROPERTY, "composite-op-host");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    private int sysPropVal = 0;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(CompositeOperationTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Before
    public void setup() throws IOException {
        sysPropVal = 0;
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT));
        op.get(VALUE).set(sysPropVal);
        domainMasterLifecycleUtil.getDomainClient().execute(op);
        op = Util.createAddOperation(PathAddress.pathAddress(HOST_MASTER, HOST_SYS_PROP_ELEMENT));
        op.get(VALUE).set(sysPropVal);
        domainMasterLifecycleUtil.getDomainClient().execute(op);
        op = Util.createAddOperation(PathAddress.pathAddress(HOST_SLAVE, HOST_SYS_PROP_ELEMENT));
        op.get(VALUE).set(sysPropVal);
        domainMasterLifecycleUtil.getDomainClient().execute(op);
    }


    @After
    public void tearDown() throws IOException {
        try {
            ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT));
            domainMasterLifecycleUtil.getDomainClient().execute(op);
        } finally {
            try {
                ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(HOST_MASTER, HOST_SYS_PROP_ELEMENT));
                domainMasterLifecycleUtil.getDomainClient().execute(op);
            } finally {
                ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(HOST_SLAVE, HOST_SYS_PROP_ELEMENT));
                domainMasterLifecycleUtil.getDomainClient().execute(op);
            }
        }
    }

    /**
     * Test of a composite operation that reads from multiple processes.
     *
     * @throws IOException
     * @throws MgmtOperationException
     */
    @Test
    public void testMultipleProcessReadOnlyComposite() throws IOException, MgmtOperationException {

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        final ModelNode steps = composite.get(STEPS);

        final ModelNode address = new ModelNode();
        address.setEmptyList();

        // host=slave
        address.add(HOST, "slave");
        steps.add().set(createReadResourceOperation(address));

        // host=slave,server=main-three
        address.add(RUNNING_SERVER, "main-three");
        steps.add().set(createReadResourceOperation(address));

        // host=slave,server=main-three,subsystem=io
        address.add(SUBSYSTEM, "io");
        steps.add().set(createReadResourceOperation(address));

        // add steps involving a different host
        address.setEmptyList();

        // host=master
        address.add(HOST, "master");
        steps.add().set(createReadResourceOperation(address));

        // host=master,server=main-one
        address.add(RUNNING_SERVER, "main-one");
        steps.add().set(createReadResourceOperation(address));

        // host=master,server=main-one,subsystem=io
        address.add(SUBSYSTEM, "io");
        steps.add().set(createReadResourceOperation(address));

        // Now repeat the whole thing, but nested

        final ModelNode nested = steps.add();
        nested.get(OP).set(COMPOSITE);
        final ModelNode nestedSteps = nested.get(STEPS);

        address.setEmptyList();

        // host=slave
        address.add(HOST, "slave");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=slave,server=main-three
        address.add(RUNNING_SERVER, "main-three");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=slave,server=main-three,subsystem=io
        address.add(SUBSYSTEM, "io");
        nestedSteps.add().set(createReadResourceOperation(address));

        // add steps involving a different host
        address.setEmptyList();

        // host=master
        address.add(HOST, "master");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=master,server=main-one
        address.add(RUNNING_SERVER, "main-one");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=master,server=main-one,subsystem=io
        address.add(SUBSYSTEM, "io");
        nestedSteps.add().set(createReadResourceOperation(address));

        final ModelNode response = domainMasterLifecycleUtil.getDomainClient().execute(composite);

        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(RESULT));
        assertFalse(response.toString(), response.has(SERVER_GROUPS));

        validateCompositeReadonlyResponse(response, true);
    }

    private void validateCompositeReadonlyResponse(ModelNode response, boolean allowNested) {

        int i = 0;
        for (Property property : response.get(RESULT).asPropertyList()) {
            assertEquals(property.getName() + " from " + response, "step-" + (++i), property.getName());
            ModelNode item = property.getValue();
            assertEquals(property.getName() + " from " + response, ModelType.OBJECT, item.getType());
            assertEquals(property.getName() + " from " + response, SUCCESS, item.get(OUTCOME).asString());
            assertTrue(property.getName() + " from " + response, item.hasDefined(RESULT));
            ModelNode itemResult = item.get(RESULT);
            assertEquals(property.getName() + " result " + itemResult, ModelType.OBJECT, itemResult.getType());
            switch (i) {
                case 1:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(RUNNING_SERVER));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(DIRECTORY_GROUPING));
                    assertEquals(property.getName() + " result " + itemResult, "slave", itemResult.get(NAME).asString());
                    break;
                case 2:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(SUBSYSTEM));
                    assertEquals(property.getName() + " result " + itemResult, "main-three", itemResult.get(NAME).asString());
                    assertEquals(property.getName() + " result " + itemResult, "slave", itemResult.get(HOST).asString());
                    break;
                case 3:
                case 6:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined("buffer-pool"));
                    break;
                case 4:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(RUNNING_SERVER));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(DIRECTORY_GROUPING));
                    assertEquals(property.getName() + " result " + itemResult, "master", itemResult.get(NAME).asString());
                    break;
                case 5:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(SUBSYSTEM));
                    assertEquals(property.getName() + " result " + itemResult, "main-one", itemResult.get(NAME).asString());
                    assertEquals(property.getName() + " result " + itemResult, "master", itemResult.get(HOST).asString());
                    break;
                case 7:
                    if (allowNested) {
                        // recurse
                        validateCompositeReadonlyResponse(item, false);
                        break;
                    } // else fall through
                default:
                    throw new IllegalStateException();
            }
        }
    }

    /**
     * Test of a composite operation that writes to and reads from multiple processes.
     */
    @Test
    public void testMultipleProcessReadWriteOperation() throws IOException {

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        final ModelNode steps = composite.get(STEPS);

        // Modify the domain-wide prop
        steps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_MASTER, SERVER_ONE, SYS_PROP_ELEMENT), VALUE));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, SERVER_THREE, SYS_PROP_ELEMENT), VALUE));

        // Modify the host=master prop

        steps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_MASTER, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_MASTER, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        // Modify the host=slave prop

        steps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_MASTER, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        // Now repeat the whole thing, but nested

        final ModelNode nested = steps.add();
        nested.get(OP).set(COMPOSITE);
        final ModelNode nestedSteps = nested.get(STEPS);

        // Domain wide
        nestedSteps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_MASTER, SERVER_ONE, SYS_PROP_ELEMENT), VALUE));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, SERVER_THREE, SYS_PROP_ELEMENT), VALUE));

        // host=master

        nestedSteps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_MASTER, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_MASTER, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        // host=slave

        nestedSteps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_MASTER, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SLAVE, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        final ModelNode response = domainMasterLifecycleUtil.getDomainClient().execute(composite);

        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(RESULT));
        assertTrue(response.toString(), response.has(SERVER_GROUPS));

        validateCompositeReadWriteResponse(response, true);

        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", HOST, "master", "main-one", "response"));
        ModelNode serverResp = response.get(SERVER_GROUPS, "main-server-group", HOST, "master", "main-one", "response");
        validateBasicServerResponse(serverResp, null, 1, null, 2, 2, -1, null, 4, null, 5, 5);
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", HOST, "slave", "main-three", "response"));
        serverResp = response.get(SERVER_GROUPS, "main-server-group", HOST, "slave", "main-three", "response");
        validateBasicServerResponse(serverResp, null, 1, 0, null, 3, -1, null, 4, 3, null, 6);
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "other-server-group", HOST, "slave", "other-two", "response"));
        serverResp = response.get(SERVER_GROUPS, "other-server-group", HOST, "slave", "other-two", "response");
        validateBasicServerResponse(serverResp, null, null, -1, null, null);

    }

    private void validateCompositeReadWriteResponse(ModelNode response, boolean allowNested) {

        int i = 0;
        int baseValue = allowNested ? 0 : 3;
        for (Property property : response.get(RESULT).asPropertyList()) {
            assertEquals(property.getName() + " from " + response, "step-" + (++i), property.getName());
            ModelNode item = property.getValue();
            if (i != 4 && i != 7) {
                assertEquals(property.getName() + " from " + response, ModelType.OBJECT, item.getType());
                assertEquals(property.getName() + " from " + response, SUCCESS, item.get(OUTCOME).asString());
            }
            ModelNode itemResult = item.get(RESULT);
            switch (i) {
                case 1:
                    assertFalse(property.getName() + " result " + itemResult, itemResult.isDefined());
                    break;
                case 2:
                case 3:
                    assertEquals(property.getName() + " result " + itemResult, ModelType.STRING, itemResult.getType());
                    assertEquals(property.getName() + " result " + itemResult, baseValue + 1, itemResult.asInt());
                    break;
                case 4:
                    //assertFalse(property.getName() + " result " + itemResult, itemResult.isDefined());
                    break;
                case 5:
                case 8:
                    assertEquals(property.getName() + " result " + itemResult, ModelType.STRING, itemResult.getType());
                    assertEquals(property.getName() + " result " + itemResult, baseValue + 2, itemResult.asInt());
                    break;
                case 6:
                    assertEquals(property.getName() + " result " + itemResult, ModelType.STRING, itemResult.getType());
                    assertEquals(property.getName() + " result " + itemResult, baseValue, itemResult.asInt());
                    break;
                case 7:
                    //assertFalse(property.getName() + " result " + itemResult, itemResult.isDefined());
                    break;
                case 9:
                    assertEquals(property.getName() + " result " + itemResult, ModelType.STRING, itemResult.getType());
                    assertEquals(property.getName() + " result " + itemResult, baseValue + 3, itemResult.asInt());
                    break;
                case 10:
                    if (allowNested) {
                        // recurse
                        validateCompositeReadWriteResponse(item, false);
                        break;
                    } // else fall through
                default:
                    throw new IllegalStateException();
            }
        }

    }

    private static void validateBasicServerResponse(ModelNode serverResponse, Integer... values) {
        assertEquals(serverResponse.toString(), ModelType.OBJECT, serverResponse.getType());
        assertEquals(serverResponse.toString(), SUCCESS, serverResponse.get(OUTCOME).asString());
        assertTrue(serverResponse.toString(), serverResponse.hasDefined(RESULT));
        ModelNode serverResult = serverResponse.get(RESULT);
        assertEquals(serverResponse.toString(), ModelType.OBJECT, serverResult.getType());
        List<Property> list = serverResult.asPropertyList();
        assertTrue(list.size() <= values.length);
        for (int i = 0; i < list.size(); i++) {
            ModelNode node = list.get(i).getValue();
            assertTrue(serverResult.toString(), node.isDefined());
            assertEquals(serverResult.toString(), ModelType.OBJECT, node.getType());
            assertEquals(serverResult.toString(), SUCCESS, node.get(OUTCOME).asString());
            Integer val = values[i];
            if (val == null) {
                assertFalse(serverResult.toString(), node.hasDefined(RESULT));
            } else {
                assertTrue(serverResult.toString() + " node " + i, node.hasDefined(RESULT));
                int valInt = val.intValue();
                if (valInt < 0) {
                    assertTrue(values.length > i + 1);
                    Integer[] sub = new Integer[values.length - i];
                    System.arraycopy(values, i + 1, sub, 0, values.length - i - 1);
                    validateBasicServerResponse(node, sub);
                } else {
                    assertEquals(ModelType.STRING, node.get(RESULT).getType());
                    assertEquals(String.valueOf(valInt), node.get(RESULT).asString());
                }
            }
        }
    }

    /** Test for https://issues.jboss.org/browse/WFCORE-998 */
    @Test
    public void testReadResourceDescriptionWithStoppedServer() throws IOException {

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        final ModelNode steps = composite.get(STEPS);

        // First an operation that just has a single step calling r-r-d on the stopped server resource
        ModelNode step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_SLAVE, SERVER_FOUR));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());

        steps.add(step);

        ModelNode response = domainMasterLifecycleUtil.getDomainClient().execute(composite);

        validateRRDResponse(response, 1);

        // Now include a step reading a stopped server on master
        step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_MASTER, SERVER_TWO));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());

        steps.add(step);

        response = domainMasterLifecycleUtil.getDomainClient().execute(composite);

        validateRRDResponse(response, 2);


        // Now add steps for some running servers too
        step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_MASTER, SERVER_ONE));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());
        steps.add(step);
        step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_SLAVE, SERVER_THREE));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());
        steps.add(step);

        response = domainMasterLifecycleUtil.getDomainClient().execute(composite);

        validateRRDResponse(response, 4);
    }

    private static void validateRRDResponse(ModelNode response, int stepCount) {

        String responseString = response.toString();
        assertEquals(responseString, SUCCESS, response.get(OUTCOME).asString());
        assertTrue(responseString, response.hasDefined(RESULT));
        ModelNode result = response.get(RESULT);
        assertEquals(responseString, ModelType.OBJECT, result.getType());
        List<Property> list = result.asPropertyList();
        assertEquals(responseString, stepCount, list.size());
        for (Property prop : list) {
            ModelNode stepResp = prop.getValue();
            assertEquals(responseString, SUCCESS, stepResp.get(OUTCOME).asString());
            assertTrue(responseString, stepResp.hasDefined(RESULT, ATTRIBUTES));
            ModelNode stepResult = stepResp.get(RESULT);
            Set<String> keys = stepResult.get(ATTRIBUTES).keys();
            assertTrue(responseString, keys.contains("launch-type"));
            assertTrue(responseString, keys.contains("server-state"));
            assertTrue(responseString, keys.contains("runtime-configuration-state"));
            assertTrue(responseString, stepResult.hasDefined(ACCESS_CONTROL, "default", ATTRIBUTES));
            Set<String> accessKeys = stepResult.get(ACCESS_CONTROL, "default", ATTRIBUTES).keys();
            assertTrue(responseString, accessKeys.contains("launch-type"));
            assertTrue(responseString, accessKeys.contains("server-state"));
            assertTrue(responseString, accessKeys.contains("runtime-configuration-state"));

            assertTrue(responseString, stepResult.hasDefined(ACCESS_CONTROL, EXCEPTIONS));
            assertEquals(responseString, 0, stepResult.get(ACCESS_CONTROL, EXCEPTIONS).asInt());

            switch (prop.getName()) {
                case "step-1":
                case "step-2":
                    assertEquals(responseString, 3, keys.size());
                    assertEquals(responseString, 3, accessKeys.size());
                    break;
                case "step-3":
                case "step-4":
                    assertTrue(responseString, keys.size() > 2);
                    assertEquals(responseString, keys.size(), accessKeys.size());
                    break;
                default:
                    fail(responseString);
            }
        }

    }

    private static ModelNode createReadResourceOperation(final ModelNode address) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);
        return operation;
    }
}
