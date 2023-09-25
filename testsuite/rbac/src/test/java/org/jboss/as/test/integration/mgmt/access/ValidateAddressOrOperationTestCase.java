/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT_LOG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOGGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROBLEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROVIDER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALIDATE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.common.ValidateAddressOperationHandler;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({BasicExtensionSetupTask.class, StandardUsersSetupTask.class})
public class ValidateAddressOrOperationTestCase extends AbstractRbacTestCase {

    private static String[] LEGAL_ADDRESS_RESP_FIELDS;
    private static String[] LEGAL_OPERATION_RESPONSE_FIELDS;

    @Before
    public void setupLegalFields() throws Exception {
        if (LEGAL_ADDRESS_RESP_FIELDS == null) {
            // See if the server is already sending response headers; if so ignore them when validating responses
            ModelNode operation = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
            ModelNode result = getManagementClient().getControllerClient().execute(operation);
            if (result.has(RESPONSE_HEADERS)) {
                LEGAL_ADDRESS_RESP_FIELDS = new String[]{OUTCOME, RESULT, RESPONSE_HEADERS};
                LEGAL_OPERATION_RESPONSE_FIELDS = new String[]{OUTCOME, FAILURE_DESCRIPTION, ROLLED_BACK, RESPONSE_HEADERS};
            } else {
                LEGAL_ADDRESS_RESP_FIELDS = new String[]{OUTCOME, RESULT};
                LEGAL_OPERATION_RESPONSE_FIELDS = new String[]{OUTCOME, FAILURE_DESCRIPTION, ROLLED_BACK};
            }
        }
    }

    @Test
    public void testMonitor() throws Exception {
        test(RbacUtil.MONITOR_USER, false, true, true, true);
    }

    @Test
    public void testOperator() throws Exception {
        test(RbacUtil.OPERATOR_USER, false, true, true, true);
    }

    @Test
    public void testMaintainer() throws Exception {
        test(RbacUtil.MAINTAINER_USER, false, true, true, true);
    }

    @Test
    public void testDeployer() throws Exception {
        test(RbacUtil.DEPLOYER_USER, false, true, true, true);
    }

    @Test
    public void testAdministrator() throws Exception {
        test(RbacUtil.ADMINISTRATOR_USER, true, true, true, true);
    }

    @Test
    public void testAuditor() throws Exception {
        test(RbacUtil.AUDITOR_USER, true, true, true, true);
    }

    @Test
    public void testSuperUser() throws Exception {
        test(RbacUtil.SUPERUSER_USER, true, true, true, true);
    }

    private void test(String userName, boolean mgmtAuthorizationExpectation, boolean auditLogExpectation,
            boolean datasourceWithPlainPasswordExpectation,
            boolean datasourceWithMaskedPasswordExpectation) throws Exception {

        testValidateAddress(userName, mgmtAuthorizationExpectation, auditLogExpectation,
                datasourceWithPlainPasswordExpectation, datasourceWithMaskedPasswordExpectation);

        testValidateOperation(userName, mgmtAuthorizationExpectation, auditLogExpectation,
                datasourceWithPlainPasswordExpectation, datasourceWithMaskedPasswordExpectation);
    }

