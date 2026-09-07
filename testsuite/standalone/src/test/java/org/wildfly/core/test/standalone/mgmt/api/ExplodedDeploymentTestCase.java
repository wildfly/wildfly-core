/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;
import static org.jboss.as.repository.PathUtil.deleteRecursively;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.inject.Inject;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.shared.PermissionUtils;
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
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
public class ExplodedDeploymentTestCase {

    // Max time to wait for some action to complete, in ms
    private static final int TIMEOUT = TimeoutUtil.adjust(20000);

    @Inject
    private ManagementClient managementClient;

    private static final Properties properties = new Properties();
    private static final Properties properties2 = new Properties();
    private static final Properties properties3 = new Properties();

    @BeforeClass
    public static void clearProperties() throws Exception {
        properties.clear();
        properties.put("service", "is new");

        properties2.clear();
        properties2.put("service", "is added");

        properties3.clear();
        properties3.put("service", "is replaced");
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
        testDeployments(false, new ExplodedDeploymentExecutor() {

            @Override
            public void initialDeploy() throws IOException {
                try (InputStream is = archive.as(ZipExporter.class).exportAsInputStream()) {
                    Future<?> future = manager.execute(manager.newDeploymentPlan()
                            .add("test-deployment.jar", is)
                            .explodeDeployment("test-deployment.jar")
                            .deploy("test-deployment.jar")
                            .build());
                    awaitDeploymentExecution(future);
                }
            }

            @Override
            public void addContent() throws IOException {
                String content = "";
                try (StringWriter writer = new StringWriter()) {
                    properties2.store(writer, "New Content");
                    content = writer.toString();
                }
                try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                    Future<?> future = manager.execute(manager.newDeploymentPlan()
                            .addContentToDeployment("test-deployment.jar", Collections.singletonMap("SimpleTest.properties", is))
                            .redeploy("test-deployment.jar")
                            .build());
                    awaitDeploymentExecution(future);
                }
            }

            @Override
            public void replaceContent() throws IOException {
                String content = "";
                try (StringWriter writer = new StringWriter()) {
                    properties3.store(writer, "Replace Content");
                    content = writer.toString();
                }
                try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                    Map<String, InputStream> contents = new HashMap<>();
                    contents.put("service-activator-deployment.properties", is);
                    Future<?> future = manager.execute(manager.newDeploymentPlan()
                            .addContentToDeployment("test-deployment.jar", contents)
                            .redeploy("test-deployment.jar")
                            .build());
                    awaitDeploymentExecution(future);
                }
            }

            @Override
            public void removeContent() throws IOException {
                Future<?> future = manager.execute(manager.newDeploymentPlan()
                        .removeContentFromDeployment("test-deployment.jar", Collections.singletonList("SimpleTest.properties"))
                        .redeploy("test-deployment.jar")
                        .build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void readContent(String path, String expectedValue) throws IOException {
                ModelNode op = new ModelNode();
                op.get(OP).set(ClientConstants.READ_CONTENT_OPERATION);
                op.get(OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
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

            @Override
            public void undeploy() {
                Future<?> future = manager.execute(manager.newDeploymentPlan().undeploy("test-deployment.jar")
                        .remove("test-deployment.jar").build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void checkNoContent(String path) throws IOException {
                ModelNode op = new ModelNode();
                op.get(OP).set(ClientConstants.READ_CONTENT_OPERATION);
                op.get(OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
                op.get(PATH).set(path);
                Future<OperationResponse> future = client.executeOperationAsync(OperationBuilder.create(op, false).build(), null);
                try {
                    OperationResponse response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
                    Assert.assertFalse(Operations.isSuccessfulOutcome(response.getResponseNode()));
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
                op.get(OP).set(ClientConstants.DEPLOYMENT_BROWSE_CONTENT_OPERATION);
                op.get(OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
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
                    List<String> unexpectedContents = new ArrayList<String>();
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
                    Assert.assertTrue("Unexpected files listed by /deployment=test-deployment.jar:browse-content(depth="
                                    + depth + ", archive=" + archive + ") : " + unexpectedContents.toString(),
                            unexpectedContents.isEmpty());
                    Assert.assertTrue("Expected files not listed by /deployment=test-deployment.jar:browse-content(depth="
                                    + depth + ", archive=" + archive + ") : " + expectedContents.toString(),
                            expectedContents.isEmpty());
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

        });
    }

    @Test
    public void testEmptyDeployment() throws Exception {

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        testDeployments(false, new ExplodedDeploymentExecutor() {

            @Override
            public void initialDeploy() throws IOException {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                properties.store(out, "");
                Map<String, InputStream> contents = new HashMap<>();
                contents.put("META-INF/MANIFEST.MF", new ByteArrayInputStream("Dependencies: org.jboss.msc\n".getBytes(StandardCharsets.UTF_8)));
                contents.put("META-INF/services/org.jboss.msc.service.ServiceActivator", new ByteArrayInputStream("org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment\n".getBytes(StandardCharsets.UTF_8)));
                contents.put("org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class", ServiceActivatorDeployment.class.getResourceAsStream("ServiceActivatorDeployment.class"));
                contents.put("service-activator-deployment.properties", new ByteArrayInputStream(out.toByteArray()));
                contents.put("META-INF/permissions.xml", new ByteArrayInputStream(PermissionUtils.createPermissionsXml(
                        new PropertyPermission("test.deployment.trivial.prop", "write"),
                        new PropertyPermission("service", "write"))));
                Future<?> future = manager.execute(manager.newDeploymentPlan()
                        .add("test-deployment.jar", (InputStream) null)
                        .addContentToDeployment("test-deployment.jar", contents)
                        .deploy("test-deployment.jar")
                        .build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void addContent() throws IOException {
                String content = "";
                try (StringWriter writer = new StringWriter()) {
                    properties2.store(writer, "New Content");
                    content = writer.toString();
                }
                try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                    Future<?> future = manager.execute(manager.newDeploymentPlan()
                            .addContentToDeployment("test-deployment.jar", Collections.singletonMap("SimpleTest.properties", is))
                            .redeploy("test-deployment.jar")
                            .build());
                    awaitDeploymentExecution(future);
                }
            }

            @Override
            public void replaceContent() throws IOException {
                Path tempFile = Files.createTempFile("test", ".properties");
                try(OutputStream out = Files.newOutputStream(tempFile)) {
                    properties3.store(out, "Replace Content");
                }
                Future<?> future = manager.execute(manager.newDeploymentPlan()
                        .addContentFileToDeployment("test-deployment.jar", Collections.singletonMap("service-activator-deployment.properties", tempFile))
                        .redeploy("test-deployment.jar")
                        .build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void removeContent() throws IOException {
                Future<?> future = manager.execute(manager.newDeploymentPlan()
                        .removeContentFromDeployment("test-deployment.jar", Collections.singletonList("SimpleTest.properties"))
                        .build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void readContent(String path, String expectedValue) throws IOException {
                ModelNode op = new ModelNode();
                op.get(OP).set(ClientConstants.READ_CONTENT_OPERATION);
                op.get(OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
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

            @Override
            public void undeploy() {
                Future<?> future = manager.execute(manager.newDeploymentPlan()
                        .undeploy("test-deployment.jar")
                        .remove("test-deployment.jar")
                        .build());
                awaitDeploymentExecution(future);
            }

            @Override
            public void checkNoContent(String path) throws IOException {
                ModelNode op = new ModelNode();
                op.get(OP).set(ClientConstants.READ_CONTENT_OPERATION);
                op.get(OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
                op.get(PATH).set(path);
                Future<OperationResponse> future = client.executeOperationAsync(OperationBuilder.create(op, false).build(), null);
                try {
                    OperationResponse response = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
                    Assert.assertFalse(Operations.isSuccessfulOutcome(response.getResponseNode()));
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
                op.get(OP).set(ClientConstants.DEPLOYMENT_BROWSE_CONTENT_OPERATION);
                op.get(OP_ADDR).add(DEPLOYMENT, "test-deployment.jar");
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
                    Assert.assertTrue(Operations.isSuccessfulOutcome(response));
                    List<String> unexpectedContents = new ArrayList<String>();
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
                    Assert.assertTrue("Unexpected files listed by /deployment=test-deployment.jar:browse-content(depth="
                                    + depth + ", archive=" + archive + ") : " + unexpectedContents.toString(),
                            unexpectedContents.isEmpty());
                    Assert.assertTrue("Expected files not listed by /deployment=test-deployment.jar:browse-content(depth="
                                    + depth + ", archive=" + archive + ") : " + expectedContents.toString(),
                            expectedContents.isEmpty());
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
        });
    }

    private void testDeployments(boolean fromFile, ExplodedDeploymentExecutor deploymentExecutor) throws Exception {
        // Initial deploy
        Set<String> initialHashes = null;
        if (!fromFile) {
            initialHashes = getAllDeploymentHashesFromContentDir(true);
        }
        deploymentExecutor.initialDeploy();

        //listener.await();
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties);

        deploymentExecutor.readContent("", "");

        String initialDeploymentHash = null;
        if (!fromFile) {
            Set<String> currentHashes = getAllDeploymentHashesFromContentDir(false);
            currentHashes.removeAll(initialHashes);
            Assert.assertEquals(1, currentHashes.size());
            initialDeploymentHash = currentHashes.iterator().next();
        }
        try {
        // Add content
        // listener.reset(2);
        deploymentExecutor.addContent();
        deploymentExecutor.readContent("SimpleTest.properties", "is added");
        deploymentExecutor.browseContent("", new ArrayList<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF",
                "META-INF/permissions.xml", "META-INF/services/", "META-INF/services/org.jboss.msc.service.ServiceActivator",
                "org/","org/jboss/","org/jboss/as/", "org/jboss/as/test/", "org/jboss/as/test/deployment/",
                "org/jboss/as/test/deployment/trivial/", "service-activator-deployment.properties",
                "org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class",  "SimpleTest.properties")),
                -1, false);
        deploymentExecutor.browseContent("", new ArrayList<>(Arrays.asList("META-INF/", "org/",
                "service-activator-deployment.properties", "SimpleTest.properties")), 1, false);
        deploymentExecutor.browseContent("", new ArrayList<>(), -1, true);
        if (!fromFile) {
            Set<String> currentHashes = getAllDeploymentHashesFromContentDir(false);
            Assert.assertFalse(currentHashes.contains(initialDeploymentHash)); //Should have been deleted when added
            currentHashes.removeAll(initialHashes);
            Assert.assertEquals(1, currentHashes.size());
        }

        // listener.await();
        deploymentExecutor.replaceContent();
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), properties3);
        deploymentExecutor.readContent("service-activator-deployment.properties", "is replaced");

        if (!fromFile) {
            Set<String> currentHashes = getAllDeploymentHashesFromContentDir(false);
            Assert.assertFalse(currentHashes.contains(initialDeploymentHash)); //Should have been deleted when replaced
            currentHashes.removeAll(initialHashes);
            Assert.assertEquals(1, currentHashes.size());
        }

        deploymentExecutor.removeContent();
        deploymentExecutor.checkNoContent("SimpleTest.properties");
        // Undeploy
        // listener.reset(1);
        } finally {
            deploymentExecutor.undeploy();
        }
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
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private interface ExplodedDeploymentExecutor {

        void initialDeploy() throws IOException;

        void addContent() throws IOException;

        void replaceContent() throws IOException;

        void removeContent() throws IOException;

        void readContent(String path, String expectedValue) throws IOException;

        void browseContent(String path, List<String> expectedContents, int depth, boolean archive) throws IOException;

        void checkNoContent(String path) throws IOException;

        void undeploy() throws IOException;
    }

}
