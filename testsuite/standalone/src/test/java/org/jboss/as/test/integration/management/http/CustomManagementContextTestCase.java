/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.http;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.test.integration.management.extension.customcontext.testbase.CustomManagementContextTestBase;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test of integrating a custom management context on the http interface on a standalone server.
 *
 * @author Brian Stansberry
 */
@RunWith(WildFlyRunner.class)
public class CustomManagementContextTestCase extends CustomManagementContextTestBase {

    @Inject
    private ManagementClient managementClient;

    @Override
    protected PathAddress getExtensionAddress() {
        return EXT;
    }

    @Override
    protected PathAddress getSubsystemAddress() {
        return PathAddress.pathAddress(SUB);
    }

    @Override
    protected ManagementClient getManagementClient() {
        return managementClient;
    }
}
