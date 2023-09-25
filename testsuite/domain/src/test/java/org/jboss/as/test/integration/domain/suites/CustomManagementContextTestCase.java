/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.extension.customcontext.testbase.CustomManagementContextTestBase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Test of integrating a custom management context on the http interface on a host controller.
 *
 * @author Brian Stansberry
 */
public class CustomManagementContextTestCase extends CustomManagementContextTestBase {

    private static final PathElement HOST = PathElement.pathElement(ModelDescriptionConstants.HOST, "primary");
    private static DomainTestSupport testSupport;
    private static ManagementClient managementClient;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(CustomManagementContextTestCase.class.getSimpleName());
        DomainClient primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        managementClient = new ManagementClient(primaryClient, TestSuiteEnvironment.getServerAddress(), 9090, "remoting+http");
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {

        testSupport = null;
        managementClient = null;
        DomainTestSuite.stopSupport();
    }

    @Override
    protected PathAddress getExtensionAddress() {
        return PathAddress.pathAddress(HOST).append(EXT);
    }

    @Override
    protected PathAddress getSubsystemAddress() {
        return PathAddress.pathAddress(HOST, SUB);
    }

    @Override
    protected ManagementClient getManagementClient() {
        return managementClient;
    }
}
