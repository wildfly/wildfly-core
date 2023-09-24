/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.optypes.OpTypesExtension;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Tests behavior of runtime-only operations registered against domain profile resources.
 *
 * @author Brian Stansberry
 */
public class RuntimeOnlyOperationsTestCase {

    private static final PathAddress PRIMARY = PathAddress.pathAddress(ModelDescriptionConstants.HOST, "primary");
    private static final PathAddress SECONDARY = PathAddress.pathAddress(ModelDescriptionConstants.HOST, "secondary");

    private static final PathAddress EXT = PathAddress.pathAddress("extension", OpTypesExtension.EXTENSION_NAME);
    private static final PathAddress PROFILE = PathAddress.pathAddress("profile", "default");
    private static final PathElement SUBSYSTEM = PathElement.pathElement("subsystem", OpTypesExtension.SUBSYSTEM_NAME);
    private static final PathElement MAIN_ONE = PathElement.pathElement("server", "main-one");
    private static final PathElement MAIN_THREE = PathElement.pathElement("server", "main-three");

    private static DomainTestSupport testSupport;
    private static ManagementClient managementClient;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(RuntimeOnlyOperationsTestCase.class.getSimpleName());
        DomainClient primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        managementClient = new ManagementClient(primaryClient, TestSuiteEnvironment.getServerAddress(), 9090, "remoting+http");


        ExtensionUtils.createExtensionModule(OpTypesExtension.EXTENSION_NAME, OpTypesExtension.class,
                EmptySubsystemParser.class.getPackage());

        executeOp(Util.createAddOperation(EXT), SUCCESS);
        executeOp(Util.createAddOperation(PROFILE.append(SUBSYSTEM)), SUCCESS);

        executeOp(Util.createAddOperation(PRIMARY.append(EXT)), SUCCESS);
        executeOp(Util.createAddOperation(PRIMARY.append(SUBSYSTEM)), SUCCESS);

