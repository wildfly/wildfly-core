/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY_GROUPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCEPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.ReadResourceDescriptionHandler;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various scenarios involving composite operations in a domain.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class CompositeOperationTestCase {
    private static String DEPLOYMENT_NAME = "deployment.jar";
    private static final PathElement DEPLOYMENT_PATH = PathElement.pathElement(DEPLOYMENT, DEPLOYMENT_NAME);
    protected static final PathAddress SERVER_GROUP_MAIN_SERVER_GROUP = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");

    private static final PathElement HOST_PRIMARY = PathElement.pathElement(HOST, "primary");
    private static final PathElement HOST_SECONDARY = PathElement.pathElement(HOST, "secondary");
    private static final PathElement SERVER_ONE = PathElement.pathElement(RUNNING_SERVER, "main-one");
    private static final PathElement SERVER_TWO = PathElement.pathElement(RUNNING_SERVER, "main-two");
    private static final PathElement SERVER_THREE = PathElement.pathElement(RUNNING_SERVER, "main-three");
    private static final PathElement SERVER_FOUR = PathElement.pathElement(RUNNING_SERVER, "main-four");
    private static final PathElement SYS_PROP_ELEMENT = PathElement.pathElement(SYSTEM_PROPERTY, "composite-op");
    private static final PathElement HOST_SYS_PROP_ELEMENT = PathElement.pathElement(SYSTEM_PROPERTY, "composite-op-host");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainClient primaryClient;

    private int sysPropVal = 0;
    private static File tmpDir;
    private static File deployment;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(CompositeOperationTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Before
    public void setup() throws IOException {
        sysPropVal = 0;
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT));
        op.get(VALUE).set(sysPropVal);
        domainPrimaryLifecycleUtil.getDomainClient().execute(op);
        op = Util.createAddOperation(PathAddress.pathAddress(HOST_PRIMARY, HOST_SYS_PROP_ELEMENT));
        op.get(VALUE).set(sysPropVal);
        domainPrimaryLifecycleUtil.getDomainClient().execute(op);
        op = Util.createAddOperation(PathAddress.pathAddress(HOST_SECONDARY, HOST_SYS_PROP_ELEMENT));
        op.get(VALUE).set(sysPropVal);
        domainPrimaryLifecycleUtil.getDomainClient().execute(op);
    }


    @After
    public void tearDown() throws IOException {
        try {
            ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT));
            domainPrimaryLifecycleUtil.getDomainClient().execute(op);
        } finally {
            try {
                ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(HOST_PRIMARY, HOST_SYS_PROP_ELEMENT));
                domainPrimaryLifecycleUtil.getDomainClient().execute(op);
            } finally {
                ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(HOST_SECONDARY, HOST_SYS_PROP_ELEMENT));
                domainPrimaryLifecycleUtil.getDomainClient().execute(op);
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

        // host=secondary
        address.add(HOST, "secondary");
        steps.add().set(createReadResourceOperation(address));

        // host=secondary,server=main-three
        address.add(RUNNING_SERVER, "main-three");
        steps.add().set(createReadResourceOperation(address));

        // host=secondary,server=main-three,subsystem=io
        address.add(SUBSYSTEM, "io");
        steps.add().set(createReadResourceOperation(address));

        // add steps involving a different host
        address.setEmptyList();

        // host=primary
        address.add(HOST, "primary");
        steps.add().set(createReadResourceOperation(address));

        // host=primary,server=main-one
        address.add(RUNNING_SERVER, "main-one");
        steps.add().set(createReadResourceOperation(address));

        // host=primary,server=main-one,subsystem=io
        address.add(SUBSYSTEM, "io");
        steps.add().set(createReadResourceOperation(address));

        // Now repeat the whole thing, but nested

        final ModelNode nested = steps.add();
        nested.get(OP).set(COMPOSITE);
        final ModelNode nestedSteps = nested.get(STEPS);

        address.setEmptyList();

        // host=secondary
        address.add(HOST, "secondary");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=secondary,server=main-three
        address.add(RUNNING_SERVER, "main-three");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=secondary,server=main-three,subsystem=io
        address.add(SUBSYSTEM, "io");
        nestedSteps.add().set(createReadResourceOperation(address));

        // add steps involving a different host
        address.setEmptyList();

        // host=primary
        address.add(HOST, "primary");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=primary,server=main-one
        address.add(RUNNING_SERVER, "main-one");
        nestedSteps.add().set(createReadResourceOperation(address));

        // host=primary,server=main-one,subsystem=io
        address.add(SUBSYSTEM, "io");
        nestedSteps.add().set(createReadResourceOperation(address));

        final ModelNode response = domainPrimaryLifecycleUtil.getDomainClient().execute(composite);

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
                    assertEquals(property.getName() + " result " + itemResult, "secondary", itemResult.get(NAME).asString());
                    break;
                case 2:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(SUBSYSTEM));
                    assertEquals(property.getName() + " result " + itemResult, "main-three", itemResult.get(NAME).asString());
                    assertEquals(property.getName() + " result " + itemResult, "secondary", itemResult.get(HOST).asString());
                    break;
                case 3:
                case 6:
                    break;
                case 4:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(RUNNING_SERVER));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(DIRECTORY_GROUPING));
                    assertEquals(property.getName() + " result " + itemResult, "primary", itemResult.get(NAME).asString());
                    break;
                case 5:
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(MANAGEMENT_MAJOR_VERSION));
                    assertTrue(property.getName() + " result " + itemResult, itemResult.hasDefined(SUBSYSTEM));
                    assertEquals(property.getName() + " result " + itemResult, "main-one", itemResult.get(NAME).asString());
                    assertEquals(property.getName() + " result " + itemResult, "primary", itemResult.get(HOST).asString());
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

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE, SYS_PROP_ELEMENT), VALUE));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, SERVER_THREE, SYS_PROP_ELEMENT), VALUE));

        // Modify the host=primary prop

        steps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        // Modify the host=secondary prop

        steps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        steps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        // Now repeat the whole thing, but nested

        final ModelNode nested = steps.add();
        nested.get(OP).set(COMPOSITE);
        final ModelNode nestedSteps = nested.get(STEPS);

        // Domain wide
        nestedSteps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE, SYS_PROP_ELEMENT), VALUE));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, SERVER_THREE, SYS_PROP_ELEMENT), VALUE));

        // host=primary

        nestedSteps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        // host=secondary

        nestedSteps.add().set(Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, HOST_SYS_PROP_ELEMENT), VALUE, ++sysPropVal));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE, HOST_SYS_PROP_ELEMENT), VALUE));

        nestedSteps.add().set(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_SECONDARY, SERVER_THREE, HOST_SYS_PROP_ELEMENT), VALUE));

        final ModelNode response = domainPrimaryLifecycleUtil.getDomainClient().execute(composite);

        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.toString(), response.hasDefined(RESULT));
        assertTrue(response.toString(), response.has(SERVER_GROUPS));

        validateCompositeReadWriteResponse(response, true);

        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", HOST, "primary", "main-one", "response"));
        ModelNode serverResp = response.get(SERVER_GROUPS, "main-server-group", HOST, "primary", "main-one", "response");
        validateBasicServerResponse(serverResp, null, 1, null, 2, 2, -1, null, 4, null, 5, 5);
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", HOST, "secondary", "main-three", "response"));
        serverResp = response.get(SERVER_GROUPS, "main-server-group", HOST, "secondary", "main-three", "response");
        validateBasicServerResponse(serverResp, null, 1, 0, null, 3, -1, null, 4, 3, null, 6);
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "other-server-group", HOST, "secondary", "other-two", "response"));
        serverResp = response.get(SERVER_GROUPS, "other-server-group", HOST, "secondary", "other-two", "response");
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
        ModelNode step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_FOUR));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());

        steps.add(step);

        ModelNode response = domainPrimaryLifecycleUtil.getDomainClient().execute(composite);

        validateRRDResponse(response, 1);

        // Now include a step reading a stopped server on primary
        step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_PRIMARY, SERVER_TWO));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());

        steps.add(step);

        response = domainPrimaryLifecycleUtil.getDomainClient().execute(composite);

        validateRRDResponse(response, 2);


        // Now add steps for some running servers too
        step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());
        steps.add(step);
        step = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_THREE));
        step.get(ACCESS_CONTROL).set(ReadResourceDescriptionHandler.AccessControl.COMBINED_DESCRIPTIONS.toString());
        steps.add(step);

        response = domainPrimaryLifecycleUtil.getDomainClient().execute(composite);

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
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(OP_ADDR).set(address);
        return operation;
    }

    /**
     * Tests reads in composite operations when the DC exclusive lock is acquired by another operation.
     */
    @Test
    public void testCompositeOperationsWriteLockAcquired() throws Exception {
        ModelNode result, op;

        op = Util.createEmptyOperation(READ_CHILDREN_TYPES_OPERATION, PathAddress.pathAddress(HOST_SECONDARY));
        result = DomainTestUtils.executeForResult(op, primaryClient);
        final List<String> secondaryChildrenTypes = result.asList().stream().map(m -> m.asString()).collect(Collectors.toList());

        op = Util.createEmptyOperation(READ_CHILDREN_TYPES_OPERATION, PathAddress.EMPTY_ADDRESS);
        result = DomainTestUtils.executeForResult(op, primaryClient);
        final List<String> emptyAddressChildrenTypes = result.asList().stream().map(m -> m.asString()).collect(Collectors.toList());

        createDeployment();

        final ExecutorService executorService = Executors.newFixedThreadPool(1);

        try {
            Future<ModelNode> deploymentFuture = executorService.submit(new Callable<ModelNode>() {
                @Override
                public ModelNode call() throws Exception {
                    // Take the DC write lock
                    final ModelNode op = getDeploymentCompositeOp();
                    DomainClient client = domainPrimaryLifecycleUtil.createDomainClient();
                    return DomainTestUtils.executeForResult(op, client);
                }
            });

            // verify reads in a composite operations
            List<ModelNode> steps;

            // it could ensure we have acquired the lock by the deployment operation executed before
            TimeUnit.SECONDS.sleep(TimeoutUtil.adjust(1));

            steps = prepareReadCompositeOperations(PathAddress.pathAddress(HOST_SECONDARY), secondaryChildrenTypes);
            op = createComposite(steps);
            DomainTestUtils.executeForResult(op, primaryClient);

            steps = prepareReadCompositeOperations(PathAddress.EMPTY_ADDRESS, emptyAddressChildrenTypes);
            op = createComposite(steps);
            DomainTestUtils.executeForResult(op, primaryClient);

            steps = prepareReadCompositeOperations(PathAddress.pathAddress(HOST_SECONDARY), secondaryChildrenTypes);
            steps.addAll(prepareReadCompositeOperations(PathAddress.EMPTY_ADDRESS, emptyAddressChildrenTypes));
            op = createComposite(steps);
            DomainTestUtils.executeForResult(op, primaryClient);

            Assert.assertEquals("It is expected deployment operation is still in progress", false, deploymentFuture.isDone());

            // keep the timeout in sync with SlowServiceActivator timeout
            deploymentFuture.get(TimeoutUtil.adjust(60), TimeUnit.SECONDS);

        } finally {
            try {
                cleanDeploymentFromServerGroup();
            } catch (MgmtOperationException e) {
                // ignored
            } finally {
                try {
                    cleanDeployment();
                } catch (MgmtOperationException e) {
                    // ignored
                }
            }
            executorService.shutdown();
        }
    }

    private ModelNode getDeploymentCompositeOp() throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        ModelNode steps = op.get(STEPS);

        ModelNode depAdd = Util.createAddOperation(PathAddress.pathAddress(DEPLOYMENT_PATH));
        ModelNode content = new ModelNode();
        content.get(URL).set(deployment.toURI().toURL().toString());
        depAdd.get(CONTENT).add(content);

        steps.add(depAdd);

        ModelNode sgAdd = Util.createAddOperation(PathAddress.pathAddress(SERVER_GROUP_MAIN_SERVER_GROUP, DEPLOYMENT_PATH));
        sgAdd.get(ENABLED).set(true);
        steps.add(sgAdd);
        return op;
    }

    private static void createDeployment() throws Exception {
        File tmpRoot = new File(System.getProperty("java.io.tmpdir"));
        tmpDir = new File(tmpRoot, CompositeOperationTestCase.class.getSimpleName() + System.currentTimeMillis());
        Files.createDirectory(tmpDir.toPath());
        deployment = new File(tmpDir, DEPLOYMENT_NAME);

        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, DEPLOYMENT_NAME)
                .addClasses(SlowServiceActivator.class, TimeoutUtil.class)
                .addAsManifestResource(new StringAsset("Dependencies: org.wildfly.security.elytron-private\n"), "MANIFEST.MF")
                .addAsServiceProvider(ServiceActivator.class, SlowServiceActivator.class)
                .addAsManifestResource(createPermissionsXmlAsset(new PropertyPermission("ts.timeout.factor", "read")), "permissions.xml");

        archive.as(ZipExporter.class).exportTo(deployment);
    }

    private void cleanDeploymentFromServerGroup() throws IOException, MgmtOperationException {
        ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP_MAIN_SERVER_GROUP, DEPLOYMENT_PATH));
        op.get(ENABLED).set(true);
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private void cleanDeployment() throws IOException, MgmtOperationException {
        ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_PATH));
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private ModelNode createComposite(List<ModelNode> steps) {
        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode stepsNode = composite.get(STEPS);
        for (ModelNode step : steps) {
            stepsNode.add(step);
        }
        return composite;
    }

    private List<ModelNode> prepareReadCompositeOperations(PathAddress address, List<String> childTypes) {
        final List<ModelNode> steps = new ArrayList<>();
        ModelNode step;

        step = Util.createEmptyOperation(READ_RESOURCE_OPERATION, address);
        addCommonReadOperationAttributes(step);
        steps.add(step);

        for(String childType : childTypes) {
            step = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, address);
            addCommonReadOperationAttributes(step);
            step.get("child-type").set(childType);
            steps.add(step);
        }

        return steps;
    }

    private void addCommonReadOperationAttributes(ModelNode op) {
        String operation = op.get(OP).asString();
        switch (operation) {
            case READ_RESOURCE_OPERATION: {
                op.get("include-aliases").set(true);
                op.get("include-undefined-metric-values").set(true);
            }
            case READ_CHILDREN_RESOURCES_OPERATION: {
                op.get("include-runtime").set(true);
                op.get("recursive").set(true);
                op.get("include-defaults").set(true);
                op.get("proxies").set(true);
            }
        }
    }
}
