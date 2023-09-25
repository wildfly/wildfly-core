/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.integration.domain.rbac.PropertiesRoleMappingHostScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.PropertiesRoleMappingServerGroupScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.PropertiesRoleMappingStandardRolesTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Simple {@code Suite} test wrapper to start the domain only once for multiple
 * test cases using the same full-RBAC-provider-enabled domain configuration
 * with authorization against .properties file.
 *
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses ({
        PropertiesRoleMappingStandardRolesTestCase.class,
        PropertiesRoleMappingServerGroupScopedRolesTestCase.class,
        PropertiesRoleMappingHostScopedRolesTestCase.class
})
public class FullRbacProviderPropertiesRoleMappingTestSuite {

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
        start(FullRbacProviderPropertiesRoleMappingTestSuite.class.getSimpleName());
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
                    "domain-configs/domain-rbac.rbac", "host-configs/host-primary-rbac-properties.xml", "host-configs/host-secondary-rbac-properties.xml");
            String mgmtUserProperties = WildFlyManagedConfiguration.loadConfigFileFromContextClassLoader("mgmt-users/mgmt-users.properties");
            String mgmtGroupsProperties = WildFlyManagedConfiguration.loadConfigFileFromContextClassLoader("mgmt-users/mgmt-groups.properties");
            configuration.getPrimaryConfiguration().setMgmtUsersFile(mgmtUserProperties);
            configuration.getSecondaryConfiguration().setMgmtUsersFile(mgmtUserProperties);
            configuration.getPrimaryConfiguration().setMgmtGroupsFile(mgmtGroupsProperties);
            configuration.getSecondaryConfiguration().setMgmtGroupsFile(mgmtGroupsProperties);
            final DomainTestSupport testSupport = DomainTestSupport.create(configuration);
            // Start!
            testSupport.start();
            ExtensionSetup.initializeTestExtension(testSupport);
            ExtensionSetup.addExtensionAndSubsystem(testSupport);
            return testSupport;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
