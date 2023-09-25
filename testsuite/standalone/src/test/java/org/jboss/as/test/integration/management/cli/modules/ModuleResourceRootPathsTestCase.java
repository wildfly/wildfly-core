/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.modules;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Inject;

import org.jboss.as.cli.Util;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test module add command and loading of modules with resources with absolute/relative paths (MODULES-218)
 *
 * @author Martin Simka
 */
@RunWith(WildFlyRunner.class)
public class ModuleResourceRootPathsTestCase extends AbstractCliTestBase {
    private static final String MODULE_RESOURCE_MODULE_NAME = "module.resource.test";
    private static final String ABSOLUTE_RESOURCE_MODULE_NAME = "absolute.resource.test";
    private static final String MODULE_RESOURCE_JAR_NAME = "module-resource.jar";
    private static final String ABSOLUTE_RESOURCE_JAR_NAME = "absolute-resource.jar";
    private File moduleResource;
    private File absoluteResource;

    @Inject
    private ManagementClient client;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void before() throws Exception {
        createResources();
        AbstractCliTestBase.initCLI();
        addModule(MODULE_RESOURCE_MODULE_NAME, true, false);
        addModule(ABSOLUTE_RESOURCE_MODULE_NAME, false, true);
        deployTestWar();
    }

    @After
    public void after() throws Exception {
        undeployTestWar();
        try {
            removeModule(ABSOLUTE_RESOURCE_MODULE_NAME);
            removeModule(MODULE_RESOURCE_MODULE_NAME);
        } catch (AssertionError e) {
            // ignore failure on Windows, cannot remove module on running server due to file locks
            if (!Util.isWindows())
                throw e;
        }
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void testModules() throws Exception {
        // test module resources
        final URL testModuleResourceUrl = new URL(TestSuiteEnvironment.getHttpUrl(), "?action=" + ModulesServiceActivator.ACTION_TEST_MODULE_RESOURCE);
        String response = HttpRequest.get(testModuleResourceUrl.toString(), 1000, 10, TimeUnit.SECONDS);
        Assert.assertEquals(ModuleResource.MODULE_RESOURCE, response);

        // test absolute resources
        final URL testAbsoluteResourceUrl = new URL(TestSuiteEnvironment.getHttpUrl(), "?action=" + ModulesServiceActivator.ACTION_TEST_ABSOLUTE_RESOURCE);
        response = HttpRequest.get(testAbsoluteResourceUrl.toString(), 1000, 10, TimeUnit.SECONDS);
        Assert.assertEquals(AbsoluteResource.ABSOLUTE_RESOURCE, response);
    }

    private void addModule(String name, boolean addModuleResources, boolean addAbsoluteResources) throws IOException {
        cli.sendLine("module add --name=" + name
                + (addModuleResources ? " --resources=" + moduleResource.getCanonicalPath() : "")
                + (addAbsoluteResources ? " --absolute-resources=" + absoluteResource.getCanonicalPath() : ""));
    }

    private void removeModule(String name) {
        cli.sendLine("module remove --name=" + name);
    }

    private void deployTestWar() throws Exception {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "test-archive.war");
        archive.addClasses(ModulesServiceActivator.DEPENDENCIES);
        archive.addAsServiceProviderAndClasses(ServiceActivator.class, ModulesServiceActivator.class)
                .addAsManifestResource(new StringAsset("Dependencies: io.undertow.core," + MODULE_RESOURCE_MODULE_NAME
                        + "," + ABSOLUTE_RESOURCE_MODULE_NAME + "\n"), "MANIFEST.MF");
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(ModulesServiceActivator.DEFAULT_PERMISSIONS), "permissions.xml");
        final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
        helper.deploy("test-archive.war", archive.as(ZipExporter.class).exportAsInputStream());
    }

    private void undeployTestWar() throws Exception {
        final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
        helper.undeploy("test-archive.war");
    }

    private void createResources() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class);
        jar.addClasses(ModuleResource.class);
        moduleResource = new File(tmpDir.getRoot(), MODULE_RESOURCE_JAR_NAME);
        jar.as(ZipExporter.class).exportTo(moduleResource, true);

        jar = ShrinkWrap.create(JavaArchive.class);
        jar.addClasses(AbsoluteResource.class);
        absoluteResource = new File(tmpDir.getRoot(), ABSOLUTE_RESOURCE_JAR_NAME);
        jar.as(ZipExporter.class).exportTo(absoluteResource, true);
    }


}
