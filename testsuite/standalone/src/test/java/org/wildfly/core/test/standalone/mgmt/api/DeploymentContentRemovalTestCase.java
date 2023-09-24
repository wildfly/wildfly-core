/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt.api;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
@RunWith(WildFlyRunner.class)
public class DeploymentContentRemovalTestCase {

    private static String DEPLOYMENT_ONE_NAME = "deployment-one.jar";
    private static String DEPLOYMENT_TWO_NAME = "deployment-two.jar";

    private static PathAddress DEPLOYMENT_ONE_ADDRESS = PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, DEPLOYMENT_ONE_NAME));
    private static PathAddress DEPLOYMENT_TWO_ADDRESS = PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, DEPLOYMENT_TWO_NAME));

    private static final String tempDir = System.getProperty("java.io.tmpdir");
    private File deployDir;
    private File deploymentFile1;
    private File deploymentFile2;
    private ModelControllerClient client;

    @Inject
    @SuppressWarnings("unused")
    private ManagementClient managementClient;

    @Before
    public void before() throws Exception {
        client = managementClient.getControllerClient();

        deployDir = new File(tempDir + File.separator + "tempDeployments");
        if (deployDir.exists()) {
            FileUtils.deleteDirectory(deployDir);
        }
        Assert.assertTrue("Unable to create deployment scanner directory.", deployDir.mkdir());

        deploymentFile1 = new File(deployDir, DEPLOYMENT_ONE_NAME);
        createDeployment(deploymentFile1);

        deploymentFile2 = new File(deployDir, DEPLOYMENT_TWO_NAME);
        createDeployment(deploymentFile2);

        undeployIfExists(DEPLOYMENT_ONE_ADDRESS);
        undeployIfExists(DEPLOYMENT_TWO_ADDRESS);
    }

    @After
    public void after() throws Exception {
        undeployIfExists(DEPLOYMENT_ONE_ADDRESS);
        undeployIfExists(DEPLOYMENT_TWO_ADDRESS);
        FileUtils.deleteDirectory(deployDir);
    }

    @Test
    public void testContentRemovedInNormalMode() throws IOException {
        testContentRemovedAfterUndeploying();
    }

    @Test
    public void testContentRemovedInAdminMode() throws IOException {
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient(), true);
        try {
            testContentRemovedAfterUndeploying();
        } finally {
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient(), false);
        }
    }

    private void testContentRemovedAfterUndeploying() throws IOException {
        // deploy jar, repository data should be created
        final Operation deployOp = deployOperation(DEPLOYMENT_ONE_ADDRESS, deploymentFile1);
        execute(deployOp);
        final byte[] hash1 = readContentHash(DEPLOYMENT_ONE_ADDRESS);
        assertDataExists(hash1, true);

        // undeploy, repository data should be removed
        final ModelNode undeployOp = undeployOperation(DEPLOYMENT_ONE_ADDRESS);
        execute(undeployOp);
        assertDataExists(hash1, false);
    }

    @Test
    public void testFullReplaceDeploymentInNormalMode() throws IOException {
        testFullReplaceDeployment();
    }

    @Test
    public void testFullReplaceDeploymentInAdminMode() throws IOException {
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient(), true);
        try {
            testFullReplaceDeployment();
        } finally {
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient(), false);
        }
    }

    private void testFullReplaceDeployment() throws IOException {
        execute(deployOperation(DEPLOYMENT_ONE_ADDRESS, deploymentFile1));
        execute(deployOperation(DEPLOYMENT_TWO_ADDRESS, deploymentFile2));

        final byte[] hash1 = readContentHash(DEPLOYMENT_ONE_ADDRESS);
        final byte[] hash2 = readContentHash(DEPLOYMENT_TWO_ADDRESS);
        Assert.assertNotEquals(hash1, hash2); // presume contents have different hashes
        assertDataExists(hash1, true);
        assertDataExists(hash2, true);

        // replace deployment1 with content from deployment2, content1 should be removed
        // as it's no longer used by any deployment
        execute(fullReplaceDeploymentOperation(DEPLOYMENT_ONE_NAME, hash2)); // replace dep1 content with dep2 content
        assertDataExists(hash1, false);
        assertDataExists(hash2, true);

        // replace deployment2 with unmanaged content, content2 should stay in the repository
        // as it's still used by deployment1
        execute(fullReplaceDeploymentOperation(DEPLOYMENT_TWO_NAME, deploymentFile2));
        assertDataExists(hash1, false);
        assertDataExists(hash2, true);
    }


    private void createDeployment(final File file) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.add(new StringAsset("ArchiveName: " + file.getName()), "META-INF/MANIFEST.MF"); // something unique for each deployment
        archive.as(ZipExporter.class).exportTo(file);
    }

    private Operation deployOperation(PathAddress address, File file) throws IOException {
        final ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).set(address.toModelNode());
        op.get(ENABLED).set(true);
        op.get(CONTENT).add().get(INPUT_STREAM_INDEX).set(0);

        return OperationBuilder.create(op, true)
                .addFileAsAttachment(file)
                .build();
    }

    private ModelNode undeployOperation(PathAddress address) throws IOException {
        final ModelNode op = new ModelNode();
        op.get(OP).set(REMOVE);
        op.get(OP_ADDR).set(address.toModelNode());
        return op;
    }

    private ModelNode readDeploymentOperation(PathAddress address) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).set(address.toModelNode());
        op.get("recursive").set(true);
        op.get(NAME).set("content");
        return op;
    }

    private ModelNode fullReplaceDeploymentOperation(String deploymentName, byte[] newContentHash) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(FULL_REPLACE_DEPLOYMENT);
        op.get(ADDRESS).setEmptyList();
        op.get(NAME).set(deploymentName);
        op.get(CONTENT).get(HASH).set(newContentHash);
        return op;
    }

    private ModelNode fullReplaceDeploymentOperation(String deploymentName, File unmanagedDeployment) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(FULL_REPLACE_DEPLOYMENT);
        op.get(ADDRESS).setEmptyList();
        op.get(NAME).set(deploymentName);
        op.get(CONTENT).get(PATH).set(unmanagedDeployment.getAbsolutePath());
        op.get(CONTENT).get(ARCHIVE).set(true);
        return op;
    }

    private byte[] extractHash(ModelNode result) {
        byte[] hash = result.get(RESULT).asList().get(0).get("hash").asBytes();
        Assert.assertEquals(20, hash.length);
        return hash;
    }

    private byte[] readContentHash(PathAddress deploymentAddress) throws IOException {
        final ModelNode readResourceOp = readDeploymentOperation(deploymentAddress);
        final ModelNode result = execute(readResourceOp);
        return extractHash(result);
    }

    private void undeployIfExists(PathAddress deploymentAddress) throws IOException {
        client.execute(undeployOperation(deploymentAddress)); // ignore result
    }

    private void assertDataExists(byte[] hash, boolean exists) {
        final String hashStr = HashUtil.bytesToHexString(hash);
        Path dataRepository = Paths.get(System.getProperty("jboss.home"), "standalone/data/content");
        Path deploymentContent = dataRepository.resolve(hashStr.substring(0, 2)).resolve(hashStr.substring(2)).resolve("content");
        Assert.assertEquals(exists, deploymentContent.toFile().exists());
    }

    private ModelNode execute(ModelNode operation) throws IOException {
        ModelNode result = client.execute(operation);
        handleResult(result);
        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    private ModelNode execute(Operation operation) throws IOException {
        ModelNode result = client.execute(operation);
        handleResult(result);
        return result;
    }

    private void handleResult(ModelNode result) {
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            String failureDescription = result.get(FAILURE_DESCRIPTION).asString();
            Assert.fail("Operation failed: " + failureDescription);
        }
    }

}
