/*
 * Copyright (C) 2016 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.wildfly.core.test.standalone.mgmt.api.core;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXPLODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OVERWRITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATHS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TARGET_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Tests to check hard requirements and extreme cases for deployment operations.
 * @author <a href="mailto:mjurc@redhat.com">Michal Jurc</a> (c) 2016 Red Hat, Inc.
 */
@RunWith(WildflyTestRunner.class)
public class DeploymentOperationsTestCase {

    @Inject
    private ManagementClient managementClient;

    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    private static final String TEST_DEPLOYMENT_NAME = "test-deployment.jar";

    @Before
    public void cleanEnv() {
        cleanUp();
    }

    @After
    public void cleanDeployments() {
        cleanUp();
    }

    @Test
    public void testManagedAttributeValue() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertTrue(getManagedAttribute(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, false);
        Assert.assertTrue(getManagedAttribute(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(getManagedAttribute(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, false);
        Assert.assertFalse(getManagedAttribute(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testManagedAttributeReadOnly() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(writeManagedAttributeAndGetOutcome(TEST_DEPLOYMENT_NAME, false));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, false);
        Assert.assertFalse(writeManagedAttributeAndGetOutcome(TEST_DEPLOYMENT_NAME, false));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(writeManagedAttributeAndGetOutcome(TEST_DEPLOYMENT_NAME, true));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, false);
        Assert.assertFalse(writeManagedAttributeAndGetOutcome(TEST_DEPLOYMENT_NAME, true));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testManagedExplodedOperationsFailWithUnmanagedArchiveDeployment() throws Exception {
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(explodeDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        Assert.assertTrue(addContentByByteArrayAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertTrue(addContentByInputStreamAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertTrue(addContentByUrlAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertFalse(removeContentFromDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        Assert.assertFalse(readContentFromDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testManagedExplodedOperationsFailWithUnmanagedExplodedDeployment() throws Exception {
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, false);
        Assert.assertFalse(explodeDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        Assert.assertTrue(addContentByByteArrayAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertTrue(addContentByInputStreamAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertTrue(addContentByUrlAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertFalse(removeContentFromDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        Assert.assertFalse(readContentFromDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testManagedExplodedOperationsFailWithManagedArchiveDeployment() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(explodeDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        Assert.assertTrue(addContentByByteArrayAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertTrue(addContentByInputStreamAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertTrue(addContentByUrlAndGetOutcome(TEST_DEPLOYMENT_NAME, true).get(ROLLED_BACK).asBoolean());
        Assert.assertFalse(removeContentFromDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        Assert.assertFalse(readContentFromDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testExplodeFailsWithDeployedManagedArchiveDeployment() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(explodeDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testAddContentFailsWithoutOverwriteManagedExplodedDeployment() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, false);

        Assert.assertFalse("Operation add-content over existing content in deployment with byte array content and overwrite=false succeeded, should fail",
                Operations.isSuccessfulOutcome(addContentByByteArrayAndGetOutcome(TEST_DEPLOYMENT_NAME, false)));
        Assert.assertFalse("Operation add-content over existing content in deployment with input stream index content and overwrite=false succeeded, should fail",
                Operations.isSuccessfulOutcome(addContentByInputStreamAndGetOutcome(TEST_DEPLOYMENT_NAME, false)));
        Assert.assertFalse("Operation add-content over existing content in deployment with url content and overwrite=false succeeded, should fail",
                Operations.isSuccessfulOutcome(addContentByUrlAndGetOutcome(TEST_DEPLOYMENT_NAME, false)));

        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }


    private boolean getManagedAttribute(String deploymentName) {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(NAME).set(MANAGED);
        Future<ModelNode> future = managementClient.getControllerClient().executeAsync(OperationBuilder.create(op, false).build(), null);
        try {
            ModelNode response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            return Operations.readResult(response).asBoolean();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean writeManagedAttributeAndGetOutcome(String deploymentName, boolean newValue) {
        ModelNode op = new ModelNode();
        op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(NAME).set(MANAGED);
        op.get(VALUE).set(newValue);

        return Operations.isSuccessfulOutcome(awaitOperationExecutionAndReturnOutcome(op));
    }

    private boolean explodeDeploymentAndGetOutcome(String deploymentName) {
        ModelNode op = new ModelNode();
        op.get(OP).set(EXPLODE);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);

        return Operations.isSuccessfulOutcome(awaitOperationExecutionAndReturnOutcome(op));
    }

    private ModelNode addContentByByteArrayAndGetOutcome(String deploymentName, boolean overwrite) throws Exception {
        final Properties addedContentProperties = new Properties();
        addedContentProperties.put(deploymentName + "Service", "isReplaced");
        ModelNode addedContentNode = new ModelNode();
        addedContentNode.get(TARGET_PATH).set("SimpleTest.properties");
        String addedContentString;
        try (StringWriter writer = new StringWriter()) {
            addedContentProperties.store(writer, "Added with add-content op");
            addedContentString = writer.toString();
            addedContentNode.get(BYTES).set(addedContentString.getBytes(StandardCharsets.UTF_8));
        }
        ModelNode addContentOp = new ModelNode();
        addContentOp.get(OP).set(ADD_CONTENT);
        addContentOp.get(OP_ADDR).set(DEPLOYMENT, deploymentName);
        addContentOp.get(CONTENT).setEmptyList();
        addContentOp.get(CONTENT).add(addedContentNode);
        addContentOp.get(OVERWRITE).add(overwrite);

        return awaitOperationExecutionAndReturnOutcome(addContentOp);
    }

    private ModelNode addContentByInputStreamAndGetOutcome(String deploymentName, boolean overwrite) throws Exception {
        final Properties addedContentProperties = new Properties();
        final ModelControllerClient client = managementClient.getControllerClient();
        addedContentProperties.put(deploymentName + "Service", "isReplaced");
        List<InputStream> attachments = new ArrayList<>();
        String content;
        try (StringWriter writer = new StringWriter()) {
            addedContentProperties.store(writer, "New Content");
            content = writer.toString();
        }
        try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            ModelNode addedContentNode = new ModelNode();
            addedContentNode.get(TARGET_PATH).set("SimpleTest.properties");
            addedContentNode.get(INPUT_STREAM_INDEX).set(0);
            attachments.add(is);
            ModelNode addContentOp = new ModelNode();
            addContentOp.get(OP).set(ADD_CONTENT);
            addContentOp.get(OP_ADDR).set(DEPLOYMENT, deploymentName);
            addContentOp.get(CONTENT).setEmptyList();
            addContentOp.get(CONTENT).add(addedContentNode);
            addContentOp.get(OVERWRITE).add(overwrite);

            Future<ModelNode> future = client.executeAsync(Operation.Factory.create(addContentOp, attachments), null);
            ModelNode response;
            try {
                response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }

            Assert.assertNotNull(response);
            return response;
        }
    }

    private ModelNode addContentByUrlAndGetOutcome(String deploymentName, boolean overwrite) throws Exception {
        final Properties addedContentProperties = new Properties();
        addedContentProperties.put(deploymentName + "Service", "isReplaced");
        ModelNode addedContentNode = new ModelNode();
        addedContentNode.get(TARGET_PATH).set("SimpleTest.properties");
        File archivesDir = new File("target", "archives");
        File addedContentFile = new File(archivesDir, "simpleTest.properties");
        if (addedContentFile.exists()) {
            addedContentFile.delete();
        }
        addedContentFile.getParentFile().mkdirs();
        addedContentFile.createNewFile();
        try (OutputStream out = new FileOutputStream(addedContentFile, false)) {
            addedContentProperties.store(out, "Added with add-content op");
            addedContentNode.get(URL).set(addedContentFile.toURI().toURL().toString());
        }
        ModelNode addContentOp = new ModelNode();
        addContentOp.get(OP).set(ADD_CONTENT);
        addContentOp.get(OP_ADDR).set(DEPLOYMENT, TEST_DEPLOYMENT_NAME);
        addContentOp.get(CONTENT).setEmptyList();
        addContentOp.get(CONTENT).add(addedContentNode);
        addContentOp.get(OVERWRITE).add(overwrite);

        return awaitOperationExecutionAndReturnOutcome(addContentOp);
    }

    private boolean removeContentFromDeploymentAndGetOutcome(String deploymentName) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(REMOVE_CONTENT);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(PATHS).setEmptyList();
        op.get(PATHS).add("simpleTest.properties");

        return Operations.isSuccessfulOutcome(awaitOperationExecutionAndReturnOutcome(op));
    }

    private boolean readContentFromDeploymentAndGetOutcome(String deploymentName) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_CONTENT);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(PATH).set("simpleTest.properties");

        return Operations.isSuccessfulOutcome(awaitOperationExecutionAndReturnOutcome(op));
    }

    private void deployManagedDeployment(String deploymentName, boolean archived) throws Exception {
        final Properties properties = new Properties();
        properties.put(deploymentName + "Service", "isNew");
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties);
        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);

        try (InputStream is = archive.as(ZipExporter.class).exportAsInputStream()) {
            if (archived) {
                Future<?> future = manager.execute(manager.newDeploymentPlan()
                        .add(deploymentName, is)
                        .deploy(deploymentName)
                        .build());
                awaitDeploymentExecution(future);
            } else {
                Future<?> future = manager.execute(manager.newDeploymentPlan()
                        .add(deploymentName, is)
                        .explodeDeployment(deploymentName)
                        .deploy(deploymentName)
                        .build());
                awaitDeploymentExecution(future);
            }
        }

        ServiceActivatorDeploymentUtil.validateProperties(client, properties);
    }

    private void deployUnmanagedDeployment(String deploymentName, boolean archived) throws Exception {
        final Properties properties = new Properties();
        properties.put(deploymentName + "Service", "isNew");
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties);
        final ModelControllerClient client = managementClient.getControllerClient();
        final File archiveDir = new File("target/archives");
        archiveDir.mkdirs();
        File deploymentFile;
        if (archived) {
            deploymentFile = new File(archiveDir, deploymentName);
            archive.as(ZipExporter.class).exportTo(deploymentFile, true);
        } else {
            archive.as(ExplodedExporter.class).exportExploded(archiveDir);
            deploymentFile = new File(archiveDir, deploymentName);
        }

        ModelNode compositeOp = new ModelNode();

        ModelNode content = new ModelNode();
        content.get(PATH).set(deploymentFile.getAbsolutePath());
        content.get(ARCHIVE).set(archived);
        ModelNode addOp = new ModelNode();
        addOp.get(OP).set(ADD);
        addOp.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        addOp.get(CONTENT).set(content);
        ModelNode deployOp = new ModelNode();
        deployOp.get(OP).set(DEPLOY);
        deployOp.get(OP_ADDR).add(DEPLOYMENT, deploymentName);

        compositeOp.get(OP).set(COMPOSITE);
        compositeOp.get(OP_ADDR).setEmptyList();
        compositeOp.get(STEPS).add(addOp);
        compositeOp.get(STEPS).add(deployOp);
        awaitOperationExecution(compositeOp);

        ServiceActivatorDeploymentUtil.validateProperties(client, properties);
    }

    private void undeployAndRemoveDeployment(String deploymentName) {
        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);

        Future<?> future = manager.execute(manager.newDeploymentPlan()
                .undeploy(deploymentName)
                .remove(deploymentName)
                .build());

        awaitDeploymentExecution(future);

        File deploymentFile = new File("target/archives/" + deploymentName);
        if (deploymentFile.exists()) {
            cleanFile(deploymentFile);
        }
    }

    private void awaitOperationExecution(ModelNode op) {
        Future<ModelNode> future = managementClient.getControllerClient().executeAsync(OperationBuilder.create(op, false).build(), null);
        try {
            ModelNode response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            Assert.assertTrue(Operations.isSuccessfulOutcome(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private ModelNode awaitOperationExecutionAndReturnOutcome(ModelNode op) {
        Future<ModelNode> future = managementClient.getControllerClient().executeAsync(OperationBuilder.create(op, false).build(), null);
        try {
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
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
        if (toClean.exists()) {
            if (toClean.isDirectory()) {
                for (File child : toClean.listFiles()) {
                    cleanFile(child);
                }
            }
            toClean.delete();
        }
    }

    private void cleanUp() {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_RESOURCE_OPERATION);
        op.get(OP_ADDR).set(DEPLOYMENT, TEST_DEPLOYMENT_NAME);
        ModelNode result = awaitOperationExecutionAndReturnOutcome(op);
        if (Operations.isSuccessfulOutcome(result)) {
            undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        }

        String jbossBaseDir = System.getProperty("jboss.home");
        Assert.assertNotNull(jbossBaseDir);
        Path dataDir = new File(jbossBaseDir).toPath().resolve("standalone").resolve("data");
        if (Files.exists(dataDir)) { cleanFile(dataDir.resolve("managed-exploded").toFile()); }
        File archivesDir = new File("target", "archives");
        if (Files.exists(archivesDir.toPath())) { cleanFile(archivesDir); }
    }

}
