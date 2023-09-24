/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderPropertiesRoleMappingTestSuite;
import org.jboss.as.test.integration.management.rbac.GroupRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
//import org.junit.Ignore;

/**
 * Tests of server group scoped roles authorized against .properties file using the "rbac" access control provider.
 *
 * @author Ladislav Thon <lthon@redhat.com>
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class PropertiesRoleMappingServerGroupScopedRolesTestCase extends AbstractServerGroupScopedRolesTestCase {
    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderPropertiesRoleMappingTestSuite.createSupport(PropertiesRoleMappingServerGroupScopedRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        setupRoles(domainClient);
        ServerGroupScopedRolesMappingSetup.INSTANCE.setup(domainClient);
        deployDeployment1(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        try {
            ServerGroupScopedRolesMappingSetup.INSTANCE.tearDown(domainClient);
        } finally {
            try {
                tearDownRoles(domainClient);
            } finally {
                try {
                    removeDeployment1(domainClient);
                } finally {
                    FullRbacProviderPropertiesRoleMappingTestSuite.stopSupport();
                    testSupport = null;
                }
            }
        }
    }

    @Override
    protected boolean isAllowLocalAuth() {
        return false;
    }

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        // no-op. Role mapping is done based on the client's authenticated Subject
    }

    static class ServerGroupScopedRolesMappingSetup extends GroupRolesMappingServerSetupTask {
        private static final Map<String, Set<String>> SERVER_GROUP_SCOPED_ROLES_TO_GROUPS;

        static {
            Map<String, Set<String>> rolesToGroups = new HashMap<String, Set<String>>();
            rolesToGroups.put(MONITOR_USER, Collections.singleton(MONITOR_USER));
            rolesToGroups.put(OPERATOR_USER, Collections.singleton(OPERATOR_USER));
            rolesToGroups.put(MAINTAINER_USER, Collections.singleton(MAINTAINER_USER));
            rolesToGroups.put(DEPLOYER_USER, Collections.singleton(DEPLOYER_USER));
            rolesToGroups.put(ADMINISTRATOR_USER, Collections.singleton(ADMINISTRATOR_USER));
            rolesToGroups.put(AUDITOR_USER, Collections.singleton(AUDITOR_USER));
            rolesToGroups.put(SUPERUSER_USER, Collections.singleton(SUPERUSER_USER));
            SERVER_GROUP_SCOPED_ROLES_TO_GROUPS = rolesToGroups;
        }

        static final ServerGroupScopedRolesMappingSetup INSTANCE = new ServerGroupScopedRolesMappingSetup();

        protected ServerGroupScopedRolesMappingSetup() {
            super(SERVER_GROUP_SCOPED_ROLES_TO_GROUPS);
        }
    }
}
