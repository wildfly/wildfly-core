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
package org.wildfly.core.test.standalone.mgmt.api;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.deployment.DeploymentScannerSetupTask;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Tests deployment to a wildfly core server, both via the client API and by the filesystem scanner.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({DeploymentScannerSetupTask.class})
public class DeploymentTestCase {

    // Max time to wait for some action to complete, in ms
    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    // Pause time between checks whether some action has completed, in ms
    private static final int BACKOFF = 10;

    @Inject
    private ManagementClient managementClient;

    private static Map<String, String> properties = new HashMap<>();
    private static Map<String, String> properties2 = new HashMap<>();

    @BeforeClass
    public static void addDeploymentScanner() throws Exception {
        properties.clear();
        properties.put("service", "is new");

        properties.clear();
        properties.put("service", "is replaced");
    }

    @Test
    public void testDeploymentStreamApi() throws Exception {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties2);
        archive2.addAsManifestResource(DeploymentTestCase.class.getPackage(), "marker.txt", "marker.txt");

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        testDeployments(false, new DeploymentExecutor() {

            @Override
            public void initialDeploy() {
                final InputStream is = archive.as(ZipExporter.class).exportAsInputStream();
                try {
                    Future<?> future = manager.execute(manager.newDeploymentPlan().add("test-deployment.jar", is).deploy("test-deployment.jar").build());
                    awaitDeploymentExecution(future);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignore) {
                            //
                        }
                    }
                }
            }

