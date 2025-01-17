/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Simple {@code Suite} test wrapper to start the domain only once for multiple
 * test cases using the same domain configuration.
 *
 * @author Emanuel Muckenhuber
 */
@RunWith(Suite.class)
@Suite.SuiteClasses ({
        DomainSuspendResumeTestCase.class,
        HostSuspendResumeTestCase.class,
        SyncModelOperationTestCase.class,
        ManagementReadXmlTestCase.class,
        ManagementReadXmlAsFileTestCase.class,
        HostReloadProxyTestCase.class,
        AuditLogTestCase.class,
        ReloadWithConfigTestCase.class,
        IgnoredResourcesProfileCloneTestCase.class,
        CapabilityReloadRequiredUnitTestCase.class,
        CompositeOperationTestCase.class,
        CoreResourceManagementTestCase.class,
        CustomManagementContextTestCase.class,
        DeploymentRolloutFailureTestCase.class,
        ExplodedDeploymentTestCase.class,
        DirectoryGroupingByTypeTestCase.class,
        ExtensionManagementTestCase.class,
        FullReplaceUndeployTestCase.class,
        HcExtensionAndSubsystemManagementTestCase.class,
        IgnoreUnusedConfigurationTestCase.class,
        IgnoredResourcesTestCase.class,
        JMXHostSubsystemTestCase.class,
        ManagementAccessTestCase.class,
        ManagementClientContentTestCase.class,
        HostLifecycleWithRolloutPlanTestCase.class,
        ManagementReadsTestCase.class,
        ManagementVersionTestCase.class,
        ModuleLoadingManagementTestCase.class,
        OperationCancellationTestCase.class,
        OperationTransformationTestCase.class,
        OperationWarningTestsCase.class,
        PrivateHiddenOperationsTestCase.class,
        ResponseStreamTestCase.class,
        RuntimeOnlyOperationsTestCase.class,
        ServerRestartRequiredTestCase.class,
        ValidateAddressOperationTestCase.class,
        ValidateOperationOperationTestCase.class,
        WildcardOperationsTestCase.class,
        ServerAuthenticationTestCase.class,
})
public class DomainTestSuite {

    private static boolean initializedLocally = false;
    private static volatile DomainTestSupport support;

    // This can only be called from tests as part of this suite
    static synchronized DomainTestSupport createSupport(final String testName) {
        if(support == null) {
            start(testName);
        }
        support.recordInitialHostState();
        return support;
    }

    // This can only be called from tests as part of this suite
    static synchronized void stopSupport() {
        AssertionError invalidHostState = null;
        if (support != null) {
            try {
                support.assertCurrentHostState();
            } catch (AssertionError e) {
                invalidHostState = e;
            }
        }
        if(! initializedLocally) {
            stop();
        }
        if (invalidHostState != null) {
            throw invalidHostState;
        }
    }

    private static synchronized void start(final String name) {
        try {
            support = DomainTestSupport.createAndStartDefaultSupport(name);
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
        start(DomainTestSuite.class.getSimpleName());
    }

    @AfterClass
    public static synchronized void afterClass() {
        stop();
        initializedLocally = false;
    }

}
