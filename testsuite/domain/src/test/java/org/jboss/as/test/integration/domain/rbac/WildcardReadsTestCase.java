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

package org.jboss.as.test.integration.domain.rbac;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ABSOLUTE_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILTERED_ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILTERED_CHILDREN_TYPES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNREADABLE_CHILDREN;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_USER;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
//import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests of wildcard reads with RBAC in place.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class WildcardReadsTestCase extends AbstractRbacTestCase {

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderTestSuite.createSupport(WildcardReadsTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        RBACProviderHostScopedRolesTestCase.HostRolesMappingSetup.StandardUsersSetup.INSTANCE.setup(domainClient);
        AbstractServerGroupScopedRolesTestCase.setupRoles(domainClient);
        RBACProviderServerGroupScopedRolesTestCase.ServerGroupRolesMappingSetup.INSTANCE.setup(domainClient);
        AbstractHostScopedRolesTestCase.setupRoles(domainClient);
        RBACProviderHostScopedRolesTestCase.HostRolesMappingSetup.INSTANCE.setup(domainClient);
    }

    @SuppressWarnings("ConstantConditions")
    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        try {
            RBACProviderHostScopedRolesTestCase.HostRolesMappingSetup.INSTANCE.tearDown(domainClient);
        } finally {
            try {
                AbstractHostScopedRolesTestCase.tearDownRoles(domainClient);
            } finally {
                try {
                    RBACProviderServerGroupScopedRolesTestCase.ServerGroupRolesMappingSetup.INSTANCE.tearDown(domainClient);
                } finally {
                    try {
                        AbstractServerGroupScopedRolesTestCase.tearDownRoles(domainClient);
                    } finally {
                        try {
                            UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.tearDown(domainClient);
                        } finally {
                            FullRbacProviderTestSuite.stopSupport();
                            testSupport = null;
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testMaintainer() throws IOException {
        ModelControllerClient client = getClientForUser(MAINTAINER_USER, false, primaryClientConfig);

        ModelNode op = createOpNode("host=*/server=*/subsystem=1/rbac-constrained=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{MAINTAINER_USER});
        ModelNode response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        ModelNode result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 2, result.asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));

        validateConstrainedResponse(response, "primary", "primary-a");
        validateConstrainedResponse(response, "secondary", "secondary-b");

        op = createOpNode("host=*/server=*/subsystem=1/rbac-sensitive=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{MAINTAINER_USER});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        // Result should be an empty list as we can't read any of these
        assertTrue(response.toString(), response.hasDefined(RESULT));
        assertEquals(response.toString(), ModelType.LIST, response.get(RESULT).getType());
        assertEquals(response.toString(), 0, response.get(RESULT).asInt());

        op = createOpNode("host=*/server=*/subsystem=1", READ_RESOURCE_OPERATION);
        op.get(RECURSIVE).set(true);
        configureRoles(op, new String[]{MAINTAINER_USER});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 2, result.asInt());

        validateSensitiveResponse(response, "primary", "primary-a");
        validateSensitiveResponse(response, "secondary", "secondary-b");
    }

    @Test
    public void testPrimaryHostScopedRoleReadResource() throws IOException {
        testHostScopedRoleReadResource(AbstractHostScopedRolesTestCase.MAINTAINER_USER, "primary", "primary-a");
    }

    @Test
    public void testSecondaryHostScopedRoleReadResource() throws IOException {
        testHostScopedRoleReadResource(AbstractHostScopedRolesTestCase.SECONDARY_MAINTAINER_USER, "secondary", "secondary-b");
    }

    private void testHostScopedRoleReadResource(final String user, final String host, final String server) throws IOException {
        ModelControllerClient client = getClientForUser(user, false, primaryClientConfig);

        ModelNode op = createOpNode("host=*/server=*/subsystem=1/rbac-constrained=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{user});
        ModelNode response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        ModelNode result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.EMPTY_ADDRESS, HOST);

        validateConstrainedResponse(response, host, server);

        op = createOpNode("host=*/server=*/subsystem=1/rbac-sensitive=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{user});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        // Result should be an empty list as we can't read any of these.
        assertTrue(response.toString(), response.hasDefined(RESULT));
        assertEquals(response.toString(), ModelType.LIST, response.get(RESULT).getType());
        assertEquals(response.toString(), 0, response.get(RESULT).asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.EMPTY_ADDRESS, HOST);
        checkFilteredChildrenType(response, PathAddress.pathAddress(PathElement.pathElement(HOST, host),
                        PathElement.pathElement(RUNNING_SERVER, server), PathElement.pathElement(SUBSYSTEM, "1")),
                "rbac-sensitive");

        op = createOpNode("host=*/server=*/subsystem=1", READ_RESOURCE_OPERATION);
        op.get(RECURSIVE).set(true);
        configureRoles(op, new String[]{user});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.EMPTY_ADDRESS, HOST);
        checkFilteredChildrenType(response, PathAddress.pathAddress(PathElement.pathElement(HOST, host),
                        PathElement.pathElement(RUNNING_SERVER, server), PathElement.pathElement(SUBSYSTEM, "1")),
                "rbac-sensitive");

        validateSensitiveResponse(response, host, server);

        // Now just read the root, to prove that's handled even when some hosts aren't allowed
        op = createOpNode("host=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{user});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.EMPTY_ADDRESS, HOST);

        ModelNode resultItem = getResultItem(response, PathAddress.pathAddress(HOST, host));
        assertTrue(resultItem.toString(), resultItem.hasDefined(RESULT));
        assertTrue(resultItem.toString(), resultItem.get(RESULT).keys().contains(MANAGEMENT_MAJOR_VERSION));
        assertEquals(resultItem.toString(),host, resultItem.get(RESULT, NAME).asString());
    }

    @Test
    public void testPrimaryHostScopedRoleReadResourceDescription() throws IOException {
        testHostScopedRoleReadResourceDescription(AbstractHostScopedRolesTestCase.MAINTAINER_USER, "primary", "primary-a");
    }

    @Test
    public void testSecondaryHostScopedRoleReadResourceDescription() throws IOException {
        testHostScopedRoleReadResourceDescription(AbstractHostScopedRolesTestCase.SECONDARY_MAINTAINER_USER, "secondary", "secondary-b");
    }

    private void testHostScopedRoleReadResourceDescription(final String user, final String host, final String server) throws IOException {
        ModelControllerClient client = getClientForUser(user, false, primaryClientConfig);

        ModelNode op = createOpNode("host=*", READ_RESOURCE_DESCRIPTION_OPERATION);
        configureRoles(op, new String[]{user});
        ModelNode response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        ModelNode result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 2, result.asInt());
        ModelNode entry = result.asList().get(1);
        Assert.assertEquals(PathAddress.pathAddress(HOST, host), PathAddress.pathAddress(entry.get(ADDRESS)));


        op = createOpNode("host=*/server-config=*", READ_RESOURCE_DESCRIPTION_OPERATION);
        configureRoles(op, new String[]{user});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());
        entry = result.asList().get(0);
        PathAddress expected = PathAddress.pathAddress(HOST, host).append(SERVER_CONFIG, "*");
        Assert.assertEquals(expected, PathAddress.pathAddress(entry.get(ADDRESS)));


        op = createOpNode("host=*/server=*", READ_RESOURCE_DESCRIPTION_OPERATION);
        configureRoles(op, new String[]{user});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        //Servers will have an extra entry for the wildcard address, in addition the exact server address
        assertEquals(result.toString(), 2, result.asInt());
        Set<PathAddress> expectedServerAddresses = new HashSet<>();
        expectedServerAddresses.add(PathAddress.pathAddress(HOST, host).append(SERVER, server));
        expectedServerAddresses.add(PathAddress.pathAddress(HOST, host).append(SERVER, "*"));
        entry = result.asList().get(0);
        Assert.assertTrue(expectedServerAddresses.remove(PathAddress.pathAddress(entry.get(ADDRESS))));
        entry = result.asList().get(1);
        Assert.assertTrue(expectedServerAddresses.remove(PathAddress.pathAddress(entry.get(ADDRESS))));


        op = createOpNode("host=*/server=*/subsystem=1", READ_RESOURCE_DESCRIPTION_OPERATION);
        configureRoles(op, new String[]{user});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());
        entry = result.asList().get(0);
        Assert.assertEquals(PathAddress.pathAddress(HOST, host).append(SERVER, server).append(SUBSYSTEM, "1"),
                PathAddress.pathAddress(entry.get(ADDRESS)));
    }

    @Test
    public void testServerGroupScopedRole() throws IOException {
        ModelControllerClient client = getClientForUser(AbstractServerGroupScopedRolesTestCase.MAINTAINER_USER, false, primaryClientConfig);

        ModelNode op = createOpNode("host=*/server=*/subsystem=1/rbac-constrained=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{AbstractServerGroupScopedRolesTestCase.MAINTAINER_USER});
        ModelNode response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        ModelNode result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.pathAddress(HOST, "secondary"), RUNNING_SERVER);

        validateConstrainedResponse(response, "primary", "primary-a");

        op = createOpNode("host=*/server=*/subsystem=1/rbac-sensitive=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{AbstractServerGroupScopedRolesTestCase.MAINTAINER_USER});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        // Result should be an empty list as we can't read any of these.
        assertTrue(response.toString(), response.hasDefined(RESULT));
        assertEquals(response.toString(), ModelType.LIST, response.get(RESULT).getType());
        assertEquals(response.toString(), 0, response.get(RESULT).asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.pathAddress(HOST, "secondary"), RUNNING_SERVER);
        checkFilteredChildrenType(response, PathAddress.pathAddress(PathElement.pathElement(HOST, "primary"),
                        PathElement.pathElement(RUNNING_SERVER, "primary-a"), PathElement.pathElement(SUBSYSTEM, "1")),
                "rbac-sensitive");

        op = createOpNode("host=*/server=*/subsystem=1", READ_RESOURCE_OPERATION);
        op.get(RECURSIVE).set(true);
        configureRoles(op, new String[]{MAINTAINER_USER});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 1, result.asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.pathAddress(HOST, "secondary"), RUNNING_SERVER);
        checkFilteredChildrenType(response, PathAddress.pathAddress(PathElement.pathElement(HOST, "primary"),
                        PathElement.pathElement(RUNNING_SERVER, "primary-a"), PathElement.pathElement(SUBSYSTEM, "1")),
                "rbac-sensitive");

        validateSensitiveResponse(response, "primary", "primary-a");

        // Now just read the root, to prove that's handled even when some servers aren't allowed
        op = createOpNode("host=*/server=*", READ_RESOURCE_OPERATION);
        configureRoles(op, new String[]{MAINTAINER_USER});
        response = RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

        result = response.get(RESULT);
        assertEquals(result.toString(), ModelType.LIST, result.getType());
        assertEquals(result.toString(), 2, result.asInt());

        assertTrue(response.toString(), response.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL));
        checkFilteredChildrenType(response, PathAddress.pathAddress(HOST, "secondary"), RUNNING_SERVER);

        ModelNode resultItem = getResultItem(response, PathAddress.pathAddress(PathElement.pathElement(HOST, "secondary"), PathElement.pathElement(RUNNING_SERVER, "secondary-a")));
        assertTrue(resultItem.toString(), resultItem.hasDefined(RESULT));
        assertEquals(resultItem.toString(), 0, resultItem.get(RESULT).asInt());

        resultItem = getResultItem(response, PathAddress.pathAddress(PathElement.pathElement(HOST, "primary"), PathElement.pathElement(RUNNING_SERVER, "primary-a")));
        assertTrue(resultItem.toString(), resultItem.hasDefined(RESULT));
        assertTrue(resultItem.toString(), resultItem.get(RESULT).keys().contains(MANAGEMENT_MAJOR_VERSION));
        assertEquals(resultItem.toString(), "primary", resultItem.get(RESULT, HOST).asString());
        assertEquals(resultItem.toString(), "primary-a", resultItem.get(RESULT, NAME).asString());

        checkFilteredChildrenType(response, PathAddress.pathAddress(HOST, "secondary"), RUNNING_SERVER);
        ModelNode accItem = getAccessControlItem(response, PathAddress.pathAddress(HOST, "secondary"));
        assertTrue(accItem.toString(), accItem.hasDefined(UNREADABLE_CHILDREN));
        assertEquals(accItem.toString(), ModelType.LIST, accItem.get(UNREADABLE_CHILDREN).getType());
        assertEquals(accItem.toString(), 1, accItem.get(UNREADABLE_CHILDREN).asInt());
        assertEquals(accItem.toString(), ModelType.PROPERTY, accItem.get(UNREADABLE_CHILDREN).get(0).getType());
        assertEquals(accItem.toString(), RUNNING_SERVER, accItem.get(UNREADABLE_CHILDREN).get(0).asProperty().getName());
        assertEquals(accItem.toString(), "secondary-b", accItem.get(UNREADABLE_CHILDREN).get(0).asProperty().getValue().asString());
    }

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        // no-op. Role mapping is done based on the client's authenticated Subject
    }

    private static void checkFilteredChildrenType(ModelNode response, PathAddress reporter, String type) {
        ModelNode accItem = getAccessControlItem(response, reporter);
        assert accItem != null;
        assertEquals(accItem.toString(), reporter, PathAddress.pathAddress(accItem.get(RELATIVE_ADDRESS)));
        assertTrue(accItem.toString(), accItem.hasDefined(FILTERED_CHILDREN_TYPES));
        assertEquals(accItem.toString(), ModelType.LIST, accItem.get(FILTERED_CHILDREN_TYPES).getType());
        assertEquals(accItem.toString(), 1, accItem.get(FILTERED_CHILDREN_TYPES).asInt());
        assertEquals(accItem.toString(), type, accItem.get(FILTERED_CHILDREN_TYPES).get(0).asString());

    }

    private static ModelNode getResultItem(ModelNode response, PathAddress pathAddress) {
        for (ModelNode item : response.get(RESULT).asList()) {
            if (pathAddress.equals(PathAddress.pathAddress(item.get(ADDRESS)))) {
                return item;
            }
        }
        fail(String.format("No %s in %s", pathAddress, response.get(RESULT)));
        // Unreachable
        throw new IllegalStateException();
    }

    private static ModelNode getAccessControlItem(ModelNode response, PathAddress pathAddress) {
        for (ModelNode item : response.get(RESPONSE_HEADERS, ACCESS_CONTROL).asList()) {
            if (pathAddress.equals(PathAddress.pathAddress(item.get(ABSOLUTE_ADDRESS)))) {
                return item;
            }
        }
        fail(String.format("No %s in %s", pathAddress, response.get(RESPONSE_HEADERS, ACCESS_CONTROL)));
        // Unreachable
        throw new IllegalStateException();
    }

    private static void validateConstrainedResponse(ModelNode response, String host, String server) {

        PathAddress target = PathAddress.pathAddress(PathElement.pathElement(HOST, host),
                PathElement.pathElement(RUNNING_SERVER, server), PathElement.pathElement(SUBSYSTEM, "1"),
                PathElement.pathElement("rbac-constrained", "default"));
        ModelNode item = getResultItem(response, target);
        assertTrue(item.toString(), item.has(RESULT, "security-domain"));
        assertFalse(item.toString(), item.hasDefined(RESULT, "security-domain"));
        assertTrue(item.toString(), item.has(RESULT, "password"));
        assertFalse(item.toString(), item.hasDefined(RESULT, "password"));

        checkFilteredAttributes(response, target);
    }

    private static void checkFilteredAttributes(ModelNode response, PathAddress reporter) {
        ModelNode accItem = getAccessControlItem(response, reporter);
        assert accItem != null;
        assertEquals(accItem.toString(), reporter, PathAddress.pathAddress(accItem.get(RELATIVE_ADDRESS)));
        assertTrue(accItem.toString(), accItem.hasDefined(FILTERED_ATTRIBUTES));
        assertEquals(accItem.toString(), 3, accItem.get(FILTERED_ATTRIBUTES).asInt());
        assertTrue(accItem.toString(), accItem.get(FILTERED_ATTRIBUTES).asString().contains("password"));
        assertTrue(accItem.toString(), accItem.get(FILTERED_ATTRIBUTES).asString().contains("security-domain"));
        assertTrue(accItem.toString(), accItem.get(FILTERED_ATTRIBUTES).asString().contains("authentication-inflow"));

    }

    private static void validateSensitiveResponse(ModelNode response, String host, String server) {
        PathAddress subsystemAddr = PathAddress.pathAddress(PathElement.pathElement(HOST, host),
                PathElement.pathElement(RUNNING_SERVER, server), PathElement.pathElement(SUBSYSTEM, "1"));

        ModelNode resultItem = getResultItem(response, subsystemAddr);
        assertTrue(resultItem.toString(), resultItem.has(RESULT, "rbac-sensitive"));
        assertFalse(resultItem.toString(), resultItem.hasDefined(RESULT, "rbac-sensitive"));
        assertTrue(resultItem.toString(), resultItem.has(RESULT, "rbac-constrained", "default", "password"));
        assertFalse(resultItem.toString(), resultItem.hasDefined(RESULT, "rbac-constrained", "default", "password"));

        checkFilteredChildrenType(response, subsystemAddr, "rbac-sensitive");
        checkFilteredAttributes(response, subsystemAddr.append(PathElement.pathElement("rbac-constrained", "default")));
    }

}
