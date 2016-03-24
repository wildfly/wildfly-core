/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
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
package org.jboss.as.test.manualmode.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
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

import java.io.File;
import java.io.IOException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public abstract class AbstractDeploymentUnitTestCase {

    protected void addDeploymentScanner(int scanInterval) throws Exception {
        ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, "org.jboss.as.deployment-scanner")));
        ModelNode result = executeOperation(addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner extension: " + addOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
        addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "deployment-scanner")));
        result = executeOperation(addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner subsystem: " + addOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
        // add deployment scanner
        final ModelNode op = getAddDeploymentScannerOp(scanInterval);
        result = executeOperation(op);
        assertEquals("Unexpected outcome of adding the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
    }

    protected void removeDeploymentScanner() throws Exception {
        boolean ok = false;
        try {
            // remove deployment scanner
            final ModelNode op = getRemoveDeploymentScannerOp();
            ModelNode result = executeOperation(op);
            assertEquals("Unexpected outcome of removing the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
            ok = true;
        } finally {
            try {
                boolean wasOK = ok;
                ok = false;
                ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "deployment-scanner")));
                ModelNode result = executeOperation(removeOp);
                if (wasOK) {
                    assertEquals("Unexpected outcome of removing the test deployment scanner subsystem: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
                } // else don't override the previous assertion error in this finally block
                ok = wasOK;
            } finally {
                ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, "org.jboss.as.deployment-scanner")));
                ModelNode result = executeOperation(removeOp);
                if (ok) {
                    assertEquals("Unexpected outcome of removing the test deployment scanner extension: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
                }  // else don't override the previous assertion error in this finally block
            }
        }
    }

    protected abstract ModelNode executeOperation(ModelNode op) throws IOException;

    protected abstract File getDeployDir();

    protected ModelNode getAddDeploymentScannerOp(int scanInterval) {
        final ModelNode op = Util.createAddOperation(getTestDeploymentScannerResourcePath());
        op.get("scan-interval").set(scanInterval);
        op.get("path").set(getDeployDir().getAbsolutePath());
        return op;
    }

    protected ModelNode getRemoveDeploymentScannerOp() {
        return createOpNode("subsystem=deployment-scanner/scanner=testScanner", "remove");
    }

    protected PathAddress getTestDeploymentScannerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement("subsystem", "deployment-scanner"), PathElement.pathElement("scanner", "testScanner"));
    }

    protected boolean exists(PathAddress address) throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_RESOURCE_OPERATION, address);
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(RECURSIVE).set(true);

        final ModelNode result = executeOperation(operation);
        return SUCCESS.equals(result.get(OUTCOME).asString());
    }

    protected String deploymentState(final PathAddress address) throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, address);
        operation.get(NAME).set("status");

        final ModelNode result = executeOperation(operation);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            return result.get(RESULT).asString();
        }
        return FAILED;
    }

    protected boolean isRunning() throws IOException {
        final ModelNode operation = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.EMPTY_ADDRESS);
        operation.get(NAME).set("runtime-configuration-state");

        final ModelNode result = executeOperation(operation);
        if (SUCCESS.equals(result.get(OUTCOME).asString())) {
            return "ok".equals(result.get(RESULT).asString());
        }
        return false;
    }

    protected void createDeployment(final File file, final String dependency) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        final String dependencies = "Dependencies: " + dependency;
        archive.add(new StringAsset(dependencies), "META-INF/MANIFEST.MF");
        archive.as(ZipExporter.class).exportTo(file);
    }
}
