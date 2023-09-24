/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.integration.domain.rbac.AbstractHostScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.AbstractServerGroupScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.IncludeAllRoleTestCase;
import org.jboss.as.test.integration.domain.rbac.JmxRBACProviderHostScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.JmxRBACProviderServerGroupScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.ListRoleNamesTestCase;
import org.jboss.as.test.integration.domain.rbac.PermissionsCoverageTestCase;
import org.jboss.as.test.integration.domain.rbac.RBACProviderHostScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.RBACProviderServerGroupScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.RBACProviderStandardRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.RBACSensitivityConstraintUtilizationTestCase;
import org.jboss.as.test.integration.domain.rbac.RolesIntegrityCheckingTestCase;
import org.jboss.as.test.integration.domain.rbac.WildcardReadsTestCase;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

/**
 * Simple {@code Suite} test wrapper to start the domain only once for multiple
 * test cases using the same full-RBAC-provider-enabled domain configuration.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses ({
        IncludeAllRoleTestCase.class,
        JmxRBACProviderHostScopedRolesTestCase.class,
        JmxRBACProviderServerGroupScopedRolesTestCase.class,
        ListRoleNamesTestCase.class,
        PermissionsCoverageTestCase.class,
        RBACProviderHostScopedRolesTestCase.class,
        RBACProviderServerGroupScopedRolesTestCase.class,
        RBACProviderStandardRolesTestCase.class,
        RBACSensitivityConstraintUtilizationTestCase.class,
        RolesIntegrityCheckingTestCase.class,
        WildcardReadsTestCase.class
})
public class FullRbacProviderTestSuite {

    private static boolean initializedLocally = false;
    private static volatile DomainTestSupport support;

    /** This can only be called from tests as part of this suite */
    public static synchronized DomainTestSupport createSupport(final String testName) {
        if(support == null) {
            start(testName);
        }
        return support;
    }

    /** This can only be called from tests as part of this suite */
    public static synchronized void stopSupport() {
        if(! initializedLocally) {
            stop();
        }
    }

    private static synchronized void start(final String name) {
        try {
            //if (true) return; // [WFCORE-1958] Clean up testsuite Elytron registration.
            support = createAndStartDefaultSupport(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static synchronized void stop() {
        if(support != null) {
            support.close();
            support = null;
        }
    }

    @BeforeClass
    public static synchronized void beforeClass() {
        initializedLocally = true;
        start(FullRbacProviderTestSuite.class.getSimpleName());
    }

    @AfterClass
    public static synchronized void afterClass() {
        stop();
    }

    /**
     * Create and start a default configuration for the domain tests.
     *
     * @param testName the test name
     * @return a started domain test support
     */
    public static DomainTestSupport createAndStartDefaultSupport(final String testName) {
        try {
            final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(testName,
                    "domain-configs/domain-rbac.rbac", "host-configs/host-primary-rbac.xml", "host-configs/host-secondary-rbac.xml");
            String mgmtUserProperties = WildFlyManagedConfiguration.loadConfigFileFromContextClassLoader("mgmt-users/mgmt-users.properties");
            configuration.getPrimaryConfiguration().setMgmtUsersFile(mgmtUserProperties);
            configuration.getSecondaryConfiguration().setMgmtUsersFile(mgmtUserProperties);
            final DomainTestSupport testSupport = DomainTestSupport.create(configuration);
            // Add users used by tests.
            List<String> users = new ArrayList<String>();
            addUsers(users, AbstractHostScopedRolesTestCase.USERS);
            addUsers(users, AbstractServerGroupScopedRolesTestCase.USERS);
            addUsers(users, "SecondaryHostSuperUser", "OtherGroupSuperUser");
            Files.write((new File(System.getProperty("jboss.home")).toPath().resolve("domain").resolve("configuration").resolve("application-users.properties")), users, Charset.forName("UTF-8"));
            // Start!
            testSupport.start();
            ExtensionSetup.initializeTestExtension(testSupport);
            ExtensionSetup.addExtensionAndSubsystem(testSupport);
            return testSupport;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addUsers(final List<String> users, String... userNames) throws Exception {
        for (String userName : userNames) {
            users.add(userName + "=" + new UsernamePasswordHashUtil().generateHashedHexURP(userName, "ApplicationRealm", RbacAdminCallbackHandler.STD_PASSWORD.toCharArray()));
        }
    }
}
