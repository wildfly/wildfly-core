/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 * Server setup task that enables the extension for deployment scanner.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class DeploymentScannerSetupTask implements ServerSetupTask {
    private static final PathAddress DEPLOYMENT_SCANNER_EXTENSION = PathAddress.pathAddress(EXTENSION, "org.jboss.as.deployment-scanner");
    private static final PathAddress DEPLOYMENT_SCANNER_SUBSYSTEM = PathAddress.pathAddress(SUBSYSTEM, "deployment-scanner");

    private boolean hasSubsytem;

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        setup(managementClient.getControllerClient());
    }

    public void setup(ModelControllerClient modelControllerClient) throws Exception {

        ModelNode op = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(CHILD_TYPE).set(SUBSYSTEM);
        ModelNode result = modelControllerClient.execute(op);
        assertEquals("Unexpected outcome of checking for presence of the deployment scanner extension: " + op, SUCCESS, result.get(OUTCOME).asString());
        for (ModelNode child : result.get(RESULT).asList()) {
            if (child.asString().equals(DEPLOYMENT_SCANNER_SUBSYSTEM.getLastElement().getValue())) {
                hasSubsytem = true;
                break;
            }
        }

        if (!hasSubsytem) {
            ModelNode addOp = Util.createAddOperation(DEPLOYMENT_SCANNER_EXTENSION);
            result = modelControllerClient.execute(addOp);
            assertEquals("Unexpected outcome of adding the test deployment scanner extension: " + addOp, SUCCESS, result.get(OUTCOME).asString());
            addOp = Util.createAddOperation(DEPLOYMENT_SCANNER_SUBSYSTEM);
            result = modelControllerClient.execute(addOp);
            assertEquals("Unexpected outcome of adding the test deployment scanner subsystem: " + addOp, SUCCESS, result.get(OUTCOME).asString());

            // Reset state in case this gets reused
            hasSubsytem = false;
        }
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        tearDown(managementClient.getControllerClient());
    }

    public void tearDown(ModelControllerClient modelControllerClient) throws Exception {

        if (!hasSubsytem) {
            try {
                ModelNode removeOp = Util.createRemoveOperation(DEPLOYMENT_SCANNER_SUBSYSTEM);
                modelControllerClient.execute(removeOp);
            } finally {
                ModelNode removeOp = Util.createRemoveOperation(DEPLOYMENT_SCANNER_EXTENSION);
                modelControllerClient.execute(removeOp);
            }
        }
    }

}
