/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author jcechace
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({StandardExtensionSetupTask.class, StandardUsersSetupTask.class})
public class Jmx2ndInterfaceStandardRolesBasicTestCase extends JmxInterfaceStandardRolesBasicTestCase {
    @Override
    protected String getJmxDomain() {
        return "jboss.as.expr";
    }
}