        executeOp(Util.createAddOperation(SECONDARY.append(EXT)), SUCCESS);
        executeOp(Util.createAddOperation(SECONDARY.append(SUBSYSTEM)), SUCCESS);
    }

    @AfterClass
    public static void tearDownDomain() throws IOException {
        Throwable t = null;
        List<ModelNode> ops = new ArrayList<>();
        ops.add(Util.createRemoveOperation(PRIMARY.append(SUBSYSTEM)));
        ops.add(Util.createRemoveOperation(PRIMARY.append(EXT)));
        ops.add(Util.createRemoveOperation(SECONDARY.append(SUBSYSTEM)));
        ops.add(Util.createRemoveOperation(SECONDARY.append(EXT)));
        ops.add(Util.createRemoveOperation(PROFILE.append(SUBSYSTEM)));
        ops.add(Util.createRemoveOperation(EXT));
        for (ModelNode op : ops) {
            try {
                executeOp(op, SUCCESS);
            } catch (IOException | AssertionError e) {
                if (t == null) {
                    t = e;
                }
            }
        }

        ExtensionUtils.deleteExtensionModule(OpTypesExtension.EXTENSION_NAME);

        testSupport = null;
        managementClient = null;
        DomainTestSuite.stopSupport();

        if (t instanceof IOException) {
            throw (IOException) t;
        } else if (t != null) {
            throw (Error) t;
        }
    }

    @Test
    public void testStandardOp() throws IOException {
        // Invoke the op on the profile resource and on the servers indivdually
        // Expect all to succeed
        runtimeOnlyTest("runtime-only", true);
    }

    @Test
    public void testPrivateOnServerOp() throws IOException {
        // Invoke the op on the profile resource and on the servers indivdually
        // Expect the server ops to fail as they are private (see WFCORE-13)
        runtimeOnlyTest("domain-runtime-private", false);
    }

    @Test
    public void testRuntimeReadOnlyOp() throws IOException {
        runtimeOnlyTest("runtime-read-only", true);
    }

    private void runtimeOnlyTest(String opName, boolean directServerSuccess) throws IOException {
        ModelNode op = Util.createEmptyOperation(opName, PROFILE.append(SUBSYSTEM));
        ModelNode response = executeOp(op, SUCCESS);
        assertFalse(response.toString(), response.hasDefined(RESULT)); // handler doesn't set a result on profile
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", "host", "primary", "main-one", "response", "result"));
        assertTrue(response.toString(), response.get(SERVER_GROUPS, "main-server-group", "host", "primary", "main-one", "response", "result").asBoolean());
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", "host", "secondary", "main-three", "response", "result"));
        assertTrue(response.toString(), response.get(SERVER_GROUPS, "main-server-group", "host", "secondary", "main-three", "response", "result").asBoolean());

        // Now check direct invocation on servers
        op = Util.createEmptyOperation(opName, PRIMARY.append(MAIN_ONE).append(SUBSYSTEM));
        response = executeOp(op, directServerSuccess ? SUCCESS : FAILED);
        if (directServerSuccess) {
            assertTrue(response.toString(), response.get(RESULT).asBoolean());
        }
        op = Util.createEmptyOperation(opName, SECONDARY.append(MAIN_THREE).append(SUBSYSTEM));
        response = executeOp(op, directServerSuccess ? SUCCESS : FAILED);
        if (directServerSuccess) {
            assertTrue(response.toString(), response.get(RESULT).asBoolean());
        }
    }

    @Test
    public void testRuntimeOnlyAttribute() throws IOException {
        attributeTest("runtime-only-attr", "read-only");
    }

    @Test
    public void testMetric() throws IOException {
        attributeTest("metric", "metric");
    }

    private void attributeTest(String attribute, String accessType) throws IOException {
        ModelNode op = Util.getReadResourceDescriptionOperation(PROFILE.append(SUBSYSTEM));
        ModelNode resp = executeOp(op, SUCCESS);
        assertTrue(resp.toString(), resp.hasDefined(RESULT, ATTRIBUTES));
        // non storage=configuration attributes should not get registered even though the ResourceDefinition tries
        assertFalse(resp.toString(), resp.has(RESULT, ATTRIBUTES, attribute));

        op = Util.getReadAttributeOperation(PROFILE.append(SUBSYSTEM), attribute);
        executeOp(op, FAILED);

        op = Util.getReadResourceDescriptionOperation(SECONDARY.append(MAIN_THREE).append(SUBSYSTEM));
        resp = executeOp(op, SUCCESS);
        assertEquals(resp.toString(), accessType, resp.get(RESULT, ATTRIBUTES, attribute, ACCESS_TYPE).asString());

        op = Util.getReadAttributeOperation(SECONDARY.append(MAIN_THREE).append(SUBSYSTEM), attribute);
        resp = executeOp(op, SUCCESS);
        assertTrue(resp.toString(), resp.get(RESULT).asBoolean());
    }

    @Test
    public void testRuntimeStepOnPrimary() throws IOException {
        ignoredProfileRuntimeStepTest("primary");
    }

    @Test
    public void testRuntimeStepOnSecondary() throws IOException {
        ignoredProfileRuntimeStepTest("secondary");
    }

    private void ignoredProfileRuntimeStepTest(String host) throws IOException {
        ModelNode op = Util.createEmptyOperation("runtime-only", PROFILE.append(SUBSYSTEM));
        op.get(HOST).set(host);
        ModelNode response = executeOp(op, SUCCESS);
        assertFalse(response.toString(), response.hasDefined(RESULT)); // handler's attempt to add a step to set a result is ignored on profile
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", "host", "primary", "main-one", "response", "result"));
        assertTrue(response.toString(), response.get(SERVER_GROUPS, "main-server-group", "host", "primary", "main-one", "response", "result").asBoolean());
        assertTrue(response.toString(), response.hasDefined(SERVER_GROUPS, "main-server-group", "host", "secondary", "main-three", "response", "result"));
        assertTrue(response.toString(), response.get(SERVER_GROUPS, "main-server-group", "host", "secondary", "main-three", "response", "result").asBoolean());
    }

    private static ModelNode executeOp(ModelNode op, String outcome) throws IOException {
        ModelNode response = managementClient.getControllerClient().execute(op);
        assertTrue(response.toString(), response.hasDefined(OUTCOME));
        assertEquals(response.toString(), outcome, response.get(OUTCOME).asString());
        return response;
    }
}
