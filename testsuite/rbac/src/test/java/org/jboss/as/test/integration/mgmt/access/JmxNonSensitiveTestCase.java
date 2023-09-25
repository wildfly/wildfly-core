/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.wildfly.test.jmx.JMXServiceDeploymentSetupTask;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({StandardExtensionSetupTask.class, StandardUsersSetupTask.class, JMXServiceDeploymentSetupTask.class})
public class JmxNonSensitiveTestCase extends AbstractJmxNonCoreMBeansSensitivityTestCase {
    @Override
    protected boolean isReadAllowed(String userName) {
        return true;
    }

    @Override
    protected boolean isWriteAllowed(String userName) {
        return RbacUtil.OPERATOR_USER.equals(userName)
                || RbacUtil.MAINTAINER_USER.equals(userName)
                || RbacUtil.ADMINISTRATOR_USER.equals(userName)
                || RbacUtil.SUPERUSER_USER.equals(userName);
    }
}
