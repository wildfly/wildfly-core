/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.manualmode.management.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.jmx.ServiceActivatorDeployment;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class DeploymentOverlayTestCase {

    private interface TestOperations {

        void addOverlay(CLIWrapper wrapper, String depRuntimeName, File overlayContent) throws Exception;

        void removeOverlay(CLIWrapper wrapper, String depRuntimeName) throws Exception;
    }

    private static final Properties properties = new Properties();
    private static final Properties properties2 = new Properties();

    private static final String OVERLAY_NAME = "ov1";
    private static CLIWrapper cli;

    private static final String runtimeName = "runtime-test-deployment.jar";
    private static final String name = "test-deployment.jar";
    private static File overlayContent;
    private static File archiveFile;

    @BeforeClass
    public static void setup() throws Exception {
        properties.clear();
        properties.put("service", "is new");

        properties2.clear();
        properties2.put("service", "is overwritten");

        cli = new CLIWrapper(true);
        JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(name, properties);
        archiveFile = new File(TestSuiteEnvironment.getTmpDir(), name);
        archiveFile.createNewFile();
        archiveFile.deleteOnExit();
        archive.as(ZipExporter.class).exportTo(archiveFile, true);
        deployContent();
        // overlay content
        overlayContent = new File(TestSuiteEnvironment.getTmpDir(), "test-properties-content.properties");
        overlayContent.createNewFile();
        overlayContent.deleteOnExit();
        try (FileWriter writer = new FileWriter(overlayContent)) {
            properties2.store(writer, "Overlay Content");
        }
    }

    private static void deployContent() {
        cli.sendLine("deploy " + archiveFile.getAbsolutePath() + " --runtime-name=" + runtimeName, false);
    }

    private static void undeployContent() {
        cli.sendLine("undeploy " + name, false);
    }

    @AfterClass
    public static void cleanup() throws IOException {
        // In case test leftover.
        cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME, true);
        cli.sendLine("undeploy *", true);
        cli.quit();
    }

    /**
     * Add overlay, content, deployments and redeploy in 1 operation. Remove
     * overlay and redeploy in 1 operation.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove1() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CLIWrapper cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.sendLine("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath()
                        + " --deployments=" + depRuntimeName + " --redeploy-affected", false);
            }

            @Override
            public void removeOverlay(CLIWrapper cli, String depRuntimeName) throws Exception {
                cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME + " --redeploy-affected", false);
            }
        });
    }

    /**
     * Add overlay, content, then link and redeploy. Remove overlay and redeploy
     * in 1 operation.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove2() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CLIWrapper cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.sendLine("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath(), false);
                cli.sendLine("deployment-overlay link --name=" + OVERLAY_NAME + " --deployments="
                        + depRuntimeName + " --redeploy-affected", false);
            }

            @Override
            public void removeOverlay(CLIWrapper cli, String depRuntimeName) throws Exception {
                cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME + " --redeploy-affected", false);
            }
        });
    }

    /**
     * Add overlay and content, then link, then redeploy. Remove deployment and
     * redeploy then overlay.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove3() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CLIWrapper cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.sendLine("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath(), false);
                cli.sendLine("deployment-overlay link --name=" + OVERLAY_NAME + " --deployments=" + depRuntimeName, false);
                cli.sendLine("deployment-overlay redeploy-affected --name=" + OVERLAY_NAME, false);
            }

            @Override
            public void removeOverlay(CLIWrapper cli, String depRuntimeName) throws Exception {
                cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME + " --deployments="
                        + depRuntimeName + " --redeploy-affected", false);
                cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME, false);
            }
        });
    }

    /**
     * Add overlay and content, then link, then redeploy. Remove content then
     * redeploy then remove overlay.
     *
     * @throws Exception
     */
    @Test
    public void testAddRemove4() throws Exception {
        test(new TestOperations() {
            @Override
            public void addOverlay(CLIWrapper cli, String depRuntimeName, File overlayContent) throws Exception {
                cli.sendLine("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                        + overlayContent.getAbsolutePath(), false);
                cli.sendLine("deployment-overlay link --name=" + OVERLAY_NAME + " --deployments=" + depRuntimeName, false);
                cli.sendLine("deployment-overlay redeploy-affected --name=" + OVERLAY_NAME, false);
            }

            @Override
            public void removeOverlay(CLIWrapper cli, String depRuntimeName) throws Exception {
                cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME + " --content="
                        + ServiceActivatorDeployment.PROPERTIES_RESOURCE + " --redeploy-affected", false);
                cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME + " --deployments="
                        + depRuntimeName, false);
                cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME);

            }
        });
    }

    @Test
    public void testBatch() throws Exception {
        // No overlay
        cli.sendLine("deployment-overlay", false);
        Assert.assertTrue(cli.readOutput() == null || cli.readOutput().isEmpty());

        // no deployment in context.
        undeployContent();

        cli.sendLine("batch", false);

        try {
            // Deploy a new content in the same composite
            deployContent();
            // Add overlay
            cli.sendLine("deployment-overlay add --name=" + OVERLAY_NAME + " --content="
                    + ServiceActivatorDeployment.PROPERTIES_RESOURCE + "="
                    + overlayContent.getAbsolutePath()
                    + " --deployments=" + runtimeName + " --redeploy-affected", false);
            cli.sendLine("run-batch", false);

            // Check that overlay is properly applied
            cli.sendLine("deployment-overlay", false);
            Assert.assertFalse(cli.readOutput() == null || cli.readOutput().isEmpty());
            ServiceActivatorDeploymentUtil.validateProperties(cli.getCommandContext().getModelControllerClient(), properties2);
            cli.sendLine("deployment-overlay remove --name=" + OVERLAY_NAME + " --redeploy-affected");
            ServiceActivatorDeploymentUtil.validateProperties(cli.getCommandContext().getModelControllerClient(), properties);
        } finally {
            cli.sendLine("discard-batch", true);
        }
    }

    private void test(TestOperations ops) throws Exception {
        // No overlay
        cli.sendLine("deployment-overlay", false);
        Assert.assertTrue(cli.readOutput() == null || cli.readOutput().isEmpty());

        ServiceActivatorDeploymentUtil.validateProperties(cli.getCommandContext().getModelControllerClient(), properties);
        //add
        ops.addOverlay(cli, runtimeName, overlayContent);
        cli.sendLine("deployment-overlay", false);
        Assert.assertFalse(cli.readOutput().isEmpty());
        ServiceActivatorDeploymentUtil.validateProperties(cli.getCommandContext().getModelControllerClient(), properties2);
        //remove
        ops.removeOverlay(cli, runtimeName);
        ServiceActivatorDeploymentUtil.validateProperties(cli.getCommandContext().getModelControllerClient(), properties);

        // Check that overlay has been properly removed
        cli.sendLine("deployment-overlay", false);
        Assert.assertTrue(cli.readOutput() == null || cli.readOutput().isEmpty());

        // Then test in a batch.
        cli.sendLine("batch");
        try {
            ops.addOverlay(cli, runtimeName, overlayContent);
            ServiceActivatorDeploymentUtil.validateProperties(cli.getCommandContext().getModelControllerClient(), properties);
            ops.removeOverlay(cli, runtimeName);
            cli.sendLine("run-batch");
            cli.sendLine("deployment-overlay", false);
            Assert.assertTrue(cli.readOutput() == null || cli.readOutput().isEmpty());
            ServiceActivatorDeploymentUtil.validateProperties(cli.getCommandContext().getModelControllerClient(), properties);
        } finally {
            cli.sendLine("discard-batch", true);
        }
    }
}