            @Override
            public void fullReplace() {
                final InputStream is = archive2.as(ZipExporter.class).exportAsInputStream();
                try {
                    Future<?> future = manager.execute(manager.newDeploymentPlan().replace("test-deployment.jar", is).build());
                    awaitDeploymentExecution(future);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignore) {
                            //
                        }
                    }
                }
            }

            @Override
            public void undeploy() {
                Future<?> future = manager.execute(manager.newDeploymentPlan().undeploy("test-deployment.jar")
                        .remove("test-deployment.jar").build());
                awaitDeploymentExecution(future);
            }
        });
    }

    @Test
    public void testDeploymentFileApi() throws Exception {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties2);
        archive2.addAsManifestResource(DeploymentTestCase.class.getPackage(), "marker.txt", "marker.txt");

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);

        testDeployments(false, new DeploymentExecutor() {

            @Override
            public void initialDeploy() throws IOException {
                Future<?> future = manager.execute(manager.newDeploymentPlan().add("test-deployment.jar", exportArchive(archive))
                        .deploy("test-deployment.jar").build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void fullReplace() throws IOException {
                Future<?> future = manager.execute(manager.newDeploymentPlan().replace("test-deployment.jar", exportArchive(archive2)).build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void undeploy() {
                Future<?> future = manager.execute(manager.newDeploymentPlan().undeploy("test-deployment.jar").remove("test-deployment.jar").build());
                awaitDeploymentExecution(future);
            }

            private File exportArchive(JavaArchive archive) {
                final File dir = new File("target/archives");
                dir.mkdirs();
                final File file = new File(dir, "test-deployment.jar");
                if (file.exists()) {
                    file.delete();
                }
                archive.as(ZipExporter.class).exportTo(file, true);
                return file;
            }
        });

    }

    @Test
    public void testFilesystemScannerRegistration() throws Exception {
        final File deployDir = createDeploymentDir("dummy");
        final ModelControllerClient client = managementClient.getControllerClient();
        final String scannerName = "dummy";
        addDeploymentScanner(deployDir, client, scannerName, false);
        removeDeploymentScanner(client, scannerName);
        addDeploymentScanner(deployDir, client, scannerName, false);
        removeDeploymentScanner(client, scannerName);
    }

    @Test
    public void testFilesystemDeployment_Marker() throws Exception {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        final File dir = new File("target/archives");
        dir.mkdirs();
        final File file = new File(dir, "test-deployment.jar");
        archive.as(ZipExporter.class).exportTo(file, true);

        final File deployDir = createDeploymentDir("marker-deployments");

        ModelControllerClient client = managementClient.getControllerClient();
        final String scannerName = "markerZips";
        addDeploymentScanner(deployDir, client, scannerName, false);
        try {
            final File target = new File(deployDir, "test-deployment.jar");
            final File deployed = new File(deployDir, "test-deployment.jar.deployed");
            Assert.assertFalse(target.exists());

            testDeployments(true, new DeploymentExecutor() {
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
                    // Create the .dodeploy file
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final OutputStream out = new BufferedOutputStream(new FileOutputStream(dodeploy));
                    try {
                        out.write("test-deployment.jar".getBytes());
                    } finally {
                        StreamUtils.safeClose(out);
                    }
                    Assert.assertTrue(dodeploy.exists());
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (deployed.exists()) {
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }

                    Assert.assertTrue(deployed.exists());
                }

                @Override
                public void fullReplace() throws IOException {
                    // The test is going to call this as soon as the deployment
                    // sends a notification
                    // but often before the scanner has completed the process
                    // and deleted the
                    // .dodpeloy put down by initialDeploy(). So pause a bit to
                    // let that complete
                    // so we don't end up having our own file deleted
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (!dodeploy.exists() && !isdeploying.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (dodeploy.exists()) {
                        Assert.fail("initialDeploy step did not complete in a reasonably timely fashion");
                    }

                    // Copy file to deploy directory again
                    initialDeploy();
                }

                @Override
                public void undeploy() {
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    final File undeployed = new File(deployDir, "test-deployment.jar.undeployed");
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (!dodeploy.exists() && !isdeploying.exists() && deployed.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    if (dodeploy.exists() || !deployed.exists()) {
                        Assert.fail("fullReplace step did not complete in a reasonably timely fashion");
                    }

                    // Delete file from deploy directory
                    deployed.delete();
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (undeployed.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (!undeployed.exists()) {
                        Assert.fail("undeploy step did not complete in a reasonably timely fashion");
                    }
                }
            });
        } finally {
            removeDeploymentScanner(client, scannerName);
        }
    }

    @Test
    public void testFilesystemDeployment_Auto() throws Exception {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        final File dir = new File("target/archives");
        dir.mkdirs();
        final File file = new File(dir, "test-deployment.jar");
        archive.as(ZipExporter.class).exportTo(file, true);

        final File deployDir = createDeploymentDir("auto-deployments");

        ModelControllerClient client = managementClient.getControllerClient();
        final String scannerName = "autoZips";
        addDeploymentScanner(deployDir, client, scannerName, true);
        try {
            final File target = new File(deployDir, "test-deployment.jar");
            final File deployed = new File(deployDir, "test-deployment.jar.deployed");
            Assert.assertFalse(target.exists());

            testDeployments(true, new DeploymentExecutor() {
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
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (deployed.exists()) {
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }

                    Assert.assertTrue(deployed.exists());
                }

                @Override
                public void fullReplace() throws IOException {
                    // The test is going to call this as soon as the deployment
                    // sends a notification
                    // but often before the scanner has completed the process
                    // and deleted the
                    // .isdeploying put down by deployment scanner. So pause a bit to
                    // let that complete
                    // so we don't end up having our own file deleted
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (!isdeploying.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (isdeploying.exists()) {
                        Assert.fail("initialDeploy step did not complete in a reasonably timely fashion");
                    }

                    // Copy file to deploy directory again
                    initialDeploy();
                }

                @Override
                public void undeploy() {
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    final File undeployed = new File(deployDir, "test-deployment.jar.undeployed");
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (!isdeploying.exists() && deployed.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    if (!deployed.exists()) {
                        Assert.fail("fullReplace step did not complete in a reasonably timely fashion");
                    }

                    // Delete file from deploy directory
                    target.delete();
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (undeployed.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (!undeployed.exists()) {
                        Assert.fail("undeploy step did not complete in a reasonably timely fashion");
                    }
                }
            });
        } finally {
            removeDeploymentScanner(client, scannerName);
        }
    }

    @Test
    public void testExplodedFilesystemDeployment() throws Exception {

        final File deployDir = createDeploymentDir("exploded-deployments");

        ModelControllerClient client = managementClient.getControllerClient();
        final String scannerName = "exploded";
        addDeploymentScanner(deployDir, client, scannerName, false);
        try {
            final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
            final File dir = new File("target/archives");
            dir.mkdirs();
            archive.as(ExplodedExporter.class).exportExploded(deployDir);

            final File deployed = new File(deployDir, "test-deployment.jar.deployed");
            Assert.assertFalse(deployed.exists());

            testDeployments(true, new DeploymentExecutor() {
                @Override
                public void initialDeploy() throws IOException {

                    // Create the .dodeploy file
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final OutputStream out = new BufferedOutputStream(new FileOutputStream(dodeploy));
                    try {
                        out.write("test-deployment.jar".getBytes());
                    } finally {
                        StreamUtils.safeClose(out);
                    }
                    Assert.assertTrue(dodeploy.exists());
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (deployed.exists()) {
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }

                    Assert.assertTrue(deployed.exists());
                }

                @Override
                public void fullReplace() throws IOException {
                    // The test is going to call this as soon as the deployment
                    // sends a notification
                    // but often before the scanner has completed the process
                    // and deleted the
                    // .dodpeloy put down by initialDeploy(). So pause a bit to
                    // let that complete
                    // so we don't end up having our own file deleted
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (!dodeploy.exists() && !isdeploying.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (dodeploy.exists()) {
                        Assert.fail("initialDeploy step did not complete in a reasonably timely fashion");
                    }

                    // Copy file to deploy directory again
                    initialDeploy();
                }

                @Override
                public void undeploy() {
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    final File undeployed = new File(deployDir, "test-deployment.jar.undeployed");
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (!dodeploy.exists() && !isdeploying.exists() && deployed.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    if (dodeploy.exists() || !deployed.exists()) {
                        Assert.fail("fullReplace step did not complete in a reasonably timely fashion");
                    }

                    // Delete file from deploy directory
                    deployed.delete();
                    for (int i = 0; i < TIMEOUT / BACKOFF; i++) {
                        if (undeployed.exists()) {
                            break;
                        }
                        // Wait for the last action to complete :(
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (!undeployed.exists()) {
                        Assert.fail("undeploy step did not complete in a reasonably timely fashion");
                    }
                }
            });
        } finally {
            removeDeploymentScanner(client, scannerName);
        }
    }

    private ModelNode addDeploymentScanner(final File deployDir, final ModelControllerClient client, final String scannerName, final boolean autoDeployZipped)
            throws IOException {
        ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        ModelNode addr = new ModelNode();
        addr.add("subsystem", "deployment-scanner");
        addr.add("scanner", scannerName);
        add.get(OP_ADDR).set(addr);
        add.get("path").set(deployDir.getAbsolutePath());
        add.get("scan-enabled").set(true);
        add.get("scan-interval").set(1000);
        if (autoDeployZipped == false) {
            add.get("auto-deploy-zipped").set(false);
        }
        ModelNode result = client.execute(add);
        Assert.assertEquals(result.toString(), ModelDescriptionConstants.SUCCESS, result.require(ModelDescriptionConstants.OUTCOME).asString());
        return result;
    }

    private void removeDeploymentScanner(final ModelControllerClient client, final String scannerName) throws IOException {
        ModelNode addr = new ModelNode();
        addr.add("subsystem", "deployment-scanner");
        addr.add("scanner", scannerName);
        ModelNode remove = new ModelNode();
        remove.get(OP).set(REMOVE);
        remove.get(OP_ADDR).set(addr);
        ModelNode result = client.execute(remove);
        Assert.assertEquals(result.toString(), ModelDescriptionConstants.SUCCESS, result.require(ModelDescriptionConstants.OUTCOME).asString());
    }

    private File createDeploymentDir(String dir) {
        final File deployDir = new File("target", dir);
        cleanFile(deployDir);
        deployDir.mkdirs();
        Assert.assertTrue(deployDir.exists());
        return deployDir;
    }

    private void testDeployments(boolean fromFile, DeploymentExecutor deploymentExecutor) throws Exception {
        // Initial deploy
        Set<String> initialHashes = null;
        if (!fromFile) {
            initialHashes = getAllDeploymentHashesFromContentDir(true);
        }
        deploymentExecutor.initialDeploy();

        //listener.await();
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties);

        Set<String> currentHashes = null;
        String initialDeploymentHash = null;
        if (!fromFile) {
            currentHashes = getAllDeploymentHashesFromContentDir(false);
            currentHashes.removeAll(initialHashes);
            Assert.assertEquals(1, currentHashes.size());
            initialDeploymentHash = currentHashes.iterator().next();
        }

            // Full replace
        // listener.reset(2);
        deploymentExecutor.fullReplace();

        // listener.await();
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties2);

        if (!fromFile) {
            currentHashes = getAllDeploymentHashesFromContentDir(false);
            Assert.assertFalse(currentHashes.contains(initialDeploymentHash)); //Should have been deleted when replaced
            currentHashes.removeAll(initialHashes);
            Assert.assertEquals(1, currentHashes.size());
        }

        // Undeploy
        // listener.reset(1);
        deploymentExecutor.undeploy();

        if (!fromFile) {
            Assert.assertEquals(initialHashes, getAllDeploymentHashesFromContentDir(false));
        }
    }

    private Set<String> getAllDeploymentHashesFromContentDir(boolean emptyOk) {
        String jbossBaseDir = System.getProperty("jboss.home");
        Assert.assertNotNull(jbossBaseDir);
        File file = new File(jbossBaseDir);
        Assert.assertTrue(file.exists());
        file = new File(file, "standalone");
        Assert.assertTrue(file.exists());
        file = new File(file, "data");
        if (!file.exists() && emptyOk) {
            return new HashSet<String>();
        }
        Assert.assertTrue(file.exists());
        file = new File(file, "content");
        Assert.assertTrue(file.exists());

        Set<String> hashes = new HashSet<String>();
        for (File top : file.listFiles()) {
            if (top.isDirectory() && top.getName().length() == 2) {
                for (File content : top.listFiles()) {
                    hashes.add(top.getName() + content.getName());
                }
            }
        }
        return hashes;
    }

    private void awaitDeploymentExecution(Future<?> future) {
        try {
            future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

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

        void fullReplace() throws IOException;

        void undeploy() throws IOException;
    }

}
