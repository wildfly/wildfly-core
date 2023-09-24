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
 * Tests of host scoped roles authorized against .properties file using the "rbac" access control provider.
 *
 * @author Ladislav Thon <lthon@redhat.com>
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class PropertiesRoleMappingHostScopedRolesTestCase extends AbstractHostScopedRolesTestCase {
    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderPropertiesRoleMappingTestSuite.createSupport(PropertiesRoleMappingHostScopedRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        setupRoles(domainClient);
        HostScopedRolesMappingSetup.INSTANCE.setup(domainClient);
        deployDeployment1(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        try {
            HostScopedRolesMappingSetup.INSTANCE.tearDown(domainClient);
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

    static class HostScopedRolesMappingSetup extends GroupRolesMappingServerSetupTask {
        private static final Map<String, Set<String>> HOST_SCOPED_ROLES_TO_GROUPS;

        static {
            Map<String, Set<String>> rolesToGroups = new HashMap<String, Set<String>>();
            rolesToGroups.put(MONITOR_USER, Collections.singleton(MONITOR_USER));
            rolesToGroups.put(OPERATOR_USER, Collections.singleton(OPERATOR_USER));
            rolesToGroups.put(MAINTAINER_USER, Collections.singleton(MAINTAINER_USER));
            rolesToGroups.put(DEPLOYER_USER, Collections.singleton(DEPLOYER_USER));
            rolesToGroups.put(ADMINISTRATOR_USER, Collections.singleton(ADMINISTRATOR_USER));
            rolesToGroups.put(AUDITOR_USER, Collections.singleton(AUDITOR_USER));
            rolesToGroups.put(SUPERUSER_USER, Collections.singleton(SUPERUSER_USER));
            HOST_SCOPED_ROLES_TO_GROUPS = rolesToGroups;
        }

        static final HostScopedRolesMappingSetup INSTANCE = new HostScopedRolesMappingSetup();

        protected HostScopedRolesMappingSetup() {
            super(HOST_SCOPED_ROLES_TO_GROUPS);
        }
    }
}
