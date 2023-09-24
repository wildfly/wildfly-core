/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class StandardUsersSetupTask extends UserRolesMappingServerSetupTask.StandardUsersSetup implements ServerSetupTask {

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        setup(managementClient.getControllerClient());
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        tearDown(managementClient.getControllerClient());
    }
}
