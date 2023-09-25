/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;

import java.io.IOException;
import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * WFLY-2270
 *
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(StandardUsersSetupTask.class)
public class RolesIntegrityCheckingTestCase {
    @Inject
    private ManagementClient managementClient;

    @Test
    public void testAddRoleMappingWithoutExistingRole() throws Exception {
        ModelControllerClient client = managementClient.getControllerClient();
        addRoleMapping(client, "NonexistingRole", Outcome.FAILED);
    }

    // test utils

    private static void addRoleMapping(ModelControllerClient client, String roleName, Outcome expectedOutcome) throws IOException {
        ModelNode operation = Util.createOperation(ADD, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, ModelDescriptionConstants.AUTHORIZATION),
                pathElement(ROLE_MAPPING, roleName)
        ));
        RbacUtil.executeOperation(client, operation, expectedOutcome);
    }
}
