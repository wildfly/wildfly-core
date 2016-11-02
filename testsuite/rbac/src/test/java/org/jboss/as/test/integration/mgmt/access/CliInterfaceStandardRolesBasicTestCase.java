/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.mgmt.access;

import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;

import java.security.Security;

import org.jboss.as.test.integration.management.interfaces.CliManagementInterface;
import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.WildFlyElytronProvider;

/**
 * @author jcechace
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
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
