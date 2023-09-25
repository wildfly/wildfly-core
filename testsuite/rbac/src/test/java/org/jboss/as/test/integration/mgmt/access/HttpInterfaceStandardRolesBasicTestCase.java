/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.as.test.integration.management.interfaces.HttpManagementInterface;
import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author jcechace
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({StandardUsersSetupTask.class, StandardExtensionSetupTask.class})
public class HttpInterfaceStandardRolesBasicTestCase extends StandardRolesBasicTestCase {
    @Override
    protected ManagementInterface createClient(String userName) {
        return HttpManagementInterface.create(
                getManagementClient().getMgmtAddress(), getManagementClient().getMgmtPort(),
                userName, RbacAdminCallbackHandler.STD_PASSWORD
        );
    }
}
