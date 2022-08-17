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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BROWSE_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPTH;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests to check hard requirements and extreme cases for deployment operations.
 * @author <a href="mailto:mjurc@redhat.com">Michal Jurc</a> (c) 2016 Red Hat, Inc.
 */
@RunWith(WildFlyRunner.class)
public class DeploymentOperationsTestCase {

    @Inject
    private ManagementClient managementClient;

    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    private static final String TEST_DEPLOYMENT_NAME = "test-deployment.jar";
    private static final String PROPERTIES_RESOURCE = "service-activator-deployment.properties";

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
    public void testManagedOperationsFailWithUnmanagedArchiveDeployment() throws Exception {
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, true);
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, ""), "WFLYSRV0255");
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "abc"), "WFLYSRV0255");
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "META-INF/"), "WFLYSRV0255");
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, ""), "WFLYSRV0255");
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, "abc"), "WFLYSRV0255");
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, "META-INF/"), "WFLYSRV0255");
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testManagedOperationsFailWithUnmanagedExplodedDeployment() throws Exception {
        deployUnmanagedDeployment(TEST_DEPLOYMENT_NAME, false);
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, ""), "WFLYSRV0255");
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "abc"), "WFLYSRV0255");
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "META-INF/"), "WFLYSRV0255");
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, ""), "WFLYSRV0255");
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, "abc"), "WFLYSRV0255");
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, "META-INF/"), "WFLYSRV0255");
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
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testExplodeFailsWithDeployedManagedArchiveDeployment() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(explodeDeploymentAndGetOutcome(TEST_DEPLOYMENT_NAME));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testExplodeContentFailsWithDeployedManagedArchiveDeployment() throws Exception {
        deployManagedDeploymentWithArchives(TEST_DEPLOYMENT_NAME, true);
        Assert.assertFalse(explodeDeploymentContentAndGetOutcome(TEST_DEPLOYMENT_NAME, "web.war"));
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testExplodeContentWithDeployedManagedArchiveDeployment() throws Exception {
        deployManagedDeploymentWithArchives(TEST_DEPLOYMENT_NAME, false);
        Assert.assertTrue(explodeDeploymentContentAndGetOutcome(TEST_DEPLOYMENT_NAME, "web.war"));
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

    @Test
    public void testBrowseContentFailsNonexistentPath() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, "nonexistent.file"), "WFLYDR0020");
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, false);
        assertOperationFailedWithCode(browseContentFromDeploymentAndGetResult(TEST_DEPLOYMENT_NAME, "nonexistent.file"), "WFLYDR0020");
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testBrowseContentParameters() throws Exception {
        testBrowseContentParameterCombinations(true);
        testBrowseContentParameterCombinations(false);
    }

    @Test
    public void testReadContentFailsNonExistentPath() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "nonexistent.file"), "WFLYDR0020");
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, false);
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "nonexistent.file"), "WFLYDR0020");
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    @Test
    public void testReadContentFailsDirectory() throws Exception {
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, true);
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "META_INF/"), "WFLYDR0020");
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
        deployManagedDeployment(TEST_DEPLOYMENT_NAME, false);
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, "META_INF/"), "WFLYDR0020");
        assertOperationFailedWithCode(readContentFromDeployment(TEST_DEPLOYMENT_NAME, ""), "WFLYDR0020");
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
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private boolean writeManagedAttributeAndGetOutcome(String deploymentName, boolean newValue) {
        ModelNode op = new ModelNode();
        op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(NAME).set(MANAGED);
        op.get(VALUE).set(newValue);

        return Operations.isSuccessfulOutcome(awaitOperationExecutionAndReturnResult(op));
    }

    private boolean explodeDeploymentAndGetOutcome(String deploymentName) {
        ModelNode op = new ModelNode();
        op.get(OP).set(EXPLODE);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);

        return Operations.isSuccessfulOutcome(awaitOperationExecutionAndReturnResult(op));
    }

    private boolean explodeDeploymentContentAndGetOutcome(String deploymentName, String archivePath) throws IOException {
        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
        DeploymentPlan plan = manager.newDeploymentPlan()
                .undeploy(deploymentName)
                .explodeDeploymentContent(deploymentName, archivePath)
                .deploy(deploymentName)
                .build();
        Future<ServerDeploymentPlanResult> future = manager.execute(plan);
        return awaitDeploymentExecution(future).getDeploymentActionResult(plan.getDeploymentActions().get(plan.getDeploymentActions().size() - 1).getId()).getResult() == ServerUpdateActionResult.Result.EXECUTED;
    }

    private ModelNode addContentByByteArrayAndGetOutcome(String deploymentName, boolean overwrite) throws Exception {
        final Properties addedContentProperties = new Properties();
        addedContentProperties.put(deploymentName + "Service", "isReplaced");
        ModelNode addedContentNode = new ModelNode();
        addedContentNode.get(TARGET_PATH).set(PROPERTIES_RESOURCE);
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
        addContentOp.get(OVERWRITE).set(overwrite);

        return awaitOperationExecutionAndReturnResult(addContentOp);
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
            addedContentNode.get(TARGET_PATH).set(PROPERTIES_RESOURCE);
            addedContentNode.get(INPUT_STREAM_INDEX).set(0);
            attachments.add(is);
            ModelNode addContentOp = new ModelNode();
            addContentOp.get(OP).set(ADD_CONTENT);
            addContentOp.get(OP_ADDR).set(DEPLOYMENT, deploymentName);
            addContentOp.get(CONTENT).setEmptyList();
            addContentOp.get(CONTENT).add(addedContentNode);
            addContentOp.get(OVERWRITE).set(overwrite);

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
            } finally {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }

            Assert.assertNotNull(response);
            return response;
        }
    }

    private ModelNode addContentByUrlAndGetOutcome(String deploymentName, boolean overwrite) throws Exception {
        final Properties addedContentProperties = new Properties();
        addedContentProperties.put(deploymentName + "Service", "isReplaced");
        ModelNode addedContentNode = new ModelNode();
        addedContentNode.get(TARGET_PATH).set(PROPERTIES_RESOURCE);
        File archivesDir = new File("target", "archives");
        File addedContentFile = new File(archivesDir, PROPERTIES_RESOURCE);
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
        addContentOp.get(OVERWRITE).set(overwrite);

        return awaitOperationExecutionAndReturnResult(addContentOp);
    }

    private boolean removeContentFromDeploymentAndGetOutcome(String deploymentName) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(REMOVE_CONTENT);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        op.get(PATHS).setEmptyList();
        op.get(PATHS).add(PROPERTIES_RESOURCE);

        return Operations.isSuccessfulOutcome(awaitOperationExecutionAndReturnResult(op));
    }

    private ModelNode readContentFromDeployment(String deploymentName, String path) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_CONTENT);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        if (!path.isEmpty()) {
            op.get(PATH).set(path);
        }

        return awaitOperationExecutionAndReturnResult(op);
    }

    private ModelNode browseContentFromDeploymentAndGetResult(String deploymentName, String path) throws Exception {
        return browseContentFromDeploymentAndGetResult(deploymentName, path, false, -1);
    }

    private ModelNode browseContentFromDeploymentAndGetResult(String deploymentName, String path, boolean archive, int depth) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(BROWSE_CONTENT);
        op.get(OP_ADDR).add(DEPLOYMENT, deploymentName);
        if (archive) {
            op.get(ARCHIVE).set(archive);
        }
        if (depth > 0) {
            op.get(DEPTH).set(depth);
        }
        if (!path.isEmpty()) {
            op.get(PATH).set(path);
        }

        return awaitOperationExecutionAndReturnResult(op);
    }

    private void assertBrowseContentReturnsExpectedResult(
            List<String> expected, String deploymentName, String path, boolean archive, int depth) throws Exception {
        ModelNode result = browseContentFromDeploymentAndGetResult(deploymentName, path, archive, depth);

        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail("Operation browse content should be successful, but failed: "
                    + Operations.getFailureDescription(result).toString());
        }

        List<String> unexpected = new ArrayList<>();

        if (expected.isEmpty()) {
            Assert.assertEquals("Expected an empty result with /deployment=" + deploymentName+ ":browse-content(" +
                            "path=\"" + path + "\", archive=" + archive + ", depth=" + depth + "), actual result was: "
                            + result.toString(),
                    new ModelNode(), Operations.readResult(result));
        } else {
            List<ModelNode> contents = Operations.readResult(result).asList();
            for (ModelNode content : contents) {
                Assert.assertTrue(content.hasDefined("path"));
                String contentPath = content.get("path").asString();
                Assert.assertTrue(content.hasDefined("directory"));
                if (!content.get("directory").asBoolean()) {
                    Assert.assertTrue(content.hasDefined("file-size"));
                }
                if (!expected.contains(contentPath)) {
                    unexpected.add(contentPath);
                }
                expected.remove(contentPath);
            }
        }
        Assert.assertTrue("Unexpected files listed by /deployment=" + deploymentName+ ":browse-content(" +
                        "path=\"" + path + "\", archive=" + archive + ", depth=" + depth + ") : " + unexpected.toString(),
                        unexpected.isEmpty());
        Assert.assertTrue("Expected files not listed by /deployment=" + deploymentName+ ":browse-content(" +
                        "path=\"" + path + "\", archive=" + archive + ", depth=" + depth + ") : " + expected.toString(),
                        expected.isEmpty());

    }

    private void testBrowseContentParameterCombinations(boolean archive) throws Exception {
        deployManagedDeploymentWithArchives(TEST_DEPLOYMENT_NAME, true);
        ArrayList<String> expected = new ArrayList<>(Arrays.asList("misc/", "other/", "lib/",
                "misc/text.txt", "web.war", "lib/lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "", false, -1);
        expected = new ArrayList<>(Arrays.asList("web.war", "lib/lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "", true, -1);
        expected = new ArrayList<>(Arrays.asList("misc/", "other/", "lib/", "web.war"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "", false, 1);
        expected = new ArrayList<>(Arrays.asList("web.war"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "", true, 1);
        expected = new ArrayList<>(Arrays.asList("lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/", true, -1);
        expected = new ArrayList<>(Arrays.asList("page.html", "lib/", "lib/inner-lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/", false, -1);
        expected = new ArrayList<>(Arrays.asList("lib/inner-lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/", true, -1);
        expected = new ArrayList<>(Arrays.asList("page.html", "lib/"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/", false, 1);
        expected = new ArrayList<>();
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/", true, 1);
        expected = new ArrayList<>(Arrays.asList("inner-lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/lib/", false, -1);
        expected = new ArrayList<>(Arrays.asList("inner-lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/lib/", true, -1);
        expected = new ArrayList<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF",
                "META-INF/permissions.xml", "META-INF/services/", "META-INF/services/org.jboss.msc.service.ServiceActivator",
                "org/","org/jboss/","org/jboss/as/", "org/jboss/as/test/", "org/jboss/as/test/deployment/",
                "org/jboss/as/test/deployment/trivial/", "service-activator-deployment.properties",
                "org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/lib/inner-lib.jar/", false, -1);
        expected = new ArrayList<>();
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/lib/inner-lib.jar/", true, -1);
        expected = new ArrayList<>(Arrays.asList("META-INF/", "org/", "service-activator-deployment.properties"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/lib/inner-lib.jar/", false, 1);
        expected = new ArrayList<>();
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "web.war/lib/inner-lib.jar/", true, 1);
        expected = new ArrayList<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF",
                "META-INF/permissions.xml", "META-INF/services/", "META-INF/services/org.jboss.msc.service.ServiceActivator",
                "org/","org/jboss/","org/jboss/as/", "org/jboss/as/test/", "org/jboss/as/test/deployment/",
                "org/jboss/as/test/deployment/trivial/", "service-activator-deployment.properties",
                "org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class", "inner-lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/", false, -1);
        expected = new ArrayList<>(Arrays.asList("inner-lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/", true, -1);
        expected = new ArrayList<>(Arrays.asList("META-INF/", "org/", "inner-lib.jar", "service-activator-deployment.properties"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/", false, 1);
        expected = new ArrayList<>(Arrays.asList("inner-lib.jar"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/", true, 1);
        expected = new ArrayList<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF",
                "META-INF/permissions.xml","META-INF/services/", "META-INF/services/org.jboss.msc.service.ServiceActivator",
                "org/","org/jboss/","org/jboss/as/", "org/jboss/as/test/", "org/jboss/as/test/deployment/",
                "org/jboss/as/test/deployment/trivial/", "service-activator-deployment.properties",
                "org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/inner-lib.jar/", false, -1);
        expected = new ArrayList<>();
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/inner-lib.jar/", true, -1);
        expected = new ArrayList<>(Arrays.asList("META-INF/", "org/", "service-activator-deployment.properties"));
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/inner-lib.jar/", false, 1);
        expected = new ArrayList<>();
        assertBrowseContentReturnsExpectedResult(expected, TEST_DEPLOYMENT_NAME, "lib/lib.jar/inner-lib.jar/", true, 1);
        undeployAndRemoveDeployment(TEST_DEPLOYMENT_NAME);
    }

    private void deployManagedDeploymentWithArchives(String deploymentName, boolean archived) throws Exception {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "web.war");
        war.addAsWebResource(new StringAsset(deploymentName + " Test Deployment Web"), "page.html");
        war.addAsDirectory("/lib");
        final Properties properties = new Properties();
        properties.put("lib.jar" + "Service", "isNew");
        final JavaArchive innerJar = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("inner-lib.jar", properties);
        final JavaArchive jar = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("lib.jar", properties);
        jar.add(innerJar, new BasicPath("/"), ZipExporter.class);
        war.add(innerJar, new BasicPath("/lib"), ZipExporter.class);
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, deploymentName);
        ear.addAsDirectory("/misc");
        ear.addAsDirectory("/other");
        ear.addAsDirectory("/lib");
        ear.add(new StringAsset("just a misc file"), "/misc/text.txt");
        ear.add(war, new BasicPath("/"), ZipExporter.class);
        ear.add(jar, new BasicPath("/lib"), ZipExporter.class);

        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);

        try (InputStream is = ear.as(ZipExporter.class).exportAsInputStream()) {
            if (archived) {
                Future<ServerDeploymentPlanResult> future = manager.execute(manager.newDeploymentPlan()
                        .add(deploymentName, is)
                        .deploy(deploymentName)
                        .build());
                awaitDeploymentExecution(future);
            } else {
                Future<ServerDeploymentPlanResult> future = manager.execute(manager.newDeploymentPlan()
                        .add(deploymentName, is)
                        .explodeDeployment(deploymentName)
                        .deploy(deploymentName)
                        .build());
                awaitDeploymentExecution(future);
            }
        }
    }

    private void deployManagedDeployment(String deploymentName, boolean archived) throws Exception {
        final Properties properties = new Properties();
        properties.put(deploymentName + "Service", "isNew");
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName, properties);
        archive.delete("META-INF/permissions.xml");
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission(TEST_DEPLOYMENT_NAME + "Service", "write"),
                new PropertyPermission("service", "write")
        ), "permissions.xml");
        final ModelControllerClient client = managementClient.getControllerClient();
        final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);

        try (InputStream is = archive.as(ZipExporter.class).exportAsInputStream()) {
            if (archived) {
                Future<ServerDeploymentPlanResult> future = manager.execute(manager.newDeploymentPlan()
                        .add(deploymentName, is)
                        .deploy(deploymentName)
                        .build());
                awaitDeploymentExecution(future);
            } else {
                Future<ServerDeploymentPlanResult> future = manager.execute(manager.newDeploymentPlan()
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
        archive.delete("META-INF/permissions.xml");
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission(TEST_DEPLOYMENT_NAME + "Service", "write"),
                new PropertyPermission("service", "write")
        ), "permissions.xml");
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

        Future<ServerDeploymentPlanResult> future = manager.execute(manager.newDeploymentPlan()
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
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private ModelNode awaitOperationExecutionAndReturnResult(ModelNode op) {
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
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private ServerDeploymentPlanResult awaitDeploymentExecution(Future<ServerDeploymentPlanResult> future) {
        try {
           return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
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

    private void assertOperationFailedWithCode(ModelNode result, String failureCode) {
        Assert.assertFalse("Operation should not be successful, expected operation failure with code " + failureCode,
                Operations.isSuccessfulOutcome(result));
        String failureDescription = Operations.getFailureDescription(result).toString();
        Assert.assertTrue("Operation should fail with code " + failureCode + ", but instead fails with " + failureDescription,
                failureDescription.contains(failureCode));
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
        ModelNode result = awaitOperationExecutionAndReturnResult(op);
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
