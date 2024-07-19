/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.installationmanager;

import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;

import jakarta.inject.Inject;

import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.installationmanager.BaseInstallationManagerTestCase;

/**
 * Tests the high-level Installation Manager commands in standalone mode environment. It uses a mocked implementation of the
 * installation manager, which provides dummy data for the test.
 * <p>
 * The purpose of this test is to ensure that the high-level commands, which rely on low-level management operations, can
 * retrieve the data from the mocked implementation.
 * <p>
 * See InstMgrResourceTestCase for low-level management operation unit testing.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InstallationManagerIntegrationTestCase extends BaseInstallationManagerTestCase {
    private static Path prepareServerDir;
    static Path customPatchBaseDir;

    @Inject
    protected static ManagementClient client;
    @Inject
    protected static ServerController container;

    @BeforeClass
    public static void setupDomain() throws Exception {
        createTestModule();
        container.start();
        AbstractCliTestBase.initCLI();

        prepareServerDir = Paths.get(TestSuiteEnvironment.getJBossHome()).resolve("standalone").resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        customPatchBaseDir = Paths.get(TestSuiteEnvironment.getJBossHome()).resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            AbstractCliTestBase.closeCLI();
        } finally {
            container.stop();
            testModule.remove();
        }
    }

    @Override
    protected LinkedHashMap<String, ServerPaths> getServerDirs() {
        // Note: preserve order of insertion using LinkedHashMap
        final LinkedHashMap<String, ServerPaths> dirs = new LinkedHashMap<>();
        dirs.put(DEFAULT_HOST, new ServerPaths(prepareServerDir, customPatchBaseDir));
        return dirs;
    }

    @Override
    protected String getNoPreparedServerErrorCode() {
        return "WFLYSRV0295:";
    }

    @Test
    public void rejectsHost() {
        final String expectedMessage = "The --host option is not available in the current context.";
        testHostParameter("--host=test", (actualMessage)->assertTrue(actualMessage, actualMessage.contains(expectedMessage)));
    }
}
