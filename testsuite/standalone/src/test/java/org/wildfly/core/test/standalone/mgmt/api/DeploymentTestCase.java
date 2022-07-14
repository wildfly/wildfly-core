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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.DeploymentScannerSetupTask;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests deployment to a wildfly core server, both via the client API and by the filesystem scanner.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(WildFlyRunner.class)
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

        properties2.clear();
        properties2.put("service", "is replaced");
    }

    @Before
    public void before() {
        cleanUp("test-deployment.jar");
        cleanUp("test-auto-deployment.jar");
    }

    @After
    public void after() {
        cleanUp("test-deployment.jar");
        cleanUp("test-auto-deployment.jar");
    }

    @Test
    public void testDeploymentStreamApi() throws Exception {
        final String deploymentName = "test-deployment.jar";
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties);
        final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties2);
        archive2.addAsManifestResource(DeploymentTestCase.class.getPackage(), "marker.txt", "marker.txt");

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        testDeployments(false, new DeploymentExecutor() {

            @Override
            public void initialDeploy() {
                final InputStream is = archive.as(ZipExporter.class).exportAsInputStream();
                try {
                    Future<?> future = manager.execute(manager.newDeploymentPlan().add(deploymentName, is).deploy(deploymentName).build());
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
                    Future<?> future = manager.execute(manager.newDeploymentPlan().replace(deploymentName, is).build());
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
            public void readContent(String path, String expectedValue) throws IOException {
                readContentManaged(deploymentName, path, expectedValue, client);
            }

            @Override
            public void browseContent(String path, List<String> expectedContents, int depth, boolean archive) throws IOException {
                browseContentManaged(deploymentName, path, expectedContents, depth, archive, client);
            }

            @Override
            public void undeploy() {
                Future<?> future = manager.execute(manager.newDeploymentPlan().undeploy(deploymentName)
                        .remove(deploymentName).build());
                awaitDeploymentExecution(future);
            }
        });
    }

    @Test
    public void testDeploymentFileApi() throws Exception {
        final String deploymentName = "test-deployment.jar";
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties);
        final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties2);
        archive2.addAsManifestResource(DeploymentTestCase.class.getPackage(), "marker.txt", "marker.txt");
        final File dir = new File("target/archives");

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        try {
            testDeployments(false, new DeploymentExecutor() {

                @Override
                public void initialDeploy() throws IOException {
                    Future<?> future = manager.execute(manager.newDeploymentPlan().add(deploymentName, exportArchive(archive))
                            .deploy(deploymentName).build());
                    awaitDeploymentExecution(future);
                }

                @Override
                public void fullReplace() throws IOException {
                    Future<?> future = manager.execute(manager.newDeploymentPlan().replace(deploymentName, exportArchive(archive2)).build());
                    awaitDeploymentExecution(future);
                }

                @Override
                public void readContent(String path, String expectedValue) throws IOException {
                    readContentManaged(deploymentName, path, expectedValue, client);
                }

                @Override
                public void browseContent(String path, List<String> expectedContents, int depth, boolean archive) throws IOException {
                    browseContentManaged(deploymentName, path, expectedContents, depth, archive, client);
                }

                @Override
                public void undeploy() {
                    Future<?> future = manager.execute(manager.newDeploymentPlan().undeploy(deploymentName).remove(deploymentName).build());
                    awaitDeploymentExecution(future);
                }

                private File exportArchive(JavaArchive archive) {
                    dir.mkdirs();
                    final File file = new File(dir, deploymentName);
                    if (file.exists()) {
                        file.delete();
                    }
                    archive.as(ZipExporter.class).exportTo(file, true);
                    return file;
                }
            });
        } finally {
            cleanFile(dir);
        }
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
    public void testUniqueRuntimeName() throws Exception {
        final JavaArchive archive1 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment1.jar", properties);
        final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment2.jar", properties2);
        archive2.addAsManifestResource(DeploymentTestCase.class.getPackage(), "marker.txt", "marker.txt");

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        try (InputStream is1 = archive1.as(ZipExporter.class).exportAsInputStream();
                InputStream is2 = archive2.as(ZipExporter.class).exportAsInputStream();) {
            Future<?> future = manager.execute(manager.newDeploymentPlan()
                    .add("test-deployment1.jar", "test-deployment.jar", is1)
                    .add("test-deployment2.jar", "test-deployment.jar", is2)
                    .build());
            awaitDeploymentExecution(future);
            checkDeploymentStatus(client, "test-deployment1.jar", "STOPPED");
            checkDeploymentStatus(client, "test-deployment2.jar", "STOPPED");
            future = manager.execute(manager.newDeploymentPlan().deploy("test-deployment1.jar").build());
            awaitDeploymentExecution(future);
            future = manager.execute(manager.newDeploymentPlan().deploy("test-deployment2.jar").build());
            awaitDeploymentExecution(future);
            checkDeploymentStatus(client, "test-deployment1.jar", "OK");
            checkDeploymentStatus(client, "test-deployment2.jar", "STOPPED");
            future = manager.execute(manager.newDeploymentPlan()
                    .undeploy("test-deployment1.jar")
                    .remove("test-deployment1.jar")
                    .undeploy("test-deployment2.jar")
                    .remove("test-deployment2.jar")
                    .build());
            awaitDeploymentExecution(future);
        }
    }

    @Test
    public void testReplaceWithRuntimeName() throws Exception {
        final JavaArchive archive1 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment1.jar", properties);
        final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment2.jar", properties2);
        archive2.addAsManifestResource(DeploymentTestCase.class.getPackage(), "marker.txt", "marker.txt");

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        try (InputStream is1 = archive1.as(ZipExporter.class).exportAsInputStream();
                InputStream is2 = archive2.as(ZipExporter.class).exportAsInputStream();) {
            Future<?> future = manager.execute(manager.newDeploymentPlan()
                    .add("test-deployment1.jar", "test-deployment1.jar", is1)
                    .add("test-deployment2.jar", "test-deployment2.jar", is2)
                    .build());
            awaitDeploymentExecution(future);
            checkDeploymentStatus(client, "test-deployment1.jar", "STOPPED");
            checkDeploymentStatus(client, "test-deployment2.jar", "STOPPED");
            future = manager.execute(manager.newDeploymentPlan().deploy("test-deployment1.jar").build());
            awaitDeploymentExecution(future);
            checkDeploymentStatus(client, "test-deployment1.jar", "OK");
            checkDeploymentStatus(client, "test-deployment2.jar", "STOPPED");
            ModelNode response = client.execute(Util.getReadAttributeOperation(pathAddress(pathElement("deployment", "test-deployment1.jar")), "runtime-name"));
            Assert.assertEquals("success", response.get("outcome").asString());
            Assert.assertEquals("test-deployment1.jar", response.get("result").asString());
            ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties);
            ModelNode op = Operations.createOperation("replace-deployment");
            op.get("name").set("test-deployment2.jar");
            op.get("to-replace").set("test-deployment1.jar");
            op.get("runtime-name").set("test-deployment1.jar");
            future = client.executeAsync(op);
            awaitDeploymentExecution(future);
            checkDeploymentStatus(client, "test-deployment1.jar", "STOPPED");
            checkDeploymentStatus(client, "test-deployment2.jar", "OK");
            response = client.execute(Util.getReadAttributeOperation(pathAddress(pathElement("deployment", "test-deployment2.jar")), "runtime-name"));
            Assert.assertEquals("success", response.get("outcome").asString());
            Assert.assertEquals("test-deployment1.jar", response.get("result").asString());
            ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties2);
            future = manager.execute(manager.newDeploymentPlan()
                    .undeploy("test-deployment1.jar")
                    .remove("test-deployment1.jar")
                    .undeploy("test-deployment2.jar")
                    .remove("test-deployment2.jar")
                    .build());
            awaitDeploymentExecution(future);
        }
    }

    private void checkDeploymentStatus(ModelControllerClient client, String deploymentName, String status) throws IOException {
        ModelNode response = client.execute(Util.getReadAttributeOperation(pathAddress(pathElement("deployment", deploymentName)), "status"));
        Assert.assertEquals("success", response.get("outcome").asString());
        Assert.assertEquals(status, response.get("result").asString());
    }

    @Test
    public void testFilesystemDeployment_Marker() throws Exception {
        final String deploymentName = "test-deployment.jar";
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties);
        final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties2);
        final File dir = new File("target/archives");
        dir.mkdirs();
        final File file = new File(dir, deploymentName);
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
                    Files.copy(file.toPath(), target.toPath());
                    // Create the .dodeploy file
                    final File dodeploy = new File(deployDir, deploymentName + ".dodeploy");
                    final File isdeploying = new File(deployDir, deploymentName + ".isdeploying");
                    try (final OutputStream out = new BufferedOutputStream(new FileOutputStream(dodeploy))){
                        out.write(deploymentName.getBytes());
                    }
                    Assert.assertTrue(dodeploy.exists());
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
                        if (!dodeploy.exists() && !isdeploying.exists() && deployed.exists()) {
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
                    // Copy same deployment with changed property to deploy directory
                    File target = new File(deployDir, deploymentName);
                    final File dodeploy = new File(deployDir, deploymentName + ".dodeploy");
                    final File isdeploying = new File(deployDir, deploymentName + ".jar.isdeploying");
                    archive2.as(ZipExporter.class).exportTo(target, true);
                    dodeploy.createNewFile();
                    Assert.assertTrue(dodeploy.exists());
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
                        if (!dodeploy.exists() && !isdeploying.exists() && deployed.exists()) {
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }

                    Assert.assertFalse("fullReplace step did not complete in a reasonably timely fashion", dodeploy.exists());
                    Assert.assertTrue(deployed.exists());
                }

                @Override
                public void readContent(String path, String expectedValue) throws IOException {
                    readContentManaged(deploymentName, path, expectedValue, client);
                }

                @Override
                public void browseContent(String path, List<String> expectedContents, int depth, boolean archive) throws IOException {
                    browseContentManaged(deploymentName, path, expectedContents, depth, archive, client);
                }

                @Override
                public void undeploy() {
                    final File dodeploy = new File(deployDir, deploymentName + ".dodeploy");
                    final File isdeploying = new File(deployDir, deploymentName +".isdeploying");
                    final File undeployed = new File(deployDir, deploymentName+ ".undeployed");
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
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
                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
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
            cleanFile(dir);
        }
    }

    @Test
    public void testFilesystemDeployment_Auto() throws Exception {
        final String deploymentName = "test-auto-deployment.jar";
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties);
        final File dir = new File("target/archives");
        dir.mkdirs();
        final File file = new File(dir, deploymentName);
        archive.as(ZipExporter.class).exportTo(file, true);

        final File deployDir = createDeploymentDir("auto-deployments");

        ModelControllerClient client = managementClient.getControllerClient();
        final String scannerName = "autoZips";
        addDeploymentScanner(deployDir, client, scannerName, true);
        try {
            final File target = new File(deployDir, deploymentName);
            final File deployed = new File(deployDir, deploymentName + ".deployed");
            final File isdeploying = new File(deployDir, deploymentName + ".isdeploying");
            Assert.assertFalse(target.exists());

            testDeployments(true, new DeploymentExecutor() {
                @Override
                public void initialDeploy() throws IOException {
                    // Copy file to deploy directory
                    Files.copy(file.toPath(), target.toPath());
                    Assert.assertTrue(file.exists());

                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
                        if (!isdeploying.exists() && deployed.exists()) {
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
                    // Copy same deployment with changed property to deploy directory
                    FileTime deployedlastModified = Files.getLastModifiedTime(deployed.toPath());
                    Assert.assertTrue(deployed.exists());
                    final JavaArchive archive2 = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties2);
                    Path target = new File(deployDir, deploymentName).toPath();
                    archive2.as(ZipExporter.class).exportTo(target.toFile(), true);
                    final Instant deploymentLastModified = Files.getLastModifiedTime(target).toInstant();
                    //Some filesystems truncate the lastModified time to seconds instead of millisecond so we touch the file sure the file update is detectable.
                    Instant touch = Instant.now().plus(1, ChronoUnit.SECONDS).with(ChronoField.NANO_OF_SECOND, 0L);
                    Files.setLastModifiedTime(target, FileTime.from(touch));
                    Assert.assertTrue("Deployment file has not been updated " + deploymentLastModified + " should be before "
                            +  Files.getLastModifiedTime(target).toInstant(), Files.getLastModifiedTime(target).toInstant().isAfter(deploymentLastModified));
                    // Wait until filesystem action gets picked up by scanner
                    boolean wasUndeployed = false;
                    long timeout = System.currentTimeMillis() + TIMEOUT + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
                        if ((isdeploying.exists() && !deployed.exists()) || Files.getLastModifiedTime(deployed.toPath()).toMillis() > deployedlastModified.toMillis()) {
                            wasUndeployed = true;
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }
                    Assert.assertTrue("fullReplace step did not complete in a reasonably timely fashion " + Files.getLastModifiedTime(deployed.toPath()), wasUndeployed);
                    // Wait for redeploy to finish
                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
                        if (!isdeploying.exists() && deployed.exists()) {
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }
                    Assert.assertFalse("fullReplace step did not complete in a reasonably timely fashion", isdeploying.exists());
                    Assert.assertTrue(deployed.exists());
                }

                @Override
                public void readContent(String path, String expectedValue) throws IOException {
                    readContentManaged(deploymentName, path, expectedValue, client);
                }

                @Override
                public void browseContent(String path, List<String> expectedContents, int depth, boolean archive) throws IOException {
                    browseContentManaged(deploymentName, path, expectedContents, depth, archive, client);
                }

                @Override
                public void undeploy() {
                    final File isdeploying = new File(deployDir, deploymentName + ".isdeploying");
                    final File undeployed = new File(deployDir, deploymentName + ".undeployed");
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
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
                        Assert.fail("current step did not complete in a reasonably timely fashion");
                    }

                    // Delete file from deploy directory
                    target.delete();
                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
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
            cleanFile(dir);
        }
    }

    @Test
    public void testAddingDeploymentScannerWillLaunchScan() throws Exception {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        final Path dir = new File("target/archives").toPath();
        Files.createDirectories(dir);
        final Path file = dir.resolve("test-deployment.jar");
        archive.as(ZipExporter.class).exportTo(file.toFile(), true);

        final Path deployDir = createDeploymentDir("first-scan").toPath();

        ModelControllerClient client = managementClient.getControllerClient();
        final String scannerName = "first-scan";
        final Path target = deployDir.resolve("test-deployment.jar");
        final Path deployed = deployDir.resolve("test-deployment.jar.deployed");
        Files.copy(file, target);
        Assert.assertFalse(Files.exists(deployed));
        Assert.assertTrue(Files.exists(target));
        addDeploymentScanner(deployDir.toFile(), client, scannerName, true, -1);
        try {
            long timeout = System.currentTimeMillis() + TIMEOUT;
            while(System.currentTimeMillis() <= timeout) {
                if (Files.exists(deployed)) {
                    break;
                }
                try {
                    Thread.sleep(BACKOFF);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            Assert.assertTrue(Files.exists(deployed));
            ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties);
        } finally {
            removeDeploymentScanner(client, scannerName);
            Files.delete(target);
            Files.delete(deployed);
            Files.delete(deployDir);
            cleanFile(dir.toFile());
        }
    }

    @Test
    public void testExplodedFilesystemDeployment() throws Exception {
        final File deployDir = createDeploymentDir("exploded-deployments");
        final File dir = new File("target/archives");
        ModelControllerClient client = managementClient.getControllerClient();
        final String scannerName = "exploded";
        addDeploymentScanner(deployDir, client, scannerName, false);
        try {
            final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
            dir.mkdirs();
            archive.as(ExplodedExporter.class).exportExploded(deployDir);

            final File deployed = new File(deployDir, "test-deployment.jar.deployed");
            Assert.assertFalse(deployed.exists());

            testDeployments(true, new DeploymentExecutor() {
                @Override
                public void initialDeploy() throws IOException {

                    // Create the .dodeploy file
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    Files.write(dodeploy.toPath(), "test-deployment.jar".getBytes(StandardCharsets.UTF_8));
                    Assert.assertTrue(dodeploy.exists());
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
                        if (!dodeploy.exists() && !isdeploying.exists() && deployed.exists()) {
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
                    // full replace with single property changed
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    File testDeployment = new File(deployDir, "test-deployment.jar");
                    File propertiesFile = new File(testDeployment, "service-activator-deployment.properties");
                    try (FileOutputStream os = new FileOutputStream(propertiesFile.getAbsolutePath())) {
                        Properties replacementProps = new Properties();
                        replacementProps.putAll(properties2);
                        replacementProps.store(os, null);
                    }
                    dodeploy.createNewFile();
                    Assert.assertTrue(dodeploy.exists());
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
                        if (!dodeploy.exists() && !isdeploying.exists() && deployed.exists()) {
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }

                    Assert.assertFalse("fullReplace step did not complete in a reasonably timely fashion", dodeploy.exists());
                    Assert.assertTrue(deployed.exists());
                }

                @Override
                public void readContent(String path, String expectedValue) throws IOException {
                    ModelNode op = new ModelNode();
                    op.get(ClientConstants.OP).set(ClientConstants.READ_CONTENT_OPERATION);
                    op.get(ClientConstants.OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
                    op.get(ClientConstants.PATH).set(path);
                    Future<ModelNode> future = client.executeAsync(OperationBuilder.create(op, false).build(), null);
                    try {
                        ModelNode response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
                        Assert.assertFalse("Operation browse-content should not be successful with unmanaged deployments.",
                                Operations.isSuccessfulOutcome(response));
                        String failureDescription = Operations.getFailureDescription(response).toString();
                        Assert.assertTrue("Operation browse-content should fail with WFLYSRV0255, but failed with " + failureDescription,
                                failureDescription.contains("WFLYSRV0255"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e.getCause());
                    } catch (TimeoutException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (!future.isDone()) {
                            future.cancel(true);
                        }
                    }
                }

                @Override
                public void browseContent(String path, List<String> expectedContents, int depth, boolean archive) throws IOException {
                    ModelNode op = new ModelNode();
                    op.get(ClientConstants.OP).set(ClientConstants.DEPLOYMENT_BROWSE_CONTENT_OPERATION);
                    op.get(ClientConstants.OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
                    Future<ModelNode> future = client.executeAsync(OperationBuilder.create(op, false).build(), null);
                    try {
                        ModelNode response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
                        Assert.assertFalse("Operation browse-content should not be successful with unmanaged deployments.",
                                Operations.isSuccessfulOutcome(response));
                        String failureDescription = Operations.getFailureDescription(response).toString();
                        Assert.assertTrue("Operation browse-content should fail with WFLYSRV0255, but failed with " + failureDescription,
                                failureDescription.contains("WFLYSRV0255"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e.getCause());
                    } catch (TimeoutException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (!future.isDone()) {
                            future.cancel(true);
                        }
                    }
                }

                @Override
                public void undeploy() {
                    final File dodeploy = new File(deployDir, "test-deployment.jar.dodeploy");
                    final File isdeploying = new File(deployDir, "test-deployment.jar.isdeploying");
                    final File undeployed = new File(deployDir, "test-deployment.jar.undeployed");
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
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
                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while(System.currentTimeMillis() <= timeout) {
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
            cleanFile(dir);
        }
    }

    private ModelNode addDeploymentScanner(final File deployDir, final ModelControllerClient client, final String scannerName, final boolean autoDeployZipped)
            throws IOException {
        return addDeploymentScanner(deployDir, client, scannerName, autoDeployZipped, 1000);
    }

    private ModelNode addDeploymentScanner(final File deployDir, final ModelControllerClient client, final String scannerName, final boolean autoDeployZipped, int scanInterval)
            throws IOException {
        ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        ModelNode addr = new ModelNode();
        addr.add("subsystem", "deployment-scanner");
        addr.add("scanner", scannerName);
        add.get(OP_ADDR).set(addr);
        add.get("path").set(deployDir.getAbsolutePath());
        add.get("scan-enabled").set(true);
        add.get("scan-interval").set(scanInterval);
        if (!autoDeployZipped) {
            add.get("auto-deploy-zipped").set(autoDeployZipped);
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

        deploymentExecutor.readContent("service-activator-deployment.properties", "is new");
        deploymentExecutor.browseContent("", new ArrayList<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF",
                "META-INF/permissions.xml", "META-INF/services/", "META-INF/services/org.jboss.msc.service.ServiceActivator",
                "org/","org/jboss/","org/jboss/as/", "org/jboss/as/test/", "org/jboss/as/test/deployment/",
                "org/jboss/as/test/deployment/trivial/", "service-activator-deployment.properties",
                "org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class")), -1, false);
        deploymentExecutor.browseContent("", new ArrayList<>(Arrays.asList("META-INF/", "org/",
                "service-activator-deployment.properties")), 1, false);
        deploymentExecutor.browseContent("", new ArrayList<>(), -1, true);

        // Full replace
        // listener.reset(2);
        deploymentExecutor.fullReplace();

        // listener.await();
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties2);
        deploymentExecutor.readContent("service-activator-deployment.properties", "is replaced");

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
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
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

    private void readContentManaged(String deploymentName, String path, String expectedValue, ModelControllerClient client) throws IOException {
        ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set(ClientConstants.READ_CONTENT_OPERATION);
        op.get(ClientConstants.OP_ADDR).add(DEPLOYMENT, deploymentName);
        if (!path.isEmpty()) {
            op.get(PATH).set(path);
        }
        Future<OperationResponse> future = client.executeOperationAsync(OperationBuilder.create(op, false).build(), null);

        try {
            OperationResponse response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            if (path.isEmpty()) {
                Assert.assertFalse("Operation read-content should not be successful without defined path parameter on exploded deployments",
                        Operations.isSuccessfulOutcome(response.getResponseNode()));
                String failureDescription = Operations.getFailureDescription(response.getResponseNode()).toString();
                Assert.assertTrue("Operation read-content should fail with WFLYDR0020, but failed with " + failureDescription,
                        failureDescription.contains("WFLYDR0020"));
            } else {
                Assert.assertTrue(Operations.isSuccessfulOutcome(response.getResponseNode()));
                Assert.assertTrue(Operations.readResult(response.getResponseNode()).hasDefined(UUID));
                List<OperationResponse.StreamEntry> streams = response.getInputStreams();
                MatcherAssert.assertThat(streams, is(notNullValue()));
                MatcherAssert.assertThat(streams.size(), is(1));
                try (InputStream in = streams.get(0).getStream()) {
                    Properties content = new Properties();
                    content.load(in);
                    MatcherAssert.assertThat(content.getProperty("service"), is(expectedValue));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private void browseContentManaged(String deploymentName, String path, List<String> expectedContents, int depth, boolean archive, ModelControllerClient client) throws IOException {
        ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set(ClientConstants.DEPLOYMENT_BROWSE_CONTENT_OPERATION);
        op.get(ClientConstants.OP_ADDR).add(DEPLOYMENT, deploymentName);
        if (path != null && !path.isEmpty()) {
            op.get(PATH).set(path);
        }
        if (depth > 0) {
            op.get(DEPTH).set(depth);
        }
        if (archive) {
            op.get(ARCHIVE).set(archive);
        }
        Future<ModelNode> future = client.executeAsync(OperationBuilder.create(op, false).build(), null);
        try {
            ModelNode response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            if (!Operations.isSuccessfulOutcome(response)) {
                Assert.fail("Operation browse content should be successful, but failed: " +
                        Operations.getFailureDescription(response).toString());
            }
            List<String> unexpectedContents = new ArrayList<>();
            if (expectedContents.isEmpty()) {
                Assert.assertEquals("Unexpected non-empty result with browse-content operation",
                        new ModelNode(), Operations.readResult(response));
            } else {
                List<ModelNode> contents = Operations.readResult(response).asList();
                for (ModelNode content : contents) {
                    Assert.assertTrue(content.hasDefined("path"));
                    String contentPath = content.get("path").asString();
                    Assert.assertTrue(content.hasDefined("directory"));
                    if (!content.get("directory").asBoolean()) {
                        Assert.assertTrue(content.hasDefined("file-size"));
                    }
                    if (!expectedContents.contains(contentPath)) {
                        unexpectedContents.add(contentPath);
                    }
                    expectedContents.remove(contentPath);
                }
            }
            Assert.assertTrue("Unexpected files listed by /deployment=" + deploymentName + ":browse-content(depth="
                    + depth + ", archive=" + archive + ") : " + unexpectedContents.toString(),
                    unexpectedContents.isEmpty());
            Assert.assertTrue("Expected files not listed by /deployment=" + deploymentName + ":browse-content(depth="
                    + depth + ", archive=" + archive + ") : " + expectedContents.toString(),
                    expectedContents.isEmpty());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private void cleanUp(String deployment) {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_RESOURCE_OPERATION);
        op.get(OP_ADDR).set(ModelDescriptionConstants.DEPLOYMENT, deployment);
        ModelControllerClient client = managementClient.getControllerClient();
        ModelNode result;
        try {
            result = client.execute(op);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (result != null && Operations.isSuccessfulOutcome(result)) {
            ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
            Future<?> future = manager.execute(manager.newDeploymentPlan()
                    .undeploy(deployment)
                    .remove(deployment)
                    .build());

            awaitDeploymentExecution(future);
        }

        String jbossBaseDir = System.getProperty("jboss.home");
        Assert.assertNotNull(jbossBaseDir);
        Path dataDir = new File(jbossBaseDir).toPath().resolve("standalone").resolve("data");
        if (Files.exists(dataDir)) { cleanFile(dataDir.resolve("managed-exploded").toFile()); }
        File archivesDir = new File("target", "archives");
        if (Files.exists(archivesDir.toPath())) { cleanFile(archivesDir); }
    }

    private interface DeploymentExecutor {

        void initialDeploy() throws IOException;

        void fullReplace() throws IOException;

        void readContent(String path, String expectedValue) throws IOException;

        void browseContent(String path, List<String> expectedContents, int depth, boolean archive) throws IOException;

        void undeploy() throws IOException;
    }

}
