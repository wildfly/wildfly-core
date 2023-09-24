/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * Handles undeployment from the runtime.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerGroupDeploymentUndeployHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = UNDEPLOY;

    public static final ServerGroupDeploymentUndeployHandler INSTANCE = new ServerGroupDeploymentUndeployHandler();

    private ServerGroupDeploymentUndeployHandler() {
    }

    public void execute(OperationContext context, ModelNode operation) {
        context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(ENABLED).set(false);
    }
}
