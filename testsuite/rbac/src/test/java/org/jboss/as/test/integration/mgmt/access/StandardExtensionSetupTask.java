/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;


import org.jboss.as.test.integration.mgmt.access.extension.ExtensionSetup;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class StandardExtensionSetupTask implements ServerSetupTask {

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        ExtensionSetup.initializeTestExtension();
        ExtensionSetup.addExtensionSubsystemAndResources(managementClient);
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        ExtensionSetup.removeResources(managementClient);
        ExtensionSetup.removeExtensionAndSubsystem(managementClient);
    }
}
