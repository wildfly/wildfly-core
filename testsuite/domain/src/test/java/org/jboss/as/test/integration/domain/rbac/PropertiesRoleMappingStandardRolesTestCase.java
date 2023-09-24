/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_USER;

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
 * Tests of the standard roles authorized against .properties file using the "rbac" access control provider.
 *
 * @author Ladislav Thon <lthon@redhat.com>
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class PropertiesRoleMappingStandardRolesTestCase extends AbstractStandardRolesTestCase {
    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderPropertiesRoleMappingTestSuite.createSupport(PropertiesRoleMappingStandardRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        StandardRolesMappingSetup.INSTANCE.setup(domainClient);
        deployDeployment1(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        try {
            StandardRolesMappingSetup.INSTANCE.tearDown(domainClient);
        } finally {
            try {
                removeDeployment1(domainClient);
            } finally {
                FullRbacProviderPropertiesRoleMappingTestSuite.stopSupport();
                testSupport = null;
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

    static class StandardRolesMappingSetup extends GroupRolesMappingServerSetupTask {
        private static final Map<String, Set<String>> STANDARD_ROLES_TO_GROUPS;

        static {
            Map<String, Set<String>> rolesToGroups = new HashMap<String, Set<String>>();
            rolesToGroups.put(MONITOR_ROLE, Collections.singleton(MONITOR_USER));
            rolesToGroups.put(OPERATOR_ROLE, Collections.singleton(OPERATOR_USER));
            rolesToGroups.put(MAINTAINER_ROLE, Collections.singleton(MAINTAINER_USER));
            rolesToGroups.put(DEPLOYER_ROLE, Collections.singleton(DEPLOYER_USER));
            rolesToGroups.put(ADMINISTRATOR_ROLE, Collections.singleton(ADMINISTRATOR_USER));
            rolesToGroups.put(AUDITOR_ROLE, Collections.singleton(AUDITOR_USER));
            rolesToGroups.put(SUPERUSER_ROLE, Collections.singleton(SUPERUSER_USER));
            STANDARD_ROLES_TO_GROUPS = rolesToGroups;
        }

        static final StandardRolesMappingSetup INSTANCE = new StandardRolesMappingSetup();

        protected StandardRolesMappingSetup() {
            super(STANDARD_ROLES_TO_GROUPS);
        }
    }
}
