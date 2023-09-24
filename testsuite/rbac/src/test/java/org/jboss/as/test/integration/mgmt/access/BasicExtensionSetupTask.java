/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import org.jboss.as.test.integration.mgmt.access.extension.ExtensionSetup;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class BasicExtensionSetupTask implements ServerSetupTask {

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        ExtensionSetup.initializeTestExtension();
        ExtensionSetup.addExtensionAndSubsystem(managementClient);
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        ExtensionSetup.removeExtensionAndSubsystem(managementClient);
    }
}
