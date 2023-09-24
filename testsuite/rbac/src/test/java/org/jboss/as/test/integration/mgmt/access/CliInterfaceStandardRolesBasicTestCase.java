/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;

import java.security.Security;

import org.jboss.as.test.integration.management.interfaces.CliManagementInterface;
import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.WildFlyElytronProvider;

/**
 * @author jcechace
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({StandardUsersSetupTask.class, StandardExtensionSetupTask.class})
public class CliInterfaceStandardRolesBasicTestCase extends StandardRolesBasicTestCase {

    @BeforeClass
    public static void installProvider() {
        Security.insertProviderAt(new WildFlyElytronProvider(), 0);
    }

    @Override
    protected ManagementInterface createClient(String userName) {
        return CliManagementInterface.create(
                getManagementClient().getMgmtAddress(), getManagementClient().getMgmtPort(),
                userName, RbacAdminCallbackHandler.STD_PASSWORD
        );
    }
}
