/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BASE_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOSTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP_SCOPED_ROLE;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
//import org.junit.Ignore;
import org.junit.Test;

/**
 * WFLY-2270
 *
 * @author Ladislav Thon <lthon@redhat.com>
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class RolesIntegrityCheckingTestCase extends AbstractRbacTestCase {
    private static final String NEW_ROLE = "NewScopedRole";

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderTestSuite.createSupport(IncludeAllRoleTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.setup(domainClient);
        AbstractServerGroupScopedRolesTestCase.setupRoles(domainClient);
        RBACProviderServerGroupScopedRolesTestCase.ServerGroupRolesMappingSetup.INSTANCE.setup(domainClient);
        AbstractHostScopedRolesTestCase.setupRoles(domainClient);
        RBACProviderHostScopedRolesTestCase.HostRolesMappingSetup.INSTANCE.setup(domainClient);
    }

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

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        // no-op. Role mapping is done based on the client's authenticated Subject
    }

    // creating scoped roles with various role names

    @Test
    public void testAddScopedRole() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, NEW_ROLE, RbacUtil.MAINTAINER_ROLE, Outcome.SUCCESS);
        removeServerGroupScopedRole(client, NEW_ROLE, Outcome.SUCCESS);
        addHostScopedRole(client, NEW_ROLE, RbacUtil.MAINTAINER_ROLE, Outcome.SUCCESS);
        removeHostScopedRole(client, NEW_ROLE, Outcome.SUCCESS);
    }

    @Test
    public void testAddScopedRoleUsingStandardRoleName() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, "Monitor", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
        addHostScopedRole(client, "Monitor", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
    }

    @Test
    public void testAddScopedRoleUsingStandardRoleNameDifferentCase() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, "MoNiToR", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
        addHostScopedRole(client, "MoNiToR", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
    }

    @Test
    public void testAddScopedRoleUsingExistingScopedRoleName() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, "MainGroupMonitor", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
        addHostScopedRole(client, "HostMasterMonitor", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
    }

    @Test
    public void testAddScopedRoleUsingExistingScopedRoleNameDifferentCase() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, "MaInGrOuPmOnItOr", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
        addHostScopedRole(client, "HoStMaStErMoNiToR", RbacUtil.MAINTAINER_ROLE, Outcome.FAILED);
    }

    // creating scoped roles with various base roles

    @Test
    public void testAddScopedRoleUsingNonexistingBaseRole() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, NEW_ROLE, "NonexistingBaseRole", Outcome.FAILED);
        addHostScopedRole(client, NEW_ROLE, "NonexistingBaseRole", Outcome.FAILED);
    }

    @Test
    public void testAddScopedRoleUsingExistingScopedBaseRole() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, NEW_ROLE, "MainGroupMaintainer", Outcome.FAILED);
        addHostScopedRole(client, NEW_ROLE, "HostMasterMaintainer", Outcome.FAILED);
    }

    @Test
    public void testAddScopedRoleUsingBaseRoleWithDifferentCase() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addServerGroupScopedRole(client, NEW_ROLE, "MaInTaInEr", Outcome.SUCCESS);
        removeServerGroupScopedRole(client, NEW_ROLE, Outcome.SUCCESS);
        addHostScopedRole(client, NEW_ROLE, "MaInTaInEr", Outcome.SUCCESS);
        removeHostScopedRole(client, NEW_ROLE, Outcome.SUCCESS);
    }

    // role mappings

    @Test
    public void testRemoveScopedRoleWithExistingRoleMapping() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        removeServerGroupScopedRole(client, "MainGroupOperator", Outcome.FAILED);
        removeHostScopedRole(client, "HostMasterOperator", Outcome.FAILED);
    }

    @Test
    public void testAddRoleMappingWithoutExistingRole() throws Exception {
        ModelControllerClient client = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        addRoleMapping(client, "NonexistingScopedRole", Outcome.FAILED);
    }

    // test utils

    private static void addServerGroupScopedRole(ModelControllerClient client, String roleName, String baseRole, Outcome expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(ADD, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, ModelDescriptionConstants.AUTHORIZATION),
                pathElement(SERVER_GROUP_SCOPED_ROLE, roleName)
        ));
        operation.get(BASE_ROLE).set(baseRole);
        operation.get(SERVER_GROUPS).add(AbstractServerGroupScopedRolesTestCase.SERVER_GROUP_A);
        RbacUtil.executeOperation(client, operation, expectedOutcome);
    }

    private static void removeServerGroupScopedRole(ModelControllerClient client, String roleName, Outcome expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(REMOVE, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, ModelDescriptionConstants.AUTHORIZATION),
                pathElement(SERVER_GROUP_SCOPED_ROLE, roleName)
        ));
        RbacUtil.executeOperation(client, operation, expectedOutcome);
    }

    private static void addHostScopedRole(ModelControllerClient client, String roleName, String baseRole, Outcome expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(ADD, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, ModelDescriptionConstants.AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, roleName)
        ));
        operation.get(BASE_ROLE).set(baseRole);
        operation.get(HOSTS).add(AbstractHostScopedRolesTestCase.PRIMARY);
        RbacUtil.executeOperation(client, operation, expectedOutcome);
    }

    private static void removeHostScopedRole(ModelControllerClient client, String roleName, Outcome expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(REMOVE, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, ModelDescriptionConstants.AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, roleName)
        ));
        RbacUtil.executeOperation(client, operation, expectedOutcome);
    }

    private static void addRoleMapping(ModelControllerClient client, String roleName, Outcome expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(ADD, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, ModelDescriptionConstants.AUTHORIZATION),
                pathElement(ROLE_MAPPING, roleName)
        ));
        RbacUtil.executeOperation(client, operation, expectedOutcome);
    }
}
