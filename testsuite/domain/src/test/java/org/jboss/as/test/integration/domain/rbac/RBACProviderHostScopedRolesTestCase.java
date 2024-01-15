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
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Tests of host scoped roles using the "rbac" access control provider.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class RBACProviderHostScopedRolesTestCase extends AbstractHostScopedRolesTestCase {

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderTestSuite.createSupport(RBACProviderHostScopedRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        setupRoles(domainClient);
        HostRolesMappingSetup.INSTANCE.setup(domainClient);
        deployDeployment1(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        try {
            HostRolesMappingSetup.INSTANCE.tearDown(domainClient);
        } finally {
            try {
                tearDownRoles(domainClient);
            } finally {
                try {
                    removeDeployment1(domainClient);
                } finally {
                    FullRbacProviderTestSuite.stopSupport();
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

    static class HostRolesMappingSetup extends UserRolesMappingServerSetupTask {

        private static final Map<String, Set<String>> STANDARD_USERS;

        static {
            Map<String, Set<String>> rolesToUsers = new HashMap<String, Set<String>>();
            rolesToUsers.put(MONITOR_USER, Collections.singleton(MONITOR_USER));
            rolesToUsers.put(OPERATOR_USER, Collections.singleton(OPERATOR_USER));
            rolesToUsers.put(MAINTAINER_USER, Collections.singleton(MAINTAINER_USER));
            rolesToUsers.put(DEPLOYER_USER, Collections.singleton(DEPLOYER_USER));
            rolesToUsers.put(ADMINISTRATOR_USER, Collections.singleton(ADMINISTRATOR_USER));
            rolesToUsers.put(AUDITOR_USER, Collections.singleton(AUDITOR_USER));
            rolesToUsers.put(SUPERUSER_USER, Collections.singleton(SUPERUSER_USER));
            rolesToUsers.put(SECONDARY_OPERATOR_USER, Collections.singleton(SECONDARY_OPERATOR_USER));
            rolesToUsers.put(SECONDARY_MAINTAINER_USER, Collections.singleton(SECONDARY_MAINTAINER_USER));
            rolesToUsers.put(SECONDARY_DEPLOYER_USER, Collections.singleton(SECONDARY_DEPLOYER_USER));
            rolesToUsers.put(SECONDARY_ADMINISTRATOR_USER, Collections.singleton(SECONDARY_ADMINISTRATOR_USER));
            rolesToUsers.put(SECONDARY_AUDITOR_USER, Collections.singleton(SECONDARY_AUDITOR_USER));
            rolesToUsers.put(SECONDARY_SUPERUSER_USER, Collections.singleton(SECONDARY_SUPERUSER_USER));
            STANDARD_USERS = rolesToUsers;
        }

        static final HostRolesMappingSetup INSTANCE = new HostRolesMappingSetup();

        protected HostRolesMappingSetup() {
            super(STANDARD_USERS);
        }
    }
}
