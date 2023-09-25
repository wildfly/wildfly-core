/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.deployment;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.DeploymentScannerSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Base class for tests that need to use a deployment scanner.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public abstract class AbstractDeploymentScannerBasedTestCase {

    protected static final int DELAY = 100;
    protected static final PathAddress DEPLOYMENT_ONE = PathAddress.pathAddress(DEPLOYMENT, "deployment-one.jar");

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private DeploymentScannerSetupTask scannerSetupTask ;

    protected void addDeploymentScanner(ModelControllerClient modelControllerClient, int scanInterval, boolean rollback, boolean enabled) throws Exception {

        // Use DeploymentScannerSetupTask to deal with installing the extension/subsystem if needed
        scannerSetupTask = new DeploymentScannerSetupTask();
        scannerSetupTask.setup(modelControllerClient);

        // add deployment scanner
        final ModelNode op = getAddDeploymentScannerOp(scanInterval, rollback, enabled);
        ModelNode result = modelControllerClient.execute(op);
        assertEquals("Unexpected outcome of adding the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
    }

    protected void removeDeploymentScanner(ModelControllerClient modelControllerClient) throws Exception {
        try {
            // remove deployment scanner
            final ModelNode op = getRemoveDeploymentScannerOp();
            ModelNode result = modelControllerClient.execute(op);
            assertEquals("Unexpected outcome of removing the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
        } finally {
            // Use DeploymentScannerSetupTask to deal with removing the extension/subsystem if needed
            scannerSetupTask.tearDown(modelControllerClient);
        }
    }

    protected File getDeployDir() {
        return this.tempDir.getRoot();
    }

    protected final Path getDeployDirPath() {
        return getDeployDir().toPath();
    }

    private ModelNode getAddDeploymentScannerOp(int scanInterval, boolean rollback, boolean enabled) {
        final ModelNode op = Util.createAddOperation(getTestDeploymentScannerResourcePath());
        op.get("scan-interval").set(scanInterval);
        op.get("runtime-failure-causes-rollback").set(rollback);
        op.get("path").set(getDeployDir().getAbsolutePath());
        op.get("scan-enabled").set(enabled);
        return op;
    }

    private ModelNode getRemoveDeploymentScannerOp() {
        return createOpNode("subsystem=deployment-scanner/scanner=testScanner", "remove");
    }

    protected PathAddress getTestDeploymentScannerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement("subsystem", "deployment-scanner"), PathElement.pathElement("scanner", "testScanner"));
    }

    protected boolean exists(ModelControllerClient client, PathAddress address) throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_RESOURCE_OPERATION, address);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(RECURSIVE).set(true);

        final ModelNode result = client.execute(operation);
        return SUCCESS.equals(result.get(OUTCOME).asString());
    }

    protected String deploymentState(final ModelControllerClient client, final PathAddress address) throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
        operation.get(NAME).set("status");

        final ModelNode result = client.execute(operation);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            return result.get(RESULT).asString();
        }
        return FAILED;
    }

    protected boolean isRunning(ModelControllerClient client) {
        final ModelNode operation = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.EMPTY_ADDRESS);
        operation.get(NAME).set("runtime-configuration-state");

        try {
            final ModelNode result = client.execute(operation);
            if (SUCCESS.equals(result.get(OUTCOME).asString())) {
                return "ok".equals(result.get(RESULT).asString());
            }
        } catch (Exception e) {
            // ignore; return false
        }
        return false;
    }

    protected Archive createDeploymentArchive(final String dependency) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        final String dependencies = "Dependencies: " + dependency;
        archive.add(new StringAsset(dependencies), "META-INF/MANIFEST.MF");
        return archive;
    }

    protected void createDeployment(final Path file, final String dependency) throws IOException {
        final Archive archive = createDeploymentArchive(dependency);
        try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
            archive.as(ZipExporter.class).exportTo(out);
        }
    }

    protected void createDeployment(final File file, final String dependency) throws IOException {
        createDeployment(file, dependency, false);
    }

    protected void createDeployment(final File file, final String dependency, boolean overwrite) throws IOException {
        createDeploymentArchive(dependency).as(ZipExporter.class).exportTo(file, overwrite);
    }
}