    private void testValidateAddress(String userName, boolean mgmtAuthorizationExpectation, boolean auditLogExpectation,
            boolean datasourceWithPlainPasswordExpectation,
            boolean datasourceWithMaskedPasswordExpectation) throws Exception {

        ModelControllerClient client = getClientForUser(userName);

        ModelNode address = new ModelNode();

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION);
        validateAddress(client, address, mgmtAuthorizationExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUDIT);
        validateAddress(client, address, auditLogExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUDIT).add(LOGGER, AUDIT_LOG);
        validateAddress(client, address, auditLogExpectation);
    }

    private void testValidateOperation(String userName, boolean mgmtAuthorizationExpectation, boolean auditLogExpectation,
            boolean datasourceWithPlainPasswordExpectation,
            boolean datasourceWithMaskedPasswordExpectation) throws Exception {

        ModelControllerClient client = getClientForUser(userName);

        ModelNode address = new ModelNode();

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION);
        validateOperation(client, readResource(address), mgmtAuthorizationExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION);
        validateOperation(client, readAttribute(address, PROVIDER), mgmtAuthorizationExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION);
        validateOperation(client, writeAttribute(address, PROVIDER, new ModelNode("simple")), mgmtAuthorizationExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUDIT);
        validateOperation(client, readResource(address), auditLogExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUDIT).add(LOGGER, AUDIT_LOG);
        validateOperation(client, readResource(address), auditLogExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUDIT).add(LOGGER, AUDIT_LOG);
        validateOperation(client, readAttribute(address, ENABLED), auditLogExpectation);

        address.setEmptyList().add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUDIT).add(LOGGER, AUDIT_LOG);
        validateOperation(client, writeAttribute(address, ENABLED, ModelNode.TRUE), auditLogExpectation);
    }

    // test utils
    private static ModelNode readResource(ModelNode address) {
        ModelNode readResource = new ModelNode();
        readResource.get(OP).set(READ_RESOURCE_OPERATION);
        readResource.get(OP_ADDR).set(address);
        readResource.get(RECURSIVE).set(true);
        return readResource;
    }

    private static ModelNode readAttribute(ModelNode address, String attribute) {
        ModelNode readAttribute = new ModelNode();
        readAttribute.get(OP).set(READ_ATTRIBUTE_OPERATION);
        readAttribute.get(OP_ADDR).set(address);
        readAttribute.get(NAME).set(attribute);
        return readAttribute;
    }

    private static ModelNode writeAttribute(ModelNode address, String attribute, ModelNode value) {
        ModelNode readAttribute = new ModelNode();
        readAttribute.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        readAttribute.get(OP_ADDR).set(address);
        readAttribute.get(NAME).set(attribute);
        readAttribute.get(VALUE).set(value);
        return readAttribute;
    }

    private static void validateAddress(ModelControllerClient client, ModelNode address, boolean expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(ValidateAddressOperationHandler.OPERATION_NAME, PathAddress.EMPTY_ADDRESS);
        operation.get(VALUE).set(address);
        ModelNode result = client.execute(operation);

        assertModelNodeOnlyContainsKeys(result, LEGAL_ADDRESS_RESP_FIELDS);
        assertModelNodeOnlyContainsKeys(result.get(RESULT), VALID, PROBLEM);
        assertEquals(result.toString(), expectedOutcome, result.get(RESULT, VALID).asBoolean());
        assertEquals(result.toString(), !expectedOutcome, result.get(RESULT).hasDefined(PROBLEM));
        if (!expectedOutcome) {
            assertTrue(result.get(RESULT, PROBLEM).asString().contains("WFLYCTL0335"));
        }
    }

    private static void validateOperation(ModelControllerClient client, ModelNode validatedOperation, boolean expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(VALIDATE_OPERATION, PathAddress.EMPTY_ADDRESS);
        operation.get(VALUE).set(validatedOperation);
        ModelNode result = client.execute(operation);

        assertModelNodeOnlyContainsKeys(result, LEGAL_OPERATION_RESPONSE_FIELDS);
        if (expectedOutcome) {
            assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
        } else {
            assertEquals(FAILED, result.get(OUTCOME).asString());
            assertTrue(result.toString(), result.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0335"));
        }
    }

    private static void assertModelNodeOnlyContainsKeys(ModelNode modelNode, String... keys) {
        Collection<String> expectedKeys = Arrays.asList(keys);
        Set<String> actualKeys = new HashSet<String>(modelNode.keys()); // need copy for modifications
        actualKeys.removeAll(expectedKeys);
        if (!actualKeys.isEmpty()) {
            fail("ModelNode contained additional keys: " + actualKeys + "  " + modelNode);
        }
    }
}
