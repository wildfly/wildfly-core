/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import static org.jboss.as.test.integration.management.rbac.PermissionsCoverageTestUtil.assertTheEntireDomainTreeHasPermissionsDefined;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
//import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class PermissionsCoverageTestCase extends AbstractRbacTestCase {
    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderTestSuite.createSupport(PermissionsCoverageTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.setup(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            UserRolesMappingServerSetupTask.StandardUsersSetup.INSTANCE.tearDown(testSupport.getDomainPrimaryLifecycleUtil().getDomainClient());
        } finally {
            FullRbacProviderTestSuite.stopSupport();
            testSupport = null;
        }
    }

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        // no-op. Role mapping is done based on the client's authenticated Subject
    }

    @Test
    public void testTheEntireDomainTreeHasPermissionsDefined() throws IOException {
        ModelControllerClient client = getClientForUser(RbacUtil.SUPERUSER_USER, true, primaryClientConfig);
        assertTheEntireDomainTreeHasPermissionsDefined(client);
    }
}
