/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
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
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 * Server setup task that enables the extension for deployment scanner.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class DeploymentScannerSetupTask implements ServerSetupTask {
    private static final String DEPLOYMENT_SCANNER_EXTENSION = "org.jboss.as.deployment-scanner";
    private static final String DEPLOYMENT_SCANNER_SUBSYSTEM = "deployment-scanner";

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        ModelNode addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, DEPLOYMENT_SCANNER_EXTENSION)));
        ModelNode result = managementClient.getControllerClient().execute(addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner extension: " + addOp, SUCCESS, result.get(OUTCOME).asString());
        addOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, DEPLOYMENT_SCANNER_SUBSYSTEM)));
        result = managementClient.getControllerClient().execute(addOp);
        assertEquals("Unexpected outcome of adding the test deployment scanner subsystem: " + addOp, SUCCESS, result.get(OUTCOME).asString());
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        try {
            ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, DEPLOYMENT_SCANNER_SUBSYSTEM)));
            managementClient.getControllerClient().execute(removeOp);
        } finally {
            ModelNode removeOp = Util.createRemoveOperation(PathAddress.pathAddress(PathElement.pathElement(EXTENSION, DEPLOYMENT_SCANNER_EXTENSION)));
            managementClient.getControllerClient().execute(removeOp);
        }
    }

}
