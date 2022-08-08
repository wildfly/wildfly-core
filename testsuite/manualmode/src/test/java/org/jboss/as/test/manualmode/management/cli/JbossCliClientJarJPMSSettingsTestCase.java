/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.management.cli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.PropertyPermission;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.manualmode.management.cli.jpms.JPMSTestActivator;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Launches a non-modular CLI client and deploys an application that requires specific JPMS settings. This test case is used to
 * verify that jboss-cli-client.jar is able to run properly without requiring the user to add JVM modular settings when it
 * launches an embedded server.
 */
public class JbossCliClientJarJPMSSettingsTestCase {
    private static final Path SERVER_HOME = Paths.get(TestSuiteEnvironment.getJBossHome());
    private static CliProcessWrapper cli;
    private static File cliTestAppJar;

    @BeforeClass
    public static void setup() throws Exception {
        cli = new CliProcessWrapper(false).addCliArgument("--no-color-output").addCliArgument("--timeout=30000");
        cli.executeInteractive();
        cliTestAppJar = createTestArchive("jpms-test-app.jar");
    }

    @AfterClass
    public static void cleanup() throws Exception {
        cli.destroyProcess();
        Files.deleteIfExists(cliTestAppJar.toPath());
    }

    @Test
    public void testManifestEntries() throws Exception {
        JarFile clientJar = new JarFile(SERVER_HOME.resolve("bin").resolve("client").resolve("jboss-cli-client.jar").toString());
        Manifest manifest = clientJar.getManifest();
        Attributes mainAttribs = manifest.getMainAttributes();
        String addOpens = mainAttribs.getValue("Add-Opens");
        Assert.assertTrue("Unexpected value for Manifest Add-Opens. Current: " + addOpens,
                addOpens.contains("java.base/java.util"));
        Assert.assertTrue("Unexpected value for Manifest Add-Opens. Current: " + addOpens,
                addOpens.contains("java.base/java.lang.invoke"));
    }

    @Test
    public void testDeploymentRequiringJMPSSettings() throws Exception {
        String line = "embed-server --admin-only=false --jboss-home=" + SERVER_HOME.toAbsolutePath();
        cli.pushLineAndWaitForResults(line);
        Assert.assertTrue("The CLI message that describes it is running on a non-modular environment was not found: \n" + cli.getOutput(), cli.getOutput().contains("non-modular"));
        cli.clearOutput();

        try {
            // ensure there is no deployment yet
            cli.pushLineAndWaitForResults("deployment-info --name=" + cliTestAppJar.getName());
            // We should get: WFLYCTL0216: Management resource '[("deployment" => "jpms-test-app.jar")]' not found
            Assert.assertTrue("Apparently the deployment already exists: \n" + cli.getOutput(), cli.getOutput().contains("WFLYCTL0216"));
            cli.clearOutput();

            // ensure that properties added by the deployment are not there
            cli.pushLineAndWaitForResults("/core-service=platform-mbean/type=runtime:read-attribute(name=system-properties)");
            Assert.assertTrue(JPMSTestActivator.DEFAULT_SYS_PROP_NAME + " name has been found after getting current System Properties. \n" + cli.getOutput(), !cli.getOutput().contains(JPMSTestActivator.DEFAULT_SYS_PROP_NAME));
            Assert.assertTrue(JPMSTestActivator.DEFAULT_SYS_PROP_VALUE + " value has been found after getting current System Properties. \n" + cli.getOutput(), !cli.getOutput().contains(JPMSTestActivator.DEFAULT_SYS_PROP_VALUE));
            cli.clearOutput();

            // deploy and verify
            cli.pushLineAndWaitForResults("deploy " + cliTestAppJar.getAbsolutePath());
            cli.pushLineAndWaitForResults("deployment-info --name=" + cliTestAppJar.getName());
            Assert.assertTrue("The deployment Failed: \n" + cli.getOutput(), !cli.getOutput().contains("WFLYCTL0216"));
            cli.clearOutput();

            // verifies the activator was executed
            cli.pushLineAndWaitForResults("/core-service=platform-mbean/type=runtime:read-attribute(name=system-properties)");
            Assert.assertTrue(JPMSTestActivator.DEFAULT_SYS_PROP_NAME + " name has not been found after getting current System Properties. \n" + cli.getOutput(), cli.getOutput().contains(JPMSTestActivator.DEFAULT_SYS_PROP_NAME));
            Assert.assertTrue(JPMSTestActivator.DEFAULT_SYS_PROP_VALUE + " value has not been found after getting current System Properties. \n" + cli.getOutput(), cli.getOutput().contains(JPMSTestActivator.DEFAULT_SYS_PROP_VALUE));
            cli.clearOutput();
        } finally {
            // undeploy
            cli.pushLineAndWaitForResults("undeploy " + cliTestAppJar.getName());
            cli.pushLineAndWaitForResults("stop-embedded-server");
        }
    }

    public static File createTestArchive(String archiveName) {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, archiveName);
        jar.addClass(JPMSTestActivator.class);
        jar.addAsServiceProvider(ServiceActivator.class, JPMSTestActivator.class);
        jar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");
        jar.addAsManifestResource(
                PermissionUtils.createPermissionsXmlAsset(new PropertyPermission("test.deployment.trivial.prop", "write")),
                "permissions.xml");

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        File file = new File(tempDir, jar.getName());
        new ZipExporterImpl(jar).exportTo(file, true);
        return file;
    }
}
