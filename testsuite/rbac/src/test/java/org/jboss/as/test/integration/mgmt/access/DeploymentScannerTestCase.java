/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;


import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.deployment.DeploymentScannerSetupTask;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test that the deployment scanner still works even with RBAC enabled.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({DeploymentScannerSetupTask.class})
public class DeploymentScannerTestCase {

    @Inject
    private static ManagementClient managementClient;

    private static File deployDir;
    private static Map<String, String> properties = new HashMap<>();

    @BeforeClass
    public static void addDeploymentScanner() throws Exception {
        deployDir = createDeploymentDir("marker-deployments");

        ModelControllerClient client = managementClient.getControllerClient();
        ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        ModelNode addr = new ModelNode();
        addr.add("subsystem", "deployment-scanner");
        addr.add("scanner", "rbac-check");
        add.get(OP_ADDR).set(addr);
        add.get("path").set(deployDir.getAbsolutePath());
        add.get("scan-enabled").set(true);
        add.get("scan-interval").set(1000);

        ModelNode result = client.execute(add);
        Assert.assertEquals(result.toString(), SUCCESS, result.require(OUTCOME).asString());
        properties.clear();
        properties.put("rbac", "is working");
    }

    @AfterClass
    public static void removeDeploymentScanner() throws Exception {
        try {
            ModelControllerClient client = managementClient.getControllerClient();
            ModelNode addr = new ModelNode();
            addr.add("subsystem", "deployment-scanner");
            addr.add("scanner", "rbac-check");
            ModelNode op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).set(addr);
            client.execute(op);
        } finally {
            if (deployDir != null) {
                cleanFile(deployDir);
                deployDir = null;
            }
        }

    }

    @Test
    public void testFilesystemDeployment_Auto() throws Exception {
        final File dir = new File("target/archives");
        if(dir.exists()) {
            cleanFile(dir);
        }
        dir.mkdirs();
        final File file = new File(dir, "test-scanner-deployment.jar");
        ServiceActivatorDeploymentUtil.createServiceActivatorDeployment(file, properties);
        final File target = new File(deployDir, "test-scanner-deployment.jar");
        final File deployed = new File(deployDir, "test-scanner-deployment.jar.deployed");
        Assert.assertFalse(target.exists());

        testDeployments(new DeploymentExecutor() {
            @Override
            public void initialDeploy() throws IOException {
                // Copy file to deploy directory
                final InputStream in = new BufferedInputStream(new FileInputStream(file));
                try {
                    final OutputStream out = new BufferedOutputStream(new FileOutputStream(target));
                    try {
                        int i = in.read();
                        while (i != -1) {
                            out.write(i);
                            i = in.read();
                        }
                    } finally {
                        StreamUtils.safeClose(out);
                    }
                } finally {
                    StreamUtils.safeClose(in);
                }

                Assert.assertTrue(file.exists());
                long start = System.currentTimeMillis();
                while (!deployed.exists() && System.currentTimeMillis() - start < 10000) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!deployed.exists()) {
                    Assert.fail("deploy step did not complete in a reasonably timely fashion");
                }
            }

            @Override
            public void undeploy() {
                final File isdeploying = new File(deployDir, "test-scanner-deployment.jar.isdeploying");
                for (int i = 0; i < 500; i++) {
                    if (!isdeploying.exists() && deployed.exists()) {
                        break;
                    }
                    // Wait for the last action to complete :(
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (!deployed.exists()) {
                    Assert.fail("undeploy step did not complete in a reasonably timely fashion");
                }

                // Delete file from deploy directory
                target.delete();
            }
        });
    }

    private static File createDeploymentDir(String dir) {
        final File deployDir = new File("target", dir);
        cleanFile(deployDir);
        deployDir.mkdirs();
        Assert.assertTrue(deployDir.exists());
        return deployDir;
    }

    private void testDeployments(DeploymentExecutor deploymentExecutor) throws Exception {
        // Initial deploy
        deploymentExecutor.initialDeploy();
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties);
        // Undeploy
        deploymentExecutor.undeploy();
    }

    private static void cleanFile(File toClean) {
        if (toClean.isDirectory()) {
            for (File child : toClean.listFiles()) {
                cleanFile(child);
            }
        }
        toClean.delete();
    }

    private interface DeploymentExecutor {

        void initialDeploy() throws IOException;

        void undeploy() throws IOException;
    }

}
