/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
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
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerRedeploymentTestCase {
    private static final int DELAY = 100;
    private static final int TIMEOUT = 30000;
    private static final PathAddress DEPLOYMENT_TEST = PathAddress.pathAddress(DEPLOYMENT, "deployment-test.jar");

    private static final String DEFAULT_SERVER = "standalone";

    @Inject
    private ServerController container;

    private ModelControllerClient client;

    private static final String jbossHome = System.getProperty("jboss.home");
    private static File deployDir;

    @Before
    public void before() throws IOException {
        deployDir = new File(jbossHome + File.separator + DEFAULT_SERVER + File.separator + "deployments");
        assertTrue("Unable to find deployment scanner directory.", deployDir.exists());
    }

    @After
    public void after() throws IOException {
        FileUtils.cleanDirectory(deployDir);
    }

    @Test
    public void testRedeployment() throws Exception {
        container.start();
        try {
            client = TestSuiteEnvironment.getModelControllerClient();
            try {

                final File deployment = new File(deployDir, "deployment-test.jar");

                createDeployment(deployment, "non.existing.dependency");

                // Add a new deployment scanner
                addDeploymentScanner();
                try {
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
                    while (!exists(DEPLOYMENT_TEST) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(DELAY);
                    }
                    Assert.assertTrue("deployemt archive is expected.", exists(DEPLOYMENT_TEST));
                    Assert.assertEquals("FAILED", deploymentState(DEPLOYMENT_TEST));

                    final File deployed = new File(deployDir, "deployment-test.jar.deployed");

                    // Restart ...
                    container.stop();

                    // replace broken deployment with a correct one
                    createDeployment(deployment, "org.jboss.modules");

                    container.start();

                    // Wait until started ...
                    timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
                    while (!isRunning() && System.currentTimeMillis() < timeout) {
                        Thread.sleep(200);
                    }

                    timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
                    while (exists(DEPLOYMENT_TEST) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(200);
                    }

                    Assert.assertTrue(".deployed marker is expected.", deployed.exists());
                    Assert.assertTrue("deployemt archive is expected.", exists(DEPLOYMENT_TEST));
                    Assert.assertEquals("OK", deploymentState(DEPLOYMENT_TEST));

                } finally {
                    removeDeploymentScanner();
                }

            } finally {
                StreamUtils.safeClose(client);
            }
        } finally {
            container.stop();
        }
    }

    private void addDeploymentScanner() throws Exception {
        ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION,
                "org.jboss.as.deployment-scanner")));
        ModelNode result = executeOperation(addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner extension: " + addOp,
                ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
        addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "deployment-scanner")));
        result = executeOperation(addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner subsystem: " + addOp,
                ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
        // add deployment scanner
        final ModelNode op = getAddDeploymentScannerOp();
        result = executeOperation(op);
        assertEquals("Unexpected outcome of adding the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS,
                result.get("outcome").asString());
    }

    private void removeDeploymentScanner() throws Exception {
        boolean ok = false;
        try {
            // remove deployment scanner
            final ModelNode op = getRemoveDeploymentScannerOp();
            ModelNode result = executeOperation(op);
            assertEquals("Unexpected outcome of removing the test deployment scanner: " + op,
                    ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
            ok = true;
        } finally {
            try {
                boolean wasOK = ok;
                ok = false;
                ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM,
                        "deployment-scanner")));
                ModelNode result = executeOperation(removeOp);
                if (wasOK) {
                    assertEquals("Unexpected outcome of removing the test deployment scanner subsystem: " + removeOp,
                            ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
                } // else don't override the previous assertion error in this finally block
                ok = wasOK;
            } finally {
                ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION,
                        "org.jboss.as.deployment-scanner")));
                ModelNode result = executeOperation(removeOp);
                if (ok) {
                    assertEquals("Unexpected outcome of removing the test deployment scanner extension: " + removeOp,
                            ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
                } // else don't override the previous assertion error in this finally block
            }
        }
    }

    private ModelNode executeOperation(ModelNode op) throws IOException {
        return client.execute(op);
    }

    private ModelNode getAddDeploymentScannerOp() {
        final ModelNode op = Util.createAddOperation(getTestDeploymentScannerResourcePath());
        op.get("scan-interval").set(0);
        op.get("path").set(deployDir.getAbsolutePath());
        return op;
    }

    private ModelNode getRemoveDeploymentScannerOp() {
        return createOpNode("subsystem=deployment-scanner/scanner=testScanner", "remove");
    }

    private PathAddress getTestDeploymentScannerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement("subsystem", "deployment-scanner"),
                PathElement.pathElement("scanner", "testScanner"));
    }

    protected void createDeployment(final File file, final String dependency) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        final String dependencies = "Dependencies: " + dependency;
        archive.add(new StringAsset(dependencies), "META-INF/MANIFEST.MF");
        archive.as(ZipExporter.class).exportTo(file, true);
    }

    protected boolean exists(PathAddress address) throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_RESOURCE_OPERATION, address);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(RECURSIVE).set(true);

        final ModelNode result = executeOperation(operation);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            return true;
        }
        return false;
    }

    protected String deploymentState(final PathAddress address) throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
        operation.get(NAME).set("status");

        final ModelNode result = executeOperation(operation);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            return result.get(RESULT).asString();
        }
        return "failed";
    }

    protected boolean isRunning() throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.EMPTY_ADDRESS);
        operation.get(NAME).set("server-state");

        final ModelNode result = executeOperation(operation);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            return "running".equals(result.get(RESULT).asString());
        }
        return false;
    }

}
