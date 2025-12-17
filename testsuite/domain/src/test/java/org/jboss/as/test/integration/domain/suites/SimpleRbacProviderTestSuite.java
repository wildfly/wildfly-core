/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import java.io.IOException;
import java.net.URL;

import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.integration.domain.rbac.AccessConstraintUtilizationTestCase;
import org.jboss.as.test.integration.domain.rbac.SimpleProviderHostScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.SimpleProviderServerGroupScopedRolesTestCase;
import org.jboss.as.test.integration.domain.rbac.SimpleProviderStandardRolesTestCase;
import org.jboss.as.test.module.util.TestModule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.wildfly.test.installationmanager.TestInstallationManager;
import org.wildfly.test.installationmanager.TestInstallationManagerFactory;

/**
 * Simple {@code Suite} test wrapper to start the domain only once for multiple
 * test cases using the same simple-RBAC-provider-enabled domain configuration.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses ({
        AccessConstraintUtilizationTestCase.class,
        SimpleProviderHostScopedRolesTestCase.class,
        SimpleProviderServerGroupScopedRolesTestCase.class,
        SimpleProviderStandardRolesTestCase.class
})
public class SimpleRbacProviderTestSuite {

    private static final String MODULE_NAME = "org.jboss.prospero";
    private static TestModule testModule;


    private static boolean initializedLocally = false;
    private static volatile DomainTestSupport support;

    /** This can only be called from tests as part of this suite */
    public static synchronized DomainTestSupport createSupport(final String testName) throws IOException {
        if(support == null) {
            createTestModule();
            start(testName);
        }
        return support;
    }

    /** This can only be called from tests as part of this suite */
    public static synchronized void stopSupport() {
        if(! initializedLocally) {
            try {
                stop();
            } finally {
                testModule.remove();
            }
        }
    }

    private static synchronized void start(final String name) {
        try {
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
    public static synchronized void beforeClass() throws IOException {
        initializedLocally = true;
        createTestModule();
        start(SimpleRbacProviderTestSuite.class.getSimpleName());
    }

    @AfterClass
    public static synchronized void afterClass() {
        try {
            stop();
        } finally {
            testModule.remove();
        }
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
                    "domain-configs/domain-rbac.xml", "host-configs/host-primary-rbac.xml", "host-configs/host-secondary-rbac.xml");
            String mgmtUserProperties = WildFlyManagedConfiguration.loadConfigFileFromContextClassLoader("mgmt-users/mgmt-users.properties");
            configuration.getPrimaryConfiguration().setMgmtUsersFile(mgmtUserProperties);
            configuration.getSecondaryConfiguration().setMgmtUsersFile(mgmtUserProperties);
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

    public static boolean isTestSuiteEnabled() {
        return initializedLocally;
    }

    private static void createTestModule() throws IOException {
        testModule = new TestModule(MODULE_NAME, "org.wildfly.installation-manager.api");

        final URL serviceLoader = SimpleRbacProviderTestSuite.class.getClassLoader()
                .getResource("org/wildfly/test/installationmanager/services/org.wildfly.installationmanager.spi.InstallationManagerFactory");

        testModule.addResource("test-mock-installation-manager.jar")
                .addClass(TestInstallationManager.class)
                .addClass(TestInstallationManagerFactory.class)
                .addAsManifestResource(serviceLoader,
                        "services/org.wildfly.installationmanager.spi.InstallationManagerFactory");

        testModule.create(true);
    }
}
