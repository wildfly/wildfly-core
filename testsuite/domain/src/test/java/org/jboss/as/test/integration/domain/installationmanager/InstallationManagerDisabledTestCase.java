/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.installationmanager;

import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.test.installationmanager.RejectingInstallationManagerFactory;
import org.wildfly.test.installationmanager.TestInstallationManager;
import org.wildfly.test.installationmanager.TestInstallationManagerFactory;

public class InstallationManagerDisabledTestCase extends AbstractCliTestBase {

    private static final String MODULE_NAME = "org.jboss.prospero";
    private static DomainTestSupport testSupport;
    private static TestModule testModule;

    static final Path TARGET_DIR = Paths.get(System.getProperty("basedir", ".")).resolve("target");

    @BeforeClass
    public static void setupDomain() throws Exception {
        TestInstallationManagerFactory.validInstallation = false;
        createTestModule();
        testSupport = DomainTestSupport.createAndStartDefaultSupport(InstallationManagerIntegrationTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        TestInstallationManagerFactory.validInstallation = true;
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

    private static void createTestModule() throws IOException {
        testModule = new TestModule(MODULE_NAME, "org.wildfly.installation-manager.api");
        testModule.addResource("test-mock-installation-manager.jar").addClass(TestInstallationManager.class).addClass(RejectingInstallationManagerFactory.class)
                .addAsManifestResource(new StringAsset(RejectingInstallationManagerFactory.class.getName()),
                        "services/org.wildfly.installationmanager.spi.InstallationManagerFactory");
        testModule.create(true);
    }

    @After
    public void clean() throws IOException {
        for(File testZip : TARGET_DIR.toFile().listFiles((dir, name) -> name.startsWith("installation-manager") && name.endsWith(".zip"))) {
            Files.deleteIfExists(testZip.toPath());
        }
    }

    @Test
    public void installerSubsystemIsNotAvailableWhenServerLacksInstManMetadata() throws Exception {
        String host = "primary";
        AssertionError exception = assertThrows(AssertionError.class, () -> {
            try {
                cli.sendLine("installer history --host=" + host);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        String expectedMessage = "The command is not available in the current context";
        String actualMessage = exception.getMessage();
        Assert.assertTrue(actualMessage, actualMessage.contains(expectedMessage));
    }
}
