/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.test.standalone.mgmt.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;
import static org.jboss.as.repository.PathUtil.deleteRecursively;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
public class DeploymentOverlayTestCase {

    // Max time to wait for some action to complete, in ms
    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    private static final PathAddress OVERLAY_ADDR = PathAddress.pathAddress(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, "overlay");
    private static final ModelNode OVERLAY_CONTENT_ADDR = OVERLAY_ADDR.append(CONTENT, ServiceActivatorDeployment.PROPERTIES_RESOURCE).toModelNode();
    private static final ModelNode OVERLAY_DEPLOYMENT_ADDR = OVERLAY_ADDR.append(ModelDescriptionConstants.DEPLOYMENT, "test-deployment.jar").toModelNode();

    @Inject
    private ManagementClient managementClient;

    private static final Properties properties = new Properties();
    private static final Properties properties2 = new Properties();

    @BeforeClass
    public static void clearProperties() throws Exception {
        properties.clear();
        properties.put("service", "is new");

        properties2.clear();
        properties2.put("service", "is overwritten");
    }

    @AfterClass
    public static void cleanFiles() throws IOException {
        String jbossBaseDir = System.getProperty("jboss.home");
        Assert.assertNotNull(jbossBaseDir);
        Path dataDir = new File(jbossBaseDir).toPath().resolve("standalone").resolve("data");
        Assert.assertTrue(Files.exists(dataDir));
        deleteRecursively(dataDir.resolve("managed-exploded"));
    }

    @Test
    public void testDeploymentArchive() throws Exception {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        testDeployments(new OverlayDeploymentExecutor() {

            @Override
            public void initialDeploy() throws IOException, MgmtOperationException {
                try (InputStream is = archive.as(ZipExporter.class).exportAsInputStream()) {
                    Future<?> future = manager.execute(manager.newDeploymentPlan()
                            .add("test-deployment.jar", is)
                            .deploy("test-deployment.jar")
                            .build());
                    awaitDeploymentExecution(future);
                    ServiceActivatorDeploymentUtil.validateProperties(client, properties);
                }
            }

            @Override
            public void undeploy() {
                Future<?> future = manager.execute(manager.newDeploymentPlan().undeploy("test-deployment.jar")
                        .remove("test-deployment.jar").build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void redeploy() {
                Future<?> future = manager.execute(manager.newDeploymentPlan().undeploy("test-deployment.jar").build());
                awaitDeploymentExecution(future);
                future = manager.execute(manager.newDeploymentPlan().deploy("test-deployment.jar").build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void addOverlay() throws IOException {
                ModelNode response = client.execute(Operations.createAddOperation(OVERLAY_ADDR.toModelNode()),
                        OperationMessageHandler.DISCARD);
                Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
                ModelNode op = Operations.createAddOperation(OVERLAY_CONTENT_ADDR);
                op.get(CONTENT).get(ModelDescriptionConstants.INPUT_STREAM_INDEX).set(0);
                String content = "";
                try (StringWriter writer = new StringWriter()) {
                    properties2.store(writer, "Overlay Content");
                    content = writer.toString();
                }
                try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                    Future<?> future = client.executeOperationAsync(Operation.Factory.create(op, Collections.singletonList(is)), OperationMessageHandler.DISCARD);
                    awaitDeploymentExecution(future);
                }
                response = client.execute(Operations.createAddOperation(OVERLAY_DEPLOYMENT_ADDR));
                Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
            }

            @Override
            public void redeployLinks() throws IOException, MgmtOperationException {
                 ModelNode failingOperation = Operations.createOperation("redeploy-links", OVERLAY_ADDR.toModelNode());
                failingOperation.get("deployments").setEmptyList();
                failingOperation.get("deployments").add("*.war");
                failingOperation.get("deployments").add("test.jar");
                ModelNode response = client.execute(failingOperation, OperationMessageHandler.DISCARD);
                Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
                ServiceActivatorDeploymentUtil.validateProperties(client, properties); //Nothing was redeployed
                response = client.execute(Operations.createOperation("redeploy-links", OVERLAY_ADDR.toModelNode()),
                        OperationMessageHandler.DISCARD);
                Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
                ServiceActivatorDeploymentUtil.validateProperties(client, properties2);
            }

            @Override
            public void readOverlayContent(String expectedValue) throws IOException {
                Future<OperationResponse> future = client.executeOperationAsync(
                        Operation.Factory.create(
                                Operations.createReadAttributeOperation(OVERLAY_CONTENT_ADDR, "stream")), OperationMessageHandler.DISCARD);
                try {
                    OperationResponse response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e.getCause());
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void removeOverlay(boolean check) throws IOException, MgmtOperationException {
                ModelNode response = client.execute(Operations.createRemoveOperation(OVERLAY_CONTENT_ADDR));
                if(check) {
                    Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
                }
                ModelNode removeOverlayLink = Operations.createRemoveOperation(OVERLAY_DEPLOYMENT_ADDR);
                removeOverlayLink.get("redeploy-affected").set(check);
                response = client.execute(removeOverlayLink);
                if(check) {
                    Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
                    ServiceActivatorDeploymentUtil.validateProperties(client, properties);
                }
                response = client.execute(Operations.createRemoveOperation(OVERLAY_ADDR.toModelNode()));
                if(check) {
                    Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
                }
            }

        });
    }

    private void testDeployments(OverlayDeploymentExecutor deploymentExecutor) throws Exception {
        // Initial deploy
        Set<String> initialHashes = getAllDeploymentHashesFromContentDir(true);
        deploymentExecutor.initialDeploy();

        //listener.await();
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties);
        try {
            // Add overlay
            deploymentExecutor.addOverlay();
            deploymentExecutor.redeployLinks();
            deploymentExecutor.readOverlayContent("is overwritten");
            ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties2);

            // listener.await();
            deploymentExecutor.removeOverlay(true);
            deploymentExecutor.redeploy();
            ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties);
            // Undeploy
        } finally {
            deploymentExecutor.undeploy();
            deploymentExecutor.removeOverlay(false);
        }
        Assert.assertEquals(initialHashes, getAllDeploymentHashesFromContentDir(false));
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
            return new HashSet<>();
        }
        Assert.assertTrue(file.exists());
        file = new File(file, "content");
        Assert.assertTrue(file.exists());

        Set<String> hashes = new HashSet<>();
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
        Object t = null;
        try {
            t = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

    }

    private interface OverlayDeploymentExecutor {

        void initialDeploy() throws Exception;

        void addOverlay() throws Exception;

        void readOverlayContent(String expectedValue) throws Exception;

        void removeOverlay(boolean check) throws Exception;

        void undeploy();

        void redeploy();

        void redeployLinks() throws Exception;
    }

}
