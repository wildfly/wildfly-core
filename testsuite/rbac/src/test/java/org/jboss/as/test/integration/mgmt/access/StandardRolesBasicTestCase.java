/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PASSWORD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONFIG_AS_XML_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_USER;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Basic tests of the standard RBAC roles.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public abstract class StandardRolesBasicTestCase extends AbstractManagementInterfaceRbacTestCase {

    private static final String DEPLOYMENT_1 = "deployment=war-example.war";
    private static final String DEPLOYMENT_2 = "deployment=rbac.txt";
    private static final byte[] DEPLOYMENT_2_CONTENT = "CONTENT".getBytes(Charset.defaultCharset());
    private static final String AUTHORIZATION = "core-service=management/access=authorization";
    private static final String ROLE_MAPPING_BASE = "core-service=management/access=authorization/role-mapping=";
    private static final String HTTP_BINDING = "socket-binding-group=standard-sockets/socket-binding=management-http";
    private static final String MEMORY_MBEAN = "core-service=platform-mbean/type=memory";
    protected static final String EXAMPLE_DS = "subsystem=rbac/rbac-constrained=default";
    private static final String TEST_PATH = "path=rbac.test";

    private static final ModelNode WFLY_1916_OP;

    static {
        WFLY_1916_OP = Util.createEmptyOperation(UPLOAD_DEPLOYMENT_BYTES, PathAddress.EMPTY_ADDRESS);
        WFLY_1916_OP.get(BYTES).set(new byte[64]);
        WFLY_1916_OP.protect();
    }

    protected static void deployDeployment1(ManagementClient client) throws IOException {
        ModelNode op = createOpNode(DEPLOYMENT_1, ADD);
        op.get(ENABLED).set(false);
        ModelNode content = op.get(CONTENT).add();
        content.get(BYTES).set(DEPLOYMENT_2_CONTENT);
        RbacUtil.executeOperation(client.getControllerClient(), op, Outcome.SUCCESS);
    }

    protected static void removeDeployment1(ManagementClient client) throws IOException {
        ModelNode op = createOpNode(DEPLOYMENT_1, REMOVE);
        RbacUtil.executeOperation(client.getControllerClient(), op, Outcome.SUCCESS);
    }

    @Before
    public void deploy() throws IOException, MgmtOperationException {
        deployDeployment1(getManagementClient());
    }

    @After
    public void tearDown() throws IOException, MgmtOperationException {
        removeDeployment1(getManagementClient());
        AssertionError assertionError = null;
        try {
            removeResource(DEPLOYMENT_2);
        } catch (AssertionError e) {
            assertionError = e;
        } finally {
            try {
                removeResource(TEST_PATH);
            } catch (AssertionError e1) {
                if (assertionError == null) {
                    assertionError = e1;
                }
            } finally {
                restoreNonSensitiveValueAttribute(getManagementClient());
            }
        }

        if (assertionError != null) {
            throw assertionError;
        }
    }

    @Test
    public void testMonitor() throws Exception {
        ManagementInterface client = getClientForUser(MONITOR_USER);
        whoami(client, MONITOR_USER);
        readWholeConfig(client, Outcome.UNAUTHORIZED);
        checkStandardReads(client);
        readResource(client, AUTHORIZATION, Outcome.HIDDEN);
        checkSensitiveAttribute(client, false);
        checkNonSensitiveDefaultAttribute(client, false, false);
        runGC(client, Outcome.UNAUTHORIZED);
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return; // the 'add' operation is not implemented in JmxManagementInterface
        }
        addDeployment2(client, Outcome.UNAUTHORIZED);
        addPath(client, Outcome.UNAUTHORIZED);

        testWFLY1916(client, Outcome.UNAUTHORIZED);

        // Monitor can't shutdown
        testWCORE1067(client);
    }

    @Test
    public void testOperator() throws Exception {
        ManagementInterface client = getClientForUser(OPERATOR_USER);
        whoami(client, OPERATOR_USER);
        readWholeConfig(client, Outcome.UNAUTHORIZED);
        checkStandardReads(client);
        readResource(client, AUTHORIZATION, Outcome.HIDDEN);
        checkSensitiveAttribute(client, false);
        checkNonSensitiveDefaultAttribute(client, false, false);
        runGC(client, Outcome.SUCCESS);
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return; // the 'add' operation is not implemented in JmxManagementInterface
        }
        addDeployment2(client, Outcome.UNAUTHORIZED);
        addPath(client, Outcome.UNAUTHORIZED);

        testWFLY1916(client, Outcome.SUCCESS);
    }

    @Test
    public void testMaintainer() throws Exception {
        ManagementInterface client = getClientForUser(MAINTAINER_USER);
        whoami(client, MAINTAINER_USER);
        readWholeConfig(client, Outcome.UNAUTHORIZED);
        checkStandardReads(client);
        readResource(client, AUTHORIZATION, Outcome.HIDDEN);
        checkSensitiveAttribute(client, false);
        checkNonSensitiveDefaultAttribute(client, false, false);
        runGC(client, Outcome.SUCCESS);
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return; // the 'add' operation is not implemented in JmxManagementInterface
        }
        addDeployment2(client, Outcome.SUCCESS);
        addPath(client, Outcome.SUCCESS);

        testWFLY1916(client, Outcome.SUCCESS);
    }

    @Test
    public void testDeployer() throws Exception {
        ManagementInterface client = getClientForUser(DEPLOYER_USER);
        whoami(client, DEPLOYER_USER);
        readWholeConfig(client, Outcome.UNAUTHORIZED);
        checkStandardReads(client);
        readResource(client, AUTHORIZATION, Outcome.HIDDEN);
        checkSensitiveAttribute(client, false);
        checkNonSensitiveDefaultAttribute(client, false, false);
        runGC(client, Outcome.UNAUTHORIZED);
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return; // the 'add' operation is not implemented in JmxManagementInterface
        }
        addDeployment2(client, Outcome.SUCCESS);
        addPath(client, Outcome.UNAUTHORIZED);

        testWFLY1916(client, Outcome.SUCCESS);

        // Deployer can't shutdown
        testWCORE1067(client);
    }

    @Test
    public void testAdministrator() throws Exception {
        ManagementInterface client = getClientForUser(ADMINISTRATOR_USER);
        whoami(client, ADMINISTRATOR_USER);
        readWholeConfig(client, Outcome.SUCCESS);
        checkStandardReads(client);
        readResource(client, AUTHORIZATION, Outcome.SUCCESS);
        checkSensitiveAttribute(client, true);
        checkNonSensitiveDefaultAttribute(client, true, true);
        runGC(client, Outcome.SUCCESS);
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return; // the 'add' operation is not implemented in JmxManagementInterface
        }
        modifyAccessibleRoles(client, RbacUtil.MONITOR_ROLE, RbacUtil.OPERATOR_ROLE, RbacUtil.MAINTAINER_ROLE, RbacUtil.ADMINISTRATOR_ROLE, RbacUtil.DEPLOYER_ROLE);
        modifyInaccessibleRoles(client, RbacUtil.AUDITOR_ROLE, RbacUtil.SUPERUSER_ROLE);
        addDeployment2(client, Outcome.SUCCESS);
        addPath(client, Outcome.SUCCESS);

        testWFLY1916(client, Outcome.SUCCESS);
    }

    @Test
    public void testAuditor() throws Exception {
        ManagementInterface client = getClientForUser(AUDITOR_USER);
        whoami(client, AUDITOR_USER);
        readWholeConfig(client, Outcome.SUCCESS);
        checkStandardReads(client);
        readResource(client, AUTHORIZATION, Outcome.SUCCESS);
        checkSensitiveAttribute(client, true);
        checkNonSensitiveDefaultAttribute(client, true, false);
        runGC(client, Outcome.UNAUTHORIZED);
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return; // the 'add' operation is not implemented in JmxManagementInterface
        }
        modifyInaccessibleRoles(client, RbacUtil.allStandardRoles());
        addDeployment2(client, Outcome.UNAUTHORIZED);
        addPath(client, Outcome.UNAUTHORIZED);

        testWFLY1916(client, Outcome.UNAUTHORIZED);

        // Auditor can't shutdown
        testWCORE1067(client);
    }

    @Test
    public void testSuperUser() throws Exception {
        ManagementInterface client = getClientForUser(SUPERUSER_USER);
        whoami(client, SUPERUSER_USER);
        readWholeConfig(client, Outcome.SUCCESS);
        checkStandardReads(client);
        readResource(client, AUTHORIZATION, Outcome.SUCCESS);
        checkSensitiveAttribute(client, true);
        checkNonSensitiveDefaultAttribute(client, true, true);
        runGC(client, Outcome.SUCCESS);
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return; // the 'add' operation is not implemented in JmxManagementInterface
        }
        modifyAccessibleRoles(client, RbacUtil.allStandardRoles());
        addDeployment2(client, Outcome.SUCCESS);
        addPath(client, Outcome.SUCCESS);

        testWFLY1916(client, Outcome.SUCCESS);
    }

    private static void whoami(ManagementInterface client, String expectedUsername) throws IOException {
        ModelNode op = createOpNode(null, "whoami");
        ModelNode result = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);
        String returnedUsername = result.get(RESULT, "identity", USERNAME).asString();
        assertEquals(expectedUsername, returnedUsername);
    }

    private void readWholeConfig(ManagementInterface client, Outcome expectedOutcome) throws IOException {
        ModelNode op = createOpNode(null, READ_CONFIG_AS_XML_OPERATION);
        RbacUtil.executeOperation(client, op, expectedOutcome);

        // the code below calls the non-published operation 'describe'; see WFLY-2379 for more info
        // once that issue is fixed, the test will only make sense for native mgmt interface
        // (or maybe not even that)
        if (this instanceof JmxInterfaceStandardRolesBasicTestCase) {
            return;
        }
        op = createOpNode(null, READ_CHILDREN_NAMES_OPERATION);
        op.get(CHILD_TYPE).set(SUBSYSTEM);
        ModelNode subsystems = RbacUtil.executeOperation(getManagementClient().getControllerClient(), op, Outcome.SUCCESS);
        for (ModelNode subsystem : subsystems.get(RESULT).asList()) {
            op = createOpNode("subsystem=" + subsystem.asString(), DESCRIBE);
            ModelNode result = RbacUtil.executeOperation(client, op, expectedOutcome);
            assertEquals(expectedOutcome == Outcome.SUCCESS, result.hasDefined(RESULT));
        }
    }

    private static void checkStandardReads(ManagementInterface client) throws IOException {
        readResource(client, null, Outcome.SUCCESS);
        readResource(client, "core-service=platform-mbean/type=runtime", Outcome.SUCCESS);
        readResource(client, HTTP_BINDING, Outcome.SUCCESS);
    }

    private static ModelNode readResource(ManagementInterface client, String address, Outcome expectedOutcome) throws IOException {
        ModelNode op = createOpNode(address, READ_RESOURCE_OPERATION);
        return RbacUtil.executeOperation(client, op, expectedOutcome);
    }

    private static ModelNode readAttribute(ManagementInterface client, String address, String attributeName,
            Outcome expectedOutcome) throws IOException {
        ModelNode op = createOpNode(address, READ_ATTRIBUTE_OPERATION);
        op.get(NAME).set(attributeName);

        return RbacUtil.executeOperation(client, op, expectedOutcome);
    }

    private static void checkSensitiveAttribute(ManagementInterface client, boolean expectSuccess) throws IOException {
        ModelNode correct = new ModelNode();
        if (expectSuccess) {
            correct.set("sa");
        }

        ModelNode attrValue = readResource(client, EXAMPLE_DS, Outcome.SUCCESS).get(RESULT, PASSWORD);
        assertEquals(correct, attrValue);

        attrValue = readAttribute(client, EXAMPLE_DS, PASSWORD, expectSuccess ? Outcome.SUCCESS : Outcome.UNAUTHORIZED).get(RESULT);
        assertEquals(correct, attrValue);
    }

    private static void checkNonSensitiveDefaultAttribute(ManagementInterface client, boolean expectReadSuccess, boolean expectWriteSuccess) throws IOException {
        ModelNode correct = new ModelNode();
        if (expectReadSuccess) {
            correct.set(false);
        }

        ModelNode attrValue = readResource(client, EXAMPLE_DS, Outcome.SUCCESS).get(RESULT, "authentication-inflow");
        assertEquals(correct, attrValue);

        attrValue = readAttribute(client, EXAMPLE_DS, "authentication-inflow", expectReadSuccess ? Outcome.SUCCESS : Outcome.UNAUTHORIZED).get(RESULT);
        assertEquals(correct, attrValue);

        ModelNode op = createOpNode(EXAMPLE_DS, WRITE_ATTRIBUTE_OPERATION);
        op.get(NAME).set("authentication-inflow");
        op.get(VALUE).set(true);

        RbacUtil.executeOperation(client, op, expectWriteSuccess ? Outcome.SUCCESS : Outcome.UNAUTHORIZED);
    }

    private static void restoreNonSensitiveValueAttribute(ManagementClient client) throws IOException {

        ModelNode op = createOpNode(EXAMPLE_DS, UNDEFINE_ATTRIBUTE_OPERATION);
        op.get(NAME).set("authentication-inflow");

        RbacUtil.executeOperation(client.getControllerClient(), op, Outcome.SUCCESS );
    }

    private static void runGC(ManagementInterface client, Outcome expectedOutcome) throws IOException {
        ModelNode op = createOpNode(MEMORY_MBEAN, "gc");
        RbacUtil.executeOperation(client, op, expectedOutcome);
    }

    private static void addDeployment2(ManagementInterface client, Outcome expectedOutcome) throws IOException {
        ModelNode op = createOpNode(DEPLOYMENT_2, ADD);
        op.get(ENABLED).set(false);
        ModelNode content = op.get(CONTENT).add();
        content.get(BYTES).set(DEPLOYMENT_2_CONTENT);

        RbacUtil.executeOperation(client, op, expectedOutcome);
    }

    private static void addPath(ManagementInterface client, Outcome expectedOutcome) throws IOException {
        ModelNode op = createOpNode(TEST_PATH, ADD);
        op.get(PATH).set("/");
        RbacUtil.executeOperation(client, op, expectedOutcome);
    }

    private void removeResource(String address) throws IOException {
        ModelNode op = createOpNode(address, READ_RESOURCE_OPERATION);
        ModelNode result = getManagementClient().getControllerClient().execute(op);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            op = createOpNode(address, REMOVE);
            result = getManagementClient().getControllerClient().execute(op);
            assertEquals(result.asString(), SUCCESS, result.get(OUTCOME).asString());
        }
    }

    private static void modifyAccessibleRoles(ManagementInterface client, String... roleNames) throws IOException {
        for (String current : roleNames) {
            addRemoveIncldueForRole(client, current, true);
        }
    }

    private static void modifyInaccessibleRoles(ManagementInterface client, String... roleNames) throws IOException {
        for (String current : roleNames) {
            addRemoveIncldueForRole(client, current, false);
        }
    }

    private static void addRemoveIncldueForRole(final ManagementInterface client, final String roleName, boolean accessible) throws IOException {
        String includeAddress = ROLE_MAPPING_BASE + roleName + "/include=temp";
        ModelNode add = createOpNode(includeAddress, ADD);
        add.get(NAME).set("temp");
        add.get(TYPE).set(USER);

        RbacUtil.executeOperation(client, add, accessible ? Outcome.SUCCESS : Outcome.UNAUTHORIZED);

        if (accessible) {
            ModelNode remove = createOpNode(includeAddress, REMOVE);
            RbacUtil.executeOperation(client, remove, Outcome.SUCCESS);
        }
    }

    private void testWFLY1916(ManagementInterface client, Outcome expected) throws IOException {
        ModelNode op = WFLY_1916_OP.clone();
        RbacUtil.executeOperation(client, op, expected);
    }

    private void testWCORE1067(ManagementInterface client) throws IOException {
        ModelNode op = Util.createEmptyOperation(SHUTDOWN, PathAddress.EMPTY_ADDRESS);
        RbacUtil.executeOperation(client, op, Outcome.UNAUTHORIZED);
    }

}
