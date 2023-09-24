/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

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
import static org.jboss.as.test.integration.management.rbac.RbacUtil.assertIsCallerInRole;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.setRoleMappingIncludeAll;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({StandardUsersSetupTask.class})
public class IncludeAllRoleTestCase extends AbstractRbacTestCase {

    @Test
    public void testMonitor() throws Exception {
        test(MONITOR_USER, MONITOR_ROLE, MAINTAINER_ROLE);
    }

    @Test
    public void testOperator() throws Exception {
        test(OPERATOR_USER, OPERATOR_ROLE, MAINTAINER_ROLE);
    }

    @Test
    public void testMaintainer() throws Exception {
        test(MAINTAINER_USER, MAINTAINER_ROLE, AUDITOR_ROLE);
    }

    @Test
    public void testDeployer() throws Exception {
        test(DEPLOYER_USER, DEPLOYER_ROLE, AUDITOR_ROLE);
    }

    @Test
    public void testAdministrator() throws Exception {
        test(ADMINISTRATOR_USER, ADMINISTRATOR_ROLE, AUDITOR_ROLE);
    }

    @Test
    public void testAuditor() throws Exception {
        test(AUDITOR_USER, AUDITOR_ROLE, ADMINISTRATOR_ROLE);
    }

    @Test
    public void testSuperUser() throws Exception {
        test(SUPERUSER_USER, SUPERUSER_ROLE, AUDITOR_ROLE);
    }

    private void test(String userName, String defaultRole, String additionalRole) throws Exception {
        ModelControllerClient mgmtClient = getManagementClient().getControllerClient();

        ModelControllerClient newUserClient = getClientForUser(userName);
        for (String role : RbacUtil.allStandardRoles()) {
            assertIsCallerInRole(newUserClient, role, defaultRole.equals(role));
        }
        removeClientForUser(userName);

        setRoleMappingIncludeAll(mgmtClient, additionalRole, true);

        newUserClient = getClientForUser(userName);
        for (String role : RbacUtil.allStandardRoles()) {
            assertIsCallerInRole(newUserClient, role, defaultRole.equals(role) || additionalRole.equals(role));
        }
        removeClientForUser(userName);

        setRoleMappingIncludeAll(mgmtClient, additionalRole, false);

        newUserClient = getClientForUser(userName);
        for (String role : RbacUtil.allStandardRoles()) {
            assertIsCallerInRole(newUserClient, role, defaultRole.equals(role));
        }
        removeClientForUser(userName);
    }
}
