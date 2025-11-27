/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.installationmanager;

import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.test.installationmanager.AbstractInstallationManagerTestCase;

/**
 * Tests the high-level Installation Manager commands in domain mode environment. It uses a mocked implementation of the
 * installation manager, which provides dummy data for the test.
 * <p>
 * The purpose of this test is to ensure that the high-level commands, which rely on low-level management operations, can
 * retrieve the data from the mocked implementation.
 * <p>
 * See InstMgrResourceTestCase for low-level management operation unit testing.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InstallationManagerIntegrationTestCase extends AbstractInstallationManagerTestCase {

    private static DomainTestSupport testSupport;

    private static Path primaryPrepareServerDir;
    private static Path secondaryPrepareServerDir;

    static Path primaryCustomPatchBaseDir;
    static Path secondaryCustomPatchBaseDir;

    @BeforeClass
    public static void setupDomain() throws Exception {
        createTestModule();
        testSupport = DomainTestSupport.createAndStartDefaultSupport(InstallationManagerIntegrationTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);

        primaryPrepareServerDir = Paths.get(testSupport.getDomainPrimaryConfiguration().getDomainDirectory()).resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        secondaryPrepareServerDir = Paths.get(testSupport.getDomainSecondaryConfiguration().getDomainDirectory()).resolve("tmp")
                .resolve(InstMgrConstants.PREPARED_SERVER_SUBPATH);

        primaryCustomPatchBaseDir = Paths.get(testSupport.getDomainPrimaryConfiguration().getJbossHome()).resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);

        secondaryCustomPatchBaseDir = Paths.get(testSupport.getDomainSecondaryConfiguration().getJbossHome()).resolve(InstMgrConstants.CUSTOM_PATCH_SUBPATH);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            AbstractCliTestBase.closeCLI();
            if (testSupport != null) {
                testSupport.close();
            }
            testSupport = null;
        } finally {
            testModule.remove();
        }
    }

    @Override
    protected LinkedHashMap<String, ServerPaths> getServerDirs() {
        // Note: preserve order of insertion using LinkedHashMap
        final LinkedHashMap<String, ServerPaths> dirs = new LinkedHashMap<>();
        dirs.put("primary", new ServerPaths(primaryPrepareServerDir, primaryCustomPatchBaseDir));
        dirs.put("secondary", new ServerPaths(secondaryPrepareServerDir, secondaryCustomPatchBaseDir));
        return dirs;
    }

    @Test
    public void requireHost() {
        String expectedMessage = "The --host option must be used in domain mode.";
        testHostParameter("", (actualMessage) -> assertTrue(actualMessage, actualMessage.contains(expectedMessage)));
    }
}
