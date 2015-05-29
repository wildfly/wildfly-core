/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, JBoss Inc., and individual contributors as indicated
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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class AbstractDeploymentScannerOperationTestCase {
    protected static final int DELAY = 100;
    protected static final int TIMEOUT = 30000;
    protected static final PathAddress DEPLOYMENT_ONE = PathAddress.pathAddress(DEPLOYMENT, "deployment-one.jar");

    protected static final String tempDir = System.getProperty("java.io.tmpdir");
    protected static File deployDir;

    @Before
    public void before() throws IOException {
        deployDir = new File(tempDir + File.separator + "deployment-test-" + UUID.randomUUID().toString());
        if (deployDir.exists()) {
            FileUtils.deleteDirectory(deployDir);
        }
        assertTrue("Unable to create deployment scanner directory.", deployDir.mkdir());
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteDirectory(deployDir);
    }

    protected void addDeploymentScanner(ModelControllerClient client) throws Exception {
        ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, "org.jboss.as.deployment-scanner")));
        ModelNode result = executeOperation(client, addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner extension: " + addOp, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
        addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "deployment-scanner")));
        result = executeOperation(client, addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner subsystem: " + addOp, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
        // add deployment scanner
        final ModelNode op = getAddDeploymentScannerOp();
        result = executeOperation(client, op);
        assertEquals("Unexpected outcome of adding the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
    }

    protected void removeDeploymentScanner(ModelControllerClient client) throws Exception {
        boolean ok = false;
        try {
            // remove deployment scanner
            final ModelNode op = getRemoveDeploymentScannerOp();
            ModelNode result = executeOperation(client, op);
            assertEquals("Unexpected outcome of removing the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
            ok = true;
        } finally {
            try {
                boolean wasOK = ok;
                ok = false;
                ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "deployment-scanner")));
                ModelNode result = executeOperation(client, removeOp);
                if (wasOK) {
                    assertEquals("Unexpected outcome of removing the test deployment scanner subsystem: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
                } // else don't override the previous assertion error in this finally block
                ok = wasOK;
            } finally {
                ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, "org.jboss.as.deployment-scanner")));
                ModelNode result = executeOperation(client, removeOp);
                if (ok) {
                    assertEquals("Unexpected outcome of removing the test deployment scanner extension: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
                }  // else don't override the previous assertion error in this finally block
            }
        }
    }

    protected ModelNode executeOperation(ModelControllerClient client, ModelNode op) throws IOException {
        return client.execute(op);
    }

    protected ModelNode getAddDeploymentScannerOp() {
        final ModelNode op = Util.createAddOperation(getTestDeploymentScannerResourcePath());
        op.get("scan-interval").set(0);
        op.get("path").set(deployDir.getAbsolutePath());
        op.get("scan-enabled").set(false);
        return op;
    }

    protected ModelNode getRemoveDeploymentScannerOp() {
        return createOpNode("subsystem=deployment-scanner/scanner=testScanner", "remove");
    }

    protected PathAddress getTestDeploymentScannerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement("subsystem", "deployment-scanner"), PathElement.pathElement("scanner", "testScanner"));
    }

    protected void createDeployment(final File file, final String dependency) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        final String dependencies = "Dependencies: " + dependency;
        archive.add(new StringAsset(dependencies), "META-INF/MANIFEST.MF");
        archive.as(ZipExporter.class).exportTo(file);
    }

    protected boolean exists(ModelControllerClient client, PathAddress address) throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_RESOURCE_OPERATION, address);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(RECURSIVE).set(true);

        final ModelNode result = executeOperation(client, operation);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            return true;
        }
        return false;
    }
}
