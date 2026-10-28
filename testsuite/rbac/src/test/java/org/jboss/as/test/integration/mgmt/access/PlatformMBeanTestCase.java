/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURED_REQUIRES_READ;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILTERED_ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerSetup({StandardUsersSetupTask.class, PlatformMBeanTestCase.JvmSensitiveServerSetup.class})
public class PlatformMBeanTestCase extends AbstractRbacTestCase {

    private static final String RUNTIME = "runtime";
    private static final PathAddress PLATFORM_ADDRESS = PathAddress.pathAddress(CORE_SERVICE, PLATFORM_MBEAN);
    private static final PathAddress RUNTIME_ADDRESS = PLATFORM_ADDRESS.append(TYPE, RUNTIME);

    @Test
    public void testMonitor() throws Exception {
        test(RbacUtil.MONITOR_USER, true);
    }

    @Test
    public void testOperator() throws Exception {
        test(RbacUtil.OPERATOR_USER, true);
    }

    @Test
    public void testMaintainer() throws Exception {
        test(RbacUtil.MAINTAINER_USER, true);
    }

    @Test
    public void testDeployer() throws Exception {
        test(RbacUtil.DEPLOYER_USER, true);
    }

    @Test
    public void testAdministrator() throws Exception {
        test(RbacUtil.ADMINISTRATOR_USER, false);
    }

    @Test
    public void testAuditor() throws Exception {
        test(RbacUtil.AUDITOR_USER, false);
    }

    @Test
    public void testSuperUser() throws Exception {
        test(RbacUtil.SUPERUSER_USER, false);
    }

    private void test(String username, boolean expectFiltering) throws IOException {
        ModelControllerClient client = getClientForUser(username);
        readAllTest(client, expectFiltering);
        readRuntimeTest(client, expectFiltering);
    }

    private void readAllTest(ModelControllerClient client, boolean expectFiltering) throws IOException {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PLATFORM_ADDRESS);
        op.get(INCLUDE_RUNTIME).set(true);
        op.get(RECURSIVE).set(true);

        ModelNode response = client.execute(op);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());

        assertTrue(response.toString(), response.hasDefined(RESULT, TYPE, RUNTIME));
        checkRuntimeResource(response.get(RESULT, TYPE, RUNTIME), expectFiltering);
        checkFiltering(response, expectFiltering);
    }

    private void readRuntimeTest(ModelControllerClient client, boolean expectFiltering) throws IOException {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, RUNTIME_ADDRESS);
        op.get(INCLUDE_RUNTIME).set(true);

        ModelNode response = client.execute(op);
        assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());

        assertTrue(response.toString(), response.hasDefined(RESULT));
        checkRuntimeResource(response.get(RESULT), expectFiltering);
        checkFiltering(response, expectFiltering);
    }

    private void checkRuntimeResource(ModelNode resource, boolean expectFiltering) {
        // Check a couple non-secure attributes as a sanity check
        assertTrue(resource.toString(), resource.hasDefined("spec-version"));
        assertTrue(resource.toString(), resource.hasDefined("management-spec-version"));

        // Confirm sensitive attributes are/aren't filtered as expected
        if (expectFiltering) {
            assertFalse(resource.toString(), resource.hasDefined("system-properties"));
            assertFalse(resource.toString(), resource.hasDefined("input-arguments"));
            assertFalse(resource.toString(), resource.hasDefined("class-path"));
            assertFalse(resource.toString(), resource.hasDefined("library-path"));
            assertFalse(resource.toString(), resource.hasDefined("boot-class-path-supported"));
            assertFalse(resource.toString(), resource.hasDefined("boot-class-path"));
        } else {
            assertTrue(resource.toString(), resource.hasDefined("system-properties"));
            assertTrue(resource.toString(), resource.hasDefined("input-arguments"));
            assertTrue(resource.toString(), resource.hasDefined("class-path"));
            assertTrue(resource.toString(), resource.hasDefined("library-path"));
            assertTrue(resource.toString(), resource.hasDefined("boot-class-path-supported"));
            if (resource.get("boot-class-path-supported").asBoolean()) {
                assertTrue(resource.toString(), resource.hasDefined("boot-class-path"));
            }
        }
    }

    private void checkFiltering(ModelNode response, boolean expectFiltering) {
        if (expectFiltering) {
            assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
            ModelNode accessControl = response.get(RESPONSE_HEADERS, ACCESS_CONTROL);
            assertTrue(response.toString(), accessControl.isDefined());
            // Test assumes only type=runtime has sensitive data; adjust this if that changes
            assertEquals(accessControl.toString() ,1, accessControl.asInt()); // this is a list size check
            checkRuntimeFiltering(accessControl.get(0));
        } else {
            assertFalse(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        }
    }

    private void checkRuntimeFiltering(ModelNode filterData) {
        assertTrue(filterData.toString(), filterData.hasDefined(FILTERED_ATTRIBUTES));
        Set<String> attrs = filterData.get(FILTERED_ATTRIBUTES).asList().stream().map(ModelNode::asString).collect(Collectors.toSet());
        assertTrue(filterData.toString(), attrs.contains("system-properties"));
        assertTrue(filterData.toString(), attrs.contains("input-arguments"));
        assertTrue(filterData.toString(), attrs.contains("class-path"));
        assertTrue(filterData.toString(), attrs.contains("library-path"));
        assertTrue(filterData.toString(), attrs.contains("boot-class-path-supported"));
        assertTrue(filterData.toString(), attrs.contains("boot-class-path"));
    }

    /**
     * Configures {@code SensitivityClassification.JVM} to treat reads a sensitive. This
     * allows us to validate the behavior of a number of attributes that use this constraint.
     */
    public static final class JvmSensitiveServerSetup implements ServerSetupTask {

        private static final PathAddress PATH_ADDRESS = PathAddress.pathAddress("core-service","management")
                .append("access", "authorization")
                .append("constraint","sensitivity-classification")
                .append("type","core").append("classification", "jvm");

        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            ModelNode op = Util.getWriteAttributeOperation(PATH_ADDRESS, CONFIGURED_REQUIRES_READ, true);
            managementClient.executeForResult(op);
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            ModelNode op = Util.getUndefineAttributeOperation(PATH_ADDRESS, CONFIGURED_REQUIRES_READ);
            managementClient.executeForResult(op);

        }
    }
}
