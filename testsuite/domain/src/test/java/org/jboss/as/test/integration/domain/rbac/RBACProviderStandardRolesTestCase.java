/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
//import org.junit.Ignore;

/**
 * Tests of the standard roles using the "rbac" access control provider.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class RBACProviderStandardRolesTestCase extends AbstractStandardRolesTestCase {

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderTestSuite.createSupport(RBACProviderStandardRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.setup(domainClient);
        deployDeployment1(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {

        try {
            UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.tearDown(testSupport.getDomainPrimaryLifecycleUtil().getDomainClient());
        } finally {
            try {
                removeDeployment1(testSupport.getDomainPrimaryLifecycleUtil().getDomainClient());
            } finally {
                FullRbacProviderTestSuite.stopSupport();
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
}
