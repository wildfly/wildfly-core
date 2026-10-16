/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EMPTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TARGET_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
public class DeploymentNamePathTraversalTestCase {

    private static final boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");

    private static final String BASE_NAME = DeploymentNamePathTraversalTestCase.class.getSimpleName();

    private static final Path JBOSS_HOME = FileSystems.getDefault().getPath(System.getProperty("jboss.home", System.getenv("JBOSS_HOME")));

    // Path traversal from $JBOSS_HOME/standalone/data/managed-content to $JBOSS_HOME/standalone/log/
    private static final String TRAVERSAL_PREFIX =
            ".." + File.separatorChar + ".." + File.separatorChar + "test" + File.separatorChar;

    private String deploymentName;

    @Inject
    private ManagementClient managementClient;

    @After
    public void tearDown() throws Exception {
        try {
            removeTestDir();
        } finally {
            if (deploymentName != null) {
                ModelNode op = Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, deploymentName));
                executeOp(op);
            }
        }
    }

    @Test
    public void testEmptyExplodedDeployment() throws Exception {
        String depName = BASE_NAME + "-empty-exploded";
        Path testFile = createTestFile(depName);

        // Use a deployment name that points to test dir subdir
        String traversalDeploymentName = TRAVERSAL_PREFIX + depName;

        // Initial add succeeds because we don't try to deploy.
        // Note: if we change behavior to reject the name in Stage.MODEL,
        // changing this test is ok.
        PathAddress address = PathAddress.pathAddress(DEPLOYMENT, traversalDeploymentName);
        ModelNode addOp = Util.createAddOperation(address);
        addOp.get(ENABLED).set(false);
        ModelNode empty = new ModelNode();
        empty.get(EMPTY).set(true);
        addOp.get(CONTENT).add(empty);
        executeOp(addOp);
        deploymentName = traversalDeploymentName; // add succeeded, so we can remove in tearDown

        // Trying to actually deploy fails
        ModelNode deployOp = Util.createEmptyOperation(DEPLOY, address);
        executeOp(deployOp, "WFLYSRV0317", traversalDeploymentName);

        // confirm rejected op did not impact the target dir
        assertTrue(testFile.toFile().exists());
    }

    @Test
    public void testExplodedDeploymentAddContent() throws Exception {

        String depName = BASE_NAME + "-exploded-add-content";

        Path testDir = createTestDir(depName);

        // Use a deployment name that points to our log file
        String traversalDeploymentName = TRAVERSAL_PREFIX + depName;

        // Initial add succeeds because we don't try to deploy.
        // Note: if we change behavior to reject the name in Stage.MODEL,
        // changing this test is ok.
        PathAddress address = PathAddress.pathAddress(DEPLOYMENT, traversalDeploymentName);
        ModelNode addOp = Util.createAddOperation(address);
        addOp.get(ENABLED).set(false);
        ModelNode empty = new ModelNode();
        empty.get(EMPTY).set(true);
        addOp.get(CONTENT).add(empty);
        executeOp(addOp);
        deploymentName = traversalDeploymentName; // add succeeded, so we can remove in tearDown

        // confirm rejected op did not impact the target dir
        assertTrue(testDir.toFile().exists());
        assertTrue(testDir.toFile().isDirectory());

        // Add content
        ModelNode addContent = Util.createEmptyOperation(ADD_CONTENT, address);
        ModelNode contentItem = new ModelNode();
        contentItem.get(BYTES).set("text".getBytes(StandardCharsets.UTF_8));
        contentItem.get(TARGET_PATH).set("text.txt");
        addContent.get(CONTENT).add(contentItem);
        executeOp(addContent);

        // confirm rejected op did not impact the target dir
        assertTrue(testDir.toFile().exists());
        assertTrue(testDir.toFile().isDirectory());
        assertFalse(testDir.resolve( "test.txt").toFile().exists());

        // Trying to actually deploy fails
        ModelNode deployOp = Util.createEmptyOperation(DEPLOY, address);
        executeOp(deployOp, "WFLYSRV0317", traversalDeploymentName);

        // confirm rejected op did not impact the target dir
        assertTrue(testDir.toFile().exists());
        assertTrue(testDir.toFile().isDirectory());
        assertFalse(testDir.resolve( "test.txt").toFile().exists());
    }

    private Path createTestDir(String dirName) throws Exception {
        Path p = JBOSS_HOME.resolve("standalone").resolve("test").resolve(dirName);
        Files.createDirectories(p);
        assertTrue(p.toFile().exists());
        return p;
    }

    private Path createTestFile(String fileName) throws Exception {
        Path p = createTestDir(fileName).resolve("test.txt");
        File f = Files.createFile(p).toFile();
        try (PrintWriter writer = new PrintWriter(f)) {
            writer.print("ok");
        }
        assertTrue(f.exists());
        return p;
    }

    private void removeTestDir() throws Exception {
        Path p = JBOSS_HOME.resolve("standalone").resolve("test");
        if (p.toFile().exists()) {
            Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        }
    }

    private void executeOp(ModelNode op, String... failureMessageContent) throws Exception {
        executeOp(new OperationBuilder(op).build(), failureMessageContent);
    }

    private void executeOp(Operation op, String... failureMessageContent) throws Exception {
        ModelNode response = managementClient.getControllerClient().execute(op);
        if (failureMessageContent.length > 0) {
            assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
            String failureDescription = response.get(FAILURE_DESCRIPTION).asString();
            for (String failureMsg : failureMessageContent) {
                if (isWindows) {
                    // The original unescaped MSC service start exception message gets escaped in the operation response
                    failureMsg = failureMsg.replaceAll("\\\\", "\\\\\\\\");
                }
                assertTrue(response.asString(), failureDescription.contains(failureMsg));
            }
        } else {
            assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
        }
    }
}
