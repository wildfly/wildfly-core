/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.suites.SimpleRbacProviderTestSuite;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Tests of host scoped roles using the "simple" access control provider.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SimpleProviderHostScopedRolesTestCase extends AbstractHostScopedRolesTestCase {

    @BeforeClass
    public static void setupDomain() throws Exception {

        // Launch the domain

        testSupport = SimpleRbacProviderTestSuite.createSupport(SimpleProviderHostScopedRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        setupRoles(domainClient);
        deployDeployment1(domainClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();

        try {
            tearDownRoles(domainClient);
        } finally {
            try {
                removeDeployment1(domainClient);
            } finally {
                testSupport = null;
                SimpleRbacProviderTestSuite.stopSupport();
            }
        }
    }

    @Override
    protected boolean isAllowLocalAuth() {
        return true;
    }

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        RbacUtil.addRoleHeader(op, roles);
    }
}
