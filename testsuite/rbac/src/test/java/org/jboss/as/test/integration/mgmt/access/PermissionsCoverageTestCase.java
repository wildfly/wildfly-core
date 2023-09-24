/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.test.integration.management.rbac.PermissionsCoverageTestUtil.assertTheEntireDomainTreeHasPermissionsDefined;

import jakarta.inject.Inject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
public class PermissionsCoverageTestCase {
    @Inject
    private ManagementClient managementClient;

    @Test
    public void testTheEntireDomainTreeHasPermissionsDefined() throws Exception {
        ModelControllerClient client = managementClient.getControllerClient();
        assertTheEntireDomainTreeHasPermissionsDefined(client);
    }
}
